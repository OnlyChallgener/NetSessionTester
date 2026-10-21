package com.demonv.netsessiontester.ios

import com.demonv.netsessiontester.core.CpsPacer
import com.demonv.netsessiontester.core.PingAccumulator
import com.demonv.netsessiontester.core.PingSummary
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.posix.*

/** iOS POSIX Socket 引擎。测试规则由公共测量核心提供，系统调用留在 iOS 层。 */
class IosTcpEngine {
    private val runMutex = Mutex()
    private val socketMutex = Mutex()
    private val heldSocketFds = mutableListOf<Int>()
    private var activeRunJob: Job? = null

    fun setScreenKeepAwake(keepAwake: Boolean) {
        runCatching { UIApplication.sharedApplication.idleTimerDisabled = keepAwake }
    }

    fun triggerHapticFeedback(isHeavy: Boolean = false) {
        runCatching {
            val style = if (isHeavy) UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
            else UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            UIImpactFeedbackGenerator(style).apply {
                prepare()
                impactOccurred()
            }
        }
    }

    /** 同时解析两种地址族，仅用于展示和开始前的能力检查。 */
    @OptIn(ExperimentalForeignApi::class)
    suspend fun resolveHost(host: String): IosResolveResult = withContext(Dispatchers.Default) {
        val cleanHost = cleanHost(host)
        val ipv4 = mutableListOf<String>()
        val ipv6 = mutableListOf<String>()
        var error: String? = null

        memScoped {
            val hints = alloc<addrinfo>().apply {
                ai_family = AF_UNSPEC
                ai_socktype = SOCK_STREAM
                ai_protocol = IPPROTO_TCP
            }
            val result = allocPointerTo<addrinfo>()
            result.value = null
            val status = getaddrinfo(cleanHost, null, hints.ptr, result.ptr)
            val head = result.value
            if (status != 0 || head == null) {
                error = gai_strerror(status)?.toKString() ?: "DNS 解析失败 (Code: $status)"
            } else {
                try {
                    val hostBuffer = allocArray<ByteVar>(128)
                    var current: CPointer<addrinfo>? = head
                    while (current != null) {
                        val family = current.pointed.ai_family
                        if (family == AF_INET || family == AF_INET6) {
                            val nameStatus = getnameinfo(
                                current.pointed.ai_addr,
                                current.pointed.ai_addrlen,
                                hostBuffer,
                                128u,
                                null,
                                0u,
                                NI_NUMERICHOST
                            )
                            if (nameStatus == 0) {
                                val address = hostBuffer.toKString()
                                val target = if (family == AF_INET) ipv4 else ipv6
                                if (address !in target) target.add(address)
                            }
                        }
                        current = current.pointed.ai_next
                    }
                } finally {
                    freeaddrinfo(head)
                }
            }
        }
        IosResolveResult(cleanHost, ipv4, ipv6, error)
    }

    private class FdHandoff(var fd: Int? = null)

    /**
     * 严格按指定地址族执行一次 TCP 握手。外层 handoff 持有 fd，覆盖后台 dispatcher
     * 完成到调用方恢复之间的取消窗口；只有调用方恢复且确认仍活跃后才转移所有权。
     */
    private suspend fun connectResolvedSingle(
        address: String,
        port: Int,
        protocol: IosIpProtocol,
        timeoutMs: Int
    ): Pair<Int?, Int?> = connectWithSafeHandoff(
        address, port, if (protocol == IosIpProtocol.IPV6) AF_INET6 else AF_INET,
        timeoutMs
    )

    private suspend fun connectWithSafeHandoff(
        host: String,
        port: Int,
        family: Int,
        timeoutMs: Int
    ): Pair<Int?, Int?> {
        val handoff = FdHandoff()
        try {
            val result = withContext(Dispatchers.Default) {
                connectOnWorker(host, port, family, timeoutMs, handoff)
            }
            currentCoroutineContext().ensureActive()
            handoff.fd = null
            return result
        } finally {
            handoff.fd?.let { close(it) }
            handoff.fd = null
        }
    }

