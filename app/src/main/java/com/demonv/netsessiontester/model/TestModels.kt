package com.demonv.netsessiontester.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class IpProtocol(val label: String) { IPV4("IPv4"), IPV6("IPv6") }

enum class TestMode(val label: String) {
    IPV4_ONLY("仅 IPv4"),
    IPV6_ONLY("仅 IPv6"),
    IPV4_THEN_IPV6("分别测试")
}

data class SessionConfig(
    val host: String,
    val port: Int,
    val mode: TestMode,
    val batchSize: Int,
    val intervalMs: Long,
    val timeoutMs: Int,
    val successLimit: Int,
    val failureLimit: Int,
    val keepConnectionsAfterStop: Boolean
) {
    fun normalized(): SessionConfig {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        return copy(
            host = cleanHost,
            port = port.coerceIn(1, 65535),
            batchSize = batchSize.coerceIn(1, 1000),
            intervalMs = intervalMs.coerceIn(100L, 60_000L),
            timeoutMs = timeoutMs.coerceIn(300, 30_000),
            successLimit = successLimit.coerceIn(1, 70_000),
            failureLimit = failureLimit.coerceIn(1, 100_000)
        )
    }
}

data class ResolveResult(
    val host: String = "",
    val ipv4: List<String> = emptyList(),
    val ipv6: List<String> = emptyList(),
    val error: String? = null
)

data class ProtocolStats(
    val protocol: IpProtocol,
    val phase: String = "待测试",
    val resolvedAddresses: List<String> = emptyList(),
    val activeSessions: Int = 0,
    val totalFailure: Int = 0,
    val totalAttempts: Int = 0,
    val lastAdded: Int = 0,
    val cps: Int = 0,
    val errorSummary: Map<String, Int> = emptyMap(),
    val totalSuccess: Int = 0,
    val maxStableSessions: Int = 0
)

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

enum class LogLevel { INFO, SUCCESS, WARN, ERROR, STAT }

data class SessionSummary(
    val startedAtEpochMs: Long,
    val host: String,
    val port: Int,
    val mode: TestMode,
    val ipv4Stats: ProtocolStats?,
    val ipv6Stats: ProtocolStats?
) {
    val startedAtText: String
        get() = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(startedAtEpochMs))
}

data class AppUiState(
    val isAdding: Boolean = false,
    val status: String = "待测试",
    val resolveResult: ResolveResult = ResolveResult(),
    val ipv4Stats: ProtocolStats = ProtocolStats(IpProtocol.IPV4),
    val ipv6Stats: ProtocolStats = ProtocolStats(IpProtocol.IPV6),
    val logs: List<LogLine> = emptyList(),
    val history: List<SessionSummary> = emptyList(),
    val summary: SessionSummary? = null,
    val error: String? = null
)
