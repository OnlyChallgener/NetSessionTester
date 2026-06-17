package com.demonv.netsessiontester.util

import com.demonv.netsessiontester.model.TestSummary

object CsvExporter {
    fun currentSessionCsv(summary: TestSummary): String {
        val header = "time,host,port,ipMode,concurrency,success,failure,successRate,failureRate,avgMs,p95Ms,minMs,maxMs,elapsedMs,addresses,errors"
        val rows = summary.batches.map { batch ->
            listOf(
                csv(summary.startedAtText),
                csv(summary.host),
                summary.port.toString(),
                summary.ipMode.name,
                batch.concurrency.toString(),
                batch.successCount.toString(),
                batch.failureCount.toString(),
                "%.4f".format(batch.successRate),
                "%.4f".format(batch.failureRate),
                batch.avgLatencyMs?.toString().orEmpty(),
                batch.p95LatencyMs?.toString().orEmpty(),
                batch.minLatencyMs?.toString().orEmpty(),
                batch.maxLatencyMs?.toString().orEmpty(),
                batch.elapsedMs.toString(),
                csv(batch.addresses.joinToString(" | ")),
                csv(batch.errorSummary.entries.joinToString(" | ") { "${it.key}:${it.value}" })
            ).joinToString(",")
        }
        return buildString {
            appendLine(header)
            rows.forEach { appendLine(it) }
        }
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '|' }) "\"$escaped\"" else escaped
    }
}
