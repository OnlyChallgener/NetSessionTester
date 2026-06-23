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
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/**
 * v0.9.9-test92：低会话判定 + 高会话提速自测核心。
 *
 * 延续 test91 固定 CPS 校准：每 tick 发起 = 目标CPS * 调度间隔 / 1000。
 * 新增低会话质量判定：尝试数 + 失败数 / 成功率，避免低会话坏链路拖太久。
 * 高会话阶段缩短有效 connect timeout，提升批次推进速度。
 *
 * 规则：
 * - 不恢复智能 CPS/动态调速；
 * - 保留调度间隔 ms 设置；
 * - UI/曲线按 1 秒节流刷新，避免活动数高速闪动；
 * - FD 保护线 32360，接近 32160 后自动裁剪批量，避免 32500+ 闪退；
 * - 不恢复 CPS 曲线/失败曲线/3s/5s 无增长检测。
 */
class TcpTester {
    private val socketLock = Mutex()
    private val releaseEpoch = AtomicLong(0L)
    private val fdClipStart = 32_160
    private val fdSafeStop = 32_360
    private val fdEmergencyStop = 32_500

    private val heldSockets: MutableMap<IpProtocol, MutableList<Socket>> = mutableMapOf(
        IpProtocol.IPV4 to Collections.synchronizedList(mutableListOf()),
        IpProtocol.IPV6 to Collections.synchronizedList(mutableListOf())
    )

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
                delay(350L)
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
        val targetCps = config.batchSize.coerceIn(20, 2_000)
        val schedulerIntervalMs = config.intervalMs.coerceIn(20L, 1_000L)
        val batchPerTick = ((targetCps.toLong() * schedulerIntervalMs + 999L) / 1000L).toInt().coerceIn(1, 2_000)
        onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 解析成功：${addressText.joinToString(" / ")}"))
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 使用 test92 固定CPS核心：目标 ${targetCps}/s，调度 ${schedulerIntervalMs}ms，每 tick ${batchPerTick} 条，多地址轮询，低会话质量判定，FD 32360。"))

