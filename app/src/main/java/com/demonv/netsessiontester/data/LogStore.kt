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
            trimIfNeeded(force = false)
        }
    }

    suspend fun load(limit: Int = 500): List<LogLine> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            if (!file.exists()) return@synchronized emptyList()
            val now = System.currentTimeMillis()
            file.readLines().takeLast(limit.coerceAtLeast(1)).mapNotNull { raw ->
                runCatching {
                    val obj = JSONObject(raw)
                    val time = obj.optLong("timeEpochMs", now)
                    if (now - time <= RETENTION_MILLIS) obj.toLogLine() else null
                }.getOrNull()
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            writesSinceLastTrim = 0
            if (file.exists()) file.delete()
        }
    }

    suspend fun clearAndReturn(): List<LogLine> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            writesSinceLastTrim = 0
            val snapshot = if (file.exists()) {
                val now = System.currentTimeMillis()
                file.readLines().takeLast(500).mapNotNull { raw ->
                    runCatching {
                        val obj = JSONObject(raw)
                        val time = obj.optLong("timeEpochMs", now)
                        if (now - time <= RETENTION_MILLIS) obj.toLogLine() else null
                    }.getOrNull()
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
            writesSinceLastTrim = 0
            val kept = lines.takeLast(500)
            if (kept.isEmpty()) {
                if (file.exists()) file.delete()
            } else {
                file.writeText(kept.joinToString("\n") { it.toJson().toString() } + "\n")
            }
        }
    }

    fun clearNow() {
        synchronized(fileLock) {
            writesSinceLastTrim = 0
            if (file.exists()) file.delete()
        }
    }

    fun sizeKb(): Int = synchronized(fileLock) {
        if (!file.exists()) return@synchronized 0
        val kb = (file.length() + 1023L) / 1024L
        kb.coerceAtLeast(0L).toInt()
    }

    /**
     * Amortized trim: instead of reading and rewriting the whole file on every single append,
     * we batch the compaction every 50 writes or when forced. Also prunes records older than 7 days.
     */
    private fun trimIfNeeded(force: Boolean = false) {
        if (!file.exists()) return
        writesSinceLastTrim++
        if (!force && writesSinceLastTrim < BATCH_TRIM_INTERVAL) return
        writesSinceLastTrim = 0

        val lines = file.readLines()
        if (lines.size > MAX_LINES || force) {
            val now = System.currentTimeMillis()
            val valid = lines.takeLast(MAX_LINES).filter { raw ->
                val time = runCatching { JSONObject(raw).optLong("timeEpochMs", now) }.getOrDefault(now)
                (now - time) <= RETENTION_MILLIS
            }
            if (valid.isEmpty()) {
                file.delete()
            } else {
                file.writeText(valid.joinToString("\n") + "\n")
            }
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
        const val MAX_LINES = 500
        const val BATCH_TRIM_INTERVAL = 50
        const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000L // 7 days retention
        @Volatile var writesSinceLastTrim = 0
    }
}
