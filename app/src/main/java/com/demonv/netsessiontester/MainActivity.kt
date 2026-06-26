@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.demonv.netsessiontester

import android.Manifest
import android.content.ContentValues
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.History
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
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
import com.demonv.netsessiontester.ui.theme.NetSessionTesterTheme
import com.demonv.netsessiontester.util.CsvExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.abs

private enum class MainTab(val label: String, val mark: String) {
    SETTINGS("设置", "settings"),
    TEST("测试", "play"),
    LOGS("历史", "logs")
}

private enum class ChartMode(val label: String) {
    GROWTH("增长曲线"),
    PEAK("峰值/最高值")
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
    val timeEpochMs: Long = System.currentTimeMillis()
)

private enum class PingProtocolMode(val label: String) {
    AUTO("自动"), IPV4("IPv4"), IPV6("IPv6")
}

private data class PingLogEntry(
    val timeEpochMs: Long = System.currentTimeMillis(),
    val target: String,
    val protocol: String,
    val latencyMs: Int?,
    val status: String,
    val note: String = "",
    val sessionId: Long = 0L
) {
    val timeText: String
        get() = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timeEpochMs))
}

private fun trimPingLogSessions(
    logs: List<PingLogEntry>,
    maxSessions: Int = 5,
    maxEntriesPerSession: Int = 300
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
                    sessionId = obj.optLong("sessionId", 0L)
                ))
            }
        }
    }.getOrDefault(emptyList())
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
    val force: Boolean = false
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
        pkg.versionName?.takeIf { it.isNotBlank() } ?: "V1.0.7"
    }.getOrDefault("V1.0.7")
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
            force = obj.optBoolean("force", false)
        )
    } finally {
        conn.disconnect()
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
        if (code !in 200..299) error("下载失败：HTTP $code")
        val total = conn.contentLengthLong.takeIf { it > 0L } ?: 0L
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
    val localIp: String = "检测中",
    val carrier: String = "未知",
    val natType: String = "检测中",
    val latencyText: String = "检测中",
    val portText: String = "检测中",
    val priority: String = "检测中",
    val mappingBehavior: String = "检测中",
    val filterBehavior: String = "检测中",
    val ipv6Status: String = "检测中",
    val dnsStatus: String = "检测中",
    val confidence: String = "检测中",
    val diagnosis: String = "检测中",
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
    val sourceHost: String = ""
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
    val value = raw.lowercase()
    return when {
        value.isBlank() || value == "unknown" || value == "null" -> "未知"
        "广电" in value || "cbn" in value || "broadnet" in value || "broadcast" in value || "china radio" in value -> "中国广电"
        "移动" in value || "china mobile" in value || "chinamobile" in value || "cmcc" in value || "cmnet" in value -> "中国移动"
        "联通" in value || "china unicom" in value || "unicom" in value || "china169" in value || "cnc" in value -> "中国联通"
        "电信" in value || "chinanet" in value || "china telecom" in value || "telecom" in value || "ctcc" in value -> "中国电信"
        "cernet" in value || "教育网" in value -> "教育网"
        "dr.peng" in value || "dr peng" in value || "鹏博士" in value -> "鹏博士"
        // ASN/IP接口有时会把地址字段误放到 org/isp，例如 “No. 1, Jin Rong Street”。
        // 运营商卡片只显示明确识别到的运营商，避免把街道/公司地址当成运营商。
        value.contains("street") || value.contains("road") || value.contains("building") || value.contains("floor") -> "未知"
        value.contains("jin rong") || value.contains("no. 1") || value.contains("address") -> "未知"
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

private fun detectCarrierFromAsn(ip: String): CarrierDetectionResult? {
    val target = ip.trim()
    if (!target.isUsableIpText()) return null
    return runCatching {
        val conn = (URL("https://ipwho.is/$target?fields=success,connection").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1800
            readTimeout = 2200
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NetSessionTester")
        }
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(body)
            if (!obj.optBoolean("success", false)) return@runCatching null
            val connection = obj.optJSONObject("connection") ?: return@runCatching null
            val samples = listOf(
                connection.optString("isp", ""),
                connection.optString("org", ""),
                connection.optString("asn", "")
            )
            val carrier = samples.asSequence()
                .map { normalizeCarrierName(it) }
                .firstOrNull { it != "未知" }
                ?: "未知"
            carrier.takeIf { it != "未知" }?.let { CarrierDetectionResult(it, "ASN") }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

private fun displayCarrierFromEnv(env: NetworkEnvironment, ipv4: String, ipv6: String, full: Boolean): String {
    val prefixCarrier = inferCarrierFromIpv6Prefix(ipv6)
    val asnCarrier = if (full && env.hasWifi && !env.hasVpn) {
        detectCarrierFromAsn(ipv6).takeIf { it?.carrier != "未知" }
            ?: detectCarrierFromAsn(ipv4).takeIf { it?.carrier != "未知" }
    } else null
    return when {
        env.hasVpn -> "VPN/代理"
        env.hasCellular && env.carrierName.isNotBlank() && env.carrierName != "未知" -> env.carrierName
        asnCarrier != null -> asnCarrier.carrier
        prefixCarrier != "未知" -> prefixCarrier
        env.hasWifi -> "WiFi出口未知"
        ipv4.isUsableIpText() || ipv6.isUsableIpText() -> "出口未知"
        else -> "未知"
    }
}


private suspend fun detectNetworkProbe(
    publicIpResult: PublicIpResult,
    env: NetworkEnvironment,
    targetHost: String,
    targetPort: Int
): NetworkProbeInfo = withContext(Dispatchers.IO) {
    buildNetworkProbeInfo(
        publicIpResult = publicIpResult,
        env = env,
        targetHost = targetHost,
        targetPort = targetPort,
        full = true
    )
}

private suspend fun detectNetworkProbeLight(
    publicIpResult: PublicIpResult,
    env: NetworkEnvironment,
    targetHost: String,
    targetPort: Int
): NetworkProbeInfo = withContext(Dispatchers.IO) {
    buildNetworkProbeInfo(
        publicIpResult = publicIpResult,
        env = env,
        targetHost = targetHost,
        targetPort = targetPort,
        full = false
    )
}

private fun buildNetworkProbeInfo(
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
    val dns = runCatching { dnsProbe(targetHost) }.getOrElse {
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
    val latencyMs = if (full) runCatching { tcpConnectLatencyMs(targetHost, targetPort.coerceIn(1, 65535), 1500) }.getOrNull() else null
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

    val natType = when {
        env.hasVpn -> "代理环境"
        isDirectPublicV4 -> "NAT1 / 开放"
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
        strongSymmetric -> "端口变化"
        weakPortChange -> "端口变化待确认"
        else -> "端口保持"
    }

    val filtering = when {
        env.hasVpn -> "无法准确判断"
        isDirectPublicV4 -> "开放型"
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
    val stunNodeText = if (full && stunTotal > 0) "STUN节点 ${multiStun}/${stunTotal} 成功。${stunRoundText}${stunBackupText}" else ""
    val diagnosis = when {
        env.hasVpn -> "检测到VPN/代理，NAT、出口和IPv6结果仅供参考。"
        dns.fakeIpDetected -> "检测到Fake-IP，DNS可能被代理工具接管。"
        !dns.systemHasAaaa && dns.domesticHasAaaa -> "系统DNS未返回IPv6，国内备用DNS可解析，可能被AdGuard/代理/路由器策略过滤。"
        !dns.systemHasAaaa && dns.globalHasAaaa -> "系统和国内备用DNS未返回IPv6，国外备用DNS可解析，可能存在地区DNS差异或本地DNS策略影响。"
        natType.startsWith("NAT4 / 对称") -> "${stunNodeText}多个可用STUN目标返回的外部端口不一致，当前按对称型/NAT4理解，P2P/游戏联机可能受影响。"
        natType.startsWith("NAT3 / 端口保持") -> "${stunNodeText}多节点端口保持，但未满足6/6节点与2轮稳定条件，当前按端口保持型受限网络显示；完整过滤行为需RFC5780/自建节点验证。"
        natType.startsWith("NAT3 / 受限") && weakPortChange -> "${stunNodeText}UDP基础探测可用，但可用节点不足以确认对称型，当前按受限型理解，建议换网络或再次刷新复测。"
        natType.startsWith("NAT3 / 受限") -> "${stunNodeText}UDP基础探测可用，过滤行为未完成RFC5780验证；完整过滤行为需RFC5780/自建节点验证。"
        natType.startsWith("NAT类型待确认") -> "公网IPv4可用，但STUN基础探测未成功，NAT类型暂按待确认处理。"
        natType.startsWith("UDP") -> "多个STUN基础请求均失败，当前仅能判断为UDP受限/无法判断，可能是UDP被防火墙、代理或运营商限制。"
        natType.startsWith("NAT2") -> "UDP映射较稳定，普通联机能力中等。"
        natType.startsWith("NAT1") -> "${stunNodeText}6/6节点与2轮复测均保持同一公网端口，按兼容口径判定为 NAT1；完整过滤行为需 RFC5780 / 自建节点验证。"
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
        return representative.copy(
            successCount = uniqueSuccess,
            totalCount = totalCount,
            mappedPorts = allResults.map { it.mappedPort }.toSet(),
            mappedIps = allResults.map { it.mappedIp }.toSet(),
            roundCount = roundResults.size,
            stableRoundCount = stableRoundCount,
            usedBackup = usedBackup
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
    return InetAddress.getAllByName(host).firstOrNull { it is Inet4Address }
        ?: error("STUN服务器无IPv4地址")
}

private fun buildStunBindingRequest(): StunRequest {
    val tx = ByteArray(12)
    synchronized(stunRandom) { stunRandom.nextBytes(tx) }
    val req = ByteArray(20)
    req[1] = 0x01.toByte()
    req[4] = 0x21.toByte()
    req[5] = 0x12.toByte()
    req[6] = 0xA4.toByte()
    req[7] = 0x42.toByte()
    System.arraycopy(tx, 0, req, 8, 12)
    return StunRequest(req, tx)
}

private fun parseStunMappedAddress(data: ByteArray, length: Int, localPort: Int, transactionId: ByteArray): StunProbeResult? {
    if (length < 20) return null
    val messageType = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
    if (messageType != 0x0101) return null
    if (data[4] != 0x21.toByte() || data[5] != 0x12.toByte() || data[6] != 0xA4.toByte() || data[7] != 0x42.toByte()) return null
    for (i in 0 until 12) {
        if (data[8 + i] != transactionId[i]) return null
    }
    var pos = 20
    while (pos + 4 <= length) {
        val type = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        val attrLen = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
        val value = pos + 4
        if (value + attrLen <= length && attrLen >= 8) {
            val family = data[value + 1].toInt() and 0xFF
            if (family == 0x01 && (type == 0x0020 || type == 0x0001)) {
                val rawPort = ((data[value + 2].toInt() and 0xFF) shl 8) or (data[value + 3].toInt() and 0xFF)
                val mappedPort = if (type == 0x0020) rawPort xor 0x2112 else rawPort
                val ipBytes = ByteArray(4)
                val magic = byteArrayOf(0x21.toByte(), 0x12.toByte(), 0xA4.toByte(), 0x42.toByte())
                for (i in 0..3) {
                    val b = data[value + 4 + i].toInt()
                    ipBytes[i] = if (type == 0x0020) ((b xor magic[i].toInt()) and 0xFF).toByte() else (b and 0xFF).toByte()
                }
                val mappedIp = InetAddress.getByAddress(ipBytes).hostAddress ?: return null
                return StunProbeResult(mappedIp, mappedPort, localPort)
            }
        }
        pos += 4 + ((attrLen + 3) / 4) * 4
    }
    return null
}


private fun dnsProbe(host: String): DnsProbeResult {
    val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
    val system = runCatching { InetAddress.getAllByName(cleanHost).toList() }.getOrDefault(emptyList())
    val systemHasA = system.any { it is Inet4Address }
    val systemHasAaaa = system.any { it is Inet6Address }
    val fakeIp = system.filterIsInstance<Inet4Address>().any { isFakeIpAddress(it.hostAddress.orEmpty()) }

    val domesticHasAaaa = if (!systemHasAaaa) {
        listOf("223.5.5.5", "119.29.29.29").any { server ->
            runCatching { dnsQueryHasAaaa(cleanHost, server) }.getOrDefault(false)
        }
    } else {
        false
    }

    val globalHasAaaa = if (!systemHasAaaa && !domesticHasAaaa) {
        listOf("1.1.1.1", "8.8.8.8").any { server ->
            runCatching { dnsQueryHasAaaa(cleanHost, server) }.getOrDefault(false)
        }
    } else {
        false
    }

    return DnsProbeResult(
        systemHasA = systemHasA,
        systemHasAaaa = systemHasAaaa,
        domesticHasAaaa = domesticHasAaaa,
        globalHasAaaa = globalHasAaaa,
        fakeIpDetected = fakeIp
    )
}

private fun isFakeIpAddress(ip: String): Boolean {
    val p = ip.split('.').mapNotNull { it.toIntOrNull() }
    return p.size == 4 && p[0] == 198 && p[1] in 18..19
}

private fun dnsQueryHasAaaa(host: String, server: String): Boolean {
    val txId = (System.nanoTime().toInt() and 0xFFFF)
    val query = buildDnsQuery(host, txId, qType = 28)
    DatagramSocket().use { socket ->
        socket.soTimeout = 1200
        socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
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

private suspend fun icmpPingMs(host: String, timeoutMs: Int, protocol: PingProtocolMode): Int? = withContext(Dispatchers.IO) {
    runCatching {
        val timeoutSec = ((timeoutMs.coerceIn(300, 10_000) + 999) / 1000).coerceAtLeast(1)
        val command = when (protocol) {
            PingProtocolMode.IPV6 -> listOf("ping6", "-c", "1", "-W", timeoutSec.toString(), host)
            PingProtocolMode.IPV4 -> listOf("ping", "-4", "-c", "1", "-W", timeoutSec.toString(), host)
            PingProtocolMode.AUTO -> listOf("ping", "-c", "1", "-W", timeoutSec.toString(), host)
        }
        val startedAt = System.nanoTime()
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val finished = process.waitFor((timeoutMs + 800).toLong(), TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching null
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val regex = Regex("time[=<]([0-9.]+)\\s*ms")
        val parsed = regex.find(output)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.roundToInt()
        parsed ?: if (process.exitValue() == 0) ((System.nanoTime() - startedAt) / 1_000_000L).toInt().coerceAtLeast(1) else null
    }.getOrNull()
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
    val tester = remember { TcpTester() }
    val historyStore = remember { HistoryStore(context.applicationContext) }
    val logStore = remember { LogStore(context.applicationContext) }
    val settingsStore = remember { SettingsStore(context.applicationContext) }

    var selectedTab by remember { mutableStateOf(MainTab.SETTINGS) }
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
    var pingJob by remember { mutableStateOf<Job?>(null) }
    var pingIntervalLabel by remember { mutableStateOf("停止") }
    var pingRunning by remember { mutableStateOf(false) }
    var pingLogs by remember { mutableStateOf<List<PingLogEntry>>(emptyList()) }
    var networkWatchJob by remember { mutableStateOf<Job?>(null) }
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
        if (clean.isBlank() || clean == "不可用" || clean == "检测中") {
            scope.launch { snackbarHostState.showSnackbar("$label 暂不可复制") }
            return
        }
        clipboardManager.setText(AnnotatedString(clean))
        scope.launch { snackbarHostState.showSnackbar("已复制$label：$clean") }
    }


    fun safeHistoryLimit(): Int = historyLimit.toIntOrNull()?.coerceIn(10, 100) ?: 30

    fun refreshHistory() {
        scope.launch {
            state = state.copy(history = historyStore.load(historyPeriod, safeHistoryLimit()))
            historySizeKb = historyStore.sizeKb()
            historySavedCount = historyStore.count()
            historyCounts = historyStore.counts()
        }
    }

    fun refreshPublicIp() {
        if (publicIpLoading) return
        scope.launch {
            publicIpLoading = true
            try {
                val result = runCatching { PublicIpDetector.detect() }.getOrElse { PublicIpResult() }
                publicIpResult = result
                networkProbeInfo = runCatching {
                    detectNetworkProbe(result, detectNetworkEnvironment(context), host.ifBlank { "www.baidu.com" }, port.toIntOrNull() ?: 80)
                }.getOrElse { error ->
                    NetworkProbeInfo(
                        localIp = localIpv4Addresses().firstOrNull() ?: localIpv6Addresses().firstOrNull() ?: "不可用",
                        natType = "检测失败",
                        latencyText = "不可用",
                        portText = "不可用",
                        priority = "未知",
                        mappingBehavior = "未知",
                        filterBehavior = "未知",
                        ipv6Status = "不可用",
                        dnsStatus = "检测失败",
                        confidence = "低",
                        diagnosis = error.message ?: "网络信息检测失败"
                    )
                }
                lastNetworkInfoSignature = currentNetworkSignature(context)
            } finally {
                publicIpLoading = false
            }
        }
    }

    fun refreshNetworkInfoLight() {
        refreshPublicIp()
    }

    LaunchedEffect(Unit) {
        state = state.copy(history = historyStore.load(historyPeriod, 30), logs = logStore.load())
        pingLogs = loadPingLogs(context.applicationContext)
        logSizeKb = logStore.sizeKb()
        historySizeKb = historyStore.sizeKb()
        historySavedCount = historyStore.count()
        historyCounts = historyStore.counts()
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
        state = state.copy(history = historyStore.load(historyPeriod, historyLimit.toIntOrNull()?.coerceIn(10, 100) ?: 30))
        historySavedCount = historyStore.count()
        historyCounts = historyStore.counts()
        settingsLoaded = true
        lastNetworkInfoSignature = currentNetworkSignature(context)
        refreshPublicIp()
    }

    LaunchedEffect(settingsLoaded) {
        if (settingsLoaded) {
            lastNetworkInfoSignature = currentNetworkSignature(context)
            while (true) {
                delay(2_000L)
                val nowSignature = currentNetworkSignature(context)
                if (nowSignature != lastNetworkInfoSignature) {
                    if (!state.isAdding && !finishInProgress && !publicIpLoading) {
                        refreshPublicIp()
                    }
                }
            }
        }
    }

    LaunchedEffect(
        settingsLoaded, host, port, mode, batchSize, intervalMs, timeoutMs,
        successLimit, failureLimit, keepConnections, maskPrivacy, historyLimit,
        pingEnabled, pingTarget, pingIntervalSetting, pingCountSetting, pingTimeoutSetting, pingProtocolSetting
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
                    pingProtocol = pingProtocolSetting.name
                )
            )
        }
    }

    fun appendLog(line: LogLine) {
        state = state.copy(logs = (state.logs + line).takeLast(500))
        scope.launch { logStore.append(line); logSizeKb = logStore.sizeKb() }
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
                    updateAvailable = hasNew && !ignored
                    if (hasNew && (manual || !ignored)) {
                        showVersionDialog = false
                        showUpdateDialog = true
                    } else if (hasNew && ignored) {
                        showUpdateDialog = false
                    } else if (manual) {
                        snackbarHostState.showSnackbar("已是最新版本")
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
                    updateAvailable = hasNew && !ignored
                }
            }
        }
    }

    fun resetCurrentCharts() {
        chartPoints = emptyList()
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

    fun appendPingSecond(sec: Int, samples: List<Int?>) {
        val valid = samples.mapNotNull { it }
        val avg = valid.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val lossCount = samples.count { it == null }
        val highLatency = valid.any { it >= 100 }
        pingPoints = (pingPoints.filterNot { it.elapsedSec == sec } + PingPoint(
            elapsedSec = sec,
            latencyMs = avg,
            lossCount = lossCount,
            highLatency = highLatency,
            sampleCount = samples.size.coerceAtLeast(1)
        ))
            .sortedBy { it.elapsedSec }
            .takeLast(360)
    }

    fun alignPingWithSessionEnd() {
        val started = currentStartedAt
        if (started <= 0L) return
        val elapsed = ((System.currentTimeMillis() - started) / 1_000L).toInt().coerceAtLeast(0)
        val last = pingPoints.lastOrNull()
        if (last != null && last.elapsedSec < elapsed) {
            pingPoints = (pingPoints + last.copy(elapsedSec = elapsed)).takeLast(360)
        }
    }

    fun safePingIntervalMs(): Long = pingIntervalSetting.toLongOrNull()?.coerceIn(200L, 60_000L) ?: 1_000L

    fun safePingTimeoutMs(): Int = pingTimeoutSetting.toIntOrNull()?.coerceIn(300, 10_000) ?: 1_000

    fun safePingCount(): Int? {
        val clean = pingCountSetting.trim()
        if (clean.isBlank() || clean == "无限") return null
        return clean.toIntOrNull()?.coerceIn(1, 100_000)
    }

    fun appendPingLog(entry: PingLogEntry) {
        val next = trimPingLogSessions(pingLogs + entry)
        pingLogs = next
        savePingLogs(context.applicationContext, next)
    }

    fun appendPingLogs(entries: List<PingLogEntry>) {
        if (entries.isEmpty()) return
        val next = trimPingLogSessions(pingLogs + entries)
        pingLogs = next
        savePingLogs(context.applicationContext, next)
    }

    fun appendPingPoint(sec: Int, latencyMs: Int?) {
        pingPoints = (pingPoints.filterNot { it.elapsedSec == sec } + PingPoint(
            elapsedSec = sec,
            latencyMs = latencyMs,
            lossCount = if (latencyMs == null) 1 else 0,
            highLatency = (latencyMs ?: 0) >= 100
        ))
            .sortedBy { it.elapsedSec }
            .takeLast(360)
    }

    fun startPingMonitor(reset: Boolean = false) {
        val target = pingTarget.trim().ifBlank { host.ifBlank { "223.5.5.5" } }
        val interval = safePingIntervalMs()
        val timeout = safePingTimeoutMs()
        val maxCount = safePingCount()
        val protocol = pingProtocolSetting
        pingJob?.cancel()
        if (reset) {
            pingPoints = emptyList()
        }
        pingRunning = true
        pingIntervalLabel = "${protocol.label} · ${interval}ms"
        val sessionId = System.currentTimeMillis()
        appendPingLog(PingLogEntry(
            timeEpochMs = sessionId,
            target = target,
            protocol = protocol.label,
            latencyMs = null,
            status = "开始",
            note = "间隔${interval}ms · 超时${timeout}ms",
            sessionId = sessionId
        ))
        pingJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            var sent = 0
            var currentSec = 0
            val secSamples = mutableListOf<Int?>()
            val pendingLogs = mutableListOf<PingLogEntry>()

            fun flushSecond(sec: Int) {
                if (secSamples.isNotEmpty()) {
                    appendPingSecond(sec, secSamples.toList())
                    secSamples.clear()
                }
                if (pendingLogs.isNotEmpty()) {
                    appendPingLogs(pendingLogs.toList())
                    pendingLogs.clear()
                }
            }

            try {
                while (currentCoroutineContext().isActive && (maxCount == null || sent < maxCount)) {
                    val loopStart = System.currentTimeMillis()
                    val elapsed = ((loopStart - startedAt) / 1_000L).toInt().coerceAtLeast(0)
                    if (elapsed != currentSec) {
                        flushSecond(currentSec)
                        currentSec = elapsed
                    }
                    val latency = icmpPingMs(target, timeout, protocol)
                    sent++
                    secSamples.add(latency)
                    val status = when {
                        latency == null -> "超时"
                        latency >= 100 -> "高延迟"
                        else -> "成功"
                    }
                    pendingLogs.add(PingLogEntry(
                        target = target,
                        protocol = protocol.label,
                        latencyMs = latency,
                        status = status,
                        note = if (latency == null) "timeout" else "",
                        sessionId = sessionId
                    ))
                    val cost = System.currentTimeMillis() - loopStart
                    delay((interval - cost).coerceAtLeast(0L))
                }
            } finally {
                flushSecond(currentSec)
                pingRunning = false
                pingIntervalLabel = if (sent > 0) "已停止 · ${sent}次" else "停止"
                appendPingLog(PingLogEntry(target = target, protocol = protocol.label, latencyMs = null, status = "停止", note = "共${sent}次", sessionId = sessionId))
            }
        }
    }

    fun stopPingMonitor() {
        pingJob?.cancel()
        pingJob = null
        pingRunning = false
        pingIntervalLabel = "已停止"
    }

    fun clearPingData() {
        pingPoints = emptyList()
        pingLogs = emptyList()
        savePingLogs(context.applicationContext, emptyList())
        pingIntervalLabel = if (pingRunning) "${pingProtocolSetting.label} · ${safePingIntervalMs()}ms" else "停止"
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun buildConfig(): SessionConfig? {
        val config = runCatching {
            SessionConfig(
                host = host.ifBlank { "www.baidu.com" },
                port = port.toIntOrNull() ?: 80,
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
                tester.closeDetachedSockets(snapshot, batchSize = 1000) { done, total, elapsedMs ->
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
        // Ping 已独立运行：如果用户开启 Ping 且当前未运行，则启动；会话测试不再重置 Ping 数据。
        if (pingEnabled && pingJob?.isActive != true) startPingMonitor(reset = false)
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
            historyCounts = historyStore.counts()
            historySavedCount = historyStore.count()
            historySizeKb = historyStore.sizeKb()
            state = state.copy(history = historyStore.load(historyPeriod, safeLimit))
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
                            state = state.copy(history = historyStore.load(historyPeriod, safeHistoryLimit))
                            historyCounts = historyStore.counts()
                            historySavedCount = historyStore.count()
                            historySizeKb = historyStore.sizeKb()
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
            onDismiss = { showDownloadDialog = false },
            onInstall = { installReadyApk() },
            onRetry = { latestUpdate?.let { startUpdateDownload(it) } },
            onCancelDownload = { cancelUpdateDownload() },
            onOpenGithub = { openGithub(latestUpdate?.githubUrl ?: PROJECT_GITHUB_URL) }
        )
    }


    Scaffold(
        snackbarHost = { OneUiSnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!showRunLogDetail) {
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
            } else when (selectedTab) {
                MainTab.SETTINGS -> SettingsPage(
                    host = host,
                    onHostChange = { host = it },
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
                    maskPrivacy = maskPrivacy,
                    onMaskPrivacyChange = { maskPrivacy = it },
                    onResolve = { resolve() },
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
                    pingIntervalMs = pingIntervalSetting,
                    onPingIntervalMsChange = { pingIntervalSetting = it },
                    pingCount = pingCountSetting,
                    onPingCountChange = { pingCountSetting = it },
                    pingTimeoutMs = pingTimeoutSetting,
                    onPingTimeoutMsChange = { pingTimeoutSetting = it },
                    pingProtocol = pingProtocolSetting,
                    onPingProtocolChange = { pingProtocolSetting = it },
                    updateBadge = updateAvailable || downloadUi.active || downloadUi.finished,
                    updateProgress = if (downloadUi.active) downloadUi.progress else null,
                    onVersionClick = { showVersionDialog = true },
                    onSave = { scope.launch { snackbarHostState.showSnackbar("参数已保存") } },
                    onRestoreDefault = {
                        host = "www.baidu.com"; port = "80"; mode = TestMode.IPV4_THEN_IPV6
                        batchSize = "200"; intervalMs = "100"; timeoutMs = "1200"
                        successLimit = "65535"; failureLimit = "600"; keepConnections = true; maskPrivacy = false; historyLimit = "30"
                        pingEnabled = true; pingTarget = "223.5.5.5"; pingIntervalSetting = "1000"; pingCountSetting = "无限"; pingTimeoutSetting = "1000"; pingProtocolSetting = PingProtocolMode.AUTO
                    }
                )

                MainTab.TEST -> TestPage(
                    mode = mode,
                    status = state.status,
                    target = successLimit,
                    testHost = currentTestConfig?.host ?: host,
                    testPort = (currentTestConfig?.port ?: port.toIntOrNull() ?: 80),
                    chartMode = chartMode,
                    onChartModeChange = { chartMode = it },
                    chartPoints = chartPoints,
                    pingPoints = pingPoints,
                    pingIntervalLabel = pingIntervalLabel,
                    pingRunning = pingRunning,
                    pingLogs = pingLogs,
                    onStartPing = { startPingMonitor(reset = true) },
                    onStopPing = { stopPingMonitor() },
                    onClearPing = { clearPingData() },
                    onShowPingLog = { showPingLogDialog = true },
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
                            historySizeKb = historyStore.sizeKb()
                            historySavedCount = historyStore.count()
                            historyCounts = historyStore.counts()
                            state = state.copy(history = historyStore.load(historyPeriod, safeLimit))
                        }
                    },
                    onHistoryPeriodChange = { period ->
                        historyPeriod = period
                        scope.launch {
                            state = state.copy(history = historyStore.load(period, safeHistoryLimit()))
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

            if (downloadUi.active || downloadUi.finished || downloadUi.failed) {
                UpdateDownloadBanner(
                    state = downloadUi,
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp),
                    onOpen = { showDownloadDialog = true },
                    onInstall = { installReadyApk() }
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
            SectionTitle("▮", title, Blue)
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

private fun formatPingLogGroupTitle(group: PingLogGroup): String {
    val time = DateTimeFormatter.ofPattern("今天 HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(group.startedAt))
    val note = group.headerNote.ifBlank { "" }
    return listOf(time, group.target, group.protocol, note).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun PingLogDialog(logs: List<PingLogEntry>, onDismiss: () -> Unit) {
    val groups = buildPingLogGroups(logs).take(5)
    val latest = groups.firstOrNull()
    val latestEntries = latest?.entries.orEmpty().filter { it.status != "开始" }
    val success = latestEntries.count { it.status == "成功" || it.status == "高延迟" }
    val timeout = latestEntries.count { it.status == "超时" }
    val avg = latestEntries.mapNotNull { it.latencyMs }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val high = latestEntries.mapNotNull { it.latencyMs }.maxOrNull()
    val low = latestEntries.mapNotNull { it.latencyMs }.minOrNull()
    var expandedSession by remember(groups.firstOrNull()?.sessionId) { mutableStateOf(groups.firstOrNull()?.sessionId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) }
        },
        title = { Text("Ping 响应日志", fontWeight = FontWeight.Bold, color = TextDark) },
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
                            Text("${latest.target} · ${latest.protocol}", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Text("最近 5 次记录", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
    onToggle: () -> Unit
) {
    val items = group.entries.filter { it.status != "开始" }
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
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(formatPingLogGroupTitle(group), color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${items.size}次 · 成功$success · 平均${avg?.let { "${it}ms" } ?: "—"} · 丢包$loss%", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "⌃" else "⌄", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items.asReversed().take(100).forEach { entry -> PingLogRow(entry) }
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
        add("时间        协议   延迟     状态     目标/备注")
        logs.takeLast(300).asReversed().forEach { item ->
            val latency = item.latencyMs?.let { "${it}ms" } ?: "—"
            val note = item.note.ifBlank { item.target }
            add("${item.timeText}  ${item.protocol.padEnd(4)}  ${latency.padEnd(7)}  ${item.status.padEnd(4)}  $note")
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
        CleanField(host, onHostChange, "www.baidu.com", leadingMark = "host")
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
                    Icon(iconFor("privacy"), contentDescription = null, tint = Blue, modifier = Modifier.width(18.dp).height(18.dp))
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
    val thresholdPx = with(density) { 170.dp.toPx() }
    var lastReorderAt by remember { mutableStateOf(0L) }
    val isDragging = draggingId == id
    val scale by animateFloatAsState(if (isDragging) 1.014f else 1f, tween(220), label = "dragScale")
    val elevation by animateFloatAsState(if (isDragging) 20f else 0f, tween(220), label = "dragElevation")

    Box(
        modifier = modifier
            .zIndex(if (isDragging) 20f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
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
                            if (now - lastReorderAt >= 240L) {
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
    host: String,
    onHostChange: (String) -> Unit,
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
    val defaultCards = listOf("target", "network", "session", "ping")
    var settingsCardOrder by remember { mutableStateOf(loadCardOrder(context.applicationContext, "settings_card_order_v2", defaultCards)) }
    fun updateSettingsOrder(next: List<String>) {
        val clean = (next.filter { it in defaultCards } + defaultCards.filterNot { it in next }).distinct()
        settingsCardOrder = clean
        saveCardOrder(context.applicationContext, "settings_card_order_v2", clean)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { PageTitle("宽带会话测试器", "TCP 会话测试 · IPv4 / IPv6 分别测试", updateBadge, updateProgress, onVersionClick) }
        items(settingsCardOrder, key = { it }) { cardId ->
            ReorderableCardItem(
                id = cardId,
                order = settingsCardOrder,
                onOrderChange = ::updateSettingsOrder
            ) {
                when (cardId) {
                    "target" -> TargetAndModeCard(
                        host = host,
                        onHostChange = onHostChange,
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
                        onCopyPublicIpv6 = onCopyPublicIpv6
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
                        Text("V1.0.7 正式版：NAT判定收紧、运营商识别修复、Ping与拖动体验优化。", color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    "ping" -> SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MarkBox("ping", BlueSoft, Blue)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("独立 Ping", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 13.sp)
                                Text("可独立启动/停止，支持响应日志", color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                            Switch(checked = pingEnabled, onCheckedChange = onPingEnabledChange)
                        }
                        FieldLabel("Ping 目标")
                        CleanField(pingTarget, onPingTargetChange, "223.5.5.5", leadingMark = "target")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ParamField("间隔ms", pingIntervalMs, { onPingIntervalMsChange(it.onlyDigits()) }, Modifier.weight(1f), leadingMark = "time")
                            ParamField("超时ms", pingTimeoutMs, { onPingTimeoutMsChange(it.onlyDigits()) }, Modifier.weight(1f), leadingMark = "time")
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
                                leadingMark = "privacy",
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
                            if ((pingIntervalMs.toIntOrNull() ?: 1000) < 1000) "高频 Ping 已启用，界面将按秒聚合显示。" else "会话测试期间 Ping 仍可独立运行；高频 Ping 建议间隔不低于 1000ms。",
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
    pingRunning: Boolean,
    pingLogs: List<PingLogEntry>,
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
    val defaultCards = listOf("control", "release", "ping", "ipv4", "ipv6", "diagnosis", "logs")
    var testCardOrder by remember { mutableStateOf(loadCardOrder(context.applicationContext, "test_card_order_v2", defaultCards)) }
    val visibleIds = buildList {
        add("control")
        if (releaseUi.visible || releaseBusy) add("release")
        add("ping")
        if (showIpv4) add("ipv4")
        if (showIpv6) add("ipv6")
        add("diagnosis")
        add("logs")
    }
    val visibleOrder = (testCardOrder.filter { it in visibleIds } + visibleIds.filterNot { it in testCardOrder }).distinct()
    fun updateTestOrder(nextVisible: List<String>) {
        val merged = (nextVisible + testCardOrder.filterNot { it in nextVisible } + defaultCards.filterNot { it in nextVisible || it in testCardOrder }).distinct()
        testCardOrder = merged
        saveCardOrder(context.applicationContext, "test_card_order_v2", merged)
    }

    LazyColumn(
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
                    "control" -> SoftCard {
                        SectionTitle("∿", "测试控制", Blue)
                        Text(
                            when {
                                releaseBusy -> "正在释放连接，完成后会自动恢复开始按钮"
                                isAdding -> "正在运行 · 定速发射 · UI/曲线每秒采样"
                                else -> "状态：$status"
                            },
                            color = if (isAdding) Green else if (releaseBusy) Orange else Muted,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onStart, enabled = startEnabled, modifier = Modifier.weight(1f).height(40.dp), shape = ShapeM) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("开始", fontSize = 13.sp)
                            }
                            Button(
                                onClick = onStopAdding,
                                enabled = stopEnabled,
                                colors = ButtonDefaults.buttonColors(containerColor = RedSoft, contentColor = ErrorRed),
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = ShapeM
                            ) { Icon(Icons.Filled.Stop, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("停止", fontSize = 13.sp) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onRelease, enabled = !releaseBusy, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) { Icon(Icons.Filled.DeleteOutline, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("强制释放", fontSize = 13.sp) }
                            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) { Icon(Icons.Filled.Download, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("导出", fontSize = 13.sp) }
                        }
                    }
                    "release" -> ReleaseProgressCard(releaseUi)
                    "ping" -> PingCompactChartCard(
                        pingPoints = pingPoints,
                        intervalLabel = pingIntervalLabel,
                        running = pingRunning,
                        logCount = pingLogs.size,
                        onStart = onStartPing,
                        onStop = onStopPing,
                        onClear = onClearPing,
                        onLog = onShowPingLog
                    )
                    "ipv4" -> SessionStatsCard("IPv4 会话", ipv4Stats, maskPrivacy, chartPoints.filter { it.protocol == IpProtocol.IPV4 }, chartMode, onChartModeChange)
                    "ipv6" -> SessionStatsCard("IPv6 会话", ipv6Stats, maskPrivacy, chartPoints.filter { it.protocol == IpProtocol.IPV6 }, chartMode, onChartModeChange)
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
                    Box(modifier = Modifier.clickable { onHistoryLimitChange(limit) }) {
                        StatusChip("显示 ${limit} 条", if (historyLimit == limit) BlueSoft else Color.White, if (historyLimit == limit) Blue else Muted, compact = true)
                    }
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
    Card(
        shape = ShapeM,
        colors = CardDefaults.cardColors(containerColor = if (selected) bg else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 1.dp else 0.dp),
        modifier = Modifier.width(104.dp).clickable(onClick = onClick)
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
                VersionLine(displayVersionName(currentAppVersionName(LocalContext.current)), "正式版：补强目标与模式样式、独立Ping标题和长时间Ping曲线滚动显示。")
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
    onInstall: () -> Unit
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
    Card(
        modifier = modifier
            .fillMaxWidth()
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
                        state.finished -> "点击安装"
                        state.failed -> state.message.ifBlank { "点击查看" }
                        state.active -> "${state.progress}% · ${formatBytes(state.downloadedBytes)} / ${if (state.totalBytes > 0) formatBytes(state.totalBytes) else "未知大小"} · ${formatSpeed(state.speedBytesPerSecond)}"
                        else -> "点击查看"
                    }
                    Text(detail, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (state.finished) "安装" else "查看", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        Icon(iconFor(mark), contentDescription = null, tint = fg, modifier = Modifier.width(16.dp).height(16.dp))
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
            { Icon(iconFor(mark), contentDescription = null, tint = Blue, modifier = Modifier.width(18.dp).height(18.dp)) }
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        shape = ShapeM,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().height(52.dp)
    )
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
            { Icon(iconFor(mark), contentDescription = null, tint = Blue, modifier = Modifier.width(16.dp).height(16.dp)) }
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
            leadingIcon = { Icon(iconFor(leadingMark), contentDescription = null, tint = Blue, modifier = Modifier.width(16.dp).height(16.dp)) },
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
            Icon(iconFor(leadingMark), contentDescription = null, tint = Blue, modifier = Modifier.width(18.dp).height(18.dp))
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
            .background(Color(0xFFF8FAFC), ShapeM)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val modes = listOf(TestMode.IPV4_ONLY, TestMode.IPV6_ONLY, TestMode.IPV4_THEN_IPV6)
        modes.forEach { item ->
            val selected = mode == item
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) Color.White else Color.Transparent, ShapeM)
                    .clickable { onModeChange(item) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(item.label, color = if (selected) Blue else Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
            }
        }
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
            SectionTitle("▮", title, Blue)
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
            Box(
                modifier = Modifier
                    .background(if (selected) Color(0xFFEBDCFD) else Color.White, ShapeM)
                    .clickable { onChartModeChange(mode) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(mode.label, color = if (selected) TextDark else Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
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
    val sorted = points.sortedBy { it.elapsedSec }
    val showFailureMiniCurve = shouldShowFailureMiniCurve(sorted)
    val minX = sorted.firstOrNull()?.elapsedSec ?: 0
    val maxXRaw = sorted.lastOrNull()?.elapsedSec ?: (minX + 1)
    val maxX = maxOf(minX + 1, maxXRaw)
    val maxSessionY = sessionYAxisMax(sorted.maxOfOrNull { it.active } ?: 0)
    val step = chartXAxisStep(maxX - minX)
    val last = sorted.lastOrNull()
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
                axisLabels(maxSessionY).forEach { Text(it.toString(), color = Muted, fontSize = 9.sp, maxLines = 1) }
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
            timeLabels(minX, maxX, step).forEach { Text("${it}s", color = Muted, fontSize = 10.sp) }
        }
        val failSummary = failureIntervalSummary(sorted)
        if (failSummary.isNotBlank()) {
            Text(failSummary, color = ErrorRed, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text("说明：CPS仅在上方文字显示；图表只保留会话数蓝线，失败仅显示区间文字。", color = Muted, fontSize = 10.sp, lineHeight = 13.sp)
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
    onCopyPublicIpv6: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("i", "网络信息", Purple)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text(if (publicIpLoading) "检测中" else "刷新", fontSize = 12.sp) }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.width(32.dp).height(32.dp)) {
                Text(if (expanded) "⌃" else "⌄", color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val ipv4Text = if (maskPrivacy) maskIpText(publicIpResult.ipv4) else publicIpResult.ipv4
                val ipv6Text = if (maskPrivacy) maskIpText(publicIpResult.ipv6) else publicIpResult.ipv6
                InfoMetricTile("◎", "IPv4", ipv4Text, BlueSoft, Blue, Modifier.weight(1f), onClick = onCopyPublicIpv4)
                InfoMetricTile("✓", "IPv6", ipv6Text, GreenSoft, Green, Modifier.weight(1f), onClick = onCopyPublicIpv6)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                InfoMetricTile("N", "NAT", probeInfo.natType, Color(0xFFFFE4E6), ErrorRed, Modifier.weight(1f))
                InfoMetricTile("C", "运营商", probeInfo.carrier, Color(0xFFF3E8FF), Purple, Modifier.weight(1f))
            }
            Text("点击右上角展开查看 STUN、DNS、映射和过滤详情。", color = Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            return@SoftCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoMetricTile("⌂", "本地IP", if (maskPrivacy) maskIpText(probeInfo.localIp) else probeInfo.localIp, Color(0xFFE0F7FA), Color(0xFF00A7C6), Modifier.weight(1f))
            InfoMetricTile("◴", "延迟", probeInfo.latencyText, Color(0xFFFFF3E0), Orange, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoMetricTile("◎", "公网IPv4", if (maskPrivacy) maskIpText(publicIpResult.ipv4) else publicIpResult.ipv4, BlueSoft, Blue, Modifier.weight(1f), onClick = onCopyPublicIpv4)
            val ipv6Display = if (publicIpResult.ipv6.isUsableIpText()) publicIpResult.ipv6 else probeInfo.ipv6Status
            InfoMetricTile("✓", "公网IPv6", if (maskPrivacy) maskIpText(ipv6Display) else ipv6Display, GreenSoft, Green, Modifier.weight(1f), onClick = onCopyPublicIpv6)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val mapped = if (publicIpResult.ipv4.isUsableIpText() && probeInfo.portText != "不可用" && probeInfo.portText != "待检测") "${publicIpResult.ipv4}:${probeInfo.portText}" else probeInfo.portText
            InfoMetricTile("M", "公网映射", if (maskPrivacy) maskIpText(mapped) else mapped, Color(0xFFE0F2FE), Blue, Modifier.weight(1f))
            InfoMetricTile("N", "NAT类型", probeInfo.natType, Color(0xFFFFE4E6), ErrorRed, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoMetricTile("↕", "优先级", probeInfo.priority, Color(0xFFFFF3E0), Orange, Modifier.weight(1f))
            InfoMetricTile("M", "映射行为", probeInfo.mappingBehavior, Color(0xFFE0F2FE), Blue, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoMetricTile("F", "过滤行为", probeInfo.filterBehavior, Color(0xFFFCE7F3), Color(0xFFD946EF), Modifier.weight(1f))
            InfoMetricTile("D", "DNS诊断", probeInfo.dnsStatus, Color(0xFFF3E8FF), Purple, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            InfoMetricTile("C", "NAT置信度", probeInfo.confidence, Color(0xFFF8FAFC), Muted, Modifier.weight(1f))
            InfoMetricTile("C", "运营商", probeInfo.carrier, Color(0xFFF3E8FF), Purple, Modifier.weight(1f))
        }
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

@Composable
private fun InfoMetricTile(
    icon: String,
    label: String,
    value: String,
    iconBg: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 58.dp)
            .background(Color(0xFFF8FAFC), ShapeM)
            .then(clickableModifier)
            .padding(9.dp)
    ) {
        MarkBox(icon, iconBg, iconColor)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
            Text(
                value,
                color = TextDark,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun PingCompactChartCard(
    pingPoints: List<PingPoint>,
    intervalLabel: String = "停止",
    running: Boolean,
    logCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onLog: () -> Unit
) {
    val successes = pingPoints.mapNotNull { it.latencyMs }
    val current = successes.lastOrNull()
    val avg = successes.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val max = successes.maxOrNull()
    val min = successes.minOrNull()
    val loss = if (pingPoints.isNotEmpty()) ((pingPoints.count { it.latencyMs == null } * 100f) / pingPoints.size).roundToInt() else 0
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("∿", "Ping 测试", Blue)
            Spacer(Modifier.width(8.dp))
            StatusChip(if (running) "运行中" else intervalLabel, if (running) GreenSoft else BlueSoft, if (running) Green else Blue, compact = true)
            Spacer(Modifier.weight(1f))
            StatusChip(if (loss == 0) "● 正常" else "丢包 $loss%", if (loss == 0) GreenSoft else RedSoft, if (loss == 0) Green else ErrorRed, compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            MiniMetric("当前", current?.let { "${it}ms" } ?: "—", Blue, Modifier.weight(1f))
            MiniMetric("平均", avg?.let { "${it}ms" } ?: "—", Navy, Modifier.weight(1f))
            MiniMetric("最高", max?.let { "${it}ms" } ?: "—", Orange, Modifier.weight(1f))
            MiniMetric("最低", min?.let { "${it}ms" } ?: "—", Green, Modifier.weight(1f))
            MiniMetric("丢包", "$loss%", if (loss == 0) Muted else ErrorRed, Modifier.weight(1f))
        }
        PingLineChart(pingPoints)
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
                Text("日志 $logCount", fontSize = 12.sp)
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
private fun PingLineChart(points: List<PingPoint>) {
    val allPoints = points.sortedBy { it.elapsedSec }
    val windowEnd = maxOf(1, allPoints.lastOrNull()?.elapsedSec ?: 1)
    val windowStart = if (windowEnd > 120) windowEnd - 120 else 0
    val sorted = allPoints.filter { it.elapsedSec in windowStart..windowEnd }
    val values = sorted.mapNotNull { it.latencyMs }
    val maxY = pingYAxisMax(values.maxOrNull() ?: 0)
    val minX = windowStart
    val maxX = maxOf(minX + 1, windowEnd)
    val ticks = pingTimeLabels(minX, maxX)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("延迟(ms)", color = Muted, fontSize = 10.sp)
        Row(modifier = Modifier.fillMaxWidth().height(154.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(34.dp).height(142.dp), verticalArrangement = Arrangement.SpaceBetween) {
                axisLabels(maxY).forEach { Text(it.toString(), color = Muted, fontSize = 9.sp, maxLines = 1) }
            }
            Canvas(modifier = Modifier.weight(1f).height(142.dp).background(Color(0xFFF8FAFC), ShapeS).padding(6.dp)) {
                val w = size.width
                val h = size.height
                repeat(4) { idx ->
                    val y = h * (idx + 1) / 5f
                    drawLine(Border.copy(alpha = 0.55f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                if (sorted.isNotEmpty()) {
                    fun toOffset(p: PingPoint): Offset? {
                        val latency = p.latencyMs ?: return null
                        val x = w * ((p.elapsedSec - minX).toFloat() / (maxX - minX).toFloat())
                        val clipped = latency.coerceIn(0, maxY)
                        val y = h - h * (clipped.toFloat() / maxY.toFloat())
                        return Offset(x, y)
                    }

                    val segments = mutableListOf<MutableList<Pair<PingPoint, Offset>>>()
                    var current = mutableListOf<Pair<PingPoint, Offset>>()
                    var previous: PingPoint? = null
                    sorted.forEach { p ->
                        val offset = toOffset(p)
                        if (offset == null) {
                            val x = w * ((p.elapsedSec - minX).toFloat() / (maxX - minX).toFloat())
                            if (p.lossCount > 0) drawCircle(ErrorRed, radius = 4f, center = Offset(x, h - 5f))
                            if (current.isNotEmpty()) segments.add(current)
                            current = mutableListOf()
                            previous = p
                        } else {
                            val gap = previous?.let { p.elapsedSec - it.elapsedSec } ?: 0
                            if (gap > 3 && current.isNotEmpty()) {
                                segments.add(current)
                                current = mutableListOf()
                            }
                            current.add(p to offset)
                            previous = p
                        }
                    }
                    if (current.isNotEmpty()) segments.add(current)

                    segments.forEach { segment ->
                        if (segment.size == 1) {
                            drawCircle(Blue, radius = 3.2f, center = segment.first().second)
                        } else {
                            val path = Path()
                            path.moveTo(segment.first().second.x, segment.first().second.y)
                            for (i in 1 until segment.size) {
                                val prev = segment[i - 1].second
                                val next = segment[i].second
                                val midX = (prev.x + next.x) / 2f
                                path.cubicTo(midX, prev.y, midX, next.y, next.x, next.y)
                            }
                            drawPath(path, color = Blue, style = Stroke(width = 3f, cap = StrokeCap.Round))
                        }
                        segment.forEach { (p, o) ->
                            if (p.highLatency) drawCircle(Orange, radius = 4.2f, center = o)
                            else drawCircle(Blue, radius = 2.6f, center = o)
                            if (p.lossCount > 0) drawCircle(ErrorRed, radius = 3.6f, center = Offset(o.x, h - 5f))
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 34.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ticks.forEach { Text("${it}s", color = Muted, fontSize = 10.sp) }
        }
    }
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
                DetailItem(Icons.Filled.Info, "地址", if (maskPrivacy) maskAddress(address) else address.ifBlank { "无" }, Blue, Modifier.weight(1f))
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
                Icon(Icons.Filled.Info, contentDescription = null, tint = Blue, modifier = Modifier.width(14.dp).height(14.dp))
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
    "◎", "target" -> Icons.Filled.Assessment
    "∿", "ping" -> Icons.Filled.SignalCellularAlt
    "≡", "mode" -> Icons.Filled.Tune
    "□", "log" -> Icons.Filled.Article
    "▮" -> Icons.Filled.SignalCellularAlt
    "!" -> Icons.Filled.WarningAmber
    "host" -> Icons.Filled.Search
    "port" -> Icons.Filled.Tune
    "privacy" -> Icons.Filled.Shield
    "count" -> Icons.Filled.Refresh
    "time" -> Icons.Filled.History
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .background(if (selected) Color(0xFFEBDCFD) else Color.Transparent, RoundedCornerShape(24.dp))
                    .clickable { onSelect(tab) }
                    .padding(top = 5.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    image,
                    contentDescription = tab.label,
                    tint = if (selected) Blue else Muted,
                    modifier = Modifier.width(21.dp).height(21.dp)
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
