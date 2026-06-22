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
 * build82：回归 v0.9.7 高速测速核心。
 *
 * 前几版的 100ms 发射器 + pending/FD/增长效率多状态模型，在高容量 IPv6 下会出现
 * CPS 掉底和 10s+ 僵直。这里恢复 v0.9.7 的核心方式：按“新增批次”高速并发 open，
 * 一批结束就统计，一旦失败/FD/分段失败阈值命中就直接收尾，不做低 CPS 慢确认。
 *
 * 保留新版必要能力：
 * - releaseEpoch，防止释放后迟到 socket 回流；
 * - detachForRelease / closeDetachedSockets，配合释放进度 UI；
 * - 分段失败上限：<1000=120，<6000=200，<12000=360，>=12000=600；
 * - IPv4 6000+ / IPv6 默认高容量，不再顶部慢跑；
 * - active >= 32000 直接按设备 FD 保护收尾；
 * - active >= 31800 后进行批量裁剪，避免一次大批量超冲到 32500+ 导致闪退。
 */
class TcpTester {
    private val socketLock = Mutex()
    private val releaseEpoch = AtomicLong(0L)
    private val fdClipStart = 31_800
    private val fdSafeStop = 32_000
    private val fdEmergencyStop = 32_200

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
        val addresses = resolveInetAddresses(config.host, protocol)
        if (addresses.isEmpty()) {
            val stats = ProtocolStats(protocol = protocol, phase = "解析失败")
            onStats(stats)
            onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 地址丢失或解析失败"))
            return stats
        }

        val addressText = addresses.mapNotNull { it.hostAddress }.distinct()
        onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 解析成功：${addressText.joinToString(" / ")}"))

        val manualMode = config.batchSize != 128
        onLog(
            LogLine(
                level = LogLevel.INFO,
                text = if (manualMode) {
                    "${protocol.label} 使用 v0.9.7 高速核心：新增值 ${config.batchSize}/批，间隔 ${config.intervalMs}ms，失败分段上限，命中即收尾。"
                } else {
                    "${protocol.label} 使用 v0.9.7 高速核心：默认 128 起步，IPv6/高容量 IPv4 自动提高批次，命中失败或 FD 上限即收尾。"
                }
            )
        )

