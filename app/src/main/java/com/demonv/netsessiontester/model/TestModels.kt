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

enum class RunPhase(val label: String) {
    Idle("待测试"),
    Preparing("准备中"),
    NatChecking("NAT检测中"),
    Running("测试中"),
    TopConfirm("顶部确认"),
    Stopping("停止新增"),
    Releasing("释放中"),
    Finished("已完成"),
    Failed("异常结束")
}

data class ReleaseUiState(
    val visible: Boolean = false,
    val total: Int = 0,
    val closed: Int = 0,
    val speedPerSecond: Int = 0,
    val elapsedMs: Long = 0L,
    val message: String = "",
    val finished: Boolean = false
) {
    val progress: Float
        get() = if (total <= 0) 1f else (closed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    val percent: Int
        get() = (progress * 100f).toInt().coerceIn(0, 100)

    val etaSeconds: Int
        get() = if (finished || speedPerSecond <= 0 || total <= closed) 0 else ((total - closed) + speedPerSecond - 1) / speedPerSecond
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
            // batchSize 直接作为“目标 CPS”使用，不再做 128 动态调速。
            batchSize = batchSize.coerceIn(20, 2_000),
            // 调度间隔用于固定 CPS 发射：每个 tick 发起 batchSize * intervalMs / 1000 条。
            intervalMs = intervalMs.coerceIn(20L, 1_000L),
            timeoutMs = timeoutMs.coerceIn(300, 10_000),
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
    val id: String = startedAtEpochMs.toString(),
    val host: String,
    val port: Int,
    val mode: TestMode,
    val ipv4Stats: ProtocolStats?,
    val ipv6Stats: ProtocolStats?,
    val remark: String = ""
) {
    val startedAtText: String
        get() = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(startedAtEpochMs))
}

data class AppUiState(
    val isAdding: Boolean = false,
    val runPhase: RunPhase = RunPhase.Idle,
    val status: String = "待测试",
    val releaseUi: ReleaseUiState = ReleaseUiState(),
    val resolveResult: ResolveResult = ResolveResult(),
    val ipv4Stats: ProtocolStats = ProtocolStats(IpProtocol.IPV4),
    val ipv6Stats: ProtocolStats = ProtocolStats(IpProtocol.IPV6),
    val logs: List<LogLine> = emptyList(),
    val history: List<SessionSummary> = emptyList(),
    val summary: SessionSummary? = null,
    val error: String? = null
)
