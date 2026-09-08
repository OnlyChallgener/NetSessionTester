package com.demonv.netsessiontester.engine

import com.demonv.netsessiontester.core.PingAccumulator
import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.PingStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Non-privileged TCP latency probes for desktop targets. */
class DesktopPingTester : AutoCloseable {
    private val stateLock = Any()
    private val generation = AtomicLong(0L)
    private val closed = AtomicBoolean(false)
    private var runningJob: Job? = null
    private val activeChannels = mutableSetOf<SocketChannel>()
    private val activeSelectors = mutableSetOf<Selector>()

    suspend fun pingOnce(
        host: String,
        port: Int = 80,
        timeoutMs: Int = 1000,
        protocol: IpProtocol? = null
    ): Int? = withContext(Dispatchers.IO) {
        check(!closed.get()) { "DesktopPingTester is closed" }
        val address = resolveAddresses(cleanHost(host), protocol).firstOrNull() ?: return@withContext null
        probe(address, port.coerceIn(1, 65535), timeoutMs.coerceAtLeast(1), generation.get())
    }

    suspend fun runContinuousPing(
        host: String,
        port: Int = 80,
        intervalMs: Long = 500L,
        timeoutMs: Int = 1000,
        protocol: IpProtocol? = null,
        onStats: suspend (PingStats) -> Unit,
        onLog: suspend (LogLine) -> Unit
    ) = withContext(Dispatchers.IO) {
        check(!closed.get()) { "DesktopPingTester is closed" }
        val job = currentCoroutineContext()[Job]
        val runGeneration: Long
        synchronized(stateLock) {
            runningJob?.takeIf { it !== job }?.cancel(CancellationException("Ping superseded"))
            runningJob = job
            runGeneration = generation.incrementAndGet()
        }

        val cleanHost = cleanHost(host)
        val cleanPort = port.coerceIn(1, 65535)
        val accumulator = PingAccumulator()
        try {
            val periodNanos = intervalMs.coerceAtLeast(1L) * 1_000_000L
            val timeout = timeoutMs.coerceAtLeast(1)
            val addresses = resolveAddresses(cleanHost, protocol)
            if (addresses.isEmpty()) {
                onLog(LogLine(level = LogLevel.ERROR, text = "${protocol?.label ?: "IP"} 未解析到有效地址: $cleanHost"))
                onStats(toStats(cleanHost, cleanPort, protocol, accumulator, false, "解析失败", System.nanoTime()))
                return@withContext
            }

            onLog(
                LogLine(
                    level = LogLevel.INFO,
                    text = "启动独立 Ping 诊断: $cleanHost:$cleanPort, ${protocol?.label ?: "自动协议"}, 探测周期 ${intervalMs.coerceAtLeast(1L)}ms"
                )
            )

            var nextProbeAt = System.nanoTime()
            var addressIndex = 0
            while (currentCoroutineContext().isActive && generation.get() == runGeneration) {
                val now = System.nanoTime()
                if (now < nextProbeAt) {
                    delay(((nextProbeAt - now) / 1_000_000L).coerceAtLeast(1L))
                    continue
                }

                val address = addresses[addressIndex++ % addresses.size]
                val rtt = probe(address, cleanPort, timeout, runGeneration)
                currentCoroutineContext().ensureActive()
                if (generation.get() != runGeneration) break

                val completedAt = System.nanoTime()
                val summary = accumulator.record(rtt)
                if (rtt == null) {
                    onLog(LogLine(level = LogLevel.WARN, text = "Ping 探测失败 [Seq #${summary.sent}] -> $cleanHost:$cleanPort"))
                }
                onStats(
                    PingStats(
                        host = cleanHost,
                        port = cleanPort,
                        currentLatencyMs = summary.currentMs,
                        minLatencyMs = summary.minMs,
                        maxLatencyMs = summary.maxMs,
                        avgLatencyMs = summary.averageMs,
                        jitterMs = summary.jitterMs,
                        sentCount = summary.sent,
                        receivedCount = summary.received,
                        lostCount = summary.lost,
                        lossPercent = summary.lossPercent,
                        isRunning = true,
                        phase = if (rtt != null) "连通良好 (${rtt}ms)" else "出现超时/丢包",
                        sampleTimeNanos = completedAt,
                        protocol = protocol
                    )
                )

                nextProbeAt += periodNanos
                if (nextProbeAt <= completedAt) {
                    val missed = (completedAt - nextProbeAt) / periodNanos + 1L
                    nextProbeAt += missed * periodNanos
                }
            }
        } finally {
            clearRunningJob(job)
            if (accumulator.summary.sent > 0) {
                withContext(NonCancellable) {
                    onStats(toStats(cleanHost, cleanPort, protocol, accumulator, false, "已停止", 0L))
                }
            }
        }
    }

