package com.demonv.netsessiontester.engine

import com.demonv.netsessiontester.core.CpsPacer
import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.PingStats
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.ResolveResult
import com.demonv.netsessiontester.model.SessionConfig
import com.demonv.netsessiontester.model.TestMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.StandardProtocolFamily
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.channels.CancelledKeyException
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Desktop TCP session tester backed by a single non-blocking selector per run. */
class DesktopTcpTester : AutoCloseable {
    private val stateLock = Any()
    private val generation = AtomicLong(0L)
    private val closed = AtomicBoolean(false)
    private var runningJob: Job? = null
    private var runSelector: Selector? = null
    private val pendingChannels = mutableSetOf<SocketChannel>()
    private val heldChannels: MutableMap<IpProtocol, MutableSet<SocketChannel>> = mutableMapOf(
        IpProtocol.IPV4 to linkedSetOf(),
        IpProtocol.IPV6 to linkedSetOf()
    )

    private val monitorSelector = Selector.open()
    private val monitorRegistrations = ConcurrentLinkedQueue<HeldRegistration>()
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val monitorJob = monitorScope.launch { monitorHeldConnections() }

    suspend fun resolveHost(host: String): ResolveResult = withContext(Dispatchers.IO) {
        val cleanHost = cleanHost(host)
        runCatching {
            val all = InetAddress.getAllByName(cleanHost).toList()
            ResolveResult(
                host = cleanHost,
                ipv4 = all.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.distinct(),
                ipv6 = all.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress }.distinct()
            )
        }.getOrElse { error ->
            ResolveResult(host = cleanHost, error = error.message ?: error.javaClass.simpleName)
        }
    }

    suspend fun runSessionHoldTest(
        rawConfig: SessionConfig,
        onStats: suspend (ProtocolStats) -> Unit,
        onLog: suspend (LogLine) -> Unit,
        pingIntervalMs: Long = 500L,
        onPingStats: (suspend (PingStats) -> Unit)? = null
    ): Pair<ProtocolStats?, ProtocolStats?> = coroutineScope {
        check(!closed.get()) { "DesktopTcpTester is closed" }
        stop()
        val ownerJob = currentCoroutineContext()[Job]
        val expectedGeneration = generation.incrementAndGet()
        synchronized(stateLock) { runningJob = ownerJob }

        val config = rawConfig.normalized()
        var ipv4Stats: ProtocolStats? = null
        var ipv6Stats: ProtocolStats? = null
        try {
            when (config.mode) {
                TestMode.IPV4_ONLY -> ipv4Stats = runProtocolWithPing(
                    config, IpProtocol.IPV4, expectedGeneration, pingIntervalMs, onStats, onLog, onPingStats
                )
                TestMode.IPV6_ONLY -> ipv6Stats = runProtocolWithPing(
                    config, IpProtocol.IPV6, expectedGeneration, pingIntervalMs, onStats, onLog, onPingStats
                )
                TestMode.IPV4_THEN_IPV6 -> {
                    ipv4Stats = runProtocolWithPing(
                        config.copy(mode = TestMode.IPV4_ONLY), IpProtocol.IPV4, expectedGeneration,
                        pingIntervalMs, onStats, onLog, onPingStats
                    )
                    currentCoroutineContext().ensureActive()
                    val releasedV4 = releaseHeld(IpProtocol.IPV4)
                    ipv4Stats = ipv4Stats?.copy(activeSessions = 0, phase = "已释放")
                    ipv4Stats?.let { onStats(it) }
                    if (releasedV4 > 0) {
                        onLog(LogLine(level = LogLevel.WARN, text = "IPv4 已释放 $releasedV4 条连接，切换 IPv6 测试"))
                    }
                    ipv6Stats = runProtocolWithPing(
                        config.copy(mode = TestMode.IPV6_ONLY), IpProtocol.IPV6, expectedGeneration,
                        pingIntervalMs, onStats, onLog, onPingStats
                    )
                }
            }
            ipv4Stats to ipv6Stats
        } catch (selectorClosed: ClosedSelectorException) {
            currentCoroutineContext().ensureActive()
            checkGeneration(expectedGeneration)
            throw selectorClosed
        } finally {
            if (!config.keepConnectionsAfterStop) {
                withContext(NonCancellable + Dispatchers.IO) { releaseHeld(null) }
            }
            synchronized(stateLock) {
                if (runningJob === ownerJob) runningJob = null
            }
        }
    }

    private suspend fun runProtocolWithPing(
        config: SessionConfig,
        protocol: IpProtocol,
        expectedGeneration: Long,
        pingIntervalMs: Long,
        onStats: suspend (ProtocolStats) -> Unit,
        onLog: suspend (LogLine) -> Unit,
        onPingStats: (suspend (PingStats) -> Unit)?
    ): ProtocolStats = coroutineScope {
        val pingTester = if (onPingStats != null) DesktopPingTester() else null
        val pingJob = pingTester?.let { tester ->
            launch {
                tester.runContinuousPing(
                    host = config.host,
                    port = config.port,
                    intervalMs = pingIntervalMs,
                    timeoutMs = config.timeoutMs,
                    protocol = protocol,
                    onStats = onPingStats!!,
                    onLog = onLog
                )
            }
        }
        try {
            runOneProtocol(config, protocol, expectedGeneration, onStats, onLog)
        } finally {
            withContext(NonCancellable) {
                pingTester?.stop()
                pingJob?.cancelAndJoin()
                pingTester?.close()
            }
        }
    }

    private suspend fun runOneProtocol(
        config: SessionConfig,
        protocol: IpProtocol,
        expectedGeneration: Long,
        onStats: suspend (ProtocolStats) -> Unit,
        onLog: suspend (LogLine) -> Unit
    ): ProtocolStats = withContext(Dispatchers.IO) {
        releaseHeld(protocol)
        currentCoroutineContext().ensureActive()
        checkGeneration(expectedGeneration)

        val startedAtNanos = System.nanoTime()
        val addresses = resolveInetAddresses(config.host, protocol)
        if (addresses.isEmpty()) {
            val stats = ProtocolStats(protocol = protocol, phase = "解析失败")
            onStats(stats)
            onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 域名未解析到有效地址"))
            return@withContext stats
        }

        val addressText = addresses.mapNotNull { it.hostAddress }.distinct()
        val targetCps = config.batchSize.coerceIn(1, 30_000)
        val selectIntervalMs = config.intervalMs.coerceIn(5L, 100L)
        val maxPending = maxOf(64, minOf(4_096, targetCps * 2, config.successLimit))
        val establishedThisRun = linkedSetOf<SocketChannel>()
        val pendingThisRun = linkedSetOf<SocketChannel>()
        var openedSelector: Selector? = null
        var runResourcesCleaned = false

        fun cleanupRunResources() {
            if (runResourcesCleaned) return
            runResourcesCleaned = true
            val selector = openedSelector ?: return
            val remainingPending = synchronized(stateLock) {
                pendingChannels.removeAll(pendingThisRun)
                if (runSelector === selector) runSelector = null
                pendingThisRun.toList()
            }
            remainingPending.forEach(::closeChannel)
            runCatching {
                selector.keys().forEach { it.cancel() }
                selector.selectNow()
            }
            runCatching { selector.close() }
            establishedThisRun.forEach { registerForMonitoring(it, protocol) }
        }

        try {
            val selector = Selector.open()
            openedSelector = selector
            registerRunSelector(selector, expectedGeneration)

            onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 解析成功：${addressText.joinToString(" / ")}"))
            onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 桌面端测试开始：目标速率 $targetCps CPS，上限 ${config.successLimit} 连接"))

        val errors = linkedMapOf<String, Int>()
        val readBuffer = ByteBuffer.allocateDirect(1_024)
        val pacer = CpsPacer(targetCps, monotonicMillis())
        var pendingCount = 0
        var totalSuccess = 0
        var totalFailure = 0
        var totalConnectLatencyMs = 0L
        var connectLatencySamples = 0
        var maxStable = 0
        var addressOffset = 0
        var lastSuccess = 0
        var lastStatsAtNanos = startedAtNanos
        var lastUiAtNanos = 0L
        var lastCps = 0
        var stats = ProtocolStats(
            protocol = protocol,
            phase = "建连中",
            resolvedAddresses = addressText,
            lastAdded = targetCps
        )
        onStats(stats)

        fun recordFailure(error: String) {
            totalFailure++
            errors[error] = (errors[error] ?: 0) + 1
        }

        fun removeHeld(channel: SocketChannel, error: String?) {
            val removed = synchronized(stateLock) { heldChannels.getValue(protocol).remove(channel) }
            establishedThisRun.remove(channel)
            closeChannel(channel)
            // A completed handshake followed by FIN is not another failed connection attempt.
            if (removed && error != null && generation.get() == expectedGeneration) {
                errors[error] = (errors[error] ?: 0) + 1
            }
        }

        fun discardPending(key: SelectionKey?, channel: SocketChannel): Boolean {
            key?.cancel()
            val removed = pendingThisRun.remove(channel)
            synchronized(stateLock) {
                pendingChannels.remove(channel)
                heldChannels.getValue(protocol).remove(channel)
            }
            establishedThisRun.remove(channel)
            if (removed) pendingCount--
            closeChannel(channel)
            return removed
        }

        fun finishConnected(key: SelectionKey, pending: PendingConnection, completedAtNanos: Long) {
            val channel = pending.channel
            try {
                // The channel remains pending until key setup and the locked ownership swap succeed.
                channel.socket().tcpNoDelay = true
                channel.socket().receiveBufferSize = 4_096
                channel.socket().sendBufferSize = 4_096
                checkGeneration(expectedGeneration)
                if (!channel.isOpen) throw SocketException("Socket closed during setup")
                key.attach(HeldConnection(channel))
                key.interestOps(SelectionKey.OP_READ)

                val transferred = synchronized(stateLock) {
                    if (generation.get() == expectedGeneration && channel.isOpen && pendingChannels.remove(channel)) {
                        heldChannels.getValue(protocol).add(channel)
                        true
                    } else {
                        false
                    }
                }
                if (!transferred) throw CancellationException("TCP test stopped")
                check(pendingThisRun.remove(channel)) { "Pending connection ownership was lost" }
                pendingCount--
                establishedThisRun += channel
                totalSuccess++
                val latency = elapsedMillis(pending.startedAtNanos, completedAtNanos)
                totalConnectLatencyMs += latency
                connectLatencySamples++
            } catch (cancelled: CancellationException) {
                discardPending(key, channel)
                throw cancelled
            } catch (t: Throwable) {
                discardPending(key, channel)
                if (generation.get() != expectedGeneration) throw CancellationException("TCP test stopped")
                recordFailure(classifyError(t))
            }
        }

        fun openOne(address: InetAddress, nowNanos: Long) {
            var channel: SocketChannel? = null
            try {
                checkGeneration(expectedGeneration)
                channel = SocketChannel.open(familyOf(address))
                channel.configureBlocking(false)
                channel.socket().reuseAddress = true
                channel.socket().tcpNoDelay = true
                registerPending(channel, pendingThisRun, expectedGeneration)
                val pending = PendingConnection(
                    channel = channel,
                    startedAtNanos = nowNanos,
                    deadlineNanos = nowNanos + config.timeoutMs.toLong() * 1_000_000L
                )
                pendingCount++
                val connected = channel.connect(InetSocketAddress(address, config.port))
                val key = channel.register(selector, if (connected) SelectionKey.OP_READ else SelectionKey.OP_CONNECT, pending)
                if (connected) finishConnected(key, pending, System.nanoTime())
            } catch (cancelled: CancellationException) {
                channel?.let { discardPending(null, it) }
                throw cancelled
            } catch (t: Throwable) {
                channel?.let { discardPending(null, it) }
                if (generation.get() != expectedGeneration) throw CancellationException("TCP test stopped")
                recordFailure(classifyError(t))
            }
        }

        fun processSelectedKeys() {
            val iterator = selector.selectedKeys().iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                iterator.remove()
                try {
                    if (!key.isValid) continue
                    when (val attachment = key.attachment()) {
                        is PendingConnection -> if (key.isConnectable) {
                            if (attachment.channel.finishConnect()) {
                                finishConnected(key, attachment, System.nanoTime())
                            }
                        }
                        is HeldConnection -> if (key.isReadable) {
                            readBuffer.clear()
                            val read = attachment.channel.read(readBuffer)
                            if (read < 0) removeHeld(attachment.channel, "远端关闭")
                        }
                    }
                } catch (_: CancelledKeyException) {
                    // stop/release owns this close; cancellation is not a failed attempt.
                } catch (t: Throwable) {
                    when (val attachment = key.attachment()) {
                        is PendingConnection -> {
                            val owned = discardPending(key, attachment.channel)
                            if (owned && generation.get() == expectedGeneration) recordFailure(classifyError(t))
                        }
                        is HeldConnection -> removeHeld(attachment.channel, classifyError(t))
                    }
                    key.cancel()
                }
            }
        }

        fun expirePending(nowNanos: Long) {
            selector.keys().toList().forEach { key ->
                val pending = key.attachment() as? PendingConnection ?: return@forEach
                if (!key.isValid || !pending.channel.isOpen) return@forEach
                if (nowNanos >= pending.deadlineNanos) {
                    if (discardPending(key, pending.channel)) recordFailure("超时")
                }
            }
        }

        try {
            while (currentCoroutineContext().isActive && generation.get() == expectedGeneration) {
                selector.select(selectIntervalMs)
                currentCoroutineContext().ensureActive()
                checkGeneration(expectedGeneration)
                processSelectedKeys()
                val nowNanos = System.nanoTime()
                expirePending(nowNanos)

                val active = activeCount(protocol)
                maxStable = maxOf(maxStable, active)
                if (totalFailure >= config.failureLimit || totalSuccess >= config.successLimit) break

                val capacity = minOf(maxPending - pendingCount, config.successLimit - totalSuccess - pendingCount)
                val permits = pacer.permits(nowNanos / 1_000_000L, capacity)
                repeat(permits) {
                    openOne(addresses[addressOffset++ % addresses.size], System.nanoTime())
                }

                if (lastUiAtNanos == 0L || nowNanos - lastUiAtNanos >= 200_000_000L) {
                    val elapsedNanos = (nowNanos - lastStatsAtNanos).coerceAtLeast(1L)
                    lastCps = (((totalSuccess - lastSuccess).toLong() * 1_000_000_000L) / elapsedNanos)
                        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                    lastSuccess = totalSuccess
                    lastStatsAtNanos = nowNanos
                    lastUiAtNanos = nowNanos
                    stats = stats.copy(
                        phase = "建连中",
                        activeSessions = active,
                        totalSuccess = totalSuccess,
                        totalFailure = totalFailure,
                        totalAttempts = totalSuccess + totalFailure,
                        lastAdded = targetCps,
                        cps = lastCps,
                        maxStableSessions = maxStable,
                        averageConnectLatencyMs = averageLatency(totalConnectLatencyMs, connectLatencySamples),
                        errorSummary = errors.toMap()
                    )
                    onStats(stats)
                }
            }
        } finally {
            cleanupRunResources()
            if (!currentCoroutineContext().isActive) {
                withContext(NonCancellable) {
                    val finalActive = activeCount(protocol)
                    onStats(stats.copy(
                        phase = "已停止", activeSessions = finalActive,
                        totalSuccess = totalSuccess, totalFailure = totalFailure,
                        totalAttempts = totalSuccess + totalFailure, cps = 0,
                        maxStableSessions = maxOf(maxStable, finalActive),
                        averageConnectLatencyMs = averageLatency(totalConnectLatencyMs, connectLatencySamples),
                        errorSummary = errors.toMap()
                    ))
                }
            }
        }

        val active = activeCount(protocol)
        maxStable = maxOf(maxStable, active)
        val totalElapsedNanos = (System.nanoTime() - startedAtNanos).coerceAtLeast(1L)
        val avgCps = (totalSuccess.toLong() * 1_000_000_000L / totalElapsedNanos)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val finalPhase = when {
            totalSuccess >= config.successLimit -> if (active < totalSuccess) "测试完成，部分连接已关闭" else "测试完成"
            totalFailure >= config.failureLimit -> "失败上限"
            totalFailure > 0 -> "出现失败"
            else -> "已停止"
        }
        val finalStats = stats.copy(
            phase = finalPhase,
            activeSessions = active,
            totalSuccess = totalSuccess,
            totalFailure = totalFailure,
            totalAttempts = totalSuccess + totalFailure,
            cps = avgCps,
            maxStableSessions = maxStable,
            averageConnectLatencyMs = averageLatency(totalConnectLatencyMs, connectLatencySamples),
            errorSummary = errors.toMap()
        )
        onStats(finalStats)
        val logLevel = if (totalFailure >= config.failureLimit) LogLevel.ERROR else LogLevel.SUCCESS
        onLog(LogLine(level = logLevel, text = "${protocol.label} $finalPhase：峰值活动 $maxStable | 当前活动 $active | 成功 $totalSuccess | 失败 $totalFailure | 平均速率 $avgCps/s"))
        if (totalFailure >= config.failureLimit) {
            onLog(LogLine(level = LogLevel.WARN, text = "提示：若压测目标为公网站点 (如百度/腾讯)，持续高并发会触发对端防火墙防 SYN 洪水/CC 策略从而拦截丢包。压测建议对准局域网网关或具备授权的压测服务器。"))
        }

        if (!config.keepConnectionsAfterStop) {
            val released = releaseHeld(protocol)
            onStats(finalStats.copy(activeSessions = 0, phase = "已释放"))
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 连接已全部释放 ($released)"))
        }
        finalStats
        } finally {
            // Includes selector registration and suspend callbacks during setup.
            cleanupRunResources()
        }
    }

    /** Cancels the active run and closes all connecting sockets before returning. */
    fun stop() {
        generation.incrementAndGet()
        val job: Job?
        val selector: Selector?
        val pending: List<SocketChannel>
        synchronized(stateLock) {
            job = runningJob
            runningJob = null
            selector = runSelector
            runSelector = null
            pending = pendingChannels.toList()
            pendingChannels.clear()
        }
        job?.cancel(CancellationException("TCP test stopped"))
        runCatching { selector?.close() }
        pending.forEach(::closeChannel)
    }

    suspend fun release(protocol: IpProtocol? = null): Int = withContext(Dispatchers.IO) {
        releaseHeld(protocol)
    }

    /** Returns the currently owned, open sessions. Remote closes are pruned by the monitor. */
    fun activeCount(protocol: IpProtocol): Int = synchronized(stateLock) {
        val channels = heldChannels.getValue(protocol)
        channels.removeAll { !it.isOpen }
        channels.size
    }

    /** Stops the run, releases every held session, and destroys the liveness monitor. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stop()
        releaseHeld(null)
        monitorScope.cancel()
        runCatching { monitorSelector.wakeup() }
        runCatching { monitorSelector.close() }
    }

    private suspend fun monitorHeldConnections() {
        val buffer = ByteBuffer.allocateDirect(1_024)
        try {
            while (currentCoroutineContext().isActive && !closed.get()) {
                while (true) {
                    val registration = monitorRegistrations.poll() ?: break
                    if (registration.channel.isOpen && isHeld(registration.channel, registration.protocol)) {
                        runCatching {
                            registration.channel.register(
                                monitorSelector,
                                SelectionKey.OP_READ,
                                registration
                            )
                        }.onFailure { removeMonitored(registration) }
                    }
                }
                monitorSelector.select(250L)
                val iterator = monitorSelector.selectedKeys().iterator()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    iterator.remove()
                    val registration = key.attachment() as? HeldRegistration ?: continue
                    try {
                        if (!key.isValid || !key.isReadable) continue
                        buffer.clear()
                        if (registration.channel.read(buffer) < 0) {
                            key.cancel()
                            removeMonitored(registration)
                        }
                    } catch (_: Throwable) {
                        key.cancel()
                        removeMonitored(registration)
                    }
                }
            }
        } catch (_: CancellationException) {
            // Normal close.
        } catch (_: Throwable) {
            // close() may terminate select by closing the selector.
        }
    }

    private fun registerRunSelector(selector: Selector, expectedGeneration: Long) {
        synchronized(stateLock) {
            if (closed.get() || generation.get() != expectedGeneration) {
                selector.close()
                throw CancellationException("TCP test stopped")
            }
            runSelector = selector
        }
    }

    private fun registerPending(
        channel: SocketChannel,
        pendingThisRun: MutableSet<SocketChannel>,
        expectedGeneration: Long
    ) {
        synchronized(stateLock) {
            if (closed.get() || generation.get() != expectedGeneration) {
                closeChannel(channel)
                throw CancellationException("TCP test stopped")
            }
            pendingChannels += channel
            pendingThisRun += channel
        }
    }

    private fun registerForMonitoring(channel: SocketChannel, protocol: IpProtocol) {
        if (!channel.isOpen || !isHeld(channel, protocol)) return
        monitorRegistrations += HeldRegistration(channel, protocol)
        runCatching { monitorSelector.wakeup() }
    }

    private fun removeMonitored(registration: HeldRegistration) {
        synchronized(stateLock) { heldChannels.getValue(registration.protocol).remove(registration.channel) }
        closeChannel(registration.channel)
    }

    private fun isHeld(channel: SocketChannel, protocol: IpProtocol): Boolean =
        synchronized(stateLock) { channel in heldChannels.getValue(protocol) }

    private fun releaseHeld(protocol: IpProtocol?): Int {
        val channels = synchronized(stateLock) {
            val targets = if (protocol == null) IpProtocol.entries else listOf(protocol)
            buildList {
                targets.forEach { target ->
                    addAll(heldChannels.getValue(target))
                    heldChannels.getValue(target).clear()
                }
            }
        }
        channels.forEach(::closeChannel)
        runCatching { monitorSelector.wakeup() }
        return channels.size
    }

    private suspend fun resolveInetAddresses(host: String, protocol: IpProtocol): List<InetAddress> =
        withContext(Dispatchers.IO) {
            runCatching {
                InetAddress.getAllByName(cleanHost(host)).filter { address ->
                    when (protocol) {
                        IpProtocol.IPV4 -> address is Inet4Address
                        IpProtocol.IPV6 -> address is Inet6Address
                    }
                }.distinctBy { it.hostAddress }.take(8)
            }.getOrDefault(emptyList())
        }

    private fun checkGeneration(expectedGeneration: Long) {
        if (closed.get() || generation.get() != expectedGeneration) {
            throw CancellationException("TCP test stopped")
        }
    }

    private fun cleanHost(host: String): String =
        host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }

    private fun familyOf(address: InetAddress) = when (address) {
        is Inet4Address -> StandardProtocolFamily.INET
        is Inet6Address -> StandardProtocolFamily.INET6
        else -> error("Unsupported address family")
    }

    private fun classifyError(error: Throwable): String {
        val message = error.message.orEmpty().lowercase()
        return when (error) {
            is SocketTimeoutException -> "超时"
            is ConnectException -> when {
                "refused" in message -> "拒绝"
                "timed out" in message -> "超时"
                else -> "连接失败"
            }
            is NoRouteToHostException -> "无路由"
            is PortUnreachableException -> "端口不可达"
            is UnknownHostException -> "DNS失败"
            is SocketException -> when {
                "too many open files" in message || "emfile" in message -> "FD上限"
                "cannot assign requested address" in message -> "端口耗尽"
                "connection reset" in message -> "重置"
                else -> "Socket异常"
            }
            else -> error.javaClass.simpleName
        }
    }

    private fun averageLatency(total: Long, count: Int): Int =
        if (count == 0) 0 else (total / count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun elapsedMillis(startNanos: Long, endNanos: Long): Int =
        ((endNanos - startNanos).coerceAtLeast(0L) / 1_000_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L

    private fun closeChannel(channel: SocketChannel) {
        runCatching { channel.socket().setSoLinger(true, 0) }
        runCatching { channel.close() }
    }

    private data class PendingConnection(
        val channel: SocketChannel,
        val startedAtNanos: Long,
        val deadlineNanos: Long
    )

    private data class HeldConnection(val channel: SocketChannel)
    private data class HeldRegistration(val channel: SocketChannel, val protocol: IpProtocol)
}
