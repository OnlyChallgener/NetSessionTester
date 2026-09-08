package com.demonv.netsessiontester.engine

import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.PingStats
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

/**
 * 跨平台桌面端高精度网络 Ping 探测器。
 * 采用高性能非特权 TCP Ping，无系统管理员权限限制，精准反映真实业务端口 RTT、抖动与丢包。
 */
class DesktopPingTester {

    @Volatile
    private var isRunning = false

    suspend fun pingOnce(host: String, port: Int = 80, timeoutMs: Int = 1000): Int? = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        val socket = Socket()
        try {
            socket.reuseAddress = true
            socket.tcpNoDelay = true
            val startNs = System.nanoTime()
            val address = InetAddress.getByName(cleanHost)
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            latencyMs
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { socket.setSoLinger(true, 0) }
            runCatching { socket.close() }
        }
    }

    suspend fun runContinuousPing(
        host: String,
        port: Int = 80,
        intervalMs: Long = 500L,
        timeoutMs: Int = 1000,
        onStats: suspend (PingStats) -> Unit,
        onLog: suspend (LogLine) -> Unit
    ) = withContext(Dispatchers.IO) {
        isRunning = true
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        onLog(LogLine(level = LogLevel.INFO, text = "启动独立 Ping 诊断: $cleanHost:$port, 探测周期 ${intervalMs}ms"))

        var sent = 0
        var received = 0
        var lost = 0
        var minMs = Int.MAX_VALUE
        var maxMs = 0
        var sumMs = 0L
        var lastRtt: Int? = null
        var jitterSum = 0L
        var jitterSamples = 0

        while (isActive && isRunning) {
            val loopStart = System.currentTimeMillis()
            sent++
            val rtt = pingOnce(cleanHost, port, timeoutMs)

            if (rtt != null) {
                received++
                sumMs += rtt
                if (rtt < minMs) minMs = rtt
                if (rtt > maxMs) maxMs = rtt
                if (lastRtt != null) {
                    val delta = abs(rtt - lastRtt!!)
                    jitterSum += delta
                    jitterSamples++
                }
                lastRtt = rtt
            } else {
                lost++
                onLog(LogLine(level = LogLevel.WARN, text = "Ping 探测超时 [Seq #$sent] -> $cleanHost:$port"))
            }

            val avg = if (received > 0) (sumMs / received).toInt() else 0
            val jitter = if (jitterSamples > 0) (jitterSum / jitterSamples).toInt() else 0
            val lossPercent = if (sent > 0) (lost * 100f / sent) else 0f

            val stats = PingStats(
                host = cleanHost,
                port = port,
                currentLatencyMs = rtt ?: 0,
                minLatencyMs = if (minMs == Int.MAX_VALUE) 0 else minMs,
                maxLatencyMs = maxMs,
                avgLatencyMs = avg,
                jitterMs = jitter,
                sentCount = sent,
                receivedCount = received,
                lostCount = lost,
                lossPercent = lossPercent,
                isRunning = true,
                phase = if (rtt != null) "连通良好 (${rtt}ms)" else "出现超时/丢包"
            )
            onStats(stats)

            val cost = System.currentTimeMillis() - loopStart
            val sleepTime = (intervalMs - cost).coerceAtLeast(50L)
            delay(sleepTime)
        }

        onLog(LogLine(level = LogLevel.SUCCESS, text = "Ping 探测结束: 发送 $sent, 接收 $received, 丢包 $lost (${String.format("%.1f", if (sent > 0) lost * 100f / sent else 0f)}%), 均值 ${if (received > 0) sumMs / received else 0}ms"))
    }

    fun stop() {
        isRunning = false
    }
}
