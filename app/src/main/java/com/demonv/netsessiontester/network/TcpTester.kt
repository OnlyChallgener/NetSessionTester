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

class TcpTester {
    private val socketLock = Mutex()
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

        var totalSuccess = 0
        var totalFailure = 0
        var totalAttempts = 0
        var maxStable = 0
        var lastTotalAttempts = 0
        var lastStatsAt = System.currentTimeMillis()
        val errors = linkedMapOf<String, Int>()
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText)
        var fdSeen = false
        onStats(stats)

        fun recordFailure(key: String) {
            if (key == "FD上限") fdSeen = true
            if (totalFailure >= config.failureLimit) return
            totalFailure++
            errors[key] = (errors[key] ?: 0) + 1
        }

        suspend fun emitStats(forcePhase: String? = null) {
            val now = System.currentTimeMillis()
            val active = activeCount(protocol)
            maxStable = maxOf(maxStable, active)
            totalAttempts = totalSuccess + totalFailure
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
                onLog(LogLine(level = LogLevel.STAT, text = "${protocol.label} 统计 - 成功：$totalSuccess | 失败：$totalFailure | 活动：$active | 总计：$totalAttempts | 新增：$added | CPS：${cps}/秒"))
            }
        }

        while (currentCoroutineContext().isActive && releaseEpoch.get() == protocolEpoch && totalSuccess < config.successLimit && totalFailure < config.failureLimit) {
            val remainingSuccess = config.successLimit - totalSuccess
            val remainingFailure = config.failureLimit - totalFailure
            if (remainingSuccess <= 0 || remainingFailure <= 0) break

            // 速度优先：没有接近失败上限时按用户设定批量并发。
            // 只有最后一段才按剩余失败值收缩，避免 0.9.8 的 pending 预限速造成卡顿和只能测到一万多。
            val nearFailureCap = totalFailure >= (config.failureLimit * 8 / 10)
            val launchCount = if (nearFailureCap) {
                minOf(config.batchSize, remainingSuccess, remainingFailure)
            } else {
                minOf(config.batchSize, remainingSuccess)
            }
            if (launchCount <= 0) break

            val batch = openBatch(addresses, config.port, config.timeoutMs.coerceIn(300, 800), launchCount)
            if (!currentCoroutineContext().isActive || releaseEpoch.get() != protocolEpoch) {
                batch.successSockets.forEach { socket -> runCatching { socket.close() } }
                break
            }
            totalSuccess += batch.successSockets.size
            socketLock.withLock {
                if (releaseEpoch.get() == protocolEpoch) {
                    heldSockets.getValue(protocol).addAll(batch.successSockets)
                } else {
                    batch.successSockets.forEach { socket -> runCatching { socket.close() } }
                }
            }
            batch.errors.forEach { (key, value) ->
                repeat(value) { recordFailure(key) }
            }
            totalAttempts = totalSuccess + totalFailure

            emitStats()

            if (fdSeen) {
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
        val finalPhase = if (fdSeen) "FD上限" else "测试完成"
        val finalStats = stats.copy(
            phase = finalPhase,
            activeSessions = finalActive,
            maxStableSessions = maxOf(maxStable, finalActive),
            totalSuccess = totalSuccess,
            totalFailure = totalFailure.coerceAtMost(config.failureLimit),
            totalAttempts = totalSuccess + totalFailure.coerceAtMost(config.failureLimit),
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
        releaseEpoch.incrementAndGet()
        val targets = if (protocol == null) IpProtocol.entries else listOf(protocol)
        val socketsToClose = mutableListOf<Socket>()

        // 先从持有列表中移除，再并发关闭。
        // 这样 UI/activeCount 会立即变为 0，路由器端会话表再等待系统慢慢下降。
        socketLock.withLock {
            targets.forEach { p ->
                val list = heldSockets.getValue(p)
                socketsToClose += list.toList()
                list.clear()
            }
        }

        if (socketsToClose.isEmpty()) return@withContext 0

        coroutineScope {
            socketsToClose.chunked(256).map { chunk ->
                async(Dispatchers.IO) {
                    var closed = 0
                    chunk.forEach { socket ->
                        runCatching { socket.close() }
                        closed++
                    }
                    closed
                }
            }.awaitAll().sum()
        }
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
