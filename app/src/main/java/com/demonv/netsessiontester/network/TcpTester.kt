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
import kotlin.math.floor

/**
 * build85：手动 CPS 固定版。
 *
 * 关键原则：
 * - 彻底取消自动 CPS 放大/降速；新增值就是目标 CPS。
 * - UI/日志/曲线只按 1 秒快照刷新，连接事件不直接驱动 UI。
 * - TCP 超时固定 3000ms，避免动态 timeout 干扰结果。
 * - FD/Socket 到 32360 附近直接收尾，且发起前会裁剪新增批次，避免超冲闪退。
 * - 失败阈值按峰值分段：<1000=120，<6000=200，<12000=360，>=12000=600。
 */
class TcpTester {
    private val socketLock = Mutex()
    private val releaseEpoch = AtomicLong(0L)

    private val fdClipStart = 31_800
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
        val addresses = resolveInetAddresses(config.host, protocol)
        if (addresses.isEmpty()) {
            val stats = ProtocolStats(protocol = protocol, phase = "解析失败")
            onStats(stats)
            onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 地址丢失或解析失败"))
            return stats
        }

        val addressText = addresses.mapNotNull { it.hostAddress }.distinct()
        onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 解析成功：${addressText.joinToString(" / ")}"))
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 手动 CPS 固定模式：目标 ${config.batchSize}/s，间隔 ${config.intervalMs}ms，TCP超时固定 3000ms。"))

