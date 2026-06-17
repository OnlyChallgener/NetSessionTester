package com.demonv.netsessiontester.util

import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.SessionSummary

object CsvExporter {
    fun logsCsv(logs: List<LogLine>): String {
        val sb = StringBuilder()
        sb.appendLine("time,level,text")
        logs.forEach { line ->
            sb.appendLine("${csv(line.timeText)},${csv(line.level.name)},${csv(line.text)}")
        }
        return sb.toString()
    }

    fun summaryCsv(summary: SessionSummary, logs: List<LogLine>): String {
        val sb = StringBuilder()
        sb.appendLine("section,key,value")
        sb.appendLine("summary,time,${csv(summary.startedAtText)}")
        sb.appendLine("summary,host,${csv(summary.host)}")
        sb.appendLine("summary,port,${summary.port}")
        sb.appendLine("summary,mode,${csv(summary.mode.label)}")
        appendStats(sb, "IPv4", summary.ipv4Stats)
        appendStats(sb, "IPv6", summary.ipv6Stats)
        sb.appendLine()
        sb.appendLine("log_time,level,text")
        logs.forEach { line ->
            sb.appendLine("${csv(line.timeText)},${csv(line.level.name)},${csv(line.text)}")
        }
        return sb.toString()
    }

    private fun appendStats(sb: StringBuilder, section: String, stats: ProtocolStats?) {
        if (stats == null) return
        sb.appendLine("$section,active,${stats.activeSessions}")
        sb.appendLine("$section,failure,${stats.totalFailure}")
        sb.appendLine("$section,total,${stats.totalAttempts}")
        sb.appendLine("$section,added,${stats.lastAdded}")
        sb.appendLine("$section,cps,${stats.cps}")
        stats.errorSummary.forEach { (key, value) ->
            sb.appendLine("$section,error_${csv(key)},$value")
        }
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
