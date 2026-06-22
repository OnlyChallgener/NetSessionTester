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
import kotlinx.coroutines.yield
import java.io.File
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
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class TcpTester {
    private val socketLock = Mutex()

    /**
     * Android 常见 FD 上限约 32760/32768。
     * build63 实测在 32147 附近进入长尾，继续低 CPS 重试会拖到 200s+。
     * 这里增加“活动连接保护线”：到 32000 直接按设备 FD 上限保护收尾，
     * 给 Compose、日志、DNS、通知和释放流程预留更大余量，避免 UI 不同步或闪退。
     */
    private val fdActiveGuardStop = 32_000
    private val fdHardStop = 32_300
    private val fdReserve = 468

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
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 智能策略：起始CPS ${config.batchSize}，快速加速，顶部确认硬止损，活动连接 32000 保护"))

        var totalSuccess = 0
        var totalFailure = 0
        var totalAttempts = 0
        var maxStable = 0
        var consecutiveFailure = 0
        var lastTotalAttempts = 0
        var lastStatsAt = System.currentTimeMillis()
        var lastLogAt = 0L
        var lastGrowthAt = System.currentTimeMillis()
        var lastMeaningfulPeak = 0
        var topConfirmStartedAt = 0L
        var topConfirmStartPeak = 0
        var topConfirmStartFailure = 0
        var currentCps = config.batchSize.coerceIn(40, 1500)
        var fdSeen = false
        var stopPhase: String? = null
        val errors = linkedMapOf<String, Int>()
        val samples = ArrayDeque<WindowSample>()
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText)
        onStats(stats)

        fun dynamicConsecutiveLimit(peak: Int): Int = ((peak * 0.012f).roundToInt()).coerceIn(25, 120)
        fun dynamicTotalLimit(peak: Int): Int = ((peak * 0.015f).roundToInt()).coerceIn(80, 400)
        fun topFailureBudget(peak: Int): Int = when {
            peak < 3_000 -> 25
            peak < 10_000 -> 50
            else -> 90
        }
        fun cpsCap(peak: Int): Int = when {
            peak < 2_000 -> 300
            peak < 6_000 -> 600
            peak < 10_000 -> 900
            else -> 1200
        }
        fun meaningfulGrowthStep(peak: Int): Int = when {
            peak < 3_000 -> 12
            peak < 10_000 -> 40
            else -> 120
        }
        fun noGrowthWindowMs(peak: Int): Long = when {
            peak < 3_000 -> 4_000L
            peak < 10_000 -> 6_000L
            else -> 6_000L
        }
        fun noGrowthFailThreshold(peak: Int): Float = when {
            peak < 3_000 -> 0.55f
            peak < 10_000 -> 0.60f
            else -> 0.35f
        }
        fun topConfirmMs(peak: Int): Long = when {
            peak < 3_000 -> 2_500L
            peak < 10_000 -> 3_500L
            else -> 4_000L
        }
        fun enterTopConfirm(now: Long, peak: Int, reason: String) {
            if (topConfirmStartedAt == 0L) {
                topConfirmStartedAt = now
                topConfirmStartPeak = peak
                topConfirmStartFailure = totalFailure
                currentCps = currentCps.coerceAtMost(20).coerceAtLeast(8)
                // 顶部确认只写一次日志，避免高频刷屏。
                // forceLog 仍由 emitStats 统一控制 1 秒节流。
            }
        }
        fun addSample(success: Int, failure: Int) {
            val now = System.currentTimeMillis()
            samples.addLast(WindowSample(now, success, failure))
            while (samples.isNotEmpty() && now - samples.first().timeMs > 12_500L) samples.removeFirst()
        }
        fun windowStats(ms: Long): Pair<Int, Int> {
            val now = System.currentTimeMillis()
            var s = 0
            var f = 0
            samples.forEach { sample ->
                if (now - sample.timeMs <= ms) {
                    s += sample.success
                    f += sample.failure
                }
            }
            return s to f
        }
        fun recordFailure(key: String) {
            totalFailure++
            consecutiveFailure++
            errors[key] = (errors[key] ?: 0) + 1
            if (key == "FD上限" || key == "资源保护") fdSeen = true
        }

        suspend fun emitStats(forcePhase: String? = null, forceLog: Boolean = false) {
            val now = System.currentTimeMillis()
            val active = activeCount(protocol)
            maxStable = maxOf(maxStable, active)
            totalAttempts = totalSuccess + totalFailure
            val added = totalAttempts - lastTotalAttempts
            val elapsedMs = (now - lastStatsAt).coerceAtLeast(1L)
            val cps = if (added <= 0) 0 else (added * 1000L / elapsedMs).toInt()
            lastTotalAttempts = totalAttempts
            lastStatsAt = now
            stats = ProtocolStats(
                protocol = protocol,
                phase = forcePhase ?: stopPhase ?: "建连中",
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
            if (forceLog || now - lastLogAt >= 1_000L) {
                lastLogAt = now
                onLog(
                    LogLine(
                        level = LogLevel.STAT,
                        text = "${protocol.label} 统计 - 成功：$totalSuccess | 失败：$totalFailure | 活动：$active | 峰值：$maxStable | 新增：$added | CPS：${cps}/秒 | 策略CPS：$currentCps"
                    )
                )
            }
        }

        while (currentCoroutineContext().isActive && releaseEpoch.get() == protocolEpoch && totalSuccess < config.successLimit) {
            val now = System.currentTimeMillis()
            val activeBefore = activeCount(protocol)
            val peak = maxOf(maxStable, activeBefore)
            val consecutiveLimit = dynamicConsecutiveLimit(peak)
            val totalLimit = minOf(dynamicTotalLimit(peak), config.failureLimit.coerceAtLeast(80))
            val (windowSuccess, windowFailure) = windowStats(noGrowthWindowMs(peak))
            val windowTotal = windowSuccess + windowFailure
            val failRate = if (windowTotal <= 0) 0f else windowFailure.toFloat() / windowTotal.toFloat()

            if (activeBefore >= fdActiveGuardStop) {
                fdSeen = true
                stopPhase = "FD上限"
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 活动连接已到 $activeBefore，达到 Android FD 保护线 $fdActiveGuardStop，立即停止新增"))
                break
            }

            if (topConfirmStartedAt > 0L) {
                val topElapsed = now - topConfirmStartedAt
                val topGrowth = peak - topConfirmStartPeak
                val topFailures = totalFailure - topConfirmStartFailure
                val confirmMs = topConfirmMs(peak)
                val topBudget = topFailureBudget(peak)
                when {
                    topFailures >= topBudget -> {
                        stopPhase = "无增长确认"
                        onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 顶部确认失败已达 $topFailures/$topBudget，停止新增，避免低 CPS 长尾"))
                        break
                    }
                    topElapsed >= confirmMs && topGrowth < meaningfulGrowthStep(peak) -> {
                        stopPhase = "无增长确认"
                        onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 顶部确认 ${topElapsed / 1000.0}s 无有效增长，停止新增"))
                        break
                    }
                    topElapsed >= confirmMs * 2 -> {
                        stopPhase = "无增长确认"
                        onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 顶部确认超时，停止新增，避免长尾拖延"))
                        break
                    }
                }
            }

            when {
                activeBefore >= fdHardStop -> {
                    fdSeen = true
                    stopPhase = "FD上限"
                    onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 接近 Android FD 上限，活动连接 $activeBefore，已保护停止"))
                    break
                }
                consecutiveFailure >= consecutiveLimit -> {
                    stopPhase = "连续失败"
                    onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 连续失败达到动态上限：$consecutiveFailure/$consecutiveLimit，已停止新增"))
                    break
                }
                totalFailure >= totalLimit -> {
                    stopPhase = "失败上限"
                    onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 累计失败达到动态上限：$totalFailure/$totalLimit，已停止新增"))
                    break
                }
                peak > 0 && now - lastGrowthAt >= noGrowthWindowMs(peak) && activeBefore >= (peak * 0.985f).roundToInt() -> {
                    enterTopConfirm(now, peak, "无有效增长")
                    currentCps = currentCps.coerceAtMost(16).coerceAtLeast(8)
                }
                peak > 10_000 && windowFailure >= 8 && failRate >= noGrowthFailThreshold(peak) && activeBefore >= (peak * 0.98f).roundToInt() -> {
                    enterTopConfirm(now, peak, "失败抬头")
                    currentCps = currentCps.coerceAtMost(16).coerceAtLeast(8)
                }
            }

            if (topConfirmStartedAt > 0L || now - lastGrowthAt >= noGrowthWindowMs(peak) / 2) {
                currentCps = currentCps.coerceAtMost(20).coerceAtLeast(8)
            }

            val remainingSuccess = config.successLimit - totalSuccess
            if (remainingSuccess <= 0) break
            val tickMs = config.intervalMs.coerceIn(350L, 700L)
            val requestedLaunchCount = ((currentCps * tickMs) / 1000L).toInt().coerceIn(1, 640).coerceAtMost(remainingSuccess)

            val pressure = resourcePressure(requestedLaunchCount)
            if (pressure.stopNow) {
                recordFailure(pressure.reason)
                stopPhase = pressure.reason
                fdSeen = pressure.reason == "FD上限"
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} ${pressure.reason}，已触发保护停止：${pressure.detail}"))
                break
            }
            val launchCount = minOf(requestedLaunchCount, pressure.allowedLaunch.coerceAtLeast(1))
            val batch = openBatch(addresses, config.port, config.timeoutMs.coerceIn(300, 800), launchCount, protocolEpoch)
            if (!currentCoroutineContext().isActive || releaseEpoch.get() != protocolEpoch) {
                batch.successSockets.forEach { socket -> runCatching { socket.close() } }
                break
            }

            val failureBeforeBatch = totalFailure
            val batchSuccess = batch.successSockets.size
            totalSuccess += batchSuccess
            socketLock.withLock {
                if (releaseEpoch.get() == protocolEpoch) {
                    heldSockets.getValue(protocol).addAll(batch.successSockets)
                } else {
                    batch.successSockets.forEach { socket -> runCatching { socket.close() } }
                }
            }
            batch.errors.forEach { (key, value) -> repeat(value) { recordFailure(key) } }
            val batchFailure = totalFailure - failureBeforeBatch
            if (batchSuccess > 0) consecutiveFailure = 0
            addSample(batchSuccess, batchFailure)

            val activeAfter = activeCount(protocol)
            val prevPeak = maxStable
            if (activeAfter >= lastMeaningfulPeak + meaningfulGrowthStep(maxOf(prevPeak, activeAfter))) {
                lastMeaningfulPeak = activeAfter
                lastGrowthAt = System.currentTimeMillis()
                if (topConfirmStartedAt > 0L) {
                    topConfirmStartedAt = 0L
                    topConfirmStartPeak = 0
                    topConfirmStartFailure = 0
                }
            }
            maxStable = maxOf(maxStable, activeAfter)
            if (batch.errors.keys.any { it == "FD上限" || it == "资源保护" }) fdSeen = true

            val (recentS, recentF) = windowStats(3_000L)
            val recentTotal = recentS + recentF
            val recentFailRate = if (recentTotal <= 0) 0f else recentF.toFloat() / recentTotal.toFloat()
            val recentSuccessRate = if (recentTotal <= 0) 1f else recentS.toFloat() / recentTotal.toFloat()
            currentCps = when {
                recentFailRate > 0.50f -> (currentCps * 0.30f).roundToInt().coerceAtLeast(20)
                recentFailRate > 0.20f -> (currentCps * 0.50f).roundToInt().coerceAtLeast(30)
                topConfirmStartedAt == 0L && recentSuccessRate >= 0.95f && batchFailure <= 1 && activeAfter >= activeBefore -> (currentCps * 1.70f).roundToInt()
                else -> currentCps
            }.coerceIn(5, cpsCap(maxOf(maxStable, activeAfter)))

            emitStats()

            if (fdSeen) {
                stopPhase = "FD上限"
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 检测到 Android FD上限，已停止新增并进入统一释放"))
                break
            }
            delay(tickMs)
        }

        val finalActive = activeCount(protocol)
        val finalPhase = when {
            fdSeen -> "FD上限"
            stopPhase != null -> stopPhase!!
            totalSuccess >= config.successLimit -> "测试完成"
            else -> "测试完成"
        }
        val finalStats = stats.copy(
            phase = finalPhase,
            activeSessions = finalActive,
            maxStableSessions = maxOf(maxStable, finalActive),
            totalSuccess = totalSuccess,
            totalFailure = totalFailure,
            totalAttempts = totalSuccess + totalFailure,
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
            val safeCount = count.coerceIn(1, 640)
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
        val fdSafeMax = minOf(fdHardStop, (fdMax - fdReserve).coerceAtLeast(128))

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
                    detail = "当前FD=$fdNow，保护线=$fdSafeMax，系统上限=$fdMax"
                )
            }
            val allowed = remaining.coerceAtMost(requestedLaunch)
            if (allowed <= 0) {
                return ResourcePressure(
                    stopNow = true,
                    allowedLaunch = 0,
                    reason = "FD上限",
                    detail = "当前FD=$fdNow，保护线=$fdSafeMax，系统上限=$fdMax"
                )
            }
            return ResourcePressure(
                stopNow = false,
                allowedLaunch = allowed,
                reason = "",
                detail = "当前FD=$fdNow，保护线=$fdSafeMax，系统上限=$fdMax"
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
     * Snapshot and clear held sockets immediately. releaseEpoch is incremented first so
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

    suspend fun closeDetachedSockets(
        socketsToClose: List<Socket>,
        batchSize: Int = 500,
        onProgress: suspend (closed: Int, total: Int, elapsedMs: Long) -> Unit = { _, _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        if (socketsToClose.isEmpty()) {
            withContext(Dispatchers.Main) { onProgress(0, 0, 0L) }
            return@withContext 0
        }
        val total = socketsToClose.size
        val safeBatch = batchSize.coerceIn(100, 1000)
        val startedAt = System.currentTimeMillis()
        var closed = 0
        var lastProgressAt = 0L
        socketsToClose.chunked(safeBatch).forEach { chunk ->
            chunk.forEach { socket ->
                runCatching { socket.close() }
                closed++
            }
            val now = System.currentTimeMillis()
            if (now - lastProgressAt >= 300L || closed >= total) {
                val elapsed = now - startedAt
                withContext(Dispatchers.Main) { onProgress(closed, total, elapsed) }
                lastProgressAt = now
            }
            yield()
        }
        closed
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

    private data class WindowSample(val timeMs: Long, val success: Int, val failure: Int)
    private data class OpenResult(val socket: Socket? = null, val error: String? = null, val discarded: Boolean = false)
    private data class BatchOpenResult(val successSockets: List<Socket>, val errors: Map<String, Int>)
}
