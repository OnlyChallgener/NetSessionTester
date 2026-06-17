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

class HistoryStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "session_history_v6.jsonl")

    suspend fun append(summary: SessionSummary) = withContext(Dispatchers.IO) {
        file.appendText(summary.toJson().toString() + "\n")
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete()
    }

    suspend fun load(limit: Int = 30): List<SessionSummary> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        file.readLines().takeLast(limit).mapNotNull { line ->
            runCatching { JSONObject(line).toSummary() }.getOrNull()
        }.reversed()
    }

    private fun SessionSummary.toJson(): JSONObject = JSONObject()
        .put("startedAtEpochMs", startedAtEpochMs)
        .put("host", host)
        .put("port", port)
        .put("mode", mode.name)
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
        .put("errors", JSONObject(errorSummary))

    private fun JSONObject.toSummary(): SessionSummary = SessionSummary(
        startedAtEpochMs = optLong("startedAtEpochMs"),
        host = optString("host", "www.baidu.com"),
        port = optInt("port", 80),
        mode = runCatching { TestMode.valueOf(optString("mode", TestMode.IPV4_THEN_IPV6.name)) }.getOrDefault(TestMode.IPV4_THEN_IPV6),
        ipv4Stats = optJSONObject("ipv4Stats")?.toStats(),
        ipv6Stats = optJSONObject("ipv6Stats")?.toStats()
    )

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
            errorSummary = errors
        )
    }
}
