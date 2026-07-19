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
        synchronized(fileLock) {
            file.appendText(line.toJson().toString() + "\n")
            trimIfNeeded()
        }
    }

    suspend fun load(limit: Int = 500): List<LogLine> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            if (!file.exists()) return@synchronized emptyList()
            file.readLines().takeLast(limit).mapNotNull { raw ->
                runCatching { JSONObject(raw).toLogLine() }.getOrNull()
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(fileLock) { if (file.exists()) file.delete() }
    }

    suspend fun clearAndReturn(): List<LogLine> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            val snapshot = if (file.exists()) {
                file.readLines().takeLast(500).mapNotNull { raw ->
                    runCatching { JSONObject(raw).toLogLine() }.getOrNull()
                }
            } else {
                emptyList()
            }
            if (file.exists()) file.delete()
            snapshot
        }
    }

    suspend fun replaceAll(lines: List<LogLine>) = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            val kept = lines.takeLast(500)
            if (kept.isEmpty()) {
                if (file.exists()) file.delete()
            } else {
                file.writeText(kept.joinToString("\n") { it.toJson().toString() } + "\n")
            }
        }
    }

    fun clearNow() {
        synchronized(fileLock) { if (file.exists()) file.delete() }
    }

    fun sizeKb(): Int = synchronized(fileLock) {
        if (!file.exists()) return@synchronized 0
        val kb = (file.length() + 1023L) / 1024L
        kb.coerceAtLeast(0L).toInt()
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

    private companion object {
        val fileLock = Any()
    }
}
