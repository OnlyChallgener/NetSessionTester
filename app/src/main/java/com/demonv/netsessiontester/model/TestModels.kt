package com.demonv.netsessiontester.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class IpMode(val label: String) {
    AUTO("自动"),
    IPV4_ONLY("仅 IPv4"),
    IPV6_ONLY("仅 IPv6")
}

data class TestConfig(
    val host: String,
    val port: Int,
    val ipMode: IpMode,
    val startConcurrency: Int,
    val maxConcurrency: Int,
    val step: Int,
    val timeoutMs: Int,
    val holdMs: Long,
    val failureStopRate: Double
) {
    fun normalized(): TestConfig {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
        return copy(
            host = cleanHost,
            port = port.coerceIn(1, 65535),
            startConcurrency = startConcurrency.coerceIn(1, SAFE_MAX_CONCURRENCY),
            maxConcurrency = maxConcurrency.coerceIn(1, SAFE_MAX_CONCURRENCY),
            step = step.coerceIn(1, SAFE_MAX_CONCURRENCY),
            timeoutMs = timeoutMs.coerceIn(300, 10_000),
            holdMs = holdMs.coerceIn(200, 60_000),
            failureStopRate = failureStopRate.coerceIn(0.01, 0.95)
        )
    }

    companion object {
        const val SAFE_MAX_CONCURRENCY = 1000
    }
}

data class SingleConnectionResult(
    val success: Boolean,
    val latencyMs: Long?,
    val address: String,
    val error: String?
)

data class BatchResult(
    val concurrency: Int,
    val successCount: Int,
    val failureCount: Int,
    val avgLatencyMs: Long?,
    val p95LatencyMs: Long?,
    val minLatencyMs: Long?,
    val maxLatencyMs: Long?,
    val elapsedMs: Long,
    val errorSummary: Map<String, Int>,
    val addresses: List<String>
) {
    val total: Int get() = successCount + failureCount
    val successRate: Double get() = if (total == 0) 0.0 else successCount.toDouble() / total
    val failureRate: Double get() = if (total == 0) 0.0 else failureCount.toDouble() / total

    fun successRateText(): String = "${(successRate * 100).roundToInt()}%"
    fun failureRateText(): String = "${(failureRate * 100).roundToInt()}%"
}

data class TestSummary(
    val startedAtEpochMs: Long,
    val host: String,
    val port: Int,
    val ipMode: IpMode,
    val stableConcurrency: Int,
    val peakConcurrency: Int,
    val finalSuccessRate: Double,
    val batches: List<BatchResult>
) {
    val startedAtText: String
        get() = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(startedAtEpochMs))
}

data class AppUiState(
    val isRunning: Boolean = false,
    val status: String = "待测试",
    val batches: List<BatchResult> = emptyList(),
    val summary: TestSummary? = null,
    val history: List<TestSummary> = emptyList(),
    val error: String? = null
)