        var totalSuccess = 0
        var totalFailure = 0
        var totalAttempts = 0
        var maxStable = 0
        var lastTotalAttempts = 0
        var lastTotalSuccess = 0
        var lastStatsAt = System.currentTimeMillis()
        var lastMeaningfulGrowthAt = System.currentTimeMillis()
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText)
        val errors = linkedMapOf<String, Int>()
        val startedAt = System.currentTimeMillis()
        onStats(stats)

        while (currentCoroutineContext().isActive && totalSuccess < config.successLimit) {
            val activeBefore = activeCount(protocol)
            maxStable = maxOf(maxStable, activeBefore)
            val failureLimit = failureLimitFor(maxStable)
            if (maxStable >= fdSafeStop || activeBefore >= fdSafeStop) {
                errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                stats = stats.copy(phase = "FD上限", errorSummary = errors.toMap())
                onStats(stats)
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 触发 Android FD/Socket 上限保护：峰值 $maxStable，活动 $activeBefore，停止新增"))
                break
            }
            if (totalFailure >= failureLimit) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到分段失败上限：$failureLimit"))
                break
            }

            val remainingSuccess = config.successLimit - totalSuccess
            val rawTargetBatch = minOf(batchFor(config, protocol, maxStable), remainingSuccess)
            if (rawTargetBatch <= 0) break

            val fdBudget = when {
                maxStable >= fdClipStart || activeBefore >= fdClipStart -> {
                    (fdSafeStop - maxOf(maxStable, activeBefore)).coerceAtLeast(0)
                }
                else -> rawTargetBatch
            }
            val targetBatch = minOf(rawTargetBatch, fdBudget)
            if (targetBatch <= 0) {
                errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                stats = stats.copy(phase = "FD上限", errorSummary = errors.toMap())
                onStats(stats)
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} FD 安全线已满：峰值 $maxStable，活动 $activeBefore，停止新增"))
                break
            }

            val failureBudget = (failureLimit - totalFailure).coerceAtLeast(0)
            if (failureBudget <= 0) break

            val batchResult = openBatchWithFailureBudget(
                addresses = addresses,
                port = config.port,
                timeoutMs = config.timeoutMs,
                targetCount = targetBatch,
                failureBudget = failureBudget,
                expectedEpoch = expectedEpoch
            )

            val rawFailAdd = batchResult.errors.values.sum()
            val failAdd = rawFailAdd.coerceAtMost(failureBudget)
            totalSuccess += batchResult.successSockets.size
            totalFailure += failAdd
            totalAttempts += batchResult.successSockets.size + rawFailAdd
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

            val active = activeCount(protocol)
            maxStable = maxOf(maxStable, active)
            val nowForStats = System.currentTimeMillis()
            val added = totalAttempts - lastTotalAttempts
            lastTotalAttempts = totalAttempts
            val successDelta = totalSuccess - lastTotalSuccess
            lastTotalSuccess = totalSuccess
            if (successDelta >= meaningfulGrowthStep(maxStable)) {
                lastMeaningfulGrowthAt = nowForStats
            }
            // build84：CPS 改为真实墙钟统计，不再用固定 intervalMs 估算。
            // 这样不会出现界面显示 800/1000 CPS，但实际跑到 FD 需要 100s+ 的错觉。
            val elapsedForStats = (nowForStats - lastStatsAt).coerceAtLeast(1L)
            lastStatsAt = nowForStats
            val cps = (added * 1000L / elapsedForStats).toInt()

            val phase = if (errors.containsKey("FD上限")) "FD上限" else "建连中"
            stats = ProtocolStats(
                protocol = protocol,
                phase = phase,
                resolvedAddresses = addressText,
                activeSessions = active,
                totalFailure = totalFailure.coerceAtMost(failureLimit),
                totalAttempts = totalAttempts,
                lastAdded = added,
                cps = cps,
                errorSummary = errors.toMap(),
                totalSuccess = totalSuccess,
                maxStableSessions = maxStable
            )
            onStats(stats)

            if (maxStable >= fdSafeStop || active >= fdSafeStop || maxStable >= fdEmergencyStop || active >= fdEmergencyStop) {
                errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                stats = stats.copy(phase = "FD上限", errorSummary = errors.toMap(), maxStableSessions = maxStable)
                onStats(stats)
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 触发 Android FD/Socket 上限保护：峰值 $maxStable，活动 $active，停止新增并释放"))
                break
            }

            val successDeltaText = if (batchResult.successSockets.size != targetBatch) "(+${batchResult.successSockets.size})" else ""
            val failureDeltaText = if (failAdd > 0) "(+$failAdd)" else ""
            onLog(LogLine(level = LogLevel.STAT, text = "${protocol.label} 统计 - 成功：$totalSuccess$successDeltaText | 失败：${stats.totalFailure}$failureDeltaText | 活动：$active | 总计：$totalAttempts | 新增：$added | CPS：${cps}/秒 | 失败上限：$failureLimit"))

            if (errors.containsKey("FD上限") || failAdd > 0 && batchResult.errors.containsKey("FD上限")) {
                stats = stats.copy(phase = "FD上限", errorSummary = errors.toMap())
                onStats(stats)
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 检测到 FD上限，停止新增"))
                break
            }

            val now = System.currentTimeMillis()
            if (maxStable < 300 && now - startedAt >= 5_000L && now - lastMeaningfulGrowthAt >= 2_000L && totalFailure >= 40) {
                onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 极低容量快速收尾：峰值 $maxStable，失败 $totalFailure，增长停滞"))
                break
            }

            if (maxStable < 1_000 && now - startedAt >= 6_000L && now - lastMeaningfulGrowthAt >= 3_000L && totalFailure >= 80) {
                onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 低容量快速收尾：峰值 $maxStable，失败 $totalFailure，增长停滞"))
                break
            }

            if (totalFailure >= failureLimit) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到分段失败上限：$failureLimit"))
                break
            }

            delay(config.intervalMs)
        }

        val finalActive = activeCount(protocol)
        val finalLimit = failureLimitFor(maxOf(maxStable, finalActive))
        val finalPeak = maxOf(maxStable, finalActive)
        val finalPhase = when {
            finalPeak >= fdSafeStop || stats.errorSummary.containsKey("FD上限") || stats.phase == "FD上限" -> "FD上限"
            totalFailure >= finalLimit -> "失败上限"
            else -> "测试完成"
        }
        val finalStats = stats.copy(
            phase = finalPhase,
            activeSessions = finalActive,
            totalFailure = totalFailure.coerceAtMost(finalLimit),
            maxStableSessions = finalPeak
        )
        onStats(finalStats)
        if (finalStats.phase == "FD上限") {
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} Android FD/Socket 上限保护：峰值 ${finalStats.maxStableSessions}+，停止新增并释放。"))
        } else {
            onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} ${finalStats.phase} - 活动：${finalStats.activeSessions} 失败：${finalStats.totalFailure} 总计：${finalStats.totalAttempts}"))
        }

        if (!config.keepConnectionsAfterStop) {
            release(protocol)
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 已按设置释放连接。"))
        }
        return finalStats
    }

    private fun batchFor(config: SessionConfig, protocol: IpProtocol, peak: Int): Int {
        // 用户修改新增值时，严格使用用户批次；默认 128 时才做极简自适应。
        if (config.batchSize != 128) return config.batchSize
        return when {
            protocol == IpProtocol.IPV6 -> when {
                peak < 2_000 -> 128
                peak < 6_000 -> 256
                peak < 12_000 -> 512
                else -> 800
            }
            peak >= 6_000 -> 800
            peak >= 3_000 -> 512
            peak >= 1_000 -> 256
            else -> 128
        }.coerceIn(20, 1500)
    }

    private fun failureLimitFor(peak: Int): Int = when {
        peak < 1_000 -> 120
        peak < 6_000 -> 200
        peak < 12_000 -> 360
        else -> 600
    }

    private fun meaningfulGrowthStep(peak: Int): Int = when {
        peak < 1_000 -> 30
        peak < 6_000 -> 80
        peak < 12_000 -> 140
        else -> 220
    }

    private suspend fun openBatchWithFailureBudget(
        addresses: List<InetAddress>,
        port: Int,
        timeoutMs: Int,
        targetCount: Int,
        failureBudget: Int,
        expectedEpoch: Long
    ): BatchOpenResult {
        // build84：这里不能再用 failureBudget 把 chunk 缩到 1、2、3。
        // 之前接近失败上限时会变成单连接串行尝试，导致 CPS 突然掉底并僵直十几秒。
        // 现在保持整批并发发起；失败展示由外层按预算裁剪，命中上限后直接收尾。
        if (targetCount <= 0 || failureBudget <= 0) return BatchOpenResult(emptyList(), emptyMap())
        return openBatch(addresses, port, timeoutMs, targetCount, expectedEpoch)
    }

    private suspend fun openBatch(
        addresses: List<InetAddress>,
        port: Int,
        timeoutMs: Int,
        count: Int,
        expectedEpoch: Long
    ): BatchOpenResult = coroutineScope {
        val jobs = (0 until count).map { index ->
            async(Dispatchers.IO) {
                val address = addresses[index % addresses.size]
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
