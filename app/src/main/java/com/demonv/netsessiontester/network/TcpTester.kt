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

class TcpTester {
    private val socketLock = Mutex()
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
        val addresses = resolveInetAddresses(config.host, protocol)
        if (addresses.isEmpty()) {
            val stats = ProtocolStats(protocol = protocol, phase = "地址丢失")
            onStats(stats)
            onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 地址丢失或解析失败"))
            throw IllegalStateException("${protocol.label} 地址丢失或解析失败")
        }

        val addressText = addresses.mapNotNull { it.hostAddress }.distinct()
        onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 解析成功：${addressText.joinToString(" / ")}"))

        var totalSuccess = 0
        var totalFailure = 0
        var totalAttempts = 0
        var maxStable = 0
        var lastTotalAttempts = 0
        val errors = linkedMapOf<String, Int>()
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText)
        onStats(stats)

        while (currentCoroutineContext().isActive && totalSuccess < config.successLimit && totalFailure < config.failureLimit) {
            val remainingSuccess = config.successLimit - totalSuccess
            val targetBatch = minOf(config.batchSize, remainingSuccess)
            if (targetBatch <= 0) break

            // 不再用 failureLimit 直接限制每批新增，避免“新增”被固定成 200。
            // 逻辑：无失败时可按用户设置的 500/1000 批量新增；一旦出现失败，后续自动缩小子批次，
            // 让最终失败数尽量靠近失败停值，而不是大幅超出。
            val batchResult = openBatchWithFailureBudget(
                addresses = addresses,
                port = config.port,
                timeoutMs = config.timeoutMs,
                targetCount = targetBatch,
                failureBudget = (config.failureLimit - totalFailure).coerceAtLeast(0)
            )

            totalSuccess += batchResult.successSockets.size
            val failAdd = batchResult.errors.values.sum()
            totalFailure += failAdd
            totalAttempts += batchResult.successSockets.size + failAdd
            batchResult.errors.forEach { (key, value) -> errors[key] = (errors[key] ?: 0) + value }

            socketLock.withLock { heldSockets.getValue(protocol).addAll(batchResult.successSockets) }

            val active = activeCount(protocol)
            maxStable = maxOf(maxStable, active)
            val added = totalAttempts - lastTotalAttempts
            lastTotalAttempts = totalAttempts
            val cps = (added * 1000L / config.intervalMs.coerceAtLeast(1L)).toInt()

            stats = ProtocolStats(
                protocol = protocol,
                phase = "建连中",
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
            val successDeltaText = if (batchResult.successSockets.size != config.batchSize) "(+${batchResult.successSockets.size})" else ""
            val failureDeltaText = if (failAdd > 0) "(+$failAdd)" else ""
            onLog(LogLine(level = LogLevel.STAT, text = "${protocol.label} 统计 - 成功：$totalSuccess$successDeltaText | 失败：$totalFailure$failureDeltaText | 活动：$active | 总计：$totalAttempts | 新增：$added | CPS：${cps}/秒"))

            if (totalFailure >= config.failureLimit) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到失败上限：${config.failureLimit}"))
                break
            }
            delay(config.intervalMs)
        }

        val finalActive = activeCount(protocol)
        val finalStats = stats.copy(
            phase = "测试完成",
            activeSessions = finalActive,
            maxStableSessions = maxOf(maxStable, finalActive)
        )
        onStats(finalStats)
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 测试完成 - 活动：${finalStats.activeSessions} 失败：${finalStats.totalFailure} 总计：${finalStats.totalAttempts}"))

        if (!config.keepConnectionsAfterStop) {
            release(protocol)
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 已按设置释放连接。"))
        }
        return finalStats
    }

    private suspend fun openBatchWithFailureBudget(
        addresses: List<InetAddress>,
        port: Int,
        timeoutMs: Int,
        targetCount: Int,
        failureBudget: Int
    ): BatchOpenResult {
        val allSockets = mutableListOf<Socket>()
        val allErrors = linkedMapOf<String, Int>()
        var attempted = 0
        var failed = 0
        var cautious = false

        while (currentCoroutineContext().isActive && attempted < targetCount && failed < failureBudget) {
            val remainingTarget = targetCount - attempted
            val remainingFailureBudget = failureBudget - failed

            // 正常网络下用 100 并发子批次，出现失败后降到 25，避免失败数冲太多。
            val maxChunk = if (cautious) 25 else 100
            val chunkSize = minOf(remainingTarget, maxChunk, remainingFailureBudget.coerceAtLeast(1))
            if (chunkSize <= 0) break

            val result = openBatch(addresses, port, timeoutMs, chunkSize)
            val failAdd = result.errors.values.sum()

            allSockets += result.successSockets
            result.errors.forEach { (key, value) -> allErrors[key] = (allErrors[key] ?: 0) + value }

            attempted += result.successSockets.size + failAdd
            failed += failAdd
            if (failAdd > 0) cautious = true
        }

        return BatchOpenResult(allSockets, allErrors)
    }

    private suspend fun openBatch(
        addresses: List<InetAddress>,
        port: Int,
        timeoutMs: Int,
        count: Int
    ): BatchOpenResult = coroutineScope {
        val jobs = (0 until count).map { index ->
            async(Dispatchers.IO) {
                val address = addresses[index % addresses.size]
                openOne(address, port, timeoutMs)
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
    }

    private fun openOne(address: InetAddress, port: Int, timeoutMs: Int): OpenResult {
        return try {
            val socket = Socket()
            socket.keepAlive = true
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            OpenResult(socket = socket)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OpenResult(error = classifyError(e))
        }
    }

    private fun classifyError(e: Exception): String {
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

    suspend fun release(protocol: IpProtocol? = null): Int = withContext(Dispatchers.IO) {
        val targets = if (protocol == null) IpProtocol.entries else listOf(protocol)
        val socketsToClose = mutableListOf<Socket>()

        socketLock.withLock {
            targets.forEach { p ->
                val list = heldSockets.getValue(p)
                socketsToClose.addAll(list)
                list.clear()
            }
        }

        // 普通并发释放：不启用 SO_LINGER(0)，避免 RST 影响释放方式真实性。
        // 分批并发 close，降低大量 socket 顺序关闭导致的释放延迟。
        coroutineScope {
            socketsToClose.chunked(300).forEach { batch ->
                batch.map { socket ->
                    async(Dispatchers.IO) {
                        runCatching { socket.close() }
                    }
                }.awaitAll()
            }
        }
        socketsToClose.size
    }

    suspend fun activeCount(protocol: IpProtocol): Int = withContext(Dispatchers.IO) {
        socketLock.withLock {
            val list = heldSockets.getValue(protocol)
            list.removeAll { it.isClosed }
            list.size
        }
    }

    private data class OpenResult(val socket: Socket? = null, val error: String? = null)
    private data class BatchOpenResult(val successSockets: List<Socket>, val errors: Map<String, Int>)
}
