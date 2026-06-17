package com.demonv.netsessiontester.data

import android.content.Context
import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.SessionSummary
import com.demonv.netsessiontester.model.TestMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class HistoryStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "session_history.csv")

    suspend fun append(summary: SessionSummary) = withContext(Dispatchers.IO) {
        if (!file.exists()) file.writeText(HEADER + "\n")
        file.appendText(summary.toHistoryLine() + "\n")
    }

    suspend fun load(limit: Int = 20): List<SessionSummary> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        file.readLines().drop(1).takeLast(limit).mapNotNull { it.fromHistoryLineOrNull() }.reversed()
    }

    private fun SessionSummary.toHistoryLine(): String = listOf(
        startedAtEpochMs.toString(), csv(host), port.toString(), mode.name,
        (ipv4Stats?.maxStableSessions ?: 0).toString(),
        (ipv4Stats?.totalFailure ?: 0).toString(),
        (ipv6Stats?.maxStableSessions ?: 0).toString(),
        (ipv6Stats?.totalFailure ?: 0).toString()
    ).joinToString(",")

    private fun String.fromHistoryLineOrNull(): SessionSummary? {
        val parts = splitCsv(this)
        if (parts.size < 8) return null
        return runCatching {
            val ipv4 = parts[4].toInt().takeIf { it > 0 }?.let {
                ProtocolStats(protocol = IpProtocol.IPV4, phase = "历史", maxStableSessions = it, totalFailure = parts[5].toInt())
            }
            val ipv6 = parts[6].toInt().takeIf { it > 0 }?.let {
                ProtocolStats(protocol = IpProtocol.IPV6, phase = "历史", maxStableSessions = it, totalFailure = parts[7].toInt())
            }
            SessionSummary(
                startedAtEpochMs = parts[0].toLong(),
                host = parts[1],
                port = parts[2].toInt(),
                mode = TestMode.valueOf(parts[3]),
                ipv4Stats = ipv4,
                ipv6Stats = ipv6
            )
        }.getOrNull()
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' }) "\"$escaped\"" else escaped
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { out += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    companion object {
        private const val HEADER = "startedAtEpochMs,host,port,mode,ipv4MaxStable,ipv4Failure,ipv6MaxStable,ipv6Failure"
    }
}