        var totalLaunched = 0
        var totalSuccess = 0
        var totalFailure = 0
        var totalAttempts = 0
        var maxStable = 0
        var lastEmitLaunched = 0
        var lastEmitSuccess = 0
        var lastEmitAt = System.currentTimeMillis()
        var lastMeaningfulGrowthAt = System.currentTimeMillis()
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText)
        val errors = linkedMapOf<String, Int>()
        val startedAt = System.currentTimeMillis()
        var quotaCarry = 0.0
        onStats(stats)

        suspend fun emitStats(force: Boolean, phase: String = stats.phase) {
            val now = System.currentTimeMillis()
            if (!force && now - lastEmitAt < 1_000L) return
            val elapsed = (now - lastEmitAt).coerceAtLeast(1L)
            val launchDelta = totalLaunched - lastEmitLaunched
            val successDelta = totalSuccess - lastEmitSuccess
            val cps = (launchDelta * 1000L / elapsed).toInt()
            lastEmitLaunched = totalLaunched
            lastEmitSuccess = totalSuccess
            lastEmitAt = now
            val active = activeCount(protocol)
            maxStable = maxOf(maxStable, active)
            stats = ProtocolStats(
                protocol = protocol,
                phase = phase,
                resolvedAddresses = addressText,
                activeSessions = active,
                totalFailure = totalFailure.coerceAtMost(failureLimitFor(maxStable)),
                totalAttempts = totalAttempts,
                lastAdded = successDelta,
                cps = cps,
                errorSummary = errors.toMap(),
                totalSuccess = totalSuccess,
                maxStableSessions = maxStable
            )
            onStats(stats)
            onLog(LogLine(level = LogLevel.STAT, text = "${protocol.label} 统计 - 活动：$active | 峰值：$maxStable | 成功：$totalSuccess | 失败：${stats.totalFailure} | 总计：$totalAttempts | 目标CPS：${config.batchSize}/s | 实际CPS：${cps}/s | 失败上限：${failureLimitFor(maxStable)}"))
        }

        while (currentCoroutineContext().isActive && totalSuccess < config.successLimit) {
            val loopStartedAt = System.currentTimeMillis()
            val activeBefore = activeCount(protocol)
            maxStable = maxOf(maxStable, activeBefore)
            val failureLimit = failureLimitFor(maxStable)

            if (maxStable >= fdSafeStop || activeBefore >= fdSafeStop) {
                errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 触发 Android FD/Socket 上限保护：峰值 $maxStable，活动 $activeBefore，停止新增"))
                break
            }
            if (totalFailure >= failureLimit) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到分段失败上限：$failureLimit"))
                break
            }

            // build85：用户输入的是 CPS，不是每批新增。按调度间隔换算为本批新增数量。
            val exactQuota = config.batchSize.toDouble() * config.intervalMs.toDouble() / 1000.0 + quotaCarry
            var targetBatch = floor(exactQuota).toInt()
            quotaCarry = exactQuota - targetBatch
            if (targetBatch <= 0) targetBatch = 1
            targetBatch = minOf(targetBatch, config.successLimit - totalSuccess)

            // FD 临界区发起前裁剪，避免上一批直接超冲到 32500+ 闪退。
            if (maxStable >= fdClipStart || activeBefore >= fdClipStart) {
                val remainingFdBudget = (fdSafeStop - maxOf(maxStable, activeBefore)).coerceAtLeast(0)
                targetBatch = minOf(targetBatch, remainingFdBudget)
            }
            if (targetBatch <= 0) {
                errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} FD 安全线已满：峰值 $maxStable，活动 $activeBefore，停止新增"))
                break
            }

            val failureBudget = (failureLimit - totalFailure).coerceAtLeast(0)
            if (failureBudget <= 0) break

            totalLaunched += targetBatch
            val batchResult = openBatchWithFailureBudget(
                addresses = addresses,
                port = config.port,
                timeoutMs = 3_000,
                targetCount = targetBatch,
                failureBudget = failureBudget,
                expectedEpoch = expectedEpoch
            )

            val rawFailAdd = batchResult.errors.values.sum()
            val failAdd = rawFailAdd.coerceAtMost(failureBudget)
            totalSuccess += batchResult.successSockets.size
            totalFailure += failAdd
            totalAttempts += batchResult.successSockets.size + failAdd
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
            val successDelta = batchResult.successSockets.size
            if (successDelta >= meaningfulGrowthStep(maxStable)) {
                lastMeaningfulGrowthAt = System.currentTimeMillis()
            }

            val terminalFd = maxStable >= fdSafeStop || active >= fdSafeStop || maxStable >= fdEmergencyStop || active >= fdEmergencyStop || errors.containsKey("FD上限") || batchResult.errors.containsKey("FD上限")
            val terminalFailure = totalFailure >= failureLimitFor(maxStable)
            emitStats(force = terminalFd || terminalFailure, phase = if (terminalFd) "FD上限" else "建连中")

            if (terminalFd) {
                errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 触发 Android FD/Socket 上限保护：峰值 $maxStable，活动 $active，停止新增并释放"))
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
            if (terminalFailure) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到分段失败上限：${failureLimitFor(maxStable)}"))
                break
            }

            val elapsed = System.currentTimeMillis() - loopStartedAt
            val sleepMs = (config.intervalMs - elapsed).coerceAtLeast(0L)
            if (sleepMs > 0L) delay(sleepMs)
        }

        val finalActive = activeCount(protocol)
        val finalLimit = failureLimitFor(maxOf(maxStable, finalActive))
        val finalPeak = maxOf(maxStable, finalActive)
        val finalPhase = when {
            finalPeak >= fdSafeStop || stats.errorSummary.containsKey("FD上限") || stats.phase == "FD上限" || errors.containsKey("FD上限") -> "FD上限"
            totalFailure >= finalLimit -> "失败上限"
            else -> "测试完成"
        }
        val finalStats = stats.copy(
            phase = finalPhase,
            activeSessions = finalActive,
            totalFailure = totalFailure.coerceAtMost(finalLimit),
            maxStableSessions = finalPeak,
            totalSuccess = totalSuccess,
            totalAttempts = totalAttempts,
            errorSummary = errors.toMap()
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
        socketLock.withLock { heldSockets.getValue(protocol).size }
    }

    private data class OpenResult(val socket: Socket? = null, val error: String? = null, val discarded: Boolean = false)
    private data class BatchOpenResult(val successSockets: List<Socket>, val errors: Map<String, Int>)
}