    /** Cancels the active loop and closes every in-flight probe before returning. */
    fun stop() {
        generation.incrementAndGet()
        val job: Job?
        val channels: List<SocketChannel>
        val selectors: List<Selector>
        synchronized(stateLock) {
            job = runningJob
            runningJob = null
            channels = activeChannels.toList()
            selectors = activeSelectors.toList()
        }
        job?.cancel(CancellationException("Ping stopped"))
        selectors.forEach { runCatching { it.close() } }
        channels.forEach(::closeChannel)
    }

    /** Stops probes and permanently releases this tester. */
    override fun close() {
        if (closed.compareAndSet(false, true)) stop()
    }

    private suspend fun probe(address: InetAddress, port: Int, timeoutMs: Int, expectedGeneration: Long): Int? {
        var channel: SocketChannel? = null
        var selector: Selector? = null
        try {
            currentCoroutineContext().ensureActive()
            if (generation.get() != expectedGeneration) throw CancellationException("Ping stopped")
            channel = SocketChannel.open(familyOf(address))
            channel.configureBlocking(false)
            channel.socket().reuseAddress = true
            channel.socket().tcpNoDelay = true
            selector = Selector.open()
            registerResources(channel, selector, expectedGeneration)

            val startedAt = System.nanoTime()
            if (!channel.connect(InetSocketAddress(address, port))) {
                channel.register(selector, SelectionKey.OP_CONNECT)
                val deadline = startedAt + timeoutMs.toLong() * 1_000_000L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (generation.get() != expectedGeneration) throw CancellationException("Ping stopped")
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0L) return null
                    selector.select(((remaining + 999_999L) / 1_000_000L).coerceIn(1L, 50L))
                    val iterator = selector.selectedKeys().iterator()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        iterator.remove()
                        if (key.isValid && key.isConnectable && channel.finishConnect()) {
                            return elapsedMillis(startedAt, System.nanoTime())
                        }
                    }
                }
            }
            return elapsedMillis(startedAt, System.nanoTime())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            currentCoroutineContext().ensureActive()
            if (generation.get() != expectedGeneration) throw CancellationException("Ping stopped")
            return null
        } finally {
            synchronized(stateLock) {
                channel?.let(activeChannels::remove)
                selector?.let(activeSelectors::remove)
            }
            runCatching { selector?.close() }
            channel?.let(::closeChannel)
        }
    }

    private fun registerResources(channel: SocketChannel, selector: Selector, expectedGeneration: Long) {
        synchronized(stateLock) {
            if (closed.get() || generation.get() != expectedGeneration) {
                closeChannel(channel)
                runCatching { selector.close() }
                throw CancellationException("Ping stopped")
            }
            activeChannels += channel
            activeSelectors += selector
        }
    }

    private fun clearRunningJob(job: Job?) {
        synchronized(stateLock) {
            if (runningJob === job) runningJob = null
        }
    }

    private fun toStats(
        host: String,
        port: Int,
        protocol: IpProtocol?,
        accumulator: PingAccumulator,
        running: Boolean,
        phase: String,
        sampleTimeNanos: Long
    ): PingStats {
        val summary = accumulator.summary
        return PingStats(
            host = host,
            port = port,
            currentLatencyMs = summary.currentMs,
            minLatencyMs = summary.minMs,
            maxLatencyMs = summary.maxMs,
            avgLatencyMs = summary.averageMs,
            jitterMs = summary.jitterMs,
            sentCount = summary.sent,
            receivedCount = summary.received,
            lostCount = summary.lost,
            lossPercent = summary.lossPercent,
            isRunning = running,
            phase = phase,
            sampleTimeNanos = sampleTimeNanos,
            protocol = protocol
        )
    }

    private fun cleanHost(host: String): String =
        host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }

    private suspend fun resolveAddresses(host: String, protocol: IpProtocol?): List<InetAddress> =
        withContext(Dispatchers.IO) {
            runCatching {
                InetAddress.getAllByName(host).filter { address ->
                    when (protocol) {
                        IpProtocol.IPV4 -> address is Inet4Address
                        IpProtocol.IPV6 -> address is Inet6Address
                        null -> address is Inet4Address || address is Inet6Address
                    }
                }.distinctBy { it.hostAddress }
            }.getOrDefault(emptyList())
        }

    private fun familyOf(address: InetAddress) = when (address) {
        is Inet4Address -> StandardProtocolFamily.INET
        is Inet6Address -> StandardProtocolFamily.INET6
        else -> error("Unsupported address family")
    }

    private fun elapsedMillis(startNanos: Long, endNanos: Long): Int =
        ((endNanos - startNanos).coerceAtLeast(0L) / 1_000_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun closeChannel(channel: SocketChannel) {
        runCatching { channel.socket().setSoLinger(true, 0) }
        runCatching { channel.close() }
    }
}
