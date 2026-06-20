package com.demonv.netsessiontester.network

import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.ResolveResult
import com.demonv.netsessiontester.model.SessionConfig
import com.demonv.netsessiontester.model.TestMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
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
    ): ProtocolStats = coroutineScope {
        release(protocol)
        val addresses = resolveInetAddresses(config.host, protocol)
        if (addresses.isEmpty()) {
            val stats = ProtocolStats(protocol = protocol, phase = "解析失败")
            onStats(stats)
            onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 没有解析到可用地址"))
            return@coroutineScope stats
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
        val pending = mutableListOf<Deferred<OpenResult>>()
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText)
        var stopReason: String? = null
        var cleanProbePassed = config.batchSize <= config.failureLimit
        onStats(stats)

        suspend fun collectCompleted(): Int {
            var completed = 0
            val sockets = mutableListOf<Socket>()
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val job = iterator.next()
                if (!job.isCompleted) continue
                iterator.remove()
                completed++
                val result = runCatching { job.await() }.getOrNull() ?: continue
                result.socket?.let { sockets += it }
                result.error?.let { key ->
                    errors[key] = (errors[key] ?: 0) + 1
                    totalFailure++
                    if (key == "FD上限") stopReason = "FD上限"
                }
            }
            if (sockets.isNotEmpty()) {
                socketLock.withLock { heldSockets.getValue(protocol).addAll(sockets) }
                totalSuccess += sockets.size
            }
            totalAttempts = totalSuccess + totalFailure
            return completed
        }

        suspend fun emitStats(forcePhase: String? = null) {
            val now = System.currentTimeMillis()
            val active = activeCount(protocol)
            maxStable = maxOf(maxStable, active)
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
                val successDelta = totalSuccess - (stats.totalSuccess - 0) // only used for readability below
                val successDeltaText = ""
                val failureDeltaText = if (errors.isNotEmpty()) "" else ""
                onLog(LogLine(level = LogLevel.STAT, text = "${protocol.label} 统计 - 成功：$totalSuccess | 失败：$totalFailure | 活动：$active | 总计：$totalAttempts | 新增：$added | CPS：${cps}/秒"))
            }
        }

        while (currentCoroutineContext().isActive && totalSuccess < config.successLimit && totalFailure < config.failureLimit && stopReason == null) {
            collectCompleted()

            val remainingSuccess = (config.successLimit - totalSuccess - pending.size).coerceAtLeast(0)
            val remainingFailure = (config.failureLimit - totalFailure).coerceAtLeast(0)
            val pendingLimit = maxOf(config.batchSize * 6, 300).coerceAtMost(6000)
            val canLaunchByPending = (pendingLimit - pending.size).coerceAtLeast(0)
            val baseBatch = when {
                !cleanProbePassed -> minOf(config.batchSize, config.failureLimit, 100)
                totalFailure > 0 -> minOf(config.batchSize, (remainingFailure - pending.size).coerceAtLeast(0))
                else -> config.batchSize
            }
            val launchCount = minOf(baseBatch, remainingSuccess, canLaunchByPending)

            if (launchCount > 0) {
                repeat(launchCount) { index ->
                    val address = addresses[(totalAttempts + pending.size + index) % addresses.size]
                    pending += async(Dispatchers.IO) { openOne(address, config.port, config.timeoutMs) }
                }
            }

            val waitUntil = System.currentTimeMillis() + config.intervalMs.coerceAtLeast(50L)
            while (currentCoroutineContext().isActive && System.currentTimeMillis() < waitUntil) {
                collectCompleted()
                if (totalSuccess >= config.successLimit || totalFailure >= config.failureLimit || stopReason != null) break
                delay(25L)
            }
            collectCompleted()

            if (!cleanProbePassed && totalAttempts > 0) {
                cleanProbePassed = totalFailure == 0
            }

            emitStats()

            if (totalFailure >= config.failureLimit) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到失败上限：${config.failureLimit}"))
                break
            }
            if (stopReason == "FD上限") {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 检测到FD上限，已停止继续建连"))
                break
            }
            if (pending.isEmpty() && launchCount <= 0 && remainingSuccess <= 0) break
        }

        pending.forEach { it.cancel() }
        pending.clear()

        val finalActive = activeCount(protocol)
        val finalPhase = if (stopReason == "FD上限") "FD上限" else "测试完成"
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
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 测试完成 - 活动：${finalStats.activeSessions} 失败：${finalStats.totalFailure} 总计：${finalStats.totalAttempts}"))

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
