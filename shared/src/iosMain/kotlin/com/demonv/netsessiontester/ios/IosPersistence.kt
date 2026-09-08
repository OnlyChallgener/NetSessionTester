@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.demonv.netsessiontester.ios

import kotlinx.cinterop.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import platform.Foundation.*
import platform.UIKit.*

data class IosHistoryRecord(
    val id: Long,
    val kind: String,
    val target: String,
    val summary: String,
    val details: String
)

/** Called on the main thread. A native tab change stops probes before another tool is started. */
object IosTaskRegistry {
    private val stoppers = mutableMapOf<String, () -> Job?>()
    private var pending = emptyList<Job>()
    fun register(key: String, stop: () -> Job?) { stoppers[key] = stop }
    fun unregister(key: String) { stoppers.remove(key) }
    fun stopAll() {
        // Capture the old jobs synchronously so a newly started tool never waits on itself.
        pending = (pending.filterNot { it.isCompleted } + stoppers.values.toList().mapNotNull { it() }).distinct()
    }
    suspend fun awaitIdle() {
        val previous = pending.toList()
        val stopped = withTimeoutOrNull(15_000L) {
            previous.forEach { it.join() }
            true
        } ?: false
        pending = pending.filterNot { it.isCompleted }
        // Do not start a competing probe if an OS resolver has not returned after cancellation.
        check(stopped) { "上一项测试仍在释放资源，请稍后重试" }
    }
}

fun stopIosActiveTasks() = IosTaskRegistry.stopAll()

object IosHistoryStore {
    private const val KEY = "network_test_history_v1"
    private val defaults get() = NSUserDefaults.standardUserDefaults
    private val state = MutableStateFlow(load())
    val records = state.asStateFlow()

    private fun load(): List<IosHistoryRecord> {
        val raw = defaults.stringForKey(KEY) ?: return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val item = element.jsonObject
                val id = item["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                IosHistoryRecord(
                    id, item["kind"]?.jsonPrimitive?.content.orEmpty(),
                    item["target"]?.jsonPrimitive?.content.orEmpty(),
                    item["summary"]?.jsonPrimitive?.content.orEmpty(),
                    item["details"]?.jsonPrimitive?.content.orEmpty()
                )
            }.take(100)
        }.getOrElse {
            // Preserve malformed data for recovery instead of silently overwriting the only copy.
            defaults.setObject(raw, forKey = "$KEY.recovery")
            emptyList()
        }
    }

    fun append(kind: String, target: String, summary: String, details: String) {
        val id = maxOf(getEpochMs(), (state.value.firstOrNull()?.id ?: 0L) + 1L)
        save((listOf(IosHistoryRecord(id, kind, target, summary, details.take(80_000))) + state.value).take(100))
    }

    fun delete(id: Long) = save(state.value.filterNot { it.id == id })
    fun clear() = save(emptyList())

    private fun save(records: List<IosHistoryRecord>) {
        val json = buildJsonArray {
            records.forEach { record ->
                add(buildJsonObject {
                    put("id", record.id); put("kind", record.kind); put("target", record.target)
                    put("summary", record.summary); put("details", record.details)
                })
            }
        }.toString()
        defaults.setObject(json, forKey = KEY)
        state.value = records
    }

    fun csv(records: List<IosHistoryRecord> = state.value): String {
        fun field(value: String): String {
            // A spreadsheet must treat user-provided targets and logs as text, never formulas.
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        return "\uFEFF时间戳,工具,目标,结果,详细记录\r\n" + records.joinToString("\r\n") {
            listOf(it.id.toString(), it.kind, it.target, it.summary, it.details).joinToString(",", transform = ::field)
        }
    }
}

object IosPreferences {
    private val defaults get() = NSUserDefaults.standardUserDefaults
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    fun loadConfig(): IosSessionConfig {
        val fallback = IosSessionConfig()
        fun number(key: String, default: Int): Int = defaults.stringForKey("config.$key")?.toIntOrNull() ?: default
        return fallback.copy(
            host = defaults.stringForKey("config.host") ?: fallback.host,
            port = number("port", fallback.port),
            batchSize = number("cps", fallback.batchSize),
            successLimit = number("success", fallback.successLimit),
            failureLimit = number("failure", fallback.failureLimit),
            timeoutMs = number("timeout", fallback.timeoutMs),
            pingIntervalMs = number("pingInterval", fallback.pingIntervalMs.toInt()).toLong(),
            keepConnectionsAfterStop = defaults.stringForKey("config.keep")?.toBooleanStrictOrNull() ?: fallback.keepConnectionsAfterStop,
            mode = IosTestMode.entries.firstOrNull { it.name == defaults.stringForKey("config.mode") } ?: fallback.mode
        ).normalized()
    }

    fun saveConfig(raw: IosSessionConfig) {
        val config = raw.normalized()
        mapOf(
            "host" to config.host, "port" to config.port.toString(), "cps" to config.batchSize.toString(),
            "success" to config.successLimit.toString(), "failure" to config.failureLimit.toString(),
            "timeout" to config.timeoutMs.toString(), "pingInterval" to config.pingIntervalMs.toString(),
            "keep" to config.keepConnectionsAfterStop.toString(), "mode" to config.mode.name
        ).forEach { (key, value) -> defaults.setObject(value, forKey = "config.$key") }
        changes.value++
    }
}

fun shareIosCsv(csv: String) {
    val path = NSTemporaryDirectory() + "NetSessionTester-history.csv"
    val bytes = csv.encodeToByteArray()
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    check(NSFileManager.defaultManager.createFileAtPath(path, data, null)) { "无法写入导出文件" }
    val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
    val windows = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        .flatMap { it.windows.filterIsInstance<UIWindow>() }
    var presenter = windows.firstOrNull { it.rootViewController is UITabBarController }?.rootViewController
        ?: UIApplication.sharedApplication.keyWindow?.rootViewController ?: error("未找到活动窗口")
    while (presenter.presentedViewController != null) presenter = presenter.presentedViewController!!
    sheet.popoverPresentationController?.sourceView = presenter.view
    sheet.popoverPresentationController?.sourceRect = platform.CoreGraphics.CGRectMake(0.0, 0.0, 1.0, 1.0)
    presenter.presentViewController(sheet, true, null)
}
