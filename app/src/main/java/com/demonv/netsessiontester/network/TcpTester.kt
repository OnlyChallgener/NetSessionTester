package com.demonv.netsessiontester.network

import android.content.Context
import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.ResolveResult
import com.demonv.netsessiontester.model.SessionConfig
import com.demonv.netsessiontester.model.TestMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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
import kotlin.math.floor

/**
 * v0.9.9-test93：非阻塞流水线发射自测核心。
 *
 * 目标：解决 test92 高 CPS 仍被 openBatch/awaitAll 慢连接拖住、释放偏慢的问题。
 * - 固定 CPS token bucket：按真实时间补 token，目标CPS * 调度间隔 / 1000 不再错算。
 * - 非阻塞发射：tick 只负责发起，成功/失败由后台任务回流，不等待整批完成。
 * - pending 窗口：maxPending = CPS * 4，范围 1000..8000，避免 pending 过小跑不快或过大卡死。
 * - 停止时立即切 releaseEpoch，取消 pending scope；后续才返回的 socket 会自关闭，不再污染结果。
 * - close 时使用 SO_LINGER(0) 加速释放。
 */
class TcpTester(context: Context) {
    private val appContext = context.applicationContext
    private val socketLock = Mutex()
    private val releaseEpoch = AtomicLong(0L)
    private val fdReserve = 128
    private val absoluteFdCeiling = 32_640

