package com.demonv.netsessiontester.model

/** A complete, replayable snapshot of one desktop test run. */
data class DesktopHistoryRecord(
    val id: Long,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val outcome: String,
    val appMode: AppMode,
    val config: SessionConfig,
    val pingIntervalMs: Long,
    val sessionStats: ProtocolStats,
    val pingStats: PingStats,
    val points: List<DualChartPoint>,
    val logs: List<LogLine>
)
