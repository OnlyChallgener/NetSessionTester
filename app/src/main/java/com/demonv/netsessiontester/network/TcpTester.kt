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
    private companion object {
        // 高并发会话测试不能被长连接超时拖慢；用户设置更低时尊重用户设置。
        const val MAX_EFFECTIVE_CONNECT_TIMEOUT_MS = 800
    }

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

        val effectiveTimeoutMs = config.timeoutMs.coerceAtMost(MAX_EFFECTIVE_CONNECT_TIMEOUT_MS)
        if (effectiveTimeoutMs != config.timeoutMs) {
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 高并发连接超时已限制为 ${effectiveTimeoutMs}ms，避免长超时拖慢批次"))
        }

        while (currentCoroutineContext().isActive && totalSuccess < config.successLimit && totalFailure < config.failureLimit) {
            val remainingSuccess = config.successLimit - totalSuccess
            val targetBatch = minOf(config.batchSize, remainingSuccess)
            if (targetBatch <= 0) break

            // 0.9.7 高速核心：不做 pending 预限速，不按失败额度拆小批次。
            // 失败停是停止条件，不是提前限速条件；这样 IPv6 可继续冲到 Android FD/Socket 附近。
            val batchResult = openBatch(addresses, config.port, effectiveTimeoutMs, targetBatch)

            totalSuccess += batchResult.successSockets.size
            val rawFailAdd = batchResult.errors.values.sum()
            val failAdd = rawFailAdd.coerceAtMost((config.failureLimit - totalFailure).coerceAtLeast(0))
            totalFailure += failAdd
            totalAttempts += batchResult.successSockets.size + failAdd
            mergeErrorsCapped(errors, batchResult.errors, failAdd)

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
            val failureDeltaText = when {
                rawFailAdd > failAdd && failAdd > 0 -> "(+$failAdd)"
                failAdd > 0 -> "(+$failAdd)"
                else -> ""
            }
            onLog(LogLine(level = LogLevel.STAT, text = "${protocol.label} 统计 - 成功：$totalSuccess$successDeltaText | 失败：$totalFailure$failureDeltaText | 活动：$active | 总计：$totalAttempts | 新增：$added | CPS：${cps}/秒"))

            if (batchResult.errors.containsKey("FD上限")) {
                stats = stats.copy(
                    phase = "FD上限",
                    errorSummary = errors.toMap()
                )
                onStats(stats)
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 检测到 Android FD上限，已停止新增并保存历史"))
                break
            }

            if (totalFailure >= config.failureLimit) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到失败上限：${config.failureLimit}"))
                break
            }
            delay(config.intervalMs)
        }

        val finalActive = activeCount(protocol)
        val finalPhase = when {
            stats.errorSummary.containsKey("FD上限") || stats.phase == "FD上限" -> "FD上限"
            else -> "测试完成"
        }
        val finalStats = stats.copy(
            phase = finalPhase,
            activeSessions = finalActive,
            maxStableSessions = maxOf(maxStable, finalActive)
        )
        onStats(finalStats)
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} ${finalStats.phase} - 活动：${finalStats.activeSessions} 失败：${finalStats.totalFailure} 总计：${finalStats.totalAttempts}"))

        if (!config.keepConnectionsAfterStop) {
            release(protocol)
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 已按设置释放连接。"))
        }
        return finalStats
    }

    private fun mergeErrorsCapped(
        target: MutableMap<String, Int>,
        source: Map<String, Int>,
        maxToAdd: Int
    ) {
        var remaining = maxToAdd
        if (remaining <= 0) return
        source.forEach { (key, value) ->
            if (remaining <= 0) return@forEach
            val add = minOf(value, remaining)
            if (add > 0) {
                target[key] = (target[key] ?: 0) + add
                remaining -= add
            }
        }
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
