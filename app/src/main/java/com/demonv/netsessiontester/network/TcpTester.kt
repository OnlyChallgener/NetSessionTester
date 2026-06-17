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
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections

class TcpTester {
    private val socketLock = Mutex()
    private val heldSockets: MutableMap<IpProtocol, MutableList<Socket>> = mutableMapOf(
        IpProtocol.IPV4 to Collections.synchronizedList(mutableListOf()),
        IpProtocol.IPV6 to Collections.synchronizedList(mutableListOf())
    )

    suspend fun resolveHost(host: String): ResolveResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
        if (cleanHost.isBlank()) return@withContext ResolveResult(error = "请填写域名或 IP")
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
        require(config.host.isNotBlank()) { "请填写你自己的 VPS、路由器、内网服务器或已授权服务器地址" }

        var ipv4Stats: ProtocolStats? = null
        var ipv6Stats: ProtocolStats? = null

        when (config.mode) {
            TestMode.IPV4_ONLY -> {
                ipv4Stats = runOneProtocol(config, IpProtocol.IPV4, onStats, onLog)
            }
            TestMode.IPV6_ONLY -> {
                ipv6Stats = runOneProtocol(config, IpProtocol.IPV6, onStats, onLog)
            }
            TestMode.IPV4_THEN_IPV6 -> {
                onLog(LogLine(level = LogLevel.INFO, text = "开始 IPv4 测试，测试完成后会释放 IPv4 连接，再测试 IPv6。"))
                ipv4Stats = runOneProtocol(config.copy(mode = TestMode.IPV4_ONLY), IpProtocol.IPV4, onStats, onLog)
                release(IpProtocol.IPV4)
                delay(1500)
                onLog(LogLine(level = LogLevel.INFO, text = "开始 IPv6 测试。"))
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
            val stats = ProtocolStats(protocol = protocol, phase = "解析失败")
            onStats(stats)
            onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 没有解析到可用地址：${config.host}"))
            return stats
        }

        val addressText = addresses.map { it.hostAddress ?: it.hostName }.distinct()
        onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 解析成功：${addressText.joinToString(" / ")}"))

        var totalSuccess = 0
        var totalFailure = 0
        var totalAttempts = 0
        var maxStable = 0
        var lastTotalAttempts = 0
        val errors = linkedMapOf<String, Int>()
        val started = System.currentTimeMillis()
        var stats = ProtocolStats(
            protocol = protocol,
            phase = "建连中",
            resolvedAddresses = addressText,
            startedAtEpochMs = started
        )

        onStats(stats)

        while (currentCoroutineContext().isActive && totalSuccess < config.successLimit && totalFailure < config.failureLimit) {
            val targetBatch = minOf(config.batchSize, config.successLimit - totalSuccess)
            if (targetBatch <= 0) break
            val batchResult = openBatch(addresses, config.port, config.timeoutMs, targetBatch)

            totalSuccess += batchResult.successSockets.size
            totalFailure += batchResult.errors.values.sum()
            totalAttempts += batchResult.successSockets.size + batchResult.errors.values.sum()
            batchResult.errors.forEach { (key, value) -> errors[key] = (errors[key] ?: 0) + value }

            socketLock.withLock {
                heldSockets.getValue(protocol).addAll(batchResult.successSockets)
            }

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
                totalSuccess = totalSuccess,
                totalFailure = totalFailure,
                totalAttempts = totalAttempts,
                maxStableSessions = maxStable,
                lastAdded = added,
                cps = cps,
                errorSummary = errors.toMap(),
                startedAtEpochMs = started
            )
            onStats(stats)
            onLog(LogLine(level = LogLevel.STAT, text = "${protocol.label} 统计 - 成功：$totalSuccess(+${batchResult.successSockets.size}) | 失败：$totalFailure(+${batchResult.errors.values.sum()}) | 活动：$active | 总计：$totalAttempts | 新增：$added | ${cps}/秒"))

            if (totalFailure >= config.failureLimit) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到失败上限：${config.failureLimit}"))
                break
            }
            if (totalSuccess >= config.successLimit) {
                onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 达到目标成功会话数：${config.successLimit}"))
                break
            }
            delay(config.intervalMs)
        }

        val finalActive = activeCount(protocol)
        val finalStats = stats.copy(
            phase = "测试完成",
            activeSessions = finalActive,
            maxStableSessions = maxOf(maxStable, finalActive),
            finishedAtEpochMs = System.currentTimeMillis()
        )
        onStats(finalStats)
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 测试完成 - 总成功：$totalSuccess 总失败：$totalFailure 当前活动：$finalActive 最大稳定：${finalStats.maxStableSessions}"))

        if (!config.keepConnectionsAfterStop) {
            release(protocol)
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 已按设置自动释放连接。"))
        }
        return finalStats
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
            OpenResult(error = e.javaClass.simpleName)
        }
    }

    private suspend fun resolveInetAddresses(host: String, protocol: IpProtocol): List<InetAddress> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
        InetAddress.getAllByName(cleanHost).filter { address ->
            when (protocol) {
                IpProtocol.IPV4 -> address is Inet4Address
                IpProtocol.IPV6 -> address is Inet6Address
            }
        }.distinctBy { it.hostAddress }
    }

    suspend fun release(protocol: IpProtocol? = null): Int = withContext(Dispatchers.IO) {
        val targets = if (protocol == null) IpProtocol.entries else listOf(protocol)
        var closed = 0
        socketLock.withLock {
            targets.forEach { p ->
                val list = heldSockets.getValue(p)
                list.forEach { socket ->
                    runCatching { socket.close() }
                    closed++
                }
                list.clear()
            }
        }
        closed
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
