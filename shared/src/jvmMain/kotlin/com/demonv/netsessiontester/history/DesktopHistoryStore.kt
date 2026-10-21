package com.demonv.netsessiontester.history

import com.demonv.netsessiontester.model.AppMode
import com.demonv.netsessiontester.model.DesktopHistoryRecord
import com.demonv.netsessiontester.model.DualChartPoint
import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.PingStats
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.SessionConfig
import com.demonv.netsessiontester.model.TestMode
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** JSON history persistence shared by the Windows and macOS desktop launchers. */
object DesktopHistoryStore {
    private const val MAX_RECORDS = 100
    private const val MAX_POINTS_PER_RECORD = 2_400
    private const val MAX_LOGS_PER_RECORD = 600
    private const val FILE_NAME = "history.json"
    private const val SCHEMA_VERSION = 1

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val historyFile: Path by lazy { applicationDataDirectory().resolve(FILE_NAME) }
    private val _records = MutableStateFlow<List<DesktopHistoryRecord>>(emptyList())
    private val _error = MutableStateFlow<String?>(null)
    private var initialized = false
    private var preserveCorruptFile = false

    val records: StateFlow<List<DesktopHistoryRecord>> = _records.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedLocked()
        }
    }

    suspend fun append(record: DesktopHistoryRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedLocked()
            val lastId = _records.value.maxOfOrNull { it.id }
            val assignedId = if (lastId == null) record.id else maxOf(Math.addExact(lastId, 1L), record.id)
            val candidate = buildList {
                add(bounded(record.copy(id = assignedId)))
                addAll(_records.value)
            }.take(MAX_RECORDS)
            // A finished measurement remains viewable/exportable even if disk persistence fails.
            _records.value = candidate
            try {
                writeAtomically(candidate)
                _error.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _error.value = failureMessage("保存历史记录", failure)
            }
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedLocked()
            val candidate = _records.value.filterNot { it.id == id }
            if (candidate.size == _records.value.size) {
                _error.value = null
                return@withLock
            }
            persistOrReport("删除历史记录", candidate) {
                _records.value = candidate
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedLocked()
            persistOrReport("清空历史记录", emptyList()) {
                _records.value = emptyList()
            }
        }
    }

    /**
     * Produces one RFC 4180 document. Summary, chart points, and logs are emitted as
     * separate row types so exports retain the original measurements and messages.
     */
    fun csv(records: List<DesktopHistoryRecord>): String {
        val rows = mutableListOf<String>()
        rows += csvRow(
            listOf(
                text("row_type"), text("history_id"), text("started_at_epoch_ms"), text("duration_ms"),
                text("outcome"), text("app_mode"), text("host"), text("port"), text("test_mode"),
                text("ping_interval_ms"), text("session_protocol"), text("session_phase"),
                text("active_sessions"), text("total_success"), text("total_failure"), text("total_attempts"),
                text("cps"), text("max_stable_sessions"), text("average_connect_latency_ms"),
                text("ping_protocol"), text("ping_phase"), text("ping_current_ms"), text("ping_min_ms"),
                text("ping_max_ms"), text("ping_average_ms"), text("ping_jitter_ms"), text("ping_sent"),
                text("ping_received"), text("ping_lost"), text("ping_loss_percent"), text("elapsed_ms"),
                text("point_protocol"), text("point_active_sessions"), text("point_ping_latency_ms"),
                text("point_has_ping_sample"), text("log_time_epoch_ms"), text("log_level"), text("text")
            )
        )

        records.forEach { record ->
            rows += csvRow(commonCsvCells(record, "summary") + summaryCsvCells(record))
            record.points.forEach { point ->
                rows += csvRow(commonCsvCells(record, "point") + pointCsvCells(point))
            }
            record.logs.forEach { log ->
                rows += csvRow(commonCsvCells(record, "log") + logCsvCells(log))
            }
        }
        return "\uFEFF" + rows.joinToString(separator = "\r\n", postfix = "\r\n")
    }

    private fun ensureLoadedLocked() {
        if (initialized) return
        initialized = true
        if (!Files.exists(historyFile)) {
            _records.value = emptyList()
            _error.value = null
            return
        }

        val raw = try {
            Files.readString(historyFile, StandardCharsets.UTF_8)
        } catch (cancelled: CancellationException) {
            initialized = false
            throw cancelled
        } catch (failure: Throwable) {
            val backup = backupCorruptFile()
            preserveCorruptFile = backup == null
            _error.value = buildString {
                append(failureMessage("读取历史记录", failure))
                if (backup != null) append("；原文件已备份为 ${backup.fileName}")
                else append("；无法创建文件备份，已停止写入以保留原文件")
            }
            return
        }

        try {
            _records.value = decodeFile(raw)
                .take(MAX_RECORDS)
                .map(::bounded)
            _error.value = null
        } catch (cancelled: CancellationException) {
            initialized = false
            throw cancelled
        } catch (failure: Throwable) {
            _records.value = emptyList()
            val backup = backupCorruptFile()
            preserveCorruptFile = backup == null
            _error.value = buildString {
                append(failureMessage("历史记录文件损坏", failure))
                if (backup != null) append("；原文件已备份为 ${backup.fileName}")
                else append("；无法创建损坏文件备份，已停止写入以保留原文件")
            }
        }
    }

    private inline fun persistOrReport(
        action: String,
        candidate: List<DesktopHistoryRecord>,
        commit: () -> Unit
    ) {
        try {
            writeAtomically(candidate)
            commit()
            _error.value = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Keep the last successfully persisted StateFlow value available to the UI.
            _error.value = failureMessage(action, failure)
        }
    }

    private fun writeAtomically(records: List<DesktopHistoryRecord>) {
        check(!preserveCorruptFile) { "损坏的历史文件尚未成功备份" }
        val directory = historyFile.parent
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "history-", ".tmp")
        try {
            val bytes = encodeFile(records).toByteArray(StandardCharsets.UTF_8)
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(
                    temporary,
                    historyFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun backupCorruptFile(): Path? {
        val backup = historyFile.resolveSibling("history.corrupt.${System.currentTimeMillis()}.json")
        return runCatching {
            Files.move(historyFile, backup, StandardCopyOption.REPLACE_EXISTING)
            backup
        }.getOrNull()
    }

    private fun bounded(record: DesktopHistoryRecord): DesktopHistoryRecord = record.copy(
        points = record.points.takeLast(MAX_POINTS_PER_RECORD),
        logs = record.logs.takeLast(MAX_LOGS_PER_RECORD)
    )

    private fun applicationDataDirectory(): Path {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val userHome = System.getProperty("user.home").orEmpty().ifBlank { "." }
        val base = when {
            "win" in osName -> System.getenv("LOCALAPPDATA")
                ?.takeIf(String::isNotBlank)
                ?.let { Paths.get(it) }
                ?: Paths.get(userHome, "AppData", "Local")
            "mac" in osName -> Paths.get(userHome, "Library", "Application Support")
            else -> System.getenv("XDG_DATA_HOME")
                ?.takeIf(String::isNotBlank)
                ?.let { Paths.get(it) }
                ?: Paths.get(userHome, ".local", "share")
        }
        return base.resolve("NetSessionTester")
    }

    private fun encodeFile(records: List<DesktopHistoryRecord>): String = buildJsonObject {
        put("schemaVersion", SCHEMA_VERSION)
        put("records", buildJsonArray { records.forEach { add(encodeRecord(it)) } })
    }.toString()

    private fun decodeFile(raw: String): List<DesktopHistoryRecord> {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: error("history root must be an object")
        val schema = root.int("schemaVersion", SCHEMA_VERSION)
        require(schema == SCHEMA_VERSION) { "unsupported history schema $schema" }
        val items = root.array("records")
        return items.map { decodeRecord(it.objectValue("record")) }
    }

    private fun encodeRecord(value: DesktopHistoryRecord): JsonObject = buildJsonObject {
        put("id", value.id)
        put("startedAtEpochMs", value.startedAtEpochMs)
        put("durationMs", value.durationMs)
        put("outcome", value.outcome)
        put("appMode", value.appMode.name)
        put("config", encodeConfig(value.config))
        put("pingIntervalMs", value.pingIntervalMs)
        put("sessionStats", encodeProtocolStats(value.sessionStats))
        put("pingStats", encodePingStats(value.pingStats))
        put("points", buildJsonArray { value.points.forEach { add(encodePoint(it)) } })
        put("logs", buildJsonArray { value.logs.forEach { add(encodeLog(it)) } })
    }

    private fun decodeRecord(value: JsonObject): DesktopHistoryRecord = DesktopHistoryRecord(
        id = value.requiredLong("id"),
        startedAtEpochMs = value.requiredLong("startedAtEpochMs"),
        durationMs = value.long("durationMs"),
        outcome = value.string("outcome"),
        appMode = enumValue(value.string("appMode"), AppMode.SESSION_HOLD),
        config = decodeConfig(value.objectOrEmpty("config")),
        pingIntervalMs = value.long("pingIntervalMs", 500L),
        sessionStats = decodeProtocolStats(value.objectOrEmpty("sessionStats")),
        pingStats = decodePingStats(value.objectOrEmpty("pingStats")),
        points = value.array("points").map { decodePoint(it.objectValue("point")) },
        logs = value.array("logs").map { decodeLog(it.objectValue("log")) }
    )

    private fun encodeConfig(value: SessionConfig): JsonObject = buildJsonObject {
        put("host", value.host)
        put("port", value.port)
        put("mode", value.mode.name)
        put("batchSize", value.batchSize)
        put("intervalMs", value.intervalMs)
        put("timeoutMs", value.timeoutMs)
        put("successLimit", value.successLimit)
        put("failureLimit", value.failureLimit)
        put("keepConnectionsAfterStop", value.keepConnectionsAfterStop)
    }

    private fun decodeConfig(value: JsonObject): SessionConfig = SessionConfig(
        host = value.string("host", "www.baidu.com"),
        port = value.int("port", 80),
        mode = enumValue(value.string("mode"), TestMode.IPV4_ONLY),
        batchSize = value.int("batchSize", 500),
        intervalMs = value.long("intervalMs", 50L),
        timeoutMs = value.int("timeoutMs", 1_500),
        successLimit = value.int("successLimit", 10_000),
        failureLimit = value.int("failureLimit", 200),
        keepConnectionsAfterStop = value.boolean("keepConnectionsAfterStop", true)
    )

    private fun encodeProtocolStats(value: ProtocolStats): JsonObject = buildJsonObject {
        put("protocol", value.protocol.name)
        put("phase", value.phase)
        put("resolvedAddresses", buildJsonArray { value.resolvedAddresses.forEach { add(JsonPrimitive(it)) } })
        put("activeSessions", value.activeSessions)
        put("totalSuccess", value.totalSuccess)
        put("totalFailure", value.totalFailure)
        put("totalAttempts", value.totalAttempts)
        put("lastAdded", value.lastAdded)
        put("cps", value.cps)
        put("maxStableSessions", value.maxStableSessions)
        put("averageConnectLatencyMs", value.averageConnectLatencyMs)
        put("errorSummary", buildJsonObject { value.errorSummary.forEach { (key, count) -> put(key, count) } })
    }

    private fun decodeProtocolStats(value: JsonObject): ProtocolStats = ProtocolStats(
        protocol = enumValue(value.string("protocol"), IpProtocol.IPV4),
        phase = value.string("phase", "待测试"),
        resolvedAddresses = value.array("resolvedAddresses").mapNotNull { it.primitiveOrNull()?.contentOrNull },
        activeSessions = value.int("activeSessions"),
        totalSuccess = value.int("totalSuccess"),
        totalFailure = value.int("totalFailure"),
        totalAttempts = value.int("totalAttempts"),
        lastAdded = value.int("lastAdded"),
        cps = value.int("cps"),
        maxStableSessions = value.int("maxStableSessions"),
        averageConnectLatencyMs = value.int("averageConnectLatencyMs"),
        errorSummary = value.objectOrEmpty("errorSummary").mapValues { (_, item) ->
            item.primitiveOrNull()?.intOrNull ?: 0
        }
    )

    private fun encodePingStats(value: PingStats): JsonObject = buildJsonObject {
        put("host", value.host)
        put("port", value.port)
        put("currentLatencyMs", value.currentLatencyMs?.let { JsonPrimitive(it) } ?: JsonNull)
        put("minLatencyMs", value.minLatencyMs)
        put("maxLatencyMs", value.maxLatencyMs)
        put("avgLatencyMs", value.avgLatencyMs)
        put("jitterMs", value.jitterMs)
        put("sentCount", value.sentCount)
        put("receivedCount", value.receivedCount)
        put("lostCount", value.lostCount)
        put("lossPercent", value.lossPercent.takeIf { it.isFinite() } ?: 0f)
        put("isRunning", value.isRunning)
        put("phase", value.phase)
        put("sampleTimeNanos", value.sampleTimeNanos)
        put("protocol", value.protocol?.let { JsonPrimitive(it.name) } ?: JsonNull)
    }

    private fun decodePingStats(value: JsonObject): PingStats = PingStats(
        host = value.string("host"),
        port = value.int("port", 80),
        currentLatencyMs = value.nullableInt("currentLatencyMs"),
        minLatencyMs = value.int("minLatencyMs"),
        maxLatencyMs = value.int("maxLatencyMs"),
        avgLatencyMs = value.int("avgLatencyMs"),
        jitterMs = value.int("jitterMs"),
        sentCount = value.int("sentCount"),
        receivedCount = value.int("receivedCount"),
        lostCount = value.int("lostCount"),
        lossPercent = value.float("lossPercent"),
        isRunning = value.boolean("isRunning"),
        phase = value.string("phase", "就绪"),
        sampleTimeNanos = value.long("sampleTimeNanos"),
        protocol = value.stringOrNull("protocol")?.let { enumValue(it, IpProtocol.IPV4) }
    )

    private fun encodePoint(value: DualChartPoint): JsonObject = buildJsonObject {
        put("elapsedMs", value.elapsedMs)
        put("activeSessions", value.activeSessions?.let { JsonPrimitive(it) } ?: JsonNull)
        put("pingLatencyMs", value.pingLatencyMs?.let { JsonPrimitive(it) } ?: JsonNull)
        put("hasPingSample", value.hasPingSample)
        put("protocol", value.protocol.name)
    }

    private fun decodePoint(value: JsonObject): DualChartPoint = DualChartPoint(
        elapsedMs = value.long("elapsedMs"),
        activeSessions = value.nullableInt("activeSessions"),
        pingLatencyMs = value.nullableInt("pingLatencyMs"),
        hasPingSample = value.boolean("hasPingSample"),
        protocol = enumValue(value.string("protocol"), IpProtocol.IPV4)
    )

    private fun encodeLog(value: LogLine): JsonObject = buildJsonObject {
        put("timeEpochMs", value.timeEpochMs)
        put("level", value.level.name)
        put("text", value.text)
    }

    private fun decodeLog(value: JsonObject): LogLine = LogLine(
        timeEpochMs = value.long("timeEpochMs"),
        level = enumValue(value.string("level"), LogLevel.INFO),
        text = value.string("text")
    )

    private fun commonCsvCells(record: DesktopHistoryRecord, rowType: String): List<CsvCell> = listOf(
        text(rowType), number(record.id), number(record.startedAtEpochMs), number(record.durationMs),
        text(record.outcome), text(record.appMode.name), text(record.config.host), number(record.config.port),
        text(record.config.mode.name), number(record.pingIntervalMs)
    )

    private fun summaryCsvCells(record: DesktopHistoryRecord): List<CsvCell> {
        val session = record.sessionStats
        val ping = record.pingStats
        return listOf(
            text(session.protocol.name), text(session.phase), number(session.activeSessions),
            number(session.totalSuccess), number(session.totalFailure), number(session.totalAttempts),
            number(session.cps), number(session.maxStableSessions), number(session.averageConnectLatencyMs),
            text(ping.protocol?.name.orEmpty()), text(ping.phase), nullableNumber(ping.currentLatencyMs),
            number(ping.minLatencyMs), number(ping.maxLatencyMs), number(ping.avgLatencyMs),
            number(ping.jitterMs), number(ping.sentCount), number(ping.receivedCount), number(ping.lostCount),
            number(ping.lossPercent), empty(), empty(), empty(), empty(), empty(), empty(), empty(), empty()
        )
    }

    private fun pointCsvCells(point: DualChartPoint): List<CsvCell> = buildList {
        addAll(List(20) { empty() })
        add(number(point.elapsedMs))
        add(text(point.protocol.name))
        add(nullableNumber(point.activeSessions))
        add(nullableNumber(point.pingLatencyMs))
        add(number(point.hasPingSample))
        addAll(List(3) { empty() })
    }

    private fun logCsvCells(log: LogLine): List<CsvCell> = buildList {
        addAll(List(28) { empty() })
    }.toMutableList().also {
        it[25] = number(log.timeEpochMs)
        it[26] = text(log.level.name)
        it[27] = text(log.text)
    }

    private data class CsvCell(val value: String, val isText: Boolean)

    private fun text(value: String): CsvCell = CsvCell(value, true)
    private fun number(value: Any): CsvCell = CsvCell(value.toString(), false)
    private fun nullableNumber(value: Any?): CsvCell = CsvCell(value?.toString().orEmpty(), false)
    private fun empty(): CsvCell = CsvCell("", false)

    private fun csvRow(cells: List<CsvCell>): String = cells.joinToString(",") { cell ->
        val safe = if (cell.isText) excelSafeText(cell.value) else cell.value
        "\"" + safe.replace("\"", "\"\"") + "\""
    }

    private fun excelSafeText(value: String): String {
        val first = value.firstOrNull { !it.isWhitespace() }
        return if (first == '=' || first == '+' || first == '-' || first == '@') "'$value" else value
    }

    private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.objectOrEmpty(name: String): JsonObject = this[name] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement.objectValue(label: String): JsonObject = this as? JsonObject ?: error("$label must be an object")
    private fun JsonElement.primitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
    private fun JsonObject.string(name: String, default: String = ""): String =
        (this[name] as? JsonPrimitive)?.contentOrNull ?: default
    private fun JsonObject.stringOrNull(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(name: String, default: Int = 0): Int =
        (this[name] as? JsonPrimitive)?.intOrNull ?: default
    private fun JsonObject.nullableInt(name: String): Int? =
        (this[name] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.long(name: String, default: Long = 0L): Long =
        (this[name] as? JsonPrimitive)?.longOrNull ?: default
    private fun JsonObject.requiredLong(name: String): Long =
        (this[name] as? JsonPrimitive)?.longOrNull ?: error("missing $name")
    private fun JsonObject.float(name: String, default: Float = 0f): Float =
        (this[name] as? JsonPrimitive)?.floatOrNull?.takeIf { it.isFinite() } ?: default
    private fun JsonObject.boolean(name: String, default: Boolean = false): Boolean =
        (this[name] as? JsonPrimitive)?.booleanOrNull ?: default

    private inline fun <reified T : Enum<T>> enumValue(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun failureMessage(action: String, failure: Throwable): String =
        "${action}失败：${failure.message ?: failure.javaClass.simpleName}"
}
