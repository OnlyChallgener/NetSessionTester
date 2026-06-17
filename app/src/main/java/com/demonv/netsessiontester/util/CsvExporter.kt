package com.demonv.netsessiontester.util

import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.SessionSummary

object CsvExporter {
    fun logsCsv(logs: List<LogLine>): String = buildString {
        appendLine("time,level,message")
        logs.forEach { line ->
            appendLine(listOf(csv(line.timeText), line.level.name, csv(line.text)).joinToString(","))
        }
    }

    fun summaryCsv(summary: SessionSummary, logs: List<LogLine>): String = buildString {
        appendLine("type,time,host,port,mode,protocol,activeSessions,totalSuccess,totalFailure,totalAttempts,maxStableSessions,cps,errors")
        appendLine(summaryRow(summary, summary.ipv4Stats))
        appendLine(summaryRow(summary, summary.ipv6Stats))
        appendLine()
        appendLine("logTime,level,message")
        logs.forEach { line ->
            appendLine(listOf(csv(line.timeText), line.level.name, csv(line.text)).joinToString(","))
        }
    }

    private fun summaryRow(summary: SessionSummary, stats: ProtocolStats?): String {
        return listOf(
            "summary",
            csv(summary.startedAtText),
            csv(summary.host),
            summary.port.toString(),
            summary.mode.name,
            stats?.protocol?.name.orEmpty(),
            stats?.activeSessions?.toString().orEmpty(),
            stats?.totalSuccess?.toString().orEmpty(),
            stats?.totalFailure?.toString().orEmpty(),
            stats?.totalAttempts?.toString().orEmpty(),
            stats?.maxStableSessions?.toString().orEmpty(),
            stats?.cps?.toString().orEmpty(),
            csv(stats?.errorSummary?.entries?.joinToString(" | ") { "${it.key}:${it.value}" }.orEmpty())
        ).joinToString(",")
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '|' }) "\"$escaped\"" else escaped
    }
}
