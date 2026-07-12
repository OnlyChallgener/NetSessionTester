@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.demonv.netsessiontester

import android.Manifest
import android.content.ContentValues
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.location.LocationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.demonv.netsessiontester.data.HistoryStore
import com.demonv.netsessiontester.data.HistoryCounts
import com.demonv.netsessiontester.data.LogStore
import com.demonv.netsessiontester.data.SavedSettings
import com.demonv.netsessiontester.data.SettingsStore
import com.demonv.netsessiontester.model.AppUiState
import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.ReleaseUiState
import com.demonv.netsessiontester.model.RunPhase
import com.demonv.netsessiontester.model.SessionConfig
import com.demonv.netsessiontester.model.SessionSummary
import com.demonv.netsessiontester.model.TestMode
import com.demonv.netsessiontester.network.TcpTester
import com.demonv.netsessiontester.network.PublicIpDetector
import com.demonv.netsessiontester.network.PublicIpResult
import com.demonv.netsessiontester.network.NetworkDnsResolver
import com.demonv.netsessiontester.ui.theme.NetSessionTesterTheme
import com.demonv.netsessiontester.util.CsvExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private enum class MainTab(val label: String, val mark: String) {
    SETTINGS("设置", "settings"),
    TEST("测试", "play"),
    LOGS("历史", "logs")
}

private enum class AppToolPage {
    NONE,
    NSLOOKUP,
    TRACKET,
    MTU,
    ROAMING,
    IPV6_DIAGNOSTIC,
    PING_HISTORY
}

private enum class ChartMode(val label: String) {
    GROWTH("折线趋势"),
    PEAK("峰值/最高值")
}

private enum class SessionProtocolView(val label: String) {
    IPV4("IPv4"),
    IPV6("IPv6"),
    COMPARE("对比")
}

private enum class FinishReason(val label: String, val saveHistory: Boolean) {
    Completed("测试完成", true),
    FailureLimit("失败上限", true),
    NoGrowth("无增长确认", true),
    ConsecutiveFailure("连续失败", true),
    FdLimit("FD上限", true),
    ManualStop("手动停止", true),
    ForceRelease("强制释放", false),
    NetworkChange("网络环境变化", true),
    DnsFail("解析失败", false),
    Interrupted("测试中断", true),
    ServiceDestroyed("服务销毁保护", true)
}

private data class ChartPoint(
    val protocol: IpProtocol,
    val elapsedSec: Int,
    val active: Int,
    val failure: Int,
    val total: Int,
    val cps: Int,
    val phase: String,
    val timeEpochMs: Long = System.currentTimeMillis()
)

private data class PingPoint(
    val elapsedSec: Int,
    val latencyMs: Int?,
    val lossCount: Int = 0,
    val highLatency: Boolean = false,
    val sampleCount: Int = 1,
    val timeEpochMs: Long = System.currentTimeMillis(),
    val elapsedMs: Long = elapsedSec * 1_000L,
    val minLatencyMs: Int? = latencyMs,
    val maxLatencyMs: Int? = latencyMs
)

private data class HistoryUiSnapshot(
    val history: List<SessionSummary>,
    val sizeKb: Int,
    val savedCount: Int,
    val counts: HistoryCounts
)

private data class PingCompactStats(
    val current: Int?,
    val avg: Int?,
    val max: Int?,
    val min: Int?,
    val sampleTotal: Int,
    val lossTotal: Int,
    val lossPercent: Int
)

private data class PingLineSegment(val from: PingPoint, val to: PingPoint, val dashed: Boolean)

private const val TEST_UI_REFRESH_MS = 500L
private const val PING_LOG_SAVE_DEBOUNCE_MS = 400L
private const val MAX_RENDER_PING_POINTS = 300
private const val MAX_RENDER_SESSION_POINTS = 180

private fun <T> downsampleForRender(items: List<T>, maxPoints: Int): List<T> {
    if (items.size <= maxPoints) return items
    val step = ceil(items.size / maxPoints.toDouble()).roundToInt().coerceAtLeast(1)
    return items.filterIndexed { index, _ -> index % step == 0 || index == items.lastIndex }.takeLast(maxPoints)
}

private fun compactPingPointsForRender(points: List<PingPoint>): List<PingPoint> =
    downsampleForRender(points.sortedBy { it.elapsedMs }, MAX_RENDER_PING_POINTS)

private fun compactSessionPointsForRender(points: List<ChartPoint>): List<ChartPoint> =
    points.groupBy { it.protocol }
        .values
        .flatMap { downsampleForRender(it.sortedBy { point -> point.elapsedSec }, (MAX_RENDER_SESSION_POINTS / 2).coerceAtLeast(1)) }
        .sortedWith(compareBy<ChartPoint> { it.protocol.ordinal }.thenBy { it.elapsedSec })

private enum class PingProtocolMode(val label: String) {
    AUTO("自动"), IPV4("IPv4"), IPV6("IPv6")
}

private data class TargetPreset(val value: String, val note: String = "")

private val DefaultTargetPresets = listOf(
    TargetPreset("www.baidu.com", "默认"),
    TargetPreset("www.qq.com", "腾讯"),
    TargetPreset("www.huawei.com", "华为"),
    TargetPreset("223.5.5.5", "阿里DNS"),
    TargetPreset("119.29.29.29", "腾讯DNS")
)

private val DefaultPingTargetPresets = listOf(
    TargetPreset("223.5.5.5", "默认"),
    TargetPreset("192.168.0.1", "常见路由器"),
    TargetPreset("192.168.1.1", "常见路由器"),
    TargetPreset("192.168.5.1", "内网网关"),
    TargetPreset("192.168.110.1", "内网网关"),
    TargetPreset("www.qq.com", "腾讯"),
    TargetPreset("www.baidu.com", "百度"),
    TargetPreset("119.29.29.29", "腾讯DNS")
)

private data class PingIntervalPreset(val label: String, val intervalMs: String)
private val DefaultPingIntervalPresets = listOf(
    PingIntervalPreset("语音 25", "25"),
    PingIntervalPreset("游戏 30", "30"),
    PingIntervalPreset("视频 40", "40"),
    PingIntervalPreset("100", "100"),
    PingIntervalPreset("200", "200"),
    PingIntervalPreset("500", "500"),
    PingIntervalPreset("网页 1000", "1000")
)

private data class PingTimeoutPreset(val label: String, val valueMs: String?)
private val DefaultPingTimeoutPresets = listOf(
    PingTimeoutPreset("自动", null),
    PingTimeoutPreset("300", "300"),
    PingTimeoutPreset("500", "500"),
    PingTimeoutPreset("800", "800"),
    PingTimeoutPreset("1000", "1000"),
    PingTimeoutPreset("1500", "1500"),
    PingTimeoutPreset("3000", "3000")
)

private data class PingLogEntry(
    val timeEpochMs: Long = System.currentTimeMillis(),
    val target: String,
    val protocol: String,
    val latencyMs: Int?,
    val status: String,
    val note: String = "",
    val sessionId: Long = 0L,
    val elapsedMs: Long = 0L
) {
    val timeText: String
        get() = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timeEpochMs))
}

private class RttJitterWindow(private val maxSize: Int = 50) {
    private val values = ArrayDeque<Double>()

    fun reset() {
        values.clear()
    }

    fun onSuccess(rttMs: Double) {
        values.addLast(rttMs)
        while (values.size > maxSize) values.removeFirst()
    }

    fun currentJitterMs(): Double? {
        if (values.size < 2) return null
        var sum = 0.0
        var count = 0
        var previous: Double? = null
        values.forEach { value ->
            previous?.let { prev ->
                sum += kotlin.math.abs(value - prev)
                count++
            }
            previous = value
        }
        return if (count > 0) sum / count else null
    }
}

private fun trimPingLogSessions(
    logs: List<PingLogEntry>,
    maxSessions: Int = 12,
    maxEntriesPerSession: Int = 6000
): List<PingLogEntry> {
    if (logs.isEmpty()) return emptyList()
    return logs
        .groupBy { if (it.sessionId != 0L) it.sessionId else it.timeEpochMs }
        .entries
        .sortedByDescending { entry -> entry.value.minOfOrNull { it.timeEpochMs } ?: entry.key }
        .take(maxSessions)
        .flatMap { (_, entries) ->
            val sorted = entries.sortedBy { it.timeEpochMs }
            val start = sorted.firstOrNull { it.status == "开始" }
            val tail = sorted.filter { it.status != "开始" }.takeLast(maxEntriesPerSession - if (start != null) 1 else 0)
            listOfNotNull(start) + tail
        }
        .sortedBy { it.timeEpochMs }
}

private fun savePingLogs(context: Context, logs: List<PingLogEntry>) {
    runCatching {
        val arr = JSONArray()
        trimPingLogSessions(logs).forEach { item ->
            arr.put(JSONObject().apply {
                put("timeEpochMs", item.timeEpochMs)
                put("target", item.target)
                put("protocol", item.protocol)
                if (item.latencyMs != null) put("latencyMs", item.latencyMs) else put("latencyMs", JSONObject.NULL)
                put("status", item.status)
                put("note", item.note)
                put("sessionId", item.sessionId)
                put("elapsedMs", item.elapsedMs)
            })
        }
        context.getSharedPreferences("ping_log_store", Context.MODE_PRIVATE)
            .edit()
            .putString("ping_logs_v1", arr.toString())
            .apply()
    }
}

private fun loadPingLogs(context: Context): List<PingLogEntry> {
    return runCatching {
        val raw = context.getSharedPreferences("ping_log_store", Context.MODE_PRIVATE)
            .getString("ping_logs_v1", "[]") ?: "[]"
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(PingLogEntry(
                    timeEpochMs = obj.optLong("timeEpochMs", System.currentTimeMillis()),
                    target = obj.optString("target", ""),
                    protocol = obj.optString("protocol", "AUTO"),
                    latencyMs = if (obj.isNull("latencyMs")) null else obj.optInt("latencyMs"),
                    status = obj.optString("status", "成功"),
                    note = obj.optString("note", ""),
                    sessionId = obj.optLong("sessionId", 0L),
                    elapsedMs = obj.optLong("elapsedMs", 0L)
                ))
            }
        }
    }.getOrDefault(emptyList())
}

private fun estimatePingLogStorageBytes(logs: List<PingLogEntry>): Int {
    val arr = JSONArray()
    trimPingLogSessions(logs).forEach { item ->
        arr.put(JSONObject().apply {
            put("timeEpochMs", item.timeEpochMs)
            put("target", item.target)
            put("protocol", item.protocol)
            if (item.latencyMs != null) put("latencyMs", item.latencyMs) else put("latencyMs", JSONObject.NULL)
            put("status", item.status)
            put("note", item.note)
            put("sessionId", item.sessionId)
            put("elapsedMs", item.elapsedMs)
        })
    }
    return arr.toString().toByteArray(Charsets.UTF_8).size
}

private fun formatBytes(bytes: Int): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${((bytes / 1024.0) * 10).roundToInt() / 10.0}KB"
    else -> "${((bytes / 1024.0 / 1024.0) * 10).roundToInt() / 10.0}MB"
}

private data class PingAxisRange(val min: Int, val max: Int)

private fun pingMainAxisMax(values: List<Int>): Int {
    if (values.isEmpty()) return 50
    val sorted = values.sorted()
    val idx = ((sorted.size - 1) * 0.95f).roundToInt().coerceIn(0, sorted.lastIndex)
    val main = sorted[idx].coerceAtLeast(1)
    return pingYAxisMax(main)
}

private fun computePingYAxisRange(values: List<Int>): PingAxisRange {
    val clean = values.filter { it in 0..20_000 }.sorted()
    if (clean.isEmpty()) return PingAxisRange(0, 50)
    val low = clean.first()
    val high = clean.last().coerceAtLeast(low + 1)
    val rawSpan = (high - low).coerceAtLeast(1)
    val pad = maxOf((rawSpan * 0.25f).roundToInt(), when {
        high <= 20 -> 2
        high <= 100 -> 5
        high <= 300 -> 10
        else -> 20
    })
    var bottom = (low - pad).coerceAtLeast(0)
    var top = high + pad
    val minSpan = when {
        high <= 20 -> 8
        high <= 100 -> 25
        high <= 300 -> 60
        else -> 120
    }
    if (top - bottom < minSpan) {
        val extra = minSpan - (top - bottom)
        bottom = (bottom - extra / 2).coerceAtLeast(0)
        top = bottom + minSpan
    }
    return PingAxisRange(bottom, nicePingAxisTop(top))
}

private fun nicePingAxisTop(value: Int): Int {
    if (value <= 10) return 10
    val step = when {
        value <= 50 -> 5
        value <= 100 -> 10
        value <= 300 -> 25
        value <= 1000 -> 50
        else -> 100
    }
    return ((value + step - 1) / step) * step
}

private fun pingAxisLabels(range: PingAxisRange): List<Int> {
    val span = (range.max - range.min).coerceAtLeast(1)
    return listOf(
        range.max,
        range.min + (span * 3 / 4),
        range.min + (span / 2),
        range.min + (span / 4),
        range.min
    ).distinct()
}

private fun loadCardOrder(context: Context, key: String, defaults: List<String>): List<String> {
    val raw = context.getSharedPreferences("layout_order_store", Context.MODE_PRIVATE).getString(key, null)
    val saved = raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    return (saved.filter { it in defaults } + defaults.filterNot { it in saved }).distinct()
}

private fun saveCardOrder(context: Context, key: String, order: List<String>) {
    context.getSharedPreferences("layout_order_store", Context.MODE_PRIVATE)
        .edit()
        .putString(key, order.joinToString(","))
        .apply()
}

private fun cleanTargetText(value: String): String = value.trim().trimEnd('/')

private fun loadTargetHistory(context: Context, key: String): List<String> {
    val raw = context.getSharedPreferences("target_history_store", Context.MODE_PRIVATE).getString(key, "") ?: ""
    return raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
}

private fun saveTargetHistory(context: Context, key: String, items: List<String>) {
    val clean = items.map { cleanTargetText(it) }.filter { it.isNotBlank() }.distinct().take(10)
    context.getSharedPreferences("target_history_store", Context.MODE_PRIVATE)
        .edit()
        .putString(key, clean.joinToString("\n"))
        .apply()
}

private fun rememberTargetHistoryItem(context: Context, key: String, target: String): List<String> {
    val clean = cleanTargetText(target)
    if (clean.isBlank()) return loadTargetHistory(context, key)
    val next = listOf(clean) + loadTargetHistory(context, key).filterNot { it.equals(clean, ignoreCase = true) }
    saveTargetHistory(context, key, next)
    return next.take(10)
}


private const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/OnlyChallgener/NetSessionTester/main/update.json"
private const val PROJECT_GITHUB_URL = "https://github.com/OnlyChallgener/NetSessionTester"
private const val UPDATE_POSTPONE_MS = 8 * 60 * 1000L

private data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val title: String,
    val content: List<String>,
    val apkUrl: String,
    val githubUrl: String,
    val force: Boolean = false,
    val status: String = "ready",
    val minReadyFileSize: Long = 3_000_000L,
    val sha256: String = ""
)

private data class UpdateDownloadUi(
    val active: Boolean = false,
    val finished: Boolean = false,
    val failed: Boolean = false,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val message: String = "",
    val apkFilePath: String? = null
)

private enum class BottomNoticeTone { Info, Success, Warning, Error }

private data class BottomNoticeUi(
    val visible: Boolean = false,
    val message: String = "",
    val tone: BottomNoticeTone = BottomNoticeTone.Info,
    val id: Long = 0L
)

private fun currentAppVersionCode(context: Context): Long = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
}.getOrDefault(0L)

private fun currentAppVersionName(context: Context): String {
    return runCatching {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        pkg.versionName?.takeIf { it.isNotBlank() } ?: "V1.0.8"
    }.getOrDefault("V1.0.8")
}

private fun displayVersionName(raw: String): String {
    val clean = raw.trim()
    if (clean.isBlank()) return "未知版本"
    return clean
}

private fun formatVersionBuild(versionName: String, versionCode: Long): String =
    "${displayVersionName(versionName)} build $versionCode"

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 KB"
    bytes < 1024L * 1024L -> "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    else -> String.format(java.util.Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)
}

private fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0L -> "-- KB/s"
    bytesPerSecond < 1024L * 1024L -> "${(bytesPerSecond / 1024L).coerceAtLeast(1L)} KB/s"
    else -> String.format(java.util.Locale.US, "%.2f MB/s", bytesPerSecond / 1024.0 / 1024.0)
}

private fun formatReleaseDuration(ms: Long): String =
    String.format(java.util.Locale.US, "%.1fs", ms.coerceAtLeast(0L) / 1000.0)

private fun formatPingDuration(ms: Long): String {
    val totalSec = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatPingJitter(value: Double): String =
    if (value < 10.0) String.format(java.util.Locale.US, "%.1fms", value) else "${value.roundToInt()}ms"

private fun formatReleaseEta(seconds: Int): String = when {
    seconds <= 0 -> "<1s"
    else -> "约 ${seconds}s"
}

private suspend fun fetchUpdateInfo(updateJsonUrl: String = UPDATE_JSON_URL): UpdateInfo = withContext(Dispatchers.IO) {
    val conn = (URL(updateJsonUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 6000
        readTimeout = 8000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-cache")
    }
    try {
        val code = conn.responseCode
        if (code !in 200..299) error("更新信息读取失败：HTTP $code")
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(text)
        val contentObj = obj.optJSONArray("content")
        val content = if (contentObj != null) {
            buildList {
                for (i in 0 until contentObj.length()) add(contentObj.optString(i))
            }.filter { it.isNotBlank() }
        } else {
            obj.optString("content", "").lines().filter { it.isNotBlank() }
        }
        UpdateInfo(
            versionCode = obj.optLong("versionCode", 0L),
            versionName = obj.optString("versionName", ""),
            title = obj.optString("title", "发现新版本"),
            content = content.ifEmpty { listOf("优化稳定性和界面体验。") },
            apkUrl = obj.optString("apkUrl", ""),
            githubUrl = obj.optString("githubUrl", PROJECT_GITHUB_URL),
            force = obj.optBoolean("force", false),
            status = obj.optString("status", "ready"),
            minReadyFileSize = obj.optLong("minReadyFileSize", 3_000_000L).coerceAtLeast(0L),
            sha256 = obj.optString("sha256", "")
        )
    } finally {
        conn.disconnect()
    }
}

private fun isUpdateReadyStatus(status: String): Boolean = status.equals("ready", ignoreCase = true) || status.equals("hotfix", ignoreCase = true)

private suspend fun verifyUpdateApkAvailable(info: UpdateInfo): Long = withContext(Dispatchers.IO) {
    if (info.apkUrl.isBlank()) error("更新包链接为空")
    fun open(method: String): HttpURLConnection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 6000
        readTimeout = 8000
        requestMethod = method
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "NetSessionTester/${info.versionName}")
        setRequestProperty("Cache-Control", "no-cache")
        if (method == "GET") setRequestProperty("Range", "bytes=0-0")
    }
    var conn: HttpURLConnection? = null
    try {
        conn = open("HEAD")
        var code = conn.responseCode
        var length = conn.contentLengthLong
        conn.disconnect()
        if (code == HttpURLConnection.HTTP_BAD_METHOD || code == HttpURLConnection.HTTP_FORBIDDEN || length <= 0L) {
            conn = open("GET")
            code = conn.responseCode
            length = conn.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: conn.getHeaderFieldLong("Content-Length", length)
        }
        if (code !in 200..299 && code != HttpURLConnection.HTTP_PARTIAL) {
            when (code) {
                404 -> error("安装包暂未发布完成：HTTP 404，可能 Release 还没上传 APK")
                403 -> error("安装包暂不可访问：HTTP 403，建议打开 GitHub 查看")
                else -> error("安装包暂不可用：HTTP $code")
            }
        }
        val minSize = info.minReadyFileSize.coerceAtLeast(1L)
        if (length in 1 until minSize) error("安装包大小异常：${formatBytes(length)}，可能 Release 附件未上传完成")
        length.coerceAtLeast(0L)
    } finally {
        conn?.disconnect()
    }
}

private suspend fun downloadUpdateApk(
    context: Context,
    info: UpdateInfo,
    onProgress: (UpdateDownloadUi) -> Unit
): File = withContext(Dispatchers.IO) {
    if (info.apkUrl.isBlank()) error("更新包链接为空")
    val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
    val apkName = runCatching { URL(info.apkUrl).path.substringAfterLast('/').ifBlank { "NetSessionTester-v${info.versionName.ifBlank { info.versionCode.toString() }}-signed.apk" } }
        .getOrDefault("NetSessionTester-v${info.versionName.ifBlank { info.versionCode.toString() }}-signed.apk")
    val outFile = File(dir, apkName)
    val partFile = File(dir, "$apkName.part")
    if (outFile.exists()) outFile.delete()
    if (partFile.exists()) partFile.delete()

    val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 15000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "NetSessionTester/${info.versionName}")
    }
    try {
        val code = conn.responseCode
        if (code !in 200..299) {
            val hint = when (code) {
                404 -> "下载失败：HTTP 404，可能 GitHub 还未发布 release 包或 APK 文件名不一致"
                403 -> "下载失败：HTTP 403，可能 GitHub 限制访问，建议打开 GitHub 手动下载"
                else -> "下载失败：HTTP $code"
            }
            error(hint)
        }
        val total = conn.contentLengthLong.takeIf { it > 0L } ?: 0L
        if (total > 0L && total < info.minReadyFileSize.coerceAtLeast(1L)) {
            error("安装包大小异常：${formatBytes(total)}，可能 Release 附件未上传完成")
        }
        val start = System.currentTimeMillis()
        var lastAt = start
        var lastBytes = 0L
        var downloaded = 0L
        var slowSince = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        BufferedInputStream(conn.inputStream).use { input ->
            FileOutputStream(partFile).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    if (!currentCoroutineContext().isActive) throw CancellationException("已取消下载")
                    output.write(buffer, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastAt >= 500L) {
                        val deltaBytes = downloaded - lastBytes
                        val deltaMs = (now - lastAt).coerceAtLeast(1L)
                        val speed = deltaBytes * 1000L / deltaMs
                        val progress = if (total > 0L) ((downloaded * 100L / total).toInt()).coerceIn(0, 99) else 0
                        val elapsed = now - start
                        val slow = elapsed > 8_000L && speed in 1L until 30L * 1024L
                        slowSince = when {
                            slow && slowSince == 0L -> now
                            !slow -> 0L
                            else -> slowSince
                        }
                        val msg = if (slowSince > 0L && now - slowSince > 10_000L) {
                            "下载速度较慢，建议切换代理网络或打开 GitHub 手动下载"
                        } else "正在下载更新包"
                        onProgress(UpdateDownloadUi(true, false, false, progress, downloaded, total, speed, msg, partFile.absolutePath))
                        lastAt = now
                        lastBytes = downloaded
                    }
                }
            }
        }
        if (!partFile.renameTo(outFile)) {
            partFile.copyTo(outFile, overwrite = true)
            partFile.delete()
        }
        onProgress(UpdateDownloadUi(active = false, finished = true, progress = 100, downloadedBytes = downloaded, totalBytes = total, speedBytesPerSecond = 0L, message = "下载完成", apkFilePath = outFile.absolutePath))
        outFile
    } catch (e: Exception) {
        runCatching { partFile.delete() }
        runCatching { outFile.delete() }
        val reason = e.message ?: "下载失败，建议切换代理网络或打开 GitHub"
        onProgress(UpdateDownloadUi(active = false, failed = true, message = reason, apkFilePath = null))
        throw e
    } finally {
        conn.disconnect()
    }
}

private fun installDownloadedApk(context: Context, apkFile: File) {
    if (!apkFile.exists()) {
        Toast.makeText(context, "更新包不存在，请重新下载", Toast.LENGTH_SHORT).show()
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Toast.makeText(context, "请允许本应用安装更新包，授权后再点立即安装", Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private data class NetworkEnvironment(
    val typeLabel: String = "未知网络",
    val carrierName: String = "未知",
    val mccMnc: String = "",
    val simMccMnc: String = "",
    val hasWifi: Boolean = false,
    val hasCellular: Boolean = false,
    val hasVpn: Boolean = false,
    val hasInternet: Boolean = false
)

private data class NetworkProbeInfo(
    val localIp: String = "待检测",
    val carrier: String = "未知",
    val natType: String = "待检测",
    val latencyText: String = "待检测",
    val portText: String = "待检测",
    val priority: String = "待检测",
    val mappingBehavior: String = "待检测",
    val filterBehavior: String = "待检测",
    val ipv6Status: String = "待检测",
    val dnsStatus: String = "待检测",
    val confidence: String = "待检测",
    val diagnosis: String = "",
    val proxyNotice: String = "",
    val refreshMode: String = "待检测"
)

private data class CarrierDetectionResult(
    val carrier: String = "未知",
    val source: String = "未知"
)

private data class StunProbeResult(
    val mappedIp: String,
    val mappedPort: Int,
    val localPort: Int,
    val successCount: Int = 1,
    val totalCount: Int = 1,
    val mappedPorts: Set<Int> = setOf(mappedPort),
    val mappedIps: Set<String> = setOf(mappedIp),
    val roundCount: Int = 1,
    val stableRoundCount: Int = 1,
    val usedBackup: Boolean = false,
    val sourceHost: String = "",
    val detectionMethod: String = "RFC8489多节点",
    val filteringVerified: Boolean = false,
    val rfc5780Server: String = "",
    val rfc5780MappingBehavior: String = "",
    val rfc5780FilteringBehavior: String = ""
) {
    val portStable: Boolean
        get() = mappedPorts.size <= 1

    val ipStable: Boolean
        get() = mappedIps.size <= 1

    val roundStable: Boolean
        get() = stableRoundCount >= roundCount.coerceAtLeast(1)
}

private data class StunEndpoint(
    val host: String,
    val port: Int
) {
    val key: String
        get() = "$host:$port"
}

private data class StunRequest(
    val bytes: ByteArray,
    val transactionId: ByteArray
)

private data class PendingStunRequest(
    val endpoint: StunEndpoint,
    val address: InetAddress,
    val request: StunRequest
)

private data class StunRawResponse(
    val mappedIp: String,
    val mappedPort: Int,
    val otherAddress: InetSocketAddress? = null,
    val responseOrigin: InetSocketAddress? = null,
    val remoteAddress: InetSocketAddress? = null
)

private data class Rfc5780ProbeResult(
    val serverKey: String,
    val mappedIp: String,
    val mappedPort: Int,
    val mappingBehavior: String,
    val filteringBehavior: String
)

private enum class ManualNatMode(val label: String) {
    RFC5780("RFC5780"),
    RFC3489("RFC3489")
}

private data class ManualNatResult(
    val success: Boolean,
    val natType: String,
    val mappingBehavior: String,
    val filteringBehavior: String,
    val localAddress: String,
    val publicAddress: String,
    val method: String,
    val server: String,
    val message: String = ""
)

private data class NatHistoryRecord(
    val id: Long,
    val timeText: String,
    val mode: String,
    val natType: String,
    val mappingBehavior: String,
    val filteringBehavior: String,
    val localAddress: String,
    val publicAddress: String,
    val method: String,
    val server: String,
    val message: String,
    val durationMs: Long
)

private const val NAT_HISTORY_PREFS = "nat_history_v1"
private const val NAT_HISTORY_KEY = "records"
private const val NAT_HISTORY_MAX = 20

private fun NatHistoryRecord.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("timeText", timeText)
    put("mode", mode)
    put("natType", natType)
    put("mappingBehavior", mappingBehavior)
    put("filteringBehavior", filteringBehavior)
    put("localAddress", localAddress)
    put("publicAddress", publicAddress)
    put("method", method)
    put("server", server)
    put("message", message)
    put("durationMs", durationMs)
}

private fun natHistoryRecordFromJson(obj: JSONObject): NatHistoryRecord? = runCatching {
    NatHistoryRecord(
        id = obj.optLong("id", 0L).takeIf { it > 0L } ?: return@runCatching null,
        timeText = obj.optString("timeText", ""),
        mode = obj.optString("mode", ""),
        natType = obj.optString("natType", ""),
        mappingBehavior = obj.optString("mappingBehavior", ""),
        filteringBehavior = obj.optString("filteringBehavior", ""),
        localAddress = obj.optString("localAddress", ""),
        publicAddress = obj.optString("publicAddress", ""),
        method = obj.optString("method", ""),
        server = obj.optString("server", ""),
        message = obj.optString("message", ""),
        durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L)
    )
}.getOrNull()

private fun loadNatHistory(context: Context): List<NatHistoryRecord> {
    val raw = context.getSharedPreferences(NAT_HISTORY_PREFS, Context.MODE_PRIVATE)
        .getString(NAT_HISTORY_KEY, "[]").orEmpty()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val record = natHistoryRecordFromJson(array.optJSONObject(index) ?: continue)
                if (record != null) add(record)
            }
        }.sortedByDescending { it.id }.take(NAT_HISTORY_MAX)
    }.getOrDefault(emptyList())
}

private fun saveNatHistory(context: Context, record: NatHistoryRecord): List<NatHistoryRecord> {
    val next = (listOf(record) + loadNatHistory(context).filterNot { it.id == record.id })
        .sortedByDescending { it.id }
        .take(NAT_HISTORY_MAX)
    val array = JSONArray()
    next.forEach { array.put(it.toJson()) }
    context.getSharedPreferences(NAT_HISTORY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(NAT_HISTORY_KEY, array.toString())
        .apply()
    return next
}

private fun natHistorySizeKb(records: List<NatHistoryRecord>): Double {
    val array = JSONArray()
    records.forEach { array.put(it.toJson()) }
    return array.toString().toByteArray(Charsets.UTF_8).size / 1024.0
}

private val stunRandom = java.security.SecureRandom()

private data class DnsProbeResult(
    val systemHasA: Boolean,
    val systemHasAaaa: Boolean,
    val domesticHasAaaa: Boolean,
    val globalHasAaaa: Boolean,
    val fakeIpDetected: Boolean,
    val error: String? = null
) {
    val backupHasAaaa: Boolean
        get() = domesticHasAaaa || globalHasAaaa

    val backupSource: String
        get() = when {
            domesticHasAaaa -> "国内备用"
            globalHasAaaa -> "国外备用"
            else -> "备用"
        }
}

private fun detectNetworkEnvironment(context: Context): NetworkEnvironment {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val caps = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
    val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val telephony = context.getSystemService(TelephonyManager::class.java)
    val networkOperator = runCatching { telephony?.networkOperator.orEmpty() }.getOrDefault("")
    val simOperator = runCatching { telephony?.simOperator.orEmpty() }.getOrDefault("")
    val rawCarrier = runCatching {
        telephony?.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: telephony?.simOperatorName?.takeIf { it.isNotBlank() }
            ?: "未知"
    }.getOrDefault("未知")
    val mccCarrier = carrierFromMccMnc(networkOperator).takeIf { it != "未知" }
        ?: carrierFromMccMnc(simOperator).takeIf { it != "未知" }
    val carrier = mccCarrier ?: normalizeCarrierName(rawCarrier)
    val typeLabel = when {
        hasVpn && hasWifi -> "WiFi + VPN"
        hasVpn && hasCellular -> "蜂窝 + VPN"
        hasWifi -> "WiFi"
        hasCellular -> "蜂窝网络"
        hasVpn -> "VPN"
        else -> "未知网络"
    }
    return NetworkEnvironment(typeLabel, carrier, networkOperator, simOperator, hasWifi, hasCellular, hasVpn, hasInternet)
}

private fun carrierFromMccMnc(code: String): String {
    return when (code.take(5)) {
        "46000", "46002", "46004", "46007", "46008" -> "中国移动"
        "46001", "46006", "46009" -> "中国联通"
        "46003", "46005", "46011" -> "中国电信"
        "46015" -> "中国广电"
        else -> "未知"
    }
}

private fun normalizeCarrierName(name: String): String {
    val raw = name.trim()
    val value = raw.lowercase(Locale.US)
    return when {
        value.isBlank() || value == "unknown" || value == "null" -> "未知"
        value.contains("street") || value.contains("road") || value.contains("building") || value.contains("floor") -> "未知"
        value.contains("jin rong") || value.contains("no. 1") || value.contains("address") -> "未知"

        "广电" in value || "china broadnet" in value || "china broadcasting network" in value ||
            "china broadcast network" in value || "cbn" in value || "broadnet" in value ||
            "radio and television network" in value -> "中国广电"

        "移动" in value || "china mobile" in value || "chinamobile" in value ||
            "cmcc" in value || "cmnet" in value || "china mobile international" in value -> "中国移动"

        "联通" in value || "china unicom" in value || "unicom" in value ||
            "china169" in value || "cucc" in value || "cncgroup" in value ||
            "china netcom" in value -> "中国联通"

        "电信" in value || "chinanet" in value || "china telecom" in value ||
            "telecom china" in value || "ctcc" in value || "cn2" in value -> "中国电信"

        "cernet" in value || "教育网" in value -> "教育网"
        "dr.peng" in value || "dr peng" in value || "鹏博士" in value -> "鹏博士"
        else -> "未知"
    }
}

private fun inferCarrierFromIpv6Prefix(ipv6: String): String {
    val value = ipv6.trim().lowercase()
    val secondHextet = value.split(":").getOrNull(1)?.toIntOrNull(16)
    return when {
        value.startsWith("240e:") -> "中国电信"
        value.startsWith("2409:") -> "中国移动"
        value.startsWith("2408:") -> "中国联通"
        value.startsWith("240a:") && secondHextet != null && secondHextet in 0x4000..0x47ff -> "中国广电"
        else -> "未知"
    }
}

private data class IpIntelligenceResult(
    val ip: String,
    val countryCode: String = "",
    val countryName: String = "",
    val companyName: String = "",
    val companyType: String = "",
    val asnNumber: Int = 0,
    val asnOrganization: String = "",
    val asnDescription: String = "",
    val asnType: String = "",
    val isDatacenter: Boolean = false,
    val isMobile: Boolean = false,
    val isVpn: Boolean = false,
    val isProxy: Boolean = false,
    val isTor: Boolean = false,
    val source: String = ""
)

private data class IpIntelligenceCacheEntry(
    val result: IpIntelligenceResult?,
    val checkedAtMs: Long
)

private val ipIntelligenceCache = mutableMapOf<String, IpIntelligenceCacheEntry>()
private val ipIntelligenceCacheLock = Any()

private fun knownChinaCarrierFromAsn(asn: Int): String = when (asn) {
    4134, 4809 -> "中国电信"
    4837, 9929 -> "中国联通"
    9808, 58453 -> "中国移动"
    else -> "未知"
}

private fun carrierFromIntelligence(result: IpIntelligenceResult): String {
    val textCarrier = listOf(
        result.companyName,
        result.asnOrganization,
        result.asnDescription
    ).asSequence()
        .map(::normalizeCarrierName)
        .firstOrNull { it != "未知" }
        ?: "未知"
    if (textCarrier != "未知") return textCarrier
    return knownChinaCarrierFromAsn(result.asnNumber)
}

private fun fetchIpApiIs(target: String): IpIntelligenceResult? {
    val encoded = URLEncoder.encode(target, Charsets.UTF_8.name())
    val conn = (URL("https://api.ipapi.is/?q=$encoded").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 1_200
        readTimeout = 1_800
        useCaches = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "NetSessionTester")
    }
    return try {
        if (conn.responseCode !in 200..299) return null
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(body)
        if (obj.has("error")) return null

        val company = obj.optJSONObject("company")
        val asn = obj.optJSONObject("asn")
        val location = obj.optJSONObject("location")
        IpIntelligenceResult(
            ip = obj.optString("ip", target).ifBlank { target },
            countryCode = location?.optString("country_code", "").orEmpty(),
            countryName = location?.optString("country", "").orEmpty(),
            companyName = company?.optString("name", "").orEmpty(),
            companyType = company?.optString("type", "").orEmpty(),
            asnNumber = asn?.optInt("asn", 0) ?: 0,
            asnOrganization = asn?.optString("org", "").orEmpty(),
            asnDescription = asn?.optString("descr", "").orEmpty(),
            asnType = asn?.optString("type", "").orEmpty(),
            isDatacenter = obj.optBoolean("is_datacenter", false),
            isMobile = obj.optBoolean("is_mobile", false),
            isVpn = obj.optBoolean("is_vpn", false),
            isProxy = obj.optBoolean("is_proxy", false),
            isTor = obj.optBoolean("is_tor", false),
            source = "ipapi.is"
        )
    } finally {
        conn.disconnect()
    }
}

private fun fetchIpWhoIs(target: String): IpIntelligenceResult? {
    val encoded = URLEncoder.encode(target, Charsets.UTF_8.name())
    val conn = (URL("https://ipwho.is/$encoded?fields=success,country,country_code,connection").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 1_200
        readTimeout = 1_800
        useCaches = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "NetSessionTester")
    }
    return try {
        if (conn.responseCode !in 200..299) return null
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(body)
        if (!obj.optBoolean("success", false)) return null
        val connection = obj.optJSONObject("connection")
        IpIntelligenceResult(
            ip = target,
            countryCode = obj.optString("country_code", ""),
            countryName = obj.optString("country", ""),
            companyName = connection?.optString("isp", "").orEmpty(),
            asnOrganization = connection?.optString("org", "").orEmpty(),
            asnDescription = connection?.optString("asn", "").orEmpty(),
            source = "ipwho.is"
        )
    } finally {
        conn.disconnect()
    }
}

private fun queryIpIntelligence(ip: String): IpIntelligenceResult? {
    val target = ip.trim()
    if (!target.isUsableIpText()) return null

    val now = System.currentTimeMillis()
    synchronized(ipIntelligenceCacheLock) {
        ipIntelligenceCache[target]?.let { cached ->
            val ttl = if (cached.result != null) 30 * 60_000L else 15_000L
            if (now - cached.checkedAtMs < ttl) return cached.result
        }
    }

    // 两个情报源并发查询，取第一个有效结果；整体最多等待约3.2秒。
    // 避免国外线路不佳时串行超时累加到十几秒。
    val executor = Executors.newFixedThreadPool(2)
    val completion = ExecutorCompletionService<IpIntelligenceResult?>(executor)
    completion.submit(Callable { runCatching { fetchIpApiIs(target) }.getOrNull() })
    completion.submit(Callable { runCatching { fetchIpWhoIs(target) }.getOrNull() })

    var result: IpIntelligenceResult? = null
    val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(3_200L)
    try {
        for (index in 0 until 2) {
            val remainingNs = deadlineNs - System.nanoTime()
            if (remainingNs <= 0L) break
            val future = completion.poll(remainingNs, TimeUnit.NANOSECONDS) ?: break
            val candidate = runCatching { future.get() }.getOrNull()
            if (candidate != null) {
                result = candidate
                break
            }
        }
    } finally {
        executor.shutdownNow()
    }

    synchronized(ipIntelligenceCacheLock) {
        ipIntelligenceCache[target] = IpIntelligenceCacheEntry(result, now)
        if (ipIntelligenceCache.size > 24) {
            ipIntelligenceCache.entries
                .sortedBy { it.value.checkedAtMs }
                .take(ipIntelligenceCache.size - 24)
                .forEach { ipIntelligenceCache.remove(it.key) }
        }
    }
    return result
}

private fun detectCarrierFromAsn(ip: String): CarrierDetectionResult? {
    val intelligence = queryIpIntelligence(ip) ?: return null
    val carrier = carrierFromIntelligence(intelligence)
    return carrier.takeIf { it != "未知" }?.let {
        CarrierDetectionResult(it, "${intelligence.source}/ASN")
    }
}

private data class VpnExitDisplay(
    val ip: String,
    val label: String,
    val checkedAtMs: Long
)

private val vpnExitDisplayCache = mutableMapOf<String, VpnExitDisplay>()
private val vpnExitDisplayCacheLock = Any()

private fun countryNameZh(code: String, fallback: String): String {
    val normalized = code.trim().uppercase(Locale.US)
    val fixed = when (normalized) {
        "CN" -> "中国"
        "US" -> "美国"
        "JP" -> "日本"
        "SG" -> "新加坡"
        "HK" -> "中国香港"
        "MO" -> "中国澳门"
        "TW" -> "中国台湾"
        "GB" -> "英国"
        "DE" -> "德国"
        "FR" -> "法国"
        "CA" -> "加拿大"
        "AU" -> "澳大利亚"
        "KR" -> "韩国"
        "NL" -> "荷兰"
        "RU" -> "俄罗斯"
        "IN" -> "印度"
        "MY" -> "马来西亚"
        "TH" -> "泰国"
        "PH" -> "菲律宾"
        "ID" -> "印度尼西亚"
        "VN" -> "越南"
        else -> ""
    }
    if (fixed.isNotBlank()) return fixed
    val localized = runCatching {
        Locale("", normalized).getDisplayCountry(Locale.SIMPLIFIED_CHINESE)
    }.getOrDefault("").trim()
    return localized.ifBlank { fallback.trim().ifBlank { "未知地区" } }
}

private fun organizationLooksLikeHosting(text: String): Boolean {
    val value = text.lowercase(Locale.US)
    val keywords = listOf(
        "amazon", "aws", "google cloud", "google llc", "microsoft", "azure",
        "digitalocean", "vultr", "linode", "akamai", "ovh", "hetzner",
        "oracle cloud", "alibaba cloud", "aliyun", "tencent cloud", "cloudflare",
        "huawei cloud", "hosting", "hostinger", "datacenter", "data center",
        "server", "colo", "colocation", "leaseweb", "rackspace", "choopa",
        "contabo", "m247", "zenlayer", "cloud", "vps", "dedicated"
    )
    return keywords.any { it in value }
}

private fun organizationLooksLikeAccessIsp(text: String): Boolean {
    val value = text.lowercase(Locale.US)
    val keywords = listOf(
        "telecom", "communications", "broadband", "fiber", "fibre", "cable",
        "wireless", "mobile", "internet service", "internet provider", "isp",
        "residential", "comcast", "verizon", "spectrum", "charter", "cox",
        "vodafone", "telefonica", "orange", "deutsche telekom", "softbank",
        "kddi", "ntt", "singtel", "starhub", "telstra", "unicom", "chinanet",
        "china mobile"
    )
    return keywords.any { it in value }
}

private fun vpnExitTypeFromIntelligence(result: IpIntelligenceResult): String {
    val orgText = listOf(result.companyName, result.asnOrganization, result.asnDescription)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val typeText = listOf(result.companyType, result.asnType)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .lowercase(Locale.US)

    return when {
        result.isDatacenter || typeText == "hosting" || organizationLooksLikeHosting(orgText) -> "机房"
        result.isMobile -> "移动网络"
        typeText == "isp" || organizationLooksLikeAccessIsp(orgText) -> "家庭地址"
        else -> "未知类型"
    }
}

private fun detectVpnExitDisplay(ipv4: String, ipv6: String): String {
    val target = ipv4.takeIf { it.isUsableIpText() }
        ?: ipv6.takeIf { it.isUsableIpText() }
        ?: return "VPN/代理"
    val now = System.currentTimeMillis()
    synchronized(vpnExitDisplayCacheLock) {
        vpnExitDisplayCache[target]?.takeIf { now - it.checkedAtMs < 10 * 60_000L }?.let {
            return it.label
        }
    }

    val intelligence = queryIpIntelligence(target)
    val label = if (intelligence != null) {
        val country = countryNameZh(intelligence.countryCode, intelligence.countryName)
        "$country · ${vpnExitTypeFromIntelligence(intelligence)}"
    } else {
        "VPN/代理"
    }

    synchronized(vpnExitDisplayCacheLock) {
        vpnExitDisplayCache[target] = VpnExitDisplay(target, label, now)
        if (vpnExitDisplayCache.size > 12) {
            vpnExitDisplayCache.entries
                .sortedBy { it.value.checkedAtMs }
                .take(vpnExitDisplayCache.size - 12)
                .forEach { vpnExitDisplayCache.remove(it.key) }
        }
    }
    return label
}

private fun displayCarrierFromEnv(env: NetworkEnvironment, ipv4: String, ipv6: String, full: Boolean): String {
    val prefixCarrier = inferCarrierFromIpv6Prefix(ipv6)
    val ipCarrier = if (!env.hasVpn) {
        detectCarrierFromAsn(ipv4).takeIf { it?.carrier != "未知" }
            ?: detectCarrierFromAsn(ipv6).takeIf { it?.carrier != "未知" }
    } else null

    return when {
        env.hasVpn -> detectVpnExitDisplay(ipv4, ipv6)
        env.hasCellular && env.carrierName.isNotBlank() && env.carrierName != "未知" -> env.carrierName
        ipCarrier != null -> ipCarrier.carrier
        prefixCarrier != "未知" -> prefixCarrier
        env.hasWifi -> "WiFi出口未知"
        ipv4.isUsableIpText() || ipv6.isUsableIpText() -> "出口未知"
        else -> "未知"
    }
}


private suspend fun detectNetworkProbe(
    context: Context,
    network: Network?,
    publicIpResult: PublicIpResult,
    env: NetworkEnvironment,
    targetHost: String,
    targetPort: Int
): NetworkProbeInfo = withContext(Dispatchers.IO) {
    // NAT 类型只由手动诊断更新；这里仅刷新轻量网络信息。
    buildNetworkProbeInfo(
        context = context,
        network = network,
        publicIpResult = publicIpResult,
        env = env,
        targetHost = targetHost,
        targetPort = targetPort,
        full = false
    )
}

private suspend fun detectNetworkProbeLight(
    context: Context,
    network: Network?,
    publicIpResult: PublicIpResult,
    env: NetworkEnvironment,
    targetHost: String,
    targetPort: Int
): NetworkProbeInfo = withContext(Dispatchers.IO) {
    buildNetworkProbeInfo(
        context = context,
        network = network,
        publicIpResult = publicIpResult,
        env = env,
        targetHost = targetHost,
        targetPort = targetPort,
        full = false
    )
}

private fun buildNetworkProbeInfo(
    context: Context,
    network: Network?,
    publicIpResult: PublicIpResult,
    env: NetworkEnvironment,
    targetHost: String,
    targetPort: Int,
    full: Boolean
): NetworkProbeInfo {
    val localIpv4 = localIpv4Addresses().firstOrNull()
    val localIpv6 = localIpv6Addresses().firstOrNull()
    val publicV4FromHttp = publicIpResult.ipv4.takeIf { isUsableIpv4(it) }
    val hasPublicV6 = publicIpResult.ipv6.isUsableIpText()
    val dns = runCatching { dnsProbe(context, targetHost, network) }.getOrElse {
        DnsProbeResult(
            systemHasA = false,
            systemHasAaaa = false,
            domesticHasAaaa = false,
            globalHasAaaa = false,
            fakeIpDetected = false,
            error = it.message ?: it.javaClass.simpleName
        )
    }
    val stun = if (full && !env.hasVpn) runCatching { stunBindingProbe() }.getOrNull() else null
    val publicV4 = publicV4FromHttp ?: stun?.mappedIp?.takeIf { isUsableIpv4(it) }
    // V1.1.15：延迟从完整 NAT/STUN 诊断中拆出。
    // 首页网络信息轻量刷新 full=false 时也会跑一次轻量延迟检测，避免长期显示“不可用”。
    val latencyMs = if (full) {
        runCatching { tcpConnectLatencyMs(targetHost, targetPort.coerceIn(1, 65535), 1500) }.getOrNull()
            ?: lightweightLatencyMs(targetHost, targetPort.coerceIn(1, 65535), 1200)
    } else {
        lightweightLatencyMs(targetHost, targetPort.coerceIn(1, 65535), 1200)
    }
    val tcpPort = if (full) runCatching { detectTcpLocalPort(targetHost, targetPort.coerceIn(1, 65535), 1500) }.getOrNull() else null

    val priority = when {
        env.hasVpn && !hasPublicV6 -> "代理路径"
        hasPublicV6 -> "IPv6"
        publicV4 != null -> "IPv4"
        else -> "未知"
    }

    val ipv6Status = when {
        env.hasVpn && !dns.systemHasAaaa && dns.backupHasAaaa -> "代理/DNS可能过滤"
        hasPublicV6 && dns.systemHasAaaa -> "公网可用"
        hasPublicV6 -> "公网地址"
        dns.systemHasAaaa || dns.backupHasAaaa -> "有AAAA待验证"
        else -> "未发现"
    }

    val isDirectPublicV4 = publicV4 != null && localIpv4 != null && !isPrivateIpv4(localIpv4) && localIpv4 == publicV4
    val stunSuccess = stun != null
    val multiStun = stun?.successCount ?: 0
    val stunTotal = stun?.totalCount ?: 0

    val strongSymmetric = stunSuccess && !stun!!.portStable && multiStun >= 3
    val weakPortChange = stunSuccess && !stun!!.portStable && multiStun < 3
    val strictNat1 = stunSuccess && stun!!.portStable && stun.ipStable && stun.roundStable && stun.successCount == stun.totalCount && stun.totalCount >= 6 && stun.roundCount >= 2
    val stableMapping = stunSuccess && stun!!.portStable && stun.ipStable && multiStun >= 3
    val rfc5780Verified = stunSuccess && stun!!.filteringVerified
    val rfc5780EndpointIndependentMapping = rfc5780Verified && stun.rfc5780MappingBehavior == "端口保持"
    val rfc5780EndpointIndependentFiltering = rfc5780Verified && stun.rfc5780FilteringBehavior == "开放"
    val rfc5780AddressFiltering = rfc5780Verified && stun.rfc5780FilteringBehavior == "地址受限"
    val rfc5780PortFiltering = rfc5780Verified && stun.rfc5780FilteringBehavior == "端口受限"

    val natType = when {
        env.hasVpn -> "代理环境"
        isDirectPublicV4 -> "NAT1 / 开放"
        rfc5780EndpointIndependentMapping && rfc5780EndpointIndependentFiltering -> "NAT1 / 全锥形"
        rfc5780EndpointIndependentMapping && rfc5780AddressFiltering -> "NAT2 / 地址受限型"
        rfc5780EndpointIndependentMapping && rfc5780PortFiltering -> "NAT3 / 端口受限型"
        rfc5780Verified && !rfc5780EndpointIndependentMapping -> "NAT4 / 对称型"
        strictNat1 -> "NAT1 / 全锥形"
        strongSymmetric -> "NAT4 / 对称型"
        stableMapping -> "NAT3 / 端口保持型"
        stunSuccess -> "NAT3 / 受限型"
        publicV4 != null && full -> "NAT类型待确认"
        publicV4 == null && full -> "UDP受限 / 无法判断"
        else -> "待检测"
    }

    val mapping = when {
        env.hasVpn -> "可能来自代理出口"
        isDirectPublicV4 -> "公网直连"
        stun == null && full -> "未知"
        stun == null -> "待检测"
        rfc5780Verified && stun.rfc5780MappingBehavior.isNotBlank() -> stun.rfc5780MappingBehavior
        strongSymmetric -> "端口变化"
        weakPortChange -> "端口变化待确认"
        else -> "端口保持"
    }

    val filtering = when {
        env.hasVpn -> "无法准确判断"
        isDirectPublicV4 -> "开放型"
        rfc5780Verified && stun.rfc5780FilteringBehavior.isNotBlank() -> stun.rfc5780FilteringBehavior
        strictNat1 || stableMapping -> "未验证"
        strongSymmetric -> "未验证"
        stun != null -> "未完成检测"
        publicV4 != null && full -> "待确认"
        stun == null && full -> "UDP回包失败"
        else -> "待检测"
    }

    val confidence = when {
        env.hasVpn -> "低"
        !full -> "低"
        rfc5780EndpointIndependentMapping && rfc5780EndpointIndependentFiltering -> "高"
        rfc5780Verified -> "中高"
        strictNat1 -> "高"
        strongSymmetric && stun!!.ipStable && stun.roundStable && multiStun >= 5 -> "高"
        strongSymmetric && stun!!.ipStable && multiStun >= 4 -> "中高"
        stun != null && stableMapping && multiStun >= 5 && stun.roundStable -> "中高"
        stun != null && multiStun >= 4 && stun.roundStable -> "中"
        stun != null && multiStun >= 3 -> "中"
        stun != null -> "低"
        publicV4 != null -> "低"
        else -> "低"
    }

    val dnsStatus = when {
        dns.fakeIpDetected -> "疑似Fake-IP"
        dns.systemHasAaaa -> "系统AAAA正常"
        dns.domesticHasAaaa -> "国内备用AAAA可用"
        dns.globalHasAaaa -> "国外备用AAAA可用"
        dns.error != null -> "DNS异常"
        else -> "无AAAA"
    }

    val stunRoundText = stun?.let { if (it.roundCount >= 2) "复测${it.roundCount}轮${if (it.roundStable) "稳定" else "有波动"}。" else "" } ?: ""
    val stunBackupText = stun?.let { if (it.usedBackup) "已启用备用节点。" else "" } ?: ""
    val rfc5780Text = stun?.let { if (it.filteringVerified) "RFC5780节点 ${it.rfc5780Server} 已验证回包限制。" else "" } ?: ""
    val stunNodeText = if (full && stunTotal > 0) "STUN节点 ${multiStun}/${stunTotal} 成功。${stunRoundText}${stunBackupText}${rfc5780Text}" else ""
    val diagnosis = when {
        env.hasVpn -> "检测到VPN/代理，NAT、出口和IPv6结果仅供参考。"
        dns.fakeIpDetected -> "检测到Fake-IP，DNS可能被代理工具接管。"
        !dns.systemHasAaaa && dns.domesticHasAaaa -> "系统DNS未返回IPv6，国内备用DNS可解析，可能被AdGuard/代理/路由器策略过滤。"
        !dns.systemHasAaaa && dns.globalHasAaaa -> "系统和国内备用DNS未返回IPv6，国外备用DNS可解析，可能存在地区DNS差异或本地DNS策略影响。"
        rfc5780Verified -> "${stunNodeText}检测方式：${stun!!.detectionMethod}，出网端口：${stun.rfc5780MappingBehavior}，回包限制：${stun.rfc5780FilteringBehavior}。"
        natType.startsWith("NAT4 / 对称") -> "${stunNodeText}多个可用STUN目标返回的外部端口不一致，当前按对称型/NAT4理解，P2P/游戏联机可能受影响。"
        natType.startsWith("NAT3 / 端口保持") -> "${stunNodeText}多节点端口保持，但未满足6/6节点与2轮稳定条件，当前按端口保持型受限网络显示；完整回包限制需RFC5780/自建节点验证。"
        natType.startsWith("NAT3 / 受限") && weakPortChange -> "${stunNodeText}UDP基础探测可用，但可用节点不足以确认对称型，当前按受限型理解，建议换网络或再次刷新复测。"
        natType.startsWith("NAT3 / 受限") -> "${stunNodeText}UDP基础探测可用，回包限制未完成RFC5780验证；完整回包限制需RFC5780/自建节点验证。"
        natType.startsWith("NAT类型待确认") -> "公网IPv4可用，但STUN基础探测未成功，NAT类型暂按待确认处理。"
        natType.startsWith("UDP") -> "多个STUN基础请求均失败，当前仅能判断为UDP受限/无法判断，可能是UDP被防火墙、代理或运营商限制。"
        natType.startsWith("NAT2") -> "UDP映射较稳定，普通联机能力中等。"
        natType.startsWith("NAT1") -> if (rfc5780Verified) "${stunNodeText}RFC5780已完成端口保持和回包验证，当前判定为 NAT1 / 全锥形。" else "${stunNodeText}6/6节点与2轮复测均保持同一公网端口，按兼容口径判定为 NAT1；完整回包限制需 RFC5780 / 自建节点验证。"
        else -> "网络信息已更新。"
    }

    val carrierText = displayCarrierFromEnv(env, publicV4 ?: publicIpResult.ipv4, publicIpResult.ipv6, full)

    return NetworkProbeInfo(
        localIp = localIpv4 ?: localIpv6 ?: "不可用",
        carrier = carrierText,
        natType = natType,
        latencyText = latencyMs?.let { "${it}ms" } ?: "不可用",
        portText = stun?.mappedPort?.toString() ?: tcpPort?.toString() ?: "不可用",
        priority = priority,
        mappingBehavior = mapping,
        filterBehavior = filtering,
        ipv6Status = ipv6Status,
        dnsStatus = dnsStatus,
        confidence = confidence,
        diagnosis = diagnosis,
        proxyNotice = if (env.hasVpn) "VPN/代理结果仅供参考" else "",
        refreshMode = if (full) "已检测" else "待检测"
    )
}

private fun String.isUsableIpText(): Boolean {
    val v = trim()
    return v.isNotBlank() && v != "不可用" && v != "检测中" && v != "未知"
}

private fun isUsableIpv4(value: String): Boolean {
    val v = value.trim()
    return v.isUsableIpText() && v.count { it == '.' } == 3
}

private fun localIpv4Addresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toListCompat().flatMap { it.inetAddresses.toListCompat() }
        .filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress }
        .mapNotNull { it.hostAddress }
        .distinct()
}.getOrDefault(emptyList())

private fun localIpv6Addresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toListCompat().flatMap { it.inetAddresses.toListCompat() }
        .filterIsInstance<Inet6Address>()
        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        .mapNotNull { it.hostAddress?.substringBefore('%') }
        .distinct()
}.getOrDefault(emptyList())

private fun <T> java.util.Enumeration<T>.toListCompat(): List<T> {
    val list = mutableListOf<T>()
    while (hasMoreElements()) list += nextElement()
    return list
}

private fun isPrivateIpv4(ip: String): Boolean {
    val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
    if (parts.size != 4) return true
    val a = parts[0]
    val b = parts[1]
    return a == 10 ||
        (a == 172 && b in 16..31) ||
        (a == 192 && b == 168) ||
        a == 127 ||
        (a == 169 && b == 254) ||
        (a == 100 && b in 64..127)
}

private fun tcpConnectLatencyMs(host: String, port: Int, timeoutMs: Int): Int {
    val start = System.currentTimeMillis()
    Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), timeoutMs)
    }
    return (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
}

private fun lightweightLatencyMs(targetHost: String, targetPort: Int, timeoutMs: Int): Int? {
    // 优先用国内稳定 IP 做 ICMP 轻量延迟；失败再用当前目标 TCP 连接延迟兜底。
    val icmpTargets = listOf("223.5.5.5", "119.29.29.29")
    for (target in icmpTargets) {
        val latency = syncPingLatencyMs(target, timeoutMs)
        if (latency != null) return latency
    }
    return runCatching { tcpConnectLatencyMs(targetHost, targetPort.coerceIn(1, 65535), timeoutMs) }.getOrNull()
}

private fun syncPingLatencyMs(address: String, timeoutMs: Int): Int? {
    val timeoutSec = ((timeoutMs.coerceIn(300, 10_000) + 999) / 1000).coerceAtLeast(1)
    return runCatching {
        val startedAt = System.nanoTime()
        val process = ProcessBuilder("ping", "-c", "1", "-W", timeoutSec.toString(), address)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor((timeoutMs + 900).toLong(), TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching null
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        Regex("""time[=<]([0-9.]+)\s*ms""")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.roundToInt()
            ?.coerceAtLeast(1)
            ?: if (process.exitValue() == 0) ((System.nanoTime() - startedAt) / 1_000_000L).toInt().coerceAtLeast(1) else null
    }.getOrNull()
}

private fun detectTcpLocalPort(host: String, port: Int, timeoutMs: Int): Int {
    Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        return socket.localPort
    }
}

private fun stunBindingProbe(): StunProbeResult {
    // 方案A：公共 STUN 增强版。
    // 关键点：同一个 UDP socket 复用本地端口、主 6 备 2、标准 2 轮复测、够票提前出结果、备用只补票。
    val primary = listOf(
        StunEndpoint("stun.cloudflare.com", 3478),
        StunEndpoint("stun.miwifi.com", 3478),
        StunEndpoint("stun.voipstunt.com", 3478),
        StunEndpoint("stun.voipbuster.com", 3478),
        StunEndpoint("stun.internetcalls.com", 3478),
        StunEndpoint("stun.voip.aebc.com", 3478)
    )
    val backup = listOf(
        StunEndpoint("stun.fitauto.ru", 3478),
        StunEndpoint("stun.qq.com", 3478)
    )

    DatagramSocket().use { socket ->
        val roundResults = mutableListOf<List<StunProbeResult>>()
        var usedBackup = false
        var totalCount = primary.size

        val firstPrimary = probeStunRoundSharedSocket(socket, primary, overallTimeoutMs = 1200)
        var firstRound = firstPrimary
        if (firstPrimary.map { it.sourceHost }.toSet().size < 4) {
            usedBackup = true
            totalCount = primary.size + backup.size
            firstRound = firstPrimary + probeStunRoundSharedSocket(socket, backup, overallTimeoutMs = 850)
        }
        roundResults += firstRound

        // 第二轮只复测首轮成功的主节点；如果主节点不足，再带上备用节点。
        // 这样既能验证端口稳定性，又不会让失败节点拖慢解析。
        val firstSuccessHosts = firstRound.map { it.sourceHost }.toSet()
        val secondTargets = (primary + if (usedBackup) backup else emptyList())
            .filter { it.key in firstSuccessHosts }
            .ifEmpty { primary }
        val secondRound = probeStunRoundSharedSocket(socket, secondTargets, overallTimeoutMs = if (usedBackup) 1200 else 1000)
        if (secondRound.isNotEmpty()) roundResults += secondRound

        val allResults = roundResults.flatten()
        if (allResults.isEmpty()) error("STUN基础探测无响应")

        val uniqueSuccess = allResults.map { it.sourceHost }.filter { it.isNotBlank() }.toSet().size
        val stableRoundCount = roundResults.count { round ->
            round.isNotEmpty() && round.map { it.mappedIp }.toSet().size <= 1 && round.map { it.mappedPort }.toSet().size <= 1
        }
        val representative = allResults.first()
        val rfc5780 = rfc5780ProbeFast()
        return representative.copy(
            successCount = uniqueSuccess,
            totalCount = totalCount,
            mappedPorts = allResults.map { it.mappedPort }.toSet(),
            mappedIps = allResults.map { it.mappedIp }.toSet(),
            roundCount = roundResults.size,
            stableRoundCount = stableRoundCount,
            usedBackup = usedBackup,
            detectionMethod = if (rfc5780 != null) "RFC5780+RFC8489" else "RFC8489多节点",
            filteringVerified = rfc5780 != null,
            rfc5780Server = rfc5780?.serverKey.orEmpty(),
            rfc5780MappingBehavior = rfc5780?.mappingBehavior.orEmpty(),
            rfc5780FilteringBehavior = rfc5780?.filteringBehavior.orEmpty()
        )
    }
}

private fun probeStunRoundSharedSocket(
    socket: DatagramSocket,
    endpoints: List<StunEndpoint>,
    overallTimeoutMs: Long
): List<StunProbeResult> {
    if (endpoints.isEmpty()) return emptyList()
    val pending = linkedMapOf<String, PendingStunRequest>()
    endpoints.forEach { endpoint ->
        val address = runCatching { resolveFirstIpv4(endpoint.host) }.getOrNull() ?: return@forEach
        val request = buildStunBindingRequest()
        pending[request.transactionId.stunKey()] = PendingStunRequest(endpoint, address, request)
    }
    if (pending.isEmpty()) return emptyList()

    pending.values.forEach { sendStunPacket(socket, it) }

    val results = mutableListOf<StunProbeResult>()
    val completed = mutableSetOf<String>()
    val start = System.currentTimeMillis()
    val deadline = start + overallTimeoutMs.coerceIn(650L, 1800L)
    val resendAt = start + (overallTimeoutMs.coerceIn(650L, 1800L) / 2)
    var resent = false
    val buffer = ByteArray(768)

    while (completed.size < pending.size && System.currentTimeMillis() < deadline) {
        val now = System.currentTimeMillis()
        if (!resent && now >= resendAt) {
            pending.filterKeys { it !in completed }.values.forEach { sendStunPacket(socket, it) }
            resent = true
        }
        val left = (deadline - now).coerceAtLeast(1L).coerceAtMost(220L).toInt()
        socket.soTimeout = left
        val packet = DatagramPacket(buffer, buffer.size)
        val received = runCatching {
            socket.receive(packet)
            true
        }.getOrDefault(false)
        if (!received) continue
        val txKey = stunTransactionKeyFromPacket(packet.data, packet.length) ?: continue
        val pendingRequest = pending[txKey] ?: continue
        if (txKey in completed) continue
        val parsed = parseStunMappedAddress(
            data = packet.data,
            length = packet.length,
            localPort = socket.localPort,
            transactionId = pendingRequest.request.transactionId
        ) ?: continue
        results += parsed.copy(sourceHost = pendingRequest.endpoint.key)
        completed += txKey
    }
    return results
}

private fun sendStunPacket(socket: DatagramSocket, pending: PendingStunRequest) {
    runCatching {
        socket.send(
            DatagramPacket(
                pending.request.bytes,
                pending.request.bytes.size,
                pending.address,
                pending.endpoint.port
            )
        )
    }
}

private fun ByteArray.stunKey(): String = joinToString(separator = "") { b -> "%02x".format(b.toInt() and 0xFF) }

private fun stunTransactionKeyFromPacket(data: ByteArray, length: Int): String? {
    if (length < 20) return null
    return data.copyOfRange(8, 20).stunKey()
}

private fun resolveFirstIpv4(host: String): InetAddress {
    return NetworkDnsResolver.resolveAddressesBlocking(
        host = host,
        includeIpv4 = true,
        includeIpv6 = false
    ).firstOrNull { it is Inet4Address } ?: error("STUN服务器无IPv4地址")
}

private fun buildStunBindingRequest(changeIp: Boolean = false, changePort: Boolean = false): StunRequest {
    val tx = ByteArray(12)
    synchronized(stunRandom) { stunRandom.nextBytes(tx) }
    val hasChangeRequest = changeIp || changePort
    val attrLen = if (hasChangeRequest) 8 else 0
    val req = ByteArray(20 + attrLen)
    req[1] = 0x01.toByte()
    req[2] = ((attrLen ushr 8) and 0xFF).toByte()
    req[3] = (attrLen and 0xFF).toByte()
    req[4] = 0x21.toByte()
    req[5] = 0x12.toByte()
    req[6] = 0xA4.toByte()
    req[7] = 0x42.toByte()
    System.arraycopy(tx, 0, req, 8, 12)
    if (hasChangeRequest) {
        // RFC3489/RFC5780 CHANGE-REQUEST: bit 2 = change IP, bit 1 = change port.
        req[21] = 0x03.toByte()
        req[23] = 0x04.toByte()
        var flags = 0
        if (changeIp) flags = flags or 0x04
        if (changePort) flags = flags or 0x02
        req[27] = flags.toByte()
    }
    return StunRequest(req, tx)
}

private fun parseStunMappedAddress(data: ByteArray, length: Int, localPort: Int, transactionId: ByteArray): StunProbeResult? {
    val response = parseStunResponse(data, length, transactionId) ?: return null
    return StunProbeResult(response.mappedIp, response.mappedPort, localPort)
}

private fun parseStunResponse(data: ByteArray, length: Int, transactionId: ByteArray): StunRawResponse? {
    if (length < 20) return null
    val messageType = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
    if (messageType != 0x0101) return null
    if (data[4] != 0x21.toByte() || data[5] != 0x12.toByte() || data[6] != 0xA4.toByte() || data[7] != 0x42.toByte()) return null
    for (i in 0 until 12) {
        if (data[8 + i] != transactionId[i]) return null
    }
    var mapped: Pair<String, Int>? = null
    var other: InetSocketAddress? = null
    var origin: InetSocketAddress? = null
    var pos = 20
    while (pos + 4 <= length) {
        val type = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        val attrLen = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
        val value = pos + 4
        if (value + attrLen <= length && attrLen >= 8) {
            val pair = parseStunAddressAttribute(data, value, attrLen, type)
            when (type) {
                0x0020, 0x0001 -> if (pair != null && mapped == null) mapped = pair
                // RFC5780 OTHER-ADDRESS. Some legacy RFC3489 servers still return
                // CHANGED-ADDRESS (0x0005); treat it as the alternate address so
                // stun.miwifi.com / stun.voip.aebc.com style servers can run the
                // same mapping/filtering compatibility flow instead of being
                // rejected as unsupported.
                0x802C, 0x0005 -> if (pair != null && other == null) other = InetSocketAddress(pair.first, pair.second)
                0x802B -> if (pair != null) origin = InetSocketAddress(pair.first, pair.second)
            }
        }
        pos += 4 + ((attrLen + 3) / 4) * 4
    }
    val m = mapped ?: return null
    return StunRawResponse(m.first, m.second, other, origin)
}

private fun parseStunAddressAttribute(data: ByteArray, value: Int, attrLen: Int, type: Int): Pair<String, Int>? {
    if (attrLen < 8) return null
    val family = data[value + 1].toInt() and 0xFF
    if (family != 0x01) return null
    val rawPort = ((data[value + 2].toInt() and 0xFF) shl 8) or (data[value + 3].toInt() and 0xFF)
    val port = if (type == 0x0020) rawPort xor 0x2112 else rawPort
    val ipBytes = ByteArray(4)
    val magic = byteArrayOf(0x21.toByte(), 0x12.toByte(), 0xA4.toByte(), 0x42.toByte())
    for (i in 0..3) {
        val b = data[value + 4 + i].toInt()
        ipBytes[i] = if (type == 0x0020) ((b xor magic[i].toInt()) and 0xFF).toByte() else (b and 0xFF).toByte()
    }
    val ip = InetAddress.getByAddress(ipBytes).hostAddress ?: return null
    return ip to port
}

private fun sendAndReceiveStun(socket: DatagramSocket, address: InetSocketAddress, request: StunRequest, timeoutMs: Int): StunRawResponse? {
    return runCatching {
        socket.soTimeout = timeoutMs.coerceIn(500, 1800)
        val packet = DatagramPacket(request.bytes, request.bytes.size, address.address, address.port)
        socket.send(packet)
        val buffer = ByteArray(1024)
        val responsePacket = DatagramPacket(buffer, buffer.size)
        socket.receive(responsePacket)
        parseStunResponse(responsePacket.data, responsePacket.length, request.transactionId)?.copy(
            remoteAddress = InetSocketAddress(responsePacket.address, responsePacket.port)
        )
    }.getOrNull()
}
private fun sameSocketAddress(a: InetSocketAddress?, b: InetSocketAddress?): Boolean {
    if (a == null || b == null) return false
    return a.port == b.port && a.address?.hostAddress == b.address?.hostAddress
}

private fun sameIp(a: InetSocketAddress?, b: InetSocketAddress?): Boolean {
    if (a == null || b == null) return false
    return a.address?.hostAddress == b.address?.hostAddress
}

private fun isStrictAlternateIpPort(main: InetSocketAddress, other: InetSocketAddress?): Boolean {
    if (other == null) return false
    val mainIp = main.address?.hostAddress ?: return false
    val otherIp = other.address?.hostAddress ?: return false
    // NAT1 / 开放过滤必须由“不同 IP + 不同端口”的回包证明。
    // 只有同 IP 不同端口时，最多只能参与 Change-Port 判断，不能判全锥形。
    return mainIp != otherIp && main.port != other.port
}

private fun isValidChangeIpPortResponse(
    response: StunRawResponse?,
    main: InetSocketAddress,
    other: InetSocketAddress?
): Boolean {
    if (!isStrictAlternateIpPort(main, other)) return false
    return response != null && sameSocketAddress(response.remoteAddress, other)
}

private fun isValidChangePortResponse(response: StunRawResponse?, main: InetSocketAddress): Boolean {
    val remote = response?.remoteAddress ?: return false
    return remote.address?.hostAddress == main.address?.hostAddress && remote.port != main.port
}

private data class FilteringDetection(
    val behavior: String,
    val detail: String,
    val strictFullCone: Boolean = false
)

private data class MappingDetection(
    val behavior: String,
    val detail: String,
    val base: StunRawResponse,
    val other: InetSocketAddress?
)

private fun detectRfc5780FilteringStrict(endpoint: StunEndpoint, timeoutMs: Int = 1200, progress: (String) -> Unit = {}): FilteringDetection {
    val mainAddress = InetSocketAddress(resolveFirstIpv4(endpoint.host), endpoint.port)

    progress("RFC5780 Filtering Test II · Change IP + Port")
    // Filtering Test II: Change IP + Change Port.
    // Use a fresh socket and do NOT contact OTHER/CHANGED before this request.
    // Full cone / open filtering is accepted only when the response comes from a real
    // alternate IP AND alternate port, and it succeeds twice on fresh local ports.
    var openSuccess = 0
    var lastOther: InetSocketAddress? = null
    var lastRemote: InetSocketAddress? = null
    repeat(2) {
        DatagramSocket().use { socket ->
            val base = sendAndReceiveStun(socket, mainAddress, buildStunBindingRequest(), timeoutMs)
            val other = base?.otherAddress
            lastOther = other
            if (base != null && isStrictAlternateIpPort(mainAddress, other)) {
                val changedIpPort = sendAndReceiveStun(
                    socket,
                    mainAddress,
                    buildStunBindingRequest(changeIp = true, changePort = true),
                    timeoutMs
                )
                lastRemote = changedIpPort?.remoteAddress
                if (isValidChangeIpPortResponse(changedIpPort, mainAddress, other)) {
                    openSuccess++
                }
            }
        }
    }
    if (openSuccess >= 2) {
        return FilteringDetection(
            behavior = "开放",
            detail = "Filtering Test II 连续2次收到备用IP+备用端口回包。",
            strictFullCone = true
        )
    }

    progress("RFC5780 Filtering Test III · Change Port")
    // Filtering Test III: Change Port only. Fresh socket again to avoid permissions
    // created by any previous probe. Success here means address-restricted; timeout
    // means address-and-port restricted (port restricted / NAT3).
    DatagramSocket().use { socket ->
        val base = sendAndReceiveStun(socket, mainAddress, buildStunBindingRequest(), timeoutMs)
        val other = base?.otherAddress
        val changedPort = sendAndReceiveStun(
            socket,
            mainAddress,
            buildStunBindingRequest(changePort = true),
            timeoutMs
        )
        if (isValidChangePortResponse(changedPort, mainAddress)) {
            return FilteringDetection(
                behavior = "地址受限",
                detail = "Filtering Test III 收到同IP不同端口回包；Test II 未连续通过。"
            )
        }
        val reason = when {
            base == null -> "基础 Binding 无响应。"
            other == null -> "服务器未返回 OTHER-ADDRESS/CHANGED-ADDRESS。"
            !isStrictAlternateIpPort(mainAddress, other) -> "备用地址不是不同IP+不同端口。"
            else -> "Test II/Test III 均未收到有效改变来源回包。"
        }
        return FilteringDetection(
            behavior = "端口受限",
            detail = "Filtering Test III 超时或来源不匹配；$reason"
        )
    }
}

private fun detectRfc5780MappingStrict(endpoint: StunEndpoint, timeoutMs: Int = 1200, progress: (String) -> Unit = {}): MappingDetection {
    val mainAddress = InetSocketAddress(resolveFirstIpv4(endpoint.host), endpoint.port)
    progress("RFC5780 Mapping Test I · 获取公网映射")
    DatagramSocket().use { socket ->
        val base = sendAndReceiveStun(socket, mainAddress, buildStunBindingRequest(), timeoutMs) ?: error("Binding无响应")
        val other = base.otherAddress
        if (other == null) {
            return MappingDetection("公网映射", "服务器未返回 OTHER-ADDRESS/CHANGED-ADDRESS。", base, null)
        }
        val altSamePort = other.address?.let { InetSocketAddress(it, mainAddress.port) } ?: other
        progress("RFC5780 Mapping Test II · 对比备用IP")
        val map2 = runCatching { sendAndReceiveStun(socket, altSamePort, buildStunBindingRequest(), timeoutMs) }.getOrNull()
        if (map2 != null && map2.mappedIp == base.mappedIp && map2.mappedPort == base.mappedPort) {
            return MappingDetection("端口保持", "Mapping Test II 映射一致。", base, other)
        }
        progress("RFC5780 Mapping Test III · 对比备用端口")
        val map3 = runCatching { sendAndReceiveStun(socket, other, buildStunBindingRequest(), timeoutMs) }.getOrNull()
        val behavior = when {
            map2 == null && map3 == null -> "公网映射"
            map2 != null && map3 != null && map2.mappedIp == map3.mappedIp && map2.mappedPort == map3.mappedPort -> "端口变化"
            else -> "端口变化"
        }
        return MappingDetection(behavior, "Mapping Test 映射不一致或备用地址无响应。", base, other)
    }
}

private fun rfc5780ProbeFast(): Rfc5780ProbeResult? {
    val candidates = listOf(
        StunEndpoint("stunserver2025.stunprotocol.org", 3478),
        StunEndpoint("stun.voipgate.com", 3478),
        StunEndpoint("stun.ekiga.net", 3478),
        StunEndpoint("stun.callwithus.com", 3478),
        StunEndpoint("stun.counterpath.net", 3478),
        StunEndpoint("stun.sipgate.net", 3478),
        StunEndpoint("stun.vline.com", 3478)
    )
    val deadline = System.currentTimeMillis() + 2600L
    for (endpoint in candidates) {
        if (System.currentTimeMillis() >= deadline) break
        val result = runCatching {
            val filtering = detectRfc5780FilteringStrict(endpoint, timeoutMs = 900)
            val mapping = detectRfc5780MappingStrict(endpoint, timeoutMs = 900)
            Rfc5780ProbeResult(
                serverKey = endpoint.key,
                mappedIp = mapping.base.mappedIp,
                mappedPort = mapping.base.mappedPort,
                mappingBehavior = mapping.behavior,
                filteringBehavior = filtering.behavior
            )
        }.getOrNull()
        if (result != null) return result
    }
    return null
}

private data class Rfc3489TestResult(
    val base: StunRawResponse,
    val changedAddress: InetSocketAddress?,
    val mappingBehavior: String,
    val filteringBehavior: String,
    val detail: String
)

private fun detectRfc3489Classic(endpoint: StunEndpoint, timeoutMs: Int = 1200, progress: (String) -> Unit = {}): Rfc3489TestResult {
    val mainAddress = InetSocketAddress(resolveFirstIpv4(endpoint.host), endpoint.port)
    DatagramSocket().use { socket ->
        progress("RFC3489 Test I · 获取公网映射")
        // Test I: ordinary binding.
        val base = sendAndReceiveStun(socket, mainAddress, buildStunBindingRequest(), timeoutMs) ?: error("Test I 无响应")
        val changedAddress = base.otherAddress

        progress("RFC3489 Test II · Change IP + Port")
        // Test II: Change IP + Change Port. Do this before contacting CHANGED-ADDRESS directly.
        val testII = sendAndReceiveStun(socket, mainAddress, buildStunBindingRequest(changeIp = true, changePort = true), timeoutMs)
        val testIIOk = isValidChangeIpPortResponse(testII, mainAddress, changedAddress)

        var mappingBehavior = "端口保持"
        var mapDetail = ""
        if (!testIIOk && changedAddress != null) {
            progress("RFC3489 Test I' · 检查映射变化")
            // Test I': ordinary binding to CHANGED-ADDRESS, used only after Test II failed.
            val testIPrime = sendAndReceiveStun(socket, changedAddress, buildStunBindingRequest(), timeoutMs)
            if (testIPrime != null && (testIPrime.mappedIp != base.mappedIp || testIPrime.mappedPort != base.mappedPort)) {
                mappingBehavior = "端口变化"
                mapDetail = "Test I' 映射变化。"
            } else {
                mapDetail = "Test I' 映射保持。"
            }
        } else if (testIIOk) {
            mapDetail = "Test II 有效回包。"
        } else {
            mapDetail = "服务器未返回 CHANGED-ADDRESS。"
        }

        val filteringBehavior = when {
            mappingBehavior == "端口变化" -> "未验证"
            testIIOk -> "开放"
            else -> {
                progress("RFC3489 Test III · Change Port")
                // Test III: Change Port only, only meaningful after Test II failed and mapping did not change.
                val testIII = sendAndReceiveStun(socket, mainAddress, buildStunBindingRequest(changePort = true), timeoutMs)
                if (isValidChangePortResponse(testIII, mainAddress)) "地址受限" else "端口受限"
            }
        }
        val detail = "RFC3489 Test I/II/I'/III 完成；$mapDetail"
        return Rfc3489TestResult(base, changedAddress, mappingBehavior, filteringBehavior, detail)
    }
}

private fun normalizeNatServerList(raw: String, fallback: String): List<String> {
    val items = raw.split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { input ->
            val endpoint = parseStunEndpoint(input)
            endpoint?.key ?: input
        }
        .distinct()
        .take(8)
    return items.ifEmpty { listOf(fallback) }
}

private fun encodeNatServerList(servers: List<String>, fallback: String): String {
    return servers.map { it.trim() }
        .filter { it.isNotBlank() }
        .map { input -> parseStunEndpoint(input)?.key ?: input }
        .distinct()
        .take(8)
        .ifEmpty { listOf(fallback) }
        .joinToString("\n")
}

private fun parseStunEndpoint(raw: String, defaultPort: Int = 3478): StunEndpoint? {
    val clean = raw.trim().removePrefix("stun:").removePrefix("turn:")
    if (clean.isBlank()) return null
    val host: String
    val port: Int
    if (clean.startsWith("[") && clean.contains("]")) {
        host = clean.substringAfter("[").substringBefore("]")
        port = clean.substringAfter("]:", defaultPort.toString()).toIntOrNull() ?: defaultPort
    } else {
        val parts = clean.split(":")
        host = parts.firstOrNull().orEmpty()
        port = parts.getOrNull(1)?.toIntOrNull() ?: defaultPort
    }
    if (host.isBlank() || port !in 1..65535) return null
    return StunEndpoint(host, port)
}

private fun manualNatProbe(mode: ManualNatMode, servers: List<String>, progress: (String) -> Unit = {}): ManualNatResult {
    val cleanServers = servers.mapNotNull { parseStunEndpoint(it) }.ifEmpty {
        listOf(if (mode == ManualNatMode.RFC5780) StunEndpoint("stunserver2025.stunprotocol.org", 3478) else StunEndpoint("stun.voip.aebc.com", 3478))
    }
    var lastError = "STUN服务器无响应"
    cleanServers.forEachIndexed { index, endpoint ->
        progress("服务器 ${index + 1}/${cleanServers.size} · ${endpoint.key}")
        val result = runCatching {
            when (mode) {
                ManualNatMode.RFC5780 -> manualRfc5780Probe(endpoint, progress)
                ManualNatMode.RFC3489 -> manualRfc3489Probe(endpoint, progress)
            }
        }.getOrElse { error ->
            lastError = error.message ?: "检测失败"
            progress("服务器 ${index + 1}/${cleanServers.size} 失败：$lastError")
            null
        }
        if (result != null) return result
        if (index < cleanServers.lastIndex) progress("正在尝试服务器 ${index + 2}/${cleanServers.size}")
    }
    return ManualNatResult(
        success = false,
        natType = "无法判断",
        mappingBehavior = "未知",
        filteringBehavior = "未知",
        localAddress = "不可用",
        publicAddress = "不可用",
        method = mode.label,
        server = cleanServers.firstOrNull()?.key.orEmpty(),
        message = lastError
    )
}

private fun compatibleNatTypeFromRfc5780(mappingBehavior: String, filteringBehavior: String): String = when {
    mappingBehavior == "端口保持" && filteringBehavior == "开放" -> "NAT1 / 全锥形"
    mappingBehavior == "端口保持" && filteringBehavior == "地址受限" -> "NAT2 / 地址受限型"
    mappingBehavior == "端口保持" && filteringBehavior == "端口受限" -> "NAT3 / 端口受限型"
    mappingBehavior == "端口变化" -> "NAT4 / 对称型"
    else -> "NAT类型待确认"
}

private fun manualRfc5780Probe(endpoint: StunEndpoint, progress: (String) -> Unit = {}): ManualNatResult? {
    return runCatching {
        progress("RFC5780 Filtering · 测试回包限制")
        val filtering = detectRfc5780FilteringStrict(endpoint, timeoutMs = 1200, progress = progress)
        progress("RFC5780 Mapping · 测试出网端口")
        val mapping = detectRfc5780MappingStrict(endpoint, timeoutMs = 1200, progress = progress)
        if (mapping.other == null) {
            progress("RFC8489 Binding · 服务器不支持严格5780，降级基础结果")
            DatagramSocket().use { socket ->
                return@runCatching manualRfc8489Result(
                    socket = socket,
                    endpoint = endpoint,
                    base = mapping.base,
                    message = "服务器未返回 OTHER-ADDRESS/CHANGED-ADDRESS，已降级为基础 STUN Binding；过滤行为未验证。"
                )
            }
        }
        ManualNatResult(
            success = true,
            natType = compatibleNatTypeFromRfc5780(mapping.behavior, filtering.behavior),
            mappingBehavior = mapping.behavior,
            filteringBehavior = filtering.behavior,
            localAddress = localIpv4Addresses().firstOrNull()?.let { "$it:${mapping.base.mappedPort}" } ?: "本地端口:${mapping.base.mappedPort}",
            publicAddress = "${mapping.base.mappedIp}:${mapping.base.mappedPort}",
            method = "RFC5780",
            server = endpoint.key,
            message = "${filtering.detail}${mapping.detail} 过滤测试独立于映射测试执行，避免提前打洞。"
        )
    }.getOrElse { error("${endpoint.key} 检测失败：${it.message ?: "未知错误"}") }
}

private fun manualRfc8489Result(
    socket: DatagramSocket,
    endpoint: StunEndpoint,
    base: StunRawResponse,
    message: String
): ManualNatResult {
    val mainAddress = InetSocketAddress(resolveFirstIpv4(endpoint.host), endpoint.port)
    val again = sendAndReceiveStun(socket, mainAddress, buildStunBindingRequest(), 900)
    val mapping = if (again != null && again.mappedIp == base.mappedIp && again.mappedPort == base.mappedPort) "端口保持" else if (again != null) "端口变化" else "公网映射"
    return ManualNatResult(
        success = true,
        natType = "RFC8489 基础行为结果",
        mappingBehavior = mapping,
        filteringBehavior = "未验证",
        localAddress = localIpv4Addresses().firstOrNull()?.let { "$it:${socket.localPort}" } ?: "本地:${socket.localPort}",
        publicAddress = "${base.mappedIp}:${base.mappedPort}",
        method = "RFC8489",
        server = endpoint.key,
        message = message
    )
}

private fun manualRfc3489Probe(endpoint: StunEndpoint, progress: (String) -> Unit = {}): ManualNatResult? {
    return runCatching {
        val classic = detectRfc3489Classic(endpoint, timeoutMs = 1200, progress = progress)
        val natType = when {
            classic.mappingBehavior == "端口变化" -> "NAT4 / 对称型"
            classic.filteringBehavior == "开放" -> "NAT1 / 全锥形"
            classic.filteringBehavior == "地址受限" -> "NAT2 / 地址受限型"
            else -> "NAT3 / 端口受限型"
        }
        ManualNatResult(
            success = true,
            natType = natType,
            mappingBehavior = classic.mappingBehavior,
            filteringBehavior = classic.filteringBehavior,
            localAddress = localIpv4Addresses().firstOrNull()?.let { "$it:${classic.base.mappedPort}" } ?: "本地端口:${classic.base.mappedPort}",
            publicAddress = "${classic.base.mappedIp}:${classic.base.mappedPort}",
            method = "RFC3489",
            server = endpoint.key,
            message = classic.detail
        )
    }.getOrElse { error("${endpoint.key} 检测失败：${it.message ?: "未知错误"}") }
}

private fun dnsProbe(context: Context, host: String, network: Network?): DnsProbeResult {
    val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
    val systemResult = runCatching {
        NetworkDnsResolver.resolveAddressesBlocking(
            context = context,
            host = cleanHost,
            network = network,
            includeIpv4 = true,
            includeIpv6 = true
        )
    }
    val system = systemResult.getOrDefault(emptyList())
    val systemHasA = system.any { it is Inet4Address }
    val systemHasAaaa = system.any { it is Inet6Address }
    val fakeIp = system.filterIsInstance<Inet4Address>().any { isFakeIpAddress(it.hostAddress.orEmpty()) }

    val domesticHasAaaa = if (!systemHasAaaa) {
        listOf("223.5.5.5", "119.29.29.29").any { server ->
            runCatching { dnsQueryHasAaaa(cleanHost, server, network) }.getOrDefault(false)
        }
    } else {
        false
    }

    val globalHasAaaa = if (!systemHasAaaa && !domesticHasAaaa) {
        listOf("1.1.1.1", "8.8.8.8").any { server ->
            runCatching { dnsQueryHasAaaa(cleanHost, server, network) }.getOrDefault(false)
        }
    } else {
        false
    }

    return DnsProbeResult(
        systemHasA = systemHasA,
        systemHasAaaa = systemHasAaaa,
        domesticHasAaaa = domesticHasAaaa,
        globalHasAaaa = globalHasAaaa,
        fakeIpDetected = fakeIp,
        error = systemResult.exceptionOrNull()?.message
    )
}

private fun isFakeIpAddress(ip: String): Boolean {
    val p = ip.split('.').mapNotNull { it.toIntOrNull() }
    return p.size == 4 && p[0] == 198 && p[1] in 18..19
}

private fun dnsQueryHasAaaa(host: String, server: String, network: Network?): Boolean {
    val txId = (System.nanoTime().toInt() and 0xFFFF)
    val query = buildDnsQuery(host, txId, qType = 28)
    DatagramSocket().use { socket ->
        network?.bindSocket(socket)
        socket.soTimeout = 1200
        val dnsAddress = InetAddress.getByName(server)
        socket.send(DatagramPacket(query, query.size, dnsAddress, 53))
        val buf = ByteArray(1024)
        val packet = DatagramPacket(buf, buf.size)
        socket.receive(packet)
        return parseDnsHasAnswer(packet.data, packet.length, txId, qType = 28)
    }
}

private fun buildDnsQuery(host: String, txId: Int, qType: Int): ByteArray {
    val labels = host.trimEnd('.').split('.').filter { it.isNotBlank() }
    val size = 12 + labels.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 } + 1 + 4
    val out = ByteArray(size)
    out[0] = ((txId shr 8) and 0xFF).toByte()
    out[1] = (txId and 0xFF).toByte()
    out[2] = 0x01
    out[5] = 0x01
    var pos = 12
    labels.forEach { label ->
        val bytes = label.toByteArray(Charsets.UTF_8)
        out[pos++] = bytes.size.toByte()
        bytes.forEach { out[pos++] = it }
    }
    out[pos++] = 0
    out[pos++] = ((qType shr 8) and 0xFF).toByte()
    out[pos++] = (qType and 0xFF).toByte()
    out[pos++] = 0
    out[pos] = 1
    return out
}

private fun parseDnsHasAnswer(data: ByteArray, length: Int, txId: Int, qType: Int): Boolean {
    if (length < 12) return false
    val id = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
    if (id != txId) return false
    val qd = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
    val an = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
    var pos = 12
    repeat(qd) {
        pos = skipDnsName(data, length, pos)
        if (pos < 0 || pos + 4 > length) return false
        pos += 4
    }
    repeat(an) {
        pos = skipDnsName(data, length, pos)
        if (pos < 0 || pos + 10 > length) return false
        val type = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        val rdLen = ((data[pos + 8].toInt() and 0xFF) shl 8) or (data[pos + 9].toInt() and 0xFF)
        pos += 10
        if (pos + rdLen > length) return false
        if (type == qType && rdLen > 0) return true
        pos += rdLen
    }
    return false
}

private fun skipDnsName(data: ByteArray, length: Int, start: Int): Int {
    var pos = start
    var jumped = false
    var jumps = 0
    while (pos in 0 until length) {
        val len = data[pos].toInt() and 0xFF
        if (len == 0) return if (jumped) start + 2 else pos + 1
        if ((len and 0xC0) == 0xC0) {
            if (pos + 1 >= length) return -1
            if (++jumps > 8) return -1
            return pos + 2
        }
        pos += 1 + len
    }
    return -1
}

private fun currentNetworkSignature(context: Context): String {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivity?.activeNetwork ?: return "none"
    val caps = connectivity.getNetworkCapabilities(network)
    val transports = buildList {
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("wifi")
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("cellular")
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("vpn")
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("ethernet")
    }.joinToString("+").ifBlank { "unknown" }
    // 只检测真实网络切换：activeNetwork 或传输类型变化。
    // 不把 VALIDATED/INTERNET 放进去，避免运营商抖动导致误中断。
    return "${network}|$transports"
}


private fun looksLikeIpv4Literal(value: String): Boolean {
    val parts = value.split('.')
    return parts.size == 4 && parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
}

private fun looksLikeIpv6Literal(value: String): Boolean = value.contains(':')

private data class NormalizedNetworkTarget(
    val host: String,
    val port: Int? = null,
    val error: String? = null
)

private fun normalizeNetworkTargetInput(raw: String, defaultHost: String = "223.5.5.5"): NormalizedNetworkTarget {
    var value = raw.trim()
    if (value.isBlank()) return NormalizedNetworkTarget(defaultHost)
    if (value.any { it.isWhitespace() }) return NormalizedNetworkTarget(defaultHost, error = "地址格式不正确，请输入域名或 IP")

    var parsedPort: Int? = null

    if (value.contains("://")) {
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        val host = uri?.host?.trim()?.trim('[', ']')
        if (host.isNullOrBlank()) return NormalizedNetworkTarget(defaultHost, error = "地址格式不正确，请输入域名或 IP")
        parsedPort = uri.port.takeIf { it in 1..65535 }
        value = host
    } else {
        // 支持用户粘贴 example.com/path，测试目标只取 host 部分。
        value = value.substringBefore('/').substringBefore('?').substringBefore('#')
        if (value.startsWith("[") && value.contains("]")) {
            val end = value.indexOf(']')
            val hostPart = value.substring(1, end)
            val rest = value.substring(end + 1)
            if (rest.startsWith(":")) parsedPort = rest.drop(1).toIntOrNull()?.takeIf { it in 1..65535 }
            value = hostPart
        } else {
            val colonCount = value.count { it == ':' }
            if (colonCount == 1) {
                val hostPart = value.substringBefore(':')
                val portPart = value.substringAfter(':')
                val portValue = portPart.toIntOrNull()
                if (portValue != null && portValue in 1..65535) {
                    parsedPort = portValue
                    value = hostPart
                } else if (portPart.isNotBlank()) {
                    return NormalizedNetworkTarget(defaultHost, error = "端口格式不正确")
                }
            }
        }
    }

    value = value.trim().trim('[', ']')
    if (value.isBlank()) return NormalizedNetworkTarget(defaultHost, error = "请输入目标地址")
    if (value.any { it.isWhitespace() || it == '/' || it == '\\' }) {
        return NormalizedNetworkTarget(defaultHost, error = "地址格式不正确，请输入域名或 IP")
    }

    val normalizedHost = when {
        looksLikeIpv4Literal(value) || looksLikeIpv6Literal(value) -> value
        else -> {
            val ascii = runCatching { IDN.toASCII(value) }.getOrNull()
            if (ascii.isNullOrBlank() || ascii.length > 253) return NormalizedNetworkTarget(defaultHost, error = "域名格式不正确")
            val labelOk = ascii.split('.').all { label ->
                label.isNotBlank() && label.length <= 63 &&
                    label.first() != '-' && label.last() != '-' &&
                    label.all { ch -> ch.isLetterOrDigit() || ch == '-' }
            }
            if (!labelOk) return NormalizedNetworkTarget(defaultHost, error = "域名格式不正确")
            ascii
        }
    }

    return NormalizedNetworkTarget(normalizedHost, parsedPort)
}

private data class ResolvedPingTarget(
    val address: String,
    val protocol: PingProtocolMode,
    val displayProtocol: String,
    val error: String? = null
)

private data class PingCommandResult(
    val latencyMs: Int?,
    val failure: String? = null
)

private data class PingStreamEvent(
    val latencyMs: Int?,
    val failure: String? = null,
    val timeEpochMs: Long = System.currentTimeMillis()
)

private suspend fun resolvePingTarget(host: String, protocol: PingProtocolMode): ResolvedPingTarget = withContext(Dispatchers.IO) {
    val target = normalizeNetworkTargetInput(host, "223.5.5.5").host
    runCatching {
        if (looksLikeIpv4Literal(target)) {
            val literal = InetAddress.getByName(target) as? Inet4Address
            return@withContext when {
                literal == null -> ResolvedPingTarget(target, PingProtocolMode.IPV4, "IPv4", "IPv4解析失败")
                protocol == PingProtocolMode.IPV6 -> ResolvedPingTarget(target, PingProtocolMode.IPV6, "IPv6", "IPv6解析失败")
                else -> ResolvedPingTarget(literal.hostAddress ?: target, PingProtocolMode.IPV4, if (protocol == PingProtocolMode.AUTO) "AUTO · IPv4" else "IPv4")
            }
        }
        if (looksLikeIpv6Literal(target)) {
            val literal = InetAddress.getByName(target) as? Inet6Address
            return@withContext when {
                literal == null -> ResolvedPingTarget(target, PingProtocolMode.IPV6, "IPv6", "IPv6解析失败")
                protocol == PingProtocolMode.IPV4 -> ResolvedPingTarget(target, PingProtocolMode.IPV4, "IPv4", "IPv4解析失败")
                else -> ResolvedPingTarget(literal.hostAddress ?: target, PingProtocolMode.IPV6, if (protocol == PingProtocolMode.AUTO) "AUTO · IPv6" else "IPv6")
            }
        }
        val all = NetworkDnsResolver.resolveAddressesBlocking(
            host = target,
            includeIpv4 = true,
            includeIpv6 = true
        ).filterNot { it.isLoopbackAddress }
        val v4 = all.firstOrNull { it is Inet4Address }
        val v6 = all.firstOrNull { it is Inet6Address }
        when (protocol) {
            PingProtocolMode.IPV4 -> if (v4 != null) {
                ResolvedPingTarget(v4.hostAddress ?: target, PingProtocolMode.IPV4, "IPv4")
            } else {
                ResolvedPingTarget(target, PingProtocolMode.IPV4, "IPv4", "IPv4解析失败")
            }
            PingProtocolMode.IPV6 -> if (v6 != null) {
                ResolvedPingTarget(v6.hostAddress ?: target, PingProtocolMode.IPV6, "IPv6")
            } else {
                ResolvedPingTarget(target, PingProtocolMode.IPV6, "IPv6", "IPv6解析失败")
            }
            PingProtocolMode.AUTO -> when {
                v6 != null -> ResolvedPingTarget(v6.hostAddress ?: target, PingProtocolMode.IPV6, "AUTO · IPv6")
                v4 != null -> ResolvedPingTarget(v4.hostAddress ?: target, PingProtocolMode.IPV4, "AUTO · IPv4")
                else -> ResolvedPingTarget(target, PingProtocolMode.AUTO, "AUTO", "解析失败")
            }
        }
    }.getOrElse {
        ResolvedPingTarget(target, protocol, protocol.label, "解析失败")
    }
}


private data class TcpPingProbe(val port: Int, val latencyMs: Int)

private val TCP_PING_PROBE_PORTS = listOf(80, 443, 22, 8080, 8443, 8000, 5000, 5001)

private suspend fun tcpSocketProbe(address: String, port: Int, timeoutMs: Int): TcpPingProbe? = withContext(Dispatchers.IO) {
    runCatching {
        val startedAt = System.nanoTime()
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(address, port), timeoutMs.coerceIn(180, 2_000))
        }
        TcpPingProbe(port, ((System.nanoTime() - startedAt) / 1_000_000L).toInt().coerceAtLeast(1))
    }.getOrNull()
}

private suspend fun findTcpPingPort(address: String, timeoutMs: Int): TcpPingProbe? = withContext(Dispatchers.IO) {
    val probeTimeout = timeoutMs.coerceIn(180, 650)
    for (port in TCP_PING_PROBE_PORTS) {
        val probe = tcpSocketProbe(address, port, probeTimeout)
        if (probe != null) return@withContext probe
    }
    null
}

private suspend fun tcpSocketPingResolved(address: String, port: Int, timeoutMs: Int): PingCommandResult = withContext(Dispatchers.IO) {
    runCatching {
        val startedAt = System.nanoTime()
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(address, port), timeoutMs.coerceIn(120, 5_000))
        }
        PingCommandResult(((System.nanoTime() - startedAt) / 1_000_000L).toInt().coerceAtLeast(1), null)
    }.getOrElse { error ->
        val msg = error.message.orEmpty()
        val failure = when {
            msg.contains("timed out", ignoreCase = true) -> "超时"
            msg.contains("refused", ignoreCase = true) -> "端口关闭"
            msg.contains("unreachable", ignoreCase = true) -> "不可达"
            else -> "TCP失败"
        }
        PingCommandResult(null, failure)
    }
}

private suspend fun icmpPingResolved(address: String, timeoutMs: Int, protocol: PingProtocolMode): PingCommandResult = withContext(Dispatchers.IO) {
    val timeoutSec = ((timeoutMs.coerceIn(300, 10_000) + 999) / 1000).coerceAtLeast(1)
    fun runCommand(command: List<String>): PingCommandResult {
        return runCatching {
            val startedAt = System.nanoTime()
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val finished = process.waitFor((timeoutMs + 900).toLong(), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching PingCommandResult(null, "超时")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val parsed = Regex("time[=<]([0-9.]+)\\s*ms")
                .find(output)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
                ?.roundToInt()
                ?.coerceAtLeast(1)
            when {
                parsed != null -> PingCommandResult(parsed, null)
                process.exitValue() == 0 -> PingCommandResult(((System.nanoTime() - startedAt) / 1_000_000L).toInt().coerceAtLeast(1), null)
                output.contains("unknown host", ignoreCase = true) -> PingCommandResult(null, "解析失败")
                output.contains("unreachable", ignoreCase = true) -> PingCommandResult(null, "不可达")
                else -> PingCommandResult(null, "超时")
            }
        }.getOrElse {
            PingCommandResult(null, "命令失败")
        }
    }
    when (protocol) {
        PingProtocolMode.IPV4 -> runCommand(listOf("ping", "-c", "1", "-W", timeoutSec.toString(), address))
        PingProtocolMode.IPV6 -> {
            val primary = runCommand(listOf("ping6", "-c", "1", "-W", timeoutSec.toString(), address))
            if (primary.failure == "命令失败") runCommand(listOf("ping", "-6", "-c", "1", "-W", timeoutSec.toString(), address)) else primary
        }
        PingProtocolMode.AUTO -> runCommand(listOf("ping", "-c", "1", "-W", timeoutSec.toString(), address))
    }
}

private suspend fun streamIcmpPingResolved(
    address: String,
    timeoutMs: Int,
    protocol: PingProtocolMode,
    intervalMs: Long,
    maxCount: Int,
    onEvent: suspend (PingStreamEvent) -> Unit
): Int = withContext(Dispatchers.IO) {
    val timeoutSec = ((timeoutMs.coerceIn(300, 10_000) + 999) / 1000).coerceAtLeast(1)
    val intervalSec = String.format(java.util.Locale.US, "%.3f", intervalMs.coerceIn(25L, 60_000L) / 1000.0)
    val base = when (protocol) {
        PingProtocolMode.IPV4 -> listOf("ping", "-i", intervalSec, "-c", maxCount.toString(), "-W", timeoutSec.toString(), address)
        PingProtocolMode.IPV6 -> listOf("ping6", "-i", intervalSec, "-c", maxCount.toString(), "-W", timeoutSec.toString(), address)
        PingProtocolMode.AUTO -> listOf("ping", "-i", intervalSec, "-c", maxCount.toString(), "-W", timeoutSec.toString(), address)
    }
    suspend fun runStream(command: List<String>): Pair<Int, Boolean> {
        var process: Process? = null
        var emitted = 0
        var lastSeq = 0
        var sawPingLine = false
        val seqRegex = Regex("(?:icmp_)?seq[= ](\\d+)")
        val timeRegex = Regex("time[=<]([0-9.]+)\\s*ms")
        return try {
            process = ProcessBuilder(command).redirectErrorStream(true).start()
            val reader = process!!.inputStream.bufferedReader()
            while (currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                val lower = line.lowercase()
                if (lower.contains("unknown host") || lower.contains("bad address")) {
                    withContext(Dispatchers.Main) { onEvent(PingStreamEvent(null, "解析失败")) }
                    emitted++
                    break
                }
                if (lower.contains("not permitted") || lower.contains("invalid option") || lower.contains("usage:")) {
                    return Pair(emitted, false)
                }
                val time = timeRegex.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.roundToInt()?.coerceAtLeast(1)
                if (time != null) {
                    sawPingLine = true
                    val seq = seqRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: (lastSeq + 1)
                    if (seq > lastSeq + 1) {
                        repeat((seq - lastSeq - 1).coerceAtMost(maxCount - emitted)) {
                            withContext(Dispatchers.Main) { onEvent(PingStreamEvent(null, "超时")) }
                            emitted++
                        }
                    }
                    lastSeq = seq.coerceAtLeast(lastSeq)
                    withContext(Dispatchers.Main) { onEvent(PingStreamEvent(time, null)) }
                    emitted++
                    if (emitted >= maxCount) break
                }
            }
            if (currentCoroutineContext().isActive) {
                val waitMs = (timeoutMs + intervalMs + 1500L).coerceAtMost(12_000L)
                process!!.waitFor(waitMs, TimeUnit.MILLISECONDS)
                if (sawPingLine && emitted < maxCount) {
                    repeat((maxCount - emitted).coerceAtMost(500)) {
                        withContext(Dispatchers.Main) { onEvent(PingStreamEvent(null, "超时")) }
                        emitted++
                    }
                }
            }
            Pair(emitted, sawPingLine || emitted > 0)
        } catch (_: Throwable) {
            Pair(emitted, false)
        } finally {
            runCatching { process?.destroy() }
            runCatching { process?.destroyForcibly() }
        }
    }

    val primary = runStream(base)
    if (primary.second || protocol != PingProtocolMode.IPV6) {
        primary.first
    } else {
        runStream(listOf("ping", "-6", "-i", intervalSec, "-c", maxCount.toString(), "-W", timeoutSec.toString(), address)).first
    }
}

private fun recommendedPingTimeoutMsForInterval(intervalMs: Long): Int = when {
    intervalMs <= 30L -> 300
    intervalMs <= 50L -> 500
    intervalMs <= 100L -> 800
    intervalMs <= 200L -> 1000
    intervalMs <= 500L -> 1500
    else -> 3000
}

private fun pingMaxInflight(intervalMs: Long, timeoutMs: Int): Int {
    val byWindow = ((timeoutMs + intervalMs - 1L) / intervalMs).toInt().coerceAtLeast(1)
    return byWindow.coerceIn(3, 16)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            NetSessionTesterTheme {
                NetSessionTesterApp()
            }
        }
    }
}

@Composable
private fun NetSessionTesterApp() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    NetworkDnsResolver.install(context.applicationContext)
    val tester = remember { TcpTester(context.applicationContext) }
    val historyStore = remember { HistoryStore(context.applicationContext) }
    val logStore = remember { LogStore(context.applicationContext) }
    val settingsStore = remember { SettingsStore(context.applicationContext) }

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.SETTINGS) }
    var appToolPage by rememberSaveable { mutableStateOf(AppToolPage.NONE) }
    val settingsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val testListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var testPingFocusRequest by remember { mutableStateOf(0) }
    var sessionCardExpanded by remember { mutableStateOf(false) }
    var networkInfoExpanded by rememberSaveable { mutableStateOf(false) }
    var runningJob by remember { mutableStateOf<Job?>(null) }
    var state by remember { mutableStateOf(AppUiState()) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var settingsLoaded by remember { mutableStateOf(false) }

    var host by remember { mutableStateOf("www.baidu.com") }
    var port by remember { mutableStateOf("80") }
    var mode by remember { mutableStateOf(TestMode.IPV4_THEN_IPV6) }
    var batchSize by remember { mutableStateOf("200") }
    var intervalMs by remember { mutableStateOf("100") }
    var timeoutMs by remember { mutableStateOf("1200") }
    var successLimit by remember { mutableStateOf("65535") }
    var failureLimit by remember { mutableStateOf("600") }
    var keepConnections by remember { mutableStateOf(true) }
    var maskPrivacy by remember { mutableStateOf(false) }
    var historyLimit by remember { mutableStateOf("30") }
    var pingEnabled by remember { mutableStateOf(true) }
    var pingTarget by remember { mutableStateOf("223.5.5.5") }
    var pingIntervalSetting by remember { mutableStateOf("1000") }
    var pingCountSetting by remember { mutableStateOf("无限") }
    var pingTimeoutSetting by remember { mutableStateOf("1000") }
    var pingProtocolSetting by remember { mutableStateOf(PingProtocolMode.AUTO) }
    var historyPeriod by remember { mutableStateOf("WEEK") }
    var logSizeKb by remember { mutableStateOf(0) }
    var historySizeKb by remember { mutableStateOf(0) }
    var historySavedCount by remember { mutableStateOf(0) }
    var historyCounts by remember { mutableStateOf(HistoryCounts()) }
    var publicIpResult by remember { mutableStateOf(PublicIpResult()) }
    var publicIpLoading by remember { mutableStateOf(false) }
    var networkProbeInfo by remember { mutableStateOf(NetworkProbeInfo()) }
    var manualStopRequested by remember { mutableStateOf(false) }
    var currentTestConfig by remember { mutableStateOf<SessionConfig?>(null) }
    var currentStartedAt by remember { mutableStateOf(0L) }
    var chartMode by remember { mutableStateOf(ChartMode.GROWTH) }
    var chartPoints by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }
    var lastChartSampleAt by remember { mutableStateOf<Map<IpProtocol, Long>>(emptyMap()) }
    var pingPoints by remember { mutableStateOf<List<PingPoint>>(emptyList()) }
    var displayChartPoints by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }
    var displayPingPoints by remember { mutableStateOf<List<PingPoint>>(emptyList()) }
    var displayPingJitterMs by remember { mutableStateOf<Double?>(null) }
    var displayPingLogCount by remember { mutableStateOf(0) }
    var pingJob by remember { mutableStateOf<Job?>(null) }
    var pingLogSaveJob by remember { mutableStateOf<Job?>(null) }
    var pingIntervalLabel by remember { mutableStateOf("停止") }
    var pingActiveTargetLabel by remember { mutableStateOf("") }
    var pingRunning by remember { mutableStateOf(false) }
    var pingSessionStartedAt by remember { mutableStateOf(0L) }
    var pingSessionEndedAt by remember { mutableStateOf(0L) }
    var pingDurationTick by remember { mutableStateOf(System.currentTimeMillis()) }
    var pingJitterMs by remember { mutableStateOf<Double?>(null) }
    var activePingSessionId by remember { mutableStateOf(0L) }
    var pingLogs by remember { mutableStateOf<List<PingLogEntry>>(emptyList()) }
    var hostHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var pingTargetHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var networkWatchJob by remember { mutableStateOf<Job?>(null) }
    var networkRefreshJob by remember { mutableStateOf<Job?>(null) }
    var networkEventRefreshJob by remember { mutableStateOf<Job?>(null) }
    var networkRefreshGeneration by remember { mutableStateOf(0L) }
    var appInForeground by remember { mutableStateOf(true) }
    var testNetworkSignature by remember { mutableStateOf("") }
    var lastNetworkInfoSignature by remember { mutableStateOf("") }
    var activeRunId by remember { mutableStateOf(0L) }
    var finishInProgress by remember { mutableStateOf(false) }

    var detailTitle by remember { mutableStateOf<String?>(null) }
    var detailLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var detailSummary by remember { mutableStateOf<SessionSummary?>(null) }
    var editingRemarkSummary by remember { mutableStateOf<SessionSummary?>(null) }
    var editingRemarkText by remember { mutableStateOf("") }
    var showRunLogDetail by remember { mutableStateOf(false) }
    var showPingLogDialog by remember { mutableStateOf(false) }
    var showNatDiagnosticDialog by remember { mutableStateOf(false) }
    var showNatHistoryDialog by remember { mutableStateOf(false) }
    var natHistory by remember { mutableStateOf(loadNatHistory(context.applicationContext)) }
    var natDiagnosticResult by remember { mutableStateOf<ManualNatResult?>(null) }
    var natDiagnosticRunning by remember { mutableStateOf(false) }
    var natDiagnosticProgress by remember { mutableStateOf("") }
    var natManualMode by remember { mutableStateOf(ManualNatMode.RFC5780) }
    var natRfc5780Servers by remember { mutableStateOf(listOf("stunserver2025.stunprotocol.org:3478")) }
    var natRfc3489Servers by remember { mutableStateOf(listOf("stun.voip.aebc.com:3478")) }

    val updatePrefs = remember { context.getSharedPreferences("app_update", Context.MODE_PRIVATE) }
    var latestUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateAvailable by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadUi by remember { mutableStateOf(UpdateDownloadUi()) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadRunId by remember { mutableStateOf(0L) }
    var bottomNotice by remember { mutableStateOf(BottomNoticeUi()) }
    var bottomNoticeJob by remember { mutableStateOf<Job?>(null) }

    BackHandler(enabled = showRunLogDetail) { showRunLogDetail = false }
    BackHandler(enabled = showNatHistoryDialog) { showNatHistoryDialog = false }
    BackHandler(enabled = showNatDiagnosticDialog) { showNatDiagnosticDialog = false }
    BackHandler(enabled = appToolPage != AppToolPage.NONE) { appToolPage = AppToolPage.NONE }

    LaunchedEffect(state.releaseUi.visible, state.releaseUi.finished, state.releaseUi.elapsedMs) {
        if (!state.releaseUi.visible || !state.releaseUi.finished) return@LaunchedEffect
        val completedSnapshot = state.releaseUi
        delay(10_000L)
        if (state.releaseUi == completedSnapshot) {
            state = state.copy(releaseUi = ReleaseUiState())
        }
    }

    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    appInForeground = true
                    networkInfoExpanded = false
                }
                Lifecycle.Event.ON_STOP -> appInForeground = false
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            networkRefreshJob?.cancel()
            networkEventRefreshJob?.cancel()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val csv = pendingCsv
        if (uri != null && csv != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(csv) }
                    } ?: error("无法打开导出文件")
                }.onSuccess {
                    snackbarHostState.showSnackbar("CSV 已导出")
                }.onFailure {
                    snackbarHostState.showSnackbar("导出失败：${it.message}")
                }
                pendingCsv = null
            }
        } else {
            pendingCsv = null
        }
    }

    fun showDetail(title: String, lines: List<String>) {
        detailTitle = title
        detailLines = lines
    }


    fun copyText(value: String, label: String) {
        val clean = value.trim()
        if (clean.isBlank() || clean == "不可用" || clean == "检测中" || clean == "待检测" || clean == "未知") {
            scope.launch { snackbarHostState.showSnackbar("$label 暂不可复制") }
            return
        }
        clipboardManager.setText(AnnotatedString(clean))
        scope.launch { snackbarHostState.showSnackbar("已复制$label：$clean") }
    }


    fun safeHistoryLimit(): Int = historyLimit.toIntOrNull()?.coerceIn(10, 100) ?: 30

    suspend fun loadHistoryUiSnapshot(period: String, limit: Int): HistoryUiSnapshot = withContext(Dispatchers.IO) {
        HistoryUiSnapshot(
            history = historyStore.load(period, limit),
            sizeKb = historyStore.sizeKb(),
            savedCount = historyStore.count(),
            counts = historyStore.counts()
        )
    }

    fun applyHistoryUiSnapshot(snapshot: HistoryUiSnapshot) {
        state = state.copy(history = snapshot.history)
        historySizeKb = snapshot.sizeKb
        historySavedCount = snapshot.savedCount
        historyCounts = snapshot.counts
    }

    fun refreshHistory() {
        scope.launch {
            applyHistoryUiSnapshot(loadHistoryUiSnapshot(historyPeriod, safeHistoryLimit()))
        }
    }

    fun persistPingLogs(snapshot: List<PingLogEntry>) {
        val appContext = context.applicationContext
        pingLogSaveJob?.cancel()
        pingLogSaveJob = scope.launch(Dispatchers.IO) {
            delay(PING_LOG_SAVE_DEBOUNCE_MS)
            savePingLogs(appContext, snapshot)
        }
    }

    fun refreshPublicIp() {
        networkRefreshGeneration += 1L
        val generation = networkRefreshGeneration
        networkRefreshJob?.cancel()
        networkRefreshJob = scope.launch {
            publicIpLoading = true
            val appContext = context.applicationContext
            val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
            val network = connectivity?.activeNetwork
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching { PublicIpDetector.detect(network) }.getOrElse { PublicIpResult() }
                }
                if (generation != networkRefreshGeneration) return@launch

                val normalizedResult = result.copy(
                    ipv4 = result.ipv4.takeIf { it.isUsableIpText() } ?: "待检测",
                    ipv6 = result.ipv6.takeIf { it.isUsableIpText() } ?: "待检测"
                )
                // 公网出口先显示，运营商/出口类型随后异步补全，避免整张卡等待全部探测。
                publicIpResult = normalizedResult
                val currentEnv = detectNetworkEnvironment(appContext)
                if (currentEnv.hasVpn) {
                    val fastCarrier = withContext(Dispatchers.IO) {
                        displayCarrierFromEnv(currentEnv, normalizedResult.ipv4, normalizedResult.ipv6, false)
                    }
                    if (generation != networkRefreshGeneration) return@launch
                    networkProbeInfo = networkProbeInfo.copy(carrier = fastCarrier)
                }

                val freshProbe = runCatching {
                    detectNetworkProbe(
                        context = appContext,
                        network = network,
                        publicIpResult = normalizedResult,
                        env = currentEnv,
                        targetHost = host.ifBlank { "www.baidu.com" },
                        targetPort = port.toIntOrNull() ?: 80
                    )
                }.getOrElse {
                    NetworkProbeInfo(
                        localIp = localIpv4Addresses().firstOrNull() ?: localIpv6Addresses().firstOrNull() ?: "待检测",
                        carrier = "未知",
                        natType = "待检测",
                        latencyText = "待检测",
                        portText = "待检测",
                        priority = "待检测",
                        mappingBehavior = "待检测",
                        filterBehavior = "待检测",
                        ipv6Status = "待检测",
                        dnsStatus = "待检测",
                        confidence = "待检测",
                        diagnosis = ""
                    )
                }
                if (generation != networkRefreshGeneration) return@launch
                // 5 秒轻量刷新不能覆盖本次手动 NAT 诊断结果。
                networkProbeInfo = if (natDiagnosticResult != null) {
                    freshProbe.copy(
                        natType = networkProbeInfo.natType,
                        portText = networkProbeInfo.portText,
                        mappingBehavior = networkProbeInfo.mappingBehavior,
                        filterBehavior = networkProbeInfo.filterBehavior,
                        confidence = networkProbeInfo.confidence,
                        diagnosis = networkProbeInfo.diagnosis,
                        refreshMode = networkProbeInfo.refreshMode
                    )
                } else {
                    freshProbe.copy(
                        natType = "待检测",
                        portText = "待检测",
                        mappingBehavior = "待检测",
                        filterBehavior = "待检测",
                        confidence = "待检测",
                        refreshMode = "待检测"
                    )
                }
                lastNetworkInfoSignature = currentNetworkSignature(appContext)
            } catch (_: CancellationException) {
                throw CancellationException("网络信息刷新已被新一轮替换")
            } finally {
                if (generation == networkRefreshGeneration) {
                    publicIpLoading = false
                    networkRefreshJob = null
                }
            }
        }
    }

    fun scheduleNetworkEventRefresh() {
        networkEventRefreshJob?.cancel()
        networkEventRefreshJob = scope.launch {
            // VPN/默认网络变化通常会连续触发多次回调，短暂防抖后只刷新一次。
            delay(250L)
            if (!settingsLoaded || !appInForeground || state.isAdding || finishInProgress) return@launch
            val appContext = context.applicationContext
            val signature = currentNetworkSignature(appContext)
            val vpnActive = detectNetworkEnvironment(appContext).hasVpn
            val shouldClearStaleExit = signature != lastNetworkInfoSignature || vpnActive
            if (shouldClearStaleExit) {
                networkRefreshGeneration += 1L
                networkRefreshJob?.cancel()
                publicIpLoading = false
                publicIpResult = PublicIpResult()
                networkProbeInfo = networkProbeInfo.copy(
                    carrier = "待检测",
                    ipv6Status = "待检测",
                    dnsStatus = "待检测",
                    priority = "待检测"
                )
                lastNetworkInfoSignature = signature
            }
            // 同一个 VPN Network 内切换节点时 network 对象可能不变，也必须绕过旧出口缓存。
            PublicIpDetector.invalidate()
            refreshPublicIp()
        }
    }

    fun refreshNetworkInfoLight() {
        PublicIpDetector.invalidate()
        refreshPublicIp()
    }

    DisposableEffect(settingsLoaded, appInForeground, state.isAdding, finishInProgress) {
        if (!settingsLoaded || !appInForeground || state.isAdding || finishInProgress) {
            onDispose { }
        } else {
            val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            val callback = object : ConnectivityManager.NetworkCallback() {
                private fun trigger() {
                    scope.launch { scheduleNetworkEventRefresh() }
                }

                override fun onAvailable(network: Network) = trigger()
                override fun onLost(network: Network) = trigger()
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = trigger()
                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = trigger()
            }
            runCatching { cm?.registerDefaultNetworkCallback(callback) }
            onDispose {
                runCatching { cm?.unregisterNetworkCallback(callback) }
                networkEventRefreshJob?.cancel()
            }
        }
    }

    LaunchedEffect(Unit) {
        val appContext = context.applicationContext
        val initialHistory = loadHistoryUiSnapshot(historyPeriod, 30)
        val initialLogs = logStore.load()
        val initialPingLogs = withContext(Dispatchers.IO) { loadPingLogs(appContext) }
        val initialHostHistory = withContext(Dispatchers.IO) { loadTargetHistory(appContext, "tcp_target_history_v1") }
        val initialPingTargetHistory = withContext(Dispatchers.IO) { loadTargetHistory(appContext, "ping_target_history_v1") }
        val initialLogSizeKb = withContext(Dispatchers.IO) { logStore.sizeKb() }
        applyHistoryUiSnapshot(initialHistory)
        state = state.copy(logs = initialLogs)
        pingLogs = initialPingLogs
        displayPingLogCount = initialPingLogs.size
        hostHistory = initialHostHistory
        pingTargetHistory = initialPingTargetHistory
        logSizeKb = initialLogSizeKb
        val saved = settingsStore.load()
        host = saved.host.ifBlank { "www.baidu.com" }
        port = saved.port
        mode = saved.mode
        batchSize = saved.batchSize
        intervalMs = saved.intervalMs
        timeoutMs = saved.timeoutMs
        successLimit = saved.successLimit
        failureLimit = saved.failureLimit
        keepConnections = saved.keepConnections
        maskPrivacy = saved.maskPrivacy
        historyLimit = if (saved.historyLimit in listOf("10", "30", "100")) saved.historyLimit else "30"
        pingEnabled = saved.pingEnabled
        pingTarget = saved.pingTarget.ifBlank { "223.5.5.5" }
        pingIntervalSetting = saved.pingIntervalMs
        pingCountSetting = saved.pingCount.ifBlank { "无限" }
        pingTimeoutSetting = saved.pingTimeoutMs
        pingProtocolSetting = runCatching { PingProtocolMode.valueOf(saved.pingProtocol) }.getOrDefault(PingProtocolMode.AUTO)
        natRfc5780Servers = normalizeNatServerList(saved.natRfc5780Servers, "stunserver2025.stunprotocol.org:3478")
        natRfc3489Servers = normalizeNatServerList(saved.natRfc3489Servers, "stun.voip.aebc.com:3478")
        applyHistoryUiSnapshot(loadHistoryUiSnapshot(historyPeriod, historyLimit.toIntOrNull()?.coerceIn(10, 100) ?: 30))
        settingsLoaded = true
        lastNetworkInfoSignature = currentNetworkSignature(context)
        refreshPublicIp()
    }

    LaunchedEffect(Unit) {
        while (true) {
            displayPingPoints = compactPingPointsForRender(pingPoints)
            displayChartPoints = compactSessionPointsForRender(chartPoints)
            displayPingJitterMs = pingJitterMs
            displayPingLogCount = pingLogs.size
            delay(if (pingRunning || state.isAdding) TEST_UI_REFRESH_MS else 1_000L)
        }
    }

    LaunchedEffect(pingRunning, pingSessionStartedAt) {
        while (pingRunning && pingSessionStartedAt > 0L) {
            pingDurationTick = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(settingsLoaded, appInForeground, state.isAdding, finishInProgress) {
        if (!settingsLoaded || !appInForeground) return@LaunchedEffect
        // 进入前台立即刷新；连接数压测/释放阶段暂停轻量刷新，避免额外占用 FD。
        if (!state.isAdding && !finishInProgress) refreshPublicIp()
        while (appInForeground) {
            delay(5_000L)
            if (!state.isAdding && !finishInProgress) refreshPublicIp()
        }
    }

    LaunchedEffect(
        settingsLoaded, host, port, mode, batchSize, intervalMs, timeoutMs,
        successLimit, failureLimit, keepConnections, maskPrivacy, historyLimit,
        pingEnabled, pingTarget, pingIntervalSetting, pingCountSetting, pingTimeoutSetting, pingProtocolSetting,
        natRfc5780Servers, natRfc3489Servers
    ) {
        if (settingsLoaded) {
            settingsStore.save(
                SavedSettings(
                    host = host.ifBlank { "www.baidu.com" },
                    port = port,
                    mode = mode,
                    batchSize = batchSize,
                    intervalMs = intervalMs,
                    timeoutMs = timeoutMs,
                    successLimit = successLimit,
                    failureLimit = failureLimit,
                    keepConnections = keepConnections,
                    maskPrivacy = maskPrivacy,
                    historyLimit = historyLimit,
                    pingEnabled = pingEnabled,
                    pingTarget = pingTarget.ifBlank { "223.5.5.5" },
                    pingIntervalMs = pingIntervalSetting,
                    pingCount = pingCountSetting,
                    pingTimeoutMs = pingTimeoutSetting,
                    pingProtocol = pingProtocolSetting.name,
                    natRfc5780Servers = encodeNatServerList(natRfc5780Servers, "stunserver2025.stunprotocol.org:3478"),
                    natRfc3489Servers = encodeNatServerList(natRfc3489Servers, "stun.voip.aebc.com:3478")
                )
            )
        }
    }

    fun appendLog(line: LogLine) {
        state = state.copy(logs = (state.logs + line).takeLast(500))
        scope.launch {
            logStore.append(line)
            logSizeKb = withContext(Dispatchers.IO) { logStore.sizeKb() }
        }
    }

    fun showBottomNotice(
        message: String,
        tone: BottomNoticeTone = BottomNoticeTone.Info,
        durationMs: Long = 3_600L
    ) {
        bottomNoticeJob?.cancel()
        val id = System.nanoTime()
        bottomNotice = BottomNoticeUi(visible = true, message = message, tone = tone, id = id)
        bottomNoticeJob = scope.launch {
            delay(durationMs)
            if (bottomNotice.id == id) {
                bottomNotice = bottomNotice.copy(visible = false)
            }
        }
    }

    fun notifyLocalReleased(prefix: String = "本机已释放") {
        val message = "$prefix，路由器会话表可能延迟数秒下降"
        appendLog(LogLine(level = LogLevel.WARN, text = message))
        scope.launch { snackbarHostState.currentSnackbarData?.dismiss() }
        showBottomNotice(message = message, tone = BottomNoticeTone.Success, durationMs = 3_800L)
    }

    fun openGithub(url: String = PROJECT_GITHUB_URL) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun isIgnoredUpdate(info: UpdateInfo): Boolean {
        val ignoredCode = (updatePrefs.all["ignored_version_code"] as? Number)?.toLong() ?: -1L
        return ignoredCode == info.versionCode
    }

    fun postponeUpdate() {
        updatePrefs.edit().putLong("postpone_until", System.currentTimeMillis() + UPDATE_POSTPONE_MS).apply()
        showUpdateDialog = false
        scope.launch { snackbarHostState.showSnackbar("已稍后更新，8分钟内不再自动提醒") }
    }

    fun ignoreUpdate(info: UpdateInfo) {
        updatePrefs.edit()
            .putLong("ignored_version_code", info.versionCode)
            .putString("ignored_version_name", info.versionName)
            .putLong("ignored_at", System.currentTimeMillis())
            .remove("postpone_until")
            .apply()
        latestUpdate = null
        updateAvailable = false
        showUpdateDialog = false
        scope.launch { snackbarHostState.showSnackbar("已忽略 ${formatVersionBuild(info.versionName, info.versionCode)}，下个版本再自动提醒") }
    }

    fun checkForUpdate(manual: Boolean = false) {
        scope.launch {
            checkingUpdate = true
            runCatching { fetchUpdateInfo() }
                .onSuccess { info ->
                    val hasNew = info.versionCode > currentAppVersionCode(context)
                    val ignored = hasNew && isIgnoredUpdate(info)
                    latestUpdate = info.takeIf { hasNew }
                    updateAvailable = false
                    if (!hasNew) {
                        if (manual) snackbarHostState.showSnackbar("已是最新版本")
                    } else if (ignored && !manual) {
                        showUpdateDialog = false
                    } else if (!isUpdateReadyStatus(info.status)) {
                        showUpdateDialog = false
                        if (manual) snackbarHostState.showSnackbar("发现新版本，但当前状态为 ${info.status}，暂不推送")
                    } else {
                        val ready = runCatching { verifyUpdateApkAvailable(info) }
                        if (ready.isSuccess) {
                            updateAvailable = !ignored
                            if (manual || !ignored) {
                                showVersionDialog = false
                                showUpdateDialog = true
                            }
                        } else {
                            showUpdateDialog = false
                            val msg = ready.exceptionOrNull()?.message ?: "安装包暂不可用"
                            if (manual) snackbarHostState.showSnackbar(msg)
                        }
                    }
                }
                .onFailure { e ->
                    if (manual) snackbarHostState.showSnackbar(e.message ?: "检测更新失败")
                }
            checkingUpdate = false
        }
    }

    fun startUpdateDownload(info: UpdateInfo) {
        showUpdateDialog = false
        showDownloadDialog = true
        downloadJob?.cancel()
        val runId = System.nanoTime()
        downloadRunId = runId
        downloadUi = UpdateDownloadUi(active = true, message = "正在准备下载")
        downloadJob = scope.launch {
            runCatching {
                downloadUpdateApk(context.applicationContext, info) { ui ->
                    if (downloadRunId == runId) scope.launch { downloadUi = ui }
                }
            }.onSuccess { file ->
                if (downloadRunId != runId) return@onSuccess
                downloadUi = UpdateDownloadUi(
                    active = false,
                    finished = true,
                    progress = 100,
                    downloadedBytes = file.length(),
                    totalBytes = file.length(),
                    message = "下载完成",
                    apkFilePath = file.absolutePath
                )
                showDownloadDialog = true
                snackbarHostState.showSnackbar("更新包下载完成，可以安装")
            }.onFailure { e ->
                if (downloadRunId != runId) return@onFailure
                if (e is CancellationException) {
                    downloadUi = UpdateDownloadUi(active = false, failed = true, message = "已取消下载，可重新下载")
                } else {
                    downloadUi = UpdateDownloadUi(active = false, failed = true, message = e.message ?: "下载失败，建议切换代理网络或打开 GitHub")
                }
                showDownloadDialog = true
            }
        }
    }

    fun cancelUpdateDownload() {
        downloadRunId = System.nanoTime()
        downloadJob?.cancel()
        downloadJob = null
        downloadUi = UpdateDownloadUi(active = false, failed = true, message = "已取消下载，可重新下载")
    }

    fun dismissUpdateDownloadUi() {
        if (downloadUi.active) {
            downloadRunId = System.nanoTime()
            downloadJob?.cancel()
            downloadJob = null
        }
        showDownloadDialog = false
        downloadUi = UpdateDownloadUi()
    }

    fun closeUpdateDownloadDialog() {
        if (downloadUi.failed || downloadUi.finished) {
            dismissUpdateDownloadUi()
        } else {
            showDownloadDialog = false
        }
    }

    fun installReadyApk() {
        val path = downloadUi.apkFilePath ?: return
        installDownloadedApk(context, File(path))
    }

    LaunchedEffect(settingsLoaded) {
        if (settingsLoaded) {
            delay(1200L)
            val postponeUntil = updatePrefs.getLong("postpone_until", 0L)
            if (System.currentTimeMillis() >= postponeUntil) {
                checkForUpdate(manual = false)
            } else {
                runCatching { fetchUpdateInfo() }.onSuccess { info ->
                    val hasNew = info.versionCode > currentAppVersionCode(context)
                    val ignored = hasNew && isIgnoredUpdate(info)
                    latestUpdate = info.takeIf { hasNew }
                    updateAvailable = hasNew && !ignored && isUpdateReadyStatus(info.status) && runCatching { verifyUpdateApkAvailable(info) }.isSuccess
                }
            }
        }
    }

    fun resetCurrentCharts() {
        chartPoints = emptyList()
        displayChartPoints = emptyList()
        lastChartSampleAt = emptyMap()
        // Ping 独立运行，不随会话曲线清空。
    }

    fun recordChartPoint(stats: ProtocolStats) {
        if (currentStartedAt <= 0L) return
        val now = System.currentTimeMillis()
        val last = lastChartSampleAt[stats.protocol] ?: 0L
        val terminal = stats.phase.contains("完成") || stats.phase.contains("释放") || stats.phase.contains("中断") || stats.phase.contains("上限")
        if (!terminal && now - last < 1_000L) return
        val elapsed = ((now - currentStartedAt) / 1_000L).toInt().coerceAtLeast(0)
        val previousPeak = chartPoints.filter { it.protocol == stats.protocol }.maxOfOrNull { it.active } ?: 0
        val displayActive = if (terminal && stats.activeSessions == 0) {
            maxOf(previousPeak, stats.maxStableSessions, stats.totalSuccess)
        } else {
            stats.activeSessions
        }
        val point = ChartPoint(
            protocol = stats.protocol,
            elapsedSec = elapsed,
            active = displayActive,
            failure = stats.totalFailure,
            total = stats.totalAttempts,
            cps = stats.cps,
            phase = stats.phase
        )
        chartPoints = (chartPoints.filterNot { it.protocol == point.protocol && it.elapsedSec == point.elapsedSec } + point)
            .sortedWith(compareBy<ChartPoint> { it.protocol.ordinal }.thenBy { it.elapsedSec })
            .takeLast(180)
        lastChartSampleAt = lastChartSampleAt + (stats.protocol to now)
    }

    fun appendPingBucket(bucketElapsedMs: Long, samples: List<Int?>) {
        val valid = samples.mapNotNull { it }
        val avg = valid.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val min = valid.minOrNull()
        val max = valid.maxOrNull()
        val lossCount = samples.count { it == null }
        val highLatency = valid.any { it >= 100 }
        val sec = (bucketElapsedMs / 1_000L).toInt().coerceAtLeast(0)
        pingPoints = (pingPoints.filterNot { it.elapsedMs == bucketElapsedMs } + PingPoint(
            elapsedSec = sec,
            latencyMs = avg,
            lossCount = lossCount,
            highLatency = highLatency,
            sampleCount = samples.size.coerceAtLeast(1),
            elapsedMs = bucketElapsedMs,
            minLatencyMs = min,
            maxLatencyMs = max
        ))
            .sortedBy { it.elapsedMs }
            .takeLast(2400)
    }

    fun appendPingSecond(sec: Int, samples: List<Int?>) {
        appendPingBucket(sec * 1_000L, samples)
    }

    fun alignPingWithSessionEnd() {
        // 不再把最后一个真实点复制到测试结束时间。
        // 复制点会造成“没有丢包但后半段变虚线”和右侧异常连接。
        // 图表的 X 轴由真实样本范围决定，结束时间只用于文字统计。
    }

    fun safePingIntervalMs(): Long = pingIntervalSetting.toLongOrNull()?.coerceIn(30L, 60_000L) ?: 1_000L

    fun safePingTimeoutMs(): Int = pingTimeoutSetting.toIntOrNull()?.coerceIn(300, 10_000) ?: 1_000

    fun safePingCount(): Int? {
        val clean = pingCountSetting.trim()
        if (clean.isBlank() || clean == "无限") return null
        return clean.toIntOrNull()?.coerceIn(1, 100_000)
    }

    fun appendPingLog(entry: PingLogEntry) {
        val next = trimPingLogSessions(pingLogs + entry)
        pingLogs = next
        displayPingLogCount = next.size
        persistPingLogs(next)
    }

    fun appendPingLogs(entries: List<PingLogEntry>) {
        if (entries.isEmpty()) return
        val next = trimPingLogSessions(pingLogs + entries)
        pingLogs = next
        displayPingLogCount = next.size
        persistPingLogs(next)
    }

    fun appendPingPoint(sec: Int, latencyMs: Int?) {
        val ms = sec * 1_000L
        pingPoints = (pingPoints.filterNot { it.elapsedMs == ms } + PingPoint(
            elapsedSec = sec,
            latencyMs = latencyMs,
            lossCount = if (latencyMs == null) 1 else 0,
            highLatency = (latencyMs ?: 0) >= 100,
            elapsedMs = ms,
            minLatencyMs = latencyMs,
            maxLatencyMs = latencyMs
        ))
            .sortedBy { it.elapsedMs }
            .takeLast(2400)
    }

    fun startPingMonitor(reset: Boolean = false, targetOverride: String? = null) {
        val rawTarget = targetOverride?.trim()?.takeIf { it.isNotBlank() } ?: pingTarget.trim().ifBlank { host.ifBlank { "223.5.5.5" } }
        val normalizedTarget = normalizeNetworkTargetInput(rawTarget, "223.5.5.5")
        if (normalizedTarget.error != null) {
            pingRunning = false
            pingIntervalLabel = normalizedTarget.error
            pingActiveTargetLabel = rawTarget
            scope.launch { snackbarHostState.showSnackbar(normalizedTarget.error) }
            return
        }
        val target = normalizedTarget.host
        val interval = safePingIntervalMs()
        val timeout = safePingTimeoutMs()
        val maxCount = safePingCount()
        val requestedProtocol = pingProtocolSetting
        val sessionId = System.currentTimeMillis()
        pingJob?.cancel()
        activePingSessionId = sessionId
        if (reset) {
            pingPoints = emptyList()
            pingJitterMs = null
            pingSessionStartedAt = 0L
            pingSessionEndedAt = 0L
            pingDurationTick = sessionId
        }
        pingRunning = true
        pingIntervalLabel = "准备中"
        pingActiveTargetLabel = target
        pingTargetHistory = rememberTargetHistoryItem(context.applicationContext, "ping_target_history_v1", target)
        appendPingLog(PingLogEntry(
            timeEpochMs = sessionId,
            target = target,
            protocol = requestedProtocol.label,
            latencyMs = null,
            status = "开始",
            note = "间隔${interval}ms · 超时${timeout}ms",
            sessionId = sessionId
        ))
        pingJob = scope.launch {
            val resolved = resolvePingTarget(target, requestedProtocol)
            if (resolved.error != null) {
                pingRunning = false
                pingIntervalLabel = resolved.error
                pingActiveTargetLabel = "$target · ${resolved.displayProtocol}"
                appendPingLog(PingLogEntry(target = target, protocol = resolved.displayProtocol, latencyMs = null, status = resolved.error, note = "未开始", sessionId = sessionId))
                return@launch
            }
            pingIntervalLabel = "${resolved.displayProtocol} · ${interval}ms"
            pingActiveTargetLabel = "$target · ${resolved.displayProtocol}"
            var startedAt = sessionId
            var sent = 0
            val highFrequency = interval < 200L
            val bucketSizeMs = if (highFrequency) 250L else 1_000L
            var currentBucketMs = 0L
            val bucketSamples = mutableListOf<Int?>()
            val pendingLogs = mutableListOf<PingLogEntry>()
            val jitterWindow = RttJitterWindow(maxSize = 50)
            var consecutiveLossStartedAt = 0L
            var autoInterruptedByLoss = false

            fun markOfficialStart() {
                val official = System.currentTimeMillis()
                startedAt = official
                currentBucketMs = 0L
                pingSessionStartedAt = official
                pingSessionEndedAt = 0L
                pingDurationTick = official
            }

            fun flushBucket(bucketMs: Long) {
                if (activePingSessionId != sessionId) {
                    bucketSamples.clear()
                    pendingLogs.clear()
                    return
                }
                if (bucketSamples.isNotEmpty()) {
                    appendPingBucket(bucketMs, bucketSamples.toList())
                    bucketSamples.clear()
                }
                if (pendingLogs.isNotEmpty()) {
                    appendPingLogs(pendingLogs.toList())
                    pendingLogs.clear()
                }
            }

            fun handlePingResult(latency: Int?, failure: String?, eventTime: Long = System.currentTimeMillis()) {
                if (activePingSessionId != sessionId) return
                val elapsedMs = (eventTime - startedAt).coerceAtLeast(0L)
                val bucketMs = (elapsedMs / bucketSizeMs) * bucketSizeMs
                if (bucketMs != currentBucketMs) {
                    flushBucket(currentBucketMs)
                    currentBucketMs = bucketMs
                }
                sent++
                if (latency != null) {
                    jitterWindow.onSuccess(latency.toDouble())
                    pingJitterMs = jitterWindow.currentJitterMs()
                }
                bucketSamples.add(latency)
                if (latency == null) {
                    if (consecutiveLossStartedAt == 0L) consecutiveLossStartedAt = eventTime
                    if (!autoInterruptedByLoss && eventTime - consecutiveLossStartedAt >= 5_000L) {
                        autoInterruptedByLoss = true
                        pendingLogs.add(PingLogEntry(
                            timeEpochMs = eventTime,
                            target = target,
                            protocol = resolved.displayProtocol,
                            latencyMs = null,
                            status = "中断",
                            note = "连续5秒100%丢包，已自动停止并保存记录",
                            sessionId = sessionId,
                            elapsedMs = elapsedMs
                        ))
                        pingJob?.cancel(CancellationException("连续5秒100%丢包"))
                    }
                } else {
                    consecutiveLossStartedAt = 0L
                }
                val status = when {
                    latency == null -> failure ?: "超时"
                    latency >= 100 -> "高延迟"
                    else -> "成功"
                }
                pendingLogs.add(PingLogEntry(
                    timeEpochMs = eventTime,
                    target = target,
                    protocol = resolved.displayProtocol,
                    latencyMs = latency,
                    status = status,
                    note = if (latency == null) (failure ?: "timeout") else "",
                    sessionId = sessionId,
                    elapsedMs = elapsedMs
                ))
            }

            try {
                val finiteCount = maxCount
                if (interval < 200L) {
                    val tcpProbe = findTcpPingPort(resolved.address, timeout)
                    if (tcpProbe != null) {
                        val tcpProtocol = "${resolved.displayProtocol} · TCP:${tcpProbe.port}"
                        pingIntervalLabel = "TCP高频${interval}ms"
                        pingActiveTargetLabel = "$target · $tcpProtocol"
                        pendingLogs.add(PingLogEntry(
                            target = target,
                            protocol = tcpProtocol,
                            latencyMs = tcpProbe.latencyMs,
                            status = "TCP高频",
                            note = "普通APP无法使用ICMP Raw Socket，已用TCP Socket高频探测",
                            sessionId = sessionId
                        ))
                        markOfficialStart()
                        var scheduled = 0
                        var inFlight = 0
                        var nextTick = SystemClock.elapsedRealtime()
                        val tcpTimeout = timeout.coerceIn(180, 5_000)
                        val maxInFlight = pingMaxInflight(interval, tcpTimeout)
                        var skippedByInflight = 0
                        val finiteJobs = mutableListOf<Job>()
                        while (currentCoroutineContext().isActive && (finiteCount == null || scheduled < finiteCount)) {
                            val waitMs = nextTick - SystemClock.elapsedRealtime()
                            if (waitMs > 0L) delay(waitMs)
                            nextTick += interval
                            if (inFlight >= maxInFlight) {
                                skippedByInflight++
                                if (skippedByInflight == 1 || skippedByInflight % 50 == 0) {
                                    pendingLogs.add(PingLogEntry(target = target, protocol = tcpProtocol, latencyMs = null, status = "跳过", note = "并发已满：${maxInFlight}，主动跳过，不计入丢包", sessionId = sessionId))
                                }
                                continue
                            }
                            scheduled++
                            inFlight++
                            val job = launch {
                                try {
                                    val result = tcpSocketPingResolved(resolved.address, tcpProbe.port, tcpTimeout)
                                    handlePingResult(result.latencyMs, result.failure, System.currentTimeMillis())
                                } finally {
                                    inFlight = (inFlight - 1).coerceAtLeast(0)
                                }
                            }
                            if (finiteCount != null) finiteJobs.add(job)
                        }
                        finiteJobs.forEach { it.join() }
                    } else {
                        pingIntervalLabel = "ICMP高频${interval}ms"
                        markOfficialStart()
                        pendingLogs.add(PingLogEntry(
                            target = target,
                            protocol = resolved.displayProtocol,
                            latencyMs = null,
                            status = "TCP端口未发现",
                            note = "已回退系统ping流式探测；实际频率受Android ping命令限制",
                            sessionId = sessionId
                        ))
                        while (currentCoroutineContext().isActive && (finiteCount == null || sent < finiteCount)) {
                            val remaining = finiteCount?.let { (it - sent).coerceAtLeast(0) } ?: 20_000
                            if (remaining <= 0) break
                            val chunk = remaining.coerceAtMost(20_000)
                            val before = sent
                            val streamed = streamIcmpPingResolved(resolved.address, timeout, resolved.protocol, interval, chunk) { event ->
                                handlePingResult(event.latencyMs, event.failure, event.timeEpochMs)
                            }
                            if (streamed <= 0 || sent == before) {
                                pendingLogs.add(PingLogEntry(
                                    target = target,
                                    protocol = resolved.displayProtocol,
                                    latencyMs = null,
                                    status = "高频受限",
                                    note = "系统ping不支持该频率，已降级串行ICMP",
                                    sessionId = sessionId
                                ))
                                val loopStart = System.currentTimeMillis()
                                val result = icmpPingResolved(resolved.address, timeout, resolved.protocol)
                                handlePingResult(result.latencyMs, result.failure, System.currentTimeMillis())
                                val cost = System.currentTimeMillis() - loopStart
                                delay((interval - cost).coerceAtLeast(0L))
                            }
                        }
                    }
                } else {
                    markOfficialStart()
                    while (currentCoroutineContext().isActive && (maxCount == null || sent < maxCount)) {
                        val loopStart = System.currentTimeMillis()
                        val result = icmpPingResolved(resolved.address, timeout, resolved.protocol)
                        handlePingResult(result.latencyMs, result.failure, System.currentTimeMillis())
                        val cost = System.currentTimeMillis() - loopStart
                        delay((interval - cost).coerceAtLeast(0L))
                    }
                }
            } finally {
                if (activePingSessionId == sessionId) {
                    flushBucket(currentBucketMs)
                    pingRunning = false
                    pingSessionEndedAt = System.currentTimeMillis()
                    pingDurationTick = pingSessionEndedAt
                    pingIntervalLabel = if (sent > 0) "已停止 · ${sent}次" else "停止"
                    appendPingLog(PingLogEntry(target = target, protocol = resolved.displayProtocol, latencyMs = null, status = "停止", note = "共${sent}次 · 时长${formatPingDuration((pingSessionEndedAt - startedAt).coerceAtLeast(0L))}", sessionId = sessionId, elapsedMs = (pingSessionEndedAt - startedAt).coerceAtLeast(0L)))
                }
            }
        }
    }

    fun stopPingMonitor(reason: String = "手动停止") {
        val wasRunning = pingRunning
        val sessionId = activePingSessionId
        val stoppedAt = System.currentTimeMillis()
        pingJob?.cancel()
        pingJob = null
        pingRunning = false
        pingSessionEndedAt = stoppedAt
        pingDurationTick = pingSessionEndedAt
        pingIntervalLabel = if (reason == "手动停止") "已停止" else "已中断"
        if (wasRunning && reason != "手动停止" && sessionId != 0L) {
            appendPingLog(PingLogEntry(
                timeEpochMs = stoppedAt,
                target = pingTarget.ifBlank { pingActiveTargetLabel.ifBlank { "Ping" } },
                protocol = pingProtocolSetting.label,
                latencyMs = null,
                status = "中断",
                note = reason,
                sessionId = sessionId
            ))
        }
    }

    fun clearPingData() {
        pingPoints = emptyList()
        displayPingPoints = emptyList()
        pingLogs = emptyList()
        displayPingLogCount = 0
        pingJitterMs = null
        displayPingJitterMs = null
        pingSessionStartedAt = 0L
        pingSessionEndedAt = 0L
        pingDurationTick = System.currentTimeMillis()
        persistPingLogs(emptyList())
        pingIntervalLabel = if (pingRunning) "${pingProtocolSetting.label} · ${safePingIntervalMs()}ms" else "停止"
    }

    fun deletePingLogSession(sessionId: Long) {
        val next = trimPingLogSessions(pingLogs.filterNot { it.sessionId == sessionId || (it.sessionId == 0L && it.timeEpochMs == sessionId) })
        pingLogs = next
        displayPingLogCount = next.size
        persistPingLogs(next)
        scope.launch { snackbarHostState.showSnackbar("已删除 1 条 Ping 历史") }
    }

    DisposableEffect(context, pingRunning, state.isAdding) {
        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && pingRunning && !state.isAdding) {
                    // 准确优先：普通 Ping 测试退后台/锁屏后不伪装连续数据，直接中断并保存部分记录。
                    stopPingMonitor("APP进入后台/锁屏，准确优先已中断；后台区间不计入统计")
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun buildConfig(): SessionConfig? {
        val normalizedHost = normalizeNetworkTargetInput(host.ifBlank { "www.baidu.com" }, "www.baidu.com")
        if (normalizedHost.error != null) {
            scope.launch { snackbarHostState.showSnackbar(normalizedHost.error) }
            return null
        }
        val config = runCatching {
            SessionConfig(
                host = normalizedHost.host,
                port = normalizedHost.port ?: (port.toIntOrNull() ?: 80),
                mode = mode,
                batchSize = batchSize.toIntOrNull() ?: 200,
                intervalMs = intervalMs.toLongOrNull() ?: 100L,
                timeoutMs = timeoutMs.toIntOrNull() ?: 1200,
                successLimit = successLimit.toIntOrNull() ?: 65535,
                failureLimit = failureLimit.toIntOrNull() ?: 600,
                keepConnectionsAfterStop = true
            ).normalized()
        }.getOrElse { error ->
            scope.launch { snackbarHostState.showSnackbar("参数错误：${error.message}") }
            return null
        }
        return config
    }

    fun resolve() {
        val target = host.ifBlank { "www.baidu.com" }
        scope.launch {
            appendLog(LogLine(level = LogLevel.INFO, text = "开始解析：$target"))
            val result = tester.resolveHost(target)
            state = state.copy(resolveResult = result)
            if (result.error != null) {
                appendLog(LogLine(level = LogLevel.ERROR, text = "解析失败：${result.error}"))
            } else {
                appendLog(LogLine(level = LogLevel.SUCCESS, text = "IPv4：${result.ipv4.joinToString(" / ").ifBlank { "无" }}"))
                appendLog(LogLine(level = LogLevel.SUCCESS, text = "IPv6：${result.ipv6.joinToString(" / ").ifBlank { "无" }}"))
            }
        }
    }

    fun hasFdLimit(stats: ProtocolStats?): Boolean {
        if (stats == null) return false
        return stats.phase.contains("FD", ignoreCase = true) ||
            stats.errorSummary.keys.any { it.contains("FD", ignoreCase = true) }
    }

    fun hasFdLimit(summary: SessionSummary?): Boolean {
        if (summary == null) return false
        return hasFdLimit(summary.ipv4Stats) || hasFdLimit(summary.ipv6Stats)
    }

    suspend fun appendHistorySafely(summary: SessionSummary) {
        // 注意：统一收尾函数会先 detach/清空 heldSockets，再调用这里。
        // 保存历史失败不能影响释放状态。
        runCatching {
            historyStore.append(summary)
            historyStore.trim(100)
        }.onFailure { error ->
            appendLog(LogLine(level = LogLevel.ERROR, text = "保存历史失败：${error.message ?: error.javaClass.simpleName}"))
        }
    }

    fun stoppedStatsForNetworkChange(current: ProtocolStats, reason: String): ProtocolStats {
        return current.copy(
            phase = reason,
            errorSummary = current.errorSummary + (reason to 1)
        )
    }

    fun buildNetworkInterruptedSummary(reason: String): SessionSummary? {
        val config = currentTestConfig ?: buildConfig() ?: return null
        val ipv4 = when (config.mode) {
            TestMode.IPV4_ONLY -> stoppedStatsForNetworkChange(state.ipv4Stats, reason)
            TestMode.IPV4_THEN_IPV6 -> if (state.ipv4Stats.totalAttempts > 0) stoppedStatsForNetworkChange(state.ipv4Stats, reason) else null
            TestMode.IPV6_ONLY -> null
        }
        val ipv6 = when (config.mode) {
            TestMode.IPV6_ONLY -> stoppedStatsForNetworkChange(state.ipv6Stats, reason)
            TestMode.IPV4_THEN_IPV6 -> if (state.ipv6Stats.totalAttempts > 0) stoppedStatsForNetworkChange(state.ipv6Stats, reason) else null
            TestMode.IPV4_ONLY -> null
        }
        return SessionSummary(
            startedAtEpochMs = if (currentStartedAt > 0L) currentStartedAt else System.currentTimeMillis(),
            host = config.host,
            port = config.port,
            mode = config.mode,
            ipv4Stats = ipv4,
            ipv6Stats = ipv6
        )
    }

    fun stoppedStatsFor(protocol: IpProtocol, current: ProtocolStats, reason: String = "手动停止"): ProtocolStats {
        return current.copy(
            phase = reason,
            errorSummary = current.errorSummary + (reason to 1)
        )
    }

    fun buildStoppedSummary(reason: String): SessionSummary? {
        val config = currentTestConfig ?: buildConfig() ?: return null
        val ipv4 = when (config.mode) {
            TestMode.IPV4_ONLY -> stoppedStatsFor(IpProtocol.IPV4, state.ipv4Stats, reason)
            TestMode.IPV4_THEN_IPV6 -> if (state.ipv4Stats.totalAttempts > 0) stoppedStatsFor(IpProtocol.IPV4, state.ipv4Stats, reason) else null
            TestMode.IPV6_ONLY -> null
        }
        val ipv6 = when (config.mode) {
            TestMode.IPV6_ONLY -> stoppedStatsFor(IpProtocol.IPV6, state.ipv6Stats, reason)
            TestMode.IPV4_THEN_IPV6 -> if (state.ipv6Stats.totalAttempts > 0) stoppedStatsFor(IpProtocol.IPV6, state.ipv6Stats, reason) else null
            TestMode.IPV4_ONLY -> null
        }
        return SessionSummary(
            startedAtEpochMs = if (currentStartedAt > 0L) currentStartedAt else System.currentTimeMillis(),
            host = config.host,
            port = config.port,
            mode = config.mode,
            ipv4Stats = ipv4,
            ipv6Stats = ipv6
        )
    }

    fun finishReasonFor(summary: SessionSummary?, config: SessionConfig?): FinishReason {
        if (summary == null) return FinishReason.Interrupted
        if (hasFdLimit(summary)) return FinishReason.FdLimit
        val stats = listOfNotNull(summary.ipv4Stats, summary.ipv6Stats)
        if (stats.isNotEmpty() && stats.all { it.phase == "解析失败" || it.errorSummary.keys.any { key -> key.contains("DNS") } }) {
            return FinishReason.DnsFail
        }
        if (stats.any { it.phase.contains("无增长") }) return FinishReason.NoGrowth
        if (stats.any { it.phase.contains("连续失败") }) return FinishReason.ConsecutiveFailure
        if (stats.any { it.phase.contains("失败上限") }) return FinishReason.FailureLimit
        return FinishReason.Completed
    }

    suspend fun releaseAndFinalize(
        reason: FinishReason,
        summary: SessionSummary?,
        saveHistory: Boolean = reason.saveHistory,
        cancelRunningJob: Boolean = true,
        toast: Boolean = true
    ) {
        if (finishInProgress && reason != FinishReason.ForceRelease) return
        finishInProgress = true
        manualStopRequested = true

        val currentJob = currentCoroutineContext()[Job]
        if (cancelRunningJob && runningJob != currentJob) {
            runningJob?.cancel()
            runningJob = null
        }
        alignPingWithSessionEnd()
        if (pingJob != currentJob) pingJob?.cancel()
        pingJob = null
        pingIntervalLabel = "AUTO"
        if (networkWatchJob != currentJob) networkWatchJob?.cancel()
        networkWatchJob = null
        activeRunId = 0L
        currentStartedAt = 0L

        val snapshot = tester.detachForRelease()
        val finalStatus = if (reason == FinishReason.ForceRelease) "已释放" else "${reason.label} · 已释放"
        val releaseStatus = if (reason == FinishReason.ForceRelease) "正在释放" else "${reason.label} · 正在释放"
        val baseIpv4 = summary?.ipv4Stats ?: state.ipv4Stats
        val baseIpv6 = summary?.ipv6Stats ?: state.ipv6Stats
        val releaseStart = System.currentTimeMillis()
        var lastReleaseLogAt = 0L

        state = state.copy(
            isAdding = false,
            runPhase = RunPhase.Releasing,
            status = releaseStatus,
            summary = summary ?: state.summary,
            error = null,
            releaseUi = ReleaseUiState(
                visible = true,
                total = snapshot.size,
                closed = 0,
                speedPerSecond = 0,
                elapsedMs = 0L,
                message = if (snapshot.isEmpty()) "没有需要释放的连接" else "正在关闭 Socket 连接，请勿退出页面"
            )
        )

        appendLog(LogLine(level = LogLevel.WARN, text = "${reason.label}：已停止新增，开始释放 ${snapshot.size} 条 socket"))
        updateForegroundNotice(context, "正在释放连接 0/${snapshot.size}")
        // 先让 Releasing 状态和释放进度卡片完成一次渲染，再开始批量 close。
        // 避免停止瞬间和 IPv4->IPv6 切换时出现 UI 短时卡顿/假死感。
        delay(80L)

        var closed = 0
        try {
            closed = runCatching {
                tester.closeDetachedSockets(snapshot, batchSize = 512, workerCount = 6, progressIntervalMs = 300L) { done, total, elapsedMs ->
                    val elapsed = elapsedMs.coerceAtLeast(1L)
                    val speed = if (done <= 0) 0 else (done * 1000L / elapsed).toInt().coerceAtLeast(1)
                    state = state.copy(
                        releaseUi = ReleaseUiState(
                            visible = true,
                            total = total,
                            closed = done,
                            speedPerSecond = speed,
                            elapsedMs = elapsedMs,
                            message = if (done >= total) "释放完成，正在更新界面状态" else "正在关闭 Socket 连接，请勿退出页面",
                            finished = done >= total
                        )
                    )
                    val now = System.currentTimeMillis()
                    if (now - lastReleaseLogAt >= 1_000L || done >= total) {
                        lastReleaseLogAt = now
                        val percent = if (total <= 0) 100 else (done * 100 / total).coerceIn(0, 100)
                        appendLog(LogLine(level = LogLevel.STAT, text = "释放进度：$done/$total，$percent%，速度约 ${speed}/秒"))
                        updateForegroundNotice(context, "正在释放连接 $done/$total｜$percent%")
                    }
                }
            }.getOrElse { error ->
                appendLog(LogLine(level = LogLevel.ERROR, text = "${reason.label}：close 异常：${error.message ?: error.javaClass.simpleName}"))
                0
            }

            appendLog(LogLine(level = LogLevel.WARN, text = "${reason.label}：close 完成：$closed 条，耗时 ${((System.currentTimeMillis() - releaseStart) / 1000f).let { String.format("%.1f", it) }} 秒"))

            if (saveHistory && summary != null) {
                appendHistorySafely(summary)
                refreshHistory()
            }
        } finally {
            val releaseElapsedMs = System.currentTimeMillis() - releaseStart
            val failed = reason != FinishReason.Completed && reason != FinishReason.ForceRelease
            state = state.copy(
                isAdding = false,
                runPhase = if (failed) RunPhase.Failed else RunPhase.Finished,
                status = finalStatus,
                summary = summary ?: state.summary,
                error = if (failed) reason.label else null,
                releaseUi = state.releaseUi.copy(
                    visible = true,
                    closed = state.releaseUi.total.takeIf { it > 0 } ?: closed,
                    elapsedMs = releaseElapsedMs,
                    finished = true,
                    message = "释放完成"
                ),
                ipv4Stats = baseIpv4.copy(activeSessions = 0, phase = if (baseIpv4.totalAttempts > 0) "已释放" else baseIpv4.phase),
                ipv6Stats = baseIpv6.copy(activeSessions = 0, phase = if (baseIpv6.totalAttempts > 0) "已释放" else baseIpv6.phase)
            )
            notifyLocalReleased(if (reason == FinishReason.ForceRelease) "本机已释放" else "${reason.label}，本机已释放")
            // 释放完成通知统一走底部白色浮层，避免同时出现系统 Toast 和黑色 Snackbar。
            stopForegroundNotice(context)
            finishInProgress = false
        }
    }

    suspend fun interruptForNetworkChange(reason: String = "网络环境变化") {
        if (!state.isAdding) return
        val summary = buildNetworkInterruptedSummary(reason)
        appendLog(LogLine(level = LogLevel.ERROR, text = "$reason，已中断测试并保存历史。"))
        releaseAndFinalize(
            reason = FinishReason.NetworkChange,
            summary = summary,
            saveHistory = true,
            cancelRunningJob = true
        )
    }

    fun startNetworkWatch(startedAt: Long, signature: String) {
        networkWatchJob?.cancel()
        networkWatchJob = scope.launch {
            delay(1500L)
            var consecutiveChanged = 0
            while (currentStartedAt == startedAt && state.isAdding) {
                val now = currentNetworkSignature(context)
                if (now != signature) {
                    consecutiveChanged++
                    if (consecutiveChanged >= 2) {
                        interruptForNetworkChange("网络环境变化")
                        break
                    }
                } else {
                    consecutiveChanged = 0
                }
                delay(1000L)
            }
        }
    }

    fun startTest() {
        val config = buildConfig() ?: return
        hostHistory = rememberTargetHistoryItem(context.applicationContext, "tcp_target_history_v1", config.host)
        ensureNotificationPermission()
        runningJob?.cancel()
        selectedTab = MainTab.TEST
        startForegroundNotice(context, "建连中：${config.mode.label}，目标 ${config.successLimit}")
        val startedAt = System.currentTimeMillis()
        currentStartedAt = startedAt
        activeRunId = startedAt
        currentTestConfig = config
        testNetworkSignature = currentNetworkSignature(context)
        resetCurrentCharts()
        // 性能发布版：测试开始时不再触发公网/IP/STUN刷新，避免抢占网络与 IO。
        manualStopRequested = false
        state = state.copy(
            isAdding = true,
            runPhase = RunPhase.Running,
            status = "建连中",
            releaseUi = ReleaseUiState(),
            ipv4Stats = ProtocolStats(IpProtocol.IPV4),
            ipv6Stats = ProtocolStats(IpProtocol.IPV6),
            summary = null,
            error = null
        )
        appendLog(LogLine(level = LogLevel.INFO, text = "目标：${config.host}:${config.port} | 模式：${config.mode.label} | 目标CPS：${config.batchSize}/s | 调度间隔：${config.intervalMs}ms | 固定CPS核心"))
        // 连接数测试优先级高于 Ping：开始连接数测试时自动停止旧 Ping，并重新开始一轮同步 Ping 监测。
        if (pingEnabled) startPingMonitor(reset = true, targetOverride = config.host)
        startNetworkWatch(startedAt, testNetworkSignature)

        runningJob = scope.launch {
            var summary: SessionSummary? = null
            var completedNormally = false
            var failureMsg: String? = null
            try {
                val oldClosed = tester.release()
                if (oldClosed > 0) {
                    appendLog(LogLine(level = LogLevel.WARN, text = "开始新测试前释放旧连接：$oldClosed 条"))
                }
                val pair = tester.runSessionHoldTest(
                    rawConfig = config,
                    onStats = statsHandler@ { stats ->
                        if (activeRunId != startedAt || !state.isAdding) return@statsHandler
                        recordChartPoint(stats)
                        val nextPhase = if (stats.phase.contains("无增长") || stats.phase.contains("确认")) RunPhase.TopConfirm else RunPhase.Running
                        state = when (stats.protocol) {
                            IpProtocol.IPV4 -> state.copy(ipv4Stats = stats, status = "${stats.protocol.label} ${stats.phase}", runPhase = nextPhase)
                            IpProtocol.IPV6 -> state.copy(ipv6Stats = stats, status = "${stats.protocol.label} ${stats.phase}", runPhase = nextPhase)
                        }
                        updateForegroundNotice(context, "${stats.protocol.label} ${stats.phase}｜活动 ${stats.activeSessions}｜失败 ${stats.totalFailure}")
                    },
                    onLog = { line -> appendLog(line) }
                )
                summary = SessionSummary(
                    startedAtEpochMs = startedAt,
                    host = config.host,
                    port = config.port,
                    mode = config.mode,
                    ipv4Stats = when (config.mode) {
                        TestMode.IPV4_ONLY -> pair.first ?: state.ipv4Stats
                        TestMode.IPV4_THEN_IPV6 -> pair.first ?: state.ipv4Stats
                        TestMode.IPV6_ONLY -> null
                    },
                    ipv6Stats = when (config.mode) {
                        TestMode.IPV6_ONLY -> pair.second ?: state.ipv6Stats
                        TestMode.IPV4_THEN_IPV6 -> pair.second ?: state.ipv6Stats
                        TestMode.IPV4_ONLY -> null
                    }
                )
                completedNormally = true
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (!manualStopRequested) {
                    failureMsg = error.message ?: error.javaClass.simpleName
                    appendLog(LogLine(level = LogLevel.ERROR, text = "测试中断：$failureMsg"))
                }
            } finally {
                alignPingWithSessionEnd()
                pingJob?.cancel()
                pingJob = null
                pingIntervalLabel = "AUTO"
                networkWatchJob?.cancel()
                networkWatchJob = null
                if (!manualStopRequested) {
                    val finalSummary = summary
                    val finalReason = finishReasonFor(finalSummary, config).let { reason ->
                        if (!completedNormally && reason == FinishReason.Completed) FinishReason.Interrupted else reason
                    }
                    scope.launch {
                        releaseAndFinalize(
                            reason = finalReason,
                            summary = finalSummary,
                            saveHistory = finalReason.saveHistory,
                            cancelRunningJob = false,
                            toast = true
                        )
                    }
                }
                runningJob = null
            }
        }
    }

    fun stopAdding() {
        val summary = buildStoppedSummary("手动停止")
        appendLog(LogLine(level = LogLevel.WARN, text = "手动停止；统一收尾，先释放再保存历史。"))
        scope.launch {
            releaseAndFinalize(
                reason = FinishReason.ManualStop,
                summary = summary,
                saveHistory = true,
                cancelRunningJob = true
            )
        }
    }

    fun releaseAll() {
        val wasRunning = state.isAdding
        val summary = if (wasRunning) buildStoppedSummary("强制释放") else null
        appendLog(LogLine(level = LogLevel.WARN, text = "强制释放；统一收尾，立即清空 UI 状态和连接。"))
        scope.launch {
            releaseAndFinalize(
                reason = FinishReason.ForceRelease,
                summary = summary,
                saveHistory = false,
                cancelRunningJob = true
            )
        }
    }

    fun exportLogs() {
        val logs = if (maskPrivacy) maskedLogs(state.logs) else state.logs
        val summary = state.summary?.let { if (maskPrivacy) maskedSummary(it) else it }
        pendingCsv = summary?.let { CsvExporter.summaryCsv(it, logs) } ?: CsvExporter.logsCsv(logs)
        exportLauncher.launch("net-session-test-v06.csv")
    }

    fun clearHistoryOnly() {
        scope.launch {
            historyStore.clear()
            historySizeKb = 0
            historySavedCount = 0
            historyCounts = HistoryCounts()
            state = state.copy(history = emptyList())
            snackbarHostState.showSnackbar("检测历史已清理")
        }
    }

    fun deleteHistoryItem(summary: SessionSummary) {
        scope.launch {
            historyStore.delete(summary.id)
            val safeLimit = historyLimit.toIntOrNull()?.coerceIn(10, 100) ?: 30
            applyHistoryUiSnapshot(loadHistoryUiSnapshot(historyPeriod, safeLimit))
            if (detailSummary?.id == summary.id) detailSummary = null
            snackbarHostState.showSnackbar("已删除 1 条检测历史")
        }
    }


    if (editingRemarkSummary != null) {
        AlertDialog(
            onDismissRequest = { editingRemarkSummary = null },
            confirmButton = {
                TextButton(onClick = {
                    val item = editingRemarkSummary
                    if (item != null) {
                        scope.launch {
                            historyStore.updateRemark(item.id, editingRemarkText)
                            val safeHistoryLimit = historyLimit.toIntOrNull()?.coerceIn(10, 100) ?: 30
                            applyHistoryUiSnapshot(loadHistoryUiSnapshot(historyPeriod, safeHistoryLimit))
                            snackbarHostState.showSnackbar("备注已保存")
                        }
                    }
                    editingRemarkSummary = null
                }) { Text("保存", fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { editingRemarkSummary = null }) { Text("取消", fontSize = 13.sp) }
            },
            title = { Text("编辑备注", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editingRemarkText,
                    onValueChange = { editingRemarkText = it.take(120) },
                    placeholder = { Text("输入备注，例如：晚高峰测试", fontSize = 12.sp) },
                    minLines = 2,
                    maxLines = 4,
                    shape = ShapeM,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    if (detailSummary != null) {
        HistoryDetailDialog(summary = detailSummary!!, maskPrivacy = maskPrivacy, onDismiss = { detailSummary = null })
    }

    if (showPingLogDialog) {
        PingLogDialog(logs = pingLogs, onDismiss = { showPingLogDialog = false })
    }

    if (showNatHistoryDialog) {
        NatHistoryDialog(
            records = natHistory,
            onDismiss = { showNatHistoryDialog = false }
        )
    }

    if (showNatDiagnosticDialog) {
        NatDiagnosticDialog(
            mode = natManualMode,
            servers = if (natManualMode == ManualNatMode.RFC5780) natRfc5780Servers else natRfc3489Servers,
            running = natDiagnosticRunning,
            progressText = natDiagnosticProgress,
            result = natDiagnosticResult,
            onOpenHistory = {
                showNatDiagnosticDialog = false
                showNatHistoryDialog = true
            },
            onModeChange = { mode ->
                natManualMode = mode
                natDiagnosticResult = null
                natDiagnosticProgress = ""
            },
            onServersChange = { next ->
                if (natManualMode == ManualNatMode.RFC5780) natRfc5780Servers = next else natRfc3489Servers = next
            },
            onRun = {
                if (!natDiagnosticRunning) {
                    scope.launch {
                        natDiagnosticRunning = true
                        natDiagnosticProgress = "准备检测 · IPv4 · UDP"
                        val startedAt = SystemClock.elapsedRealtime()
                        val modeNow = natManualMode
                        val serverNow = if (modeNow == ManualNatMode.RFC5780) natRfc5780Servers else natRfc3489Servers
                        val result = withContext(Dispatchers.IO) {
                            manualNatProbe(modeNow, serverNow) { message -> scope.launch { natDiagnosticProgress = message } }
                        }
                        val durationMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
                        natDiagnosticResult = result
                        natDiagnosticProgress = if (result.success) "检测完成 · ${result.method} · ${result.server}" else "检测失败 · ${result.message}"
                        networkProbeInfo = networkProbeInfo.copy(
                            natType = result.natType,
                            portText = result.publicAddress.substringAfter(':', networkProbeInfo.portText),
                            mappingBehavior = result.mappingBehavior,
                            filterBehavior = result.filteringBehavior,
                            confidence = if (result.success) if (result.method == "RFC5780") "高" else "中" else "低",
                            diagnosis = if (result.success) "手动NAT诊断完成：${result.method} · ${result.server}。${result.message}" else "手动NAT诊断失败：${result.message}",
                            refreshMode = if (result.success) "已检测" else "待检测"
                        )
                        if (result.success) {
                            natHistory = saveNatHistory(
                                context.applicationContext,
                                NatHistoryRecord(
                                    id = System.currentTimeMillis(),
                                    timeText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                                    mode = modeNow.label,
                                    natType = result.natType,
                                    mappingBehavior = standardMappingText(result.mappingBehavior),
                                    filteringBehavior = standardFilteringText(result.filteringBehavior),
                                    localAddress = result.localAddress,
                                    publicAddress = result.publicAddress,
                                    method = result.method,
                                    server = result.server,
                                    message = result.message,
                                    durationMs = durationMs
                                )
                            )
                        }
                        natDiagnosticRunning = false
                    }
                }
            },
            onDismiss = { showNatDiagnosticDialog = false }
        )
    }

    if (detailTitle != null) {
        AlertDialog(
            onDismissRequest = { detailTitle = null },
            confirmButton = {
                TextButton(onClick = { detailTitle = null }) { Text("关闭", fontSize = 13.sp) }
            },
            title = { Text(detailTitle ?: "", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    detailLines.forEach { Text(it, color = TextDark, fontSize = 12.sp) }
                }
            }
        )
    }

    if (showVersionDialog) {
        VersionInfoDialog(
            checkingUpdate = checkingUpdate,
            updateAvailable = updateAvailable,
            onCheckUpdate = { checkForUpdate(manual = true) },
            onDismiss = { showVersionDialog = false },
            onOpenGithub = { openGithub(PROJECT_GITHUB_URL) }
        )
    }

    latestUpdate?.let { info ->
        if (showUpdateDialog) {
            UpdateAvailableDialog(
                info = info,
                onDismiss = { showUpdateDialog = false },
                onIgnore = { ignoreUpdate(info) },
                onOpenGithub = { openGithub(info.githubUrl.ifBlank { PROJECT_GITHUB_URL }) },
                onUpdateNow = { startUpdateDownload(info) }
            )
        }
    }

    if (showDownloadDialog) {
        UpdateDownloadDialog(
            info = latestUpdate,
            state = downloadUi,
            onDismiss = { closeUpdateDownloadDialog() },
            onInstall = { installReadyApk() },
            onRetry = { latestUpdate?.let { startUpdateDownload(it) } },
            onCancelDownload = { cancelUpdateDownload() },
            onOpenGithub = { openGithub(latestUpdate?.githubUrl ?: PROJECT_GITHUB_URL) }
        )
    }


    Scaffold(
        snackbarHost = { OneUiSnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!showRunLogDetail && appToolPage == AppToolPage.NONE) {
                BottomNav(selectedTab = selectedTab, onSelect = {
                    showRunLogDetail = false
                    selectedTab = it
                })
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgTop, Bg, Color.White)))
                .padding(padding)
        ) {
            if (showRunLogDetail) {
                FullRunLogPage(
                    logs = state.logs,
                    logSizeKb = logSizeKb,
                    maskPrivacy = maskPrivacy,
                    onBack = { showRunLogDetail = false },
                    onExport = { exportLogs() },
                    onClear = {
                        scope.launch {
                            logStore.clear()
                            logSizeKb = 0
                            state = state.copy(logs = emptyList())
                        }
                    }
                )
            } else when (appToolPage) {
                AppToolPage.NSLOOKUP -> NsLookupToolPage(onBack = { appToolPage = AppToolPage.NONE })
                AppToolPage.TRACKET -> TracketToolPage(onBack = { appToolPage = AppToolPage.NONE })
                AppToolPage.MTU -> MtuToolPage(onBack = { appToolPage = AppToolPage.NONE })
                AppToolPage.ROAMING -> RoamingToolPage(onBack = { appToolPage = AppToolPage.NONE })
                AppToolPage.IPV6_DIAGNOSTIC -> Ipv6DiagnosticToolPage(onBack = { appToolPage = AppToolPage.NONE })
                AppToolPage.PING_HISTORY -> PingHistoryToolPage(logs = pingLogs, onBack = { appToolPage = AppToolPage.NONE }, onDeleteSession = { sessionId -> deletePingLogSession(sessionId) })
                AppToolPage.NONE -> when (selectedTab) {
                MainTab.SETTINGS -> SettingsPage(
                    listState = settingsListState,
                    networkInfoExpanded = networkInfoExpanded,
                    onNetworkInfoExpandedChange = { networkInfoExpanded = it },
                    host = host,
                    onHostChange = { host = it },
                    hostHistory = hostHistory,
                    onPickHostHistory = { host = it },
                    onDeleteHostHistory = { item ->
                        val next = hostHistory.filterNot { it == item }
                        hostHistory = next
                        saveTargetHistory(context.applicationContext, "tcp_target_history_v1", next)
                    },
                    port = port,
                    onPortChange = { port = it },
                    result = state.resolveResult,
                    publicIpResult = publicIpResult,
                    networkEnvironment = detectNetworkEnvironment(context),
                    networkProbeInfo = networkProbeInfo,
                    publicIpLoading = publicIpLoading,
                    onRefreshPublicIp = { refreshPublicIp() },
                    onCopyPublicIpv4 = { copyText(publicIpResult.ipv4, "IPv4出口地址") },
                    onCopyPublicIpv6 = { copyText(publicIpResult.ipv6, "IPv6出口地址") },
                    onOpenNatDiagnostics = { showNatDiagnosticDialog = true },
                    onOpenNsLookup = { appToolPage = AppToolPage.NSLOOKUP },
                    onOpenTracket = { appToolPage = AppToolPage.TRACKET },
                    onOpenMtu = { appToolPage = AppToolPage.MTU },
                    onOpenRoaming = { appToolPage = AppToolPage.ROAMING },
                    onOpenIpv6Diagnostics = {
                        selectedTab = MainTab.TEST
                        appToolPage = AppToolPage.IPV6_DIAGNOSTIC
                    },
                    onOpenPingSettings = {
                        selectedTab = MainTab.TEST
                        testPingFocusRequest += 1
                    },
                    maskPrivacy = maskPrivacy,
                    onMaskPrivacyChange = { maskPrivacy = it },
                    onResolve = {
                        hostHistory = rememberTargetHistoryItem(context.applicationContext, "tcp_target_history_v1", host)
                        resolve()
                    },
                    mode = mode,
                    onModeChange = { mode = it },
                    batchSize = batchSize,
                    onBatchSizeChange = { batchSize = it },
                    intervalMs = intervalMs,
                    onIntervalMsChange = { intervalMs = it },
                    timeoutMs = timeoutMs,
                    onTimeoutMsChange = { timeoutMs = it },
                    successLimit = successLimit,
                    onSuccessLimitChange = { successLimit = it },
                    failureLimit = failureLimit,
                    onFailureLimitChange = { failureLimit = it },
                    pingEnabled = pingEnabled,
                    onPingEnabledChange = { pingEnabled = it },
                    pingTarget = pingTarget,
                    onPingTargetChange = { pingTarget = it },
                    pingTargetHistory = pingTargetHistory,
                    onPickPingTargetHistory = { pingTarget = it },
                    onDeletePingTargetHistory = { item ->
                        val next = pingTargetHistory.filterNot { it == item }
                        pingTargetHistory = next
                        saveTargetHistory(context.applicationContext, "ping_target_history_v1", next)
                    },
                    pingIntervalMs = pingIntervalSetting,
                    onPingIntervalMsChange = { value ->
                        val clean = value.onlyDigits()
                        pingIntervalSetting = clean
                        clean.toLongOrNull()?.let { pingTimeoutSetting = recommendedPingTimeoutMsForInterval(it).toString() }
                    },
                    pingCount = pingCountSetting,
                    onPingCountChange = { pingCountSetting = it },
                    pingTimeoutMs = pingTimeoutSetting,
                    onPingTimeoutMsChange = { pingTimeoutSetting = it },
                    pingProtocol = pingProtocolSetting,
                    onPingProtocolChange = { pingProtocolSetting = it },
                    updateBadge = updateAvailable || downloadUi.active || downloadUi.finished,
                    updateProgress = if (downloadUi.active) downloadUi.progress else null,
                    onVersionClick = { showVersionDialog = true },
                    onSave = {
                        hostHistory = rememberTargetHistoryItem(context.applicationContext, "tcp_target_history_v1", host)
                        pingTargetHistory = rememberTargetHistoryItem(context.applicationContext, "ping_target_history_v1", pingTarget)
                        scope.launch { snackbarHostState.showSnackbar("参数已保存") }
                    },
                    onRestoreDefault = {
                        host = "www.baidu.com"; port = "80"; mode = TestMode.IPV4_THEN_IPV6
                        batchSize = "200"; intervalMs = "100"; timeoutMs = "1200"
                        successLimit = "65535"; failureLimit = "600"; keepConnections = true; maskPrivacy = false; historyLimit = "30"
                        pingEnabled = true; pingTarget = "223.5.5.5"; pingIntervalSetting = "1000"; pingCountSetting = "无限"; pingTimeoutSetting = recommendedPingTimeoutMsForInterval(1000).toString(); pingProtocolSetting = PingProtocolMode.AUTO
                    }
                )

                MainTab.TEST -> TestPage(
                    listState = testListState,
                    pingFocusRequest = testPingFocusRequest,
                    sessionExpanded = sessionCardExpanded,
                    onSessionExpandedChange = { sessionCardExpanded = it },
                    mode = mode,
                    status = state.status,
                    target = successLimit,
                    testHost = currentTestConfig?.host ?: host,
                    testPort = (currentTestConfig?.port ?: port.toIntOrNull() ?: 80),
                    chartMode = chartMode,
                    onChartModeChange = { chartMode = it },
                    chartPoints = displayChartPoints,
                    pingPoints = displayPingPoints,
                    pingIntervalLabel = pingIntervalLabel,
                    pingActiveTargetLabel = pingActiveTargetLabel.ifBlank { pingTarget },
                    pingRunning = pingRunning,
                    pingDurationMs = if (pingSessionStartedAt > 0L) {
                        ((if (pingRunning) pingDurationTick else (pingSessionEndedAt.takeIf { it > 0L } ?: System.currentTimeMillis())) - pingSessionStartedAt).coerceAtLeast(0L)
                    } else 0L,
                    pingJitterMs = displayPingJitterMs,
                    pingLogCount = displayPingLogCount,
                    onStartPing = { startPingMonitor(reset = true) },
                    onStopPing = { stopPingMonitor() },
                    onClearPing = { clearPingData() },
                    onShowPingLog = { appToolPage = AppToolPage.PING_HISTORY },
                    isAdding = state.isAdding,
                    runPhase = state.runPhase,
                    releaseUi = state.releaseUi,
                    onStart = { startTest() },
                    onStopAdding = { stopAdding() },
                    onRelease = { releaseAll() },
                    onExport = { exportLogs() },
                    ipv4Stats = state.ipv4Stats,
                    ipv6Stats = state.ipv6Stats,
                    showIpv4 = mode != TestMode.IPV6_ONLY,
                    showIpv6 = mode != TestMode.IPV4_ONLY,
                    maskPrivacy = maskPrivacy,
                    updateBadge = updateAvailable || downloadUi.active || downloadUi.finished,
                    updateProgress = if (downloadUi.active) downloadUi.progress else null,
                    onVersionClick = { showVersionDialog = true },
                    logs = state.logs,
                    onMoreLogs = { showRunLogDetail = true },
                    onMoreFailure = {
                        showDetail("失败原因详情", failureDetailLines(listOf(state.ipv4Stats, state.ipv6Stats)))
                    }
                )

                MainTab.LOGS -> LogsPage(
                    logs = state.logs,
                    history = state.history,
                    historyLimit = historyLimit,
                    historyPeriod = historyPeriod,
                    historySizeKb = historySizeKb,
                    historySavedCount = historySavedCount,
                    historyCounts = historyCounts,
                    maskPrivacy = maskPrivacy,
                    onExport = { exportLogs() },
                    onClear = { clearHistoryOnly() },
                    onHistoryLimitChange = { limit ->
                        historyLimit = limit
                        scope.launch {
                            val safeLimit = limit.toIntOrNull()?.coerceIn(10, 100) ?: 30
                            historyStore.trim(100)
                            applyHistoryUiSnapshot(loadHistoryUiSnapshot(historyPeriod, safeLimit))
                        }
                    },
                    onHistoryPeriodChange = { period ->
                        historyPeriod = period
                        scope.launch {
                            applyHistoryUiSnapshot(loadHistoryUiSnapshot(period, safeHistoryLimit()))
                        }
                    },
                    onEditRemark = { summary ->
                        editingRemarkSummary = summary
                        editingRemarkText = summary.remark
                    },
                    onDeleteHistory = { summary -> deleteHistoryItem(summary) },
                    onHistoryDetail = { summary ->
                        detailSummary = summary
                    }
                )
                }
            }

            if (downloadUi.active || downloadUi.finished || downloadUi.failed) {
                UpdateDownloadBanner(
                    state = downloadUi,
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp),
                    onOpen = { showDownloadDialog = true },
                    onInstall = { installReadyApk() },
                    onDismiss = { dismissUpdateDownloadUi() }
                )
            }

            BottomNoticeBanner(
                state = bottomNotice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}


private suspend fun saveHistorySnapshotImage(context: Context, summary: SessionSummary, maskPrivacy: Boolean): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val width = 1440
        val height = 2200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(AndroidColor.rgb(246, 248, 252))
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(17, 24, 39); textSize = 68f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(100, 116, 139); textSize = 36f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(17, 24, 39); textSize = 40f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(100, 116, 139); textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(37, 99, 235) }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(239, 68, 68) }
        val navyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(15, 47, 110) }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(226, 232, 240) }
        fun card(l: Float, t: Float, r: Float, b: Float) { canvas.drawRoundRect(RectF(l, t, r, b), 46f, 46f, cardPaint) }
        fun line(label: String, value: String, x: Float, y: Float) { canvas.drawText(label, x, y, smallPaint); canvas.drawText(value, x + 210f, y, textPaint) }
        fun bar(label: String, value: Int, max: Int, y: Float, paint: Paint) {
            canvas.drawText(label, 130f, y + 38f, smallPaint)
            val startX = 420f; val endX = 1080f
            canvas.drawRoundRect(RectF(startX, y + 8f, endX, y + 38f), 18f, 18f, bgPaint)
            val w = ((endX - startX) * (value.toFloat() / max.coerceAtLeast(1))).coerceIn(12f, endX - startX)
            canvas.drawRoundRect(RectF(startX, y + 8f, startX + w, y + 38f), 18f, 18f, paint)
            canvas.drawText(value.toString(), 1120f, y + 40f, textPaint)
        }
        val host = if (maskPrivacy) maskIpText(summary.host) else summary.host
        canvas.drawText("宽带会话测试器", 90f, 130f, titlePaint)
        canvas.drawText("检测详情快照", 90f, 190f, subPaint)
        card(70f, 250f, 1370f, 560f)
        canvas.drawText("结果摘要", 120f, 335f, titlePaint.apply { textSize = 56f })
        canvas.drawText(if (isAbnormalSummary(summary)) "异常中断 · 仅供参考" else "检测记录 · 可作为参考", 900f, 335f, subPaint)
        line("时间", summary.startedAtText, 120f, 420f)
        line("目标", "$host:${summary.port}", 120f, 490f)
        var y = 630f
        listOf("IPv4" to summary.ipv4Stats, "IPv6" to summary.ipv6Stats).forEach { (name, stats) ->
            if (stats != null) {
                card(70f, y, 1370f, y + 350f)
                canvas.drawText("$name 结果", 120f, y + 82f, titlePaint.apply { textSize = 52f })
                val max = maxOf(stats.activeSessions, stats.totalFailure, stats.totalAttempts, 1)
                bar("$name 活动", protocolPeak(stats), max, y + 122f, bluePaint)
                bar("$name 失败", stats.totalFailure, max, y + 197f, redPaint)
                bar("$name 总计", stats.totalAttempts, max, y + 272f, navyPaint)
                y += 400f
            }
        }
        card(70f, y, 1370f, y + 330f)
        canvas.drawText("诊断建议", 120f, y + 82f, titlePaint.apply { textSize = 52f })
        var ay = y + 145f
        historyAdvice(summary).take(3).forEachIndexed { index, item ->
            canvas.drawText("${index + 1}.", 120f, ay, bluePaint.apply { textSize = 36f })
            canvas.drawText(item.take(34), 175f, ay, smallPaint)
            ay += 60f
        }
        y += 380f
        card(70f, y, 1370f, y + 220f)
        canvas.drawText("失败原因", 120f, y + 82f, titlePaint.apply { textSize = 52f })
        val reasons = listOfNotNull(summary.ipv4Stats, summary.ipv6Stats).flatMap { it.errorSummary.entries.map { e -> "${e.key} ${e.value}" } }
        canvas.drawText(if (reasons.isEmpty()) "无明显失败原因" else reasons.joinToString("  ").take(42), 120f, y + 155f, textPaint)
        val fileName = "NetSessionTester_${summary.id}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NetSessionTester")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建图片")
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) } ?: error("无法写入图片")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); resolver.update(uri, values, null, null)
        }
        bitmap.recycle()
        true
    }.getOrElse { false }
}

@Composable
private fun HistoryDetailDialog(summary: SessionSummary, maskPrivacy: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) } },
        title = {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("检测详情", fontWeight = FontWeight.ExtraBold, color = TextDark, fontSize = 21.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch {
                            val ok = saveHistorySnapshotImage(context, summary, maskPrivacy)
                            Toast.makeText(context, if (ok) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("保存图片", fontSize = 12.sp) }
                }
                Text("OneUI / Material 3 卡片详情", color = Muted, fontSize = 11.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistorySummaryCard(summary, maskPrivacy)
                HistoryConclusionCard(summary)
                HistoryDetailChartCard(summary)
                HistoryAdviceCard(summary)
                HistoryFailureCard(summary)
                if (summary.remark.isNotBlank()) {
                    SoftCard {
                        SectionTitle("□", "备注", Blue)
                        Text(summary.remark, color = TextDark, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        },
        shape = ShapeL
    )
}

@Composable
private fun HistorySummaryCard(summary: SessionSummary, maskPrivacy: Boolean) {
    val abnormal = isAbnormalSummary(summary)
    val host = if (maskPrivacy) maskIpText(summary.host) else summary.host
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("◎", "结果摘要", if (abnormal) ErrorRed else Blue)
            Spacer(Modifier.weight(1f))
            StatusChip(if (abnormal) "异常中断" else "检测记录", if (abnormal) RedSoft else GreenSoft, if (abnormal) ErrorRed else Green, compact = true)
        }
        InfoLine("时间", summary.startedAtText)
        InfoLine("目标", "$host:${summary.port}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(summary.mode.label, BlueSoft, Blue, compact = true)
            StatusChip(if (abnormal) "可信度低" else "可作为参考", if (abnormal) RedSoft else GreenSoft, if (abnormal) ErrorRed else Green, compact = true)
        }
    }
}

@Composable
private fun HistoryConclusionCard(summary: SessionSummary) {
    val title = historyConclusion(summary)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("▮", "分享结论", Blue)
            Spacer(Modifier.weight(1f))
            Text("适合截图", color = Muted, fontSize = 11.sp)
        }
        Text(title, color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 18.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            summary.ipv4Stats?.let { StatusChip("IPv4峰值 ${protocolPeak(it)}", BlueSoft, Blue, compact = true) }
            summary.ipv6Stats?.let { StatusChip("IPv6峰值 ${protocolPeak(it)}", GreenSoft, Green, compact = true) }
            val failures = listOfNotNull(summary.ipv4Stats, summary.ipv6Stats).sumOf { it.totalFailure }
            if (failures > 0) StatusChip("失败 $failures", RedSoft, ErrorRed, compact = true)
        }
    }
}

@Composable
private fun HistoryProtocolCard(title: String, stats: ProtocolStats, maskPrivacy: Boolean) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if (title.contains("IPv4")) "session_ipv4" else if (title.contains("IPv6")) "session_ipv6" else "chart", title, Blue)
            Spacer(Modifier.weight(1f))
            Text(stats.phase, color = if (isAbnormalPhase(stats.phase)) ErrorRed else Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricTile("活动", stats.activeSessions.toString(), Blue, Modifier.weight(1f))
            MetricTile("失败", stats.totalFailure.toString(), ErrorRed, Modifier.weight(1f))
            MetricTile("总计", stats.totalAttempts.toString(), Navy, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricTile("目标CPS", "${stats.lastAdded}/s", Blue, Modifier.weight(1f))
            MetricTile(if (stats.activeSessions == 0 && stats.totalAttempts > 0) "平均CPS" else "实际CPS", "${stats.cps}/s", Blue, Modifier.weight(1f))
            MetricTile("峰值", protocolPeak(stats).toString(), Green, Modifier.weight(1f))
        }
        if (stats.resolvedAddresses.isNotEmpty()) {
            Text("地址：${displayIpList(stats.resolvedAddresses, maskPrivacy)}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HistoryDetailChartCard(summary: SessionSummary) {
    val stats = listOfNotNull(summary.ipv4Stats, summary.ipv6Stats)
    if (stats.isEmpty()) return
    val maxValue = stats.maxOf { maxOf(1, protocolPeak(it), it.totalFailure, it.totalAttempts) }
    SoftCard {
        SectionTitle("▮", "结果图表", Green)
        stats.forEach { s ->
            HistoryBarRow("${s.protocol.label} 活动", protocolPeak(s), maxValue, Blue)
            HistoryBarRow("${s.protocol.label} 失败", s.totalFailure, maxValue, ErrorRed)
            HistoryBarRow("${s.protocol.label} 总计", s.totalAttempts, maxValue, Navy)
        }
    }
}

@Composable
private fun HistoryAdviceCard(summary: SessionSummary) {
    val lines = historyAdvice(summary)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("!", "诊断建议", Orange)
            Spacer(Modifier.weight(1f))
            StatusChip("自动分析", BlueSoft, Blue, compact = true)
        }
        lines.take(3).forEachIndexed { index, line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("${index + 1}.", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                Text(line, color = Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryFailureCard(summary: SessionSummary) {
    val errors = mergedErrors(listOfNotNull(summary.ipv4Stats, summary.ipv6Stats))
    if (errors.isEmpty()) return
    SoftCard {
        SectionTitle("!", "失败原因", ErrorRed)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            errors.entries.sortedByDescending { it.value }.take(8).forEach { (name, count) -> ReasonChip("$name $count") }
        }
    }
}

private fun protocolPeak(stats: ProtocolStats): Int = maxOf(stats.activeSessions, stats.maxStableSessions, stats.totalSuccess)

private fun isAbnormalPhase(phase: String): Boolean = phase.contains("中断") || phase.contains("地址丢失") || phase.contains("解析失败") || phase.contains("异常")

private fun isAbnormalSummary(summary: SessionSummary): Boolean = listOfNotNull(summary.ipv4Stats, summary.ipv6Stats).any { s ->
    val isFd = s.phase.contains("FD", true) || s.errorSummary.keys.any { it.contains("FD", true) }
    !isFd && (isAbnormalPhase(s.phase) || s.errorSummary.keys.any { it.contains("中断") || it.contains("地址丢失") || it.contains("异常") })
}



private data class PingLogGroup(
    val sessionId: Long,
    val target: String,
    val protocol: String,
    val startedAt: Long,
    val headerNote: String,
    val entries: List<PingLogEntry>
)

private fun buildPingLogGroups(logs: List<PingLogEntry>): List<PingLogGroup> {
    if (logs.isEmpty()) return emptyList()
    val groups = logs.groupBy { entry ->
        if (entry.sessionId != 0L) entry.sessionId else entry.timeEpochMs
    }
    return groups.map { (id, items) ->
        val sorted = items.sortedBy { it.timeEpochMs }
        val first = sorted.first()
        val header = sorted.firstOrNull { it.status == "开始" } ?: first
        PingLogGroup(
            sessionId = id,
            target = header.target,
            protocol = header.protocol,
            startedAt = header.timeEpochMs,
            headerNote = header.note,
            entries = sorted
        )
    }.sortedByDescending { it.startedAt }
}

private fun pingLogGroupDurationMs(group: PingLogGroup): Long {
    val stop = group.entries.lastOrNull { it.status == "停止" }?.timeEpochMs
    val last = group.entries.maxOfOrNull { it.timeEpochMs }
    return ((stop ?: last ?: group.startedAt) - group.startedAt).coerceAtLeast(0L)
}

private fun formatPingLogGroupTitle(group: PingLogGroup): String {
    val time = DateTimeFormatter.ofPattern("今天 HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(group.startedAt))
    val note = group.headerNote.ifBlank { "" }
    return listOf(time, formatPingDuration(pingLogGroupDurationMs(group)), group.target, group.protocol, note).filter { it.isNotBlank() }.joinToString(" · ")
}


private fun pingEntriesForSession(group: PingLogGroup): List<PingLogEntry> =
    group.entries.filter { it.status != "开始" && it.status != "停止" && it.status != "高频不可用" }

private fun pingLogGroupJitterMs(group: PingLogGroup): Double? {
    val latencies = pingEntriesForSession(group).mapNotNull { it.latencyMs?.toDouble() }
    if (latencies.size < 2) return null
    var sum = 0.0
    var count = 0
    var last: Double? = null
    latencies.takeLast(50).forEach { value ->
        last?.let { prev ->
            sum += kotlin.math.abs(value - prev)
            count++
        }
        last = value
    }
    return if (count > 0) sum / count else null
}

private fun pingIntervalFromHeader(note: String): Long {
    val match = Regex("""间隔(\d+)ms""").find(note)
    return match?.groupValues?.getOrNull(1)?.toLongOrNull()?.coerceIn(30L, 60_000L) ?: 1_000L
}

private fun pingPointsFromLogGroup(group: PingLogGroup): List<PingPoint> {
    val items = pingEntriesForSession(group).sortedBy { it.timeEpochMs }
    if (items.isEmpty()) return emptyList()
    val interval = pingIntervalFromHeader(group.headerNote)
    val bucketSize = if (interval < 200L) 250L else 1_000L
    val start = group.startedAt
    return items
        .groupBy { (((it.timeEpochMs - start).coerceAtLeast(0L)) / bucketSize) * bucketSize }
        .map { (bucketMs, entries) ->
            val valid = entries.mapNotNull { it.latencyMs }
            val avg = valid.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            PingPoint(
                elapsedSec = (bucketMs / 1_000L).toInt().coerceAtLeast(0),
                latencyMs = avg,
                lossCount = entries.count { it.latencyMs == null || it.status == "超时" },
                highLatency = valid.any { it >= 100 },
                sampleCount = entries.size.coerceAtLeast(1),
                timeEpochMs = start + bucketMs,
                elapsedMs = bucketMs,
                minLatencyMs = valid.minOrNull(),
                maxLatencyMs = valid.maxOrNull()
            )
        }
        .sortedBy { it.elapsedMs }
}

@Composable
private fun PingHistoryToolPage(logs: List<PingLogEntry>, onBack: () -> Unit, onDeleteSession: (Long) -> Unit) {
    val groups = remember(logs) { buildPingLogGroups(logs).take(12) }
    val storageText = remember(logs) { formatBytes(estimatePingLogStorageBytes(logs)) }
    var expandedSession by remember(groups.firstOrNull()?.sessionId) { mutableStateOf<Long?>(null) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ToolPageHeader("Ping历史", "${groups.size}/12 · $storageText · 点击卡片展开，单条可删除", onBack) }
        if (groups.isEmpty()) {
            item {
                SoftCard {
                    Text("暂无 Ping 历史记录。", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            items(groups, key = { it.sessionId }) { group ->
                val expanded = expandedSession == group.sessionId
                PingLogSessionCard(
                    group = group,
                    expanded = expanded,
                    onToggle = { expandedSession = if (expanded) null else group.sessionId },
                    onDelete = { onDeleteSession(group.sessionId) }
                )
            }
        }
        item { Spacer(Modifier.height(70.dp)) }
    }
}

@Composable
private fun PingLogDialog(logs: List<PingLogEntry>, onDismiss: () -> Unit) {
    val groups = buildPingLogGroups(logs).take(12)
    val latest = groups.firstOrNull()
    val latestEntries = latest?.entries.orEmpty().filter { it.status != "开始" && it.status != "停止" && it.status != "高频不可用" }
    val success = latestEntries.count { it.status == "成功" || it.status == "高延迟" }
    val timeout = latestEntries.count { it.status == "超时" }
    val avg = latestEntries.mapNotNull { it.latencyMs }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val high = latestEntries.mapNotNull { it.latencyMs }.maxOrNull()
    val low = latestEntries.mapNotNull { it.latencyMs }.minOrNull()
    val storageText = remember(logs) { formatBytes(estimatePingLogStorageBytes(logs)) }
    val storedRows = remember(logs) { trimPingLogSessions(logs).size }
    var expandedSession by remember(groups.firstOrNull()?.sessionId) { mutableStateOf(groups.firstOrNull()?.sessionId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ping 响应日志", fontWeight = FontWeight.Bold, color = TextDark)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip("12次历史", BlueSoft, Blue, compact = true)
                    StatusChip("存储 $storageText", Color(0xFFF8FAFC), Muted, compact = true)
                    StatusChip("$storedRows 条", Color(0xFFF8FAFC), Muted, compact = true)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (latest == null) {
                    Text("暂无 Ping 响应日志。", color = Muted, fontSize = 13.sp)
                } else {
                    Card(
                        shape = ShapeM,
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF7FF)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("最近一次", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${latest.target} · ${latest.protocol} · ${formatPingDuration(pingLogGroupDurationMs(latest))}", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusChip("总数 ${latestEntries.size}", BlueSoft, Blue, compact = true)
                                StatusChip("成功 $success", GreenSoft, Green, compact = true)
                                StatusChip("超时 $timeout", if (timeout == 0) Color(0xFFF8FAFC) else RedSoft, if (timeout == 0) Muted else ErrorRed, compact = true)
                                StatusChip("平均 ${avg?.let { "${it}ms" } ?: "—"}", Color(0xFFF8FAFC), Navy, compact = true)
                                StatusChip("最高 ${high?.let { "${it}ms" } ?: "—"}", Color(0xFFFFF3E0), Orange, compact = true)
                                StatusChip("最低 ${low?.let { "${it}ms" } ?: "—"}", GreenSoft, Green, compact = true)
                            }
                        }
                    }
                    Text("最近 12 次记录", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        groups.forEach { group ->
                            item(key = "session-${group.sessionId}") {
                                val expanded = expandedSession == group.sessionId
                                PingLogSessionCard(
                                    group = group,
                                    expanded = expanded,
                                    onToggle = { expandedSession = if (expanded) null else group.sessionId }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun PingLogSessionCard(
    group: PingLogGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val items = group.entries.filter { it.status != "开始" && it.status != "停止" && it.status != "高频不可用" }
    val success = items.count { it.status == "成功" || it.status == "高延迟" }
    val timeout = items.count { it.status == "超时" }
    val avg = items.mapNotNull { it.latencyMs }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val loss = if (items.isNotEmpty()) ((timeout * 100f) / items.size).roundToInt() else 0
    Card(
        shape = ShapeM,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Border.copy(alpha = 0.8f), ShapeM)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(ShapeM).clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Text(formatPingLogGroupTitle(group), color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Text("${formatPingDuration(pingLogGroupDurationMs(group))} · ${items.size}次 · 成功$success · 平均${avg?.let { "${it}ms" } ?: "—"} · 丢包$loss%", color = Muted, fontSize = 11.sp, maxLines = 1)
                    }
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("删除", color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (expanded) "⌃" else "⌄", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            AnimatedVisibility(visible = expanded) {
                val chartPoints = remember(group.entries) { pingPointsFromLogGroup(group) }
                val jitter = remember(group.entries) { pingLogGroupJitterMs(group) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip("时长 ${formatPingDuration(pingLogGroupDurationMs(group))}", BlueSoft, Blue, compact = true)
                        StatusChip("平均 ${avg?.let { "${it}ms" } ?: "—"}", Color(0xFFF8FAFC), Navy, compact = true)
                        StatusChip("抖动 ${jitter?.let { formatPingJitter(it) } ?: "—"}", Color(0xFFFAF7FF), Purple, compact = true)
                        StatusChip("丢包 $loss%", if (loss == 0) Color(0xFFF8FAFC) else RedSoft, if (loss == 0) Muted else ErrorRed, compact = true)
                    }
                    PingLineChart(chartPoints, group.target, running = false, jitterMs = jitter)
                    Text("已保存聚合图形与测试总结；如需查看明细，下方仅展示最近 80 条。", color = Muted, fontSize = 10.sp)
                    items.asReversed().take(80).forEach { entry -> PingLogRow(entry) }
                }
            }
        }
    }
}


@Composable
private fun PingLogRow(entry: PingLogEntry) {
    val dotColor = when (entry.status) {
        "成功" -> Green
        "高延迟" -> Orange
        "超时" -> ErrorRed
        else -> Blue
    }
    val latencyText = entry.latencyMs?.let { "${it}ms" } ?: "—"
    val statusText = when {
        entry.status == "停止" && entry.note.isNotBlank() -> "停止 · ${entry.note}"
        entry.status == "超时" && entry.note.isNotBlank() -> "超时 · ${entry.note}"
        else -> entry.status
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, ShapeS)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.width(7.dp).height(7.dp).background(dotColor, RoundedCornerShape(50)))
        Text(entry.timeText, color = Muted, fontSize = 11.sp, modifier = Modifier.width(62.dp))
        Text(String.format(java.util.Locale.US, "%.2fs", entry.elapsedMs.coerceAtLeast(0L) / 1000.0), color = Muted, fontSize = 11.sp, modifier = Modifier.width(54.dp))
        Text(latencyText, color = if (entry.status == "高延迟") Orange else if (entry.status == "超时") ErrorRed else TextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
        Text(statusText, color = dotColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

private fun pingLogDetailLines(logs: List<PingLogEntry>): List<String> {
    if (logs.isEmpty()) return listOf("暂无 Ping 响应日志。")
    val success = logs.count { it.status == "成功" || it.status == "高延迟" }
    val timeout = logs.count { it.status == "超时" }
    val avg = logs.mapNotNull { it.latencyMs }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    return buildList {
        add("总记录：${logs.size}  成功：$success  超时：$timeout  平均：${avg?.let { "${it}ms" } ?: "—"}")
        add("时间        运行秒   协议   延迟     状态     目标/备注")
        logs.takeLast(300).asReversed().forEach { item ->
            val latency = item.latencyMs?.let { "${it}ms" } ?: "—"
            val note = item.note.ifBlank { item.target }
            val elapsed = String.format(java.util.Locale.US, "%.2fs", item.elapsedMs.coerceAtLeast(0L) / 1000.0)
            add("${item.timeText}  ${elapsed.padEnd(7)}  ${item.protocol.padEnd(4)}  ${latency.padEnd(7)}  ${item.status.padEnd(4)}  $note")
        }
    }
}

private fun historyConclusion(summary: SessionSummary): String {
    val abnormal = isAbnormalSummary(summary)
    val v4 = summary.ipv4Stats
    val v6 = summary.ipv6Stats
    val v4Peak = v4?.let { protocolPeak(it) } ?: 0
    val v6Peak = v6?.let { protocolPeak(it) } ?: 0
    val fd = listOfNotNull(v4, v6).any { it.phase.contains("FD", true) || it.errorSummary.keys.any { k -> k.contains("FD", true) } }
    val totalFail = listOfNotNull(v4, v6).sumOf { it.totalFailure }
    val timeoutFail = listOfNotNull(v4, v6).sumOf { it.errorSummary["超时"] ?: 0 }
    return when {
        fd -> "触发手机 FD/Socket 上限，不一定代表宽带到顶。"
        abnormal -> "本次非正常中断，结果仅供参考。"
        v4 != null && v6 != null && v4Peak in 1..999 && v6Peak >= 5000 -> "IPv4 可能是主要瓶颈，优先怀疑 NAT/CGNAT。"
        v4 != null && v6 == null && v4Peak in 1..999 && totalFail > 0 -> "仅 IPv4 偏低且有失败，建议补测 IPv6 判断瓶颈。"
        v6 != null && v4 == null && v6Peak in 1..999 && totalFail > 0 -> "仅 IPv6 偏低且有失败，建议补测 IPv4 对比。"
        timeoutFail > 0 || totalFail > 0 -> "存在失败连接，需结合失败原因和目标站复测。"
        else -> "结果可作为本次网络会话能力参考。"
    }
}

private fun historyAdvice(summary: SessionSummary): List<String> {
    val v4 = summary.ipv4Stats
    val v6 = summary.ipv6Stats
    val v4Peak = v4?.let { protocolPeak(it) } ?: 0
    val v6Peak = v6?.let { protocolPeak(it) } ?: 0
    val fd = listOfNotNull(v4, v6).any { it.phase.contains("FD", true) || it.errorSummary.keys.any { k -> k.contains("FD", true) } }
    val totalFail = listOfNotNull(v4, v6).sumOf { it.totalFailure }
    val timeoutFail = listOfNotNull(v4, v6).sumOf { it.errorSummary["超时"] ?: 0 }
    return when {
        fd -> listOf("本机已触发 FD/Socket 上限，结果更像手机端资源限制。", "观察路由器连接数表，判断宽带侧是否还在增长。", "本机已释放，路由器会话表可能延迟数秒下降。")
        isAbnormalSummary(summary) -> listOf("保持网络不切换后重新测试。", "检查 VPN、WiFi/蜂窝、4G/5G 是否变化。", "非正常中断结果不要直接作为会话上限。")
        v4 != null && v6 != null && v4Peak in 1..999 && v6Peak >= 5000 -> listOf("IPv4 低、IPv6 高，优先看 IPv4 NAT/CGNAT。", "蜂窝网络可换 APN 或切换 4G/5G 复测。", "WiFi 网络可观察路由器 NAT/连接数表。")
        v4 != null && v6 == null && v4Peak in 1..999 -> listOf("补测 IPv6 或使用分别测试模式。", "如果 IPv6 明显更高，优先怀疑 IPv4 出口策略。", "更换目标站复测排除目标限制。")
        v6 != null && v4 == null && v6Peak in 1..999 -> listOf("补测 IPv4 或使用分别测试模式。", "如果 IPv4 明显更低，可重点排查 IPv4 出口策略。", "更换目标站复测排除目标限制。")
        timeoutFail > 0 -> listOf("失败以超时为主，可能是目标站、网络质量或出口策略影响。", "建议更换目标站、换网络、换时间段复测。", "如果只在 IPv4 出现，优先排查 IPv4 NAT/CGNAT。")
        totalFail > 0 -> listOf("存在失败连接，结果应结合失败原因卡判断。", "建议用相同参数复测一次排除偶发因素。", "分别测试 IPv4/IPv6 更利于判断瓶颈。")
        else -> listOf("保存截图对比不同运营商和网络。", "分别测试 IPv4/IPv6 更利于判断瓶颈。", "异常结果可结合失败原因卡判断。")
    }
}


@Composable
private fun TargetAndModeCard(
    host: String,
    onHostChange: (String) -> Unit,
    hostHistory: List<String>,
    onPickHostHistory: (String) -> Unit,
    onDeleteHostHistory: (String) -> Unit,
    hostPresets: List<TargetPreset> = DefaultTargetPresets,
    port: String,
    onPortChange: (String) -> Unit,
    result: com.demonv.netsessiontester.model.ResolveResult,
    maskPrivacy: Boolean,
    onMaskPrivacyChange: (Boolean) -> Unit,
    onResolve: () -> Unit,
    mode: TestMode,
    onModeChange: (TestMode) -> Unit
) {
    var modeMenuOpen by remember { mutableStateOf(false) }
    SoftCard {
        SectionTitle("◎", "目标与模式", Blue)
        FieldLabel("地址")
        HistoryTextField(
            value = host,
            onValueChange = onHostChange,
            placeholder = "www.baidu.com",
            history = hostHistory,
            presets = hostPresets,
            onPick = onPickHostHistory,
            onDelete = onDeleteHostHistory,
            onAdd = onPickHostHistory,
            leadingMark = "host"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                FieldLabel("端口")
                CleanField(port, { onPortChange(it.onlyDigits()) }, "80", keyboardType = KeyboardType.Number, leadingMark = "port")
            }
            OutlinedButton(onClick = onResolve, shape = ShapeM, modifier = Modifier.height(52.dp).width(104.dp)) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.width(15.dp).height(15.dp))
                Spacer(Modifier.width(3.dp))
                Text("解析", fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingChoiceCard(
                label = "测试模式",
                value = mode.label,
                leadingMark = "mode",
                modifier = Modifier.weight(1f),
                onClick = { modeMenuOpen = true }
            )
            Column(Modifier.weight(1f)) {
                FieldLabel("隐私模式")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color.White, ShapeM)
                        .border(1.dp, Border, ShapeM)
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconGlyph(if (maskPrivacy) "privacy_on" else "privacy", Blue, Modifier.width(18.dp).height(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("隐私", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Switch(checked = maskPrivacy, onCheckedChange = onMaskPrivacyChange)
                }
            }
        }
        if (modeMenuOpen) {
            AlertDialog(
                onDismissRequest = { modeMenuOpen = false },
                confirmButton = {},
                title = { Text("测试模式", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(TestMode.IPV4_ONLY, TestMode.IPV6_ONLY, TestMode.IPV4_THEN_IPV6).forEach { option ->
                            TextButton(
                                onClick = {
                                    onModeChange(option)
                                    modeMenuOpen = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(option.label, fontWeight = if (mode == option) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            )
        }
        if (result.ipv4.isNotEmpty() || result.ipv6.isNotEmpty() || result.error != null) {
            HorizontalDivider(color = Border)
            InfoLine("IPv4", displayIpList(result.ipv4, maskPrivacy))
            InfoLine("IPv6", displayIpList(result.ipv6, maskPrivacy))
            result.error?.let { InfoLine("错误", it, ErrorRed) }
        }
    }
}

@Composable
private fun ProtocolPickDialog(
    title: String,
    current: PingProtocolMode,
    onDismiss: () -> Unit,
    onPick: (PingProtocolMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PingProtocolMode.values().forEach { option ->
                    TextButton(
                        onClick = { onPick(option) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when (option) {
                                PingProtocolMode.AUTO -> "自动 IPv4/IPv6"
                                PingProtocolMode.IPV4 -> "仅 IPv4"
                                PingProtocolMode.IPV6 -> "仅 IPv6"
                            },
                            fontWeight = if (current == option) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ReorderableCardItem(
    id: String,
    order: List<String>,
    onOrderChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 124.dp.toPx() }
    var lastReorderAt by remember { mutableStateOf(0L) }
    val isDragging = draggingId == id
    val scale by animateFloatAsState(if (isDragging) 1.018f else 1f, tween(220, easing = FastOutSlowInEasing), label = "dragScale")
    val elevation by animateFloatAsState(if (isDragging) 18f else 0f, tween(220, easing = FastOutSlowInEasing), label = "dragElevation")

    Box(
        modifier = modifier
            .zIndex(if (isDragging) 20f else 0f)
            .shadow(elevation.dp, ShapeL, clip = false)
            .clip(ShapeL)
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(id, order) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        draggingId = id
                        dragOffsetY = 0f
                    },
                    onDragEnd = {
                        draggingId = null
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        draggingId = null
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (draggingId != id) return@detectDragGesturesAfterLongPress
                        dragOffsetY += dragAmount.y
                        if (abs(dragOffsetY) >= thresholdPx) {
                            val now = System.currentTimeMillis()
                            if (now - lastReorderAt >= 360L) {
                                val from = order.indexOf(id)
                                if (from >= 0) {
                                    val direction = if (dragOffsetY > 0) 1 else -1
                                    val to = (from + direction).coerceIn(0, order.lastIndex)
                                    if (to != from) {
                                        val next = order.toMutableList()
                                        next.removeAt(from)
                                        next.add(to, id)
                                        onOrderChange(next)
                                        dragOffsetY -= direction * thresholdPx
                                        lastReorderAt = now
                                    }
                                }
                            }
                        }
                    }
                )
            }
    ) {
        content()
    }
}

@Composable
private fun SettingsPage(
    listState: LazyListState,
    networkInfoExpanded: Boolean,
    onNetworkInfoExpandedChange: (Boolean) -> Unit,
    host: String,
    onHostChange: (String) -> Unit,
    hostHistory: List<String>,
    onPickHostHistory: (String) -> Unit,
    onDeleteHostHistory: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    result: com.demonv.netsessiontester.model.ResolveResult,
    publicIpResult: PublicIpResult,
    networkEnvironment: NetworkEnvironment,
    networkProbeInfo: NetworkProbeInfo,
    publicIpLoading: Boolean,
    onRefreshPublicIp: () -> Unit,
    onCopyPublicIpv4: () -> Unit,
    onCopyPublicIpv6: () -> Unit,
    onOpenNatDiagnostics: () -> Unit,
    onOpenNsLookup: () -> Unit,
    onOpenTracket: () -> Unit,
    onOpenMtu: () -> Unit,
    onOpenRoaming: () -> Unit,
    onOpenIpv6Diagnostics: () -> Unit,
    onOpenPingSettings: () -> Unit,
    maskPrivacy: Boolean,
    onMaskPrivacyChange: (Boolean) -> Unit,
    onResolve: () -> Unit,
    mode: TestMode,
    onModeChange: (TestMode) -> Unit,
    batchSize: String,
    onBatchSizeChange: (String) -> Unit,
    intervalMs: String,
    onIntervalMsChange: (String) -> Unit,
    timeoutMs: String,
    onTimeoutMsChange: (String) -> Unit,
    successLimit: String,
    onSuccessLimitChange: (String) -> Unit,
    failureLimit: String,
    onFailureLimitChange: (String) -> Unit,
    pingEnabled: Boolean,
    onPingEnabledChange: (Boolean) -> Unit,
    pingTarget: String,
    onPingTargetChange: (String) -> Unit,
    pingTargetHistory: List<String>,
    onPickPingTargetHistory: (String) -> Unit,
    onDeletePingTargetHistory: (String) -> Unit,
    pingIntervalMs: String,
    onPingIntervalMsChange: (String) -> Unit,
    pingCount: String,
    onPingCountChange: (String) -> Unit,
    pingTimeoutMs: String,
    onPingTimeoutMsChange: (String) -> Unit,
    pingProtocol: PingProtocolMode,
    onPingProtocolChange: (PingProtocolMode) -> Unit,
    updateBadge: Boolean,
    updateProgress: Int?,
    onVersionClick: () -> Unit,
    onSave: () -> Unit,
    onRestoreDefault: () -> Unit
) {
    val context = LocalContext.current
    val defaultCards = listOf("network", "target", "session", "ping")
    var settingsCardOrder by remember {
        mutableStateOf(loadCardOrder(context.applicationContext, "settings_card_order_v3", defaultCards))
    }
    fun updateSettingsOrder(next: List<String>) {
        val clean = (next.filter { it in defaultCards } + defaultCards.filterNot { it in next }).distinct()
        settingsCardOrder = clean
        saveCardOrder(context.applicationContext, "settings_card_order_v3", clean)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { PageTitle("宽带会话测试器", "TCP 会话测试 · IPv4 / IPv6 分别测试", updateBadge, updateProgress, onVersionClick) }
        items(settingsCardOrder, key = { it }) { cardId ->
            ReorderableCardItem(
                id = cardId,
                modifier = Modifier.animateItem(placementSpec = tween(360, easing = FastOutSlowInEasing)),
                order = settingsCardOrder,
                onOrderChange = ::updateSettingsOrder
            ) {
                when (cardId) {
                    "target" -> TargetAndModeCard(
                        host = host,
                        onHostChange = onHostChange,
                        hostHistory = hostHistory,
                        onPickHostHistory = onPickHostHistory,
                        onDeleteHostHistory = onDeleteHostHistory,
                        port = port,
                        onPortChange = onPortChange,
                        result = result,
                        maskPrivacy = maskPrivacy,
                        onMaskPrivacyChange = onMaskPrivacyChange,
                        onResolve = onResolve,
                        mode = mode,
                        onModeChange = onModeChange
                    )
                    "network" -> NetworkEnvironmentSettingsCard(
                        env = networkEnvironment,
                        publicIpResult = publicIpResult,
                        probeInfo = networkProbeInfo,
                        publicIpLoading = publicIpLoading,
                        maskPrivacy = maskPrivacy,
                        onRefresh = onRefreshPublicIp,
                        onCopyPublicIpv4 = onCopyPublicIpv4,
                        onCopyPublicIpv6 = onCopyPublicIpv6,
                        onOpenNatDiagnostics = onOpenNatDiagnostics,
                        onOpenNsLookup = onOpenNsLookup,
                        onOpenTracket = onOpenTracket,
                        onOpenMtu = onOpenMtu,
                        onOpenRoaming = onOpenRoaming,
                        onOpenIpv6Diagnostics = onOpenIpv6Diagnostics,
                        expanded = networkInfoExpanded,
                        onExpandedChange = onNetworkInfoExpandedChange
                    )
                    "session" -> SoftCard {
                        SectionTitle("≡", "会话参数", Green)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ParamField("目标CPS", batchSize, onBatchSizeChange, Modifier.weight(1f), leadingMark = "count")
                            ParamField("调度间隔ms", intervalMs, onIntervalMsChange, Modifier.weight(1f), leadingMark = "time")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ParamField("超时（ms）", timeoutMs, onTimeoutMsChange, Modifier.weight(1f), leadingMark = "time")
                            ParamField("失败兜底", failureLimit, onFailureLimitChange, Modifier.weight(1f), leadingMark = "!")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ParamField("目标会话（条）", successLimit, onSuccessLimitChange, Modifier.weight(1f), leadingMark = "count")
                            Spacer(Modifier.weight(1f))
                        }
                        Text("V1.1.3：NAT 手动诊断支持多服务器顺延，测试过程会显示 Test I/II/III 或 Filtering/Mapping 阶段。", color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    "ping" -> SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(ShapeM)
                                    .clickable(onClick = onOpenPingSettings)
                            ) {
                                MarkBox("ping", BlueSoft, Blue)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("独立 Ping", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 13.sp)
                                Text("可独立启动/停止，支持响应日志", color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                            Switch(checked = pingEnabled, onCheckedChange = onPingEnabledChange)
                        }
                        FieldLabel("Ping 目标")
                        HistoryTextField(
                            value = pingTarget,
                            onValueChange = onPingTargetChange,
                            placeholder = "223.5.5.5",
                            history = pingTargetHistory,
                            presets = DefaultPingTargetPresets,
                            onPick = onPickPingTargetHistory,
                            onDelete = onDeletePingTargetHistory,
                            onAdd = onPickPingTargetHistory,
                            leadingMark = "target"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ParamField("间隔ms", pingIntervalMs, {
                                val clean = it.onlyDigits()
                                onPingIntervalMsChange(clean)
                                clean.toLongOrNull()?.let { ms -> onPingTimeoutMsChange(recommendedPingTimeoutMsForInterval(ms).toString()) }
                            }, Modifier.weight(1f), leadingMark = "time")
                            ParamField("超时ms", pingTimeoutMs, { onPingTimeoutMsChange(it.onlyDigits()) }, Modifier.weight(1f), leadingMark = "time")
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            DefaultPingIntervalPresets.forEach { preset ->
                                SoftChoicePill(
                                    text = preset.label,
                                    selected = pingIntervalMs == preset.intervalMs,
                                    onClick = {
                                        onPingIntervalMsChange(preset.intervalMs)
                                        onPingTimeoutMsChange(recommendedPingTimeoutMsForInterval(preset.intervalMs.toLong()).toString())
                                    },
                                    compact = true
                                )
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            DefaultPingTimeoutPresets.forEach { preset ->
                                SoftChoicePill(
                                    text = preset.label,
                                    selected = if (preset.valueMs == null) pingTimeoutMs == recommendedPingTimeoutMsForInterval(pingIntervalMs.toLongOrNull() ?: 1000L).toString() else pingTimeoutMs == preset.valueMs,
                                    onClick = { onPingTimeoutMsChange(preset.valueMs ?: recommendedPingTimeoutMsForInterval(pingIntervalMs.toLongOrNull() ?: 1000L).toString()) },
                                    compact = true
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ParamField("次数", pingCount, onPingCountChange, Modifier.weight(1f), leadingMark = "count")
                            var protocolMenuOpen by remember { mutableStateOf(false) }
                            SelectField(
                                label = "协议",
                                value = when (pingProtocol) {
                                    PingProtocolMode.AUTO -> "自动 IPv4/IPv6"
                                    PingProtocolMode.IPV4 -> "仅 IPv4"
                                    PingProtocolMode.IPV6 -> "仅 IPv6"
                                },
                                leadingMark = "mode",
                                modifier = Modifier.weight(1f),
                                onClick = { protocolMenuOpen = true }
                            )
                            if (protocolMenuOpen) {
                                ProtocolPickDialog(
                                    title = "Ping 协议",
                                    current = pingProtocol,
                                    onDismiss = { protocolMenuOpen = false },
                                    onPick = {
                                        onPingProtocolChange(it)
                                        protocolMenuOpen = false
                                    }
                                )
                            }
                        }
                        Text(
                            "超时自动推荐，可手动改。",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onSave, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) {
                    Icon(Icons.Filled.Save, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("保存参数", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onRestoreDefault, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) {
                    Icon(Icons.Filled.Refresh, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("恢复默认", fontSize = 13.sp)
                }
            }
        }
        item { Spacer(Modifier.height(70.dp)) }
    }
}


@Composable
private fun TestPage(
    listState: LazyListState,
    pingFocusRequest: Int,
    sessionExpanded: Boolean,
    onSessionExpandedChange: (Boolean) -> Unit,
    mode: TestMode,
    status: String,
    target: String,
    testHost: String,
    testPort: Int,
    chartMode: ChartMode,
    onChartModeChange: (ChartMode) -> Unit,
    chartPoints: List<ChartPoint>,
    pingPoints: List<PingPoint>,
    pingIntervalLabel: String,
    pingActiveTargetLabel: String,
    pingRunning: Boolean,
    pingDurationMs: Long,
    pingJitterMs: Double?,
    pingLogCount: Int,
    onStartPing: () -> Unit,
    onStopPing: () -> Unit,
    onClearPing: () -> Unit,
    onShowPingLog: () -> Unit,
    isAdding: Boolean,
    runPhase: RunPhase,
    releaseUi: ReleaseUiState,
    onStart: () -> Unit,
    onStopAdding: () -> Unit,
    onRelease: () -> Unit,
    onExport: () -> Unit,
    ipv4Stats: ProtocolStats,
    ipv6Stats: ProtocolStats,
    showIpv4: Boolean,
    showIpv6: Boolean,
    maskPrivacy: Boolean,
    updateBadge: Boolean,
    updateProgress: Int?,
    onVersionClick: () -> Unit,
    logs: List<LogLine>,
    onMoreLogs: () -> Unit,
    onMoreFailure: () -> Unit
) {
    val context = LocalContext.current
    val startEnabled = runPhase == RunPhase.Idle || runPhase == RunPhase.Finished || runPhase == RunPhase.Failed
    val stopEnabled = isAdding && (runPhase == RunPhase.Running || runPhase == RunPhase.TopConfirm)
    val releaseBusy = runPhase == RunPhase.Stopping || runPhase == RunPhase.Releasing
    val phaseLabel = when {
        releaseBusy -> "● 释放中"
        isAdding && runPhase == RunPhase.TopConfirm -> "● 顶部确认"
        isAdding -> "● 运行中"
        else -> status
    }
    val defaultCards = listOf("sessions", "release", "ping", "diagnosis", "logs")
    val initialTestOrder = remember {
        val saved = loadCardOrder(context.applicationContext, "test_card_order_v4", defaultCards)
        (saved.filterNot { it == "control" } + defaultCards.filterNot { it in saved }).distinct()
    }
    var testCardOrder by remember { mutableStateOf(initialTestOrder) }
    val totalSessionAttempts = ipv4Stats.totalAttempts + ipv6Stats.totalAttempts
    var previousSessionAttempts by remember { mutableStateOf(totalSessionAttempts) }

    LaunchedEffect(isAdding) {
        if (isAdding) onSessionExpandedChange(true)
    }
    LaunchedEffect(totalSessionAttempts) {
        if (previousSessionAttempts == 0 && totalSessionAttempts > 0) onSessionExpandedChange(true)
        previousSessionAttempts = totalSessionAttempts
    }

    val visibleIds = remember(releaseUi.visible, releaseBusy) {
        buildList {
            add("sessions")
            if (releaseUi.visible || releaseBusy) add("release")
            add("ping")
            add("diagnosis")
            add("logs")
        }
    }
    val visibleOrder = remember(testCardOrder, visibleIds) {
        (testCardOrder.filter { it in visibleIds } + visibleIds.filterNot { it in testCardOrder }).distinct()
    }
    val ipv4ChartPoints = remember(chartPoints) { chartPoints.filter { it.protocol == IpProtocol.IPV4 } }
    val ipv6ChartPoints = remember(chartPoints) { chartPoints.filter { it.protocol == IpProtocol.IPV6 } }
    fun updateTestOrder(nextVisible: List<String>) {
        val merged = (nextVisible + testCardOrder.filterNot { it in nextVisible } + defaultCards.filterNot { it in nextVisible || it in testCardOrder }).distinct()
        testCardOrder = merged
        saveCardOrder(context.applicationContext, "test_card_order_v4", merged)
    }

    LaunchedEffect(pingFocusRequest, visibleOrder) {
        if (pingFocusRequest <= 0) return@LaunchedEffect
        val pingIndex = visibleOrder.indexOf("ping")
        if (pingIndex >= 0) listState.animateScrollToItem(pingIndex + 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            PageTitle("宽带会话测试器", null, updateBadge, updateProgress, onVersionClick)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusChip(mode.label, BlueSoft, Blue)
                StatusChip(phaseLabel, if (releaseBusy) Color(0xFFFFF7ED) else GreenSoft, if (releaseBusy) Orange else Green)
                StatusChip("◎ 目标 $target", Color.White, TextDark)
            }
        }
        items(visibleOrder, key = { it }) { cardId ->
            ReorderableCardItem(id = cardId, order = visibleOrder, onOrderChange = ::updateTestOrder) {
                when (cardId) {
                    "sessions" -> SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("connection_tree", "连接数测试", Blue)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onSessionExpandedChange(!sessionExpanded) }) {
                                Text(if (sessionExpanded) "收起" else "展开", color = Muted, fontSize = 12.sp)
                            }
                        }
                        Text(
                            when {
                                releaseBusy -> "正在释放连接，完成后会自动恢复开始按钮"
                                isAdding -> "正在运行 · 定速发射 · 同步 Ping 监测"
                                else -> "状态：$status"
                            },
                            color = if (isAdding) Green else if (releaseBusy) Orange else Muted,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onStart, enabled = startEnabled, modifier = Modifier.weight(1f).height(40.dp), shape = ShapeM) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("开始", fontSize = 13.sp)
                            }
                            Button(
                                onClick = onStopAdding,
                                enabled = stopEnabled,
                                colors = ButtonDefaults.buttonColors(containerColor = RedSoft, contentColor = ErrorRed),
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = ShapeM
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("停止", fontSize = 13.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onRelease, enabled = !releaseBusy, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("强制释放", fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("导出", fontSize = 13.sp)
                            }
                        }
                        if (sessionExpanded) {
                            HorizontalDivider(color = Border.copy(alpha = 0.72f))
                            CombinedSessionStatsCard(
                                ipv4Stats = ipv4Stats,
                                ipv6Stats = ipv6Stats,
                                maskPrivacy = maskPrivacy,
                                ipv4Points = ipv4ChartPoints,
                                ipv6Points = ipv6ChartPoints,
                                showIpv4 = showIpv4,
                                showIpv6 = showIpv6,
                                chartMode = chartMode,
                                onChartModeChange = onChartModeChange,
                                testActive = isAdding,
                                embedded = true
                            )
                        }
                    }
                    "release" -> ReleaseProgressCard(releaseUi)
                    "ping" -> PingCompactChartCard(
                        pingPoints = pingPoints,
                        intervalLabel = pingIntervalLabel,
                        activeTargetLabel = pingActiveTargetLabel,
                        running = pingRunning,
                        durationMs = pingDurationMs,
                        jitterMs = pingJitterMs,
                        logCount = pingLogCount,
                        onStart = onStartPing,
                        onStop = onStopPing,
                        onClear = onClearPing,
                        onLog = onShowPingLog
                    )
                    "diagnosis" -> DiagnosisAdviceInlineCard(mode, ipv4Stats, ipv6Stats)
                    "logs" -> RecentLogCard(logs, maskPrivacy, onMoreLogs)
                }
            }
        }
        item { Spacer(Modifier.height(70.dp)) }
    }

}



@Composable
private fun ReleaseProgressCard(releaseUi: ReleaseUiState) {
    val total = releaseUi.total.coerceAtLeast(0)
    val closedRaw = releaseUi.closed.coerceIn(0, total.coerceAtLeast(releaseUi.closed))
    val animatedClosed by animateIntAsState(
        targetValue = closedRaw,
        animationSpec = tween(durationMillis = 420),
        label = "releaseClosed"
    )
    val closed = animatedClosed.coerceIn(0, total.coerceAtLeast(animatedClosed))
    val animatedProgress by animateFloatAsState(
        targetValue = releaseUi.progress,
        animationSpec = tween(durationMillis = 420),
        label = "releaseProgress"
    )
    val progress = animatedProgress.coerceIn(0f, 1f)
    val percent = (progress * 100f).roundToInt().coerceIn(0, 100)
    val rightLabel = if (releaseUi.finished) "耗时" else "预计剩余"
    val rightValue = if (releaseUi.finished) {
        formatReleaseDuration(releaseUi.elapsedMs)
    } else {
        formatReleaseEta(releaseUi.etaSeconds)
    }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("↧", if (releaseUi.finished) "释放完成" else "正在释放连接", Purple)
            Spacer(Modifier.weight(1f))
            StatusChip(if (releaseUi.finished) "已完成" else "进行中", if (releaseUi.finished) GreenSoft else Color(0xFFFFF7ED), if (releaseUi.finished) Green else Orange, compact = true)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.width(122.dp).height(122.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                    drawArc(
                        color = Color(0xFFE9D5FF),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 14f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Purple,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 14f, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${percent}%", color = TextDark, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("释放进度", color = Muted, fontSize = 11.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$closed / $total", color = TextDark, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text(releaseUi.message.ifBlank { "正在关闭 Socket 连接，请勿退出页面" }, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricTile("释放速度", if (releaseUi.speedPerSecond > 0) "${releaseUi.speedPerSecond}/s" else "—", Blue, Modifier.weight(1f))
                    MetricTile(rightLabel, rightValue, Orange, Modifier.weight(1f))
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), ShapeM).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReleaseStep("停止新增连接", true)
            ReleaseStep("正在释放 Socket", releaseUi.closed > 0 || releaseUi.finished, !releaseUi.finished)
            ReleaseStep("清理连接资源", releaseUi.finished)
            ReleaseStep("更新界面状态", releaseUi.finished)
        }
        Text("释放期间会冻结曲线渲染，只保留进度刷新，避免 UI 卡顿和按钮状态不同步。", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ReleaseStep(title: String, done: Boolean, active: Boolean = false) {
    val color = when {
        done -> Green
        active -> Blue
        else -> Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (done) "✓" else if (active) "●" else "○", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(22.dp))
        Text(title, color = TextDark, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(if (done) "已完成" else if (active) "进行中" else "等待中", color = color, fontSize = 11.sp)
    }
}

@Composable
private fun FullRunLogPage(
    logs: List<LogLine>,
    logSizeKb: Int,
    maskPrivacy: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 5.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.width(52.dp)) {
                    Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
                Text("运行日志", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = TextDark, modifier = Modifier.weight(1f))
            }
        }
        item {
            SoftCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("导出日志", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("清理日志", fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("□", "全部日志", Blue)
                    Spacer(Modifier.weight(1f))
                    Text("${logs.takeLast(500).size} 条 · 占用 ${logSizeKb}KB", color = Muted, fontSize = 11.sp)
                }
                if (logs.isEmpty()) {
                    Text("暂无日志", color = Muted, fontSize = 12.sp)
                } else {
                    val visibleLogs = logs.takeLast(500).asReversed()
                    visibleLogs.forEachIndexed { index, line ->
                        CompactLogLine(line, maskPrivacy)
                        if (index != visibleLogs.lastIndex) {
                            HorizontalDivider(color = Border.copy(alpha = 0.65f), thickness = 0.6.dp)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}


@Composable
private fun LogsPage(
    logs: List<LogLine>,
    history: List<SessionSummary>,
    historyLimit: String,
    historyPeriod: String,
    historySizeKb: Int,
    historySavedCount: Int,
    historyCounts: HistoryCounts,
    maskPrivacy: Boolean,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onHistoryLimitChange: (String) -> Unit,
    onHistoryPeriodChange: (String) -> Unit,
    onEditRemark: (SessionSummary) -> Unit,
    onDeleteHistory: (SessionSummary) -> Unit,
    onHistoryDetail: (SessionSummary) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 5.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("检测历史", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                    Text("已保存 ${historySavedCount} 条 · 占用 ${historySizeKb}KB", color = Muted, fontSize = 11.sp)
                }
                OutlinedButton(onClick = onClear, shape = ShapeM, modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.width(15.dp).height(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("清理", fontSize = 11.sp)
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PeriodCountChip("今天 ${historyCounts.today} 条", "最多 30 条", GreenSoft, Green, historyPeriod == "TODAY") { onHistoryPeriodChange("TODAY") }
                PeriodCountChip("昨天 ${historyCounts.yesterday} 条", "最多 30 条", BlueSoft, Blue, historyPeriod == "YESTERDAY") { onHistoryPeriodChange("YESTERDAY") }
                PeriodCountChip("本周 ${historyCounts.week} 条", "最多 100 条", Color(0xFFF3E8FF), Purple, historyPeriod == "WEEK") { onHistoryPeriodChange("WEEK") }
            }
            Text(
                "按周期保存：今天最多30条，昨天最多30条，本周最多100条",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("10", "30", "100").forEach { limit ->
                    val selected = historyLimit == limit
                    SoftChoicePill(
                        text = "显示 ${limit} 条",
                        selected = selected,
                        onClick = { onHistoryLimitChange(limit) },
                        compact = true
                    )
                }
            }
        }
        if (history.isEmpty()) {
            item { SoftCard { Text("暂无历史记录", color = TextDark, fontSize = 13.sp) } }
        } else {
            itemsIndexed(
                history.take(historyLimit.toIntOrNull()?.coerceIn(10, 100) ?: 30),
                key = { index, item -> "${item.id}_${item.startedAtEpochMs}_$index" }
            ) { _, item ->
                SwipeDeleteHistoryCard(
                    item = item,
                    maskPrivacy = maskPrivacy,
                    onClick = { onHistoryDetail(item) },
                    onEditRemark = { onEditRemark(item) },
                    onDelete = { onDeleteHistory(item) }
                )
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun PeriodCountChip(title: String, subtitle: String, bg: Color, fg: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = ShapeM
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = if (selected) bg else Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.8.dp, if (selected) fg.copy(alpha = 0.18f) else Border.copy(alpha = 0.72f)),
        modifier = Modifier
            .width(104.dp)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}


@Composable
private fun PageTitle(
    title: String,
    subtitle: String?,
    updateBadge: Boolean = false,
    updateProgress: Int? = null,
    onVersionClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
            if (title == "宽带会话测试器") {
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.clickable { onVersionClick?.invoke() }) {
                    Row(
                        modifier = Modifier
                            .background(BlueSoft, RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(displayVersionName(currentAppVersionName(LocalContext.current)), color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (updateProgress != null) {
                            Spacer(Modifier.width(4.dp))
                            Text("${updateProgress}%", color = Purple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (updateBadge) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-3).dp)
                                .width(7.dp)
                                .height(7.dp)
                                .background(ErrorRed, RoundedCornerShape(50))
                        )
                    }
                }
            }
        }
        subtitle?.let {
            Text(it, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VersionInfoDialog(
    checkingUpdate: Boolean,
    updateAvailable: Boolean,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onOpenGithub: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) }
        },
        dismissButton = {
            TextButton(onClick = onOpenGithub) { Text("打开 GitHub", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        },
        title = { Text("版本信息", fontWeight = FontWeight.ExtraBold, color = TextDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("当前版本", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    StatusChip(displayVersionName(currentAppVersionName(LocalContext.current)), BlueSoft, Blue, compact = true)
                }
                VersionLine(displayVersionName(currentAppVersionName(LocalContext.current)), "正式版：修复 Ping IPv4/AUTO 解析、0ms异常、目标历史与图表目标显示。")
                VersionLine("V1.0.6", "目标与模式合并，网络信息折叠，Ping日志持久化，卡片长按拖动排序。")
                VersionLine("V1.0.5", "Ping采集和界面展示分离，图表按秒聚合，日志弹窗分组优化。")
                VersionLine("V1.0.4", "新增独立Ping、Ping参数、响应日志与NAT兼容判定。")
                VersionLine("V1.0.3", "修复版本显示、释放通知重复和底部通知样式。")
                VersionLine("V1.0.0", "正式版：定速发射核心、释放耗时、网络检测与更新流程。")
                HorizontalDivider(color = Border)
                Button(
                    onClick = onCheckUpdate,
                    enabled = !checkingUpdate,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = ShapeM
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (checkingUpdate) "正在检测..." else if (updateAvailable) "检测更新（发现新版）" else "检测更新", fontSize = 13.sp)
                }
            }
        },
        shape = ShapeL
    )
}

@Composable
private fun UpdateAvailableDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onIgnore: () -> Unit,
    onOpenGithub: () -> Unit,
    onUpdateNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onUpdateNow, shape = ShapeM) { Text("一键更新", fontSize = 13.sp) }
        },
        dismissButton = {
            FlowRow(
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onIgnore) { Text("忽略本版", fontSize = 13.sp) }
                TextButton(onClick = onOpenGithub) { Text("打开 GitHub", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkBox("download", BlueSoft, Blue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val context = LocalContext.current
                    Text(info.title.ifBlank { "发现新版本 ${formatVersionBuild(info.versionName, info.versionCode)}" }, fontWeight = FontWeight.ExtraBold, color = TextDark)
                    Text(
                        "当前 ${formatVersionBuild(currentAppVersionName(context), currentAppVersionCode(context))} → 最新 ${formatVersionBuild(info.versionName, info.versionCode)}",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Muted) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("更新内容：", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                info.content.take(6).forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("${index + 1}.", color = Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(20.dp))
                        Text(item, color = TextDark, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    }
                }
                Text("忽略本版后不会自动弹出当前版本；手动检测更新仍可再次打开，直到下一个版本会重新自动提醒。", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                Text("后台下载时顶部会显示下载横幅；如果速度较慢，可打开 GitHub 手动下载。", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        },
        shape = ShapeL
    )
}

@Composable
private fun UpdateDownloadDialog(
    info: UpdateInfo?,
    state: UpdateDownloadUi,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenGithub: () -> Unit
) {
    val titleText = when {
        state.finished -> "下载完成"
        state.failed -> if (state.message.contains("取消")) "已取消" else "下载失败"
        state.active -> "正在下载更新"
        else -> "下载更新"
    }
    val iconTint = when {
        state.finished -> Green
        state.failed -> ErrorRed
        else -> Blue
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            when {
                state.finished -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = onInstall, shape = ShapeM) { Text("立即安装", fontSize = 13.sp) }
                        OutlinedButton(onClick = onDismiss, shape = ShapeM) { Text("稍后安装", fontSize = 13.sp) }
                    }
                }
                state.failed -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onRetry, shape = ShapeM) { Text("重新下载", fontSize = 13.sp) }
                        TextButton(onClick = onOpenGithub) { Text("打开 GitHub", fontSize = 13.sp) }
                        TextButton(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) }
                    }
                }
                state.active -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onCancelDownload, shape = ShapeM) { Text("取消下载", fontSize = 13.sp) }
                        Button(onClick = onDismiss, shape = ShapeM) { Text("后台下载", fontSize = 13.sp) }
                    }
                }
                else -> {
                    Button(onClick = onRetry, shape = ShapeM) { Text("开始下载", fontSize = 13.sp) }
                }
            }
        },
        dismissButton = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.finished) Icons.Filled.CheckCircle else if (state.failed) Icons.Filled.WarningAmber else Icons.Filled.Download,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.width(28.dp).height(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(titleText, fontWeight = FontWeight.ExtraBold, color = TextDark)
                    Text(info?.let { formatVersionBuild(it.versionName, it.versionCode) } ?: "", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Muted) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("NetSessionTester-${info?.let { formatVersionBuild(it.versionName, it.versionCode).replace(" ", "-") } ?: "update"}.apk", color = TextDark, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (state.failed) ErrorRed else Blue,
                    trackColor = if (state.failed) RedSoft else BlueSoft
                )
                Text("${state.progress}%  ·  ${formatBytes(state.downloadedBytes)} / ${if (state.totalBytes > 0) formatBytes(state.totalBytes) else "未知大小"}", color = TextDark, fontSize = 12.sp)
                if (state.active) Text("速度：${formatSpeed(state.speedBytesPerSecond)}", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (state.message.isNotBlank()) {
                    val warn = state.message.contains("较慢") || state.failed
                    Text(
                        state.message,
                        color = if (warn) ErrorRed else Muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (warn) RedSoft else Color.Transparent, ShapeM)
                            .padding(if (warn) 8.dp else 0.dp)
                    )
                }
                val note = when {
                    state.failed -> "重新下载会自动清理未完成文件（.part），并从 0% 重新开始。"
                    state.active -> "关闭卡片后会继续在后台下载，完成后会再次提示安装。"
                    state.finished -> "下载完成后如无法安装，请检查未知来源安装权限。"
                    else -> "下载失败时可重新下载或打开 GitHub 手动处理。"
                }
                Text(note, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        },
        shape = ShapeL
    )
}

@Composable
private fun OneUiSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState) { data ->
        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(32.dp)
                        .background(BlueSoft, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Blue,
                        modifier = Modifier.width(19.dp).height(19.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Text(
                    text = data.visuals.message,
                    color = TextDark,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNoticeBanner(
    state: BottomNoticeUi,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.visible && state.message.isNotBlank(),
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(160)) + slideInVertically(animationSpec = tween(220)) { it / 2 },
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(animationSpec = tween(200)) { it / 2 }
    ) {
        val tint = when (state.tone) {
            BottomNoticeTone.Success -> Green
            BottomNoticeTone.Warning -> Orange
            BottomNoticeTone.Error -> ErrorRed
            BottomNoticeTone.Info -> Blue
        }
        val soft = when (state.tone) {
            BottomNoticeTone.Success -> GreenSoft
            BottomNoticeTone.Warning -> Color(0xFFFFF7ED)
            BottomNoticeTone.Error -> RedSoft
            BottomNoticeTone.Info -> BlueSoft
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(30.dp)
                        .background(soft, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.tone == BottomNoticeTone.Error || state.tone == BottomNoticeTone.Warning) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.width(18.dp).height(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = state.message,
                    color = TextDark,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UpdateDownloadBanner(
    state: UpdateDownloadUi,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when {
        state.finished -> "更新包已下载"
        state.failed -> if (state.message.contains("取消")) "下载已取消" else "更新下载失败"
        state.active -> "正在后台下载更新"
        else -> "更新下载"
    }
    val color = when {
        state.finished -> Green
        state.failed -> ErrorRed
        else -> Blue
    }
    var dragX by remember { mutableStateOf(0f) }
    val dragOffset by animateFloatAsState(
        targetValue = dragX,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "update_banner_drag"
    )
    val dismissible = state.failed || state.finished || state.message.contains("取消")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(dragOffset.roundToInt(), 0) }
            .pointerInput(dismissible) {
                if (dismissible) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragX += amount
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(dragX) > 80f) onDismiss() else dragX = 0f
                        },
                        onDragCancel = { dragX = 0f }
                    )
                }
            }
            .clickable { if (state.finished) onInstall() else onOpen() },
        shape = ShapeM,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.finished) Icons.Filled.CheckCircle else if (state.failed) Icons.Filled.WarningAmber else Icons.Filled.Download,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.width(18.dp).height(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    val detail = when {
                        state.finished -> "点击安装，右滑或点 × 可关闭"
                        state.failed -> state.message.ifBlank { "点击查看详情，右滑或点 × 可关闭" }
                        state.active -> "${state.progress}% · ${formatBytes(state.downloadedBytes)} / ${if (state.totalBytes > 0) formatBytes(state.totalBytes) else "未知大小"} · ${formatSpeed(state.speedBytesPerSecond)}"
                        else -> "点击查看"
                    }
                    Text(detail, color = Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                }
                if (dismissible) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.width(30.dp).height(30.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭下载提示", tint = Muted, modifier = Modifier.width(18.dp).height(18.dp))
                    }
                } else {
                    Text(if (state.finished) "安装" else "查看", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (state.active || state.finished) {
                LinearProgressIndicator(
                    progress = { if (state.finished) 1f else state.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = color,
                    trackColor = BlueSoft
                )
            }
        }
    }
}

@Composable
private fun VersionLine(version: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(version, color = Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(92.dp))
        Text(text, color = TextDark, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SoftCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeL,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(mark: String, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MarkBox(mark, color.copy(alpha = 0.12f), color)
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
    }
}

@Composable
private fun MarkBox(mark: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(11.dp))
            .padding(7.dp),
        contentAlignment = Alignment.Center
    ) {
        AppIconGlyph(mark = mark, color = fg, modifier = Modifier.width(18.dp).height(18.dp))
        val badge = when (mark) {
            "ipv4", "session_ipv4" -> "4"
            "ipv6", "session_ipv6" -> "6"
            else -> null
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .width(13.dp)
                    .height(13.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, fg.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(badge, color = fg, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 8.sp)
            }
        }
    }
}

@Composable
private fun AppIconGlyph(mark: String, color: Color, modifier: Modifier = Modifier) {
    if (usesCustomNetGlyph(mark)) {
        NetGlyph(mark = mark, color = color, modifier = modifier)
    } else {
        Icon(iconFor(mark), contentDescription = null, tint = color, modifier = modifier)
    }
}


private fun usesCustomNetGlyph(mark: String): Boolean = mark in setOf(
    "network_info", "ipv4", "ipv6", "session_ipv4", "session_ipv6", "local_ip", "mapping", "nat", "nat1", "nat2", "nat3", "nat4", "filter", "priority", "connection_tree",
    "egress", "dns", "nslookup", "tracket", "mtu", "roaming", "ping", "∿",
    "target", "mode", "tune", "≡", "port", "host", "address", "□", "log",
    "latency", "time", "hourglass", "count", "privacy", "privacy_on", "carrier", "wifi", "confidence",
    "note", "download", "chart"
)

@Composable
private fun NetGlyph(mark: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = (size.minDimension * 0.105f).coerceAtLeast(1.6f)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round)
        val thin = Stroke(width = sw * 0.72f, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, a: Float = 1f, st: Stroke = stroke) {
            drawLine(color.copy(alpha = a), Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), strokeWidth = st.width, cap = StrokeCap.Round)
        }
        fun dot(x: Float, y: Float, r: Float = 0.055f, a: Float = 1f) {
            drawCircle(color.copy(alpha = a), radius = r * size.minDimension, center = Offset(x * w, y * h))
        }
        fun arrow(x1: Float, y1: Float, x2: Float, y2: Float, a: Float = 1f) {
            line(x1, y1, x2, y2, a)
            val dx = x2 - x1
            val dy = y2 - y1
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val ux = dx / len
            val uy = dy / len
            val px = -uy
            val py = ux
            val ah = 0.12f
            val aw = 0.055f
            line(x2, y2, x2 - ux * ah + px * aw, y2 - uy * ah + py * aw, a, thin)
            line(x2, y2, x2 - ux * ah - px * aw, y2 - uy * ah - py * aw, a, thin)
        }
        fun globe(cx: Float = 0.5f, cy: Float = 0.5f, r: Float = 0.33f) {
            drawCircle(color, radius = r * size.minDimension, center = Offset(cx * w, cy * h), style = stroke)
            line(cx - r, cy, cx + r, cy, 0.72f, thin)
            line(cx, cy - r, cx, cy + r, 0.72f, thin)
            val p1 = Path().apply {
                moveTo((cx - r * 0.42f) * w, (cy - r * 0.88f) * h)
                cubicTo((cx - r * 0.72f) * w, cy * h, (cx - r * 0.72f) * w, cy * h, (cx - r * 0.42f) * w, (cy + r * 0.88f) * h)
            }
            val p2 = Path().apply {
                moveTo((cx + r * 0.42f) * w, (cy - r * 0.88f) * h)
                cubicTo((cx + r * 0.72f) * w, cy * h, (cx + r * 0.72f) * w, cy * h, (cx + r * 0.42f) * w, (cy + r * 0.88f) * h)
            }
            drawPath(p1, color.copy(alpha = 0.72f), style = thin)
            drawPath(p2, color.copy(alpha = 0.72f), style = thin)
        }
        fun shield(cx: Float = 0.5f, cy: Float = 0.5f, scale: Float = 1f) {
            val p = Path().apply {
                moveTo(cx * w, (cy - 0.33f * scale) * h)
                lineTo((cx + 0.27f * scale) * w, (cy - 0.19f * scale) * h)
                lineTo((cx + 0.20f * scale) * w, (cy + 0.24f * scale) * h)
                lineTo(cx * w, (cy + 0.38f * scale) * h)
                lineTo((cx - 0.20f * scale) * w, (cy + 0.24f * scale) * h)
                lineTo((cx - 0.27f * scale) * w, (cy - 0.19f * scale) * h)
                close()
            }
            drawPath(p, color, style = stroke)
        }
        when (mark) {
            "network_info" -> {
                // 网络信息：地球网格，表示整体网络环境。
                globe(0.50f, 0.50f, 0.32f)
            }
            "ipv4", "ipv6", "host", "address" -> globe()
            "local_ip" -> {
                // 本地IP：清晰小手机。
                drawRoundRect(
                    color,
                    topLeft = Offset(0.34f*w, 0.16f*h),
                    size = androidx.compose.ui.geometry.Size(0.32f*w, 0.68f*h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.07f*w,0.07f*h),
                    style = stroke
                )
                line(0.43f,0.24f,0.57f,0.24f,0.75f,thin)
                dot(0.50f,0.76f,0.020f,0.8f)
            }
            "mapping" -> {
                // 公网映射：上面向左箭头，下面向右箭头，避免和 IPv4/IPv6 地球图标混淆。
                arrow(0.78f, 0.36f, 0.23f, 0.36f)
                arrow(0.22f, 0.64f, 0.77f, 0.64f)
                dot(0.50f, 0.50f, 0.035f, 0.72f)
            }
            "nat", "nat1", "nat2", "nat3", "nat4" -> {
                shield()
                val d = mark.removePrefix("nat").takeIf { it in listOf("1","2","3","4") }
                if (d != null) {
                    drawContext.canvas.nativeCanvas.drawText(
                        d,
                        0.50f * w - Paint().apply { textSize = h * 0.38f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }.measureText(d) / 2f,
                        0.59f * h,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); textSize = h * 0.38f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.LEFT }
                    )
                } else {
                    line(0.30f, 0.47f, 0.70f, 0.47f, 0.8f, thin)
                    line(0.30f, 0.57f, 0.70f, 0.57f, 0.8f, thin)
                }
            }
            "filter" -> {
                // 回包限制：网状防火墙，避免再用感叹号/通用盾牌。
                drawRoundRect(
                    color,
                    topLeft = Offset(0.20f * w, 0.22f * h),
                    size = androidx.compose.ui.geometry.Size(0.60f * w, 0.56f * h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.06f * w, 0.06f * h),
                    style = stroke
                )
                line(0.20f, 0.41f, 0.80f, 0.41f, 0.72f, thin)
                line(0.20f, 0.59f, 0.80f, 0.59f, 0.72f, thin)
                line(0.40f, 0.22f, 0.40f, 0.78f, 0.72f, thin)
                line(0.60f, 0.22f, 0.60f, 0.78f, 0.72f, thin)
                arrow(0.90f, 0.50f, 0.75f, 0.50f, 0.95f)
                line(0.73f, 0.38f, 0.84f, 0.50f, 0.85f, thin)
                line(0.73f, 0.62f, 0.84f, 0.50f, 0.85f, thin)
            }
            "priority" -> {
                // 优先级：更清晰的奖杯。
                val cup = Path().apply {
                    moveTo(0.32f*w, 0.26f*h)
                    lineTo(0.68f*w, 0.26f*h)
                    lineTo(0.62f*w, 0.58f*h)
                    cubicTo(0.58f*w, 0.66f*h, 0.42f*w, 0.66f*h, 0.38f*w, 0.58f*h)
                    close()
                }
                drawPath(cup, color, style = stroke)
                drawArc(color, 88f, 120f, false, topLeft=Offset(0.13f*w,0.31f*h), size=androidx.compose.ui.geometry.Size(0.31f*w,0.26f*h), style=stroke)
                drawArc(color, -28f, 120f, false, topLeft=Offset(0.56f*w,0.31f*h), size=androidx.compose.ui.geometry.Size(0.31f*w,0.26f*h), style=stroke)
                line(0.50f,0.64f,0.50f,0.76f,1f,stroke)
                line(0.35f,0.80f,0.65f,0.80f,1f,stroke)
                line(0.40f,0.88f,0.60f,0.88f,0.9f,thin)
                dot(0.50f,0.40f,0.035f,0.9f)
            }
            "egress" -> {
                // 出网端口：U形端口/接口。
                val p = Path().apply {
                    moveTo(0.25f*w,0.22f*h)
                    lineTo(0.25f*w,0.55f*h)
                    cubicTo(0.25f*w,0.76f*h,0.75f*w,0.76f*h,0.75f*w,0.55f*h)
                    lineTo(0.75f*w,0.22f*h)
                }
                drawPath(p, color, style=stroke)
                dot(0.38f,0.28f,0.035f); dot(0.62f,0.28f,0.035f)
            }
            "dns" -> {
                // DNS诊断：诊断波形，避免小尺寸下地球+放大镜糊成一团。
                val wave = Path().apply {
                    moveTo(0.14f*w, 0.55f*h)
                    lineTo(0.30f*w, 0.55f*h)
                    lineTo(0.39f*w, 0.34f*h)
                    lineTo(0.52f*w, 0.72f*h)
                    lineTo(0.62f*w, 0.48f*h)
                    lineTo(0.72f*w, 0.55f*h)
                    lineTo(0.86f*w, 0.55f*h)
                }
                drawPath(wave, color, style = stroke)
                drawCircle(color.copy(alpha = 0.65f), radius = 0.05f*size.minDimension, center = Offset(0.39f*w,0.34f*h))
            }
            "nslookup" -> {
                // NSLookup：纯放大镜，避免和 DNS 诊断重复。
                drawCircle(color, radius=0.25f*size.minDimension, center=Offset(0.42f*w,0.42f*h), style=stroke)
                line(0.60f,0.60f,0.84f,0.84f)
            }
            "tracket" -> { line(0.18f,0.68f,0.38f,0.35f); line(0.38f,0.35f,0.62f,0.56f); line(0.62f,0.56f,0.83f,0.28f); dot(0.18f,0.68f); dot(0.38f,0.35f); dot(0.62f,0.56f); drawCircle(color.copy(alpha=0.55f), radius=0.12f*size.minDimension, center=Offset(0.83f*w,0.28f*h), style=thin) }
            "mtu" -> {
                // MTU检测：三条曲线组成弯曲小路。
                listOf(0.22f, 0.42f, 0.62f).forEachIndexed { idx, sx ->
                    val p = Path().apply {
                        moveTo(sx*w, 0.78f*h)
                        cubicTo((sx-0.10f)*w, 0.58f*h, (sx+0.15f)*w, 0.46f*h, (sx+0.02f)*w, 0.26f*h)
                    }
                    drawPath(p, color.copy(alpha = 1f - idx*0.18f), style=stroke)
                }
            }
            "roaming", "wifi" -> {
                // 漫游/WiFi：弧线和圆点更紧凑，避免上下分离。
                drawArc(color, 205f, 130f, false, topLeft=Offset(0.16f*w,0.22f*h), size=androidx.compose.ui.geometry.Size(0.68f*w,0.62f*h), style=stroke)
                drawArc(color, 210f, 120f, false, topLeft=Offset(0.30f*w,0.39f*h), size=androidx.compose.ui.geometry.Size(0.40f*w,0.37f*h), style=thin)
                drawArc(color, 218f, 104f, false, topLeft=Offset(0.41f*w,0.54f*h), size=androidx.compose.ui.geometry.Size(0.18f*w,0.16f*h), style=thin)
                dot(0.50f,0.75f,0.043f)
            }
            "ping", "∿" -> {
                // 独立Ping/Ping测试：仪表盘/速度表风格。
                drawArc(color, 205f, 130f, false, topLeft=Offset(0.18f*w,0.20f*h), size=androidx.compose.ui.geometry.Size(0.64f*w,0.64f*h), style=stroke)
                line(0.50f,0.58f,0.66f,0.38f,1f,stroke)
                dot(0.50f,0.58f,0.04f)
                line(0.28f,0.72f,0.72f,0.72f,0.75f,thin)
            }
            "connection_tree" -> {
                // 连接数测试：短横向闪电 + 电弧 + 小端点，紧凑且不糊。
                val bolt = Path().apply {
                    moveTo(0.24f*w, 0.52f*h)
                    lineTo(0.44f*w, 0.52f*h)
                    lineTo(0.36f*w, 0.66f*h)
                    lineTo(0.61f*w, 0.66f*h)
                    lineTo(0.51f*w, 0.80f*h)
                    lineTo(0.78f*w, 0.80f*h)
                }
                drawPath(bolt, color, style = stroke)
                drawArc(color.copy(alpha = 0.72f), 210f, 120f, false, topLeft=Offset(0.38f*w,0.20f*h), size=androidx.compose.ui.geometry.Size(0.44f*w,0.48f*h), style=thin)
                drawArc(color.copy(alpha = 0.46f), 210f, 120f, false, topLeft=Offset(0.50f*w,0.30f*h), size=androidx.compose.ui.geometry.Size(0.28f*w,0.32f*h), style=thin)
                dot(0.22f, 0.52f, 0.030f)
                dot(0.78f, 0.80f, 0.030f)
            }
            "session_ipv4", "session_ipv6" -> {
                globe(0.50f, 0.50f, 0.32f)
            }
            "target" -> { drawRoundRect(color, Offset(0.18f*w,0.22f*h), androidx.compose.ui.geometry.Size(0.35f*w,0.46f*h), androidx.compose.ui.geometry.CornerRadius(0.04f*w,0.04f*h), style=stroke); arrow(0.53f,0.38f,0.82f,0.25f); arrow(0.53f,0.55f,0.82f,0.70f) }
            "port" -> { drawRoundRect(color, Offset(0.20f*w,0.24f*h), androidx.compose.ui.geometry.Size(0.42f*w,0.45f*h), androidx.compose.ui.geometry.CornerRadius(0.04f*w,0.04f*h), style=stroke); line(0.09f,0.47f,0.88f,0.47f); dot(0.68f,0.47f,0.028f) }
            "mode", "tune", "≡" -> { line(0.18f,0.30f,0.82f,0.30f); dot(0.36f,0.30f,0.05f); line(0.18f,0.52f,0.82f,0.52f); dot(0.62f,0.52f,0.05f); line(0.18f,0.74f,0.82f,0.74f); dot(0.48f,0.74f,0.05f) }
            "□", "log", "note" -> {
                // 备注/日志：小笔记本。
                drawRoundRect(color, Offset(0.28f*w,0.16f*h), androidx.compose.ui.geometry.Size(0.46f*w,0.68f*h), androidx.compose.ui.geometry.CornerRadius(0.05f*w,0.05f*h), style=stroke)
                line(0.28f,0.28f,0.18f,0.28f,0.82f,thin); line(0.28f,0.44f,0.18f,0.44f,0.82f,thin); line(0.28f,0.60f,0.18f,0.60f,0.82f,thin)
                line(0.40f,0.38f,0.63f,0.38f,0.8f,thin); line(0.40f,0.55f,0.62f,0.55f,0.8f,thin)
            }
            "latency", "time", "hourglass" -> { drawCircle(color, radius=0.28f*size.minDimension, center=Offset(0.48f*w,0.50f*h), style=stroke); line(0.48f,0.50f,0.48f,0.32f); line(0.48f,0.50f,0.64f,0.58f) }
            "count" -> { arrow(0.72f,0.30f,0.32f,0.30f); arrow(0.28f,0.70f,0.68f,0.70f); dot(0.50f,0.50f,0.05f) }
            "privacy" -> {
                // 隐私关闭：横向小钥匙。
                drawCircle(color, radius=0.12f*size.minDimension, center=Offset(0.28f*w,0.50f*h), style=stroke)
                line(0.40f,0.50f,0.82f,0.50f,1f,stroke)
                line(0.62f,0.50f,0.62f,0.64f,1f,thin)
                line(0.73f,0.50f,0.73f,0.62f,1f,thin)
            }
            "privacy_on" -> {
                // 隐私开启：遮蔽的小眼睛。
                val eye = Path().apply {
                    moveTo(0.15f*w,0.50f*h)
                    cubicTo(0.32f*w,0.28f*h,0.68f*w,0.28f*h,0.85f*w,0.50f*h)
                    cubicTo(0.68f*w,0.72f*h,0.32f*w,0.72f*h,0.15f*w,0.50f*h)
                }
                drawPath(eye, color, style=stroke)
                dot(0.50f,0.50f,0.055f)
                line(0.22f,0.78f,0.82f,0.22f,1f,stroke)
            }
            "confidence" -> { drawCircle(color, radius=0.31f*size.minDimension, center=Offset(0.50f*w,0.50f*h), style=stroke); line(0.32f,0.51f,0.45f,0.64f); line(0.45f,0.64f,0.72f,0.36f) }
            "carrier" -> {
                // 运营商：蜂窝信号图标。
                line(0.22f,0.72f,0.22f,0.58f,1f,stroke)
                line(0.40f,0.72f,0.40f,0.46f,1f,stroke)
                line(0.58f,0.72f,0.58f,0.34f,1f,stroke)
                line(0.76f,0.72f,0.76f,0.22f,1f,stroke)
            }
            "download" -> { arrow(0.50f,0.18f,0.50f,0.62f); line(0.28f,0.78f,0.72f,0.78f) }
            "chart" -> { line(0.18f,0.74f,0.18f,0.58f); line(0.39f,0.74f,0.39f,0.42f); line(0.60f,0.74f,0.60f,0.28f); line(0.81f,0.74f,0.81f,0.50f) }
            else -> dot(0.50f,0.50f,0.18f)
        }
    }
}

@Composable
private fun FieldLabel(label: String) {
    Text(label, fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CleanField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingMark: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        singleLine = true,
        leadingIcon = leadingMark?.let { mark ->
            { AppIconGlyph(mark, Blue, Modifier.width(18.dp).height(18.dp)) }
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        shape = ShapeM,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().height(52.dp)
    )
}

@Composable
private fun HistoryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    history: List<String>,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
    leadingMark: String? = null,
    presets: List<TargetPreset> = emptyList(),
    onAdd: ((String) -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var addValue by remember { mutableStateOf("") }
    var addNote by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    val canOpen = history.isNotEmpty() || presets.isNotEmpty() || onAdd != null
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 12.sp) },
            singleLine = true,
            leadingIcon = leadingMark?.let { mark ->
                { AppIconGlyph(mark, Blue, Modifier.width(18.dp).height(18.dp)) }
            },
            trailingIcon = {
                TextButton(onClick = { if (canOpen) open = true }) { Text("⌄", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            shape = ShapeM,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        )
    }
    if (open) {
        var presetsExpanded by remember { mutableStateOf(true) }
        var historyExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onAdd != null) TextButton(onClick = { addValue = ""; addNote = ""; addError = null; addOpen = true }) { Text("添加", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { open = false }) { Text("取消") }
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("选择目标", fontWeight = FontWeight.Bold, color = TextDark, modifier = Modifier.weight(1f))
                    IconButton(onClick = { open = false }, modifier = Modifier.width(32.dp).height(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Muted, modifier = Modifier.width(18.dp).height(18.dp))
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (presets.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { presetsExpanded = !presetsExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("常用预设", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(if (presetsExpanded) "⌃" else "⌄", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        if (presetsExpanded) {
                            presets.forEach { preset ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), ShapeM).clickable { onPick(preset.value); open = false }.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(preset.value, color = TextDark, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (preset.note.isNotBlank()) Text(preset.note, color = Muted, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                    if (history.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { historyExpanded = !historyExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("自定义/最近", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(if (historyExpanded) "⌃" else "⌄", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        val shownHistory = if (historyExpanded) history.take(10) else history.take(3)
                        shownHistory.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), ShapeM).clickable { onPick(item); open = false }.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item, color = TextDark, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onDelete(item) }, modifier = Modifier.width(32.dp).height(32.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = null, tint = Muted, modifier = Modifier.width(16.dp).height(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            shape = ShapeL
        )
    }
    if (addOpen && onAdd != null) {
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text("添加目标", fontWeight = FontWeight.Bold, color = TextDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = addValue, onValueChange = { addValue = it; addError = null }, label = { Text("域名 / IP") }, singleLine = true, shape = ShapeM)
                    OutlinedTextField(value = addNote, onValueChange = { addNote = it }, label = { Text("备注（可选）") }, singleLine = true, shape = ShapeM)
                    addError?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
                    Text("最多保存10个自定义目标，预设不会被删除。", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = normalizeNetworkTargetInput(addValue, "")
                    if (normalized.error != null || normalized.host.isBlank()) {
                        addError = normalized.error ?: "请输入正确的域名或IP"
                    } else {
                        onAdd(normalized.host)
                        onValueChange(normalized.host)
                        addOpen = false
                        open = false
                    }
                }) { Text("添加", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text("取消") } },
            shape = ShapeL
        )
    }
}

@Composable
private fun ParamField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingMark: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.onlyDigits()) },
        label = { Text(label, maxLines = 1, fontSize = 11.sp) },
        singleLine = true,
        leadingIcon = leadingMark?.let { mark ->
            { AppIconGlyph(mark, Blue, Modifier.width(16.dp).height(16.dp)) }
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        shape = ShapeM,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.height(56.dp)
    )
}

@Composable
private fun SelectField(
    label: String,
    value: String,
    leadingMark: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier = modifier.height(56.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, maxLines = 1, fontSize = 11.sp) },
            singleLine = true,
            leadingIcon = { AppIconGlyph(leadingMark, Blue, Modifier.width(16.dp).height(16.dp)) },
            trailingIcon = { Text("⌄", color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            shape = ShapeM,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun SettingChoiceCard(
    label: String,
    value: String,
    leadingMark: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier = modifier) {
        FieldLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Color.White, ShapeM)
                .border(1.dp, Border, ShapeM)
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconGlyph(leadingMark, Blue, Modifier.width(18.dp).height(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(value, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("⌄", color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeSelector(mode: TestMode, onModeChange: (TestMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeM)
            .background(Color(0xFFF8FAFC), ShapeM)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val modes = listOf(TestMode.IPV4_ONLY, TestMode.IPV6_ONLY, TestMode.IPV4_THEN_IPV6)
        modes.forEach { item ->
            val selected = mode == item
            val interactionSource = remember(item) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(ShapeM)
                    .background(if (selected) Color.White else Color.Transparent, ShapeM)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onModeChange(item) }
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(item.label, color = if (selected) Blue else Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
}


private data class SessionChartSeries(
    val label: String,
    val color: Color,
    val points: List<ChartPoint>
)

private enum class SessionChartStage(val label: String) {
    GROWTH("增长"),
    CONFIRM("平台确认"),
    RESULT("结果")
}

private data class SessionStageRange(
    val startSec: Int,
    val endSec: Int,
    val stage: SessionChartStage
)

private fun sessionStageForPhase(phase: String): SessionChartStage = when {
    phase.contains("确认") || phase.contains("无增长") -> SessionChartStage.CONFIRM
    phase.contains("完成") ||
        phase.contains("上限") ||
        phase.contains("失败") ||
        phase.contains("停止") ||
        phase.contains("释放") ||
        phase.contains("中断") -> SessionChartStage.RESULT
    else -> SessionChartStage.GROWTH
}

private fun buildSessionStageRanges(points: List<ChartPoint>, minX: Int, maxX: Int): List<SessionStageRange> {
    if (points.isEmpty() || maxX <= minX) return emptyList()
    val ordered = points.sortedBy { it.elapsedSec }
    val result = mutableListOf<SessionStageRange>()
    var currentStage = sessionStageForPhase(ordered.first().phase)
    var rangeStart = minX
    ordered.drop(1).forEach { point ->
        val nextStage = sessionStageForPhase(point.phase)
        if (nextStage != currentStage) {
            result += SessionStageRange(rangeStart, point.elapsedSec.coerceAtLeast(rangeStart), currentStage)
            currentStage = nextStage
            rangeStart = point.elapsedSec
        }
    }
    result += SessionStageRange(rangeStart, maxX, currentStage)
    return result
}

private fun normalizeSessionPoints(points: List<ChartPoint>): List<ChartPoint> {
    val sorted = points.sortedBy { it.elapsedSec }
    val first = sorted.firstOrNull()?.elapsedSec ?: return emptyList()
    return sorted.map { it.copy(elapsedSec = (it.elapsedSec - first).coerceAtLeast(0)) }
}

private fun sessionPeakValue(stats: ProtocolStats, points: List<ChartPoint>): Int =
    listOf(
        stats.activeSessions,
        stats.maxStableSessions,
        stats.totalSuccess,
        points.maxOfOrNull { it.active } ?: 0
    ).maxOrNull() ?: 0

private fun sessionStableValue(stats: ProtocolStats, points: List<ChartPoint>): Int {
    val positive = points.sortedBy { it.elapsedSec }.filter { it.active > 0 }
    if (positive.isEmpty()) return stats.activeSessions.coerceAtLeast(0)
    val peakIndex = positive.indices.maxByOrNull { positive[it].active } ?: positive.lastIndex
    val afterPeak = positive.drop(peakIndex)
    val tailWindow = afterPeak.takeLast(10).ifEmpty { positive.takeLast(10) }
    return tailWindow.minOfOrNull { it.active } ?: positive.last().active
}

private fun sessionRetentionPercent(stats: ProtocolStats, points: List<ChartPoint>): Float {
    val peak = sessionPeakValue(stats, points)
    if (peak <= 0) return 0f
    return (sessionStableValue(stats, points).toFloat() * 100f / peak.toFloat()).coerceIn(0f, 100f)
}

@Composable
private fun SessionProtocolSelector(
    selected: SessionProtocolView,
    available: List<SessionProtocolView>,
    onSelected: (SessionProtocolView) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeM)
            .background(Color(0xFFF8FAFC), ShapeM)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        available.forEach { item ->
            val isSelected = selected == item
            val interactionSource = remember(item) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .shadow(if (isSelected) 4.dp else 0.dp, ShapeM, clip = false)
                    .clip(ShapeM)
                    .background(if (isSelected) BlueSoft else Color.Transparent, ShapeM)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelected(item) }
                    )
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.label,
                    color = if (isSelected) Blue else Muted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CombinedSessionStatsCard(
    ipv4Stats: ProtocolStats,
    ipv6Stats: ProtocolStats,
    maskPrivacy: Boolean,
    ipv4Points: List<ChartPoint>,
    ipv6Points: List<ChartPoint>,
    showIpv4: Boolean,
    showIpv6: Boolean,
    chartMode: ChartMode,
    onChartModeChange: (ChartMode) -> Unit,
    testActive: Boolean = false,
    embedded: Boolean = false
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val availableViews = remember(showIpv4, showIpv6) {
        buildList {
            if (showIpv4) add(SessionProtocolView.IPV4)
            if (showIpv6) add(SessionProtocolView.IPV6)
            if (showIpv4 && showIpv6) add(SessionProtocolView.COMPARE)
        }
    }
    var selectedView by rememberSaveable(showIpv4, showIpv6) {
        mutableStateOf(
            when {
                showIpv4 -> SessionProtocolView.IPV4
                showIpv6 -> SessionProtocolView.IPV6
                else -> SessionProtocolView.IPV4
            }
        )
    }
    var autoSwitchStage by rememberSaveable(showIpv4, showIpv6) { mutableStateOf(0) }
    var previousTestActive by remember { mutableStateOf(testActive) }

    LaunchedEffect(availableViews) {
        if (selectedView !in availableViews && availableViews.isNotEmpty()) selectedView = availableViews.first()
    }
    LaunchedEffect(
        testActive,
        ipv4Stats.totalAttempts,
        ipv6Stats.totalAttempts,
        showIpv4,
        showIpv6
    ) {
        if (showIpv4 && showIpv6) {
            when {
                testActive && !previousTestActive -> {
                    selectedView = SessionProtocolView.IPV4
                    autoSwitchStage = 1
                }
                testActive && ipv6Stats.totalAttempts > 0 && autoSwitchStage < 2 -> {
                    selectedView = SessionProtocolView.IPV6
                    autoSwitchStage = 2
                }
                !testActive && previousTestActive && ipv4Stats.totalAttempts > 0 && ipv6Stats.totalAttempts > 0 -> {
                    selectedView = SessionProtocolView.COMPARE
                    autoSwitchStage = 3
                }
            }
        }
        previousTestActive = testActive
    }

    val selectedStats = if (selectedView == SessionProtocolView.IPV6) ipv6Stats else ipv4Stats
    val selectedRawPoints = if (selectedView == SessionProtocolView.IPV6) ipv6Points else ipv4Points
    val selectedPoints = remember(selectedRawPoints, selectedView) { normalizeSessionPoints(selectedRawPoints) }
    val compareSeries = remember(ipv4Points, ipv6Points) {
        buildList {
            if (ipv4Points.isNotEmpty()) add(SessionChartSeries("IPv4", Blue, ipv4Points.sortedBy { it.elapsedSec }))
            if (ipv6Points.isNotEmpty()) add(SessionChartSeries("IPv6", Purple, ipv6Points.sortedBy { it.elapsedSec }))
        }
    }

    val active = when (selectedView) {
        SessionProtocolView.IPV4 -> ipv4Stats.activeSessions
        SessionProtocolView.IPV6 -> ipv6Stats.activeSessions
        SessionProtocolView.COMPARE -> ipv4Stats.activeSessions + ipv6Stats.activeSessions
    }
    val attempts = when (selectedView) {
        SessionProtocolView.IPV4 -> ipv4Stats.totalAttempts
        SessionProtocolView.IPV6 -> ipv6Stats.totalAttempts
        SessionProtocolView.COMPARE -> ipv4Stats.totalAttempts + ipv6Stats.totalAttempts
    }
    val success = when (selectedView) {
        SessionProtocolView.IPV4 -> ipv4Stats.totalSuccess
        SessionProtocolView.IPV6 -> ipv6Stats.totalSuccess
        SessionProtocolView.COMPARE -> ipv4Stats.totalSuccess + ipv6Stats.totalSuccess
    }
    val failure = when (selectedView) {
        SessionProtocolView.IPV4 -> ipv4Stats.totalFailure
        SessionProtocolView.IPV6 -> ipv6Stats.totalFailure
        SessionProtocolView.COMPARE -> ipv4Stats.totalFailure + ipv6Stats.totalFailure
    }
    val peak = when (selectedView) {
        SessionProtocolView.IPV4 -> sessionPeakValue(ipv4Stats, ipv4Points)
        SessionProtocolView.IPV6 -> sessionPeakValue(ipv6Stats, ipv6Points)
        SessionProtocolView.COMPARE -> maxOf(
            sessionPeakValue(ipv4Stats, ipv4Points),
            sessionPeakValue(ipv6Stats, ipv6Points)
        )
    }
    val targetCps = when (selectedView) {
        SessionProtocolView.IPV4 -> ipv4Stats.lastAdded
        SessionProtocolView.IPV6 -> ipv6Stats.lastAdded
        SessionProtocolView.COMPARE -> maxOf(ipv4Stats.lastAdded, ipv6Stats.lastAdded)
    }
    val actualCps = when (selectedView) {
        SessionProtocolView.IPV4 -> ipv4Stats.cps
        SessionProtocolView.IPV6 -> ipv6Stats.cps
        SessionProtocolView.COMPARE -> maxOf(ipv4Stats.cps, ipv6Stats.cps)
    }
    val averageLatencyMs = when (selectedView) {
        SessionProtocolView.IPV4 -> ipv4Stats.averageConnectLatencyMs
        SessionProtocolView.IPV6 -> ipv6Stats.averageConnectLatencyMs
        SessionProtocolView.COMPARE -> {
            val samples = ipv4Stats.totalSuccess + ipv6Stats.totalSuccess
            if (samples > 0) {
                ((ipv4Stats.averageConnectLatencyMs.toLong() * ipv4Stats.totalSuccess.toLong() +
                    ipv6Stats.averageConnectLatencyMs.toLong() * ipv6Stats.totalSuccess.toLong()) / samples.toLong()).toInt()
            } else 0
        }
    }
    val rawAddress = when (selectedView) {
        SessionProtocolView.IPV4 -> ipv4Stats.resolvedAddresses.joinToString(" / ")
        SessionProtocolView.IPV6 -> ipv6Stats.resolvedAddresses.joinToString(" / ")
        SessionProtocolView.COMPARE -> buildList {
            if (ipv4Stats.resolvedAddresses.isNotEmpty()) add("IPv4 ${ipv4Stats.resolvedAddresses.joinToString(" / ")}")
            if (ipv6Stats.resolvedAddresses.isNotEmpty()) add("IPv6 ${ipv6Stats.resolvedAddresses.joinToString(" / ")}")
        }.joinToString("    ")
    }
    val displayAddress = when (selectedView) {
        SessionProtocolView.IPV4 -> displayIpList(ipv4Stats.resolvedAddresses, maskPrivacy)
        SessionProtocolView.IPV6 -> displayIpList(ipv6Stats.resolvedAddresses, maskPrivacy)
        SessionProtocolView.COMPARE -> buildList {
            if (ipv4Stats.resolvedAddresses.isNotEmpty()) add("IPv4 ${displayIpList(ipv4Stats.resolvedAddresses, maskPrivacy)}")
            if (ipv6Stats.resolvedAddresses.isNotEmpty()) add("IPv6 ${displayIpList(ipv6Stats.resolvedAddresses, maskPrivacy)}")
        }.joinToString("    ")
    }

    val content: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("connection_tree", "IPv4 / IPv6 会话", Blue)
            Spacer(Modifier.weight(1f))
            val phaseText = when (selectedView) {
                SessionProtocolView.IPV4 -> ipv4Stats.phase
                SessionProtocolView.IPV6 -> ipv6Stats.phase
                SessionProtocolView.COMPARE -> if (ipv6Stats.totalAttempts > 0) ipv6Stats.phase else ipv4Stats.phase
            }
            Text(phaseText, color = Blue, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 12.sp)
        }

        if (availableViews.size > 1) {
            SessionProtocolSelector(selectedView, availableViews, onSelected = { selectedView = it })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            if (selectedView == SessionProtocolView.COMPARE) {
                SessionCompareMetricTileCompact(
                    label = "活动",
                    ipv4Value = ipv4Stats.activeSessions.toString(),
                    ipv6Value = ipv6Stats.activeSessions.toString(),
                    color = Blue,
                    modifier = Modifier.weight(1f)
                )
                SessionCompareMetricTileCompact(
                    label = "总计",
                    ipv4Value = ipv4Stats.totalAttempts.toString(),
                    ipv6Value = ipv6Stats.totalAttempts.toString(),
                    color = Navy,
                    modifier = Modifier.weight(1f)
                )
                SessionCompareMetricTileCompact(
                    label = "CPS",
                    ipv4Value = "${ipv4Stats.lastAdded}/${ipv4Stats.cps}",
                    ipv6Value = "${ipv6Stats.lastAdded}/${ipv6Stats.cps}",
                    color = Blue,
                    modifier = Modifier.weight(1f)
                )
                SessionCompareMetricTileCompact(
                    label = "平均延迟",
                    ipv4Value = ipv4Stats.averageConnectLatencyMs.takeIf { it > 0 }?.let { "${it}ms" } ?: "—",
                    ipv6Value = ipv6Stats.averageConnectLatencyMs.takeIf { it > 0 }?.let { "${it}ms" } ?: "—",
                    color = Purple,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SessionMetricTileCompact("活动", active.toString(), Blue, Modifier.weight(1f))
                SessionMetricTileCompact("总计", attempts.toString(), Navy, Modifier.weight(1f))
                SessionMetricTileCompact("CPS", "$targetCps/$actualCps", Blue, Modifier.weight(1f))
                SessionMetricTileCompact("平均延迟", if (averageLatencyMs > 0) "${averageLatencyMs}ms" else "—", Purple, Modifier.weight(1f))
            }
        }

        SessionAddressRow(
            displayAddress = displayAddress,
            rawAddress = rawAddress,
            onCopy = {
                if (rawAddress.isBlank()) {
                    Toast.makeText(context, "暂无可复制地址", Toast.LENGTH_SHORT).show()
                } else {
                    clipboard.setText(AnnotatedString(rawAddress))
                    Toast.makeText(context, "地址已复制", Toast.LENGTH_SHORT).show()
                }
            }
        )

        ChartModeSelector(chartMode, onChartModeChange)

        val hasPoints = if (selectedView == SessionProtocolView.COMPARE) compareSeries.any { it.points.isNotEmpty() } else selectedPoints.isNotEmpty()
        if (!hasPoints) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(Color(0xFFF8FAFC), ShapeM),
                contentAlignment = Alignment.Center
            ) {
                Text("开始测试后显示本次曲线", color = Muted, fontSize = 12.sp)
            }
        } else if (chartMode == ChartMode.GROWTH) {
            val series = when (selectedView) {
                SessionProtocolView.IPV4 -> listOf(SessionChartSeries("IPv4", Blue, selectedPoints))
                SessionProtocolView.IPV6 -> listOf(SessionChartSeries("IPv6", Purple, selectedPoints))
                SessionProtocolView.COMPARE -> compareSeries
            }
            CombinedSessionGrowthChart(series)
        } else {
            if (selectedView == SessionProtocolView.COMPARE) {
                CombinedSessionPeakChart(ipv4Stats, ipv4Points, ipv6Stats, ipv6Points)
            } else {
                SessionPeakChart(selectedStats, selectedRawPoints)
            }
        }

        if (selectedView == SessionProtocolView.COMPARE) {
            SessionCompareResultSummaryCard(
                ipv4Stats = ipv4Stats,
                ipv4Points = ipv4Points,
                ipv6Stats = ipv6Stats,
                ipv6Points = ipv6Points,
                testActive = testActive
            )
        } else {
            SessionResultSummaryCard(
                peak = peak,
                success = success,
                failure = failure,
                active = active,
                attempts = attempts,
                testActive = testActive
            )
        }
    }

    if (embedded) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    } else {
        SoftCard(content = content)
    }
}

private val SessionMetricCompactHeight = 48.dp
private val SessionCompareMetricCompactHeight = 54.dp

@Composable
private fun SessionMetricTileCompact(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(SessionMetricCompactHeight)
            .background(Color(0xFFF8FAFC), ShapeM)
            .border(1.dp, Border.copy(alpha = 0.72f), ShapeM)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            value,
            color = color,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        Spacer(Modifier.height(1.dp))
        Text(
            label,
            color = Muted,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun SessionCompareMetricTileCompact(
    label: String,
    ipv4Value: String,
    ipv6Value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(SessionCompareMetricCompactHeight)
            .background(Color(0xFFF8FAFC), ShapeM)
            .border(1.dp, Border.copy(alpha = 0.72f), ShapeM)
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = Muted,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
        Text(
            "V4 $ipv4Value",
            color = color,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        Text(
            "V6 $ipv6Value",
            color = if (color == Blue) Purple else color,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun SessionAddressRow(
    displayAddress: String,
    rawAddress: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), ShapeM)
            .border(1.dp, Border.copy(alpha = 0.65f), ShapeM)
            .padding(start = 9.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("地址：", color = Muted, fontSize = 10.sp, maxLines = 1)
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .clickable(enabled = rawAddress.isNotBlank(), onClick = onCopy)
        ) {
            Text(
                displayAddress.ifBlank { "—" },
                color = TextDark,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                fontFamily = FontFamily.Monospace
            )
        }
        IconButton(
            onClick = onCopy,
            enabled = rawAddress.isNotBlank(),
            modifier = Modifier.width(32.dp).height(32.dp)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "复制地址", tint = Blue, modifier = Modifier.width(16.dp).height(16.dp))
        }
    }
}

@Composable
private fun SessionResultSummaryCard(
    peak: Int,
    success: Int,
    failure: Int,
    active: Int,
    attempts: Int,
    testActive: Boolean
) {
    val fourthLabel = if (testActive) "保持率" else "失败率"
    val fourthValue = when {
        testActive && peak > 0 -> String.format(Locale.US, "%.1f%%", active.toDouble() * 100.0 / peak.toDouble())
        !testActive && attempts > 0 -> String.format(Locale.US, "%.2f%%", failure.toDouble() * 100.0 / attempts.toDouble())
        else -> "—"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SessionMetricCompactHeight)
            .background(Color(0xFFF8FAFC), ShapeM)
            .border(1.dp, Border.copy(alpha = 0.75f), ShapeM)
            .padding(horizontal = 3.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingSummaryValue("峰值", if (peak > 0) peak.toString() else "—", Blue, Modifier.weight(1f))
        FloatingSummaryDivider()
        FloatingSummaryValue("成功", success.toString(), Green, Modifier.weight(1f))
        FloatingSummaryDivider()
        FloatingSummaryValue("失败", failure.toString(), ErrorRed, Modifier.weight(1f))
        FloatingSummaryDivider()
        FloatingSummaryValue(fourthLabel, fourthValue, Purple, Modifier.weight(1f))
    }
}

@Composable
private fun SessionCompareResultSummaryCard(
    ipv4Stats: ProtocolStats,
    ipv4Points: List<ChartPoint>,
    ipv6Stats: ProtocolStats,
    ipv6Points: List<ChartPoint>,
    testActive: Boolean
) {
    fun rateText(stats: ProtocolStats, points: List<ChartPoint>): String {
        return if (testActive) {
            val peak = sessionPeakValue(stats, points)
            if (peak > 0) String.format(Locale.US, "%.1f%%", stats.activeSessions.toDouble() * 100.0 / peak.toDouble()) else "—"
        } else {
            if (stats.totalAttempts > 0) String.format(Locale.US, "%.2f%%", stats.totalFailure.toDouble() * 100.0 / stats.totalAttempts.toDouble()) else "—"
        }
    }
    val rateLabel = if (testActive) "保持率" else "失败率"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SessionCompareMetricCompactHeight)
            .background(Color(0xFFF8FAFC), ShapeM)
            .border(1.dp, Border.copy(alpha = 0.75f), ShapeM)
            .padding(horizontal = 3.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingCompareSummaryValue(
            "峰值",
            sessionPeakValue(ipv4Stats, ipv4Points).takeIf { it > 0 }?.toString() ?: "—",
            sessionPeakValue(ipv6Stats, ipv6Points).takeIf { it > 0 }?.toString() ?: "—",
            Blue,
            Modifier.weight(1f)
        )
        FloatingSummaryDivider()
        FloatingCompareSummaryValue("成功", ipv4Stats.totalSuccess.toString(), ipv6Stats.totalSuccess.toString(), Green, Modifier.weight(1f))
        FloatingSummaryDivider()
        FloatingCompareSummaryValue("失败", ipv4Stats.totalFailure.toString(), ipv6Stats.totalFailure.toString(), ErrorRed, Modifier.weight(1f))
        FloatingSummaryDivider()
        FloatingCompareSummaryValue(rateLabel, rateText(ipv4Stats, ipv4Points), rateText(ipv6Stats, ipv6Points), Purple, Modifier.weight(1f))
    }
}

@Composable
private fun FloatingCompareSummaryValue(
    label: String,
    ipv4Value: String,
    ipv6Value: String,
    color: Color,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = Muted, fontSize = 7.5.sp, lineHeight = 8.sp, maxLines = 1, softWrap = false)
        Text("V4 $ipv4Value", color = color, fontSize = 8.5.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Text("V6 $ipv6Value", color = if (color == Blue) Purple else color, fontSize = 8.5.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun FloatingSummaryValue(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(label, color = Muted, fontSize = 8.sp, lineHeight = 10.sp, maxLines = 1, softWrap = false)
        Text(value, color = color, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun FloatingSummaryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(Border.copy(alpha = 0.9f))
    )
}

@Composable
private fun CombinedSessionGrowthChart(series: List<SessionChartSeries>) {
    val renderedSeries = remember(series) {
        series.map { item ->
            item.copy(points = downsampleForRender(item.points.sortedBy { it.elapsedSec }, MAX_RENDER_SESSION_POINTS))
        }.filter { it.points.isNotEmpty() }
    }
    val allPoints = remember(renderedSeries) { renderedSeries.flatMap { it.points }.sortedBy { it.elapsedSec } }
    val minX = 0
    val maxXRaw = allPoints.maxOfOrNull { it.elapsedSec } ?: 1
    val maxX = maxOf(1, maxXRaw)
    val maxSessionY = remember(allPoints) { sessionYAxisMax(allPoints.maxOfOrNull { it.active } ?: 0) }
    val step = chartXAxisStep(maxX - minX)
    val yLabels = remember(maxSessionY) { axisLabels(maxSessionY) }
    val xLabels = remember(minX, maxX, step) { timeLabels(minX, maxX, step) }
    val stageRanges = remember(allPoints, minX, maxX) { buildSessionStageRanges(allPoints, minX, maxX) }
    val failSummary = remember(allPoints) { failureIntervalSummary(allPoints) }
    val latest = allPoints.lastOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("会话数", color = Muted, fontSize = 10.sp)
            renderedSeries.forEach { item ->
                Spacer(Modifier.width(8.dp))
                Text("● ${item.label}", color = item.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(
                latest?.let { "${it.elapsedSec}s 活动 ${it.active}｜失败 ${it.failure}" } ?: "",
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("■ 增长", color = Blue.copy(alpha = 0.78f), fontSize = 9.sp)
            Text("■ 平台确认", color = Orange.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("■ 结果", color = Green.copy(alpha = 0.82f), fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text("红条=本周期失败", color = ErrorRed, fontSize = 9.sp)
        }

        Row(modifier = Modifier.fillMaxWidth().height(176.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(36.dp).height(163.dp), verticalArrangement = Arrangement.SpaceBetween) {
                yLabels.forEach { Text(it.toString(), color = Muted, fontSize = 9.sp, maxLines = 1) }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(163.dp)
                    .background(Color(0xFFF8FAFC), ShapeS)
                    .padding(6.dp)
            ) {
                val w = size.width
                val h = size.height
                fun xOfSec(sec: Int): Float = w * ((sec - minX).toFloat() / (maxX - minX).toFloat()).coerceIn(0f, 1f)
                fun ySession(value: Int): Float = h - h * (value.coerceIn(0, maxSessionY).toFloat() / maxSessionY.toFloat())

                stageRanges.forEach { range ->
                    val color = when (range.stage) {
                        SessionChartStage.GROWTH -> Blue.copy(alpha = 0.055f)
                        SessionChartStage.CONFIRM -> Orange.copy(alpha = 0.09f)
                        SessionChartStage.RESULT -> Green.copy(alpha = 0.075f)
                    }
                    val left = xOfSec(range.startSec)
                    val right = xOfSec(range.endSec).coerceAtLeast(left + 1f)
                    drawRect(color = color, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(right - left, h))
                }

                repeat(4) { idx ->
                    val y = h * (idx + 1) / 5f
                    drawLine(Border.copy(alpha = 0.55f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }

                renderedSeries.forEach { item ->
                    val ordered = item.points.sortedBy { it.elapsedSec }
                    if (ordered.size > 1) {
                        val path = Path()
                        ordered.forEachIndexed { index, point ->
                            val x = xOfSec(point.elapsedSec)
                            val y = ySession(point.active)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = item.color, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                    }
                    ordered.forEach { point ->
                        drawCircle(item.color, radius = 2.2f, center = Offset(xOfSec(point.elapsedSec), ySession(point.active)))
                    }
                }

                val maxFailureDelta = renderedSeries.maxOfOrNull { item ->
                    val ordered = item.points.sortedBy { it.elapsedSec }
                    var previous = ordered.firstOrNull()?.failure ?: 0
                    var maxDelta = 0
                    ordered.drop(1).forEach { point ->
                        maxDelta = maxOf(maxDelta, (point.failure - previous).coerceAtLeast(0))
                        previous = point.failure
                    }
                    maxDelta
                }?.coerceAtLeast(1) ?: 1

                renderedSeries.forEachIndexed { seriesIndex, item ->
                    val ordered = item.points.sortedBy { it.elapsedSec }
                    var previousFailure = ordered.firstOrNull()?.failure ?: 0
                    ordered.drop(1).forEach { point ->
                        val delta = (point.failure - previousFailure).coerceAtLeast(0)
                        if (delta > 0) {
                            val xOffset = if (renderedSeries.size > 1) (seriesIndex * 3f - 1.5f) else 0f
                            val barHeight = (6f + 20f * delta.toFloat() / maxFailureDelta.toFloat()).coerceIn(7f, 26f)
                            val x = xOfSec(point.elapsedSec) + xOffset
                            drawLine(
                                color = ErrorRed.copy(alpha = if (seriesIndex == 0) 0.92f else 0.65f),
                                start = Offset(x, h),
                                end = Offset(x, h - barHeight),
                                strokeWidth = 2.6f,
                                cap = StrokeCap.Round
                            )
                        }
                        previousFailure = point.failure
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(start = 36.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            xLabels.forEach { Text("${it}s", color = Muted, fontSize = 10.sp) }
        }
        if (failSummary.isNotBlank()) {
            Text(failSummary, color = ErrorRed, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "说明：折线为活动会话；阶段背景来自实际运行状态；红条表示相邻采样间新增失败。",
            color = Muted,
            fontSize = 10.sp,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun CombinedSessionPeakChart(
    ipv4Stats: ProtocolStats,
    ipv4Points: List<ChartPoint>,
    ipv6Stats: ProtocolStats,
    ipv6Points: List<ChartPoint>
) {
    val v4Peak = sessionPeakValue(ipv4Stats, ipv4Points)
    val v6Peak = sessionPeakValue(ipv6Stats, ipv6Points)
    val maxValue = sessionYAxisMax(
        listOf(v4Peak, v6Peak, ipv4Stats.totalFailure, ipv6Stats.totalFailure, 1).maxOrNull() ?: 1
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.background(Color(0xFFF8FAFC), ShapeM).padding(10.dp)
    ) {
        HistoryBarRow("IPv4峰值", v4Peak, maxValue, Blue)
        HistoryBarRow("IPv6峰值", v6Peak, maxValue, Purple)
        HistoryBarRow("IPv4失败", ipv4Stats.totalFailure, maxValue, ErrorRed)
        HistoryBarRow("IPv6失败", ipv6Stats.totalFailure, maxValue, ErrorRed.copy(alpha = 0.72f))
        Text("对比模式显示两种协议的峰值和失败；增长过程请切换到折线趋势。", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SessionStatsCard(
    title: String,
    stats: ProtocolStats,
    maskPrivacy: Boolean,
    points: List<ChartPoint>,
    chartMode: ChartMode,
    onChartModeChange: (ChartMode) -> Unit
) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if (title.contains("IPv4")) "session_ipv4" else if (title.contains("IPv6")) "session_ipv6" else "chart", title, Blue)
            Spacer(Modifier.weight(1f))
            Text(stats.phase, color = Blue, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricTile("活动", stats.activeSessions.toString(), Blue, Modifier.weight(1f))
            MetricTile("上次活动", if (stats.maxStableSessions > 0) stats.maxStableSessions.toString() else "—", Navy, Modifier.weight(1f))
            MetricTile("失败", stats.totalFailure.toString(), ErrorRed, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricTile("总计", stats.totalAttempts.toString(), Navy, Modifier.weight(1f))
            MetricTile("目标CPS", "${stats.lastAdded}/s", Blue, Modifier.weight(1f))
            MetricTile(if (stats.activeSessions == 0 && stats.totalAttempts > 0) "平均CPS" else "实际CPS", "${stats.cps}/s", Blue, Modifier.weight(1f))
        }
        if (stats.resolvedAddresses.isNotEmpty()) {
            Text(
                "地址：${displayIpList(stats.resolvedAddresses, maskPrivacy)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Muted,
                fontSize = 11.sp
            )
        }
        ChartModeSelector(chartMode, onChartModeChange)
        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(Color(0xFFF8FAFC), ShapeM),
                contentAlignment = Alignment.Center
            ) { Text("开始测试后显示本次曲线", color = Muted, fontSize = 12.sp) }
        } else {
            if (chartMode == ChartMode.GROWTH) SessionGrowthChart(points) else SessionPeakChart(stats, points)
        }
    }
}

@Composable
private fun ChartModeSelector(chartMode: ChartMode, onChartModeChange: (ChartMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ChartMode.entries.forEach { mode ->
            val selected = chartMode == mode
            SoftChoicePill(
                text = mode.label,
                selected = selected,
                onClick = { onChartModeChange(mode) },
                compact = true
            )
        }
    }
}

private fun chartXAxisStep(durationSec: Int): Int = when {
    durationSec <= 20 -> 3
    durationSec <= 60 -> 5
    durationSec <= 120 -> 10
    durationSec <= 300 -> 30
    else -> 60
}

private fun sessionYAxisMax(peak: Int): Int = when {
    peak <= 300 -> 300
    peak <= 1000 -> 1000
    peak <= 2000 -> 2000
    peak <= 5000 -> 5000
    peak <= 10000 -> 10000
    peak <= 20000 -> 20000
    peak <= 30000 -> 30000
    peak <= 35000 -> 35000
    else -> (((peak + 4_999) / 5_000) * 5_000).coerceAtMost(65_535)
}

private fun pingYAxisMax(peak: Int): Int = when {
    peak <= 10 -> 10
    peak <= 20 -> 20
    peak <= 50 -> 50
    peak <= 100 -> 100
    peak <= 200 -> 200
    peak <= 500 -> 500
    peak <= 1000 -> 1000
    peak <= 1500 -> 1500
    peak <= 2000 -> 2000
    else -> (((peak + 499) / 500) * 500).coerceAtMost(10000)
}


private fun axisLabels(maxValue: Int): List<Int> = listOf(maxValue, maxValue * 3 / 4, maxValue / 2, maxValue / 4, 0).distinct()

private fun timeLabels(minX: Int, maxX: Int, step: Int): List<Int> {
    val labels = mutableListOf<Int>()
    labels.add(minX)
    var t = ((minX + step - 1) / step) * step
    while (t < maxX) {
        if (t > minX) labels.add(t)
        t += step
    }
    if (labels.lastOrNull() != maxX) {
        val last = labels.lastOrNull() ?: minX
        // 避免末尾出现 210s、211s 这种挤在一起的标注。
        if (maxX - last < step / 2f && labels.size > 1) {
            labels[labels.lastIndex] = maxX
        } else {
            labels.add(maxX)
        }
    }
    return labels.distinct()
}

private fun pingTimeLabels(minX: Int, maxX: Int, maxTicks: Int = 6): List<Int> {
    if (maxX <= minX) return listOf(minX)
    val duration = maxX - minX
    val niceSteps = listOf(1, 2, 3, 5, 10, 15, 20, 30, 60, 120, 180, 300, 600)
    val step = niceSteps.firstOrNull { (duration / it) + 1 <= maxTicks } ?: ((duration + maxTicks - 1) / maxTicks).coerceAtLeast(1)
    val labels = mutableListOf<Int>()
    labels.add(minX)
    var t = ((minX + step - 1) / step) * step
    while (t < maxX && labels.size < maxTicks - 1) {
        if (t > minX) labels.add(t)
        t += step
    }
    if (labels.lastOrNull() != maxX) labels.add(maxX)
    return labels.distinct().take(maxTicks)
}


private fun failureIntervalSummary(points: List<ChartPoint>, bucketSeconds: Int = 10): String {
    if (points.isEmpty()) return ""
    val sorted = points.sortedBy { it.elapsedSec }
    val firstFailure = sorted.first().failure
    val lastFailure = sorted.last().failure
    if (lastFailure <= firstFailure) return ""
    val buckets = linkedMapOf<Int, Int>()
    var prevFailure = firstFailure
    var prevSec = sorted.first().elapsedSec
    sorted.drop(1).forEach { point ->
        val delta = (point.failure - prevFailure).coerceAtLeast(0)
        if (delta > 0) {
            val bucketStart = (prevSec / bucketSeconds) * bucketSeconds
            buckets[bucketStart] = (buckets[bucketStart] ?: 0) + delta
        }
        prevFailure = point.failure
        prevSec = point.elapsedSec
    }
    if (buckets.isEmpty()) return ""
    val top = buckets.entries.sortedByDescending { it.value }.take(3)
    return "失败区间：" + top.joinToString("，") { (start, count) -> "${start}-${start + bucketSeconds}s +$count" }
}

private fun shouldShowFailureMiniCurve(points: List<ChartPoint>): Boolean = false

@Composable
private fun FailureMiniChart(points: List<ChartPoint>) {
    val sorted = points.sortedBy { it.elapsedSec }
    val minX = sorted.firstOrNull()?.elapsedSec ?: 0
    val maxXRaw = sorted.lastOrNull()?.elapsedSec ?: (minX + 1)
    val maxX = maxOf(minX + 1, maxXRaw)
    val maxFailure = (sorted.maxOfOrNull { it.failure } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("失败数", color = Muted, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Text("● 失败", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("最高 $maxFailure", color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(48.dp).background(Color(0xFFF8FAFC), ShapeS).padding(6.dp)) {
            val w = size.width
            val h = size.height
            repeat(2) { idx ->
                val y = h * (idx + 1) / 3f
                drawLine(Border.copy(alpha = 0.45f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            fun xOf(p: ChartPoint) = w * ((p.elapsedSec - minX).toFloat() / (maxX - minX).toFloat())
            fun yFail(v: Int) = h - h * (v.coerceIn(0, maxFailure).toFloat() / maxFailure.toFloat())
            if (sorted.size > 1) {
                val path = Path()
                sorted.forEachIndexed { index, p ->
                    val x = xOf(p)
                    val y = yFail(p.failure)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = ErrorRed, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
            sorted.forEach { p ->
                drawCircle(ErrorRed, radius = 2.2f, center = Offset(xOf(p), yFail(p.failure)))
            }
        }
    }
}

@Composable
private fun SessionGrowthChart(points: List<ChartPoint>) {
    val sorted = remember(points) { downsampleForRender(points.sortedBy { it.elapsedSec }, MAX_RENDER_SESSION_POINTS) }
    val showFailureMiniCurve = remember(sorted) { shouldShowFailureMiniCurve(sorted) }
    val minX = sorted.firstOrNull()?.elapsedSec ?: 0
    val maxXRaw = sorted.lastOrNull()?.elapsedSec ?: (minX + 1)
    val maxX = maxOf(minX + 1, maxXRaw)
    val maxSessionY = remember(sorted) { sessionYAxisMax(sorted.maxOfOrNull { it.active } ?: 0) }
    val step = chartXAxisStep(maxX - minX)
    val last = sorted.lastOrNull()
    val yLabels = remember(maxSessionY) { axisLabels(maxSessionY) }
    val xLabels = remember(minX, maxX, step) { timeLabels(minX, maxX, step) }
    val failSummary = remember(sorted) { failureIntervalSummary(sorted) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("会话数", color = Muted, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Text("● 会话数", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (showFailureMiniCurve) {
                Spacer(Modifier.width(8.dp))
                Text("● 失败", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(last?.let { "${it.elapsedSec}s 活动 ${it.active}｜失败 ${it.failure}" } ?: "", color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        Row(modifier = Modifier.fillMaxWidth().height(158.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(34.dp).height(145.dp), verticalArrangement = Arrangement.SpaceBetween) {
                yLabels.forEach { Text(it.toString(), color = Muted, fontSize = 9.sp, maxLines = 1) }
            }
            Canvas(modifier = Modifier.weight(1f).height(145.dp).background(Color(0xFFF8FAFC), ShapeS).padding(6.dp)) {
                val w = size.width
                val h = size.height
                repeat(4) { idx ->
                    val y = h * (idx + 1) / 5f
                    drawLine(Border.copy(alpha = 0.55f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                fun xOf(p: ChartPoint) = w * ((p.elapsedSec - minX).toFloat() / (maxX - minX).toFloat())
                fun ySession(v: Int) = h - h * (v.coerceIn(0, maxSessionY).toFloat() / maxSessionY.toFloat())
                if (sorted.size > 1) {
                    val path = Path()
                    sorted.forEachIndexed { index, p ->
                        val x = xOf(p)
                        val y = ySession(p.active)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = Blue, style = Stroke(width = 4f, cap = StrokeCap.Round))
                }
                sorted.forEach { p ->
                    drawCircle(Blue, radius = 2.6f, center = Offset(xOf(p), ySession(p.active)))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 34.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            xLabels.forEach { Text("${it}s", color = Muted, fontSize = 10.sp) }
        }
        if (failSummary.isNotBlank()) {
            Text(failSummary, color = ErrorRed, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text("说明：连接数测试已使用折线趋势；CPS在上方文字显示，失败仅显示区间文字。", color = Muted, fontSize = 10.sp, lineHeight = 13.sp)
    }
}

@Composable
private fun SessionPeakChart(stats: ProtocolStats, points: List<ChartPoint>) {
    val peak = maxOf(stats.activeSessions, stats.maxStableSessions, stats.totalSuccess, points.maxOfOrNull { it.active } ?: 0)
    val maxValue = sessionYAxisMax(maxOf(peak, stats.totalFailure, stats.totalAttempts))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.background(Color(0xFFF8FAFC), ShapeM).padding(10.dp)) {
        HistoryBarRow("活动峰值", peak, maxValue, Blue)
        HistoryBarRow("失败累计", stats.totalFailure, maxValue, ErrorRed)
        HistoryBarRow("总计尝试", stats.totalAttempts, maxValue, Navy)
        Text("用于截图分享最高值；增长过程请切换到增长曲线。", color = Muted, fontSize = 10.sp)
    }
}


@Composable
private fun HistoryBarRow(label: String, value: Int, maxValue: Int, color: Color) {
    val fraction = (value.toFloat() / maxValue.coerceAtLeast(1).toFloat()).coerceIn(0.02f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(24.dp)) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.width(72.dp), maxLines = 1)
        Box(modifier = Modifier.weight(1f).height(8.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))) {
            Box(modifier = Modifier.fillMaxWidth(fraction).height(8.dp).background(color, RoundedCornerShape(8.dp)))
        }
        Spacer(Modifier.width(8.dp))
        Text(value.toString(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp), maxLines = 1)
    }
}


@Composable
private fun NatDiagnosticDialog(
    mode: ManualNatMode,
    servers: List<String>,
    running: Boolean,
    progressText: String,
    result: ManualNatResult?,
    onOpenHistory: () -> Unit,
    onModeChange: (ManualNatMode) -> Unit,
    onServersChange: (List<String>) -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !running) { Text("完成", fontSize = 13.sp) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onRun, enabled = !running) {
                    Text(if (running) "检测中" else "开始检测", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onOpenHistory, enabled = !running) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("历史记录", fontSize = 13.sp)
                }
            }
        },
        title = { Text("NAT 类型检测", fontWeight = FontWeight.ExtraBold, color = TextDark) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("手动检测 · IPv4 · UDP", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NatModeChip("RFC5780", mode == ManualNatMode.RFC5780, Modifier.weight(1f)) { onModeChange(ManualNatMode.RFC5780) }
                    NatModeChip("RFC3489", mode == ManualNatMode.RFC3489, Modifier.weight(1f)) { onModeChange(ManualNatMode.RFC3489) }
                }
                Text(
                    if (mode == ManualNatMode.RFC5780) "RFC5780 会检测映射行为和过滤行为；若服务器不支持严格流程，会自动降级为 RFC8489 基础结果。" else "RFC3489 是经典兼容检测，结果可与老工具对比。",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.background(Color(0xFFF8FAFC), ShapeM).padding(10.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("STUN服务器", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("未填写端口时默认 3478", color = Muted, fontSize = 10.sp)
                }
                servers.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = item,
                            onValueChange = { value ->
                                val next = servers.toMutableList()
                                next[index] = value.trim()
                                onServersChange(next)
                            },
                            label = { Text("服务器 ${index + 1}/${servers.size}", fontSize = 11.sp) },
                            placeholder = { Text(defaultNatServer(mode), fontSize = 12.sp) },
                            singleLine = true,
                            shape = ShapeM,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val next = if (servers.size > 1) {
                                    servers.filterIndexed { i, _ -> i != index }
                                } else {
                                    listOf("")
                                }
                                onServersChange(next)
                            }
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ErrorRed)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val clean = servers.map { it.trim() }
                        val next = if (clean.size >= 6) clean else clean + ""
                        onServersChange(next)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeM
                ) { Text("+ 添加服务器", fontSize = 13.sp) }
                if (running || progressText.isNotBlank()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), ShapeM).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("检测进度", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(progressText.ifBlank { "等待开始" }, color = TextDark, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                result?.let { r ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().background(if (r.success) BlueSoft else RedSoft, ShapeM).padding(12.dp)
                    ) {
                        Text("测试结果", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            r.natType,
                            color = if (r.success) Blue else ErrorRed,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            MiniResultTile("映射行为", standardMappingText(r.mappingBehavior), Modifier.weight(1f))
                            MiniResultTile("过滤行为", standardFilteringText(r.filteringBehavior), Modifier.weight(1f))
                        }
                        if (mode == ManualNatMode.RFC5780 && r.success) {
                            Text(
                                "传统 NAT 类型为兼容换算；准确判断请同时参考上方 RFC5780 映射行为和过滤行为。",
                                color = Muted,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                        MiniResultLine("本地地址", r.localAddress)
                        MiniResultLine("公网地址", r.publicAddress)
                        MiniResultLine("测试方法", r.method)
                        MiniResultLine("服务器", r.server)
                        if (r.message.isNotBlank()) Text(r.message, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    )
}

@Composable
private fun NatHistoryDialog(
    records: List<NatHistoryRecord>,
    onDismiss: () -> Unit
) {
    var expandedIds by remember(records) { mutableStateOf<Set<Long>>(emptySet()) }
    val sizeKb = remember(records) { natHistorySizeKb(records) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) } },
        title = { Text("NAT 检测历史", fontWeight = FontWeight.ExtraBold, color = TextDark) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "已保存 ${records.size}/$NAT_HISTORY_MAX 条 · 占用 ${String.format(Locale.US, "%.1f", sizeKb)} KB",
                    color = Muted,
                    fontSize = 11.sp
                )
                if (records.isEmpty()) {
                    Text("暂无 NAT 检测记录。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    records.forEach { record ->
                        val expanded = record.id in expandedIds
                        Surface(
                            shape = ShapeM,
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(0.7.dp, Border.copy(alpha = 0.75f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedIds = if (expanded) expandedIds - record.id else expandedIds + record.id
                                }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(record.timeText, color = Muted, fontSize = 10.sp)
                                        Text(
                                            "${record.mode} · ${record.natType}",
                                            color = TextDark,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(if (expanded) "收起" else "展开", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (expanded) {
                                    HorizontalDivider(color = Border.copy(alpha = 0.7f))
                                    MiniResultLine("映射行为", record.mappingBehavior.ifBlank { "—" })
                                    MiniResultLine("过滤行为", record.filteringBehavior.ifBlank { "—" })
                                    MiniResultLine("本地地址", record.localAddress.ifBlank { "—" })
                                    MiniResultLine("公网地址", record.publicAddress.ifBlank { "—" })
                                    MiniResultLine("测试方法", record.method.ifBlank { record.mode })
                                    MiniResultLine("服务器", record.server.ifBlank { "—" })
                                    MiniResultLine("检测耗时", if (record.durationMs > 0L) "${record.durationMs}ms" else "—")
                                    if (record.message.isNotBlank()) {
                                        Text(record.message, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun NatModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = ShapeM
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = if (selected) BlueSoft else Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.8.dp, if (selected) Blue.copy(alpha = 0.18f) else Border.copy(alpha = 0.72f)),
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, color = if (selected) Blue else TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MiniResultTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color.White.copy(alpha = 0.8f), ShapeM).padding(10.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
    }
}

@Composable
private fun MiniResultLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.width(72.dp))
        Text(value, color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


private fun standardMappingText(value: String): String = when (value) {
    "端口保持" -> "Endpoint-Independent Mapping（端口保持）"
    "端口变化" -> "Address and Port-Dependent Mapping（端口变化）"
    "公网映射" -> "Binding Mapping（公网映射）"
    else -> value
}

private fun standardFilteringText(value: String): String = when (value) {
    "开放" -> "Endpoint-Independent Filtering（开放）"
    "地址受限" -> "Address-Dependent Filtering（地址受限）"
    "端口受限" -> "Address and Port-Dependent Filtering（端口受限）"
    "未验证" -> "Not Verified（未验证）"
    else -> value
}

private fun defaultNatServer(mode: ManualNatMode): String = when (mode) {
    ManualNatMode.RFC5780 -> "stunserver2025.stunprotocol.org:3478"
    ManualNatMode.RFC3489 -> "stun.voip.aebc.com:3478"
}



private enum class NsLookupRecordType(val label: String) {
    ALL("全部"),
    A("A"),
    AAAA("AAAA")
}

private enum class NsLookupMode(val label: String) {
    LOCAL("本机DNS"),
    CUSTOM("自定义DNS")
}

private const val DEFAULT_NS_DNS1 = "223.5.5.5"
private const val DEFAULT_NS_DNS2 = "2400:3200::1"

private enum class ToolIpPolicy(val label: String) {
    AUTO("自动"),
    IPV6_FIRST("IPv6优先"),
    IPV4_FIRST("IPv4优先")
}

private enum class TracketRunState {
    Idle,
    Running,
    Paused,
    Finished,
    Failed,
    Canceled
}

private data class NsLookupToolRecord(
    val id: Long = System.currentTimeMillis(),
    val timeText: String = toolNowText(),
    val host: String,
    val dnsServers: String,
    val recordType: String = NsLookupRecordType.ALL.label,
    val ipv4: List<String>,
    val ipv6: List<String>,
    val costMs: Long,
    val error: String = ""
)

private data class TracketToolRecord(
    val id: Long = System.currentTimeMillis(),
    val timeText: String = toolNowText(),
    val host: String,
    val dnsServers: String,
    val targetIp: String,
    val ipPolicy: String = ToolIpPolicy.IPV6_FIRST.label,
    val maxHops: Int = 30,
    val timeoutMs: Int = 1200,
    val hops: List<String>,
    val costMs: Long,
    val error: String = ""
)

private fun toolNowText(): String = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

private fun cleanToolHost(input: String): String {
    var value = input.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .trim()
    value = if (value.startsWith("[") && value.contains("]")) {
        value.substringAfter("[").substringBefore("]")
    } else if (value.count { it == ':' } == 1) {
        value.substringBefore(':')
    } else {
        value
    }
    return value.trim().ifBlank { "www.baidu.com" }
}

private fun readLocalDnsServers(context: Context): List<String> {
    return NetworkDnsResolver.orderedDnsServers(context)
        .mapNotNull { it.hostAddress?.substringBefore('%') }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun dnsTextFromSnapshot(localDns: List<String>): String = localDns.joinToString(" / ").ifBlank { "未读取到" }

private fun Int.safeMs(default: Int = 1200): Int = coerceIn(500, 10000).takeIf { this > 0 } ?: default

private fun String.safeInt(default: Int, min: Int, max: Int): Int {
    val v = toIntOrNull() ?: default
    return v.coerceIn(min, max)
}

private class NetToolHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("net_tool_history_v1", Context.MODE_PRIVATE)

    fun loadNsDns(slot: Int): List<String> {
        val key = if (slot == 2) "ns_dns2_history" else "ns_dns1_history"
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        return buildList {
            for (i in 0 until arr.length()) {
                val value = arr.optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }.distinct().take(3)
    }

    fun addNsDns(slot: Int, input: String) {
        val value = normalizeDnsServerInput(input) ?: return
        val key = if (slot == 2) "ns_dns2_history" else "ns_dns1_history"
        val next = (listOf(value) + loadNsDns(slot).filterNot { it == value }).take(3)
        prefs.edit().putString(key, JSONArray(next).toString()).apply()
    }

    fun deleteNsDns(slot: Int, input: String) {
        val value = input.trim()
        val key = if (slot == 2) "ns_dns2_history" else "ns_dns1_history"
        val next = loadNsDns(slot).filterNot { it == value }
        prefs.edit().putString(key, JSONArray(next).toString()).apply()
    }

    fun loadNsLookup(): List<NsLookupToolRecord> {
        val arr = JSONArray(prefs.getString("nslookup", "[]") ?: "[]")
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    NsLookupToolRecord(
                        id = o.optLong("id", System.currentTimeMillis() + i),
                        timeText = o.optString("timeText"),
                        host = o.optString("host"),
                        dnsServers = o.optString("dnsServers"),
                        recordType = o.optString("recordType", NsLookupRecordType.ALL.label),
                        ipv4 = o.optJSONArray("ipv4")?.toStringList().orEmpty(),
                        ipv6 = o.optJSONArray("ipv6")?.toStringList().orEmpty(),
                        costMs = o.optLong("costMs"),
                        error = o.optString("error")
                    )
                )
            }
        }
    }

    fun addNsLookup(record: NsLookupToolRecord) {
        val next = (listOf(record) + loadNsLookup().filterNot {
            it.host == record.host && it.recordType == record.recordType && it.ipv4 == record.ipv4 && it.ipv6 == record.ipv6 && it.error == record.error
        }).take(15)
        prefs.edit().putString("nslookup", JSONArray().apply {
            next.forEach { r ->
                put(JSONObject()
                    .put("id", r.id)
                    .put("timeText", r.timeText)
                    .put("host", r.host)
                    .put("dnsServers", r.dnsServers)
                    .put("recordType", r.recordType)
                    .put("ipv4", JSONArray(r.ipv4))
                    .put("ipv6", JSONArray(r.ipv6))
                    .put("costMs", r.costMs)
                    .put("error", r.error))
            }
        }.toString()).apply()
    }

    fun deleteNsLookup(id: Long) {
        val next = loadNsLookup().filterNot { it.id == id }
        prefs.edit().putString("nslookup", JSONArray().apply {
            next.forEach { r ->
                put(JSONObject()
                    .put("id", r.id)
                    .put("timeText", r.timeText)
                    .put("host", r.host)
                    .put("dnsServers", r.dnsServers)
                    .put("recordType", r.recordType)
                    .put("ipv4", JSONArray(r.ipv4))
                    .put("ipv6", JSONArray(r.ipv6))
                    .put("costMs", r.costMs)
                    .put("error", r.error))
            }
        }.toString()).apply()
    }

    fun loadTracket(): List<TracketToolRecord> {
        val arr = JSONArray(prefs.getString("tracket", "[]") ?: "[]")
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    TracketToolRecord(
                        id = o.optLong("id", System.currentTimeMillis() + i),
                        timeText = o.optString("timeText"),
                        host = o.optString("host"),
                        dnsServers = o.optString("dnsServers"),
                        targetIp = o.optString("targetIp"),
                        ipPolicy = o.optString("ipPolicy", ToolIpPolicy.IPV6_FIRST.label),
                        maxHops = o.optInt("maxHops", 30),
                        timeoutMs = o.optInt("timeoutMs", 1200),
                        hops = o.optJSONArray("hops")?.toStringList().orEmpty(),
                        costMs = o.optLong("costMs"),
                        error = o.optString("error")
                    )
                )
            }
        }
    }

    fun addTracket(record: TracketToolRecord) {
        val next = (listOf(record) + loadTracket().filterNot { it.host == record.host && it.hops == record.hops && it.error == record.error }).take(15)
        prefs.edit().putString("tracket", JSONArray().apply {
            next.forEach { r ->
                put(JSONObject()
                    .put("id", r.id)
                    .put("timeText", r.timeText)
                    .put("host", r.host)
                    .put("dnsServers", r.dnsServers)
                    .put("targetIp", r.targetIp)
                    .put("ipPolicy", r.ipPolicy)
                    .put("maxHops", r.maxHops)
                    .put("timeoutMs", r.timeoutMs)
                    .put("hops", JSONArray(r.hops))
                    .put("costMs", r.costMs)
                    .put("error", r.error))
            }
        }.toString()).apply()
    }

    fun deleteTracket(id: Long) {
        val next = loadTracket().filterNot { it.id == id }
        prefs.edit().putString("tracket", JSONArray().apply {
            next.forEach { r ->
                put(JSONObject()
                    .put("id", r.id)
                    .put("timeText", r.timeText)
                    .put("host", r.host)
                    .put("dnsServers", r.dnsServers)
                    .put("targetIp", r.targetIp)
                    .put("ipPolicy", r.ipPolicy)
                    .put("maxHops", r.maxHops)
                    .put("timeoutMs", r.timeoutMs)
                    .put("hops", JSONArray(r.hops))
                    .put("costMs", r.costMs)
                    .put("error", r.error))
            }
        }.toString()).apply()
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (i in 0 until length()) add(optString(i))
}

private fun chooseToolTargetAddress(addresses: List<InetAddress>, policy: ToolIpPolicy): InetAddress? {
    return when (policy) {
        ToolIpPolicy.IPV6_FIRST -> addresses.firstOrNull { it is Inet6Address } ?: addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull()
        ToolIpPolicy.IPV4_FIRST -> addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull { it is Inet6Address } ?: addresses.firstOrNull()
        ToolIpPolicy.AUTO -> addresses.firstOrNull { it is Inet6Address } ?: addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull()
    }
}

private data class DnsToolQueryResult(
    val answers: List<String>,
    val server: String,
    val slotLabel: String
)

private fun normalizeDnsServerInput(input: String): String? {
    val value = input.trim()
        .removePrefix("[")
        .removeSuffix("]")
        .substringBefore('%')
        .trim()
    if (value.isBlank()) return null
    val isIpv4 = Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""").matches(value)
    val isIpv6 = value.contains(":") && Regex("""^[0-9A-Fa-f:.]+$""").matches(value)
    if (!isIpv4 && !isIpv6) return null
    return runCatching { InetAddress.getByName(value).hostAddress?.substringBefore('%') }.getOrNull()
}

private fun parseDnsAddressAnswers(data: ByteArray, length: Int, txId: Int, qType: Int): List<String> {
    if (length < 12) error("DNS响应过短")
    val id = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
    if (id != txId) error("DNS响应ID不匹配")
    val flags = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
    val rcode = flags and 0x0F
    if (rcode != 0) error("DNS返回错误码 $rcode")
    val qd = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
    val an = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
    var pos = 12
    repeat(qd) {
        pos = skipDnsName(data, length, pos)
        if (pos < 0 || pos + 4 > length) error("DNS问题区解析失败")
        pos += 4
    }
    val answers = mutableListOf<String>()
    repeat(an) {
        pos = skipDnsName(data, length, pos)
        if (pos < 0 || pos + 10 > length) error("DNS答案区解析失败")
        val type = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        val clazz = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
        val rdLen = ((data[pos + 8].toInt() and 0xFF) shl 8) or (data[pos + 9].toInt() and 0xFF)
        pos += 10
        if (pos + rdLen > length) error("DNS记录长度异常")
        if (clazz == 1 && type == qType) {
            when {
                qType == 1 && rdLen == 4 -> {
                    answers += (0 until 4).joinToString(".") { idx -> (data[pos + idx].toInt() and 0xFF).toString() }
                }
                qType == 28 && rdLen == 16 -> {
                    answers += InetAddress.getByAddress(data.copyOfRange(pos, pos + 16)).hostAddress.substringBefore('%')
                }
            }
        }
        pos += rdLen
    }
    return answers.distinct()
}

private fun queryDnsAddressByServer(host: String, server: String, qType: Int, timeoutMs: Int): List<String> {
    val txId = (System.nanoTime().toInt() and 0xFFFF)
    val query = buildDnsQuery(host, txId, qType = qType)
    DatagramSocket().use { socket ->
        socket.soTimeout = timeoutMs.coerceIn(500, 10000)
        socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
        val buf = ByteArray(1500)
        val packet = DatagramPacket(buf, buf.size)
        socket.receive(packet)
        return parseDnsAddressAnswers(packet.data, packet.length, txId, qType)
    }
}

private fun queryDnsAddressWithFallback(
    host: String,
    qType: Int,
    dns1: String,
    dns2: String,
    timeoutMs: Int
): DnsToolQueryResult {
    val candidates = buildList {
        normalizeDnsServerInput(dns1)?.let { add("DNS1" to it) }
        normalizeDnsServerInput(dns2)?.let { add("DNS2" to it) }
    }.distinctBy { it.second }
    if (candidates.isEmpty()) error("DNS1/DNS2 需要填写有效 IPv4 或 IPv6 地址")
    var lastError: Throwable? = null
    for ((label, server) in candidates) {
        val result = runCatching { queryDnsAddressByServer(host, server, qType, timeoutMs) }
        if (result.isSuccess) {
            val answers = result.getOrDefault(emptyList())
            if (answers.isNotEmpty()) return DnsToolQueryResult(answers, server, label)
            lastError = IllegalStateException("$label 无记录")
        } else {
            lastError = result.exceptionOrNull()
        }
    }
    return DnsToolQueryResult(emptyList(), candidates.first().second, candidates.first().first)
}

private suspend fun runNsLookupTool(
    context: Context,
    input: String,
    mode: NsLookupMode,
    dns1: String,
    dns2: String,
    recordType: NsLookupRecordType,
    timeoutMs: Int
): NsLookupToolRecord = withContext(Dispatchers.IO) {
    val host = cleanToolHost(input)
    val timeout = timeoutMs.coerceIn(500, 10000)
    val start = System.currentTimeMillis()
    if (mode == NsLookupMode.LOCAL) {
        return@withContext runCatching {
            val resolution = NetworkDnsResolver.resolveBlocking(
                context = context,
                hostInput = host,
                includeIpv4 = recordType != NsLookupRecordType.AAAA,
                includeIpv6 = recordType != NsLookupRecordType.A,
                timeoutMs = timeout
            )
            val addresses = resolution.addresses
            val ipv4 = addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.distinct()
            val ipv6 = addresses.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress?.substringBefore('%') }.distinct()
            NsLookupToolRecord(
                host = host,
                dnsServers = resolution.usedDnsServers.joinToString(" / ").ifBlank { "本机DNS" },
                recordType = recordType.label,
                ipv4 = if (recordType == NsLookupRecordType.AAAA) emptyList() else ipv4,
                ipv6 = if (recordType == NsLookupRecordType.A) emptyList() else ipv6,
                costMs = (System.currentTimeMillis() - start).coerceAtLeast(0L)
            )
        }.getOrElse { e ->
            NsLookupToolRecord(
                host = host,
                dnsServers = "本机DNS",
                recordType = recordType.label,
                ipv4 = emptyList(),
                ipv6 = emptyList(),
                costMs = (System.currentTimeMillis() - start).coerceAtLeast(0L),
                error = e.message ?: "本机DNS解析失败"
            )
        }
    }
    runCatching {
        val usedDns = linkedSetOf<String>()
        var ipv4 = emptyList<String>()
        var ipv6 = emptyList<String>()
        var successCount = 0
        var lastError: Throwable? = null
        if (recordType != NsLookupRecordType.AAAA) {
            val result = runCatching { queryDnsAddressWithFallback(host, 1, dns1, dns2, timeout) }
            result.onSuccess {
                ipv4 = it.answers
                usedDns += "${it.slotLabel} ${it.server}"
                successCount++
            }.onFailure { lastError = it }
        }
        if (recordType != NsLookupRecordType.A) {
            val result = runCatching { queryDnsAddressWithFallback(host, 28, dns1, dns2, timeout) }
            result.onSuccess {
                ipv6 = it.answers
                usedDns += "${it.slotLabel} ${it.server}"
                successCount++
            }.onFailure { lastError = it }
        }
        if (successCount == 0) error(lastError?.message ?: "自定义DNS查询失败")
        NsLookupToolRecord(
            host = host,
            dnsServers = usedDns.joinToString(" / ").ifBlank { "自定义DNS" },
            recordType = recordType.label,
            ipv4 = if (recordType == NsLookupRecordType.AAAA) emptyList() else ipv4,
            ipv6 = if (recordType == NsLookupRecordType.A) emptyList() else ipv6,
            costMs = (System.currentTimeMillis() - start).coerceAtLeast(0L)
        )
    }.getOrElse { e ->
        NsLookupToolRecord(
            host = host,
            dnsServers = listOfNotNull(
                normalizeDnsServerInput(dns1)?.let { "DNS1 $it" },
                normalizeDnsServerInput(dns2)?.let { "DNS2 $it" }
            ).joinToString(" / ").ifBlank { "自定义DNS无效" },
            recordType = recordType.label,
            ipv4 = emptyList(),
            ipv6 = emptyList(),
            costMs = (System.currentTimeMillis() - start).coerceAtLeast(0L),
            error = e.message ?: "解析失败"
        )
    }
}

private suspend fun runTracketToolLive(
    context: Context,
    input: String,
    dnsSnapshot: List<String>,
    policy: ToolIpPolicy,
    maxHops: Int,
    timeoutMs: Int,
    activeProcess: java.util.concurrent.atomic.AtomicReference<Process?>? = null,
    shouldPause: suspend () -> Boolean = { false },
    onEvent: suspend (String) -> Unit = {},
    onHop: suspend (String) -> Unit
): TracketToolRecord = withContext(Dispatchers.IO) {
    val host = cleanToolHost(input)
    val dns = dnsTextFromSnapshot(dnsSnapshot)
    val start = System.currentTimeMillis()
    try {
        val resolved = NetworkDnsResolver.resolveAddressesBlocking(
            context = context,
            host = host,
            includeIpv4 = true,
            includeIpv6 = true,
            timeoutMs = timeoutMs
        ).filterNot { it.isLoopbackAddress }
        val target = chooseToolTargetAddress(resolved, policy) ?: error("无法解析目标")
        val targetIp = target.hostAddress?.substringBefore('%') ?: host
        val ipv6 = target is Inet6Address
        val hops = mutableListOf<String>()
        for (ttl in 1..maxHops) {
            currentCoroutineContext().ensureActive()
            while (shouldPause()) {
                delay(200)
                currentCoroutineContext().ensureActive()
            }
            val output = runPingWithTtl(host = targetIp, ttl = ttl, ipv6 = ipv6, timeoutMs = timeoutMs, activeProcess = activeProcess)
            while (shouldPause()) {
                delay(200)
                currentCoroutineContext().ensureActive()
            }
            val hopIp = parseTracerouteHop(output)
            val rtt = parseTracerouteRtt(output)
            val line = when {
                hopIp.isBlank() -> "%02d  *                超时".format(ttl)
                rtt.isBlank() -> "%02d  %-18s".format(ttl, hopIp)
                else -> "%02d  %-18s  %s".format(ttl, hopIp, rtt)
            }
            hops += line
            withContext(Dispatchers.Main) { onHop(line) }
            if (isTracerouteReached(output, targetIp)) break
        }
        withContext(Dispatchers.Main) { onEvent("路由追踪完成") }
        TracketToolRecord(
            host = host,
            dnsServers = dns,
            targetIp = targetIp,
            ipPolicy = policy.label,
            maxHops = maxHops,
            timeoutMs = timeoutMs,
            hops = hops,
            costMs = (System.currentTimeMillis() - start).coerceAtLeast(0L)
        )
    } catch (e: CancellationException) {
        withContext(Dispatchers.Main) { onEvent("路由追踪已停止") }
        throw e
    } catch (e: Throwable) {
        val msg = e.message ?: "追踪失败"
        withContext(Dispatchers.Main) { onHop("错误：$msg") }
        withContext(Dispatchers.Main) { onEvent("路由追踪失败") }
        TracketToolRecord(
            host = host,
            dnsServers = dns,
            targetIp = "-",
            ipPolicy = policy.label,
            maxHops = maxHops,
            timeoutMs = timeoutMs,
            hops = emptyList(),
            costMs = (System.currentTimeMillis() - start).coerceAtLeast(0L),
            error = msg
        )
    }
}

private suspend fun runPingWithTtl(
    host: String,
    ttl: Int,
    ipv6: Boolean,
    timeoutMs: Int = 1200,
    activeProcess: java.util.concurrent.atomic.AtomicReference<Process?>? = null
): String {
    val waitMs = timeoutMs.coerceIn(500, 10000)
    val waitSeconds = ((waitMs + 999) / 1000).coerceIn(1, 10).toString()
    val timeoutArgs = listOf("-c", "1", "-W", waitSeconds, "-t", ttl.toString(), host)
    val commands = if (ipv6) {
        listOf(
            listOf("/system/bin/ping6") + timeoutArgs,
            listOf("ping6") + timeoutArgs,
            listOf("/system/bin/ping", "-6") + timeoutArgs,
            listOf("ping", "-6") + timeoutArgs
        )
    } else {
        listOf(
            listOf("/system/bin/ping") + timeoutArgs,
            listOf("ping") + timeoutArgs
        )
    }
    for (cmd in commands) {
        currentCoroutineContext().ensureActive()
        val text = runCatching {
            val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            activeProcess?.set(process)
            try {
                val completed = process.waitFor((waitMs + 800).toLong(), TimeUnit.MILLISECONDS)
                if (!completed) {
                    process.destroy()
                    if (!process.waitFor(250, TimeUnit.MILLISECONDS)) process.destroyForcibly()
                }
                process.inputStream.bufferedReader().use { it.readText() }
            } finally {
                runCatching { process.destroy() }
                activeProcess?.compareAndSet(process, null)
            }
        }.getOrDefault("")
        if (text.isNotBlank()) return text
    }
    return ""
}

private fun parseTracerouteHop(output: String): String {
    val candidates = listOf(
        Regex("""(?i)from\s+([0-9a-f:.%]+)"""),
        Regex("""(?i)from\s+[^\s(]+\s+\(([0-9a-f:.%]+)\)"""),
        Regex("""(?i)bytes\s+from\s+([0-9a-f:.%]+)"""),
        Regex("""(?i)bytes\s+from\s+[^\s(]+\s+\(([0-9a-f:.%]+)\)""")
    )
    return candidates.firstNotNullOfOrNull { it.find(output)?.groupValues?.getOrNull(1) }
        ?.substringBefore('%')
        ?.trim()
        .orEmpty()
}

private fun parseTracerouteRtt(output: String): String {
    val value = Regex("""(?i)time[=<]([0-9.]+)\s*ms""").find(output)?.groupValues?.getOrNull(1)
    return value?.let { "${it}ms" }.orEmpty()
}

private fun isTracerouteReached(output: String, targetIp: String): Boolean {
    return output.contains("bytes from", ignoreCase = true) && output.contains(targetIp.substringBefore('%'), ignoreCase = true)
}

private fun NsLookupToolRecord.copyText(): String {
    return buildString {
        appendLine("NSLookup")
        appendLine("时间：$timeText")
        appendLine("目标：$host")
        appendLine("查询DNS：$dnsServers")
        appendLine("记录类型：$recordType")
        appendLine("耗时：${costMs}ms")
        if (error.isNotBlank()) {
            appendLine("错误：$error")
        } else {
            appendLine("A：${ipv4.joinToString(" / ").ifBlank { "无" }}")
            appendLine("AAAA：${ipv6.joinToString(" / ").ifBlank { "无" }}")
        }
    }.trim()
}

private fun TracketToolRecord.copyText(): String {
    return buildString {
        appendLine("Tracket 路由追踪")
        appendLine("时间：$timeText")
        appendLine("目标：$host")
        appendLine("目标IP：$targetIp")
        appendLine("本机DNS：$dnsServers")
        appendLine("IP策略：$ipPolicy")
        appendLine("跳数：$maxHops")
        appendLine("超时：${timeoutMs}ms")
        appendLine("耗时：${costMs}ms")
        if (error.isNotBlank()) {
            appendLine("错误：$error")
        } else {
            appendLine("经过IP：")
            appendLine(hops.joinToString("\n").ifBlank { "无跳点结果" })
        }
    }.trim()
}

@Composable
private fun NetworkToolShortcutRow(
    onOpenNsLookup: () -> Unit,
    onOpenTracket: () -> Unit,
    onOpenMtu: () -> Unit,
    onOpenRoaming: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetworkToolShortcutCard(
                title = "NSLookup",
                subtitle = "DNS解析",
                mark = "nslookup",
                color = Blue,
                bg = BlueSoft,
                modifier = Modifier.weight(1f),
                onClick = onOpenNsLookup
            )
            NetworkToolShortcutCard(
                title = "Tracket",
                subtitle = "路由追踪",
                mark = "tracket",
                color = Purple,
                bg = Color(0xFFF3E8FF),
                modifier = Modifier.weight(1f),
                onClick = onOpenTracket
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetworkToolShortcutCard(
                title = "MTU检测",
                subtitle = "路径MTU",
                mark = "mtu",
                color = Orange,
                bg = Color(0xFFFFF3E0),
                modifier = Modifier.weight(1f),
                onClick = onOpenMtu
            )
            NetworkToolShortcutCard(
                title = "漫游测试",
                subtitle = "WiFi漫游",
                mark = "roaming",
                color = Green,
                bg = GreenSoft,
                modifier = Modifier.weight(1f),
                onClick = onOpenRoaming
            )
        }
    }
}

@Composable
private fun NetworkToolShortcutCard(
    title: String,
    subtitle: String,
    mark: String,
    color: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(ShapeM)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = ShapeM,
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.46f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(9.dp)
        ) {
            MarkBox(mark, bg, color)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ToolPageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val backShape = RoundedCornerShape(14.dp)
        Surface(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .clip(backShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ),
            shape = backShape,
            color = Color.White,
            border = BorderStroke(1.dp, Border),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("‹", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextDark, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
            Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


private data class Ipv6DiagnosticCheck(
    val title: String,
    val detail: String,
    val passed: Boolean
)

private suspend fun runIpv6DiagnosticChecks(
    context: Context,
    onProgress: suspend (List<Ipv6DiagnosticCheck>) -> Unit
): List<Ipv6DiagnosticCheck> = withContext(Dispatchers.IO) {
    val checks = mutableListOf<Ipv6DiagnosticCheck>()
    suspend fun add(title: String, passed: Boolean, detail: String) {
        checks += Ipv6DiagnosticCheck(title, detail, passed)
        withContext(Dispatchers.Main) { onProgress(checks.toList()) }
    }

    val cm = context.getSystemService(ConnectivityManager::class.java)
    val network = cm?.activeNetwork
    val lp = network?.let { cm.getLinkProperties(it) }
    val localIpv6 = lp?.linkAddresses.orEmpty()
        .map { it.address }
        .filterIsInstance<Inet6Address>()
        .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isMulticastAddress }
    add(
        title = "有效 IPv6 地址",
        passed = localIpv6 != null,
        detail = localIpv6?.hostAddress?.substringBefore('%') ?: "当前网络未获得可用 IPv6 地址"
    )

    val hasDefaultRoute = lp?.routes.orEmpty().any { route ->
        val destination = route.destination
        destination.address is Inet6Address && destination.prefixLength == 0
    }
    add("IPv6 默认路由", hasDefaultRoute, if (hasDefaultRoute) "已发现 ::/0 默认路由" else "未发现 IPv6 默认路由")

    val orderedDns = NetworkDnsResolver.orderedDnsServers(context, network)
    val firstDns = orderedDns.firstOrNull()
    add(
        "本机 DNS 可用",
        orderedDns.isNotEmpty(),
        if (firstDns != null) {
            "优先 ${if (firstDns is Inet4Address) "IPv4" else "IPv6"} DNS：${firstDns.hostAddress?.substringBefore('%')}"
        } else "当前链路未提供 DNS"
    )

    val domesticCandidates = listOf("www.baidu.com", "www.qq.com", "www.taobao.com", "testipv6.cn")
    var targetHost = ""
    var targetIpv6: Inet6Address? = null
    var dualStackPassed = false
    for (candidate in domesticCandidates) {
        val resolution = runCatching {
            NetworkDnsResolver.resolveBlocking(
                context = context,
                hostInput = candidate,
                network = network,
                includeIpv4 = true,
                includeIpv6 = true,
                timeoutMs = 1_500
            )
        }.getOrNull() ?: continue
        val v4 = resolution.addresses.any { it is Inet4Address }
        val v6 = resolution.addresses.filterIsInstance<Inet6Address>().firstOrNull()
        if (v6 != null && targetIpv6 == null) {
            targetHost = candidate
            targetIpv6 = v6
        }
        if (v4 && v6 != null) dualStackPassed = true
        if (targetIpv6 != null && dualStackPassed) break
    }
    add(
        "AAAA 解析",
        targetIpv6 != null,
        targetIpv6?.let { "$targetHost → ${it.hostAddress?.substringBefore('%')}" } ?: "国内常用目标未返回 AAAA"
    )
    add("双栈域名", dualStackPassed, if (dualStackPassed) "同一目标同时返回 A / AAAA" else "未确认双栈解析")

    val publicIpv6Result = runCatching { PublicIpDetector.detect(network).ipv6 }.getOrNull()
    val publicIpv6Ok = publicIpv6Result?.isUsableIpText() == true
    add("公网 IPv6", publicIpv6Ok, if (publicIpv6Ok) publicIpv6Result.orEmpty() else "公网 IPv6 暂未检测到")

    val targetAddress = targetIpv6
    val simplePingOk = targetAddress?.let { address ->
        runCatching {
            val target = address.hostAddress?.substringBefore('%') ?: return@runCatching false
            val commands = listOf(
                listOf("/system/bin/ping6", "-c", "1", "-W", "2", target),
                listOf("/system/bin/ping", "-6", "-c", "1", "-W", "2", target)
            )
            commands.any { command ->
                runCatching {
                    val process = ProcessBuilder(command).redirectErrorStream(true).start()
                    val finished = process.waitFor(3, TimeUnit.SECONDS)
                    if (!finished) process.destroyForcibly()
                    finished && process.exitValue() == 0
                }.getOrDefault(false)
            }
        }.getOrDefault(false)
    } ?: false
    add("IPv6 ICMP", simplePingOk, if (simplePingOk) "$targetHost ICMPv6 正常" else "ICMPv6 未通过，可能受目标策略影响")

    val tcpOk = targetAddress?.let { address ->
        runCatching {
            Socket().use { socket ->
                network?.bindSocket(socket)
                socket.connect(InetSocketAddress(address, 443), 2_500)
            }
            true
        }.getOrDefault(false)
    } ?: false
    add("IPv6 TCP 443", tcpOk, if (tcpOk) "$targetHost:443 建连成功" else "TCP 443 未建立")

    val basicMtuOk = targetAddress?.let { address ->
        runMtuPing(
            address = address.hostAddress?.substringBefore('%').orEmpty(),
            ipv6 = true,
            mtu = 1280,
            timeoutMs = 2_000,
            mode = MtuProbeMode.PMTU
        ).first
    } ?: false
    add("IPv6 基础 MTU", basicMtuOk, if (basicMtuOk) "1280 字节路径正常" else "1280 字节大包未确认")

    val interfaceMtu = readLocalInterfaceMtu(context).first?.coerceIn(1280, 1500) ?: 1500
    val nearMtuOk = targetAddress?.let { address ->
        runMtuPing(
            address = address.hostAddress?.substringBefore('%').orEmpty(),
            ipv6 = true,
            mtu = interfaceMtu,
            timeoutMs = 2_000,
            mode = MtuProbeMode.PMTU
        ).first
    } ?: false
    add("接近链路 MTU", nearMtuOk, if (nearMtuOk) "$interfaceMtu 字节通过" else "$interfaceMtu 字节未通过或无响应")

    checks
}

@Composable
private fun Ipv6DiagnosticToolPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf<List<Ipv6DiagnosticCheck>>(emptyList()) }
    val score = checks.count { it.passed }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ToolPageHeader("IPv6 专项检测", "参考中国网络环境的 10 分制检测", onBack)
        }
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("ipv6", "IPv6 状况评分", Green)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (checks.isEmpty()) "—/10" else "$score/10",
                        color = if (score >= 8) Green else if (score >= 6) Orange else ErrorRed,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    "自动使用当前网络，并优先通过本机 IPv4 DNS 查询 A / AAAA；测试不会修改系统网络设置。",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Button(
                    onClick = {
                        if (!running) {
                            running = true
                            checks = emptyList()
                            scope.launch {
                            runCatching {
                                runIpv6DiagnosticChecks(context.applicationContext) { progress -> checks = progress }
                            }
                            running = false
                            }
                        }
                    },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = ShapeM
                ) {
                    Text(if (running) "检测中…" else if (checks.isEmpty()) "开始 IPv6 检测" else "重新检测")
                }
            }
        }
        if (checks.isEmpty()) {
            item {
                SoftCard {
                    Text("点击开始后依次检查 IPv6 地址、路由、DNS、连通性和大包能力。", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            itemsIndexed(checks) { index, check ->
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", color = Muted, fontSize = 11.sp, modifier = Modifier.width(22.dp))
                        MarkBox(if (check.passed) "check" else "!", if (check.passed) GreenSoft else RedSoft, if (check.passed) Green else ErrorRed)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(check.title, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(check.detail, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Text(if (check.passed) "通过" else "未通过", color = if (check.passed) Green else ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NsLookupToolPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val store = remember { NetToolHistoryStore(context.applicationContext) }
    var host by remember { mutableStateOf("www.baidu.com") }
    var running by remember { mutableStateOf(false) }
    var recordType by remember { mutableStateOf(NsLookupRecordType.ALL) }
    var timeoutMs by remember { mutableStateOf("1200") }
    var lookupMode by remember { mutableStateOf(NsLookupMode.LOCAL) }
    var dns1 by remember { mutableStateOf(DEFAULT_NS_DNS1) }
    var dns2 by remember { mutableStateOf(DEFAULT_NS_DNS2) }
    var dns1History by remember { mutableStateOf(store.loadNsDns(1)) }
    var dns2History by remember { mutableStateOf(store.loadNsDns(2)) }
    var latest by remember { mutableStateOf<NsLookupToolRecord?>(null) }
    var records by remember { mutableStateOf(store.loadNsLookup()) }
    val localDns by remember { mutableStateOf(readLocalDnsServers(context.applicationContext)) }
    val recentHosts = remember(records) { records.map { it.host }.distinct().take(8) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ToolPageHeader("解析配置", "可切换本机 DNS / 自定义 DNS 解析 A / AAAA", onBack) }
        item {
            SoftCard {
                ConfigLongRow(
                    label = "目标",
                    content = {
                        HistoryTextField(
                            value = host,
                            onValueChange = { host = it },
                            placeholder = "www.baidu.com",
                            history = recentHosts,
                            onPick = { host = it },
                            onDelete = { deleted -> records = records.filterNot { it.host == deleted } },
                            leadingMark = "host"
                        )
                    }
                )
                ConfigLongRow(
                    label = "本机DNS",
                    content = {
                        ConfigInfoBox(
                            text = if (localDns.isEmpty()) "未读取到本机 DNS" else localDns.joinToString(" / "),
                            mark = "dns",
                            color = Blue
                        )
                    }
                )
                ConfigLongRow(
                    label = "解析方式",
                    content = {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                            NsLookupMode.values().forEach { mode ->
                                MiniSelectPill(mode.label, selected = lookupMode == mode, modifier = Modifier.weight(1f)) { lookupMode = mode }
                            }
                        }
                    }
                )
                if (lookupMode == NsLookupMode.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ConfigColumn(label = "DNS1", modifier = Modifier.weight(1f)) {
                        HistoryTextField(
                            value = dns1,
                            onValueChange = { dns1 = it.trim() },
                            placeholder = DEFAULT_NS_DNS1,
                            history = dns1History,
                            onPick = { dns1 = it },
                            onDelete = { deleted ->
                                store.deleteNsDns(1, deleted)
                                dns1History = store.loadNsDns(1)
                            },
                            leadingMark = "dns"
                        )
                    }
                    ConfigColumn(label = "DNS2", modifier = Modifier.weight(1f)) {
                        HistoryTextField(
                            value = dns2,
                            onValueChange = { dns2 = it.trim() },
                            placeholder = DEFAULT_NS_DNS2,
                            history = dns2History,
                            onPick = { dns2 = it },
                            onDelete = { deleted ->
                                store.deleteNsDns(2, deleted)
                                dns2History = store.loadNsDns(2)
                            },
                            leadingMark = "dns"
                        )
                    }
                }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ConfigColumn(label = "记录类型", modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                            NsLookupRecordType.values().forEach { type ->
                                MiniSelectPill(type.label, selected = recordType == type, modifier = Modifier.weight(1f)) { recordType = type }
                            }
                        }
                    }
                    ConfigColumn(label = "超时", modifier = Modifier.weight(1f)) {
                        CleanField(
                            value = timeoutMs,
                            onValueChange = { timeoutMs = it.onlyDigits().take(5) },
                            placeholder = "1200",
                            keyboardType = KeyboardType.Number,
                            leadingMark = "hourglass"
                        )
                    }
                }
                Button(
                    onClick = {
                        if (running) return@Button
                        running = true
                        scope.launch {
                            val timeout = timeoutMs.safeInt(1200, 500, 10000)
                            if (lookupMode == NsLookupMode.CUSTOM) {
                                store.addNsDns(1, dns1)
                                store.addNsDns(2, dns2)
                                dns1History = store.loadNsDns(1)
                                dns2History = store.loadNsDns(2)
                            }
                            val record = runNsLookupTool(context.applicationContext, host, lookupMode, dns1, dns2, recordType, timeout)
                            latest = record
                            store.addNsLookup(record)
                            records = store.loadNsLookup()
                            running = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(if (running) "解析中..." else "开始解析", fontWeight = FontWeight.ExtraBold) }
            }
        }
        latest?.let { record ->
            item {
                NsLookupRecordCard(record, compact = false, onCopy = {
                    clipboard.setText(AnnotatedString(record.copyText()))
                    Toast.makeText(context, "已复制解析结果", Toast.LENGTH_SHORT).show()
                })
            }
        }
        item { Text("解析记录", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (records.isEmpty()) {
            item { Text("暂无记录。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp)) }
        } else {
            items(records, key = { it.id }) { record ->
                SwipeDeleteToolBox(onDelete = {
                    store.deleteNsLookup(record.id)
                    records = store.loadNsLookup()
                }) {
                    NsLookupRecordCard(record, compact = true, onCopy = {
                        clipboard.setText(AnnotatedString(record.copyText()))
                        Toast.makeText(context, "已复制解析结果", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun TracketToolPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { NetToolHistoryStore(context.applicationContext) }
    val tracePaused = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val traceProcess = remember { java.util.concurrent.atomic.AtomicReference<Process?>(null) }
    val traceRunToken = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    var host by remember { mutableStateOf("www.baidu.com") }
    var runState by remember { mutableStateOf(TracketRunState.Idle) }
    var traceJob by remember { mutableStateOf<Job?>(null) }
    var ipPolicy by remember { mutableStateOf(ToolIpPolicy.IPV6_FIRST) }
    var maxHopsText by remember { mutableStateOf("30") }
    var timeoutMsText by remember { mutableStateOf("1200") }
    var liveHops by remember { mutableStateOf<List<String>>(emptyList()) }
    var liveTitle by remember { mutableStateOf("追踪过程") }
    var records by remember { mutableStateOf(store.loadTracket()) }
    var expandedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val localDns by remember { mutableStateOf(readLocalDnsServers(context.applicationContext)) }
    val recentHosts = remember(records) { records.map { it.host }.distinct().take(8) }
    val tracingActive = runState == TracketRunState.Running || runState == TracketRunState.Paused

    fun appendTraceEvent(text: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        liveHops = (liveHops + "· $stamp  $text").takeLast(40)
    }

    fun completedHopCount(): Int = liveHops.count { !it.startsWith("·") }
    fun isCurrentTrace(token: Long): Boolean = traceRunToken.get() == token

    fun stopTrace(reason: String = "追踪已停止") {
        tracePaused.set(false)
        traceRunToken.incrementAndGet()
        traceProcess.getAndSet(null)?.let { process ->
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
        }
        traceJob?.cancel()
        traceJob = null
        if (tracingActive) {
            appendTraceEvent(reason)
            runState = TracketRunState.Canceled
            liveTitle = reason
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tracePaused.set(false)
            traceRunToken.incrementAndGet()
            traceProcess.getAndSet(null)?.let { process ->
                runCatching { process.destroy() }
                runCatching { process.destroyForcibly() }
            }
            traceJob?.cancel()
        }
    }

    DisposableEffect(tracingActive) {
        if (!tracingActive) {
            onDispose { }
        } else {
            val lifecycleOwner = context as? LifecycleOwner
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    stopTrace("APP进入后台，路由追踪已停止")
                }
            }
            lifecycleOwner?.lifecycle?.addObserver(observer)
            onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
        }
    }

    DisposableEffect(tracingActive) {
        if (!tracingActive) {
            onDispose { }
        } else {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    scope.launch { stopTrace("网络已断开，路由追踪已停止") }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        scope.launch { stopTrace("当前网络不可用，路由追踪已停止") }
                    }
                }
            }
            runCatching { cm?.registerDefaultNetworkCallback(callback) }
            onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ToolPageHeader("追踪配置", "逐跳追踪域名经过的 IP；结果以系统返回为准") {
                stopTrace("返回页面，路由追踪已停止")
                onBack()
            }
        }
        item {
            SoftCard {
                ConfigLongRow(
                    label = "目标",
                    content = {
                        HistoryTextField(
                            value = host,
                            onValueChange = { host = it },
                            placeholder = "www.baidu.com",
                            history = recentHosts,
                            onPick = { host = it },
                            onDelete = { deleted -> records = records.filterNot { it.host == deleted } },
                            leadingMark = "target"
                        )
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ConfigColumn(label = "IP策略", modifier = Modifier.weight(1f)) {
                        PolicyPicker(current = ipPolicy, onPick = { ipPolicy = it })
                    }
                    ConfigColumn(label = "跳数", modifier = Modifier.weight(1f)) {
                        CleanField(
                            value = maxHopsText,
                            onValueChange = { maxHopsText = it.onlyDigits().take(2) },
                            placeholder = "30",
                            keyboardType = KeyboardType.Number,
                            leadingMark = "chart"
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ConfigColumn(label = "超时", modifier = Modifier.weight(1f)) {
                        CleanField(
                            value = timeoutMsText,
                            onValueChange = { timeoutMsText = it.onlyDigits().take(5) },
                            placeholder = "1200",
                            keyboardType = KeyboardType.Number,
                            leadingMark = "hourglass"
                        )
                    }
                    ConfigColumn(label = "说明", modifier = Modifier.weight(1f)) {
                        ConfigInfoBox("显示经过IP", mark = "i", color = Blue)
                    }
                }
                Button(
                    onClick = {
                        when (runState) {
                            TracketRunState.Running -> {
                                runState = TracketRunState.Paused
                                tracePaused.set(true)
                                liveTitle = "已暂停：保留 ${completedHopCount()} 跳结果"
                                appendTraceEvent("路由追踪已暂停")
                                return@Button
                            }
                            TracketRunState.Paused -> {
                                runState = TracketRunState.Running
                                tracePaused.set(false)
                                liveTitle = "继续追踪：已完成 ${completedHopCount()} 跳"
                                appendTraceEvent("路由追踪继续")
                                return@Button
                            }
                            else -> Unit
                        }
                        traceJob?.cancel()
                        val token = traceRunToken.incrementAndGet()
                        runState = TracketRunState.Running
                        tracePaused.set(false)
                        liveHops = emptyList()
                        liveTitle = "正在追踪：第 0 / ${maxHopsText.safeInt(30, 1, 30)} 跳"
                        appendTraceEvent("路由追踪开始")
                        traceJob = scope.launch {
                            val maxHops = maxHopsText.safeInt(30, 1, 30)
                            val timeout = timeoutMsText.safeInt(1200, 500, 10000)
                            try {
                                val record = runTracketToolLive(
                                    context = context.applicationContext,
                                    input = host,
                                    dnsSnapshot = localDns,
                                    policy = ipPolicy,
                                    maxHops = maxHops,
                                    timeoutMs = timeout,
                                    activeProcess = traceProcess,
                                    shouldPause = { tracePaused.get() },
                                    onEvent = { if (isCurrentTrace(token)) appendTraceEvent(it) },
                                    onHop = { line ->
                                        if (isCurrentTrace(token)) {
                                            liveHops = liveHops + line
                                            liveTitle = if (line.startsWith("错误：")) "追踪失败" else "正在追踪：第 ${completedHopCount()} / $maxHops 跳"
                                        }
                                    }
                                )
                                if (isCurrentTrace(token)) {
                                    if (record.error.isBlank()) {
                                        runState = TracketRunState.Finished
                                        liveTitle = "追踪完成：${record.hops.size} 跳 · ${record.costMs}ms"
                                    } else {
                                        runState = TracketRunState.Failed
                                        liveTitle = "追踪失败：${record.error}"
                                    }
                                    store.addTracket(record)
                                    records = store.loadTracket()
                                }
                            } catch (_: CancellationException) {
                                if (isCurrentTrace(token)) runState = TracketRunState.Canceled
                            } finally {
                                if (isCurrentTrace(token)) traceJob = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) {
                    Text(
                        when (runState) {
                            TracketRunState.Running -> "暂停追踪"
                            TracketRunState.Paused -> "继续追踪"
                            else -> "开始追踪"
                        },
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                if (tracingActive) {
                    OutlinedButton(
                        onClick = { stopTrace("手动停止，路由追踪已取消") },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("停止追踪", fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (tracingActive || liveHops.isNotEmpty()) {
            item { TracketLiveProcessCard(title = liveTitle, hops = liveHops) }
        }
        item { Text("追踪历史", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (records.isEmpty()) {
            item { Text("暂无记录。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp)) }
        } else {
            items(records, key = { it.id }) { record ->
                val expanded = expandedIds.contains(record.id)
                SwipeDeleteToolBox(onDelete = {
                    store.deleteTracket(record.id)
                    records = store.loadTracket()
                    expandedIds = expandedIds - record.id
                }) {
                    TracketRecordCard(
                        record = record,
                        expanded = expanded,
                        onToggle = { expandedIds = if (expanded) expandedIds - record.id else expandedIds + record.id }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}


private enum class MtuProbeMode(val label: String) {
    FAILOVER("故障转移"),
    ICMP("ICMP"),
    TCP("TCP"),
    PMTU("PMTU")
}

private enum class MtuRunMode(val label: String) {
    IPV4("IPv4"),
    IPV6("IPv6"),
    AUTO("自动")
}

private data class MtuStep(val mtu: Int, val success: Boolean, val detail: String)
private data class MtuCheckLine(val name: String, val value: String, val detail: String, val ok: Boolean = true)
private data class MtuResult(
    val target: String,
    val address: String,
    val protocol: String,
    val method: String,
    val mtu: Int?,
    val steps: List<MtuStep>,
    val analysis: String,
    val error: String = "",
    val localMtu: Int? = null,
    val localDetail: String = "",
    val tcpMss: Int? = null,
    val tcpMtu: Int? = null,
    val tcpDetail: String = "",
    val appLayerDetail: String = "",
    val checkLines: List<MtuCheckLine> = emptyList()
)

private enum class RoamingPingTarget(val label: String) {
    GATEWAY("内网"),
    EXTERNAL("外网")
}

private data class RoamingWifiSample(
    val timeText: String,
    val elapsedRealtimeNanos: Long,
    val elapsedMs: Long,
    val bssid: String?,
    val rssi: Int?,
    val frequencyMhz: Int?,
    val channel: Int?,
    val linkSpeed: Int?,
    val ssid: String = "未知",
    val unavailableReason: String? = null,
    val candidateBssid: String? = null,
    val candidateRssi: Int? = null
)

private data class RoamingPingSample(
    val timeText: String,
    val target: RoamingPingTarget,
    val startedElapsedMs: Long,
    val completedElapsedMs: Long,
    val attempted: Boolean,
    val latencyMs: Int?,
    val failureReason: String? = null
)

private data class RoamingSwitchEvent(
    val timeText: String,
    val observedElapsedMs: Long,
    val oldBssid: String,
    val newBssid: String,
    val oldBand: String,
    val newBand: String,
    val oldChannel: Int?,
    val newChannel: Int?,
    val oldRssi: Int?,
    val newRssi: Int?,
    val observationMs: Long,
    val pingRecoveryMs: Long?,
    val roamingLossCount: Int
)

private data class StickyApEvent(
    val startTimeText: String,
    val durationMs: Long,
    val durationText: String,
    val currentBssid: String,
    val currentRssi: Int,
    val candidateBssid: String,
    val candidateRssi: Int,
    val deltaDb: Int,
    val reason: String = "弱信号+候选更强"
)

private data class RoamingBssidSegment(
    val startIndex: Int,
    val endIndexExclusive: Int,
    val bssid: String
)

private data class RoamingQuality(
    val label: String,
    val score: Int,
    val color: Color,
    val summary: String
)

private data class WifiSnapshot(
    val rssi: Int?,
    val linkSpeed: Int?,
    val bssid: String?,
    val ssid: String,
    val frequencyMhz: Int?,
    val unavailableReason: String?,
    val candidateBssid: String? = null,
    val candidateRssi: Int? = null
)

private data class RoamingCandidateCache(
    val updatedElapsedMs: Long = 0L,
    val ssid: String = "",
    val currentBssid: String? = null,
    val candidateBssid: String? = null,
    val candidateRssi: Int? = null
)


private data class RoamingNetworkEvent(
    val timeText: String,
    val elapsedSec: Int,
    val label: String
)

private data class RoamingHistoryRecord(
    val id: Long,
    val timeText: String,
    val targetText: String,
    val durationText: String,
    val sampleCount: Int,
    val lossCount: Int,
    val gatewaySummary: String,
    val externalSummary: String,
    val rssiSummary: String,
    val speedSummary: String,
    val roamingSummary: String,
    val eventLines: List<String>,
    val networkEventLines: List<String>
)

private enum class RoamingTargetMode(val label: String) {
    GATEWAY_AND_EXTERNAL("路由器+外网"),
    GATEWAY("仅路由器"),
    EXTERNAL("仅外网")
}

private const val ROAMING_WIFI_SAMPLE_INTERVAL_MS = 50L
private const val ROAMING_LIVE_WINDOW_MS = 10_000L
private const val ROAMING_NETWORK_LOST_GRACE_MS = 8_000L
private const val ROAMING_CANDIDATE_REFRESH_MS = 500L
private const val ROAMING_STICKY_WEAK_RSSI = -75
private const val ROAMING_STICKY_ADVANTAGE_DB = 8
private const val ROAMING_STICKY_DURATION_MS = 5_000L
private var roamingCandidateCache = RoamingCandidateCache()
private val RoamingPingIntervalPresets = listOf(100, 200, 500, 1000)
private val RoamingPingTimeoutPresets = listOf(800, 1000, 2000, 5000)

private suspend fun resolveMtuAddress(hostInput: String, policy: ToolIpPolicy): Pair<InetAddress?, String?> = withContext(Dispatchers.IO) {
    val host = cleanToolHost(hostInput)
    runCatching {
        val addresses = NetworkDnsResolver.resolveAddressesBlocking(
            host = host,
            includeIpv4 = true,
            includeIpv6 = true
        ).filterNot { it.isLoopbackAddress }
        val selected = chooseToolTargetAddress(addresses, policy)
        selected to null
    }.getOrElse { null to (it.message ?: "解析失败") }
}

private fun readLocalInterfaceMtu(context: Context): Pair<Int?, String> {
    return runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val lp = network?.let { cm.getLinkProperties(it) }
        val ifaceName = lp?.interfaceName
        val lpMtu = lp?.mtu?.takeIf { it > 0 }
        val ifaceMtu = ifaceName?.let { name ->
            runCatching { NetworkInterface.getByName(name)?.mtu?.takeIf { it > 0 } }.getOrNull()
        }
        val fallback = runCatching {
            java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .mapNotNull { it.mtu.takeIf { mtu -> mtu > 0 } }
                .maxOrNull()
        }.getOrNull()
        val mtu = lpMtu ?: ifaceMtu ?: fallback
        val source = when {
            lpMtu != null -> "LinkProperties.getMtu()"
            ifaceMtu != null -> "NetworkInterface ${ifaceName ?: "active"}"
            fallback != null -> "NetworkInterface fallback"
            else -> "未读取到"
        }
        mtu to "$source；本地网卡/链路上限，仅作路径探测天花板参考"
    }.getOrElse { null to (it.message ?: "读取本地 MTU 失败") }
}

private data class MtuTcpProbe(
    val connected: Boolean,
    val host: String,
    val port: Int,
    val address: String,
    val protocol: String,
    val costMs: Long,
    val mss: Int?,
    val mtu: Int?,
    val detail: String
)

private suspend fun runTcpBusinessMtuProbe(
    context: Context,
    hostInput: String,
    ipv6: Boolean,
    timeoutMs: Int,
    port: Int = 443
): MtuTcpProbe = withContext(Dispatchers.IO) {
    val host = cleanToolHost(hostInput)
    val address = resolveMtuFamilyAddress(host, ipv6)
        ?: return@withContext MtuTcpProbe(false, host, port, "-", if (ipv6) "IPv6" else "IPv4", 0L, null, null, "未解析到${if (ipv6) "IPv6" else "IPv4"}地址")
    val cleanAddress = address.hostAddress?.substringBefore('%') ?: host
    val localMtu = readLocalInterfaceMtu(context).first
    val start = SystemClock.elapsedRealtime()
    val socket = Socket()
    val ok = runCatching {
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(address, port), timeoutMs.coerceIn(500, 10000))
        true
    }.getOrElse { false }
    runCatching { socket.close() }
    val cost = SystemClock.elapsedRealtime() - start
    val estimatedMss = if (ok) localMtu?.let { (it - if (ipv6) 60 else 40).coerceAtLeast(0) } else null
    val detail = if (ok) {
        "TCP ${host}:$port 连接成功 ${cost}ms；受 Android Java 接口限制，MSS 为按本地接口 MTU 计算的建议值，不等同于精确路径 MTU。"
    } else {
        "TCP ${host}:$port 连接失败或超时。"
    }
    MtuTcpProbe(ok, host, port, cleanAddress, if (ipv6) "IPv6" else "IPv4", cost, estimatedMss, localMtu, detail)
}

private fun pmtuModeNote(ipv6: Boolean): String {
    return if (ipv6) "PMTU增强：IPv6 使用大包探测，依赖路径 Packet Too Big 语义；若系统 ping/网络策略限制，结果会降级参考。" else "PMTU增强：IPv4 优先使用 DF 禁止分片语义探测；若系统 ping 不支持 -M do，会显示降级或失败原因。"
}

private fun runMtuPing(address: String, ipv6: Boolean, mtu: Int, timeoutMs: Int, mode: MtuProbeMode): Pair<Boolean, String> {
    val waitSec = ((timeoutMs.coerceIn(500, 10000) + 999) / 1000).coerceIn(1, 10).toString()
    val payload = if (ipv6) (mtu - 48).coerceAtLeast(0) else (mtu - 28).coerceAtLeast(0)
    val pmtuCommands = if (ipv6) {
        listOf(
            listOf("/system/bin/ping6", "-c", "1", "-W", waitSec, "-s", payload.toString(), address),
            listOf("ping6", "-c", "1", "-W", waitSec, "-s", payload.toString(), address),
            listOf("/system/bin/ping", "-6", "-c", "1", "-W", waitSec, "-s", payload.toString(), address),
            listOf("ping", "-6", "-c", "1", "-W", waitSec, "-s", payload.toString(), address)
        )
    } else {
        listOf(
            listOf("/system/bin/ping", "-M", "do", "-c", "1", "-W", waitSec, "-s", payload.toString(), address),
            listOf("ping", "-M", "do", "-c", "1", "-W", waitSec, "-s", payload.toString(), address)
        )
    }
    val fastCommands = if (ipv6) {
        pmtuCommands
    } else {
        listOf(
            listOf("/system/bin/ping", "-c", "1", "-W", waitSec, "-s", payload.toString(), address),
            listOf("ping", "-c", "1", "-W", waitSec, "-s", payload.toString(), address)
        )
    }
    val commands = when (mode) {
        MtuProbeMode.FAILOVER -> pmtuCommands + fastCommands
        MtuProbeMode.ICMP -> fastCommands
        MtuProbeMode.PMTU -> pmtuCommands
        MtuProbeMode.TCP -> pmtuCommands
    }
    var last = ""
    var unsupported = false
    for (cmd in commands) {
        val result = runCatching {
            val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val finished = process.waitFor((timeoutMs + 900).toLong(), TimeUnit.MILLISECONDS)
            if (!finished) process.destroyForcibly()
            process.inputStream.bufferedReader().use { it.readText() }
        }.getOrDefault("")
        if (result.isBlank()) continue
        last = result
        val lower = result.lowercase(Locale.getDefault())
        if (lower.contains("invalid option") || lower.contains("usage:")) {
            unsupported = true
            continue
        }
        val ok = lower.contains("bytes from") || lower.contains("1 received") || lower.contains("1 packets received") || lower.contains("0% packet loss")
        val hint = result.lineSequence().firstOrNull {
            it.contains("time") || it.contains("too big", true) || it.contains("frag", true) || it.contains("message too long", true)
        }?.take(90).orEmpty()
        val method = when {
            !ipv6 && cmd.contains("-M") -> "PMTU/DF"
            ipv6 -> "ICMPv6"
            else -> "快速"
        }
        return ok to "[$method] ${hint.ifBlank { if (ok) "成功" else "失败/超时" }}"
    }
    val fallback = when {
        unsupported -> "系统 ping 不支持该参数"
        last.isNotBlank() -> last.lineSequence().firstOrNull()?.take(90).orEmpty()
        else -> "无响应"
    }
    return false to fallback
}

private suspend fun waitWhileMtuPaused(pauseRequested: () -> Boolean) {
    while (pauseRequested() && currentCoroutineContext().isActive) {
        delay(80L)
    }
}

private suspend fun runMtuCandidate(
    address: String,
    ipv6: Boolean,
    mtu: Int,
    timeoutMs: Int,
    mode: MtuProbeMode,
    attempts: Int = 3,
    requiredSuccess: Int = 2,
    pauseRequested: () -> Boolean = { false }
): Triple<Boolean, String, Int> {
    val totalAttempts = attempts.coerceAtLeast(1)
    val needed = requiredSuccess.coerceIn(1, totalAttempts)
    var successCount = 0
    var executed = 0
    val details = mutableListOf<String>()
    while (executed < totalAttempts && currentCoroutineContext().isActive) {
        waitWhileMtuPaused(pauseRequested)
        currentCoroutineContext().ensureActive()
        val (ok, detail) = runMtuPing(address, ipv6, mtu, timeoutMs, mode)
        executed++
        if (ok) successCount++
        if (detail.isNotBlank()) details += detail
        val remaining = totalAttempts - executed
        if (successCount >= needed || successCount + remaining < needed) break
    }
    val passed = successCount >= needed
    val summary = "$successCount/$executed 成功" + details.firstOrNull()?.let { " · $it" }.orEmpty()
    return Triple(passed, summary, successCount)
}

private data class MtuPathProbe(
    val protocol: String,
    val address: String,
    val mtu: Int?,
    val steps: List<MtuStep>,
    val detail: String,
    val confidence: String,
    val error: String = ""
)

private suspend fun resolveMtuFamilyAddress(hostInput: String, ipv6: Boolean): InetAddress? = withContext(Dispatchers.IO) {
    val host = cleanToolHost(hostInput)
    runCatching {
        NetworkDnsResolver.resolveAddressesBlocking(
            host = host,
            includeIpv4 = !ipv6,
            includeIpv6 = ipv6
        ).firstOrNull { address ->
            if (ipv6) address is Inet6Address else address is Inet4Address
        }
    }.getOrNull()
}

private suspend fun runMtuCandidateQuick(
    address: String,
    ipv6: Boolean,
    mtu: Int,
    timeoutMs: Int,
    mode: MtuProbeMode,
    pauseRequested: () -> Boolean
): Triple<Boolean, String, Int> {
    var successes = 0
    var executed = 0
    val details = mutableListOf<String>()
    repeat(2) {
        waitWhileMtuPaused(pauseRequested)
        currentCoroutineContext().ensureActive()
        val (ok, detail) = runMtuPing(address, ipv6, mtu, timeoutMs, mode)
        executed++
        if (ok) successes++
        if (detail.isNotBlank()) details += detail
    }
    if (successes == 1) {
        waitWhileMtuPaused(pauseRequested)
        currentCoroutineContext().ensureActive()
        val (ok, detail) = runMtuPing(address, ipv6, mtu, timeoutMs, mode)
        executed++
        if (ok) successes++
        if (detail.isNotBlank()) details += detail
    }
    val passed = if (executed >= 3) successes >= 2 else successes == 2
    val summary = "$successes/$executed 成功" + details.firstOrNull()?.let { " · $it" }.orEmpty()
    return Triple(passed, summary, successes)
}

private suspend fun probeMtuPath(
    host: String,
    ipv6: Boolean,
    localMtu: Int?,
    timeoutMs: Int,
    probeMode: MtuProbeMode,
    onStep: suspend (MtuStep) -> Unit,
    pauseRequested: () -> Boolean = { false }
): MtuPathProbe {
    val protocol = if (ipv6) "IPv6" else "IPv4"
    val resolved = resolveMtuFamilyAddress(host, ipv6)
        ?: return MtuPathProbe(protocol, "-", null, emptyList(), "未解析到 $protocol 地址", "低", "无可用地址")
    val address = resolved.hostAddress?.substringBefore('%') ?: host
    val minimum = if (ipv6) 1280 else 576
    val cappedMax = (localMtu ?: 1500).coerceIn(minimum, 9000)
    val effectiveMode = if (probeMode == MtuProbeMode.FAILOVER) MtuProbeMode.PMTU else probeMode

    val steps = mutableListOf<MtuStep>()
    val observations = linkedMapOf<Int, Boolean>()

    suspend fun testCandidate(mtu: Int, label: String, quick: Boolean = true): Boolean {
        val result = if (quick) {
            runMtuCandidateQuick(address, ipv6, mtu, timeoutMs, effectiveMode, pauseRequested)
        } else {
            runMtuCandidate(
                address = address,
                ipv6 = ipv6,
                mtu = mtu,
                timeoutMs = timeoutMs,
                mode = effectiveMode,
                attempts = 3,
                requiredSuccess = 2,
                pauseRequested = pauseRequested
            )
        }
        val step = MtuStep(mtu, result.first, "[$protocol $label] ${result.second}")
        steps += step
        observations[mtu] = result.first
        withContext(Dispatchers.Main) { onStep(step) }
        return result.first
    }

    // 先测常见值，绝大多数 1500/1492/1480 路径可快速完成，再进入小区间精查。
    val commonCandidates = listOf(cappedMax, 1500, 1492, 1480, 1460, 1400, 1280, minimum)
        .filter { it in minimum..cappedMax }
        .distinct()
        .sortedDescending()

    var passedCommon: Int? = null
    var nearestFailedAbove: Int? = null
    for (candidate in commonCandidates) {
        waitWhileMtuPaused(pauseRequested)
        val ok = testCandidate(candidate, "快速")
        if (ok) {
            passedCommon = candidate
            break
        }
        nearestFailedAbove = candidate
    }

    val initialPass = passedCommon ?: return MtuPathProbe(
        protocol = protocol,
        address = address,
        mtu = null,
        steps = steps,
        detail = "$protocol 路径未得到有效 MTU",
        confidence = "低",
        error = "目标不响应或网络限制了大包探测"
    )

    var best = initialPass
    var low = initialPass + 1
    var high = ((nearestFailedAbove ?: (cappedMax + 1)) - 1).coerceAtMost(cappedMax)
    while (low <= high && currentCoroutineContext().isActive) {
        waitWhileMtuPaused(pauseRequested)
        val mid = (low + high) / 2
        if (testCandidate(mid, "精查")) {
            best = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    val confirm = runMtuCandidate(
        address = address,
        ipv6 = ipv6,
        mtu = best,
        timeoutMs = timeoutMs,
        mode = effectiveMode,
        attempts = 5,
        requiredSuccess = 4,
        pauseRequested = pauseRequested
    )
    val confirmStep = MtuStep(best, confirm.first, "[$protocol 最终确认] ${confirm.second}")
    steps += confirmStep
    observations[best] = confirm.first
    withContext(Dispatchers.Main) { onStep(confirmStep) }

    if (!confirm.first) {
        best = observations.filterValues { it }.keys.maxOrNull() ?: initialPass
    }

    val nextMtu = best + 1
    var nextFailed = true
    if (nextMtu <= cappedMax) {
        nextFailed = !testCandidate(nextMtu, "边界外", quick = false)
    }

    val sorted = observations.toSortedMap()
    var seenFailure = false
    var nonMonotonic = false
    sorted.values.forEach { ok ->
        if (!ok) seenFailure = true else if (seenFailure) nonMonotonic = true
    }
    val confidence = when {
        nonMonotonic -> "低"
        effectiveMode == MtuProbeMode.ICMP && confirm.first -> "中"
        confirm.first && nextFailed -> "高"
        confirm.first -> "中"
        else -> "低"
    }
    val modeText = when (effectiveMode) {
        MtuProbeMode.PMTU -> "PMTU"
        MtuProbeMode.ICMP -> "ICMP"
        else -> effectiveMode.label
    }
    val detail = buildString {
        append("$modeText 测得路径 MTU 约为 $best 字节 · 置信度$confidence")
        if (nonMonotonic) append(" · 结果有波动，建议换目标再测")
    }
    return MtuPathProbe(protocol, address, best, steps, detail, confidence)
}

private fun analyzeMtu(mtu: Int?, ipv6: Boolean): String {
    if (mtu == null) return "没有测出有效路径 MTU，可能是目标不响应或网络限制了大包探测。"
    return when {
        ipv6 && mtu < 1280 -> "结果低于 IPv6 的最低要求，建议更换目标后复测。"
        mtu >= 1498 -> "接近常见的 1500 字节，当前路径表现正常。"
        mtu in 1488..1497 -> "接近宽带拨号常见的 1492 字节。"
        mtu in 1390..1487 -> "路径 MTU 低于常见值，可能经过 VPN、隧道或额外封装。"
        else -> "路径 MTU 偏低，建议更换网络或目标复测。"
    }
}

private suspend fun runMtuProbeLive(
    context: Context,
    hostInput: String,
    runMode: MtuRunMode,
    probeMode: MtuProbeMode,
    timeoutMs: Int,
    pauseRequested: () -> Boolean,
    onStep: suspend (MtuStep) -> Unit
): MtuResult = withContext(Dispatchers.IO) {
    val host = cleanToolHost(hostInput)
    val local = readLocalInterfaceMtu(context)
    val checkLines = mutableListOf<MtuCheckLine>()
    checkLines += MtuCheckLine(
        name = "本地接口",
        value = local.first?.let { "$it 字节" } ?: "未读取到",
        detail = "这是手机当前网络接口允许的上限，不等于到目标服务器的路径 MTU。",
        ok = local.first != null
    )

    val families = when (runMode) {
        MtuRunMode.IPV4 -> listOf(false)
        MtuRunMode.IPV6 -> listOf(true)
        MtuRunMode.AUTO -> listOf(false, true)
    }

    val pathResults = mutableListOf<MtuPathProbe>()
    val tcpResults = mutableListOf<MtuTcpProbe>()

    for (ipv6 in families) {
        waitWhileMtuPaused(pauseRequested)
        if (probeMode == MtuProbeMode.TCP) {
            val tcp = runTcpBusinessMtuProbe(context, host, ipv6, timeoutMs)
            tcpResults += tcp
            checkLines += MtuCheckLine(
                name = "${tcp.protocol} TCP",
                value = if (tcp.connected) "连接正常" else "连接失败",
                detail = tcp.detail,
                ok = tcp.connected
            )
            checkLines += MtuCheckLine(
                name = "${tcp.protocol} 建议 MSS",
                value = tcp.mss?.let { "$it 字节" } ?: "—",
                detail = if (tcp.mss != null) "按当前接口 MTU 估算，仅供配置参考。" else "TCP 未连接，无法给出建议值。",
                ok = tcp.mss != null
            )
            continue
        }

        val primaryMode = if (probeMode == MtuProbeMode.FAILOVER) MtuProbeMode.PMTU else probeMode
        var path = probeMtuPath(
            host = host,
            ipv6 = ipv6,
            localMtu = local.first,
            timeoutMs = timeoutMs,
            probeMode = primaryMode,
            onStep = onStep,
            pauseRequested = pauseRequested
        )

        if (probeMode == MtuProbeMode.FAILOVER && path.mtu == null) {
            checkLines += MtuCheckLine(
                name = "${path.protocol} 故障转移",
                value = "改用 ICMP",
                detail = "PMTU 没有得到稳定结果，已自动使用 ICMP 辅助探测。",
                ok = true
            )
            path = probeMtuPath(
                host = host,
                ipv6 = ipv6,
                localMtu = local.first,
                timeoutMs = timeoutMs,
                probeMode = MtuProbeMode.ICMP,
                onStep = onStep,
                pauseRequested = pauseRequested
            )
        }

        if (probeMode == MtuProbeMode.FAILOVER && path.mtu == null) {
            val tcp = runTcpBusinessMtuProbe(context, host, ipv6, timeoutMs)
            tcpResults += tcp
            checkLines += MtuCheckLine(
                name = "${tcp.protocol} TCP辅助",
                value = if (tcp.connected) "连接正常" else "连接失败",
                detail = "大包探测失败，TCP 仅用于确认业务连通，不能替代准确路径 MTU。${tcp.detail}",
                ok = tcp.connected
            )
        }
        pathResults += path
    }

    pathResults.forEach { path ->
        val ipv6 = path.protocol == "IPv6"
        val mss = path.mtu?.let { (it - if (ipv6) 60 else 40).coerceAtLeast(0) }
        checkLines += MtuCheckLine(
            name = "${path.protocol} 路径 MTU",
            value = path.mtu?.let { "$it 字节" } ?: "未测出",
            detail = if (path.mtu != null) {
                "从本机到目标服务器，建议单个 IP 数据包不要超过 ${path.mtu} 字节。${analyzeMtu(path.mtu, ipv6)}"
            } else {
                path.error.ifBlank { path.detail }
            },
            ok = path.mtu != null
        )
        checkLines += MtuCheckLine(
            name = "${path.protocol} 建议 MSS",
            value = mss?.let { "$it 字节" } ?: "—",
            detail = if (mss != null) {
                "按路径 MTU 扣除 ${if (ipv6) "IPv6+TCP 60" else "IPv4+TCP 40"} 字节计算。"
            } else {
                "路径 MTU 未测出，暂时无法给出准确 MSS 建议。"
            },
            ok = mss != null
        )
    }

    val successfulPath = pathResults.firstOrNull { it.mtu != null }
    val effectiveMtu = successfulPath?.mtu
    val effectiveIpv6 = successfulPath?.protocol == "IPv6"
    val suggestedMss = effectiveMtu?.let { (it - if (effectiveIpv6) 60 else 40).coerceAtLeast(0) }
        ?: tcpResults.firstOrNull { it.mss != null }?.mss
    val analysis = when {
        effectiveMtu != null && suggestedMss != null -> "当前到目标的路径 MTU 约为 $effectiveMtu 字节，建议 TCP MSS 为 $suggestedMss 字节。"
        probeMode == MtuProbeMode.TCP && suggestedMss != null -> "TCP 连接正常；建议 MSS 为 $suggestedMss 字节，但该值来自本地接口估算，不是精确路径 MTU。"
        else -> "没有测出有效路径 MTU；可更换目标、切换模式或稍后再测。"
    }

    MtuResult(
        target = host,
        address = successfulPath?.address
            ?: tcpResults.firstOrNull { it.connected }?.address
            ?: pathResults.joinToString(" / ") { it.address }.ifBlank { "-" },
        protocol = when (runMode) {
            MtuRunMode.IPV4 -> "IPv4"
            MtuRunMode.IPV6 -> "IPv6"
            MtuRunMode.AUTO -> "IPv4 + IPv6"
        },
        method = probeMode.label,
        mtu = effectiveMtu,
        steps = pathResults.flatMap { it.steps },
        analysis = analysis,
        error = if (effectiveMtu == null && tcpResults.none { it.connected }) "未得到有效路径 MTU" else "",
        localMtu = local.first,
        localDetail = local.second,
        tcpMss = suggestedMss,
        tcpMtu = null,
        tcpDetail = tcpResults.joinToString("；") { it.detail },
        appLayerDetail = "",
        checkLines = checkLines
    )
}

private fun buildMtuOverallAnalysis(localMtu: Int?, icmpMtu: Int?, tcpMtu: Int?, ipv6: Boolean): String {
    val base = analyzeMtu(icmpMtu, ipv6)
    val localHint = localMtu?.let { "本地上限 $it；" }.orEmpty()
    val effectiveHint = icmpMtu?.let { "建议应用层单包保守控制在 ${max(1200, it - 80)} 字节以内。" }.orEmpty()
    return "$localHint$base $effectiveHint".trim()
}


private fun intToIpv4(value: Int): String {
    return listOf(value and 0xff, value shr 8 and 0xff, value shr 16 and 0xff, value shr 24 and 0xff).joinToString(".")
}

private fun normalizeWifiSsid(raw: String?): String {
    val value = raw.orEmpty().trim()
    return value.removePrefix("\"").removeSuffix("\"").ifBlank { "未知" }
}

private val WifiBssidPattern = Regex("^(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

private fun isUsableWifiBssid(raw: String?): Boolean {
    val value = raw?.trim().orEmpty()
    if (!WifiBssidPattern.matches(value)) return false
    if (value.equals("00:00:00:00:00:00", ignoreCase = true)) return false
    if (value.equals("02:00:00:00:00:00", ignoreCase = true)) return false
    if (value.equals("ff:ff:ff:ff:ff:ff", ignoreCase = true)) return false
    return (value.substring(0, 2).toIntOrNull(16)?.and(1) ?: 1) == 0
}

private fun wifiBandOf(frequencyMhz: Int?): String = when (frequencyMhz) {
    in 2400..2500 -> "2.4G"
    in 4900..5900 -> "5G"
    in 5925..7125 -> "6G"
    else -> "未知频段"
}

private fun wifiChannelOf(frequencyMhz: Int?): Int? {
    val frequency = frequencyMhz ?: return null
    return when (frequency) {
    2484 -> 14
    in 2412..2472 -> if ((frequency - 2407) % 5 == 0) (frequency - 2407) / 5 else null
    in 4910..4980 -> if ((frequency - 4000) % 5 == 0) (frequency - 4000) / 5 else null
    in 5000..5900 -> if ((frequency - 5000) % 5 == 0) (frequency - 5000) / 5 else null
    5935 -> 2
    in 5955..7115 -> if ((frequency - 5950) % 5 == 0) (frequency - 5950) / 5 else null
    else -> null
    }
}

private fun hasRoamingWifiPermissions(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val nearby = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    return fine && nearby
}

private fun isSystemLocationEnabled(context: Context): Boolean = runCatching {
    context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
}.getOrDefault(false)

private fun roamingWifiUnavailableReason(context: Context): String = when {
    !hasRoamingWifiPermissions(context) -> "权限受限：请授予精确位置和附近 Wi-Fi 设备权限"
    !isSystemLocationEnabled(context) -> "定位未开启：系统会隐藏 BSSID"
    !runCatching { (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled }.getOrDefault(false) -> "Wi-Fi 未开启"
    !isCurrentNetworkWifi(context) -> "当前未连接 Wi-Fi"
    else -> "系统限制：当前设备未提供 BSSID"
}

private fun readCachedRoamingCandidate(
    wifi: WifiManager,
    ssid: String,
    currentBssid: String?
): Pair<String?, Int?> {
    if (ssid == "未知" || currentBssid == null) return null to null
    val now = SystemClock.elapsedRealtime()
    val cached = roamingCandidateCache
    if (
        cached.ssid == ssid &&
        cached.currentBssid.equals(currentBssid, ignoreCase = true) &&
        now - cached.updatedElapsedMs < ROAMING_CANDIDATE_REFRESH_MS
    ) {
        return cached.candidateBssid to cached.candidateRssi
    }
    val candidate = runCatching {
        wifi.scanResults
            .asSequence()
            .filter { normalizeWifiSsid(it.SSID) == ssid }
            .filter { isUsableWifiBssid(it.BSSID) && !it.BSSID.equals(currentBssid, ignoreCase = true) }
            .filter { it.level in -120..0 }
            .maxByOrNull { it.level }
    }.getOrNull()
    roamingCandidateCache = RoamingCandidateCache(
        updatedElapsedMs = now,
        ssid = ssid,
        currentBssid = currentBssid,
        candidateBssid = candidate?.BSSID,
        candidateRssi = candidate?.level
    )
    return candidate?.BSSID to candidate?.level
}

private fun readWifiSnapshot(context: Context): WifiSnapshot {
    return runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifi.connectionInfo
        val rssi = info?.rssi?.takeIf { it in -120..0 }
        val speed = info?.linkSpeed?.takeIf { it > 0 }
        val rawBssid = info?.bssid
        val bssid = rawBssid?.trim()?.takeIf(::isUsableWifiBssid)
        val ssid = normalizeWifiSsid(info?.ssid)
        val frequency = info?.frequency?.takeIf { it in 2400..7125 }
        val (candidateBssid, candidateRssi) = readCachedRoamingCandidate(wifi, ssid, bssid)
        WifiSnapshot(
            rssi = rssi,
            linkSpeed = speed,
            bssid = bssid,
            ssid = ssid,
            frequencyMhz = frequency,
            unavailableReason = if (bssid == null) {
                if (rawBssid.equals("00:00:00:00:00:00", ignoreCase = true)) "Wi-Fi切换中：BSSID暂不可用"
                else roamingWifiUnavailableReason(context)
            } else null,
            candidateBssid = candidateBssid,
            candidateRssi = candidateRssi
        )
    }.getOrElse { WifiSnapshot(null, null, null, "未知", null, "系统限制：无法读取当前 Wi-Fi 信息") }
}

private fun readRoamingWifiSample(context: Context, runStartNanos: Long): RoamingWifiSample {
    val capturedNanos = SystemClock.elapsedRealtimeNanos()
    val snapshot = readWifiSnapshot(context)
    return RoamingWifiSample(
        timeText = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
        elapsedRealtimeNanos = capturedNanos,
        elapsedMs = ((capturedNanos - runStartNanos) / 1_000_000L).coerceAtLeast(0L),
        bssid = snapshot.bssid,
        rssi = snapshot.rssi,
        frequencyMhz = snapshot.frequencyMhz,
        channel = wifiChannelOf(snapshot.frequencyMhz),
        linkSpeed = snapshot.linkSpeed,
        ssid = snapshot.ssid,
        unavailableReason = snapshot.unavailableReason,
        candidateBssid = snapshot.candidateBssid,
        candidateRssi = snapshot.candidateRssi
    )
}

private fun readGatewayAddress(context: Context): String? {
    return runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val gateway = wifi.dhcpInfo?.gateway ?: 0
        gateway.takeIf { it != 0 }?.let(::intToIpv4)
    }.getOrNull()
}

private suspend fun pingForRoaming(target: String, timeoutMs: Int): PingCommandResult {
    val resolved = resolvePingTarget(target, PingProtocolMode.AUTO)
    if (resolved.error != null) return PingCommandResult(null, resolved.error)
    return icmpPingResolved(resolved.address, timeoutMs, resolved.protocol)
}

private fun isWifiNetworkReady(context: Context, requireValidated: Boolean): Boolean = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching false
    val network = cm.activeNetwork ?: return@runCatching false
    val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        (!requireValidated || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
}.getOrDefault(false)

private fun recommendedRoamingTimeoutMs(intervalMs: Int): Int = max(800, intervalMs * 2).coerceIn(800, 5000)


private fun isCurrentNetworkWifi(context: Context): Boolean {
    val capsWifi = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching false
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)
    if (capsWifi) return true
    return runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifi.connectionInfo ?: return@runCatching false
        info.networkId != -1 || isUsableWifiBssid(info.bssid)
    }.getOrDefault(false)
}

private fun buildRoamingSwitchEvents(
    wifiSamples: List<RoamingWifiSample>,
    pingSamples: List<RoamingPingSample>
): List<RoamingSwitchEvent> {
    val validSamples = wifiSamples.filter { isUsableWifiBssid(it.bssid) }
    if (validSamples.size < 2) return emptyList()

    val segments = mutableListOf<RoamingBssidSegment>()
    var segmentStart = 0
    var currentBssid = validSamples.first().bssid ?: return emptyList()
    for (index in 1 until validSamples.size) {
        val bssid = validSamples[index].bssid ?: continue
        if (!bssid.equals(currentBssid, ignoreCase = true)) {
            segments += RoamingBssidSegment(segmentStart, index, currentBssid)
            segmentStart = index
            currentBssid = bssid
        }
    }
    segments += RoamingBssidSegment(segmentStart, validSamples.size, currentBssid)
    if (segments.size < 2) return emptyList()

    fun firstRssi(segment: RoamingBssidSegment): Int? {
        for (index in segment.startIndex until segment.endIndexExclusive) {
            validSamples[index].rssi?.let { return it }
        }
        return null
    }

    fun lastRssi(segment: RoamingBssidSegment): Int? {
        for (index in segment.endIndexExclusive - 1 downTo segment.startIndex) {
            validSamples[index].rssi?.let { return it }
        }
        return null
    }

    fun firstRadio(segment: RoamingBssidSegment): RoamingWifiSample {
        for (index in segment.startIndex until segment.endIndexExclusive) {
            val sample = validSamples[index]
            if (sample.frequencyMhz != null || sample.channel != null) return sample
        }
        return validSamples[segment.startIndex]
    }

    fun lastRadio(segment: RoamingBssidSegment): RoamingWifiSample {
        for (index in segment.endIndexExclusive - 1 downTo segment.startIndex) {
            val sample = validSamples[index]
            if (sample.frequencyMhz != null || sample.channel != null) return sample
        }
        return validSamples[segment.endIndexExclusive - 1]
    }

    return segments.zipWithNext().map { (oldSegment, newSegment) ->
        val before = validSamples[oldSegment.endIndexExclusive - 1]
        val after = validSamples[newSegment.startIndex]
        val oldRadio = lastRadio(oldSegment)
        val newRadio = firstRadio(newSegment)
        val observationMs = ((after.elapsedRealtimeNanos - before.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
        val lossWindowEnd = after.elapsedMs + 3_000L
        val trueLosses = pingSamples.count {
            it.attempted && it.latencyMs == null && it.startedElapsedMs in before.elapsedMs..lossWindowEnd
        }
        val recoveryMs = pingSamples.asSequence()
            .filter { it.attempted && it.latencyMs != null && it.completedElapsedMs >= after.elapsedMs }
            .minOfOrNull { (it.completedElapsedMs - after.elapsedMs).coerceAtLeast(0L) }

        RoamingSwitchEvent(
            timeText = after.timeText,
            observedElapsedMs = after.elapsedMs,
            oldBssid = oldSegment.bssid,
            newBssid = newSegment.bssid,
            oldBand = wifiBandOf(oldRadio.frequencyMhz),
            newBand = wifiBandOf(newRadio.frequencyMhz),
            oldChannel = oldRadio.channel,
            newChannel = newRadio.channel,
            oldRssi = lastRssi(oldSegment),
            newRssi = firstRssi(newSegment),
            observationMs = observationMs,
            pingRecoveryMs = recoveryMs,
            roamingLossCount = trueLosses
        )
    }
}

private fun buildStickyApEvents(
    wifiSamples: List<RoamingWifiSample>,
    weakThreshold: Int = ROAMING_STICKY_WEAK_RSSI,
    advantageDb: Int = ROAMING_STICKY_ADVANTAGE_DB,
    durationMs: Long = ROAMING_STICKY_DURATION_MS
): List<StickyApEvent> {
    val samples = wifiSamples.filter { isUsableWifiBssid(it.bssid) }
    if (samples.isEmpty()) return emptyList()
    val events = mutableListOf<StickyApEvent>()
    var start: RoamingWifiSample? = null
    var last: RoamingWifiSample? = null

    fun qualifies(sample: RoamingWifiSample): Boolean {
        val currentBssid = sample.bssid ?: return false
        val currentRssi = sample.rssi ?: return false
        val candidateBssid = sample.candidateBssid?.takeIf(::isUsableWifiBssid) ?: return false
        val candidateRssi = sample.candidateRssi ?: return false
        return !candidateBssid.equals(currentBssid, ignoreCase = true) &&
            currentRssi <= weakThreshold &&
            candidateRssi - currentRssi >= advantageDb
    }

    fun flush() {
        val first = start ?: return
        val end = last ?: first
        val actualDurationMs = (end.elapsedMs - first.elapsedMs).coerceAtLeast(0L)
        if (actualDurationMs >= durationMs) {
            val currentBssid = end.bssid ?: first.bssid ?: return
            val currentRssi = end.rssi ?: first.rssi ?: return
            val candidateBssid = end.candidateBssid ?: first.candidateBssid ?: return
            val candidateRssi = end.candidateRssi ?: first.candidateRssi ?: return
            events += StickyApEvent(
                startTimeText = first.timeText,
                durationMs = actualDurationMs,
                durationText = if (actualDurationMs < 1_000L) "<1s" else String.format(Locale.getDefault(), "%.1fs", actualDurationMs / 1000.0),
                currentBssid = currentBssid,
                currentRssi = currentRssi,
                candidateBssid = candidateBssid,
                candidateRssi = candidateRssi,
                deltaDb = candidateRssi - currentRssi
            )
        }
        start = null
        last = null
    }

    samples.forEach { sample ->
        if (qualifies(sample)) {
            val existingStart = start
            if (existingStart == null) {
                start = sample
                last = sample
            } else if (existingStart.bssid.equals(sample.bssid, ignoreCase = true)) {
                last = sample
            } else {
                flush()
                start = sample
                last = sample
            }
        } else {
            flush()
        }
    }
    flush()
    return events
}

private fun roamingAverage(values: List<Int>): Int? = values.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

private fun roamingDurationText(wifiSamples: List<RoamingWifiSample>, pingSamples: List<RoamingPingSample>): String {
    val sec = (max(
        wifiSamples.lastOrNull()?.elapsedMs ?: 0L,
        pingSamples.maxOfOrNull { it.completedElapsedMs } ?: 0L
    ) / 1000L).toInt()
    return when {
        sec < 60 -> "${sec}s"
        sec % 60 == 0 -> "${sec / 60}分"
        else -> "${sec / 60}分${sec % 60}秒"
    }
}

private fun roamingStatsText(label: String, values: List<Int>, unit: String): String {
    if (values.isEmpty()) return "$label：—"
    return "$label：最小${values.minOrNull()}$unit / 最大${values.maxOrNull()}$unit / 平均${roamingAverage(values)}$unit"
}

private fun roamingRssiStatsText(values: List<Int>): String {
    if (values.isEmpty()) return "RSSI：—"
    return "RSSI：最强${values.maxOrNull()}dBm / 最弱${values.minOrNull()}dBm / 平均${roamingAverage(values)}dBm"
}

private fun roamingPercent(count: Int, total: Int): Int = if (total <= 0) 0 else ((count * 100.0) / total).roundToInt().coerceIn(0, 100)

private fun roamingLossLabel(pingSamples: List<RoamingPingSample>, targetMode: RoamingTargetMode): String {
    val gateway = pingSamples.filter { it.target == RoamingPingTarget.GATEWAY && it.attempted }
    val external = pingSamples.filter { it.target == RoamingPingTarget.EXTERNAL && it.attempted }
    val gw = roamingPercent(gateway.count { it.latencyMs == null }, gateway.size)
    val ext = roamingPercent(external.count { it.latencyMs == null }, external.size)
    return when (targetMode) {
        RoamingTargetMode.GATEWAY_AND_EXTERNAL -> if (gateway.isEmpty() && external.isEmpty()) "—" else "内${gw}%/外${ext}%"
        RoamingTargetMode.GATEWAY -> if (gateway.isEmpty()) "—" else "网关${gw}%"
        RoamingTargetMode.EXTERNAL -> if (external.isEmpty()) "—" else "外网${ext}%"
    }
}

private fun roamingLossPercentText(total: Int, loss: Int): String = if (total <= 0) "—" else "${roamingPercent(loss, total)}%"

private fun roamingLatencyJitterMs(samples: List<RoamingPingSample>): Double? {
    val changes = RoamingPingTarget.values().flatMap { target ->
        samples.filter { it.target == target }.mapNotNull { it.latencyMs }.zipWithNext()
            .map { (a, b) -> abs(a - b).toDouble() }
    }
    return changes.takeIf { it.isNotEmpty() }?.average()
}

private fun roamingRssiJitterDb(samples: List<RoamingWifiSample>): Double? {
    val values = samples.mapNotNull { it.rssi }
    if (values.size < 2) return null
    return values.zipWithNext().map { (a, b) -> abs(a - b).toDouble() }.average()
}

private fun computeRoamingQuality(
    wifiSamples: List<RoamingWifiSample>,
    pingSamples: List<RoamingPingSample>,
    events: List<RoamingSwitchEvent>,
    targetMode: RoamingTargetMode
): RoamingQuality {
    if (wifiSamples.isEmpty() && pingSamples.isEmpty()) return RoamingQuality("待测", 0, Muted, "开始后自动评分")
    val gateway = pingSamples.filter { it.target == RoamingPingTarget.GATEWAY && it.attempted }
    val external = pingSamples.filter { it.target == RoamingPingTarget.EXTERNAL && it.attempted }
    val gatewayLoss = gateway.count { it.latencyMs == null }
    val externalLoss = external.count { it.latencyMs == null }
    val activeLoss = when (targetMode) {
        RoamingTargetMode.GATEWAY_AND_EXTERNAL -> max(
            roamingPercent(gatewayLoss, gateway.size),
            roamingPercent(externalLoss, external.size)
        )
        RoamingTargetMode.GATEWAY -> roamingPercent(gatewayLoss, gateway.size)
        RoamingTargetMode.EXTERNAL -> roamingPercent(externalLoss, external.size)
    }
    val lossPercent = activeLoss.toDouble()
    val latencyJitter = roamingLatencyJitterMs(pingSamples) ?: 0.0
    val rssiJitter = roamingRssiJitterDb(wifiSamples) ?: 0.0
    val switchLoss = events.sumOf { it.roamingLossCount }
    val maxLatency = pingSamples.mapNotNull { it.latencyMs }.maxOrNull() ?: 0
    var score = 100
    score -= when {
        lossPercent >= 20.0 -> 34
        lossPercent >= 8.0 -> 24
        lossPercent >= 3.0 -> 14
        lossPercent > 0.0 -> 7
        else -> 0
    }
    score -= when {
        latencyJitter >= 80.0 -> 18
        latencyJitter >= 40.0 -> 12
        latencyJitter >= 20.0 -> 6
        else -> 0
    }
    score -= when {
        rssiJitter >= 12.0 -> 10
        rssiJitter >= 7.0 -> 6
        else -> 0
    }
    score -= (switchLoss * 4).coerceAtMost(18)
    score -= when {
        maxLatency >= 500 -> 12
        maxLatency >= 200 -> 7
        else -> 0
    }
    score = score.coerceIn(0, 100)
    val label = when {
        score >= 90 -> "优秀"
        score >= 75 -> "正常"
        score >= 60 -> "一般"
        else -> "较差"
    }
    val color = when {
        score >= 90 -> Green
        score >= 75 -> Blue
        score >= 60 -> Orange
        else -> ErrorRed
    }
    val summary = "${score}分 · 丢包${String.format(Locale.getDefault(), "%.1f", lossPercent)}% · Ping抖动${latencyJitter.roundToInt()}ms · RSSI抖动${String.format(Locale.getDefault(), "%.1f", rssiJitter)}dB"
    return RoamingQuality(label, score, color, summary)
}

private fun buildRoamingHistoryRecord(
    targetMode: RoamingTargetMode,
    externalTarget: String,
    wifiSamples: List<RoamingWifiSample>,
    pingSamples: List<RoamingPingSample>,
    events: List<RoamingSwitchEvent>,
    networkEvents: List<RoamingNetworkEvent>
): RoamingHistoryRecord {
    val gateway = pingSamples.filter { it.target == RoamingPingTarget.GATEWAY }.mapNotNull { it.latencyMs }
    val external = pingSamples.filter { it.target == RoamingPingTarget.EXTERNAL }.mapNotNull { it.latencyMs }
    val rssi = wifiSamples.mapNotNull { it.rssi }
    val speed = wifiSamples.mapNotNull { it.linkSpeed }
    val targetText = if (targetMode == RoamingTargetMode.GATEWAY) targetMode.label else "${targetMode.label} · ${externalTarget.ifBlank { "223.5.5.5" }}"
    val eventLines = events.map { e ->
        "AP切换：${e.oldBssid} → ${e.newBssid}\n频段/信道：${roamingRadioText(e.oldBand, e.oldChannel, e.oldRssi)} → ${roamingRadioText(e.newBand, e.newChannel, e.newRssi)}\n结果：切换${e.observationMs}ms · Ping恢复${e.pingRecoveryMs?.let { "${it}ms" } ?: "—"} · 漫游丢包${e.roamingLossCount}"
    }
    val networkLines = networkEvents.map { "${it.timeText}  ${it.label}" }
    val trueLossCount = pingSamples.count { it.attempted && it.latencyMs == null }
    return RoamingHistoryRecord(
        id = System.currentTimeMillis(),
        timeText = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
        targetText = targetText,
        durationText = roamingDurationText(wifiSamples, pingSamples),
        sampleCount = pingSamples.count { it.attempted },
        lossCount = trueLossCount,
        gatewaySummary = roamingStatsText("网关", gateway, "ms"),
        externalSummary = roamingStatsText("外网", external, "ms"),
        rssiSummary = roamingRssiStatsText(rssi),
        speedSummary = roamingStatsText("速率", speed, "Mbps"),
        roamingSummary = "AP切换：${events.size}次 · 漫游窗口真实丢包${events.sumOf { it.roamingLossCount }}",
        eventLines = eventLines,
        networkEventLines = networkLines
    )
}

private fun roamingRadioText(band: String, channel: Int?, rssi: Int?): String =
    "$band ${channel?.let { "CH.$it" } ?: "CH.—"} ${rssi?.let { "${it}dBm" } ?: "—"}"

private fun loadRoamingHistory(context: Context): List<RoamingHistoryRecord> {
    val raw = context.getSharedPreferences("net_tools_history", Context.MODE_PRIVATE).getString("roaming_history_v1", "[]") ?: "[]"
    val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            fun strList(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return buildList { for (j in 0 until a.length()) add(a.optString(j)) }
            }
            add(
                RoamingHistoryRecord(
                    id = o.optLong("id"),
                    timeText = o.optString("timeText"),
                    targetText = o.optString("targetText"),
                    durationText = o.optString("durationText"),
                    sampleCount = o.optInt("sampleCount"),
                    lossCount = o.optInt("lossCount"),
                    gatewaySummary = o.optString("gatewaySummary"),
                    externalSummary = o.optString("externalSummary"),
                    rssiSummary = o.optString("rssiSummary"),
                    speedSummary = o.optString("speedSummary"),
                    roamingSummary = o.optString("roamingSummary"),
                    eventLines = strList("eventLines"),
                    networkEventLines = strList("networkEventLines")
                )
            )
        }
    }
}

private fun saveRoamingHistory(context: Context, record: RoamingHistoryRecord) {
    val next = (listOf(record) + loadRoamingHistory(context).filterNot { it.id == record.id }).take(10)
    writeRoamingHistory(context, next)
}

private fun deleteRoamingHistory(context: Context, id: Long) {
    writeRoamingHistory(context, loadRoamingHistory(context).filterNot { it.id == id })
}

private fun writeRoamingHistory(context: Context, records: List<RoamingHistoryRecord>) {
    val arr = JSONArray()
    records.forEach { r ->
        arr.put(
            JSONObject()
                .put("id", r.id)
                .put("timeText", r.timeText)
                .put("targetText", r.targetText)
                .put("durationText", r.durationText)
                .put("sampleCount", r.sampleCount)
                .put("lossCount", r.lossCount)
                .put("gatewaySummary", r.gatewaySummary)
                .put("externalSummary", r.externalSummary)
                .put("rssiSummary", r.rssiSummary)
                .put("speedSummary", r.speedSummary)
                .put("roamingSummary", r.roamingSummary)
                .put("eventLines", JSONArray(r.eventLines))
                .put("networkEventLines", JSONArray(r.networkEventLines))
        )
    }
    context.getSharedPreferences("net_tools_history", Context.MODE_PRIVATE).edit().putString("roaming_history_v1", arr.toString()).apply()
}

private fun roamingPingAxisMax(values: List<Int>): Int {
    val maxValue = values.filter { it in 0..20_000 }.maxOrNull() ?: return 50
    val padded = (maxValue * 1.12f).roundToInt().coerceAtLeast(20)
    val fineSteps = listOf(20, 30, 40, 50, 60, 80, 100, 120, 150, 180, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000)
    return fineSteps.firstOrNull { it >= padded }
        ?: (ceil(padded / 500.0) * 500).roundToInt().coerceAtLeast(2500)
}

private fun roamingTimeLabel(sec: Int): String {
    return if (sec < 60) "${sec}s" else "${sec / 60}m${if (sec % 60 == 0) "" else "${sec % 60}s"}"
}

private fun roamingXTicks(size: Int): List<Int> {
    if (size <= 1) return listOf(0)
    val maxTicks = 5
    val step = ceil((size - 1) / (maxTicks - 1).toDouble()).roundToInt().coerceAtLeast(1)
    val ticks = (0 until size step step).toMutableList()
    if (ticks.last() != size - 1) ticks += size - 1
    return ticks.distinct().take(6)
}

@Composable
private fun MtuToolPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pauseFlag = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var host by remember { mutableStateOf("www.qq.com") }
    var timeoutMs by remember { mutableStateOf("1200") }
    var runMode by remember { mutableStateOf(MtuRunMode.IPV4) }
    var probeMode by remember { mutableStateOf(MtuProbeMode.FAILOVER) }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var steps by remember { mutableStateOf<List<MtuStep>>(emptyList()) }
    var result by remember { mutableStateOf<MtuResult?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            pauseFlag.set(false)
            job?.cancel()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ToolPageHeader("MTU检测", "准确测试路径 MTU 与建议 MSS", onBack) }
        item {
            SoftCard {
                ConfigLongRow("目标") { CleanField(host, { host = it }, "www.qq.com", leadingMark = "host") }
                ConfigColumn("测试协议") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        MtuRunMode.values().forEach { mode ->
                            MiniSelectPill(mode.label, runMode == mode, Modifier.weight(1f)) {
                                if (!running) runMode = mode
                            }
                        }
                    }
                }
                ConfigColumn("测试模式") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        MtuProbeMode.values().forEach { mode ->
                            MiniSelectPill(mode.label, probeMode == mode, Modifier.weight(1f)) {
                                if (!running) probeMode = mode
                            }
                        }
                    }
                }
                ConfigLongRow("单次等待") {
                    CleanField(
                        timeoutMs,
                        { timeoutMs = it.onlyDigits().take(5) },
                        "1200",
                        keyboardType = KeyboardType.Number,
                        leadingMark = "hourglass"
                    )
                }
                Text(
                    when (probeMode) {
                        MtuProbeMode.FAILOVER -> "优先使用 PMTU 准确探测；若被网络限制，再自动改用 ICMP，最后用 TCP 只确认业务是否连通。"
                        MtuProbeMode.PMTU -> "使用禁止分片/大包语义寻找准确路径 MTU，并计算建议 TCP MSS。"
                        MtuProbeMode.ICMP -> "使用普通 ICMP 大包测试，速度较快，但结果可能受分片或回包策略影响。"
                        MtuProbeMode.TCP -> "只检查 TCP 业务连通，并按本地接口 MTU 估算建议 MSS，不会冒充准确路径 MTU。"
                    } + when (runMode) {
                        MtuRunMode.IPV4 -> " 当前只测 IPv4。"
                        MtuRunMode.IPV6 -> " 当前只测 IPv6。"
                        MtuRunMode.AUTO -> " 当前会依次测试 IPv4 和 IPv6。"
                    },
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Button(
                    onClick = {
                        if (running) {
                            paused = !paused
                            pauseFlag.set(paused)
                        } else {
                            running = true
                            paused = false
                            pauseFlag.set(false)
                            steps = emptyList()
                            result = null
                            job = scope.launch {
                                try {
                                    result = runMtuProbeLive(
                                        context = context.applicationContext,
                                        hostInput = host,
                                        runMode = runMode,
                                        probeMode = probeMode,
                                        timeoutMs = timeoutMs.safeInt(1200, 500, 10000),
                                        pauseRequested = { pauseFlag.get() }
                                    ) { step ->
                                        steps = steps + step
                                    }
                                } catch (_: CancellationException) {
                                    // 页面退出时结束当前检测，不写入错误结果。
                                } finally {
                                    pauseFlag.set(false)
                                    paused = false
                                    running = false
                                    job = null
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        when {
                            running && paused -> "继续测试"
                            running -> "暂停"
                            result != null -> "重新测试"
                            else -> "开始测试"
                        },
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
        item { MtuProcessCard(running = running, paused = paused, steps = steps, result = result) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MtuProcessCard(running: Boolean, paused: Boolean, steps: List<MtuStep>, result: MtuResult?) {
    SoftCompactToolCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("检测过程", color = TextDark, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            val stateText = when {
                paused -> "已暂停"
                running -> "运行中"
                result != null -> "完成"
                else -> "等待"
            }
            Text(stateText, color = if (running) Blue else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (steps.isEmpty()) {
            Text(
                if (paused) "检测已暂停，点击继续测试后从当前进度恢复。" else "开始后会自动寻找路径可稳定通过的最大数据包。",
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        } else {
            Text(
                steps.joinToString("\n") { "${it.mtu} 字节：${if (it.success) "通过" else "未通过"} · ${it.detail}" },
                color = TextDark,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 18,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
        result?.let { r ->
            HorizontalDivider(color = Border)
            ToolMonoLine("目标", r.target)
            ToolMonoLine("地址", r.address)
            ToolMonoLine("协议", r.protocol)
            ToolMonoLine("方式", r.method)
            if (r.checkLines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.checkLines.forEach { line ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (line.ok) BlueSoft else RedSoft,
                            border = BorderStroke(0.6.dp, if (line.ok) Color(0xFFD9E8FF) else Color(0xFFFECACA))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(line.name, color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(line.value, color = if (line.ok) Blue else ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Text(line.detail, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
            if (r.error.isNotBlank()) Text("提示：${r.error}", color = ErrorRed, fontSize = 11.sp)
            Text(r.analysis, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun RoamingToolPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetMode by remember { mutableStateOf(RoamingTargetMode.GATEWAY_AND_EXTERNAL) }
    var externalTarget by remember { mutableStateOf("223.5.5.5") }
    var pingIntervalMs by remember { mutableStateOf("500") }
    var pingTimeoutMs by remember { mutableStateOf("1000") }
    var customPingTimeout by remember { mutableStateOf(false) }
    var networkLostAtMs by remember { mutableStateOf<Long?>(null) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val wifiSamples = remember { mutableStateListOf<RoamingWifiSample>() }
    val pingSamples = remember { mutableStateListOf<RoamingPingSample>() }
    var networkEvents by remember { mutableStateOf<List<RoamingNetworkEvent>>(emptyList()) }
    var runStartMs by remember { mutableStateOf<Long?>(null) }
    var savedRunId by remember { mutableStateOf<Long?>(null) }
    var history by remember { mutableStateOf(loadRoamingHistory(context.applicationContext)) }
    var showHistory by remember { mutableStateOf(false) }
    var expandedHistoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var lastCapabilitySignature by remember { mutableStateOf("") }
    var lastLinkSignature by remember { mutableStateOf("") }
    var permissionRefresh by remember { mutableStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRefresh += 1
    }

    fun appendNetworkEvent(label: String) {
        val now = SystemClock.elapsedRealtime()
        val elapsed = runStartMs?.let { ((now - it) / 1000L).toInt().coerceAtLeast(0) } ?: 0
        // 事件去重：ConnectivityManager 可能连续回调完全相同的能力/链路内容，避免事件区刷屏。
        val last = networkEvents.lastOrNull()
        if (last?.label == label) return
        networkEvents = (networkEvents + RoamingNetworkEvent(
            timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            elapsedSec = elapsed,
            label = label
        )).takeLast(40)
    }

    fun saveCurrentRunIfNeeded() {
        val runId = runStartMs ?: return
        if (savedRunId == runId || (wifiSamples.isEmpty() && pingSamples.isEmpty())) return
        val events = buildRoamingSwitchEvents(wifiSamples, pingSamples)
        val record = buildRoamingHistoryRecord(targetMode, externalTarget, wifiSamples, pingSamples, events, networkEvents)
        saveRoamingHistory(context.applicationContext, record)
        history = loadRoamingHistory(context.applicationContext)
        savedRunId = runId
    }

    fun stop(reason: String = "手动停止") {
        job?.cancel()
        job = null
        if (running && reason != "手动停止") appendNetworkEvent("网络事件：$reason")
        if (running) saveCurrentRunIfNeeded()
        running = false
    }

    DisposableEffect(running, runStartMs) {
        if (!running) {
            onDispose { }
        } else {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        val lostAt = networkLostAtMs
                        networkLostAtMs = null
                        if (lostAt != null) {
                            val lostSec = ((SystemClock.elapsedRealtime() - lostAt) / 1000.0)
                            appendNetworkEvent("网络事件：短暂断开 ${String.format(Locale.getDefault(), "%.1f", lostSec)}s，已恢复，继续测试")
                        } else {
                            appendNetworkEvent("网络事件：默认网络可用")
                        }
                    }
                }

                override fun onLost(network: Network) {
                    scope.launch {
                        val lostAt = SystemClock.elapsedRealtime()
                        networkLostAtMs = lostAt
                        appendNetworkEvent("网络事件：网络丢失/可能切换，等待 ${ROAMING_NETWORK_LOST_GRACE_MS / 1000}s 恢复")
                        delay(ROAMING_NETWORK_LOST_GRACE_MS)
                        if (running && networkLostAtMs == lostAt) {
                            networkLostAtMs = null
                            stop("网络断开超过${ROAMING_NETWORK_LOST_GRACE_MS / 1000}s，已中断并保存")
                        }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val transport = when {
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝"
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
                        else -> "未知网络"
                    }
                    val internet = if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) "有Internet" else "无Internet"
                    val validated = if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "已验证" else "未验证"
                    val metered = if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "非计费" else "可能计费"
                    val vpn = if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) " · VPN" else ""
                    val signature = "$transport|$internet|$validated|$metered|$vpn"
                    if (signature != lastCapabilitySignature) {
                        lastCapabilitySignature = signature
                        scope.launch { appendNetworkEvent("能力变化：$transport · $internet · $validated · $metered$vpn") }
                    }
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    val dnsCount = linkProperties.dnsServers.size
                    val iface = linkProperties.interfaceName ?: "未知接口"
                    val mtu = linkProperties.mtu.takeIf { it > 0 }
                    val hasIpv6 = linkProperties.linkAddresses.any { it.address is Inet6Address }
                    val mtuText = mtu?.let { " · MTU $it" }.orEmpty()
                    val signature = "$iface|$dnsCount|${mtu ?: "无"}|$hasIpv6"
                    if (signature != lastLinkSignature) {
                        lastLinkSignature = signature
                        scope.launch { appendNetworkEvent("链路变化：$iface · DNS ${dnsCount}个$mtuText · IPv6 ${if (hasIpv6) "有" else "无"}") }
                    }
                }
            }
            runCatching { cm?.registerDefaultNetworkCallback(callback) }
            onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
        }
    }

    DisposableEffect(running) {
        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && running) {
                    stop("APP进入后台/锁屏，漫游测试已中断并保存")
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    if (showHistory) {
        RoamingHistoryDialog(
            records = history,
            expandedIds = expandedHistoryIds,
            onDismiss = { showHistory = false },
            onToggle = { id -> expandedHistoryIds = if (id in expandedHistoryIds) expandedHistoryIds - id else expandedHistoryIds + id },
            onDelete = { id ->
                deleteRoamingHistory(context.applicationContext, id)
                history = loadRoamingHistory(context.applicationContext)
                expandedHistoryIds = expandedHistoryIds - id
            }
        )
    }

    val wifiAccessStatus = remember(permissionRefresh, running) {
        when {
            !hasRoamingWifiPermissions(context.applicationContext) -> "权限受限：需要精确位置和附近 Wi-Fi 设备权限"
            !isSystemLocationEnabled(context.applicationContext) -> "定位未开启：系统可能隐藏 BSSID"
            else -> "Wi-Fi 信息权限已就绪"
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ToolPageHeader("漫游测试", "记录 RSSI、延迟、丢包、协商速率和 BSSID切换观测时间", onBack) }
        item {
            SoftCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ConfigColumn("目标", Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                            RoamingTargetMode.values().forEach { m -> MiniSelectPill(m.label, targetMode == m, Modifier.weight(1f)) { targetMode = m } }
                        }
                    }
                }
                ConfigLongRow("外网") { CleanField(externalTarget, { externalTarget = it }, "223.5.5.5", leadingMark = "host") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ConfigColumn("Ping间隔", Modifier.weight(1f)) {
                        RoamingIntervalField(pingIntervalMs) { value ->
                            pingIntervalMs = value.onlyDigits().take(5)
                            if (!customPingTimeout) pingTimeoutMs = recommendedRoamingTimeoutMs(pingIntervalMs.safeInt(500, 100, 10_000)).toString()
                        }
                    }
                    ConfigColumn(if (customPingTimeout) "Ping超时·自定义" else "Ping超时·自动", Modifier.weight(1f)) {
                        RoamingTimeoutField(
                            value = pingTimeoutMs,
                            custom = customPingTimeout,
                            onValueChange = { value -> pingTimeoutMs = value.onlyDigits().take(5); customPingTimeout = true },
                            onAuto = {
                                customPingTimeout = false
                                pingTimeoutMs = recommendedRoamingTimeoutMs(pingIntervalMs.safeInt(500, 100, 10_000)).toString()
                            }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ping间隔 100–10000ms · 内外网共用 · 自动超时=max(800ms, 间隔×2)，上限5000ms", color = Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                    if (customPingTimeout) {
                        TextButton(onClick = {
                            customPingTimeout = false
                            pingTimeoutMs = recommendedRoamingTimeoutMs(pingIntervalMs.safeInt(500, 100, 10_000)).toString()
                        }) { Text("恢复自动", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val perms = buildList {
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        }.toTypedArray()
                        permissionLauncher.launch(perms)
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = ShapeM
                ) { Text("授权 WiFi / 定位信息（用于 BSSID、RSSI）", fontSize = 12.sp) }
                Text(wifiAccessStatus, color = if (wifiAccessStatus.startsWith("Wi-Fi")) Green else Orange, fontSize = 11.sp, lineHeight = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            history = loadRoamingHistory(context.applicationContext)
                            showHistory = true
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("漫游历史", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            if (running) {
                                stop()
                            } else {
                                if (!hasRoamingWifiPermissions(context.applicationContext)) {
                                    Toast.makeText(context, "需要 Wi-Fi / 精确位置权限才能读取 BSSID，正在请求授权", Toast.LENGTH_LONG).show()
                                    permissionLauncher.launch(buildList {
                                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                    }.toTypedArray())
                                    return@Button
                                }
                                if (!isSystemLocationEnabled(context.applicationContext)) {
                                    Toast.makeText(context, "系统定位未开启，BSSID 会被隐藏；请先开启定位", Toast.LENGTH_LONG).show()
                                    runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                                    return@Button
                                }
                                if (!isCurrentNetworkWifi(context.applicationContext)) {
                                    Toast.makeText(context, "请连接WIFI", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                wifiSamples.clear()
                                pingSamples.clear()
                                networkEvents = emptyList()
                                val startNanos = SystemClock.elapsedRealtimeNanos()
                                val startElapsedMs = SystemClock.elapsedRealtime()
                                runStartMs = startElapsedMs
                                savedRunId = null
                                running = true
                                appendNetworkEvent("开始监听网络事件")
                                val interval = pingIntervalMs.safeInt(500, 100, 10_000)
                                val timeout = if (customPingTimeout) pingTimeoutMs.safeInt(1000, 100, 10_000) else recommendedRoamingTimeoutMs(interval)
                                pingIntervalMs = interval.toString()
                                pingTimeoutMs = timeout.toString()
                                appendNetworkEvent("Wi-Fi 当前连接信息 50ms 观测 · 不主动扫描")
                                appendNetworkEvent("Ping配置：内外网共用 · 间隔 ${interval}ms · 超时 ${timeout}ms")
                                val activeMode = targetMode
                                val activeExternalTarget = externalTarget.trim().ifBlank { "223.5.5.5" }
                                job = scope.launch {
                                    launch {
                                        while (currentCoroutineContext().isActive) {
                                            val tickAt = SystemClock.elapsedRealtime()
                                            wifiSamples.add(readRoamingWifiSample(context.applicationContext, startNanos))
                                            if (wifiSamples.size > 72_000) wifiSamples.removeAt(0)
                                            val spent = SystemClock.elapsedRealtime() - tickAt
                                            delay((ROAMING_WIFI_SAMPLE_INTERVAL_MS - spent).coerceAtLeast(1L))
                                        }
                                    }
                                    if (activeMode != RoamingTargetMode.EXTERNAL) launch {
                                        while (currentCoroutineContext().isActive) {
                                            val tickClock = SystemClock.elapsedRealtime()
                                            val tickAt = (tickClock - startElapsedMs).coerceAtLeast(0L)
                                            val gateway = if (isWifiNetworkReady(context.applicationContext, requireValidated = false)) readGatewayAddress(context.applicationContext) else null
                                            if (!gateway.isNullOrBlank()) {
                                                val result = pingForRoaming(gateway, timeout)
                                                pingSamples.add(RoamingPingSample(
                                                    timeText = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
                                                    target = RoamingPingTarget.GATEWAY,
                                                    startedElapsedMs = tickAt,
                                                    completedElapsedMs = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(tickAt),
                                                    attempted = true,
                                                    latencyMs = result.latencyMs,
                                                    failureReason = result.failure
                                                ))
                                                if (pingSamples.size > 10_000) pingSamples.removeAt(0)
                                            }
                                            delay((interval - (SystemClock.elapsedRealtime() - tickClock)).coerceAtLeast(1L))
                                        }
                                    }
                                    if (activeMode != RoamingTargetMode.GATEWAY) launch {
                                        while (currentCoroutineContext().isActive) {
                                            val tickClock = SystemClock.elapsedRealtime()
                                            val tickAt = (tickClock - startElapsedMs).coerceAtLeast(0L)
                                            if (isWifiNetworkReady(context.applicationContext, requireValidated = true)) {
                                                val result = pingForRoaming(activeExternalTarget, timeout)
                                                pingSamples.add(RoamingPingSample(
                                                    timeText = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
                                                    target = RoamingPingTarget.EXTERNAL,
                                                    startedElapsedMs = tickAt,
                                                    completedElapsedMs = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(tickAt),
                                                    attempted = true,
                                                    latencyMs = result.latencyMs,
                                                    failureReason = result.failure
                                                ))
                                                if (pingSamples.size > 10_000) pingSamples.removeAt(0)
                                            }
                                            delay((interval - (SystemClock.elapsedRealtime() - tickClock)).coerceAtLeast(1L))
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1.45f).height(46.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text(if (running) "停止测试" else "开始测试", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
        item {
            RoamingLiveCard(
                wifiSamples = wifiSamples,
                pingSamples = pingSamples,
                running = running,
                networkEvents = networkEvents,
                targetMode = targetMode,
                onHistoryClick = { history = loadRoamingHistory(context.applicationContext); showHistory = true }
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RoamingLiveCard(
    wifiSamples: List<RoamingWifiSample>,
    pingSamples: List<RoamingPingSample>,
    running: Boolean,
    networkEvents: List<RoamingNetworkEvent>,
    targetMode: RoamingTargetMode,
    onHistoryClick: () -> Unit
) {
    val latestWifi = wifiSamples.lastOrNull()
    val gatewaySamples = pingSamples.filter { it.target == RoamingPingTarget.GATEWAY && it.attempted }
    val externalSamples = pingSamples.filter { it.target == RoamingPingTarget.EXTERNAL && it.attempted }
    val gatewayLoss = gatewaySamples.count { it.latencyMs == null }
    val externalLoss = externalSamples.count { it.latencyMs == null }
    val latestGateway = gatewaySamples.lastOrNull()
    val latestExternal = externalSamples.lastOrNull()
    val events = remember(wifiSamples.size, pingSamples.size) { buildRoamingSwitchEvents(wifiSamples, pingSamples) }
    val stickyEvents = remember(wifiSamples.size / 10, running) { buildStickyApEvents(wifiSamples.toList()) }
    SoftCompactToolCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("实时结果", color = TextDark, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onHistoryClick) { Text("漫游历史", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            Text(if (running) "运行中" else "停止", color = if (running) Blue else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        val quality = computeRoamingQuality(wifiSamples, pingSamples, events, targetMode)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            MiniMetric("RSSI", latestWifi?.rssi?.let { "$it dBm" } ?: "—", Blue, Modifier.weight(1f))
            MiniMetric("速率", latestWifi?.linkSpeed?.let { "$it Mbps" } ?: "—", Green, Modifier.weight(1f))
            MiniMetric("AP切换", "${events.size}次", if (events.isEmpty()) Muted else Orange, Modifier.weight(1f))
            MiniMetric("质量", quality.label, quality.color, Modifier.weight(1f))
        }
        ToolMonoLine("BSSID", latestWifi?.bssid ?: latestWifi?.unavailableReason ?: "—")
        RoamingBandChannelLine(latestWifi?.let { "${wifiBandOf(it.frequencyMhz)} · ${it.channel?.let { channel -> "CH.$channel" } ?: "CH.—"} · ${it.frequencyMhz?.let { mhz -> "${mhz}MHz" } ?: "—"}" } ?: "—")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            MiniMetric("网关", latestGateway?.latencyMs?.let { "${it}ms" } ?: if (latestGateway?.attempted == true) "丢包" else "—", Blue, Modifier.weight(1f))
            MiniMetric("内网丢包", roamingLossPercentText(gatewaySamples.size, gatewayLoss), if (gatewayLoss > 0) ErrorRed else Muted, Modifier.weight(1f))
            MiniMetric("外网", latestExternal?.latencyMs?.let { "${it}ms" } ?: if (latestExternal?.attempted == true) "丢包" else "—", Purple, Modifier.weight(1f))
            MiniMetric("外网丢包", roamingLossPercentText(externalSamples.size, externalLoss), if (externalLoss > 0) ErrorRed else Muted, Modifier.weight(1f))
        }
        Text(quality.summary, color = quality.color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (networkEvents.isNotEmpty()) {
            Text("网络事件 / 能力变化 / 链路变化", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            networkEvents.takeLast(3).forEach { e ->
                Text("${e.timeText} · ${e.label}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val pingDurationMs = pingSamples.maxOfOrNull { it.startedElapsedMs } ?: 0L
        val signalDurationMs = wifiSamples.lastOrNull()?.elapsedMs ?: 0L
        RoamingPingChartCard(pingSamples, pingDurationMs, running)
        RoamingSignalChartCard(wifiSamples, events, signalDurationMs, running)
        if (events.isNotEmpty()) {
            Text("AP切换详情", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            events.takeLast(4).forEach { RoamingEventMiniCard(it) }
        }
        if (stickyEvents.isNotEmpty()) {
            Text("疑似粘连 AP", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            stickyEvents.takeLast(3).forEach { StickyApMiniCard(it) }
        }
        if (!running && (wifiSamples.isNotEmpty() || pingSamples.isNotEmpty())) {
            RoamingSummaryCard(wifiSamples, pingSamples, events, stickyEvents, targetMode)
        }
        Text("说明：BSSID切换仅由当前 Wi-Fi 连接信息观测；Ping 只统计真实发起的业务探测，未执行的探测不计丢包。", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun RoamingHistoryDialog(
    records: List<RoamingHistoryRecord>,
    expandedIds: Set<Long>,
    onDismiss: () -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("漫游历史", fontWeight = FontWeight.ExtraBold) },
        text = {
            if (records.isEmpty()) {
                Text("暂无漫游测试历史。停止一次测试后会自动保存，最多保留10条。", color = Muted, fontSize = 12.sp)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        RoamingHistoryItem(
                            record = record,
                            expanded = record.id in expandedIds,
                            onToggle = { onToggle(record.id) },
                            onDelete = { onDelete(record.id) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun RoamingHistoryItem(
    record: RoamingHistoryRecord,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeM,
        color = Color.White,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Border.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.timeText, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${record.durationText} · ${record.targetText} · 漫游${record.eventLines.size}次", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = ErrorRed) }
                TextButton(onClick = onToggle) { Text(if (expanded) "收起" else "展开", fontSize = 11.sp) }
            }
            if (expanded) {
                Text(record.gatewaySummary, color = TextDark, fontSize = 11.sp)
                Text(record.externalSummary, color = TextDark, fontSize = 11.sp)
                Text(record.rssiSummary, color = TextDark, fontSize = 11.sp)
                Text(record.speedSummary, color = TextDark, fontSize = 11.sp)
                Text(record.roamingSummary, color = if (record.eventLines.isEmpty()) Muted else Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (record.eventLines.isNotEmpty()) {
                    Text("切换详情", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    record.eventLines.take(8).forEach { Text(it, color = Muted, fontSize = 10.sp, lineHeight = 14.sp) }
                }
                if (record.networkEventLines.isNotEmpty()) {
                    Text("网络事件 / 能力变化 / 链路变化", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    record.networkEventLines.takeLast(6).forEach { Text(it, color = Muted, fontSize = 10.sp, lineHeight = 14.sp) }
                }
            }
        }
    }
}

@Composable
private fun RoamingSwitchPopup(event: RoamingSwitchEvent) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF7ED),
        border = BorderStroke(1.dp, Orange.copy(alpha = 0.28f)),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AP切换", color = Orange, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(event.timeText, color = Muted, fontSize = 10.sp)
            }
            Text("AP切换：${event.oldBssid} → ${event.newBssid}", color = TextDark, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("频段/信道：${roamingRadioText(event.oldBand, event.oldChannel, event.oldRssi)} → ${roamingRadioText(event.newBand, event.newChannel, event.newRssi)}", color = Muted, fontSize = 11.sp)
            Text("结果：切换${event.observationMs}ms · Ping恢复${event.pingRecoveryMs?.let { "${it}ms" } ?: "—"} · 漫游丢包${event.roamingLossCount}", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RoamingPingChartCard(samples: List<RoamingPingSample>, durationMs: Long, running: Boolean) {
    var selected by remember { mutableStateOf<RoamingPingSample?>(null) }
    val viewEndMs = durationMs.coerceAtLeast(1L)
    val viewStartMs = if (running) (viewEndMs - ROAMING_LIVE_WINDOW_MS).coerceAtLeast(0L) else 0L
    val plot = samples.filter { it.startedElapsedMs in viewStartMs..viewEndMs }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), ShapeM)
            .border(1.dp, Border.copy(alpha = 0.55f), ShapeM)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ping表", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("折线=Ping · 红条=丢包", color = Muted, fontSize = 11.sp)
        }
        RoamingPingCanvas(plot = plot, viewStartMs = viewStartMs, viewEndMs = viewEndMs, onSelect = { selected = it })
        selected?.let { RoamingPingSampleDetailLine(it) }
    }
}

private fun downsampleRoamingSignalForDisplay(
    samples: List<RoamingWifiSample>,
    bucketMs: Long = 500L
): List<RoamingWifiSample> {
    if (samples.size <= 2) return samples
    val keep = mutableSetOf(0, samples.lastIndex)
    samples.withIndex()
        .groupBy { it.value.elapsedMs / bucketMs }
        .values
        .forEach { bucket -> keep += bucket.last().index }

    var lastValidIndex: Int? = null
    samples.forEachIndexed { index, sample ->
        val bssid = sample.bssid?.takeIf(::isUsableWifiBssid) ?: return@forEachIndexed
        lastValidIndex?.let { previousIndex ->
            val previousBssid = samples[previousIndex].bssid
            if (isUsableWifiBssid(previousBssid) && previousBssid?.equals(bssid, ignoreCase = true) == false) {
                keep += previousIndex
                keep += index
            }
        }
        lastValidIndex = index
    }
    return keep.sorted().map(samples::get)
}

@Composable
private fun RoamingSignalChartCard(samples: List<RoamingWifiSample>, events: List<RoamingSwitchEvent>, durationMs: Long, running: Boolean) {
    var selected by remember { mutableStateOf<RoamingWifiSample?>(null) }
    val viewEndMs = durationMs.coerceAtLeast(1L)
    val viewStartMs = if (running) (viewEndMs - ROAMING_LIVE_WINDOW_MS).coerceAtLeast(0L) else 0L
    val rawPlot = samples.filter { it.elapsedMs in viewStartMs..viewEndMs }
    val plot = downsampleRoamingSignalForDisplay(rawPlot)
    val selectedSwitch = selected?.let { sample -> events.minByOrNull { abs(it.observedElapsedMs - sample.elapsedMs) }?.takeIf { abs(it.observedElapsedMs - sample.elapsedMs) <= 500L } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), ShapeM)
            .border(1.dp, Border.copy(alpha = 0.55f), ShapeM)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("信号表", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("实线=同AP · 虚线=AP切换", color = Muted, fontSize = 11.sp)
        }
        RoamingSignalCanvas(plot = plot, viewStartMs = viewStartMs, viewEndMs = viewEndMs, onSelect = { selected = it })
        selectedSwitch?.let { RoamingSwitchPopup(it) }
        selected?.let { RoamingWifiSampleDetailLine(it) }
    }
}

@Composable
private fun RoamingPingCanvas(
    plot: List<RoamingPingSample>,
    viewStartMs: Long,
    viewEndMs: Long,
    onSelect: (RoamingPingSample) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(184.dp)
            .clip(ShapeM)
            .background(Color.White, ShapeM)
            .pointerInput(plot, viewStartMs, viewEndMs) {
                detectTapGestures { pos ->
                    if (plot.isEmpty()) return@detectTapGestures
                    val left = 64f
                    val right = size.width - 24f
                    val ratio = ((pos.x - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val selectedMs = viewStartMs + ((viewEndMs - viewStartMs) * ratio).toLong()
                    plot.minByOrNull { abs(it.startedElapsedMs - selectedMs) }?.let(onSelect)
                }
            }
    ) {
        val left = 64f
        val top = 28f
        val right = size.width - 24f
        val bottom = size.height - 48f
        val w = (right - left).coerceAtLeast(1f)
        val h = (bottom - top).coerceAtLeast(1f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(107, 114, 128); textSize = 27f }
        val values = plot.mapNotNull { it.latencyMs }
        val axisMax = roamingPingAxisMax(values)
        val yTicks = listOf(0, axisMax / 4, axisMax / 2, axisMax * 3 / 4, axisMax).distinct()
        yTicks.forEach { tick ->
            val yy = bottom - (tick / axisMax.toFloat()) * h
            drawLine(Border.copy(alpha = 0.72f), Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
            textPaint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(tick.toString(), left - 10f, yy + 9f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
        val spanMs = (viewEndMs - viewStartMs).coerceAtLeast(1L)
        fun x(elapsedMs: Long) = left + ((elapsedMs - viewStartMs).toFloat() / spanMs.toFloat()).coerceIn(0f, 1f) * w
        (0..4).forEach { idx ->
            val tickMs = viewStartMs + spanMs * idx / 4L
            val xx = x(tickMs)
            drawLine(Border.copy(alpha = 0.45f), Offset(xx, top), Offset(xx, bottom), strokeWidth = 1f)
            val label = roamingTimeLabel((tickMs / 1000L).toInt())
            val labelWidth = textPaint.measureText(label)
            val tx = (xx - labelWidth / 2f).coerceIn(left, right - labelWidth)
            drawContext.canvas.nativeCanvas.drawText(label, tx, size.height - 18f, textPaint)
        }
        fun y(v: Int) = bottom - (v.coerceIn(0, axisMax) / axisMax.toFloat()) * h
        fun drawLatencyLine(target: RoamingPingTarget, color: Color) {
            val path = Path()
            var started = false
            plot.filter { it.target == target }.forEach { sample ->
                val latency = sample.latencyMs ?: return@forEach
                if (!started) { path.moveTo(x(sample.startedElapsedMs), y(latency)); started = true }
                else path.lineTo(x(sample.startedElapsedMs), y(latency))
            }
            drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
        drawLatencyLine(RoamingPingTarget.GATEWAY, Blue)
        drawLatencyLine(RoamingPingTarget.EXTERNAL, Purple)
        plot.filter { it.attempted && it.latencyMs == null }.forEach { sample ->
            val xx = x(sample.startedElapsedMs)
            drawLine(ErrorRed, Offset(xx, bottom - 13f), Offset(xx, bottom), strokeWidth = 4f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun RoamingSignalCanvas(
    plot: List<RoamingWifiSample>,
    viewStartMs: Long,
    viewEndMs: Long,
    onSelect: (RoamingWifiSample) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(184.dp)
            .clip(ShapeM)
            .background(Color.White, ShapeM)
            .pointerInput(plot, viewStartMs, viewEndMs) {
                detectTapGestures { pos ->
                    if (plot.isEmpty()) return@detectTapGestures
                    val left = 64f
                    val right = size.width - 24f
                    val ratio = ((pos.x - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val selectedMs = viewStartMs + ((viewEndMs - viewStartMs) * ratio).toLong()
                    plot.minByOrNull { abs(it.elapsedMs - selectedMs) }?.let(onSelect)
                }
            }
    ) {
        if (plot.isEmpty()) return@Canvas
        val left = 64f
        val top = 28f
        val right = size.width - 24f
        val bottom = size.height - 48f
        val w = (right - left).coerceAtLeast(1f)
        val h = (bottom - top).coerceAtLeast(1f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(107, 114, 128); textSize = 27f }
        val rssiValues = plot.mapNotNull { it.rssi }.filter { it in -120..0 }
        val minRssi = rssiValues.minOrNull() ?: -70
        val maxRssi = rssiValues.maxOrNull() ?: -50
        val centerRssi = (minRssi + maxRssi) / 2f
        val spanRssi = (maxRssi - minRssi).coerceAtLeast(1)
        val halfRange = max(8f, spanRssi * 0.8f)
        var axisTop = (centerRssi + halfRange).coerceAtMost(-10f)
        var axisBottom = (centerRssi - halfRange).coerceAtLeast(-95f)
        if (axisTop - axisBottom < 16f) {
            val mid = (axisTop + axisBottom) / 2f
            axisTop = (mid + 8f).coerceAtMost(-10f)
            axisBottom = (mid - 8f).coerceAtLeast(-95f)
        }
        if (axisTop <= axisBottom) { axisTop = -30f; axisBottom = -90f }
        fun yRssi(v: Int?): Float {
            val value = (v ?: axisBottom.toInt()).coerceIn(axisBottom.roundToInt(), axisTop.roundToInt())
            return bottom - ((value - axisBottom) / (axisTop - axisBottom).coerceAtLeast(1f)) * h
        }
        val yTicks = (0..4).map { idx -> (axisBottom + (axisTop - axisBottom) * idx / 4f).roundToInt() }.distinct()
        yTicks.forEach { tick ->
            val yy = yRssi(tick)
            drawLine(Border.copy(alpha = 0.72f), Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
            textPaint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(tick.toString(), left - 10f, yy + 9f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
        val spanMs = (viewEndMs - viewStartMs).coerceAtLeast(1L)
        fun x(elapsedMs: Long) = left + ((elapsedMs - viewStartMs).toFloat() / spanMs.toFloat()).coerceIn(0f, 1f) * w
        (0..4).forEach { idx ->
            val tickMs = viewStartMs + spanMs * idx / 4L
            val xx = x(tickMs)
            drawLine(Border.copy(alpha = 0.45f), Offset(xx, top), Offset(xx, bottom), strokeWidth = 1f)
            val label = roamingTimeLabel((tickMs / 1000L).toInt())
            val labelWidth = textPaint.measureText(label)
            val tx = (xx - labelWidth / 2f).coerceIn(left, right - labelWidth)
            drawContext.canvas.nativeCanvas.drawText(label, tx, size.height - 18f, textPaint)
        }
        val signalPoints = plot.filter { it.rssi != null && isUsableWifiBssid(it.bssid) }
        signalPoints.zipWithNext().forEach { (before, after) ->
            val beforeRssi = before.rssi ?: return@forEach
            val afterRssi = after.rssi ?: return@forEach
            val beforeBssid = before.bssid ?: return@forEach
            val afterBssid = after.bssid ?: return@forEach
            val segment = Path().apply {
                moveTo(x(before.elapsedMs), yRssi(beforeRssi))
                lineTo(x(after.elapsedMs), yRssi(afterRssi))
            }
            val switched = !beforeBssid.equals(afterBssid, ignoreCase = true)
            drawPath(
                segment,
                color = if (switched) Orange else Green,
                style = Stroke(
                    width = 3f,
                    cap = StrokeCap.Round,
                    pathEffect = if (switched) PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f) else null
                )
            )
        }
    }
}

@Composable
private fun RoamingPingSampleDetailLine(sample: RoamingPingSample) {
    Text(
        "${sample.timeText} · ${sample.target.label} ${sample.latencyMs?.let { "${it}ms" } ?: sample.failureReason ?: "丢包"} · ${(sample.startedElapsedMs / 1000.0).let { String.format(Locale.getDefault(), "%.2fs", it) }}",
        color = TextDark,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RoamingWifiSampleDetailLine(sample: RoamingWifiSample) {
    Text(
        "${sample.timeText} · ${sample.bssid ?: sample.unavailableReason ?: "BSSID不可用"} · ${wifiBandOf(sample.frequencyMhz)} ${sample.channel?.let { "CH.$it" } ?: "CH.—"} · RSSI ${sample.rssi?.let { "${it}dBm" } ?: "—"} · 速率 ${sample.linkSpeed?.let { "${it}Mbps" } ?: "—"}",
        color = TextDark,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RoamingEventMiniCard(event: RoamingSwitchEvent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeM)
            .background(Color(0xFFFFF7ED), ShapeM)
            .border(1.dp, Orange.copy(alpha = 0.24f), ShapeM)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AP切换", color = Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(event.timeText, color = Muted, fontSize = 10.sp)
        }
        Text("AP切换：${event.oldBssid} → ${event.newBssid}", color = TextDark, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("频段/信道：${roamingRadioText(event.oldBand, event.oldChannel, event.oldRssi)} → ${roamingRadioText(event.newBand, event.newChannel, event.newRssi)}", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        Text("结果：切换${event.observationMs}ms · Ping恢复${event.pingRecoveryMs?.let { "${it}ms" } ?: "—"} · 漫游丢包${event.roamingLossCount}", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun StickyApMiniCard(event: StickyApEvent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeM)
            .background(Color(0xFFFFF7ED), ShapeM)
            .border(1.dp, Orange.copy(alpha = 0.24f), ShapeM)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("疑似粘连", color = Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(event.startTimeText, color = Muted, fontSize = 10.sp)
        }
        Text("当前 ${event.currentBssid}  ${event.currentRssi}dBm", color = TextDark, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("候选 ${event.candidateBssid}  ${event.candidateRssi}dBm · 优势 ${event.deltaDb}dB · 持续 ${event.durationText}", color = Muted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoamingSummaryCard(
    wifiSamples: List<RoamingWifiSample>,
    pingSamples: List<RoamingPingSample>,
    events: List<RoamingSwitchEvent>,
    stickyEvents: List<StickyApEvent>,
    targetMode: RoamingTargetMode
) {
    val gatewaySamples = pingSamples.filter { it.target == RoamingPingTarget.GATEWAY && it.attempted }
    val externalSamples = pingSamples.filter { it.target == RoamingPingTarget.EXTERNAL && it.attempted }
    val gateway = gatewaySamples.mapNotNull { it.latencyMs }
    val external = externalSamples.mapNotNull { it.latencyMs }
    val rssi = wifiSamples.mapNotNull { it.rssi }
    val speed = wifiSamples.mapNotNull { it.linkSpeed }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeM)
            .background(BlueSoft.copy(alpha = 0.35f), ShapeM)
            .border(1.dp, Blue.copy(alpha = 0.16f), ShapeM)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("测试总结", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("网关延迟：最小${gateway.minOrNull()?.let { "${it}ms" } ?: "—"} / 最大${gateway.maxOrNull()?.let { "${it}ms" } ?: "—"} / 平均${roamingAverage(gateway)?.let { "${it}ms" } ?: "—"}", color = TextDark, fontSize = 11.sp)
        Text("外网延迟：最小${external.minOrNull()?.let { "${it}ms" } ?: "—"} / 最大${external.maxOrNull()?.let { "${it}ms" } ?: "—"} / 平均${roamingAverage(external)?.let { "${it}ms" } ?: "—"}", color = TextDark, fontSize = 11.sp)
        Text("信号RSSI：最强${rssi.maxOrNull()?.let { "${it}dBm" } ?: "—"} / 最弱${rssi.minOrNull()?.let { "${it}dBm" } ?: "—"} / 平均${roamingAverage(rssi)?.let { "${it}dBm" } ?: "—"}", color = TextDark, fontSize = 11.sp)
        Text("协商速率：最高${speed.maxOrNull()?.let { "${it}Mbps" } ?: "—"} / 最低${speed.minOrNull()?.let { "${it}Mbps" } ?: "—"} / 平均${roamingAverage(speed)?.let { "${it}Mbps" } ?: "—"}", color = TextDark, fontSize = 11.sp)
        val gwLoss = gatewaySamples.count { it.latencyMs == null }
        val extLoss = externalSamples.count { it.latencyMs == null }
        val quality = computeRoamingQuality(wifiSamples, pingSamples, events, targetMode)
        Text("漫游质量：${quality.label} · ${quality.summary}", color = quality.color, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp)
        Text("丢包：内网${roamingLossPercentText(gatewaySamples.size, gwLoss)} / 外网${roamingLossPercentText(externalSamples.size, extLoss)}", color = if (gwLoss + extLoss > 0) ErrorRed else Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("AP切换：${events.size}次 · 最长观测${events.maxOfOrNull { it.observationMs }?.let { "${it}ms" } ?: "—"} · 漫游窗口真实丢包${events.sumOf { it.roamingLossCount }}", color = if (events.isEmpty()) Muted else Orange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(
            "粘连AP：${if (stickyEvents.isEmpty()) "未发现明显粘连" else "疑似${stickyEvents.size}段 · 最长${stickyEvents.maxOfOrNull { it.durationMs }?.let { String.format(Locale.getDefault(), "%.1fs", it / 1000.0) } ?: "—"}"}",
            color = if (stickyEvents.isEmpty()) Muted else Orange,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun RoamingBandChannelLine(value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "频段/信道：",
            color = Muted,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(72.dp)
        )
        Text(
            value,
            color = TextDark,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RoamingIntervalField(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            CleanField(value, onValueChange, "500", keyboardType = KeyboardType.Number, leadingMark = "time")
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text("预设", fontSize = 11.sp) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                RoamingPingIntervalPresets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text("${preset}ms", fontSize = 12.sp) },
                        onClick = { onValueChange(preset.toString()); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoamingTimeoutField(
    value: String,
    custom: Boolean,
    onValueChange: (String) -> Unit,
    onAuto: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            CleanField(value, onValueChange, "1000", keyboardType = KeyboardType.Number, leadingMark = "hourglass")
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text(if (custom) "预设" else "自动", fontSize = 11.sp) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("自动", fontSize = 12.sp) },
                    onClick = { onAuto(); expanded = false }
                )
                RoamingPingTimeoutPresets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text("${preset}ms", fontSize = 12.sp) },
                        onClick = { onValueChange(preset.toString()); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigLongRow(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(56.dp))
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ConfigColumn(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FieldLabel(label)
        content()
    }
}

@Composable
private fun ConfigInfoBox(text: String, mark: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(if (color == Blue) BlueSoft.copy(alpha = 0.62f) else Color(0xFFF8FAFC), ShapeM)
            .border(1.dp, Border.copy(alpha = 0.78f), ShapeM)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarkBox(mark, color.copy(alpha = 0.10f), color)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()))
    }
}

@Composable
private fun MiniSelectPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pillShape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .height(32.dp)
            .clip(pillShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = pillShape,
        color = if (selected) BlueSoft else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (selected) Blue.copy(alpha = 0.40f) else Border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text, color = if (selected) Blue else TextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun PolicyPicker(current: ToolIpPolicy, onPick: (ToolIpPolicy) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(ShapeM)
                .background(Color.White, ShapeM)
                .border(1.dp, Border.copy(alpha = 0.86f), ShapeM)
                .clickable { open = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarkBox("wifi", BlueSoft, Blue)
            Spacer(Modifier.width(8.dp))
            Text(current.label, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("⌄", color = Muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        if (open) {
            AlertDialog(
                onDismissRequest = { open = false },
                confirmButton = {},
                title = { Text("IP策略", fontWeight = FontWeight.Bold, color = TextDark) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolIpPolicy.values().forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ShapeM)
                                    .background(if (item == current) BlueSoft else Color(0xFFF8FAFC), ShapeM)
                                    .clickable {
                                        onPick(item)
                                        open = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.label, color = if (item == current) Blue else TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (item == current) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Blue, modifier = Modifier.width(16.dp).height(16.dp))
                            }
                        }
                    }
                },
                shape = ShapeL
            )
        }
    }
}

@Composable
private fun SwipeDeleteToolBox(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val revealWidthPx = with(LocalDensity.current) { 58.dp.toPx() }
    val thresholdPx = revealWidthPx * 0.42f
    var dragOffset by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(dragOffset, tween(190, easing = FastOutSlowInEasing), label = "toolSwipe")
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(52.dp)
                .height(56.dp)
                .background(RedSoft, RoundedCornerShape(16.dp))
                .border(1.dp, ErrorRed.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "删除", tint = ErrorRed, modifier = Modifier.width(17.dp).height(17.dp))
                Text("删除", color = ErrorRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { dragOffset = if (dragOffset <= -thresholdPx) -revealWidthPx else 0f },
                        onDragCancel = { dragOffset = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset = (dragOffset + dragAmount).coerceIn(-revealWidthPx, 0f)
                        }
                    )
                }
        ) { content() }
    }
}

@Composable
private fun NsLookupRecordCard(record: NsLookupToolRecord, compact: Boolean, onCopy: () -> Unit) {
    SoftCompactToolCard(modifier = Modifier.clickable(onClick = onCopy)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(record.host, color = TextDark, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${record.costMs}ms", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text("${record.timeText} · ${record.recordType} · 点击复制", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ToolMonoLine("DNS", record.dnsServers)
        if (record.error.isNotBlank()) {
            Text("错误：${record.error}", color = ErrorRed, fontSize = 11.sp, maxLines = if (compact) 1 else 3, overflow = TextOverflow.Ellipsis)
        } else {
            if (record.recordType != NsLookupRecordType.AAAA.label) ToolMonoLine("A", record.ipv4.joinToString(" / ").ifBlank { "无" })
            if (record.recordType != NsLookupRecordType.A.label) ToolMonoLine("AAAA", record.ipv6.joinToString(" / ").ifBlank { "无" })
        }
    }
}

@Composable
private fun TracketLiveProcessCard(title: String, hops: List<String>) {
    SoftCompactToolCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("追踪过程", color = TextDark, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(title.removePrefix("正在追踪："), color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            hops.joinToString("\n").ifBlank { "等待第一跳返回..." },
            color = TextDark,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 24,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun TracketRecordCard(record: TracketToolRecord, expanded: Boolean, onToggle: () -> Unit) {
    SoftCompactToolCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.host, color = TextDark, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${record.timeText} · ${record.ipPolicy} · ${record.hops.size}/${record.maxHops}跳 · ${record.costMs}ms", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggle, modifier = Modifier.width(34.dp).height(34.dp)) {
                Text(if (expanded) "⌃" else "⌄", color = Blue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Text("目标IP：${record.targetIp}", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (record.error.isNotBlank()) {
            Text("错误：${record.error}", color = ErrorRed, fontSize = 11.sp, maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis)
        } else if (expanded) {
            SelectionContainer {
                Text(
                    record.copyText(),
                    color = TextDark,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                )
            }
        } else {
            Text(
                record.hops.take(3).joinToString("\n").ifBlank { "无跳点结果" },
                color = TextDark,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun SoftCompactToolCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun ToolMonoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$label：", color = Muted, fontSize = 10.sp, modifier = Modifier.width(44.dp))
        Text(
            value,
            color = TextDark,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetworkEnvironmentSettingsCard(
    env: NetworkEnvironment,
    publicIpResult: PublicIpResult,
    probeInfo: NetworkProbeInfo,
    publicIpLoading: Boolean,
    maskPrivacy: Boolean,
    onRefresh: () -> Unit,
    onCopyPublicIpv4: () -> Unit,
    onCopyPublicIpv6: () -> Unit,
    onOpenNatDiagnostics: () -> Unit,
    onOpenNsLookup: () -> Unit,
    onOpenTracket: () -> Unit,
    onOpenMtu: () -> Unit,
    onOpenRoaming: () -> Unit,
    onOpenIpv6Diagnostics: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("network_info", "网络信息", Purple)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenNatDiagnostics) { Text("NAT诊断", fontSize = 12.sp) }
            TextButton(onClick = onRefresh) { Text("刷新", fontSize = 12.sp) }
            IconButton(onClick = { onExpandedChange(!expanded) }, modifier = Modifier.width(32.dp).height(32.dp)) {
                Text(if (expanded) "⌃" else "⌄", color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val ipv4Text = if (maskPrivacy) maskIpText(publicIpResult.ipv4) else publicIpResult.ipv4
                val ipv6Text = if (maskPrivacy) maskIpText(publicIpResult.ipv6) else publicIpResult.ipv6
                InfoMetricTile("ipv4", "IPv4", ipv4Text, BlueSoft, Blue, Modifier.weight(1f), onClick = onCopyPublicIpv4)
                InfoMetricTile(
                    icon = "ipv6",
                    label = "IPv6",
                    value = ipv6Text,
                    iconBg = GreenSoft,
                    iconColor = Green,
                    modifier = Modifier.weight(1f),
                    onIconClick = onOpenIpv6Diagnostics,
                    onValueClick = if (publicIpResult.ipv6.isUsableIpText()) onCopyPublicIpv6 else onOpenIpv6Diagnostics
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                InfoMetricTile("nat", "NAT", probeInfo.natType, Color(0xFFFFE4E6), ErrorRed, Modifier.weight(1f), onClick = onOpenNatDiagnostics)
                InfoMetricTile("carrier", "运营商", probeInfo.carrier, Color(0xFFF3E8FF), Purple, Modifier.weight(1f))
            }
            return@SoftCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoMetricTile("local_ip", "本地IP", if (maskPrivacy) maskIpText(probeInfo.localIp) else probeInfo.localIp, Color(0xFFE0F7FA), Color(0xFF00A7C6), Modifier.weight(1f))
            InfoMetricTile("latency", "延迟", probeInfo.latencyText, Color(0xFFFFF3E0), Orange, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoMetricTile("ipv4", "公网IPv4", if (maskPrivacy) maskIpText(publicIpResult.ipv4) else publicIpResult.ipv4, BlueSoft, Blue, Modifier.weight(1f), onClick = onCopyPublicIpv4)
            val ipv6Display = publicIpResult.ipv6
            InfoMetricTile(
                icon = "ipv6",
                label = "公网IPv6",
                value = if (maskPrivacy) maskIpText(ipv6Display) else ipv6Display,
                iconBg = GreenSoft,
                iconColor = Green,
                modifier = Modifier.weight(1f),
                onIconClick = onOpenIpv6Diagnostics,
                onValueClick = if (ipv6Display.isUsableIpText()) onCopyPublicIpv6 else onOpenIpv6Diagnostics
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val mapped = if (publicIpResult.ipv4.isUsableIpText() && probeInfo.portText != "不可用" && probeInfo.portText != "待检测") "${publicIpResult.ipv4}:${probeInfo.portText}" else probeInfo.portText
            InfoMetricTile("mapping", "公网映射", if (maskPrivacy) maskIpText(mapped) else mapped, Color(0xFFE0F2FE), Blue, Modifier.weight(1f))
            InfoMetricTile("nat", "NAT类型", probeInfo.natType, Color(0xFFFFE4E6), ErrorRed, Modifier.weight(1f), onClick = onOpenNatDiagnostics)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoMetricTile("priority", "优先级", probeInfo.priority, Color(0xFFFFF3E0), Orange, Modifier.weight(1f))
            InfoMetricTile("egress", "出网端口", probeInfo.mappingBehavior, Color(0xFFE0F2FE), Blue, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoMetricTile("filter", "回包限制", probeInfo.filterBehavior, Color(0xFFFCE7F3), Color(0xFFD946EF), Modifier.weight(1f))
            InfoMetricTile("dns", "DNS诊断", probeInfo.dnsStatus, Color(0xFFF3E8FF), Purple, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoMetricTile("confidence", "NAT置信度", probeInfo.confidence, Color(0xFFF8FAFC), Muted, Modifier.weight(1f))
            InfoMetricTile("carrier", "运营商", probeInfo.carrier, Color(0xFFF3E8FF), Purple, Modifier.weight(1f))
        }
        NetworkToolShortcutRow(onOpenNsLookup = onOpenNsLookup, onOpenTracket = onOpenTracket, onOpenMtu = onOpenMtu, onOpenRoaming = onOpenRoaming)
        Text(
            probeInfo.diagnosis,
            color = if (probeInfo.proxyNotice.isNotBlank()) Purple else Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), ShapeM).padding(10.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(if (env.hasInternet) "可访问互联网" else "无互联网能力", if (env.hasInternet) GreenSoft else RedSoft, if (env.hasInternet) Green else ErrorRed, compact = true)
            StatusChip("网络 ${env.typeLabel}", Color(0xFFF3E8FF), Purple, compact = true)
            StatusChip("运营商 ${probeInfo.carrier}", Color(0xFFF3E8FF), Purple, compact = true)
            if (env.hasVpn) StatusChip("VPN/代理仅供参考", Color(0xFFF3E8FF), Purple, compact = true)
        }
    }
}


private fun natIconMark(value: String): String {
    val match = Regex("NAT\\s*([1-4])|NAT([1-4])", RegexOption.IGNORE_CASE).find(value)
    val digit = match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
    return if (digit != null) "nat$digit" else "nat"
}

@Composable
private fun InfoMetricTile(
    icon: String,
    label: String,
    value: String,
    iconBg: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onIconClick: (() -> Unit)? = null,
    onValueClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    @Composable
    fun TileContent() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(9.dp)
        ) {
            val displayIcon = if (icon == "nat") natIconMark(value) else icon
            Box(
                modifier = Modifier.then(
                    if (onIconClick != null) Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onIconClick
                    ) else Modifier
                )
            ) {
                MarkBox(displayIcon, iconBg, iconColor)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
                Text(
                    value,
                    color = TextDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .then(
                            if (onValueClick != null) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onValueClick
                            ) else Modifier
                        )
                )
            }
        }
    }
    val wholeTileClick = onClick.takeIf { onIconClick == null && onValueClick == null }
    if (wholeTileClick != null) {
        Surface(
            modifier = modifier
                .heightIn(min = 58.dp)
                .clip(ShapeM)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = wholeTileClick
                ),
            shape = ShapeM,
            color = Color(0xFFF8FAFC),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) { TileContent() }
    } else {
        Surface(
            modifier = modifier.heightIn(min = 58.dp),
            shape = ShapeM,
            color = Color(0xFFF8FAFC),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) { TileContent() }
    }
}


@Composable
private fun PingCompactChartCard(
    pingPoints: List<PingPoint>,
    intervalLabel: String = "停止",
    activeTargetLabel: String = "",
    running: Boolean,
    durationMs: Long,
    jitterMs: Double?,
    logCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onLog: () -> Unit
) {
    val stats = remember(pingPoints) {
        val successes = pingPoints.mapNotNull { it.latencyMs }
        val sampleTotal = pingPoints.sumOf { it.sampleCount }.coerceAtLeast(0)
        val lossTotal = pingPoints.sumOf { it.lossCount }
        PingCompactStats(
            current = successes.lastOrNull(),
            avg = successes.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            max = pingPoints.mapNotNull { it.maxLatencyMs ?: it.latencyMs }.maxOrNull(),
            min = pingPoints.mapNotNull { it.minLatencyMs ?: it.latencyMs }.minOrNull(),
            sampleTotal = sampleTotal,
            lossTotal = lossTotal,
            lossPercent = if (sampleTotal > 0) ((lossTotal * 100f) / sampleTotal).roundToInt() else 0
        )
    }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("ping", "Ping 测试", Blue)
            Spacer(Modifier.width(8.dp))
            StatusChip(if (running) "运行中 · ${stats.sampleTotal}次" else intervalLabel, if (running) GreenSoft else BlueSoft, if (running) Green else Blue, compact = true)
            Spacer(Modifier.weight(1f))
            val stateText = when {
                running && stats.lossPercent == 0 -> "● 正常 · 实时"
                running -> "● 丢包 ${stats.lossPercent}% · 实时"
                stats.lossPercent == 0 -> "● 已停止"
                else -> "● 丢包 ${stats.lossPercent}% · 已停止"
            }
            StatusChip(stateText, if (stats.lossPercent == 0) GreenSoft else RedSoft, if (stats.lossPercent == 0) Green else ErrorRed, compact = true)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            MiniMetric("当前", stats.current?.let { "${it}ms" } ?: "—", Blue, Modifier.width(72.dp))
            MiniMetric("平均", stats.avg?.let { "${it}ms" } ?: "—", Navy, Modifier.width(72.dp))
            MiniMetric("最高", stats.max?.let { "${it}ms" } ?: "—", Orange, Modifier.width(72.dp))
            MiniMetric("最低", stats.min?.let { "${it}ms" } ?: "—", Green, Modifier.width(72.dp))
            MiniMetric("丢包", "${stats.lossPercent}%", if (stats.lossPercent == 0) Muted else ErrorRed, Modifier.width(72.dp))
            MiniMetric("超时", "${stats.lossTotal}次", if (stats.lossTotal == 0) Muted else ErrorRed, Modifier.width(72.dp))
            MiniMetric("抖动", jitterMs?.let { formatPingJitter(it) } ?: "—", Purple, Modifier.width(72.dp))
            MiniMetric("时长", formatPingDuration(durationMs), Navy, Modifier.width(72.dp))
        }
        PingLineChart(pingPoints, activeTargetLabel, running = running, jitterMs = jitterMs)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = if (running) onStop else onStart,
                modifier = Modifier.weight(1f).height(38.dp),
                shape = ShapeM,
                colors = if (running) ButtonDefaults.buttonColors(containerColor = RedSoft, contentColor = ErrorRed) else ButtonDefaults.buttonColors()
            ) {
                Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (running) "停止 Ping" else "开始 Ping", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onLog, modifier = Modifier.weight(0.8f).height(38.dp), shape = ShapeM) {
                Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("历史 $logCount", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(0.65f).height(38.dp), shape = ShapeM) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("清空", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0xFFF8FAFC), ShapeM).padding(horizontal = 5.dp, vertical = 5.dp)) {
        Column {
            Text(label, color = Muted, fontSize = 8.sp, maxLines = 1)
            Text(value, color = color, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PingLineChart(points: List<PingPoint>, activeTargetLabel: String = "", running: Boolean = false, jitterMs: Double? = null) {
    val allPoints = remember(points) { compactPingPointsForRender(points) }
    val latestMs = allPoints.lastOrNull()?.elapsedMs ?: 0L
    val earliestMs = allPoints.firstOrNull()?.elapsedMs ?: 0L
    val fullSpanMs = (latestMs - earliestMs).coerceAtLeast(1_000L)
    val liveWindowSpanMs = when {
        running && latestMs < 60_000L -> latestMs.coerceAtLeast(1_000L)
        running -> 60_000L
        else -> fullSpanMs
    }
    var stoppedZoomSpanMs by remember { mutableStateOf<Long?>(null) }
    val windowSpanMs = if (running) liveWindowSpanMs else (stoppedZoomSpanMs ?: fullSpanMs).coerceIn(1_000L, fullSpanMs)
    var autoFollow by remember { mutableStateOf(true) }
    var viewEndMs by remember { mutableStateOf(windowSpanMs) }
    var chartWidthPx by remember { mutableStateOf(1f) }
    var selectedPoint by remember { mutableStateOf<PingPoint?>(null) }
    val firstPointKey = allPoints.firstOrNull()?.timeEpochMs ?: 0L

    LaunchedEffect(firstPointKey, allPoints.isEmpty()) {
        selectedPoint = null
        autoFollow = true
        stoppedZoomSpanMs = null
    }

    LaunchedEffect(running, latestMs, fullSpanMs) {
        if (!running && allPoints.isNotEmpty()) {
            // 测试停止后自动缩放到全局概览。
            stoppedZoomSpanMs = fullSpanMs
            autoFollow = false
            viewEndMs = latestMs.coerceAtLeast(fullSpanMs)
        }
    }

    LaunchedEffect(latestMs, windowSpanMs, autoFollow, running) {
        if (running && autoFollow) viewEndMs = latestMs.coerceAtLeast(windowSpanMs)
        else {
            val minEnd = (earliestMs + windowSpanMs).coerceAtLeast(windowSpanMs)
            val maxEnd = latestMs.coerceAtLeast(minEnd)
            viewEndMs = viewEndMs.coerceIn(minEnd, maxEnd)
        }
    }

    val effectiveViewEndMs = if (running && autoFollow) latestMs.coerceAtLeast(windowSpanMs) else {
        val minEnd = (earliestMs + windowSpanMs).coerceAtLeast(windowSpanMs)
        val maxEnd = latestMs.coerceAtLeast(minEnd)
        viewEndMs.coerceIn(minEnd, maxEnd)
    }
    val viewStartMs = (effectiveViewEndMs - windowSpanMs).coerceAtLeast(0L)
    val visible = remember(allPoints, viewStartMs, effectiveViewEndMs, latestMs, windowSpanMs) {
        val window = allPoints.filter { it.elapsedMs in viewStartMs..effectiveViewEndMs }
        if (window.isNotEmpty() || allPoints.isEmpty()) {
            window
        } else {
            val fallbackEnd = latestMs.coerceAtLeast(windowSpanMs)
            val fallbackStart = (fallbackEnd - windowSpanMs).coerceAtLeast(0L)
            allPoints.filter { it.elapsedMs in fallbackStart..fallbackEnd }
        }
    }
    val values = remember(visible) {
        visible.flatMap { listOfNotNull(it.minLatencyMs ?: it.latencyMs, it.latencyMs, it.maxLatencyMs ?: it.latencyMs) }
    }
    val yRange = remember(values) { computePingYAxisRange(values) }
    val minY = yRange.min
    val maxY = yRange.max
    val instantStartMs = (latestMs - 2_000L).coerceAtLeast(0L)
    val instantSamples = remember(allPoints, instantStartMs, latestMs) {
        allPoints.filter { it.elapsedMs >= instantStartMs && it.elapsedMs <= latestMs }.sumOf { it.sampleCount }
    }
    val instantSpanSec = ((latestMs - instantStartMs).coerceAtLeast(1_000L) / 1000f).coerceAtLeast(1f)
    val totalSamples = remember(allPoints) { allPoints.sumOf { it.sampleCount } }
    val averageSpanSec = (latestMs.coerceAtLeast(1_000L) / 1000f).coerceAtLeast(1f)
    val instantRateText = if (instantSamples > 0) "瞬时${String.format(java.util.Locale.US, "%.1f", instantSamples / instantSpanSec)}/s" else "瞬时—"
    val averageRateText = if (totalSamples > 0) "平均${String.format(java.util.Locale.US, "%.1f", totalSamples / averageSpanSec)}/s" else "平均—"
    val rangeText = when {
        running && autoFollow -> "实时"
        running -> "查看历史 ${formatPingAxisTime(viewStartMs)}-${formatPingAxisTime(effectiveViewEndMs)}"
        else -> "已停止 ${formatPingAxisTime(viewStartMs)}-${formatPingAxisTime(effectiveViewEndMs)}"
    }
    val labelParts = activeTargetLabel.split(" · ").map { it.trim() }.filter { it.isNotBlank() }
    val targetDisplay = labelParts.firstOrNull().orEmpty()
    val engineDisplay = labelParts.firstOrNull { it.startsWith("TCP:", ignoreCase = true) } ?: "ICMP"
    val selected = selectedPoint
    val axisLabels = remember(yRange) { pingAxisLabels(yRange) }
    val axisTicks = remember(viewStartMs, effectiveViewEndMs, chartWidthPx) { pingAxisTicks(viewStartMs, effectiveViewEndMs, chartWidthPx) }
    val successSegments = remember(visible) {
        val successPoints = visible.filter { it.latencyMs != null }
        val expectedGapMs = visible.zipWithNext().map { (a, b) -> (b.elapsedMs - a.elapsedMs).coerceAtLeast(1L) }.minOrNull()?.coerceAtLeast(1L) ?: 1_000L
        val normalGapLimit = maxOf(expectedGapMs * 3L, 900L)
        successPoints.zipWithNext().map { (a, b) ->
            val hasRealLoss = visible.any { it.elapsedMs > a.elapsedMs && it.elapsedMs < b.elapsedMs && (it.lossCount > 0 || it.latencyMs == null) }
            PingLineSegment(a, b, dashed = hasRealLoss || b.elapsedMs - a.elapsedMs > normalGapLimit)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Text(
                    listOf(
                        targetDisplay.takeIf { it.isNotBlank() },
                        jitterMs?.let { "抖动${formatPingJitter(it)}" } ?: "抖动—",
                        instantRateText,
                        averageRateText,
                        "窗口${formatPingDuration(windowSpanMs)}",
                        engineDisplay,
                        rangeText
                    ).filterNotNull().joinToString(" · "),
                    color = Muted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            if (!autoFollow) {
                Spacer(Modifier.width(6.dp))
                SoftChoicePill(
                    text = if (running) "回实时" else "回全局",
                    selected = false,
                    onClick = {
                        if (running) autoFollow = true
                        else {
                            stoppedZoomSpanMs = fullSpanMs
                            viewEndMs = latestMs.coerceAtLeast(fullSpanMs)
                        }
                    },
                    compact = true
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(204.dp)
                .background(Color(0xFFF8FAFC), ShapeM)
                .clip(ShapeM)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { chartWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(visible, viewStartMs, effectiveViewEndMs, windowSpanMs, latestMs) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (running) autoFollow = true
                                else {
                                    stoppedZoomSpanMs = fullSpanMs
                                    viewEndMs = latestMs.coerceAtLeast(fullSpanMs)
                                }
                            },
                            onTap = { offset ->
                                val left = 82f
                                val right = size.width - 34f
                                val plotW = (right - left).coerceAtLeast(1f)
                                val x = offset.x.coerceIn(left, right)
                                val targetMs = viewStartMs + (((x - left) / plotW) * windowSpanMs).toLong()
                                selectedPoint = visible.minByOrNull { kotlin.math.abs(it.elapsedMs - targetMs) }
                            }
                        )
                    }
                    .pointerInput(latestMs, earliestMs, windowSpanMs, chartWidthPx, running) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            val left = 82f
                            val rightPad = 34f
                            val plotW = (chartWidthPx - left - rightPad).coerceAtLeast(1f)
                            val deltaMs = (dragAmount / plotW * windowSpanMs).toLong()
                            autoFollow = false
                            val minEnd = (earliestMs + windowSpanMs).coerceAtLeast(windowSpanMs)
                            val maxEnd = latestMs.coerceAtLeast(minEnd)
                            viewEndMs = (viewEndMs - deltaMs).coerceIn(minEnd, maxEnd)
                        }
                    }
                    .pointerInput(running, fullSpanMs, windowSpanMs, latestMs, chartWidthPx) {
                        if (!running) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val left = 82f
                                val rightPad = 34f
                                val plotW = (chartWidthPx - left - rightPad).coerceAtLeast(1f)
                                val focalRatio = ((centroid.x - left) / plotW).coerceIn(0f, 1f)
                                val focalMs = viewStartMs + (windowSpanMs * focalRatio).toLong()
                                val newSpan = (windowSpanMs / zoom.coerceIn(0.25f, 4.0f)).toLong().coerceIn(1_000L, fullSpanMs)
                                stoppedZoomSpanMs = newSpan
                                val newEnd = (focalMs + ((newSpan) * (1f - focalRatio)).toLong() - (pan.x / plotW * newSpan).toLong())
                                val minEnd = (earliestMs + newSpan).coerceAtLeast(newSpan)
                                val maxEnd = latestMs.coerceAtLeast(minEnd)
                                viewEndMs = newEnd.coerceIn(minEnd, maxEnd)
                                autoFollow = false
                            }
                        }
                    }
            ) {
                val left = 82f
                val top = 42f
                val right = size.width - 34f
                val bottom = size.height - 42f
                val plotW = (right - left).coerceAtLeast(1f)
                val plotH = (bottom - top).coerceAtLeast(1f)

                fun xOf(ms: Long): Float = left + ((ms - viewStartMs).toFloat() / windowSpanMs.toFloat()).coerceIn(0f, 1f) * plotW
                fun yOf(value: Int): Float {
                    val clipped = value.coerceIn(minY, maxY)
                    val span = (maxY - minY).coerceAtLeast(1)
                    return bottom - ((clipped - minY).toFloat() / span.toFloat()) * plotH
                }

                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.rgb(226, 232, 240)
                    strokeWidth = 1f
                    textSize = 23f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.rgb(100, 116, 139)
                    textSize = 23f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }

                axisLabels.forEach { tick ->
                    val y = yOf(tick)
                    drawLine(Border.copy(alpha = 0.45f), Offset(left, y), Offset(right, y), strokeWidth = 1f)
                    val label = tick.toString()
                    val tw = axisPaint.measureText(label)
                    drawContext.canvas.nativeCanvas.drawText(label, (left - 12f - tw).coerceAtLeast(12f), (y + 7f).coerceIn(top + 10f, bottom - 3f), axisPaint)
                }

                axisTicks.forEach { tickMs ->
                    val x = xOf(tickMs)
                    drawLine(Border.copy(alpha = 0.25f), Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
                    val label = formatPingAxisTime(tickMs)
                    val tw = axisPaint.measureText(label)
                    val tx = (x - tw / 2f).coerceIn(left + 2f, right - tw - 2f)
                    drawContext.canvas.nativeCanvas.drawText(label, tx, (bottom + 24f).coerceAtMost(size.height - 10f), axisPaint)
                }

                // 丢包不再铺满整张图，统一绘制成底部小立柱，避免整块发红。

                // 成功点之间按质量分段：连续成功用实线；中间发生丢包/超时或长间隔时，用浅蓝虚线桥接。
                fun chartLatencyOf(p: PingPoint): Int? {
                    val latency = p.latencyMs ?: return null
                    val peak = p.maxLatencyMs ?: latency
                    val highPoint = p.highLatency || peak >= (minY + ((maxY - minY) * 0.85f)).roundToInt()
                    // 高延迟是一次成功响应，不是断点；聚合点里如果有峰值，用峰值参与主折线，避免橙点悬空。
                    return if (highPoint && peak > latency) peak else latency
                }
                fun drawSegment(a: PingPoint, b: PingPoint, dashed: Boolean) {
                    val la = chartLatencyOf(a) ?: return
                    val lb = chartLatencyOf(b) ?: return
                    val path = Path().apply {
                        moveTo(xOf(a.elapsedMs), yOf(la))
                        lineTo(xOf(b.elapsedMs), yOf(lb))
                    }
                    drawPath(
                        path = path,
                        color = if (dashed) Blue.copy(alpha = 0.42f) else Blue,
                        style = Stroke(
                            width = if (dashed) 2.0f else 2.6f,
                            cap = StrokeCap.Round,
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 7f), 0f) else null
                        )
                    )
                }
                successSegments.forEach { segment ->
                    drawSegment(segment.from, segment.to, dashed = segment.dashed)
                }

                visible.forEach { p ->
                    val latency = p.latencyMs
                    if (latency != null && p.lossCount == 0) {
                        val x = xOf(p.elapsedMs)
                        val minSample = p.minLatencyMs
                        val maxSample = p.maxLatencyMs
                        val peak = maxSample ?: latency
                        val highPoint = p.highLatency || peak >= (minY + ((maxY - minY) * 0.85f)).roundToInt()
                        if (highPoint && minSample != null && maxSample != null && maxSample > minSample) {
                            // 只有独立橙色高延迟点才画细蓝竖线，普通点不画竖线。
                            drawLine(Blue.copy(alpha = 0.16f), Offset(x, yOf(minSample)), Offset(x, yOf(maxSample)), strokeWidth = 1.0f, cap = StrokeCap.Round)
                        }
                        if (highPoint) drawCircle(Orange, radius = 3.5f, center = Offset(x, yOf(peak)))
                    }
                    if (p.lossCount > 0) {
                        val x = xOf(p.elapsedMs)
                        val colH = (8f + p.lossCount.coerceAtMost(8) * 2.2f).coerceAtMost(28f)
                        drawLine(ErrorRed.copy(alpha = 0.82f), Offset(x, bottom - colH), Offset(x, bottom - 2f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                    }
                }

                selected?.takeIf { it.elapsedMs in viewStartMs..effectiveViewEndMs }?.let { p ->
                    val x = xOf(p.elapsedMs)
                    val anchorY = p.latencyMs?.let { yOf(it) } ?: (bottom - 10f)
                    val lineTop = (anchorY - 32f).coerceAtLeast(top)
                    val lineBottom = (anchorY + 32f).coerceAtMost(bottom)
                    drawLine(Color(0xFF334155).copy(alpha = 0.45f), Offset(x, lineTop), Offset(x, lineBottom), strokeWidth = 1.5f, cap = StrokeCap.Round)
                    p.latencyMs?.let { drawCircle(Navy, radius = 4.5f, center = Offset(x, yOf(it))) }
                }
            }
        }
        selected?.let { p ->
            val time = DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(p.timeEpochMs))
            val text = if (p.sampleCount > 1) {
                "$time · 聚合${p.sampleCount}次 · 平均${p.latencyMs?.let { "${it}ms" } ?: "—"} · 最小${p.minLatencyMs?.let { "${it}ms" } ?: "—"} · 最大${p.maxLatencyMs?.let { "${it}ms" } ?: "—"} · 丢包${p.lossCount}"
            } else {
                "$time · ${p.latencyMs?.let { "延迟 ${it}ms" } ?: "丢包/超时"} · 丢包${p.lossCount}"
            }
            Text(text, color = TextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } ?: Text(
            when {
                running && autoFollow -> "$rangeText · 红色=丢包，橙色=高延迟；拖动查看历史，双击回实时"
                running -> "$rangeText · 红色=丢包，橙色=高延迟；双击回实时"
                windowSpanMs < fullSpanMs -> "$rangeText · 双指缩放/左右拖动查看细节，双击回全局"
                else -> "$rangeText · 双指放大查看细节"
            },
            color = Muted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun pingAxisTicks(startMs: Long, endMs: Long, plotWidthPx: Float): List<Long> {
    val maxCount = when {
        plotWidthPx < 320f -> 4
        plotWidthPx < 520f -> 5
        else -> 6
    }
    val span = (endMs - startMs).coerceAtLeast(1L)
    val count = maxCount.coerceAtLeast(3)
    return (0 until count).map { idx -> startMs + (span * idx / (count - 1)) }
}

private fun formatPingRelativeLabel(deltaMs: Long): String {
    val abs = kotlin.math.abs(deltaMs)
    return when {
        abs < 1_000L -> "现在"
        abs < 60_000L -> "-${abs / 1_000}s"
        else -> "-${abs / 60_000}m"
    }
}

private fun formatPingAxisTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    return when {
        safe < 10_000L && safe % 1_000L != 0L -> String.format(java.util.Locale.US, "%.1fs", safe / 1000.0)
        safe < 60_000L -> {
            if (safe % 1_000L == 0L) "${safe / 1_000L}s" else String.format(java.util.Locale.US, "%.1fs", safe / 1000.0)
        }
        else -> {
            val totalSec = safe / 1_000L
            val remainMs = safe % 1_000L
            if (remainMs == 0L) "${totalSec / 60}m${(totalSec % 60).toString().padStart(2, '0')}"
            else String.format(java.util.Locale.US, "%dm%04.1fs", totalSec / 60, (totalSec % 60) + remainMs / 1000.0)
        }
    }
}

private fun buildPingLossRanges(points: List<PingPoint>, startMs: Long, endMs: Long): List<Triple<Long, Long, Int>> {
    val loss = points.filter { it.lossCount > 0 }.sortedBy { it.elapsedMs }
    if (loss.isEmpty()) return emptyList()
    val ranges = mutableListOf<Triple<Long, Long, Int>>()
    var s = loss.first().elapsedMs
    var e = s
    var c = loss.first().lossCount
    var prev = loss.first()
    loss.drop(1).forEach { p ->
        val close = p.elapsedMs - prev.elapsedMs <= 1_100L
        if (close) {
            e = p.elapsedMs
            c += p.lossCount
        } else {
            ranges += Triple(s.coerceAtLeast(startMs), e.coerceAtMost(endMs), c)
            s = p.elapsedMs
            e = p.elapsedMs
            c = p.lossCount
        }
        prev = p
    }
    ranges += Triple(s.coerceAtLeast(startMs), e.coerceAtMost(endMs), c)
    return ranges
}

@Composable
private fun DiagnosisAdviceInlineCard(mode: TestMode, ipv4Stats: ProtocolStats, ipv6Stats: ProtocolStats) {
    val hasV4 = mode != TestMode.IPV6_ONLY && ipv4Stats.totalAttempts > 0
    val hasV6 = mode != TestMode.IPV4_ONLY && ipv6Stats.totalAttempts > 0
    val summary = SessionSummary(
        startedAtEpochMs = System.currentTimeMillis(),
        host = "",
        port = 0,
        mode = mode,
        ipv4Stats = if (hasV4) ipv4Stats else null,
        ipv6Stats = if (hasV6) ipv6Stats else null
    )
    val v4Peak = if (hasV4) protocolPeak(ipv4Stats) else 0
    val v6Peak = if (hasV6) protocolPeak(ipv6Stats) else 0
    val conclusion = if (!hasV4 && !hasV6) "测试完成后生成实际诊断" else historyConclusion(summary)
    val advice = if (!hasV4 && !hasV6) {
        listOf("建议使用分别测试模式，便于对比 IPv4 / IPv6。", "测试后会结合峰值和失败原因自动分析。")
    } else historyAdvice(summary)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("!", "诊断建议", Orange)
            Spacer(Modifier.weight(1f))
            StatusChip("自动分析", BlueSoft, Blue, compact = true)
        }
        Text(conclusion, color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("IPv4峰值", if (hasV4) v4Peak.toString() else "—", if (v4Peak in 1..999) ErrorRed else Blue, Modifier.weight(1f))
            MetricTile("IPv6峰值", if (hasV6) v6Peak.toString() else "—", if (v6Peak >= 5000) Green else Blue, Modifier.weight(1f))
        }
        advice.take(3).forEachIndexed { index, line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("${index + 1}.", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                Text(line, color = Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}


@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = ShapeM,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
            Text(label, color = Muted, fontSize = 8.sp, maxLines = 1)
            Text(value, color = color, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun FailureReasonCard(stats: List<ProtocolStats>, onMore: () -> Unit) {
    val merged = mergedErrors(stats)
    if (merged.isEmpty()) return
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("!", "失败原因", ErrorRed)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onMore) { Text("更多", fontSize = 13.sp) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            merged.entries.sortedByDescending { it.value }.take(6).forEach { (name, count) ->
                ReasonChip("$name $count")
            }
        }
    }
}

@Composable
private fun ReasonChip(text: String) {
    Box(
        modifier = Modifier
            .background(RedSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun RecentLogCard(logs: List<LogLine>, maskPrivacy: Boolean, onMore: () -> Unit, showMore: Boolean = true) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("□", "最近日志", Blue)
            Spacer(Modifier.weight(1f))
            if (showMore) {
                TextButton(onClick = onMore) { Text("更多", fontSize = 12.sp, color = Muted) }
            }
        }
        if (logs.isEmpty()) {
            Text("暂无日志", color = Muted, fontSize = 12.sp)
        } else {
            val visibleLogs = logs.takeLast(4).asReversed()
            visibleLogs.forEachIndexed { index, line ->
                CompactLogLine(line, maskPrivacy)
                if (index != visibleLogs.lastIndex) {
                    HorizontalDivider(color = Border.copy(alpha = 0.65f), thickness = 0.6.dp)
                }
            }
        }
    }
}

@Composable
private fun CompactLogLine(line: LogLine, maskPrivacy: Boolean) {
    val tag = when (line.level) {
        LogLevel.STAT -> "统计"
        LogLevel.SUCCESS -> "新增"
        LogLevel.WARN -> "告警"
        LogLevel.ERROR -> "错误"
        LogLevel.INFO -> "信息"
    }
    val color = when (line.level) {
        LogLevel.STAT -> Blue
        LogLevel.SUCCESS -> Green
        LogLevel.WARN -> Orange
        LogLevel.ERROR -> ErrorRed
        LogLevel.INFO -> Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(34.dp)) {
        Text(
            line.timeText,
            color = Muted,
            modifier = Modifier.width(62.dp),
            maxLines = 1,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(tag, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                compactLogText(if (maskPrivacy) maskIpText(line.text) else line.text),
                color = TextDark,
                maxLines = 1,
                softWrap = false,
                fontSize = 11.sp
            )
        }
    }
}


@Composable
private fun SwipeDeleteHistoryCard(
    item: SessionSummary,
    maskPrivacy: Boolean,
    onClick: () -> Unit,
    onEditRemark: () -> Unit,
    onDelete: () -> Unit
) {
    val revealWidthPx = with(LocalDensity.current) { 78.dp.toPx() }
    val thresholdPx = revealWidthPx * 0.35f
    var dragOffset by remember(item.id) { mutableStateOf(0f) }
    val isRevealed = kotlin.math.abs(dragOffset) >= revealWidthPx * 0.9f
    val revealLeft = dragOffset > 0f

    Box(modifier = Modifier.fillMaxWidth()) {
        if (isRevealed) {
            Box(
                modifier = Modifier
                    .align(if (revealLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .width(72.dp)
                    .heightIn(min = 156.dp)
                    .background(
                        ErrorRed,
                        if (revealLeft) {
                            RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 14.dp, bottomEnd = 14.dp)
                        } else {
                            RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp, topStart = 14.dp, bottomStart = 14.dp)
                        }
                    )
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "删除", tint = Color.White, modifier = Modifier.width(22.dp).height(22.dp))
                    Text("删除", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            dragOffset = when {
                                dragOffset <= -thresholdPx -> -revealWidthPx
                                dragOffset >= thresholdPx -> revealWidthPx
                                else -> 0f
                            }
                        },
                        onDragCancel = { dragOffset = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffset = (dragOffset + dragAmount).coerceIn(-revealWidthPx, revealWidthPx)
                        }
                    )
                }
        ) {
            HistoryCard(
                item = item,
                maskPrivacy = maskPrivacy,
                onClick = {
                    if (isRevealed) dragOffset = 0f else onClick()
                },
                onEditRemark = onEditRemark
            )
        }
    }
}

@Composable
private fun HistoryCard(
    item: SessionSummary,
    maskPrivacy: Boolean,
    onClick: () -> Unit,
    onEditRemark: () -> Unit
) {
    val mainStats = item.ipv4Stats ?: item.ipv6Stats
    val protocol = when {
        item.ipv4Stats != null && item.ipv6Stats != null -> "分别测试"
        item.ipv4Stats != null -> "IPv4 完成"
        item.ipv6Stats != null -> "IPv6 完成"
        else -> "完成"
    }
    val address = mainStats?.resolvedAddresses?.firstOrNull().orEmpty()
    SoftCard {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = Muted, modifier = Modifier.width(15.dp).height(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(item.startedAtText, color = Muted, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1)
                }
                StatusChip(protocol, GreenSoft, Green, compact = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailItem(Icons.Filled.Assessment, "目标", if (maskPrivacy) maskIpText(item.host) else item.host, Purple, Modifier.weight(1f))
                DetailItem(Icons.Filled.Tune, "端口", item.port.toString(), Purple, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailItem(Icons.Filled.SignalCellularAlt, "活动", (mainStats?.activeSessions ?: 0).toString(), Blue, Modifier.weight(1f))
                DetailItem(Icons.Filled.Assessment, "总计", (mainStats?.totalAttempts ?: 0).toString(), Navy, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailItem(Icons.Filled.WarningAmber, "失败", (mainStats?.totalFailure ?: 0).toString(), ErrorRed, Modifier.weight(1f))
                DetailItem(Icons.Filled.Download, "CPS", "${mainStats?.cps ?: 0}/s", Orange, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MarkDetailItem("address", "地址", if (maskPrivacy) maskAddress(address) else address.ifBlank { "无" }, Blue, Modifier.weight(1f))
            }
            val reasons = mainStats?.errorSummary.orEmpty()
            if (reasons.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("失败原因", color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        reasons.entries.sortedByDescending { it.value }.take(3).forEach { (name, count) ->
                            ReasonChip("$name $count")
                        }
                    }
                }
            }
            Text(
                "诊断：${historyConclusion(item)}",
                color = Orange,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlueSoft.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onEditRemark)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconGlyph("note", Blue, Modifier.width(14.dp).height(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (item.remark.isBlank()) "备注：点击添加备注" else "备注：${item.remark}",
                    color = if (item.remark.isBlank()) Muted else TextDark,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("编辑", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
private fun MarkDetailItem(mark: String, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconGlyph(mark, color, Modifier.width(14.dp).height(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(value, color = TextDark, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DetailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.width(14.dp).height(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(value, color = TextDark, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}


@Composable
private fun SoftChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val bg = if (selected) Color(0xFFEBDCFD) else Color.White
    val fg = if (selected) TextDark else Muted
    Surface(
        shape = shape,
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.8.dp, if (selected) Blue.copy(alpha = 0.16f) else Border.copy(alpha = 0.66f)),
        modifier = modifier
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 6.dp else 8.dp
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun StatusChip(text: String, bg: Color, fg: Color, compact: Boolean = false) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(if (compact) 12.dp else 14.dp))
            .padding(horizontal = if (compact) 9.dp else 11.dp, vertical = if (compact) 5.dp else 7.dp)
    ) {
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1)
    }
}


@Composable
private fun iconFor(mark: String) = when (mark) {
    "ipv4", "ipv6" -> Icons.Filled.Language
    "local_ip" -> Icons.Filled.Home
    "latency" -> Icons.Filled.History
    "mapping", "target" -> Icons.Filled.Assessment
    "nat", "filter", "privacy" -> Icons.Filled.Shield
    "priority" -> Icons.Filled.WarningAmber
    "egress" -> Icons.Filled.OpenInNew
    "dns" -> Icons.Filled.Tune
    "confidence" -> Icons.Filled.CheckCircle
    "carrier", "wifi", "∿", "ping", "▮" -> Icons.Filled.SignalCellularAlt
    "nslookup", "host" -> Icons.Filled.Search
    "tracket" -> Icons.Filled.OpenInNew
    "mtu", "≡", "mode", "tune", "port" -> Icons.Filled.Tune
    "roaming" -> Icons.Filled.SignalCellularAlt
    "◎" -> Icons.Filled.Assessment
    "hourglass", "time" -> Icons.Filled.History
    "□", "log" -> Icons.Filled.Article
    "!" -> Icons.Filled.WarningAmber
    "count" -> Icons.Filled.Refresh
    else -> Icons.Filled.Info
}

@Composable
private fun InfoLine(label: String, value: String, color: Color = TextDark) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label：", color = Muted, fontSize = 12.sp, modifier = Modifier.width(54.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                value,
                color = color,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}


@Composable
private fun PublicExitLine(
    label: String,
    value: String,
    copyValue: String,
    onCopy: () -> Unit,
    color: Color = TextDark
) {
    val canCopy = copyValue.isNotBlank() && copyValue != "不可用" && copyValue != "检测中"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clickable(enabled = canCopy, onClick = onCopy)
            .background(if (canCopy) BlueSoft.copy(alpha = 0.28f) else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            "$label：$value",
            color = if (canCopy) Blue else color,
            fontSize = 12.sp,
            fontWeight = if (canCopy) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
    }
}



@Composable
private fun BottomNav(selectedTab: MainTab, onSelect: (MainTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFFEAF2FF))
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            val image = when (tab) {
                MainTab.SETTINGS -> Icons.Filled.Settings
                MainTab.TEST -> Icons.Filled.PlayArrow
                MainTab.LOGS -> Icons.Filled.Article
            }
            val shape = RoundedCornerShape(24.dp)
            val interactionSource = remember(tab) { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(shape)
                    .background(if (selected) Color(0xFFEBDCFD) else Color.Transparent, shape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelect(tab) }
                    )
                    .padding(top = 5.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    image,
                    contentDescription = tab.label,
                    tint = if (selected) Blue else Muted,
                    modifier = Modifier.width(19.dp).height(19.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.label,
                    color = TextDark,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

private fun startForegroundNotice(context: Context, text: String) {
    val intent = Intent(context, TestForegroundService::class.java)
        .setAction(TestForegroundService.ACTION_START)
        .putExtra(TestForegroundService.EXTRA_TEXT, text)
    ContextCompat.startForegroundService(context, intent)
}

private fun updateForegroundNotice(context: Context, text: String) {
    val intent = Intent(context, TestForegroundService::class.java)
        .setAction(TestForegroundService.ACTION_UPDATE)
        .putExtra(TestForegroundService.EXTRA_TEXT, text)
    ContextCompat.startForegroundService(context, intent)
}

private fun stopForegroundNotice(context: Context) {
    val intent = Intent(context, TestForegroundService::class.java)
        .setAction(TestForegroundService.ACTION_STOP)
    runCatching { context.startService(intent) }
}


private fun compactLogText(text: String): String {
    return text
        .replace("统计 - 成功：", "成功 ")
        .replace(" | 失败：", " 失败 ")
        .replace(" | 活动：", " 活动 ")
        .replace(" | 总计：", " 总计 ")
        .replace(" | 新增：", " 新增 ")
        .replace(" | CPS：", " CPS ")
        .replace("/秒", "/s")
        .replace("测试完成 - 活动：", "完成 活动 ")
}

private fun displayIpList(values: List<String>, maskPrivacy: Boolean): String {
    if (values.isEmpty()) return "未解析"
    return values.joinToString(" / ") { if (maskPrivacy) maskAddress(it) else it }
}

private fun maskedLogs(logs: List<LogLine>): List<LogLine> = logs.map { it.copy(text = maskIpText(it.text)) }

private fun maskedSummary(summary: SessionSummary): SessionSummary = summary.copy(
    host = maskIpText(summary.host),
    ipv4Stats = summary.ipv4Stats?.let { maskStats(it) },
    ipv6Stats = summary.ipv6Stats?.let { maskStats(it) }
)

private fun maskStats(stats: ProtocolStats): ProtocolStats = stats.copy(
    resolvedAddresses = stats.resolvedAddresses.map { maskAddress(it) }
)

private fun maskIpText(text: String): String {
    val ipv4Masked = IPV4_REGEX.replace(text) { match -> maskAddress(match.value) }
    return IPV6_REGEX.replace(ipv4Masked) { match ->
        val value = match.value
        if (value.contains("::") || value.any { it.lowercaseChar() in 'a'..'f' }) maskAddress(value) else value
    }
}

private fun maskAddress(address: String): String {
    val raw = address.removePrefix("[").removeSuffix("]")
    return if (raw.contains(":")) {
        val parts = raw.split(":").filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0]}:${parts[1]}:****"
            parts.size == 1 -> "${parts[0]}:****"
            else -> "****"
        }
    } else {
        val parts = raw.split(".")
        if (parts.size == 4) "${parts[0]}.${parts[1]}.*.*" else "****"
    }
}

private fun mergedErrors(stats: List<ProtocolStats>): Map<String, Int> {
    val out = linkedMapOf<String, Int>()
    stats.forEach { s ->
        s.errorSummary.forEach { (k, v) -> out[k] = (out[k] ?: 0) + v }
    }
    return out
}

private fun failureDetailLines(stats: List<ProtocolStats>): List<String> {
    val errors = mergedErrors(stats)
    if (errors.isEmpty()) return listOf("暂无失败原因。")
    return errors.entries.sortedByDescending { it.value }.map { (k, v) -> "$k：$v 次" }
}

private fun historyDetailLines(summary: SessionSummary, maskPrivacy: Boolean): List<String> {
    val host = if (maskPrivacy) maskIpText(summary.host) else summary.host
    val lines = mutableListOf<String>()
    lines += "时间：${summary.startedAtText}"
    lines += "目标：$host:${summary.port}"
    lines += "模式：${summary.mode.label}"
    summary.ipv4Stats?.let { lines += statsLines("IPv4", it) }
    summary.ipv6Stats?.let { lines += statsLines("IPv6", it) }
    return lines
}

private fun statsLines(name: String, s: ProtocolStats): List<String> = listOf(
    "$name 活动：${s.activeSessions}",
    "$name 失败：${s.totalFailure}",
    "$name 总计：${s.totalAttempts}",
    "$name 新增：${s.lastAdded}",
    "$name CPS：${s.cps}/s",
    "$name 失败原因：${if (s.errorSummary.isEmpty()) "无" else s.errorSummary.entries.joinToString { "${it.key}:${it.value}" }}"
)

private fun String.onlyDigits(): String = filter { it.isDigit() }

private val IPV4_REGEX = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
private val IPV6_REGEX = Regex("""(?i)(?<![\w.])(?:[0-9a-f]{1,4}:){2,}[0-9a-f]{0,4}(?:%[\w.]+)?(?![\w.])""")

private val BgTop = Color(0xFFF8FBFF)
private val Bg = Color(0xFFF6F8FC)
private val TextDark = Color(0xFF111827)
private val Muted = Color(0xFF64748B)
private val Border = Color(0xFFE5E7EB)
private val Blue = Color(0xFF2563EB)
private val BlueSoft = Color(0xFFEFF6FF)
private val Green = Color(0xFF16A34A)
private val GreenSoft = Color(0xFFEAFBF0)
private val ErrorRed = Color(0xFFEF4444)
private val RedSoft = Color(0xFFFFF1F2)
private val Orange = Color(0xFFF97316)
private val Purple = Color(0xFF7C3AED)
private val Navy = Color(0xFF0F2F6E)
private val ShapeL = RoundedCornerShape(20.dp)
private val ShapeM = RoundedCornerShape(14.dp)
private val ShapeS = RoundedCornerShape(10.dp)