    private fun readProcessFdSoftLimit(): Int {
        return runCatching {
            val text = File("/proc/self/limits").readText()
            Regex("Max open files\\s+(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.getOrNull()?.coerceAtLeast(1_024) ?: 32_768
    }

    private fun currentOpenFdCount(): Int = runCatching {
        File("/proc/self/fd").list()?.size ?: 0
    }.getOrDefault(0)

    private fun calculateSafeSocketTarget(): Triple<Int, Int, Int> {
        val softLimit = readProcessFdSoftLimit()
        val baseline = currentOpenFdCount().coerceAtLeast(32)
        val target = (softLimit - baseline - fdReserve)
            .coerceAtMost(absoluteFdCeiling)
            .coerceAtLeast(1_000)
        return Triple(target, softLimit, baseline)
    }

    private val heldSockets: MutableMap<IpProtocol, MutableList<Socket>> = mutableMapOf(
        IpProtocol.IPV4 to Collections.synchronizedList(mutableListOf()),
        IpProtocol.IPV6 to Collections.synchronizedList(mutableListOf())
    )

    suspend fun resolveHost(host: String): ResolveResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        runCatching {
            val all = NetworkDnsResolver.resolveAddressesBlocking(
                context = appContext,
                host = cleanHost,
                includeIpv4 = true,
                includeIpv6 = true
            )
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
                delay(250L)
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
        val expectedEpoch = releaseEpoch.get()
        val startedAt = System.currentTimeMillis()
        val addresses = resolveInetAddresses(config.host, protocol)
        if (addresses.isEmpty()) {
            val stats = ProtocolStats(protocol = protocol, phase = "解析失败")
            onStats(stats)
            onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 地址丢失或解析失败"))
            return stats
        }

        val addressText = addresses.mapNotNull { it.hostAddress }.distinct()
        val targetCps = config.batchSize.coerceIn(1, 2_000)
        val schedulerIntervalMs = config.intervalMs.coerceIn(20L, 1_000L)
        val maxPending = (targetCps * 4).coerceIn(1_000, 8_000)
        val (fdSafeStop, fdSoftLimit, baselineFd) = calculateSafeSocketTarget()
        val fdClipStart = (fdSafeStop - 1_500).coerceAtLeast(1_000)
        onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 解析成功：${addressText.joinToString(" / ")}"))
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 安全FD策略：软上限 $fdSoftLimit，测试前占用 $baselineFd，预留 $fdReserve，目标活动上限 $fdSafeStop；活动达到 32000 后才分级降速试探FD上限。"))

        val pendingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val pending = ArrayDeque<Deferred<OpenResult>>()
        val errors = linkedMapOf<String, Int>()

        var totalSuccess = 0
        var totalFailure = 0
        var totalConnectLatencyMs = 0L
        var connectLatencySamples = 0
        var launchedAttempts = 0
        var maxStable = 0
        var lastTotalSuccess = 0
        var lastStatsAt = System.currentTimeMillis()
        var lastUiAt = 0L
        var lastTokenAt = System.currentTimeMillis()
        var tokenCarry = 0.0
        var addressOffset = 0
        var lastCps = 0
        var consecutiveFailures = 0
        var lastGrowthAt = startedAt
        val recentOutcomes = ArrayDeque<Boolean>()
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText, lastAdded = targetCps)
        onStats(stats)

        suspend fun addSuccessSockets(sockets: List<Socket>) {
            if (sockets.isEmpty()) return
            socketLock.withLock {
                if (releaseEpoch.get() == expectedEpoch) {
                    heldSockets.getValue(protocol).addAll(sockets)
                } else {
                    sockets.forEach { fastClose(it) }
                }
            }
        }

        suspend fun drainCompleted(): Int {
            if (pending.isEmpty()) return 0
            var drained = 0
            val sockets = mutableListOf<Socket>()
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val job = iterator.next()
                if (!job.isCompleted) continue
                iterator.remove()
                drained++
                val result = runCatching { job.await() }.getOrElse { error ->
                    if (error is CancellationException) OpenResult(discarded = true) else OpenResult(error = classifyError(error))
                }
                if (result.discarded) continue
                result.socket?.let { socket ->
                    sockets += socket
                    recentOutcomes.addLast(true)
                    while (recentOutcomes.size > 200) recentOutcomes.removeFirst()
                    consecutiveFailures = 0
                    result.connectLatencyMs?.let { latency ->
                        totalConnectLatencyMs += latency.toLong().coerceAtLeast(0L)
                        connectLatencySamples++
                    }
                }
                result.error?.let { error ->
                    errors[error] = (errors[error] ?: 0) + 1
                    recentOutcomes.addLast(false)
                    while (recentOutcomes.size > 200) recentOutcomes.removeFirst()
                    consecutiveFailures++
                }
            }
            if (sockets.isNotEmpty()) {
                totalSuccess += sockets.size
                lastGrowthAt = System.currentTimeMillis()
                addSuccessSockets(sockets)
            }
            val failureNow = errors.values.sum()
            totalFailure = failureNow
            maxStable = maxOf(maxStable, totalSuccess)
            return drained
        }

        fun launchOne(timeoutMs: Int) {
            val address = addresses[addressOffset % addresses.size]
            addressOffset++
            launchedAttempts++
            pending.addLast(pendingScope.async {
                openOne(address, config.port, timeoutMs, expectedEpoch)
            })
        }

        fun pendingFdBudget(): Int {
            val occupied = totalSuccess + pending.size
            return (fdSafeStop - occupied).coerceAtLeast(0)
        }

        fun totalAttemptsForUi(): Int = totalSuccess + totalFailure

        fun adaptiveCpsForFdProbe(activeSessions: Int, remaining: Int): Int = when {
            remaining <= 0 -> 0
            activeSessions < 32_000 -> targetCps
            activeSessions < 32_200 -> minOf(targetCps, 100)
            activeSessions < 32_400 -> minOf(targetCps, 50)
            else -> minOf(targetCps, 20)
        }

        try {
            while (currentCoroutineContext().isActive && totalSuccess < config.successLimit) {
                val loopStart = System.currentTimeMillis()
                drainCompleted()

                if (maxStable >= fdSafeStop || totalSuccess >= fdSafeStop) {
                    errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                    stats = stats.copy(phase = "FD上限", errorSummary = errors.toMap(), maxStableSessions = maxStable)
                    onStats(stats)
                    onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 触发 Android FD/Socket 上限保护：峰值 $maxStable，停止新增"))
                    break
                }

                val nowForQuality = System.currentTimeMillis()
                val recentFailureRate = if (recentOutcomes.isNotEmpty()) {
                    recentOutcomes.count { !it }.toDouble() / recentOutcomes.size.toDouble()
                } else 0.0
                val noGrowthMs = nowForQuality - lastGrowthAt
                val qualityStopReason = when {
                    errors.containsKey("FD上限") -> "FD上限"
                    consecutiveFailures >= 200 -> "连续失败上限"
                    recentOutcomes.size >= 100 && recentFailureRate >= 0.80 && consecutiveFailures >= 50 && noGrowthMs >= 3_000L -> "平台确认"
                    totalSuccess >= fdClipStart && noGrowthMs >= 3_000L && recentOutcomes.size >= 50 -> "平台确认"
                    else -> null
                }
                val stopReason = qualityStopReason ?: stopReasonByQualityOrFailure(
                    success = totalSuccess,
                    failure = totalFailure,
                    launched = launchedAttempts,
                    peak = maxStable,
                    userFailureLimit = config.failureLimit
                )
                if (stopReason != null) {
                    stats = stats.copy(phase = stopReason, errorSummary = errors.toMap(), maxStableSessions = maxStable)
                    onStats(stats)
                    onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} $stopReason：成功 $totalSuccess，失败 $totalFailure，已发起 $launchedAttempts，pending ${pending.size}，峰值 $maxStable"))
                    break
                }

                val now = System.currentTimeMillis()
                val elapsedTokenMs = (now - lastTokenAt).coerceAtLeast(0L)
                lastTokenAt = now
                val remainingFd = (fdSafeStop - totalSuccess - pending.size).coerceAtLeast(0)
                val effectiveTargetCps = adaptiveCpsForFdProbe(totalSuccess, remainingFd)
                tokenCarry += effectiveTargetCps.toDouble() * elapsedTokenMs.toDouble() / 1000.0
                var toLaunch = floor(tokenCarry).toInt()
                if (toLaunch > 0) tokenCarry -= toLaunch.toDouble()

                if (toLaunch > 0) {
                    val remainingSuccess = (config.successLimit - totalSuccess).coerceAtLeast(0)
                    val pendingRoom = (maxPending - pending.size).coerceAtLeast(0)
                    val fdRoom = if (maxStable >= fdClipStart || totalSuccess >= fdClipStart) pendingFdBudget() else pendingFdBudget().coerceAtMost(maxPending)
                    toLaunch = minOf(toLaunch, remainingSuccess, pendingRoom, fdRoom)
                    val timeout = effectiveTimeoutMs(config.timeoutMs, maxStable)
                    repeat(toLaunch.coerceAtLeast(0)) { launchOne(timeout) }
                }

                val uiNow = System.currentTimeMillis()
                if (uiNow - lastUiAt >= 1_000L) {
                    val elapsed = (uiNow - lastStatsAt).coerceAtLeast(1L)
                    lastCps = ((totalSuccess - lastTotalSuccess) * 1000L / elapsed).toInt().coerceAtLeast(0)
                    lastTotalSuccess = totalSuccess
                    lastStatsAt = uiNow
                    lastUiAt = uiNow
                    stats = stats.copy(
                        phase = "建连中",
                        activeSessions = totalSuccess,
                        totalSuccess = totalSuccess,
                        totalFailure = totalFailure,
                        totalAttempts = totalAttemptsForUi(),
                        lastAdded = targetCps,
                        cps = lastCps,
                        maxStableSessions = maxStable,
                        averageConnectLatencyMs = if (connectLatencySamples > 0) (totalConnectLatencyMs / connectLatencySamples).toInt() else 0,
                        errorSummary = errors.toMap()
                    )
                    onStats(stats)
                    if (uiNow - startedAt >= 1_000L && uiNow % 5_000L < schedulerIntervalMs) {
                        onLog(LogLine(level = LogLevel.STAT, text = "${protocol.label} 活动 $totalSuccess｜失败 $totalFailure｜pending ${pending.size}｜实际CPS $lastCps/s"))
                    }
                }

                val cost = System.currentTimeMillis() - loopStart
                val sleep = schedulerIntervalMs - cost
                if (sleep > 0) delay(sleep) else yield()
            }

            // 结束前只捞一次已完成任务，不等待慢连接。
            drainCompleted()
        } finally {
            // 关键：停止新增后立即切 epoch。未完成的 connect 后续即使成功，也会自关闭，不会污染已结束统计。
            releaseEpoch.compareAndSet(expectedEpoch, expectedEpoch + 1)
            pendingScope.cancel()
        }

