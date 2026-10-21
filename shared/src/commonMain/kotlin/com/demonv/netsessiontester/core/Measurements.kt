package com.demonv.netsessiontester.core

import kotlin.math.abs
import kotlin.math.floor

/** Statistics for completed probes only. Cancellation is never passed to record. */
data class PingSummary(
    val currentMs: Int? = null,
    val minMs: Int = 0,
    val maxMs: Int = 0,
    val averageMs: Int = 0,
    val jitterMs: Int = 0,
    val sent: Int = 0,
    val received: Int = 0,
    val lost: Int = 0,
    val lossPercent: Float = 0f
)

/** One accumulator per run and address family; a failed probe breaks jitter adjacency. */
class PingAccumulator {
    private var sent = 0
    private var received = 0
    private var sum = 0L
    private var minimum = Int.MAX_VALUE
    private var maximum = 0
    private var previous: Int? = null
    private var jitterSum = 0L
    private var jitterCount = 0
    var summary = PingSummary()
        private set

    fun record(latencyMs: Int?): PingSummary {
        require(latencyMs == null || latencyMs >= 0)
        sent++
        if (latencyMs != null) {
            received++
            sum += latencyMs
            minimum = minOf(minimum, latencyMs)
            maximum = maxOf(maximum, latencyMs)
            previous?.let {
                jitterSum += abs(latencyMs.toLong() - it.toLong())
                jitterCount++
            }
        }
        previous = latencyMs
        val lost = sent - received
        summary = PingSummary(
            currentMs = latencyMs,
            minMs = if (received == 0) 0 else minimum,
            maxMs = maximum,
            averageMs = if (received == 0) 0 else (sum / received).toInt(),
            jitterMs = if (jitterCount == 0) 0 else (jitterSum / jitterCount).toInt(),
            sent = sent,
            received = received,
            lost = lost,
            lossPercent = lost * 100f / sent
        )
        return summary
    }
}

/** Fixed attempted connections/second. A delayed scheduler cannot accumulate an unlimited burst. */
class CpsPacer(
    private val cps: Int,
    startedAtMs: Long,
    private val burstWindowMs: Long = 100L
) {
    init {
        require(cps > 0)
        require(burstWindowMs > 0)
    }
    private var previousMs = startedAtMs
    private var tokens = 0.0

    fun permits(nowMs: Long, capacity: Int): Int {
        val elapsedMs = (nowMs - previousMs).coerceAtLeast(0L)
        previousMs = maxOf(previousMs, nowMs)
        val burst = maxOf(1.0, cps.toDouble() * burstWindowMs / 1000.0)
        tokens = minOf(burst, tokens + cps.toDouble() * elapsedMs / 1000.0)
        val due = floor(tokens).toInt()
        // Missed launch slots are dropped when full, rather than replayed as a burst later.
        tokens -= due
        return minOf(due, capacity.coerceAtLeast(0))
    }
}
