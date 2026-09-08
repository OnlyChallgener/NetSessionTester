package com.demonv.netsessiontester.engine

import com.demonv.netsessiontester.model.ChartPoint
import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.ResolveResult
import com.demonv.netsessiontester.model.SessionConfig
import com.demonv.netsessiontester.model.TestMode
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.floor

/**
 * 适用于 Windows / macOS 桌面端的高性能并发 Socket 测试引擎。
 * 充分利用桌面 OS 无严格移动端沙盒限制的特性，支持数千至上万级别并发 Socket 建连与维持。
 */
class DesktopTcpTester {
    private val socketLock = Mutex()
    private val releaseEpoch = AtomicLong(0L)
    private val testerDispatcher = Dispatchers.IO.limitedParallelism(1024)

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
                delay(300L)
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
            onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 域名未解析到有效地址"))
            return stats
        }

        val addressText = addresses.mapNotNull { it.hostAddress }.distinct()
        val targetCps = config.batchSize.coerceIn(10, 20_000)
        val schedulerIntervalMs = config.intervalMs.coerceIn(20L, 500L)
        val maxPending = (targetCps * 4).coerceIn(500, 10_000)

        onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} 解析成功：${addressText.joinToString(" / ")}"))
        onLog(LogLine(level = LogLevel.INFO, text = "${protocol.label} 桌面端测试开始：目标速率 $targetCps CPS，上限 ${config.successLimit} 连接"))

        val pendingScope = CoroutineScope(SupervisorJob() + testerDispatcher)
        val resultChannel = Channel<OpenResult>(Channel.UNLIMITED)
        val pendingCount = AtomicInteger(0)
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

        var stats = ProtocolStats(
            protocol = protocol,
            phase = "建连中",
            resolvedAddresses = addressText,
            lastAdded = targetCps
        )
        onStats(stats)

        suspend fun drainCompleted(): Int {
            var drained = 0
            val sockets = mutableListOf<Socket>()
            while (true) {
                val pollResult = resultChannel.tryReceive()
                if (!pollResult.isSuccess) break
                val result = pollResult.getOrNull() ?: break
                drained++
                pendingCount.decrementAndGet()
                if (result.discarded) continue
                result.socket?.let { socket ->
                    sockets += socket
                    result.connectLatencyMs?.let { latency ->
                        totalConnectLatencyMs += latency.toLong().coerceAtLeast(0L)
                        connectLatencySamples++
                    }
                }
                result.error?.let { error ->
                    errors[error] = (errors[error] ?: 0) + 1
                }
            }
            if (sockets.isNotEmpty()) {
                totalSuccess += sockets.size
                socketLock.withLock {
                    if (releaseEpoch.get() == expectedEpoch) {
                        heldSockets.getValue(protocol).addAll(sockets)
                    } else {
                        sockets.forEach { fastClose(it) }
                    }
                }
            }
            totalFailure = errors.values.sum()
            maxStable = maxOf(maxStable, totalSuccess)
            return drained
        }

        fun launchOne(timeoutMs: Int) {
            val address = addresses[addressOffset % addresses.size]
            addressOffset++
            launchedAttempts++
            pendingCount.incrementAndGet()
            pendingScope.launch {
                try {
                    val res = openOne(address, config.port, timeoutMs, expectedEpoch)
                    resultChannel.send(res)
                } catch (_: CancellationException) {
                    resultChannel.send(OpenResult(discarded = true))
                } catch (t: Throwable) {
                    resultChannel.send(OpenResult(error = classifyError(t)))
                }
            }
        }

        try {
            while (currentCoroutineContext().isActive && totalSuccess < config.successLimit) {
                val loopStart = System.currentTimeMillis()
                drainCompleted()

                if (totalFailure >= config.failureLimit) {
                    stats = stats.copy(phase = "失败上限", errorSummary = errors.toMap(), maxStableSessions = maxStable)
                    onStats(stats)
                    onLog(LogLine(level = LogLevel.ERROR, text = "${protocol.label} 达到失败上限 $totalFailure，测试中止"))
                    break
                }

                val now = System.currentTimeMillis()
                val elapsedTokenMs = (now - lastTokenAt).coerceAtLeast(0L)
                lastTokenAt = now
                val inFlight = pendingCount.get()
                tokenCarry += targetCps.toDouble() * elapsedTokenMs.toDouble() / 1000.0
                var toLaunch = floor(tokenCarry).toInt()
                if (toLaunch > 0) tokenCarry -= toLaunch.toDouble()

                if (toLaunch > 0) {
                    val remainingSuccess = (config.successLimit - totalSuccess - inFlight).coerceAtLeast(0)
                    val pendingRoom = (maxPending - inFlight).coerceAtLeast(0)
                    toLaunch = minOf(toLaunch, remainingSuccess, pendingRoom)
                    repeat(toLaunch) { launchOne(config.timeoutMs) }
                }

                val uiNow = System.currentTimeMillis()
                if (uiNow - lastUiAt >= 200L) { // 桌面端支持更高频率流畅刷新 (5 FPS)
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
                        totalAttempts = totalSuccess + totalFailure,
                        lastAdded = targetCps,
                        cps = lastCps,
                        maxStableSessions = maxStable,
                        averageConnectLatencyMs = if (connectLatencySamples > 0) (totalConnectLatencyMs / connectLatencySamples).toInt() else 0,
                        errorSummary = errors.toMap()
                    )
                    onStats(stats)
                }

                val cost = System.currentTimeMillis() - loopStart
                val sleep = schedulerIntervalMs - cost
                if (sleep > 0) delay(sleep) else yield()
            }
            drainCompleted()
        } finally {
            releaseEpoch.compareAndSet(expectedEpoch, expectedEpoch + 1)
            pendingScope.cancel()
            resultChannel.close()
        }

        val totalElapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val avgCps = (totalSuccess * 1000L / totalElapsedMs).toInt().coerceAtLeast(0)
        val finalPhase = if (totalSuccess >= config.successLimit) "测试完成" else if (totalFailure > 0) "出现失败" else "已停止"

        val finalStats = stats.copy(
            phase = finalPhase,
            activeSessions = totalSuccess,
            totalSuccess = totalSuccess,
            totalFailure = totalFailure,
            totalAttempts = totalSuccess + totalFailure,
            cps = avgCps,
            maxStableSessions = maxStable,
            averageConnectLatencyMs = if (connectLatencySamples > 0) (totalConnectLatencyMs / connectLatencySamples).toInt() else 0,
            errorSummary = errors.toMap()
        )
        onStats(finalStats)
        onLog(LogLine(level = LogLevel.SUCCESS, text = "${protocol.label} $finalPhase：峰值活动 $maxStable | 成功 $totalSuccess | 失败 $totalFailure | 平均速率 $avgCps/s"))

        if (!config.keepConnectionsAfterStop) {
            release(protocol)
            onLog(LogLine(level = LogLevel.WARN, text = "${protocol.label} 连接已全部释放"))
        }

        return finalStats
    }

    private fun openOne(address: InetAddress, port: Int, timeoutMs: Int, expectedEpoch: Long): OpenResult {
        var socket: Socket? = null
        return try {
            if (releaseEpoch.get() != expectedEpoch) return OpenResult(discarded = true)
            val candidate = Socket()
            socket = candidate
            candidate.reuseAddress = true
            candidate.receiveBufferSize = 4096
            candidate.sendBufferSize = 4096
            candidate.tcpNoDelay = true
            val startNs = System.nanoTime()
            candidate.connect(InetSocketAddress(address, port), timeoutMs)
            val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            if (releaseEpoch.get() != expectedEpoch) {
                fastClose(candidate)
                OpenResult(discarded = true)
            } else {
                OpenResult(socket = candidate, connectLatencyMs = latencyMs)
            }
        } catch (e: CancellationException) {
            socket?.let(::fastClose)
            throw e
        } catch (t: Throwable) {
            socket?.let(::fastClose)
            OpenResult(error = classifyError(t))
        }
    }

    private fun fastClose(socket: Socket) {
        runCatching { socket.setSoLinger(true, 0) }
        runCatching { socket.close() }
    }

    private fun classifyError(e: Throwable): String {
        val msg = e.message.orEmpty().lowercase()
        return when (e) {
            is SocketTimeoutException -> "超时"
            is ConnectException -> when {
                "refused" in msg -> "拒绝"
                "timed out" in msg -> "超时"
                else -> "连接失败"
            }
            is NoRouteToHostException -> "无路由"
            is PortUnreachableException -> "端口不可达"
            is UnknownHostException -> "DNS失败"
            is SocketException -> when {
                "too many open files" in msg || "emfile" in msg -> "FD上限"
                "cannot assign requested address" in msg -> "端口耗尽"
                "connection reset" in msg -> "重置"
                else -> "Socket异常"
            }
            else -> e.javaClass.simpleName
        }
    }

    private suspend fun resolveInetAddresses(host: String, protocol: IpProtocol): List<InetAddress> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        runCatching {
            InetAddress.getAllByName(cleanHost).filter { address ->
                when (protocol) {
                    IpProtocol.IPV4 -> address is Inet4Address
                    IpProtocol.IPV6 -> address is Inet6Address
                }
            }.distinctBy { it.hostAddress }.take(8)
        }.getOrDefault(emptyList())
    }

    suspend fun release(protocol: IpProtocol? = null): Int = withContext(Dispatchers.IO) {
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
        socketsToClose.forEach { fastClose(it) }
        socketsToClose.size
    }

    private data class OpenResult(
        val socket: Socket? = null,
        val error: String? = null,
        val discarded: Boolean = false,
        val connectLatencyMs: Int? = null
    )
}