        val growthEndedAt = System.currentTimeMillis()
        val finalElapsedMs = (growthEndedAt - startedAt).coerceAtLeast(1L)
        val averageCps = (totalSuccess * 1000L / finalElapsedMs).toInt().coerceAtLeast(0)
        val finalPeak = maxOf(maxStable, totalSuccess)
        val finalTotal = totalSuccess + totalFailure
        val baseFinalPhase = when {
            finalPeak >= fdSafeStop || stats.phase == "FD上限" || errors.keys.any { it.contains("FD") } -> "FD上限"
            stats.phase == "平台确认" -> "平台确认"
            stats.phase == "连续失败上限" -> "连续失败上限"
            stats.phase == "低会话失败" -> "低会话失败"
            stats.phase == "失败上限" -> "失败上限"
            totalFailure > 0 -> "出现失败"
            totalSuccess >= config.successLimit -> "测试完成"
            else -> "测试完成"
        }

        val nearFdLimit =
            finalPeak >= 32_000 ||
                finalPeak >= fdSafeStop ||
                stats.phase == "FD上限" ||
                errors.keys.any { it.contains("FD", ignoreCase = true) }
        val holdDurationMs = if (nearFdLimit) 0L else 5_000L

        var stableActive = totalSuccess
        if (
            config.keepConnectionsAfterStop &&
            totalSuccess > 0 &&
            currentCoroutineContext().isActive &&
            holdDurationMs > 0L
        ) {
            val holdStartedAt = System.currentTimeMillis()
            val totalHoldSeconds = (holdDurationMs / 1_000L).coerceAtLeast(1L)
            while (currentCoroutineContext().isActive) {
                val elapsed = System.currentTimeMillis() - holdStartedAt
                stableActive = activeCount(protocol)
                val seconds = (elapsed / 1_000L).coerceIn(0L, totalHoldSeconds)
                onStats(
                    stats.copy(
                        phase = "保持验证 ${seconds}/${totalHoldSeconds}s",
                        activeSessions = stableActive,
                        totalSuccess = totalSuccess,
                        totalFailure = totalFailure,
                        totalAttempts = finalTotal,
                        lastAdded = targetCps,
                        cps = 0,
                        maxStableSessions = finalPeak,
                        averageConnectLatencyMs = if (connectLatencySamples > 0) (totalConnectLatencyMs / connectLatencySamples).toInt() else 0,
                        errorSummary = errors.toMap()
                    )
                )
                if (elapsed >= holdDurationMs) break
                delay(500L)
            }
            val retention = if (totalSuccess > 0) stableActive * 100.0 / totalSuccess.toDouble() else 0.0
            onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 保持验证完成：开始 $totalSuccess，稳定 $stableActive，保持率 ${String.format("%.1f", retention)}%"))
        } else if (config.keepConnectionsAfterStop && totalSuccess > 0 && nearFdLimit) {
            onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 已接近 FD 上限，跳过保持验证并进入释放流程"))
        }