        var totalSuccess = 0
        var totalFailure = 0
        var totalAttempts = 0
        var maxStable = 0
        var lastTotalSuccess = 0
        var lastStatsAt = System.currentTimeMillis()
        var lastUiAt = System.currentTimeMillis()
        var lastLogAt = 0L
        var lastCps = 0
        var launchOffset = 0
        var weakAfterSoftLimit = 0
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText, lastAdded = targetCps)
        val errors = linkedMapOf<String, Int>()
        onStats(stats)

        while (currentCoroutineContext().isActive && totalSuccess < config.successLimit) {
            val loopStartedAt = System.currentTimeMillis()
            val activeBefore = totalSuccess
            maxStable = maxOf(maxStable, activeBefore)

            if (maxStable >= fdSafeStop || activeBefore >= fdSafeStop) {
                errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                stats = stats.copy(phase = "FD上限", errorSummary = errors.toMap(), maxStableSessions = maxStable)
                onStats(stats)
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 触发 Android FD/Socket 上限保护：峰值 $maxStable，活动 $activeBefore，停止新增"))
                break
            }

            val softLimit = failureSoftLimitFor(maxStable)
            val hardLimit = failureHardLimitFor(maxStable, config.failureLimit)
            val lowQualityStop = shouldStopByLowQuality(totalSuccess, totalFailure, totalAttempts, maxStable)
            if (lowQualityStop || shouldStopByFailure(totalFailure, softLimit, hardLimit, maxStable, weakAfterSoftLimit)) {
                val reason = if (lowQualityStop) "低会话质量判定" else "失败收尾条件"
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到${reason}：成功 $totalSuccess，失败 $totalFailure，总计 $totalAttempts，软阈值 $softLimit，硬阈值 $hardLimit，峰值 $maxStable"))
                break
            }

            val remainingSuccess = config.successLimit - totalSuccess
            if (remainingSuccess <= 0) break

            val fdBudget = if (maxStable >= fdClipStart || activeBefore >= fdClipStart) {
                (fdSafeStop - maxOf(maxStable, activeBefore)).coerceAtLeast(0)
            } else {
                batchPerTick
            }
            val targetBatch = minOf(batchPerTick, remainingSuccess, fdBudget)
            if (targetBatch <= 0) {
                errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                stats = stats.copy(phase = "FD上限", errorSummary = errors.toMap(), maxStableSessions = maxStable)
                onStats(stats)
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} FD 安全线已满：峰值 $maxStable，活动 $activeBefore，停止新增"))
                break
            }

            val batchResult = openBatch(
                addresses = addresses,
                port = config.port,
                timeoutMs = effectiveTimeoutMs(config.timeoutMs, maxStable),
                count = targetBatch,
                startOffset = launchOffset,
                expectedEpoch = expectedEpoch
            )
            launchOffset += targetBatch

            val successAdd = batchResult.successSockets.size
            val rawFailAdd = batchResult.errors.values.sum()
            totalSuccess += successAdd
            totalFailure += rawFailAdd
            totalAttempts = totalSuccess + totalFailure
            batchResult.errors.forEach { (key, value) -> errors[key] = (errors[key] ?: 0) + value }

            if (batchResult.successSockets.isNotEmpty()) {
                socketLock.withLock {
                    if (releaseEpoch.get() == expectedEpoch) {
                        heldSockets.getValue(protocol).addAll(batchResult.successSockets)
                    } else {
                        batchResult.successSockets.forEach { runCatching { it.close() } }
                    }
                }
            }

            maxStable = maxOf(maxStable, totalSuccess)

            val currentSoftLimit = failureSoftLimitFor(maxStable)
            if (totalFailure >= currentSoftLimit && maxStable >= 6_000 && maxStable < 12_000) {
                // 360 进入软阈值后，只在“连续两批基本没有成功增长”时收尾。
                // 这样避免 9k-10k 仍在上涨时被失败数误杀。
                weakAfterSoftLimit = if (successAdd < weakGrowthThreshold(maxStable, batchPerTick)) weakAfterSoftLimit + 1 else 0
            } else {
                weakAfterSoftLimit = 0
            }

            val nowForStats = System.currentTimeMillis()
            val elapsedForStats = (nowForStats - lastStatsAt).coerceAtLeast(1L)
            if (elapsedForStats >= 1_000L) {
                val successDelta = totalSuccess - lastTotalSuccess
                lastCps = (successDelta * 1000L / elapsedForStats).toInt().coerceAtLeast(0)
                lastStatsAt = nowForStats
                lastTotalSuccess = totalSuccess
            }

            val phase = if (errors.containsKey("FD上限")) "FD上限" else "建连中"
            stats = ProtocolStats(
                protocol = protocol,
                phase = phase,
                resolvedAddresses = addressText,
                activeSessions = totalSuccess,
                totalFailure = totalFailure,
                totalAttempts = totalAttempts,
                lastAdded = targetCps,
                cps = lastCps,
                errorSummary = errors.toMap(),
                totalSuccess = totalSuccess,
                maxStableSessions = maxStable
            )
            if (nowForStats - lastUiAt >= 1_000L) {
                lastUiAt = nowForStats
                onStats(stats)
            }

            if (nowForStats - lastLogAt >= 1_000L) {
                lastLogAt = nowForStats
                onLog(LogLine(level = LogLevel.STAT, text = "${protocol.label} 统计 - 成功：$totalSuccess(+${successAdd}) | 失败：$totalFailure(+${rawFailAdd}) | 活动：$totalSuccess | 总计：$totalAttempts | 目标CPS：$targetCps | tick：$targetBatch | CPS：${lastCps}/秒 | 阈值：${failureSoftLimitFor(maxStable)}/${failureHardLimitFor(maxStable, config.failureLimit)}"))
            }

            val stopByFd = maxStable >= fdSafeStop || totalSuccess >= fdSafeStop || maxStable >= fdEmergencyStop || totalSuccess >= fdEmergencyStop || errors.containsKey("FD上限")
            val stopByLowQuality = shouldStopByLowQuality(totalSuccess, totalFailure, totalAttempts, maxStable)
            val stopByFailure = shouldStopByFailure(totalFailure, failureSoftLimitFor(maxStable), failureHardLimitFor(maxStable, config.failureLimit), maxStable, weakAfterSoftLimit)
            if (stopByFd || stopByLowQuality || stopByFailure || totalSuccess >= config.successLimit) {
                when {
                    stopByFd -> {
                        errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                        stats = stats.copy(phase = "FD上限", errorSummary = errors.toMap(), maxStableSessions = maxStable)
                        onStats(stats)
                        onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 触发 Android FD/Socket 上限保护：峰值 $maxStable，活动 $totalSuccess，停止新增并释放"))
                    }
                    stopByLowQuality -> {
                        stats = stats.copy(phase = "低会话失败", errorSummary = errors.toMap(), maxStableSessions = maxStable)
                        onStats(stats)
                        onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 低会话质量判定收尾：成功 $totalSuccess，失败 $totalFailure，总计 $totalAttempts，停止新增并释放"))
                    }
                    stopByFailure -> {
                        stats = stats.copy(phase = "失败上限", errorSummary = errors.toMap(), maxStableSessions = maxStable)
                        onStats(stats)
                        onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到失败收尾条件：失败 $totalFailure，峰值 $maxStable，停止新增并释放"))
                    }
                }
                break
            }

            val cost = System.currentTimeMillis() - loopStartedAt
            val sleep = schedulerIntervalMs - cost
            if (sleep > 0) delay(sleep) else kotlinx.coroutines.yield()
        }

        val finalActive = activeCount(protocol)
        val finalElapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val averageCps = (totalSuccess * 1000L / finalElapsedMs).toInt().coerceAtLeast(0)
        val finalPeak = maxOf(maxStable, finalActive, totalSuccess)
        val finalTotal = totalSuccess + totalFailure
        val finalHardLimit = failureHardLimitFor(finalPeak, config.failureLimit)
        val finalPhase = when {
            finalPeak >= fdSafeStop || stats.errorSummary.containsKey("FD上限") || stats.phase == "FD上限" -> "FD上限"
            stats.phase == "低会话失败" -> "低会话失败"
            totalFailure >= finalHardLimit || stats.phase == "失败上限" -> "失败上限"
            totalFailure > 0 -> "出现失败"
            totalSuccess >= config.successLimit -> "测试完成"
            else -> "测试完成"
        }
        val finalStats = stats.copy(
            phase = finalPhase,
            activeSessions = finalActive,
            totalFailure = totalFailure,
            totalAttempts = finalTotal,
            lastAdded = targetCps,
            cps = averageCps,
            totalSuccess = totalSuccess,
            maxStableSessions = finalPeak,
            errorSummary = errors.toMap()
        )
        onStats(finalStats)
        if (finalStats.phase == "FD上限") {
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} Android FD/Socket 上限保护：峰值 ${finalStats.maxStableSessions}+，停止新增并释放。"))
        } else {
            onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} ${finalStats.phase} - 活动：${finalStats.activeSessions} 失败：${finalStats.totalFailure} 总计：${finalStats.totalAttempts} 平均CPS：$averageCps/秒"))
        }

        if (!config.keepConnectionsAfterStop) {
            release(protocol)
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 已按设置释放连接。"))
        }
        return finalStats
    }

    private fun failureSoftLimitFor(peak: Int): Int = when {
        peak < 1_000 -> 120
        peak < 6_000 -> 200
        peak < 12_000 -> 360
        else -> 600
    }

    private fun failureHardLimitFor(peak: Int, userLimit: Int): Int {
        val hard = when {
            peak < 1_000 -> 120
            peak < 6_000 -> 200
            peak < 12_000 -> 600
            else -> 600
        }
        return minOf(userLimit.coerceAtLeast(1), hard)
    }

    private fun shouldStopByFailure(
        totalFailure: Int,
        softLimit: Int,
        hardLimit: Int,
        peak: Int,
        weakAfterSoftLimit: Int
    ): Boolean {
        if (totalFailure >= hardLimit) return true
        if (totalFailure < softLimit) return false
        if (peak < 6_000) return true
        if (peak < 12_000) return weakAfterSoftLimit >= 2
        return true
    }

    private fun shouldStopByLowQuality(success: Int, failure: Int, attempts: Int, peak: Int): Boolean {
        if (peak >= 1_000) return false
        if (attempts < 500) return false
        if (peak < 500 && failure >= 120) return true
        if (attempts >= 800) {
            val successRate = success.toFloat() / attempts.toFloat()
            if (successRate < 0.60f && failure >= 120) return true
        }
        return false
    }

    private fun weakGrowthThreshold(peak: Int, targetWindow: Int): Int {
        val ratio = when {
            peak < 8_000 -> 0.10f
            peak < 12_000 -> 0.08f
            else -> 0.06f
        }
        return (targetWindow * ratio).toInt().coerceIn(20, 120)
    }

    private fun effectiveTimeoutMs(configTimeoutMs: Int, peak: Int): Int {
        return when {
            peak < 500 -> configTimeoutMs.coerceAtMost(800)
            peak < 1_000 -> configTimeoutMs.coerceAtMost(1_000)
            peak < 6_000 -> configTimeoutMs.coerceAtMost(1_200)
            peak < 12_000 -> configTimeoutMs.coerceAtMost(800)
            else -> configTimeoutMs.coerceAtMost(650)
        }.coerceAtLeast(300)
    }

    private suspend fun openBatch(
        addresses: List<InetAddress>,
        port: Int,
        timeoutMs: Int,
        count: Int,
        startOffset: Int,
        expectedEpoch: Long
    ): BatchOpenResult = coroutineScope {
        val safeAddresses = addresses.ifEmpty { return@coroutineScope BatchOpenResult(emptyList(), emptyMap()) }
        val jobs = (0 until count).map { index ->
            async(Dispatchers.IO) {
                val address = safeAddresses[(startOffset + index) % safeAddresses.size]
                openOne(address, port, timeoutMs, expectedEpoch)
            }
        }
        val results = jobs.awaitAll()
        val sockets = mutableListOf<Socket>()
        val errors = linkedMapOf<String, Int>()
        results.forEach { result ->
            if (result.discarded) return@forEach
            result.socket?.let { sockets += it }
            result.error?.let { errors[it] = (errors[it] ?: 0) + 1 }
        }
        BatchOpenResult(sockets, errors)
    }

    private fun openOne(address: InetAddress, port: Int, timeoutMs: Int, expectedEpoch: Long): OpenResult {
        return try {
            if (releaseEpoch.get() != expectedEpoch) return OpenResult(discarded = true)
            val socket = Socket()
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
        } catch (e: Throwable) {
            OpenResult(error = classifyError(e))
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
            else -> e.javaClass.simpleName
        }
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
        batchSize: Int = 1000,
        onProgress: suspend (closed: Int, total: Int, elapsedMs: Long) -> Unit = { _, _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val total = sockets.size
        if (total <= 0) {
            onProgress(0, 0, 0L)
            return@withContext 0
        }
        val startedAt = System.currentTimeMillis()
        var closed = 0
        coroutineScope {
            sockets.chunked(batchSize.coerceAtLeast(1)).forEach { batch ->
                batch.map { socket ->
                    async(Dispatchers.IO) { runCatching { socket.close() } }
                }.awaitAll()
                closed += batch.size
                val elapsed = System.currentTimeMillis() - startedAt
                onProgress(closed.coerceAtMost(total), total, elapsed)
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

    private data class OpenResult(val socket: Socket? = null, val error: String? = null, val discarded: Boolean = false)
    private data class BatchOpenResult(val successSockets: List<Socket>, val errors: Map<String, Int>)
}
