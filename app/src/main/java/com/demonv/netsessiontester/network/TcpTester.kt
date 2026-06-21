package com.demonv.netsessiontester.network

import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.ResolveResult
import com.demonv.netsessiontester.model.SessionConfig
import com.demonv.netsessiontester.model.TestMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

class TcpTester {
    private val socketLock = Mutex()

    /**
     * Keep a small FD reserve so Android / ART / Compose / logging still have room
     * to open files, Binder fds and sockets while the test is near the device limit.
     * Without this reserve the process may crash before Java throws a clean
     * "Too many open files" SocketException.
     */
    private val fdReserve = 192

    /** Low memory reserve. If free heap is lower than this, stop before OOM. */
    private val memoryReserveBytes = 48L * 1024L * 1024L

    private val heldSockets: MutableMap<IpProtocol, MutableList<Socket>> = mutableMapOf(
        IpProtocol.IPV4 to Collections.synchronizedList(mutableListOf()),
        IpProtocol.IPV6 to Collections.synchronizedList(mutableListOf())
    )

    /**
     * Release generation. Any openBatch that completes after a release must be discarded.
     * This prevents the UI/log from saying "released" while late sockets are added back.
     */
    private val releaseEpoch = AtomicLong(0L)

    suspend fun resolveHost(host: String): ResolveResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
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
        onLog: suspend (LogLine) -> Unit
    ): Pair<ProtocolStats?, ProtocolStats?> {
        val config = rawConfig.normalized()
        var ipv4Stats: ProtocolStats? = null
        var ipv6Stats: ProtocolStats? = null

        when (config.mode) {
            TestMode.IPV4_ONLY -> ipv4Stats = runOneProtocol(config, IpProtocol.IPV4, onStats, onLog)
            TestMode.IPV6_ONLY -> ipv6Stats = runOneProtocol(config, IpProtocol.IPV6, onStats, onLog)
            TestMode.IPV4_THEN_IPV6 -> {
                ipv4Stats = runOneProtocol(config.copy(mode = TestMode.IPV4_ONLY), IpProtocol.IPV4, onStats, onLog)
                release(IpProtocol.IPV4)
                delay(1200)
                ipv6Stats = runOneProtocol(config.copy(mode = TestMode.IPV6_ONLY), IpProtocol.IPV6, onStats, onLog)
            }
        }
        return ipv4Stats to ipv6Stats
    }

    private suspend fun runOneProtocol(
        config: SessionConfig,
        protocol: IpProtocol,
        onStats: suspend (ProtocolStats) -> Unit,
        onLog: suspend (LogLine) -> Unit
    ): ProtocolStats {
        release(protocol)
        val protocolEpoch = releaseEpoch.get()
        val addresses = resolveInetAddresses(config.host, protocol)
        if (addresses.isEmpty()) {
            val stats = ProtocolStats(protocol = protocol, phase = "解析失败")
            onStats(stats)
            onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 没有解析到可用地址"))
            return stats
        }

        val addressText = addresses.mapNotNull { it.hostAddress }.distinct()
        onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 解析成功：${addressText.joinToString(" / ")}"))

        var totalSuccess = 0
        var totalFailure = 0
        var totalAttempts = 0
        var maxStable = 0
        var lastTotalAttempts = 0
        var lastStatsAt = System.currentTimeMillis()
        val errors = linkedMapOf<String, Int>()
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText)
        var fdSeen = false

        // CPS 自适应调速：10 CPS 只作为短时保护，不作为长期巡航速度。
        // 普通情况下按用户设置的 batch/interval 运行；连续无成功才短暂降速，
        // 只要仍有成功连接，就会阶梯恢复，避免长时间卡在 10 CPS。
        val normalCps = ((config.batchSize * 1000L) / config.intervalMs).toInt().coerceIn(1, 2_000)
        var cpsCap = normalCps
        var consecutiveNoSuccessBatches = 0
        var lowCpsStartedAt = 0L
        var lastCpsRecoverAt = 0L

        fun cpsCapLaunchLimit(): Int {
            return ((cpsCap.toLong() * config.intervalMs + 999L) / 1000L).toInt().coerceAtLeast(1)
        }

        fun nextRecoveryCps(current: Int): Int = when {
            current <= 10 -> 30
            current < 60 -> 60
            current < 100 -> 100
            current < 150 -> 150
            else -> (current * 2).coerceAtMost(normalCps)
        }.coerceAtMost(normalCps).coerceAtLeast(1)

        onStats(stats)

        fun recordFailure(key: String) {
            if (key == "FD上限" || key == "资源保护") fdSeen = true
            if (totalFailure >= config.failureLimit) return
            totalFailure++
            errors[key] = (errors[key] ?: 0) + 1
        }

        suspend fun emitStats(forcePhase: String? = null) {
            val now = System.currentTimeMillis()
            val active = activeCount(protocol)
            maxStable = maxOf(maxStable, active)
            totalAttempts = totalSuccess + totalFailure
            val added = totalAttempts - lastTotalAttempts
            val elapsedMs = (now - lastStatsAt).coerceAtLeast(1L)
            lastTotalAttempts = totalAttempts
            lastStatsAt = now
            val cps = if (added <= 0) 0 else (added * 1000L / elapsedMs).toInt()
            stats = ProtocolStats(
                protocol = protocol,
                phase = forcePhase ?: "建连中",
                resolvedAddresses = addressText,
                activeSessions = active,
                totalFailure = totalFailure,
                totalAttempts = totalAttempts,
                lastAdded = added,
                cps = cps,
                errorSummary = errors.toMap(),
                totalSuccess = totalSuccess,
                maxStableSessions = maxStable
            )
            onStats(stats)
            if (added > 0 || forcePhase != null) {
                onLog(LogLine(level = LogLevel.STAT, text = "${protocol.label} 统计 - 成功：$totalSuccess | 失败：$totalFailure | 活动：$active | 总计：$totalAttempts | 新增：$added | CPS：${cps}/秒"))
            }
        }

        while (currentCoroutineContext().isActive && releaseEpoch.get() == protocolEpoch && totalSuccess < config.successLimit && totalFailure < config.failureLimit) {
            val remainingSuccess = config.successLimit - totalSuccess
            val remainingFailure = config.failureLimit - totalFailure
            if (remainingSuccess <= 0 || remainingFailure <= 0) break

            // 失败上限是硬终止；调速只负责保护速度，不能替代失败上限停止。
            if (totalFailure >= config.failureLimit) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到失败上限：${config.failureLimit}，已停止新增"))
                break
            }

            // 速度优先：没有接近失败上限时按用户设定批量并发。
            // 接近失败上限时按剩余失败值收缩，避免一次批量把失败数冲过头。
            val nearFailureCap = totalFailure >= (config.failureLimit * 8 / 10)
            val baseLaunchCount = if (nearFailureCap) {
                minOf(config.batchSize, remainingSuccess, remainingFailure)
            } else {
                minOf(config.batchSize, remainingSuccess)
            }
            val requestedLaunchCount = minOf(baseLaunchCount, cpsCapLaunchLimit())
            if (requestedLaunchCount <= 0) break

            // FD / heap hard guard. Do not wait for EMFILE/OOM. Stop while the
            // process still has enough reserve to update UI, save history and close sockets.
            val pressure = resourcePressure(requestedLaunchCount)
            if (pressure.stopNow) {
                repeat(minOf(remainingFailure, 1)) { recordFailure(pressure.reason) }
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} ${pressure.reason}，已触发保护停止：${pressure.detail}"))
                fdSeen = true
                break
            }
            val launchCount = minOf(requestedLaunchCount, pressure.allowedLaunch.coerceAtLeast(1))

            val batch = openBatch(addresses, config.port, config.timeoutMs.coerceIn(300, 800), launchCount, protocolEpoch)
            if (!currentCoroutineContext().isActive || releaseEpoch.get() != protocolEpoch) {
                batch.successSockets.forEach { socket -> runCatching { socket.close() } }
                break
            }
            val batchSuccess = batch.successSockets.size
            val failureBeforeBatch = totalFailure
            totalSuccess += batchSuccess
            socketLock.withLock {
                if (releaseEpoch.get() == protocolEpoch) {
                    heldSockets.getValue(protocol).addAll(batch.successSockets)
                } else {
                    batch.successSockets.forEach { socket -> runCatching { socket.close() } }
                }
            }
            batch.errors.forEach { (key, value) ->
                repeat(value) {
                    if (totalFailure < config.failureLimit) recordFailure(key)
                }
            }
            val batchFailure = totalFailure - failureBeforeBatch
            if (batch.errors.keys.any { it == "FD上限" || it == "资源保护" }) {
                fdSeen = true
            }
            totalAttempts = totalSuccess + totalFailure

            // CPS 调速：连续无成功才短暂降到 10 CPS；10 CPS 最多保护约 5 秒，
            // 如果仍有成功连接则 10 → 30 → 60 → 100 → 150 → 正常速度阶梯恢复。
            val nowForCps = System.currentTimeMillis()
            if (batchSuccess <= 0 && batchFailure > 0) {
                consecutiveNoSuccessBatches++
            } else if (batchSuccess > 0) {
                consecutiveNoSuccessBatches = 0
            }

            if (consecutiveNoSuccessBatches >= 2 && cpsCap > 10) {
                cpsCap = minOf(10, normalCps)
                lowCpsStartedAt = nowForCps
                lastCpsRecoverAt = nowForCps
                onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 连续批次无成功，短时降速到 ${cpsCap} CPS"))
            } else if (cpsCap < normalCps) {
                val lowCpsTooLong = lowCpsStartedAt > 0L && nowForCps - lowCpsStartedAt >= 5_000L
                val hasSuccessAgain = batchSuccess > 0
                if ((hasSuccessAgain || lowCpsTooLong) && nowForCps - lastCpsRecoverAt >= 3_000L) {
                    val oldCps = cpsCap
                    cpsCap = nextRecoveryCps(cpsCap)
                    lastCpsRecoverAt = nowForCps
                    if (cpsCap != oldCps) {
                        onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} CPS 恢复：$oldCps → $cpsCap"))
                    }
                }
            }

            emitStats()

            if (fdSeen) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 检测到 Android FD上限，已停止新增并保存历史"))
                break
            }
            if (totalFailure >= config.failureLimit) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到失败上限：${config.failureLimit}，硬终止并进入统一释放"))
                break
            }
            delay(config.intervalMs)
        }

        val finalActive = activeCount(protocol)
        val finalPhase = when {
            fdSeen -> "FD上限"
            totalFailure >= config.failureLimit -> "失败上限"
            else -> "测试完成"
        }
        val finalStats = stats.copy(
            phase = finalPhase,
            activeSessions = finalActive,
            maxStableSessions = maxOf(maxStable, finalActive),
            totalSuccess = totalSuccess,
            totalFailure = totalFailure.coerceAtMost(config.failureLimit),
            totalAttempts = totalSuccess + totalFailure.coerceAtMost(config.failureLimit),
            errorSummary = errors.toMap()
        )
        onStats(finalStats)
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} ${finalStats.phase} - 活动：${finalStats.activeSessions} 失败：${finalStats.totalFailure} 总计：${finalStats.totalAttempts}"))

        if (!config.keepConnectionsAfterStop) {
            release(protocol)
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 已按设置释放连接。"))
        }
        return finalStats
    }

    private suspend fun openBatch(
        addresses: List<InetAddress>,
        port: Int,
        timeoutMs: Int,
        count: Int,
        expectedEpoch: Long
    ): BatchOpenResult = coroutineScope {
        if (releaseEpoch.get() != expectedEpoch) return@coroutineScope BatchOpenResult(emptyList(), emptyMap())
        try {
            val safeCount = count.coerceIn(1, 512)
            val jobs = (0 until safeCount).map { index ->
                async(Dispatchers.IO) {
                    if (releaseEpoch.get() != expectedEpoch) {
                        OpenResult(discarded = true)
                    } else {
                        val address = addresses[index % addresses.size]
                        openOne(address, port, timeoutMs, expectedEpoch)
                    }
                }
            }
            val results = jobs.awaitAll()
            val sockets = mutableListOf<Socket>()
            val errors = linkedMapOf<String, Int>()
            results.forEach { result ->
                result.socket?.let { sockets += it }
                result.error?.let { errors[it] = (errors[it] ?: 0) + 1 }
            }
            BatchOpenResult(sockets, errors)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Last-chance guard for rare native/ART pressure around FD limit.
            BatchOpenResult(emptyList(), mapOf(classifyError(t) to 1))
        }
    }

    private fun openOne(address: InetAddress, port: Int, timeoutMs: Int, expectedEpoch: Long): OpenResult {
        var socket: Socket? = null
        return try {
            if (releaseEpoch.get() != expectedEpoch) return OpenResult(discarded = true)
            socket = Socket()
            socket.keepAlive = true
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            if (releaseEpoch.get() != expectedEpoch) {
                runCatching { socket.close() }
                OpenResult(discarded = true)
            } else {
                OpenResult(socket = socket)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            runCatching { socket?.close() }
            OpenResult(error = classifyError(t))
        }
    }

    private fun classifyError(e: Throwable): String {
        val message = e.message.orEmpty().lowercase()
        return when (e) {
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
                "network is unreachable" in message -> "网络不可达"
                "connection reset" in message -> "重置"
                "broken pipe" in message -> "已断开"
                else -> "Socket异常"
            }
            is OutOfMemoryError -> "资源保护"
            else -> e.javaClass.simpleName
        }
    }

    private fun currentFdCount(): Int {
        return runCatching { File("/proc/self/fd").list()?.size ?: -1 }.getOrDefault(-1)
    }

    private fun maxOpenFiles(): Int {
        return runCatching {
            File("/proc/self/limits").readLines()
                .firstOrNull { it.startsWith("Max open files") }
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.getOrNull(3)
                ?.toIntOrNull()
        }.getOrNull() ?: 32768
    }

    private fun availableHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    }

    private fun resourcePressure(requestedLaunch: Int): ResourcePressure {
        val fdNow = currentFdCount()
        val fdMax = maxOpenFiles()
        val heapFree = availableHeapBytes()
        val fdSafeMax = (fdMax - fdReserve).coerceAtLeast(128)

        if (heapFree in 1 until memoryReserveBytes) {
            return ResourcePressure(
                stopNow = true,
                allowedLaunch = 0,
                reason = "资源保护",
                detail = "可用堆内存约 ${heapFree / 1024 / 1024}MB，低于保护阈值 ${memoryReserveBytes / 1024 / 1024}MB"
            )
        }

        if (fdNow > 0) {
            val remaining = fdSafeMax - fdNow
            if (remaining <= 0) {
                return ResourcePressure(
                    stopNow = true,
                    allowedLaunch = 0,
                    reason = "FD上限",
                    detail = "当前FD=$fdNow，上限=$fdMax，预留=$fdReserve"
                )
            }
            val allowed = remaining.coerceAtMost(requestedLaunch)
            if (allowed <= 0) {
                return ResourcePressure(
                    stopNow = true,
                    allowedLaunch = 0,
                    reason = "FD上限",
                    detail = "当前FD=$fdNow，上限=$fdMax，预留=$fdReserve"
                )
            }
            return ResourcePressure(
                stopNow = false,
                allowedLaunch = allowed,
                reason = "",
                detail = "当前FD=$fdNow，上限=$fdMax，预留=$fdReserve"
            )
        }

        return ResourcePressure(stopNow = false, allowedLaunch = requestedLaunch, reason = "", detail = "FD未知")
    }

    private suspend fun resolveInetAddresses(host: String, protocol: IpProtocol): List<InetAddress> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        InetAddress.getAllByName(cleanHost).filter { address ->
            when (protocol) {
                IpProtocol.IPV4 -> address is Inet4Address
                IpProtocol.IPV6 -> address is Inet6Address
            }
        }.distinctBy { it.hostAddress }
    }

    /**
     * Snapshot and clear held sockets immediately.
     *
     * Callers can update UI to "已释放" right after this returns, then close the
     * returned snapshot in the background. releaseEpoch is incremented first so
     * late sockets opened by an old batch are closed and never added back.
     */
    suspend fun detachForRelease(protocol: IpProtocol? = null): List<Socket> = withContext(Dispatchers.IO) {
        releaseEpoch.incrementAndGet()
        val targets = if (protocol == null) IpProtocol.entries else listOf(protocol)
        val socketsToClose = mutableListOf<Socket>()
        socketLock.withLock {
            targets.forEach { p ->
                val list = heldSockets.getValue(p)
                socketsToClose += list.toList()
                list.clear()
            }
        }
        socketsToClose
    }

    suspend fun closeDetachedSockets(socketsToClose: List<Socket>): Int = withContext(Dispatchers.IO) {
        if (socketsToClose.isEmpty()) return@withContext 0
        coroutineScope {
            socketsToClose.chunked(256).map { chunk ->
                async(Dispatchers.IO) {
                    var closed = 0
                    chunk.forEach { socket ->
                        runCatching { socket.close() }
                        closed++
                    }
                    closed
                }
            }.awaitAll().sum()
        }
    }

    suspend fun release(protocol: IpProtocol? = null): Int {
        val snapshot = detachForRelease(protocol)
        return closeDetachedSockets(snapshot)
    }

    suspend fun activeCount(protocol: IpProtocol): Int = withContext(Dispatchers.IO) {
        socketLock.withLock {
            val list = heldSockets.getValue(protocol)
            list.removeAll { it.isClosed }
            list.size
        }
    }

    private data class ResourcePressure(
        val stopNow: Boolean,
        val allowedLaunch: Int,
        val reason: String,
        val detail: String
    )

    private data class OpenResult(val socket: Socket? = null, val error: String? = null, val discarded: Boolean = false)
    private data class BatchOpenResult(val successSockets: List<Socket>, val errors: Map<String, Int>)
}