        val finalPhase = when {
            !config.keepConnectionsAfterStop || totalSuccess <= 0 -> baseFinalPhase
            nearFdLimit -> "$baseFinalPhase · 跳过保持"
            else -> "$baseFinalPhase · 已保持5s"
        }
        val finalStats = stats.copy(
            phase = finalPhase,
            activeSessions = stableActive,
            totalFailure = totalFailure,
            totalAttempts = finalTotal,
            lastAdded = targetCps,
            cps = averageCps,
            totalSuccess = totalSuccess,
            maxStableSessions = finalPeak,
            averageConnectLatencyMs = if (connectLatencySamples > 0) (totalConnectLatencyMs / connectLatencySamples).toInt() else 0,
            errorSummary = errors.toMap()
        )
        onStats(finalStats)
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} ${finalStats.phase} - 活动：${finalStats.activeSessions} 失败：${finalStats.totalFailure} 总计：${finalStats.totalAttempts} 平均CPS：$averageCps/秒"))

        if (!config.keepConnectionsAfterStop) {
            release(protocol)
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 已按设置释放连接。"))
        }
        return finalStats
    }

    private fun stopReasonByQualityOrFailure(
        success: Int,
        failure: Int,
        launched: Int,
        peak: Int,
        userFailureLimit: Int
    ): String? {
        // 低会话：不用 3s/5s 无增长，改为尝试数 + 失败数 + 成功率。
        if (peak < 500 && launched >= 500 && failure >= 120) return "低会话失败"
        if (peak < 1_000 && launched >= 800 && failure >= 120) {
            val rate = success.toFloat() / launched.toFloat()
            if (rate < 0.60f) return "低会话失败"
        }

        val limit = minOf(userFailureLimit.coerceAtLeast(1), failureLimitFor(peak))
        return if (failure >= limit) "失败上限" else null
    }

    private fun failureLimitFor(peak: Int): Int = when {
        peak < 1_000 -> 120
        peak < 6_000 -> 200
        peak < 12_000 -> 360
        else -> 600
    }

    private fun effectiveTimeoutMs(configTimeoutMs: Int, peak: Int): Int {
        return when {
            peak < 500 -> configTimeoutMs.coerceAtMost(800)
            peak < 1_000 -> configTimeoutMs.coerceAtMost(1_000)
            peak < 6_000 -> configTimeoutMs.coerceAtMost(1_200)
            peak < 12_000 -> configTimeoutMs.coerceAtMost(900)
            else -> configTimeoutMs.coerceAtMost(800)
        }.coerceAtLeast(300)
    }

    private fun openOne(address: InetAddress, port: Int, timeoutMs: Int, expectedEpoch: Long): OpenResult {
        return try {
            if (releaseEpoch.get() != expectedEpoch) return OpenResult(discarded = true)
            val socket = Socket()
            socket.keepAlive = true
            socket.tcpNoDelay = true
            val connectStartedAt = System.nanoTime()
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            val connectLatencyMs = ((System.nanoTime() - connectStartedAt) / 1_000_000L).toInt().coerceAtLeast(1)
            if (releaseEpoch.get() != expectedEpoch) {
                fastClose(socket)
                OpenResult(discarded = true)
            } else {
                OpenResult(socket = socket, connectLatencyMs = connectLatencyMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            OpenResult(error = classifyError(e))
        }
    }

    private fun fastClose(socket: Socket) {
        runCatching { socket.setSoLinger(true, 0) }
        runCatching { socket.close() }
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
            else -> e.javaClass.simpleName
        }
    }

    private suspend fun resolveInetAddresses(host: String, protocol: IpProtocol): List<InetAddress> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        NetworkDnsResolver.resolveAddressesBlocking(
            context = appContext,
            host = cleanHost,
            includeIpv4 = protocol == IpProtocol.IPV4,
            includeIpv6 = protocol == IpProtocol.IPV6
        ).filter { address ->
            when (protocol) {
                IpProtocol.IPV4 -> address is Inet4Address
                IpProtocol.IPV6 -> address is Inet6Address
            }
        }.distinctBy { it.hostAddress }.take(8)
    }

    suspend fun detachForRelease(protocol: IpProtocol? = null): List<Socket> = withContext(Dispatchers.IO) {
        releaseEpoch.incrementAndGet()
        val targets = if (protocol == null) IpProtocol.entries else listOf(protocol)
        val socketsToClose = mutableListOf<Socket>()
        socketLock.withLock {
            targets.forEach { p ->
                val list = heldSockets.getValue(p)
                socketsToClose.addAll(list)
                list.clear()
            }
        }
        socketsToClose
    }

    suspend fun closeDetachedSockets(
        sockets: List<Socket>,
        batchSize: Int = 3000,
        onProgress: suspend (closed: Int, total: Int, elapsedMs: Long) -> Unit = { _, _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val total = sockets.size
        if (total <= 0) {
            onProgress(0, 0, 0L)
            return@withContext 0
        }
        val startedAt = System.currentTimeMillis()
        var closed = 0
        val actualBatchSize = batchSize.coerceAtLeast(1).coerceAtLeast(3000)
        coroutineScope {
            sockets.chunked(actualBatchSize).forEach { batch ->
                batch.map { socket ->
                    async(Dispatchers.IO) {
                        fastClose(socket)
                    }
                }.awaitAll()
                closed += batch.size
                onProgress(closed.coerceAtMost(total), total, System.currentTimeMillis() - startedAt)
            }
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

    private data class OpenResult(
        val socket: Socket? = null,
        val error: String? = null,
        val discarded: Boolean = false,
        val connectLatencyMs: Int? = null
    )
}
