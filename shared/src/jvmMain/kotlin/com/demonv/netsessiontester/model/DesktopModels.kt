package com.demonv.netsessiontester.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class IpProtocol(val label: String) {
    IPV4("IPv4"),
    IPV6("IPv6")
}

enum class TestMode(val label: String) {
    IPV4_ONLY("仅 IPv4"),
    IPV6_ONLY("仅 IPv6"),
    IPV4_THEN_IPV6("双栈分别轮测")
}

data class SessionConfig(
    val host: String = "www.baidu.com",
    val port: Int = 80,
    val mode: TestMode = TestMode.IPV4_ONLY,
    val batchSize: Int = 500, // CPS
    val intervalMs: Long = 50L,
    val timeoutMs: Int = 1500,
    val successLimit: Int = 10000,
    val failureLimit: Int = 2000,
    val keepConnectionsAfterStop: Boolean = true
) {
    fun normalized(): SessionConfig {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        return copy(
            host = cleanHost,
            port = port.coerceIn(1, 65535),
            batchSize = batchSize.coerceIn(1, 30_000),
            intervalMs = intervalMs.coerceIn(20L, 1_000L),
            timeoutMs = timeoutMs.coerceIn(200, 10_000),
            successLimit = successLimit.coerceIn(10, 100_000),
            failureLimit = failureLimit.coerceIn(10, 100_000)
        )
    }
}

data class ProtocolStats(
    val protocol: IpProtocol,
    val phase: String = "待测试",
    val resolvedAddresses: List<String> = emptyList(),
    val activeSessions: Int = 0,
    val totalSuccess: Int = 0,
    val totalFailure: Int = 0,
    val totalAttempts: Int = 0,
    val lastAdded: Int = 0,
    val cps: Int = 0,
    val maxStableSessions: Int = 0,
    val averageConnectLatencyMs: Int = 0,
    val errorSummary: Map<String, Int> = emptyMap()
)

data class ChartPoint(
    val elapsedSec: Int,
    val active: Int,
    val failure: Int = 0,
    val protocol: IpProtocol = IpProtocol.IPV4
)

enum class LogLevel { INFO, SUCCESS, WARN, ERROR, STAT }

data class LogLine(
    val timeEpochMs: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val text: String
) {
    val timeText: String
        get() = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timeEpochMs))
}

data class ResolveResult(
    val host: String = "",
    val ipv4: List<String> = emptyList(),
    val ipv6: List<String> = emptyList(),
    val error: String? = null
)
