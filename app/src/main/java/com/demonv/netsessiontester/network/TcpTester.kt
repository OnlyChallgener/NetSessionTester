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
import kotlinx.coroutines.Deferred
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
     * Android 常见单进程 FD 上限约 32768，但 App 自身还会占用 DNS、通知、日志、
     * 图表、pipe/eventfd、STUN/Ping 等 FD。build66 不再固定 32000/32600 作为主判断，
     * 而是实时读取 /proc/self/fd 与 /proc/self/limits，按剩余 FD 分三段保护。
     *
     * 预警：剩余 <= 900，降低 CPS，继续接近上限；
     * 保护：剩余 <= 500，进入顶部确认，只允许少量补测；
     * 硬停：剩余 <= 300，立即停止新增并释放。
     *
     * active 32600 仅作为最终兜底，防止某些 ROM 读取 FD 异常时继续冲顶。
     */
    private val fdActiveFallbackStop = 32_600
    private val fdWarnRemaining = 900
    private val fdGuardRemaining = 500
    private val fdHardRemaining = 300

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
                val releasedV4 = release(IpProtocol.IPV4)
                ipv4Stats = ipv4Stats?.copy(activeSessions = 0, phase = "已释放")
                ipv4Stats?.let { onStats(it) }
                if (releasedV4 > 0) {
                    onLog(LogLine(level = LogLevel.WARN, text = "IPv4 已释放 $releasedV4 条连接，切换 IPv6 测试"))
                }
                delay(350)
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
    ): ProtocolStats = coroutineScope {
        release(protocol)
        val protocolEpoch = releaseEpoch.get()
        val addresses = resolveInetAddresses(config.host, protocol)
        if (addresses.isEmpty()) {
            val stats = ProtocolStats(protocol = protocol, phase = "解析失败")
            onStats(stats)
            onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 没有解析到可用地址"))
            return@coroutineScope stats
        }

        val addressText = addresses.mapNotNull { it.hostAddress }.distinct()
        onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 解析成功：${addressText.joinToString(" / ")}"))

        val userCps = config.batchSize.coerceIn(20, 1500)
        val manualCpsMode = userCps != 128
        val isIpv6HighCapacity = protocol == IpProtocol.IPV6
        onLog(
            LogLine(
                level = LogLevel.INFO,
                text = if (manualCpsMode) {
                    "${protocol.label} 手动目标 CPS：${userCps}/s。按时间发射连接，严格优先执行用户设定；仅在 FD、pending、低容量到顶、后台/异常时暂停或停止。"
                } else {
                    "${protocol.label} 智能策略：128 起步，按协议自动加速；IPv6/IPv4 6000+ 高容量快速冲刺，IPv4 低中容量快速确认。"
                }
            )
        )

        var totalSuccess = 0
        var totalFailure = 0
        var totalLaunched = 0
        var maxStable = 0
        var consecutiveFailure = 0
        var lastStatsAt = System.currentTimeMillis()
        var lastUiStatsAt = 0L
        var lastLogAt = 0L
        var lastLaunchedForStats = 0
        var lastGrowthAt = System.currentTimeMillis()
        var lastMeaningfulPeak = 0
        var topConfirmStartedAt = 0L
        var topConfirmStartPeak = 0
        var topConfirmStartFailure = 0
        var topConfirmReason = ""
        var currentCps = if (manualCpsMode) userCps else 128
        var launchCarry = 0.0
        var fdSeen = false
        var lastFdGuardLogAt = 0L
        var ipv4HighCapacityAnnounced = false
        var stopPhase: String? = null
        var addressCursor = 0
        val startedAt = System.currentTimeMillis()
        val timeoutMs = config.timeoutMs.coerceIn(1_200, 5_000)
        val errors = linkedMapOf<String, Int>()
        val samples = ArrayDeque<WindowSample>()
        val activeHistory = ArrayDeque<Pair<Long, Int>>()
        val pending = mutableListOf<Deferred<OpenResult>>()
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText)
        onStats(stats)

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

        fun recordActive(active: Int) {
            val now = System.currentTimeMillis()
            activeHistory.addLast(now to active)
            while (activeHistory.isNotEmpty() && now - activeHistory.first().first > 15_000L) activeHistory.removeFirst()
        }

        fun activeGrowth(ms: Long, currentActive: Int): Int {
            val now = System.currentTimeMillis()
            val base = activeHistory.firstOrNull { now - it.first <= ms }?.second ?: activeHistory.firstOrNull()?.second ?: currentActive
            return (currentActive - base).coerceAtLeast(0)
        }

        fun recordFailure(key: String) {
            totalFailure++
            consecutiveFailure++
            errors[key] = (errors[key] ?: 0) + 1
            if (key == "FD上限" || key == "资源保护") fdSeen = true
        }

        fun meaningfulGrowthStep(peak: Int): Int = when {
            peak < 1_000 -> 30
            peak < 3_000 -> 60
            peak < 10_000 -> 120
            else -> 180
        }

        fun pendingMax(targetCps: Int, pressure: ResourcePressure): Int {
            val byTimeout = (targetCps * (timeoutMs / 1000.0) * 1.35).roundToInt().coerceAtLeast(300)
            val general = byTimeout.coerceAtMost(if (protocol == IpProtocol.IPV6 || maxStable >= 10_000 || manualCpsMode) 5_000 else 3_000)
            val byFd = if (pressure.fdRemaining > 0) (pressure.fdRemaining - 700).coerceAtLeast(120) else general
            return minOf(general, byFd).coerceAtLeast(80)
        }

        fun smartTargetCps(active: Int, failRate: Float): Int {
            if (manualCpsMode) return userCps
            return when (protocol) {
                IpProtocol.IPV6 -> when {
                    active < 1_000 -> 512
                    active < 5_000 -> 900
                    active < 20_000 -> 1_300
                    else -> 1_500
                }
                IpProtocol.IPV4 -> when {
                    active < 1_000 -> if (failRate > 0.35f) 256 else 512
                    active < 3_000 -> 700
                    active < 6_000 -> 900
                    // IPv4 超过 6000 会话后，按高容量/疑似公网策略处理：不再套低中容量慢确认。
                    active < 12_000 -> 1_300
                    else -> 1_500
                }
            }
        }

        fun approachTarget(current: Int, target: Int): Int {
            if (current == target) return current
            return if (current < target) {
                maxOf(current + 96, (current * 1.55f).roundToInt()).coerceAtMost(target)
            } else {
                maxOf(target, (current * 0.75f).roundToInt())
            }.coerceIn(16, 1500)
        }

        fun enterTopConfirm(now: Long, peak: Int, reason: String) {
            if (topConfirmStartedAt == 0L) {
                topConfirmStartedAt = now
                topConfirmStartPeak = peak
                topConfirmStartFailure = totalFailure
                topConfirmReason = reason
                if (!manualCpsMode) currentCps = currentCps.coerceIn(16, 32)
            }
        }

        fun topConfirmLimitMs(peak: Int): Long = when (topConfirmReason) {
            "低容量确认" -> 5_000L
            "FD接近" -> 6_000L
            else -> if (peak < 10_000) 7_000L else 8_000L
        }

        suspend fun emitStats(forcePhase: String? = null, forceLog: Boolean = false) {
            val now = System.currentTimeMillis()
            val minUiIntervalMs = 500L
            if (!forceLog && forcePhase == null && now - lastUiStatsAt < minUiIntervalMs) return
            lastUiStatsAt = now
            val active = activeCount(protocol)
            maxStable = maxOf(maxStable, active)
            val launchedDelta = totalLaunched - lastLaunchedForStats
            val elapsedMs = (now - lastStatsAt).coerceAtLeast(1L)
            val launchCps = if (launchedDelta <= 0) 0 else (launchedDelta * 1000L / elapsedMs).toInt()
            lastLaunchedForStats = totalLaunched
            lastStatsAt = now
            stats = ProtocolStats(
                protocol = protocol,
                phase = forcePhase ?: stopPhase ?: if (topConfirmStartedAt > 0L) "顶部确认" else "建连中",
                resolvedAddresses = addressText,
                activeSessions = active,
                totalFailure = totalFailure,
                totalAttempts = totalLaunched,
                lastAdded = launchedDelta,
                cps = launchCps,
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
                        text = "${protocol.label} 统计 - 发起：$totalLaunched | 成功：$totalSuccess | 失败：$totalFailure | 活动：$active | 峰值：$maxStable | 新增：$launchedDelta | 发起CPS：${launchCps}/秒 | 目标CPS：$currentCps | pending：${pending.size}"
                    )
                )
            }
        }

        suspend fun reapCompleted() {
            if (pending.isEmpty()) return
            val done = pending.filter { it.isCompleted }
            if (done.isEmpty()) return
            pending.removeAll(done.toSet())
            val sockets = mutableListOf<Socket>()
            var success = 0
            var failure = 0
            done.forEach { job ->
                val result = runCatching { job.await() }.getOrElse { OpenResult(error = classifyError(it)) }
                result.socket?.let { socket ->
                    sockets += socket
                    success++
                }
                result.error?.let { key ->
                    failure++
                    recordFailure(key)
                }
            }
            if (sockets.isNotEmpty()) {
                socketLock.withLock {
                    if (releaseEpoch.get() == protocolEpoch) {
                        heldSockets.getValue(protocol).addAll(sockets)
                    } else {
                        sockets.forEach { socket -> runCatching { socket.close() } }
                    }
                }
            }
            if (success > 0) consecutiveFailure = 0
            totalSuccess += success
            addSample(success, failure)
        }

        while (currentCoroutineContext().isActive && releaseEpoch.get() == protocolEpoch && totalSuccess < config.successLimit) {
            val now = System.currentTimeMillis()
            reapCompleted()
            val active = activeCount(protocol)
            recordActive(active)
            maxStable = maxOf(maxStable, active)
            val peak = maxOf(maxStable, active)
            val elapsed = now - startedAt
            val (winS4, winF4) = windowStats(4_000L)
            val (winS8, winF8) = windowStats(8_000L)
            val win4Total = winS4 + winF4
            val win8Total = winS8 + winF8
            val failRate4 = if (win4Total <= 0) 0f else winF4.toFloat() / win4Total.toFloat()
            val failRate8 = if (win8Total <= 0) 0f else winF8.toFloat() / win8Total.toFloat()
            val growth4 = activeGrowth(4_000L, active)
            val growth8 = activeGrowth(8_000L, active)

            if (active >= fdActiveFallbackStop) {
                fdSeen = true
                stopPhase = "FD上限"
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 活动连接已到 $active，达到 Android FD 兜底保护线 $fdActiveFallbackStop，立即停止新增"))
                break
            }

            val desiredTarget = smartTargetCps(active, failRate4)
            currentCps = if (manualCpsMode && topConfirmStartedAt == 0L) {
                // 手动 CPS 模式要严格遵守用户目标值：不做平滑爬升、不因普通失败率悄悄降速。
                // 安全保护只通过 pending/FD 限制暂停发射或直接停止，不修改目标 CPS。
                userCps
            } else {
                approachTarget(currentCps, desiredTarget)
            }

            val pressure = resourcePressure(currentCps)
            if (pressure.stopNow) {
                recordFailure(pressure.reason)
                stopPhase = pressure.reason
                fdSeen = pressure.reason == "FD上限"
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} ${pressure.reason}，已触发保护停止：${pressure.detail}"))
                break
            }
            if (pressure.fdRemaining in 1..fdWarnRemaining && now - lastFdGuardLogAt >= 1_500L) {
                lastFdGuardLogAt = now
                val level = if (pressure.fdRemaining <= fdGuardRemaining) LogLevel.WARN else LogLevel.INFO
                onLog(LogLine(level = level, text = "${protocol.label} FD动态保护：${pressure.detail}"))
            }

            val ipv4HighCapacity = protocol == IpProtocol.IPV4 && active >= 6_000
            if (ipv4HighCapacity && !ipv4HighCapacityAnnounced) {
                ipv4HighCapacityAnnounced = true
                onLog(LogLine(level = LogLevel.INFO, text = "IPv4 活动连接已超过 6000，切换为高容量/疑似公网策略；手动 CPS 不降速，主要依靠 pending/FD 保护收尾。"))
            }

            // 手动 CPS 模式：用户设 800/1000/1500 后，普通失败率/增长效率不能再把 CPS 压到个位数。
            // 低容量场景直接快速停止；FD 接近直接短收尾；不进入 16~32 CPS 慢确认。
            if (manualCpsMode) {
                val manualLowCapacityDone = elapsed >= 6_000L && peak < 800 && growth4 < 30 && totalFailure >= maxOf(80, (peak * 0.55f).roundToInt())
                if (manualLowCapacityDone) {
                    stopPhase = "低容量确认"
                    onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 手动 CPS 低容量快速收尾：峰值 $peak，4秒增长 $growth4，失败 $totalFailure，停止新增"))
                    break
                }
                if (pressure.fdRemaining in 1..fdGuardRemaining || active >= 32_200) {
                    fdSeen = true
                    stopPhase = "FD上限"
                    onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 手动 CPS 已接近 FD 上限：活动 $active，${pressure.detail}，停止新增并释放"))
                    break
                }
            } else {
                if (pressure.fdRemaining in 1..fdGuardRemaining || active >= 32_200) {
                    enterTopConfirm(now, peak, "FD接近")
                }

                val lowCapacityLooksDone = elapsed >= 8_000L && active < 1_000 && win4Total >= 40 && failRate4 >= 0.35f && growth4 < 60
                if (lowCapacityLooksDone) enterTopConfirm(now, peak, "低容量确认")

                // IPv4 6000+ 在你的场景里基本属于公网/高容量 NAT，不再用低中容量平台期确认提前降速。
                val allowPlateauForIpv4 = protocol == IpProtocol.IPV4 && active in 1_000 until 6_000
                if (allowPlateauForIpv4 && elapsed >= 14_000L && win8Total >= 120 && growth8 < 150 && failRate8 >= 0.18f) {
                    enterTopConfirm(now, peak, "增长效率低")
                }
            }

            if (topConfirmStartedAt > 0L) {
                if (!manualCpsMode) currentCps = currentCps.coerceIn(16, 32)
                val topElapsed = now - topConfirmStartedAt
                val topGrowth = peak - topConfirmStartPeak
                val topFailures = totalFailure - topConfirmStartFailure
                val limitMs = topConfirmLimitMs(peak)
                val growthNeed = when (topConfirmReason) {
                    "低容量确认" -> 35
                    "FD接近" -> 90
                    else -> meaningfulGrowthStep(peak)
                }
                val failureBudget = when (topConfirmReason) {
                    "低容量确认" -> 80
                    "FD接近" -> 160
                    else -> 140
                }
                if (topElapsed >= limitMs && topGrowth < growthNeed) {
                    stopPhase = if (topConfirmReason == "FD接近") "FD上限" else "无增长确认"
                    fdSeen = topConfirmReason == "FD接近"
                    onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} ${topConfirmReason} ${topElapsed / 1000.0}s 增长 $topGrowth<$growthNeed，停止新增"))
                    break
                }
                if (topFailures >= failureBudget) {
                    stopPhase = if (topConfirmReason == "FD接近") "FD上限" else "失败确认"
                    fdSeen = topConfirmReason == "FD接近"
                    onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} ${topConfirmReason} 失败 $topFailures/$failureBudget，停止新增"))
                    break
                }
                if (topElapsed >= limitMs + 3_000L) {
                    stopPhase = if (topConfirmReason == "FD接近") "FD上限" else "无增长确认"
                    fdSeen = topConfirmReason == "FD接近"
                    onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} ${topConfirmReason} 达到硬时间上限，停止新增"))
                    break
                }
            }

            val targetForPending = if (!manualCpsMode && topConfirmStartedAt > 0L) currentCps.coerceIn(16, 32) else currentCps
            val maxPending = pendingMax(targetForPending, pressure)
            val canLaunchByPending = (maxPending - pending.size).coerceAtLeast(0)
            val remainingBySuccessLimit = (config.successLimit - totalSuccess - pending.size).coerceAtLeast(0)
            val tickMs = 100L
            launchCarry += targetForPending * (tickMs / 1000.0)
            var launchCount = launchCarry.toInt()
            if (launchCount > 0) launchCarry -= launchCount.toDouble()
            launchCount = minOf(launchCount, canLaunchByPending, remainingBySuccessLimit, pressure.allowedLaunch.coerceAtLeast(0))

            if (launchCount > 0) {
                repeat(launchCount) {
                    val address = addresses[addressCursor % addresses.size]
                    addressCursor++
                    totalLaunched++
                    pending += async(Dispatchers.IO) { openOne(address, config.port, timeoutMs, protocolEpoch) }
                }
            }

            emitStats()
            delay(tickMs)
        }

        // 停止新增后给已经发射的连接一个很短的收敛窗口，避免把刚成功的慢连接漏掉；但不等待完整 timeout，防止收尾卡顿。
        val settleUntil = System.currentTimeMillis() + 700L
        while (pending.isNotEmpty() && System.currentTimeMillis() < settleUntil && currentCoroutineContext().isActive) {
            reapCompleted()
            delay(50L)
        }
        pending.forEach { it.cancel() }
        pending.clear()

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
            totalAttempts = totalLaunched,
            errorSummary = errors.toMap()
        )
        onStats(finalStats)
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} ${finalStats.phase} - 活动：${finalStats.activeSessions} 失败：${finalStats.totalFailure} 发起：${finalStats.totalAttempts}"))

        if (!config.keepConnectionsAfterStop) {
            release(protocol)
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 已按设置释放连接。"))
        }
        return@coroutineScope finalStats
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

        if (heapFree in 1 until memoryReserveBytes) {
            return ResourcePressure(
                stopNow = true,
                allowedLaunch = 0,
                reason = "资源保护",
                detail = "可用堆内存约 ${heapFree / 1024 / 1024}MB，低于保护阈值 ${memoryReserveBytes / 1024 / 1024}MB",
                fdNow = fdNow,
                fdMax = fdMax,
                fdRemaining = -1
            )
        }

        if (fdNow > 0 && fdMax > 0) {
            val remaining = (fdMax - fdNow).coerceAtLeast(0)
            if (remaining <= fdHardRemaining) {
                return ResourcePressure(
                    stopNow = true,
                    allowedLaunch = 0,
                    reason = "FD上限",
                    detail = "当前FD=$fdNow，剩余=$remaining，硬停阈值=$fdHardRemaining，系统上限=$fdMax",
                    fdNow = fdNow,
                    fdMax = fdMax,
                    fdRemaining = remaining
                )
            }

            val allowed = when {
                remaining <= fdGuardRemaining -> {
                    // 保护区：只允许极少量补测，避免一下子吃掉硬停冗余。
                    minOf(requestedLaunch, (remaining - fdHardRemaining).coerceIn(1, 96))
                }
                remaining <= fdWarnRemaining -> {
                    // 预警区：降低冲刺速度，但仍允许继续接近真实上限。
                    minOf(requestedLaunch, (remaining - fdGuardRemaining).coerceIn(80, 640))
                }
                else -> requestedLaunch
            }.coerceAtLeast(1)

            return ResourcePressure(
                stopNow = false,
                allowedLaunch = allowed,
                reason = "",
                detail = "当前FD=$fdNow，剩余=$remaining，预警=$fdWarnRemaining，保护=$fdGuardRemaining，硬停=$fdHardRemaining，系统上限=$fdMax",
                fdNow = fdNow,
                fdMax = fdMax,
                fdRemaining = remaining
            )
        }

        return ResourcePressure(
            stopNow = false,
            allowedLaunch = requestedLaunch,
            reason = "",
            detail = "FD未知",
            fdNow = fdNow,
            fdMax = fdMax,
            fdRemaining = -1
        )
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
        val safeBatch = batchSize.coerceIn(200, 1500)
        val startedAt = System.currentTimeMillis()
        var closed = 0
        var lastProgressAt = 0L
        socketsToClose.chunked(safeBatch).forEach { chunk ->
            chunk.forEach { socket ->
                runCatching { socket.close() }
                closed++
            }
            val now = System.currentTimeMillis()
            if (now - lastProgressAt >= 500L || closed >= total) {
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
        val detail: String,
        val fdNow: Int,
        val fdMax: Int,
        val fdRemaining: Int
    )

    private data class WindowSample(val timeMs: Long, val success: Int, val failure: Int)
    private data class OpenResult(val socket: Socket? = null, val error: String? = null, val discarded: Boolean = false)
    private data class BatchOpenResult(val successSockets: List<Socket>, val errors: Map<String, Int>)
}
