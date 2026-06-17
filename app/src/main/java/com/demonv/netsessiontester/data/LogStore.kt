package com.demonv.netsessiontester.data

import android.content.Context
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class LogStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "run_logs_v6.jsonl")

    suspend fun append(line: LogLine) = withContext(Dispatchers.IO) {
        file.appendText(line.toJson().toString() + "\n")
        trimIfNeeded()
    }

    suspend fun load(limit: Int = 500): List<LogLine> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        file.readLines().takeLast(limit).mapNotNull { raw ->
            runCatching { JSONObject(raw).toLogLine() }.getOrNull()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete()
    }

    fun clearNow() {
        if (file.exists()) file.delete()
    }

    fun sizeKb(): Int {
        if (!file.exists()) return 0
        val kb = (file.length() + 1023L) / 1024L
        return kb.coerceAtLeast(0L).toInt()
    }

    private fun trimIfNeeded(maxLines: Int = 500) {
        if (!file.exists()) return
        val lines = file.readLines()
        if (lines.size > maxLines) {
            file.writeText(lines.takeLast(maxLines).joinToString("\n") + "\n")
        }
    }

    private fun LogLine.toJson(): JSONObject = JSONObject()
        .put("timeEpochMs", timeEpochMs)
        .put("level", level.name)
        .put("text", text)

    private fun JSONObject.toLogLine(): LogLine = LogLine(
        timeEpochMs = optLong("timeEpochMs", System.currentTimeMillis()),
        level = runCatching { LogLevel.valueOf(optString("level", LogLevel.INFO.name)) }.getOrDefault(LogLevel.INFO),
        text = optString("text", "")
    )
}
