package com.demonv.netsessiontester.data

import android.content.Context
import com.demonv.netsessiontester.model.IpMode
import com.demonv.netsessiontester.model.TestSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class HistoryStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "test_history.csv")

    suspend fun append(summary: TestSummary) = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            file.writeText(HEADER + "\n")
        }
        file.appendText(summary.toHistoryLine() + "\n")
    }

    suspend fun load(limit: Int = 20): List<TestSummary> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        file.readLines()
            .drop(1)
            .takeLast(limit)
            .mapNotNull { line -> line.fromHistoryLineOrNull() }
            .reversed()
    }

    private fun TestSummary.toHistoryLine(): String = listOf(
        startedAtEpochMs.toString(),
        csv(host),
        port.toString(),
        ipMode.name,
        stableConcurrency.toString(),
        peakConcurrency.toString(),
        "%.4f".format(finalSuccessRate)
    ).joinToString(",")

    private fun String.fromHistoryLineOrNull(): TestSummary? {
        val parts = splitCsv(this)
        if (parts.size < 7) return null
        return runCatching {
            TestSummary(
                startedAtEpochMs = parts[0].toLong(),
                host = parts[1],
                port = parts[2].toInt(),
                ipMode = IpMode.valueOf(parts[3]),
                stableConcurrency = parts[4].toInt(),
                peakConcurrency = parts[5].toInt(),
                finalSuccessRate = parts[6].toDouble(),
                batches = emptyList()
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
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    out += sb.toString()
                    sb.clear()
                }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    companion object {
        private const val HEADER = "startedAtEpochMs,host,port,ipMode,stableConcurrency,peakConcurrency,finalSuccessRate"
    }
}