    /** poll(0) + cancellable delay never parks a Kotlin/Native worker for the full timeout. */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun connectOnWorker(
        host: String,
        port: Int,
        family: Int,
        timeoutMs: Int,
        handoff: FdHandoff
    ): Pair<Int?, Int?> {
        val context = currentCoroutineContext()
        val head = memScoped {
            val hints = alloc<addrinfo>().apply {
                ai_family = family
                ai_socktype = SOCK_STREAM
                ai_protocol = IPPROTO_TCP
                ai_flags = AI_NUMERICHOST or AI_NUMERICSERV
            }
            val result = allocPointerTo<addrinfo>()
            result.value = null
            if (getaddrinfo(host, port.toString(), hints.ptr, result.ptr) == 0) result.value else null
        } ?: return Pair(null, null)

        var ownedFd: Int? = null
        try {
            val startedAt = getMonotonicMs()
            val deadline = startedAt + timeoutMs.coerceAtLeast(1)
            var current: CPointer<addrinfo>? = head
            while (current != null && getMonotonicMs() < deadline) {
                context.ensureActive()
                val fd = socket(current.pointed.ai_family, SOCK_STREAM, IPPROTO_TCP)
                if (fd >= 0) {
                    ownedFd = fd
                    memScoped {
                        val noSigPipe = alloc<IntVar>().apply { value = 1 }
                        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, noSigPipe.ptr, sizeOf<IntVar>().toUInt())
                    }
                    val flags = fcntl(fd, F_GETFL, 0)
                    if (flags >= 0 && fcntl(fd, F_SETFL, flags or O_NONBLOCK) >= 0) {
                        val connectResult = connect(fd, current.pointed.ai_addr, current.pointed.ai_addrlen)
                        var connected = connectResult == 0
                        var pending = !connected && errno == EINPROGRESS
                        while (pending && !connected && getMonotonicMs() < deadline) {
                            context.ensureActive()
                            val pollResult = memScoped {
                                val pollFd = alloc<pollfd>().apply {
                                    this.fd = fd
                                    events = (POLLOUT or POLLERR).toShort()
                                }
                                poll(pollFd.ptr, 1u, 0)
                            }
                            if (pollResult > 0) {
                                val socketError = memScoped {
                                    val error = alloc<IntVar>()
                                    val length = alloc<socklen_tVar>().apply { value = sizeOf<IntVar>().toUInt() }
                                    if (getsockopt(fd, SOL_SOCKET, SO_ERROR, error.ptr, length.ptr) == 0) error.value else errno
                                }
                                connected = socketError == 0
                                pending = false
                            } else if (pollResult < 0 && errno != EINTR) {
                                pending = false
                            } else {
                                delay(5L)
                            }
                        }
                        if (connected) {
                            handoff.fd = fd
                            ownedFd = null
                            return Pair(fd, (getMonotonicMs() - startedAt).toInt().coerceAtLeast(1))
                        }
                    }
                    if (ownedFd == fd) {
                        close(fd)
                        ownedFd = null
                    }
                }
                current = current.pointed.ai_next
            }
            return Pair(null, null)
        } finally {
            ownedFd?.let { close(it) }
            freeaddrinfo(head)
        }
    }

    private suspend fun probeResolved(
        address: String,
        port: Int,
        protocol: IosIpProtocol,
        timeoutMs: Int
    ): Int? {
        val (fd, latencyMs) = connectResolvedSingle(address, port, protocol, timeoutMs)
        fd?.let { close(it) }
        return latencyMs
    }

    suspend fun runSessionHoldTest(
        rawConfig: IosSessionConfig,
        onStats: suspend (IosProtocolStats) -> Unit,
        onLog: suspend (IosLogLine) -> Unit,
        onPingStats: (suspend (IosPingStats) -> Unit)? = null
    ): Pair<IosProtocolStats?, IosProtocolStats?> = runMutex.withLock {
        coroutineScope {
            val config = rawConfig.normalized()
            val runJob = currentCoroutineContext()[Job]
            activeRunJob = runJob
            setScreenKeepAwake(true)
            triggerHapticFeedback(false)

            var ipv4Stats: IosProtocolStats? = null
            var ipv6Stats: IosProtocolStats? = null
            try {
                val staleCount = releaseHeldSockets()
                if (staleCount > 0) onLog(log(IosLogLevel.WARN, "开始新测试前已释放 $staleCount 条旧连接"))
                when (config.mode) {
                    IosTestMode.IPV4_ONLY -> ipv4Stats = runOneProtocol(
                        config, IosIpProtocol.IPV4, onStats, onLog, onPingStats
                    )
                    IosTestMode.IPV6_ONLY -> ipv6Stats = runOneProtocol(
                        config, IosIpProtocol.IPV6, onStats, onLog, onPingStats
                    )
                    IosTestMode.IPV4_THEN_IPV6 -> {
                        ipv4Stats = runOneProtocol(config, IosIpProtocol.IPV4, onStats, onLog, onPingStats)
                        val released = releaseHeldSockets()
                        onLog(log(IosLogLevel.WARN, "IPv4 测试结束并释放 $released 条连接，切换 IPv6"))
                        delay(300L)
                        ipv6Stats = runOneProtocol(config, IosIpProtocol.IPV6, onStats, onLog, onPingStats)
                    }
                }
            } finally {
                if (activeRunJob === runJob) activeRunJob = null
                setScreenKeepAwake(false)
                if (!config.keepConnectionsAfterStop) {
                    withContext(NonCancellable) { releaseHeldSockets() }
                }
            }
            Pair(ipv4Stats, ipv6Stats)
        }
    }

    private data class ConnectOutcome(val fd: Int?, val latencyMs: Int?)

    private suspend fun runOneProtocol(
        config: IosSessionConfig,
        protocol: IosIpProtocol,
        onStats: suspend (IosProtocolStats) -> Unit,
        onLog: suspend (IosLogLine) -> Unit,
        onPingStats: (suspend (IosPingStats) -> Unit)?
    ): IosProtocolStats = coroutineScope {
        val preferIpv6 = protocol == IosIpProtocol.IPV6
        val resolved = resolveHost(config.host)
        val addresses = if (preferIpv6) resolved.ipv6 else resolved.ipv4
        onLog(log(IosLogLevel.INFO, "开始 ${protocol.label} 握手压测: ${config.host}:${config.port}"))

        if (addresses.isEmpty()) {
            val failed = IosProtocolStats(
                protocol = protocol,
                phase = "无可用 ${protocol.label} 地址",
                totalFailure = 1,
                totalAttempts = 1,
                errorSummary = mapOf("无可用 ${protocol.label} 地址" to 1)
            )
            onStats(failed)
            onLog(log(IosLogLevel.ERROR, "${protocol.label} 解析失败，未回退到其他地址族"))
            return@coroutineScope failed
        }
        onLog(log(IosLogLevel.INFO, "${protocol.label} 解析目标: ${addresses.joinToString(", ")}"))

        val pingJob = onPingStats?.let { callback ->
            launch {
                val accumulator = PingAccumulator()
                var last = IosPingStats(host = config.host, port = config.port, protocol = protocol)
                var addressIndex = 0
                try {
                    while (isActive) {
                        val cycleStartedAt = getMonotonicMs()
                        val address = addresses[addressIndex++ % addresses.size]
                        val rtt = probeResolved(address, config.port, protocol, config.timeoutMs)
                        last = accumulator.record(rtt).toIosStats(
                            config.host, config.port, protocol, true,
                            if (rtt == null) "超时丢包" else "联动探测中"
                        )
                        callback(last)
                        val remaining = config.pingIntervalMs - (getMonotonicMs() - cycleStartedAt)
                        if (remaining > 0L) delay(remaining)
                    }
                } finally {
                    withContext(NonCancellable) { callback(last.copy(isRunning = false, phase = "已停止")) }
                }
            }
        }

        var stats = IosProtocolStats(
            protocol = protocol,
            phase = "正在建连",
            resolvedAddresses = addresses
        )
        onStats(stats)

        val results = Channel<ConnectOutcome>(Channel.UNLIMITED)
        val workers = mutableListOf<Job>()
        val successfulAt = ArrayDeque<Long>()
        val errors = mutableMapOf<String, Int>()
        val pacer = CpsPacer(config.batchSize, getMonotonicMs())
        val maxInFlight = minOf(maxOf(config.batchSize, 32), 512)
        var inFlight = 0
        var activeSessions = 0
        var totalSuccess = 0
        var totalFailure = 0
        var totalAttempts = 0
        var totalLatency = 0L
        var lastReportAt = getMonotonicMs()
        var lastHealthCheckAt = lastReportAt
        var addressIndex = 0

        try {
            while (isActive && totalSuccess < config.successLimit && totalFailure < config.failureLimit) {
                var addedSinceReport = 0
                while (true) {
                    val outcome = results.tryReceive().getOrNull() ?: break
                    inFlight--
                    totalAttempts++
                    val fd = outcome.fd
                    var ownedOutcomeFd = fd
                    try {
                        if (fd != null && outcome.latencyMs != null) {
                            if (totalSuccess < config.successLimit) {
                                socketMutex.withLock { heldSocketFds.add(fd) }
                                ownedOutcomeFd = null
                                activeSessions++
                                totalSuccess++
                                addedSinceReport++
                                totalLatency += outcome.latencyMs
                                successfulAt.addLast(getMonotonicMs())
                            }
                        } else {
                            totalFailure++
                            errors["连接超时/被拒"] = (errors["连接超时/被拒"] ?: 0) + 1
                        }
                    } finally {
                        ownedOutcomeFd?.let { close(it) }
                    }
                }

                val now = getMonotonicMs()
                if (now - lastHealthCheckAt >= 1000L) {
                    val health = inspectHeldSockets()
                    activeSessions = health.active
                    if (health.closed > 0) {
                        errors["远端已关闭"] = (errors["远端已关闭"] ?: 0) + health.closed
                    }
                    lastHealthCheckAt = now
                }
                while (successfulAt.isNotEmpty() && successfulAt.first() <= now - 1000L) successfulAt.removeFirst()
                if (now - lastReportAt >= 100L || addedSinceReport > 0) {
                    stats = stats.copy(
                        phase = "持续建连中",
                        activeSessions = activeSessions,
                        totalSuccess = totalSuccess,
                        totalFailure = totalFailure,
                        totalAttempts = totalAttempts,
                        lastAdded = addedSinceReport,
                        cps = successfulAt.size,
                        maxStableSessions = maxOf(stats.maxStableSessions, activeSessions),
                        averageConnectLatencyMs = if (totalSuccess == 0) 0 else (totalLatency / totalSuccess).toInt(),
                        errorSummary = errors.toMap()
                    )
                    onStats(stats)
                    lastReportAt = now
                }

                if (totalSuccess >= config.successLimit || totalFailure >= config.failureLimit) break
                workers.removeAll { it.isCompleted }
                val remainingTarget = (config.successLimit - totalSuccess - inFlight).coerceAtLeast(0)
                val capacity = minOf(maxInFlight - inFlight, remainingTarget)
                val permits = pacer.permits(now, capacity)
                repeat(permits) {
                    inFlight++
                    val address = addresses[addressIndex++ % addresses.size]
                    workers += launch {
                        var ownedFd: Int? = null
                        try {
                            val (fd, latency) = connectResolvedSingle(address, config.port, protocol, config.timeoutMs)
                            ownedFd = fd
                            if (results.trySend(ConnectOutcome(fd, latency)).isSuccess) ownedFd = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            results.trySend(ConnectOutcome(null, null))
                        } finally {
                            ownedFd?.let { close(it) }
                        }
                    }
                }
                delay(config.intervalMs)
            }
        } finally {
            withContext(NonCancellable) {
                workers.forEach { it.cancel() }
                workers.joinAll()
                while (true) {
                    val pending = results.tryReceive().getOrNull() ?: break
                    pending.fd?.let { close(it) }
                }
                results.close()
                pingJob?.cancelAndJoin()
            }
            if (!currentCoroutineContext().isActive) {
                withContext(NonCancellable) {
                    val finalActive = getHeldCount()
                    onStats(stats.copy(
                        phase = "已停止", activeSessions = finalActive,
                        totalSuccess = totalSuccess, totalFailure = totalFailure,
                        totalAttempts = totalAttempts, cps = 0,
                        maxStableSessions = maxOf(stats.maxStableSessions, finalActive),
                        averageConnectLatencyMs = if (totalSuccess == 0) 0 else (totalLatency / totalSuccess).toInt(),
                        errorSummary = errors.toMap()
                    ))
                }
            }
        }

        val finalHealth = inspectHeldSockets()
        activeSessions = finalHealth.active
        if (finalHealth.closed > 0) {
            errors["远端已关闭"] = (errors["远端已关闭"] ?: 0) + finalHealth.closed
        }
        val retainedSessions = if (config.keepConnectionsAfterStop) activeSessions else {
            releaseHeldSockets()
            0
        }
        val reachedTarget = totalSuccess >= config.successLimit
        val reachedFailureLimit = totalFailure >= config.failureLimit
        val finalPhase = when {
            reachedFailureLimit -> "失败达到上限，保留 $retainedSessions 条连接"
            reachedTarget && config.keepConnectionsAfterStop -> "目标完成，连接已维持"
            reachedTarget -> "目标完成并已释放"
            else -> "测试结束"
        }
        stats = stats.copy(
            phase = finalPhase,
            activeSessions = retainedSessions,
            totalSuccess = totalSuccess,
            totalFailure = totalFailure,
            totalAttempts = totalAttempts,
            lastAdded = 0,
            cps = 0,
            maxStableSessions = maxOf(stats.maxStableSessions, activeSessions),
            averageConnectLatencyMs = if (totalSuccess == 0) 0 else (totalLatency / totalSuccess).toInt(),
            errorSummary = errors.toMap()
        )
        onStats(stats)
        onLog(log(
            if (reachedFailureLimit) IosLogLevel.ERROR else IosLogLevel.SUCCESS,
            "${protocol.label} $finalPhase：成功 $totalSuccess，失败 $totalFailure，当前活跃 $retainedSessions"
        ))
        stats
    }

    suspend fun runStandalonePing(
        rawConfig: IosSessionConfig,
        onStats: suspend (IosPingStats) -> Unit,
        onLog: suspend (IosLogLine) -> Unit
    ) = runMutex.withLock {
        coroutineScope {
            val config = rawConfig.normalized()
            val runJob = currentCoroutineContext()[Job]
            activeRunJob = runJob
            setScreenKeepAwake(true)
            triggerHapticFeedback(false)

            val protocols = when (config.mode) {
                IosTestMode.IPV4_ONLY -> listOf(IosIpProtocol.IPV4)
                IosTestMode.IPV6_ONLY -> listOf(IosIpProtocol.IPV6)
                IosTestMode.IPV4_THEN_IPV6 -> listOf(IosIpProtocol.IPV4, IosIpProtocol.IPV6)
            }
            var last = IosPingStats(host = config.host, port = config.port, protocol = protocols.first())

            try {
                val accumulators = protocols.associateWith { PingAccumulator() }
                val resolved = resolveHost(config.host)
                val addresses = mapOf(
                    IosIpProtocol.IPV4 to resolved.ipv4,
                    IosIpProtocol.IPV6 to resolved.ipv6
                )
                val addressIndexes = mutableMapOf(IosIpProtocol.IPV4 to 0, IosIpProtocol.IPV6 to 0)
                var probeIndex = 0
                onLog(log(IosLogLevel.INFO, "启动独立 TCP Ping: ${config.host}:${config.port} (${config.mode.label})"))
                while (isActive) {
                    val cycleStartedAt = getMonotonicMs()
                    val protocol = protocols[(probeIndex / 10) % protocols.size]
                    val protocolAddresses = addresses.getValue(protocol)
                    val index = addressIndexes.getValue(protocol)
                    val rtt = if (protocolAddresses.isEmpty()) null else {
                        addressIndexes[protocol] = index + 1
                        probeResolved(protocolAddresses[index % protocolAddresses.size], config.port, protocol, config.timeoutMs)
                    }
                    val summary = accumulators.getValue(protocol).record(rtt)
                    last = summary.toIosStats(
                        config.host, config.port, protocol, true,
                        if (rtt == null) "超时丢包" else "探测中"
                    )
                    onStats(last)
                    probeIndex++
                    if (rtt == null) {
                        onLog(log(IosLogLevel.ERROR, "${protocol.label} Ping #${summary.sent}: 超时 (丢包率 ${summary.lossPercent.toInt()}%)"))
                    } else {
                        onLog(log(IosLogLevel.STAT, "${protocol.label} Ping #${summary.sent}: ${rtt}ms, 均值 ${summary.averageMs}ms"))
                    }
                    val remaining = config.pingIntervalMs - (getMonotonicMs() - cycleStartedAt)
                    if (remaining > 0L) delay(remaining)
                }
            } finally {
                if (activeRunJob === runJob) activeRunJob = null
                setScreenKeepAwake(false)
                withContext(NonCancellable) { onStats(last.copy(isRunning = false, phase = "已停止")) }
            }
        }
    }

    fun stopTest() {
        activeRunJob?.cancel(CancellationException("用户停止测试"))
    }

    suspend fun releaseHeldSockets(): Int = withContext(Dispatchers.Default) {
        socketMutex.withLock {
            val count = heldSocketFds.size
            heldSocketFds.forEach { close(it) }
            heldSocketFds.clear()
            count
        }
    }

    suspend fun getHeldCount(): Int = inspectHeldSockets().active

    private data class SocketHealth(val active: Int, val closed: Int)

    /**
     * 有界读取并丢弃最多 16KiB 协议数据，以便识别 FIN。只做 MSG_PEEK 会让已排队数据
     * 永远遮住其后的 EOF，导致关闭连接被误算为活跃。
     */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun inspectHeldSockets(): SocketHealth = withContext(Dispatchers.Default) {
        socketMutex.withLock {
            var closedCount = 0
            val drainBuffer = ByteArray(4096)
            drainBuffer.usePinned { pinned ->
                val iterator = heldSocketFds.iterator()
                while (iterator.hasNext()) {
                    val fd = iterator.next()
                    var closed = false
                    var reads = 0
                    while (reads < 4) {
                        val received = recv(fd, pinned.addressOf(0), drainBuffer.size.convert(), MSG_DONTWAIT)
                        if (received > 0L) {
                            reads++
                            continue
                        }
                        if (received < 0L && errno == EINTR) {
                            reads++
                            continue
                        }
                        closed = received == 0L || (errno != EAGAIN && errno != EWOULDBLOCK)
                        break
                    }
                    if (closed) {
                        close(fd)
                        iterator.remove()
                        closedCount++
                    }
                }
            }
            SocketHealth(active = heldSocketFds.size, closed = closedCount)
        }
    }

    private fun cleanHost(host: String): String =
        host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }

    private fun log(level: IosLogLevel, text: String) =
        IosLogLine(timeEpochMs = getEpochMs(), level = level, text = text)

    private fun PingSummary.toIosStats(
        host: String,
        port: Int,
        protocol: IosIpProtocol,
        running: Boolean,
        phase: String
    ) = IosPingStats(
        host = host,
        port = port,
        protocol = protocol,
        currentLatencyMs = currentMs,
        minLatencyMs = minMs,
        maxLatencyMs = maxMs,
        avgLatencyMs = averageMs,
        jitterMs = jitterMs,
        sentCount = sent,
        receivedCount = received,
        lostCount = lost,
        lossPercent = lossPercent,
        isRunning = running,
        phase = phase
    )
}
