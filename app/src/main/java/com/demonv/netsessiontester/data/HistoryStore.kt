package com.demonv.netsessiontester.data

import android.content.Context
import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.SessionSummary
import com.demonv.netsessiontester.model.TestMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

data class HistoryCounts(
    val today: Int = 0,
    val yesterday: Int = 0,
    val week: Int = 0,
    val total: Int = 0,
    val sizeKb: Int = 0
)

class HistoryStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "session_history_v6.jsonl")
    private val zone: ZoneId get() = ZoneId.systemDefault()

    suspend fun append(summary: SessionSummary) = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            file.appendText(summary.toJson().toString() + "\n")
            trimByPeriodLimits()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(fileLock) { if (file.exists()) file.delete() }
    }

    suspend fun load(limit: Int = 30): List<SessionSummary> = withContext(Dispatchers.IO) {
        synchronized(fileLock) { loadAllInternal().sortedByDescending { it.startedAtEpochMs }.take(limit.coerceIn(10, 100)) }
    }

    suspend fun load(period: String, limit: Int = 30): List<SessionSummary> = withContext(Dispatchers.IO) {
        synchronized(fileLock) { loadAllInternal().filterByPeriod(period).sortedByDescending { it.startedAtEpochMs }.take(limit.coerceIn(10, 100)) }
    }

    suspend fun loadAll(): List<SessionSummary> = withContext(Dispatchers.IO) {
        synchronized(fileLock) { loadAllInternal().sortedByDescending { it.startedAtEpochMs } }
    }

    suspend fun updateRemark(id: String, remark: String) = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            val all = loadAllInternal()
            val updated = all.map { item ->
                if (item.id == id) item.copy(remark = remark.take(120)) else item
            }
            writeAll(updated)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            val updated = loadAllInternal().filterNot { it.id == id }
            writeAll(updated)
        }
    }

    suspend fun counts(): HistoryCounts = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
        val all = loadAllInternal()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val weekFields = WeekFields.of(Locale.getDefault())
        val thisWeek = today.get(weekFields.weekOfWeekBasedYear())
        val thisWeekYear = today.get(weekFields.weekBasedYear())
        var todayCount = 0
        var yesterdayCount = 0
        var weekCount = 0
        all.forEach { item ->
            val date = Instant.ofEpochMilli(item.startedAtEpochMs).atZone(zone).toLocalDate()
            if (date == today) todayCount++
            if (date == yesterday) yesterdayCount++
            if (date.get(weekFields.weekOfWeekBasedYear()) == thisWeek &&
                date.get(weekFields.weekBasedYear()) == thisWeekYear
            ) weekCount++
        }
        HistoryCounts(
            today = todayCount,
            yesterday = yesterdayCount,
            week = weekCount,
            total = all.size,
            sizeKb = sizeKb()
        )
        }
    }

    suspend fun trim(limit: Int) = withContext(Dispatchers.IO) {
        synchronized(fileLock) { trimByPeriodLimits() }
    }

    fun sizeKb(): Int = synchronized(fileLock) {
        if (!file.exists()) return@synchronized 0
        val kb = (file.length() + 1023L) / 1024L
        kb.coerceAtLeast(0L).toInt()
    }

    fun count(): Int = synchronized(fileLock) {
        if (!file.exists()) return@synchronized 0
        file.useLines { lines -> lines.count() }
    }


    private fun List<SessionSummary>.filterByPeriod(period: String): List<SessionSummary> {
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val weekFields = WeekFields.of(Locale.getDefault())
        val thisWeek = today.get(weekFields.weekOfWeekBasedYear())
        val thisWeekYear = today.get(weekFields.weekBasedYear())
        return filter { item ->
            val date = Instant.ofEpochMilli(item.startedAtEpochMs).atZone(zone).toLocalDate()
            when (period) {
                "TODAY" -> date == today
                "YESTERDAY" -> date == yesterday
                "WEEK" -> date.get(weekFields.weekOfWeekBasedYear()) == thisWeek &&
                    date.get(weekFields.weekBasedYear()) == thisWeekYear
                else -> true
            }
        }
    }

    private fun trimByPeriodLimits() {
        val all = loadAllInternal().sortedByDescending { it.startedAtEpochMs }
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val weekFields = WeekFields.of(Locale.getDefault())
        val thisWeek = today.get(weekFields.weekOfWeekBasedYear())
        val thisWeekYear = today.get(weekFields.weekBasedYear())

        val todayItems = mutableListOf<SessionSummary>()
        val yesterdayItems = mutableListOf<SessionSummary>()
        val weekOtherItems = mutableListOf<SessionSummary>()

        all.forEach { item ->
            val date = Instant.ofEpochMilli(item.startedAtEpochMs).atZone(zone).toLocalDate()
            val inThisWeek = date.get(weekFields.weekOfWeekBasedYear()) == thisWeek &&
                date.get(weekFields.weekBasedYear()) == thisWeekYear
            when {
                date == today -> todayItems += item
                date == yesterday -> yesterdayItems += item
                inThisWeek -> weekOtherItems += item
            }
        }

        val keptToday = todayItems.take(30)
        val keptYesterday = yesterdayItems.take(30)
        val remainingWeekSlots = (100 - keptToday.size - keptYesterday.size).coerceAtLeast(0)
        val keptWeekOther = weekOtherItems.take(remainingWeekSlots)
        writeAll((keptToday + keptYesterday + keptWeekOther).sortedBy { it.startedAtEpochMs })
    }

    private fun loadAllInternal(): List<SessionSummary> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            runCatching { JSONObject(line).toSummary() }.getOrNull()
        }
    }

    private fun writeAll(items: List<SessionSummary>) {
        if (items.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        file.writeText(items.joinToString("\n") { it.toJson().toString() } + "\n")
    }

    private fun SessionSummary.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("startedAtEpochMs", startedAtEpochMs)
        .put("host", host)
        .put("port", port)
        .put("mode", mode.name)
        .put("remark", remark)
        .put("ipv4Stats", ipv4Stats?.toJson())
        .put("ipv6Stats", ipv6Stats?.toJson())

    private fun ProtocolStats.toJson(): JSONObject = JSONObject()
        .put("protocol", protocol.name)
        .put("phase", phase)
        .put("addresses", resolvedAddresses.joinToString("|"))
        .put("active", activeSessions)
        .put("failure", totalFailure)
        .put("total", totalAttempts)
        .put("added", lastAdded)
        .put("cps", cps)
        .put("success", totalSuccess)
        .put("stable", maxStableSessions)
        .put("avgConnectLatencyMs", averageConnectLatencyMs)
        .put("errors", JSONObject(errorSummary))

    private fun JSONObject.toSummary(): SessionSummary {
        val started = optLong("startedAtEpochMs")
        return SessionSummary(
            id = optString("id", started.toString()),
            startedAtEpochMs = started,
            host = optString("host", "www.baidu.com"),
            port = optInt("port", 80),
            mode = runCatching { TestMode.valueOf(optString("mode", TestMode.IPV4_THEN_IPV6.name)) }.getOrDefault(TestMode.IPV4_THEN_IPV6),
            ipv4Stats = optJSONObject("ipv4Stats")?.toStats(),
            ipv6Stats = optJSONObject("ipv6Stats")?.toStats(),
            remark = optString("remark", "")
        )
    }

    private fun JSONObject.toStats(): ProtocolStats {
        val errorsObj = optJSONObject("errors") ?: JSONObject()
        val errors = mutableMapOf<String, Int>()
        val keys = errorsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            errors[key] = errorsObj.optInt(key)
        }
        return ProtocolStats(
            protocol = runCatching { IpProtocol.valueOf(optString("protocol")) }.getOrDefault(IpProtocol.IPV4),
            phase = optString("phase", "历史"),
            resolvedAddresses = optString("addresses").split("|").filter { it.isNotBlank() },
            activeSessions = optInt("active"),
            totalFailure = optInt("failure"),
            totalAttempts = optInt("total"),
            lastAdded = optInt("added"),
            cps = optInt("cps"),
            totalSuccess = optInt("success"),
            maxStableSessions = optInt("stable"),
            averageConnectLatencyMs = optInt("avgConnectLatencyMs"),
            errorSummary = errors
        )
    }

    private companion object {
        val fileLock = Any()
    }
}
