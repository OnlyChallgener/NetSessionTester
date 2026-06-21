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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TcpTester {
    private companion object {
        // 高并发会话测试不能被长连接超时拖慢；用户设置更低时尊重用户设置。
        const val MAX_EFFECTIVE_CONNECT_TIMEOUT_MS = 800
        // 活动会话超过这个值后，失败上限不再提前结束，继续追 FD 上限。
        const val FD_CHASE_THRESHOLD = 5000
        // 高活动会话后如果失败已到上限，不再按失败停提前结束；继续冲 FD。
        // 若高位连续几轮没有任何新增成功，很多 Android 机型不会明确抛 EMFILE，
        // 这里按“接近 FD 上限并停滞”归类为 FD 上限，避免误判为普通失败。
        const val FD_NEAR_ACTIVE_THRESHOLD = 28000
        const val FD_STAGNANT_ROUNDS = 3
        const val LOW_FAILURE_STOP_ACTIVE_THRESHOLD = 1200
    }

    private val socketLock = Mutex()
    private val heldSockets: MutableMap<IpProtocol, MutableList<Socket>> = mutableMapOf(
        IpProtocol.IPV4 to Collections.synchronizedList(mutableListOf()),
        IpProtocol.IPV6 to Collections.synchronizedList(mutableListOf())
    )

    /**
     * 高并发失败计数门：
     * - 用 CAS 精准封顶，失败停=200 时 UI/历史最多显示 200。
     * - 失败上限只是“是否停止新增”的判断信号，不提前限速、不提前关闭 pending。
     * - 当活动会话已经很高时继续冲 FD 上限；活动很低时按失败上限停止。
     */
    private class FailureGate(private val limit: Int) {
        private val count = AtomicInteger(0)
        private val reachedLimit = AtomicBoolean(false)

        fun value(): Int = count.get().coerceAtMost(limit)
        fun isLimitReached(): Boolean = reachedLimit.get()

        /**
         * @return true 表示这一次失败被计入；超过上限后的失败不再计数。
         */
        fun recordFailure(): Boolean {
            while (true) {
                val current = count.get()
                if (current >= limit) {
                    reachedLimit.set(true)
                    return false
                }
                val next = current + 1
                if (count.compareAndSet(current, next)) {
                    if (next >= limit) reachedLimit.set(true)
                    return true
                }
            }
        }
    }

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
        var stagnantAfterFailureLimitRounds = 0
        val errors = linkedMapOf<String, Int>()
        var stats = ProtocolStats(protocol = protocol, phase = "建连中", resolvedAddresses = addressText)
        val failureGate = FailureGate(config.failureLimit)
        onStats(stats)

        val effectiveTimeoutMs = config.timeoutMs.coerceAtMost(MAX_EFFECTIVE_CONNECT_TIMEOUT_MS)
        if (effectiveTimeoutMs != config.timeoutMs) {
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 高并发连接超时已限制为 ${effectiveTimeoutMs}ms，避免长超时拖慢批次"))
        }

        while (currentCoroutineContext().isActive && totalSuccess < config.successLimit) {
            val remainingSuccess = config.successLimit - totalSuccess
            val targetBatch = minOf(config.batchSize, remainingSuccess)
            if (targetBatch <= 0) break

            // 0.9.7 高速核心：不做 pending 预限速，不按失败额度拆小批次。
            // 失败停是停止条件，不是提前限速条件；这样 IPv6 可继续冲到 Android FD/Socket 附近。
            val batchResult = openBatch(addresses, config.port, effectiveTimeoutMs, targetBatch, failureGate)

            totalSuccess += batchResult.successSockets.size
            val previousFailure = totalFailure
            totalFailure = failureGate.value()
            val failAdd = (totalFailure - previousFailure).coerceAtLeast(0)
            totalAttempts += batchResult.successSockets.size + failAdd
            mergeErrors(errors, batchResult.errors)

            socketLock.withLock { heldSockets.getValue(protocol).addAll(batchResult.successSockets) }

            val active = activeCount(protocol)
            maxStable = maxOf(maxStable, active)
            val added = totalAttempts - lastTotalAttempts
            lastTotalAttempts = totalAttempts
            val cps = (added * 1000L / config.intervalMs.coerceAtLeast(1L)).toInt()
            stagnantAfterFailureLimitRounds = if (failureGate.isLimitReached() && batchResult.successSockets.isEmpty()) {
                stagnantAfterFailureLimitRounds + 1
            } else {
                0
            }

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

            if (batchResult.fdLimitDetected) {
                stats = stats.copy(
                    phase = "FD上限",
                    errorSummary = errors.toMap()
                )
                onStats(stats)
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 检测到 Android FD上限，已停止新增并保存历史"))
                break
            }

            // 失败上限策略：
            // - 失败数用 CAS 封顶，只作为显示和低会话网络的停止条件。
            // - 活动会话已明显进入高位后，不再因为失败数到上限就提前结束，继续追 Android FD/Socket 上限。
            // - 部分 Android 机型接近 FD 上限时不会稳定抛出 EMFILE，而是表现为超时/Socket异常；
            //   高位且连续无新增成功时，按 FD 上限处理，避免误判为普通失败。
            if (failureGate.isLimitReached()) {
                val shouldStopAsLowFailure = active < LOW_FAILURE_STOP_ACTIVE_THRESHOLD && totalSuccess < FD_CHASE_THRESHOLD
                val shouldTreatAsFdLimit = active >= FD_NEAR_ACTIVE_THRESHOLD && stagnantAfterFailureLimitRounds >= FD_STAGNANT_ROUNDS

                if (shouldTreatAsFdLimit) {
                    errors["FD上限"] = (errors["FD上限"] ?: 0) + 1
                    stats = stats.copy(
                        phase = "FD上限",
                        errorSummary = errors.toMap()
                    )
                    onStats(stats)
                    onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 接近 Android FD上限并连续无新增，按FD上限停止：活动 $active 失败 ${failureGate.value()}"))
                    break
                }

                if (shouldStopAsLowFailure) {
                    onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到失败上限：${config.failureLimit}"))
                    break
                }
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

    private fun mergeErrors(
        target: MutableMap<String, Int>,
        source: Map<String, Int>
    ) {
        source.forEach { (key, value) ->
            if (value > 0) target[key] = (target[key] ?: 0) + value
        }
    }

    private suspend fun openBatch(
        addresses: List<InetAddress>,
        port: Int,
        timeoutMs: Int,
        count: Int,
        failureGate: FailureGate
    ): BatchOpenResult = coroutineScope {
        val pendingSockets = ConcurrentHashMap.newKeySet<Socket>()
        val successSockets = Collections.synchronizedList(mutableListOf<Socket>())
        val errorCounters = ConcurrentHashMap<String, AtomicInteger>()
        val fdLimitDetected = AtomicBoolean(false)

        fun addError(name: String) {
            errorCounters.getOrPut(name) { AtomicInteger(0) }.incrementAndGet()
        }

        fun closePendingSockets() {
            pendingSockets.forEach { socket -> runCatching { socket.close() } }
        }

        val jobs = (0 until count).map { index ->
            async(Dispatchers.IO) {
                val address = addresses[index % addresses.size]
                val result = openOneTracked(address, port, timeoutMs, pendingSockets)

                result.socket?.let { socket ->
                    if (!fdLimitDetected.get()) {
                        successSockets.add(socket)
                    } else {
                        runCatching { socket.close() }
                    }
                    return@async
                }

                val error = result.error ?: return@async
                if (error == "FD上限") {
                    addError(error)
                    fdLimitDetected.set(true)
                    closePendingSockets()
                    return@async
                }

                if (failureGate.recordFailure()) {
                    addError(error)
                }
            }
        }
        jobs.awaitAll()

        BatchOpenResult(
            successSockets = successSockets.toList(),
            errors = errorCounters.mapValues { it.value.get() },
            fdLimitDetected = fdLimitDetected.get()
        )
    }

    private fun openOneTracked(
        address: InetAddress,
        port: Int,
        timeoutMs: Int,
        pendingSockets: MutableSet<Socket>
    ): OpenResult {
        val socket = try {
            Socket()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return OpenResult(error = classifyError(e))
        }

        pendingSockets.add(socket)
        return try {
            socket.keepAlive = true
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            pendingSockets.remove(socket)
            OpenResult(socket = socket)
        } catch (e: CancellationException) {
            pendingSockets.remove(socket)
            runCatching { socket.close() }
            throw e
        } catch (e: Exception) {
            pendingSockets.remove(socket)
            runCatching { socket.close() }
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
    private data class BatchOpenResult(
        val successSockets: List<Socket>,
        val errors: Map<String, Int>,
        val fdLimitDetected: Boolean = false
    )
}
