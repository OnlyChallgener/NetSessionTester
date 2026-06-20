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
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.demonv.netsessiontester.model.SessionConfig
import com.demonv.netsessiontester.model.SessionSummary
import com.demonv.netsessiontester.model.TestMode
import com.demonv.netsessiontester.network.TcpTester
import com.demonv.netsessiontester.network.PublicIpDetector
import com.demonv.netsessiontester.network.PublicIpResult
import com.demonv.netsessiontester.ui.theme.NetSessionTesterTheme
import com.demonv.netsessiontester.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

private enum class MainTab(val label: String, val mark: String) {
    SETTINGS("设置", "settings"),
    TEST("测试", "play"),
    LOGS("历史", "logs")
}

private enum class ChartMode(val label: String) {
    GROWTH("增长曲线"),
    PEAK("峰值/最高值")
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
    val timeEpochMs: Long = System.currentTimeMillis()
)

private data class NetworkEnvironment(
    val typeLabel: String = "未知网络",
    val carrierName: String = "未知",
    val hasWifi: Boolean = false,
    val hasCellular: Boolean = false,
    val hasVpn: Boolean = false,
    val hasInternet: Boolean = false
)

private fun detectNetworkEnvironment(context: Context): NetworkEnvironment {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val caps = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
    val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val telephony = context.getSystemService(TelephonyManager::class.java)
    val carrier = runCatching { telephony?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "未知" }.getOrDefault("未知")
    val typeLabel = when {
        hasVpn && hasWifi -> "WiFi + VPN"
        hasVpn && hasCellular -> "蜂窝 + VPN"
        hasWifi -> "WiFi"
        hasCellular -> "蜂窝网络"
        hasVpn -> "VPN"
        else -> "未知网络"
    }
    return NetworkEnvironment(typeLabel, carrier, hasWifi, hasCellular, hasVpn, hasInternet)
}

private fun inferCarrierFromIpv6Prefix(ipv6: String): String {
    val value = ipv6.trim().lowercase()
    return when {
        value.startsWith("2408:") -> "中国联通"
        value.startsWith("2409:") -> "中国移动"
        value.startsWith("240e:") -> "中国电信"
        else -> "未知"
    }
}

private fun displayCarrierFromEnv(env: NetworkEnvironment, ipv6: String): String {
    val prefixCarrier = inferCarrierFromIpv6Prefix(ipv6)
    return when {
        env.hasCellular && env.carrierName.isNotBlank() && env.carrierName != "未知" -> env.carrierName
        prefixCarrier != "未知" -> prefixCarrier
        env.hasWifi -> "WiFi"
        else -> "未知"
    }
}

private suspend fun tcpPingMs(host: String, port: Int): Int? = withContext(Dispatchers.IO) {
    runCatching {
        val start = System.nanoTime()
        Socket().use { socket -> socket.connect(InetSocketAddress(host, port.coerceIn(1, 65535)), 180) }
        ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
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
    var batchSize by remember { mutableStateOf("100") }
    var intervalMs by remember { mutableStateOf("500") }
    var timeoutMs by remember { mutableStateOf("3000") }
    var successLimit by remember { mutableStateOf("65535") }
    var failureLimit by remember { mutableStateOf("200") }
    var keepConnections by remember { mutableStateOf(true) }
    var maskPrivacy by remember { mutableStateOf(false) }
    var historyLimit by remember { mutableStateOf("30") }
    var historyPeriod by remember { mutableStateOf("WEEK") }
    var logSizeKb by remember { mutableStateOf(0) }
    var historySizeKb by remember { mutableStateOf(0) }
    var historySavedCount by remember { mutableStateOf(0) }
    var historyCounts by remember { mutableStateOf(HistoryCounts()) }
    var publicIpResult by remember { mutableStateOf(PublicIpResult()) }
    var publicIpLoading by remember { mutableStateOf(false) }
    var manualStopRequested by remember { mutableStateOf(false) }
    var currentTestConfig by remember { mutableStateOf<SessionConfig?>(null) }
    var currentStartedAt by remember { mutableStateOf(0L) }
    var chartMode by remember { mutableStateOf(ChartMode.GROWTH) }
    var chartPoints by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }
    var lastChartSampleAt by remember { mutableStateOf<Map<IpProtocol, Long>>(emptyMap()) }
    var pingPoints by remember { mutableStateOf<List<PingPoint>>(emptyList()) }
    var pingJob by remember { mutableStateOf<Job?>(null) }

    var detailTitle by remember { mutableStateOf<String?>(null) }
    var detailLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var detailSummary by remember { mutableStateOf<SessionSummary?>(null) }
    var editingRemarkSummary by remember { mutableStateOf<SessionSummary?>(null) }
    var editingRemarkText by remember { mutableStateOf("") }
    var showRunLogDetail by remember { mutableStateOf(false) }

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
        scope.launch {
            publicIpLoading = true
            publicIpResult = PublicIpDetector.detect()
            publicIpLoading = false
        }
    }

    LaunchedEffect(Unit) {
        state = state.copy(history = historyStore.load(historyPeriod, 30), logs = logStore.load())
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
        state = state.copy(history = historyStore.load(historyPeriod, historyLimit.toIntOrNull()?.coerceIn(10, 100) ?: 30))
        historySavedCount = historyStore.count()
        historyCounts = historyStore.counts()
        settingsLoaded = true
        refreshPublicIp()
    }

    LaunchedEffect(
        settingsLoaded, host, port, mode, batchSize, intervalMs, timeoutMs,
        successLimit, failureLimit, keepConnections, maskPrivacy, historyLimit
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
                    historyLimit = historyLimit
                )
            )
        }
    }

    fun appendLog(line: LogLine) {
        state = state.copy(logs = (state.logs + line).takeLast(500))
        scope.launch { logStore.append(line); logSizeKb = logStore.sizeKb() }
    }

    fun resetCurrentCharts() {
        chartPoints = emptyList()
        lastChartSampleAt = emptyMap()
        pingPoints = emptyList()
    }

    fun recordChartPoint(stats: ProtocolStats) {
        if (currentStartedAt <= 0L) return
        val now = System.currentTimeMillis()
        val last = lastChartSampleAt[stats.protocol] ?: 0L
        val terminal = stats.phase.contains("完成") || stats.phase.contains("释放") || stats.phase.contains("中断") || stats.phase.contains("上限")
        if (!terminal && now - last < 1_000L) return
        val elapsed = ((now - currentStartedAt) / 1_000L).toInt().coerceAtLeast(0)
        val point = ChartPoint(
            protocol = stats.protocol,
            elapsedSec = elapsed,
            active = stats.activeSessions,
            failure = stats.totalFailure,
            total = stats.totalAttempts,
            cps = stats.cps,
            phase = stats.phase
        )
        chartPoints = (chartPoints.filterNot { it.protocol == point.protocol && it.elapsedSec == point.elapsedSec } + point)
            .sortedWith(compareBy<ChartPoint> { it.protocol.ordinal }.thenBy { it.elapsedSec })
            .takeLast(240)
        lastChartSampleAt = lastChartSampleAt + (stats.protocol to now)
    }

    fun appendPingSecond(sec: Int, samples: List<Int?>) {
        val valid = samples.mapNotNull { it }
        val avg = valid.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        pingPoints = (pingPoints.filterNot { it.elapsedSec == sec } + PingPoint(elapsedSec = sec, latencyMs = avg))
            .sortedBy { it.elapsedSec }
            .takeLast(180)
    }

    fun alignPingWithSessionEnd() {
        val started = currentStartedAt
        if (started <= 0L) return
        val elapsed = ((System.currentTimeMillis() - started) / 1_000L).toInt().coerceAtLeast(0)
        val last = pingPoints.lastOrNull()
        if (last != null && last.elapsedSec < elapsed) {
            pingPoints = (pingPoints + last.copy(elapsedSec = elapsed)).takeLast(180)
        }
    }

    fun startPingMonitor(config: SessionConfig, startedAt: Long) {
        pingJob?.cancel()
        pingPoints = emptyList()
        pingJob = scope.launch {
            var bucketSec = -1
            val bucketSamples = mutableListOf<Int?>()
            while (currentStartedAt == startedAt) {
                val loopStart = System.currentTimeMillis()
                val elapsed = ((loopStart - startedAt) / 1_000L).toInt().coerceAtLeast(0)
                if (bucketSec < 0) bucketSec = elapsed
                if (elapsed != bucketSec) {
                    appendPingSecond(bucketSec, bucketSamples.toList())
                    bucketSamples.clear()
                    bucketSec = elapsed
                }
                bucketSamples.add(tcpPingMs(config.host, config.port))
                val cost = System.currentTimeMillis() - loopStart
                delay((200L - cost).coerceAtLeast(0L))
            }
            if (bucketSec >= 0 && bucketSamples.isNotEmpty()) {
                appendPingSecond(bucketSec, bucketSamples.toList())
            }
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
        val config = runCatching {
            SessionConfig(
                host = host.ifBlank { "www.baidu.com" },
                port = port.toInt(),
                mode = mode,
                batchSize = batchSize.toInt(),
                intervalMs = intervalMs.toLong(),
                timeoutMs = timeoutMs.toInt(),
                successLimit = successLimit.toInt(),
                failureLimit = failureLimit.toInt(),
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

    fun startTest() {
        val config = buildConfig() ?: return
        ensureNotificationPermission()
        runningJob?.cancel()
        selectedTab = MainTab.TEST
        startForegroundNotice(context, "建连中：${config.mode.label}，目标 ${config.successLimit}")
        val startedAt = System.currentTimeMillis()
        currentStartedAt = startedAt
        currentTestConfig = config
        resetCurrentCharts()
        refreshPublicIp()
        manualStopRequested = false
        state = state.copy(
            isAdding = true,
            status = "建连中",
            ipv4Stats = ProtocolStats(IpProtocol.IPV4),
            ipv6Stats = ProtocolStats(IpProtocol.IPV6),
            summary = null,
            error = null
        )
        appendLog(LogLine(level = LogLevel.INFO, text = "目标：${config.host}:${config.port} | 模式：${config.mode.label} | 新增：${config.batchSize} | 间隔：${config.intervalMs}ms"))
        startPingMonitor(config, startedAt)

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
                    onStats = { stats ->
                        recordChartPoint(stats)
                        state = when (stats.protocol) {
                            IpProtocol.IPV4 -> state.copy(ipv4Stats = stats, status = "${stats.protocol.label} ${stats.phase}")
                            IpProtocol.IPV6 -> state.copy(ipv6Stats = stats, status = "${stats.protocol.label} ${stats.phase}")
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
                historyStore.append(summary!!)
                historyStore.trim(100)
                refreshHistory()
                completedNormally = true
            } catch (error: Exception) {
                if (!manualStopRequested) {
                    failureMsg = error.message ?: error.javaClass.simpleName
                    appendLog(LogLine(level = LogLevel.ERROR, text = "测试中断：$failureMsg"))
                }
            } finally {
                alignPingWithSessionEnd()
                pingJob?.cancel()
                pingJob = null
                if (!manualStopRequested) {
                    val closed = tester.release()
                    if (completedNormally) {
                        appendLog(LogLine(level = LogLevel.WARN, text = "测试完成，已立即释放连接：$closed 条"))
                    } else {
                        appendLog(LogLine(level = LogLevel.WARN, text = "测试结束收尾，已释放连接：$closed 条"))
                    }

                    val finalSummary = summary
                    val finalIpv4 = finalSummary?.ipv4Stats ?: state.ipv4Stats
                    val finalIpv6 = finalSummary?.ipv6Stats ?: state.ipv6Stats

                    state = state.copy(
                        isAdding = false,
                        status = if (completedNormally) "测试完成 · 已释放" else "测试中断 · 已释放",
                        summary = finalSummary ?: state.summary,
                        error = failureMsg,
                        ipv4Stats = if (config.mode != TestMode.IPV6_ONLY) {
                            finalIpv4.copy(activeSessions = 0, phase = if (finalIpv4.totalAttempts > 0) "已释放" else finalIpv4.phase)
                        } else state.ipv4Stats,
                        ipv6Stats = if (config.mode != TestMode.IPV4_ONLY) {
                            finalIpv6.copy(activeSessions = 0, phase = if (finalIpv6.totalAttempts > 0) "已释放" else finalIpv6.phase)
                        } else state.ipv6Stats
                    )
                    stopForegroundNotice(context)
                }
                runningJob = null
            }
        }
    }

    fun stoppedStatsFor(protocol: IpProtocol, current: ProtocolStats, reason: String = "手动停止"): ProtocolStats {
        return current.copy(
            phase = reason,
            errorSummary = current.errorSummary + (reason to 1)
        )
    }

    fun saveManualStopSummary(reason: String = "手动停止") {
        val config = currentTestConfig ?: buildConfig() ?: return
        val ipv4 = when (config.mode) {
            TestMode.IPV4_ONLY -> stoppedStatsFor(IpProtocol.IPV4, state.ipv4Stats, reason)
            TestMode.IPV4_THEN_IPV6 -> stoppedStatsFor(IpProtocol.IPV4, state.ipv4Stats, reason)
            TestMode.IPV6_ONLY -> null
        }
        val ipv6 = when (config.mode) {
            TestMode.IPV6_ONLY -> stoppedStatsFor(IpProtocol.IPV6, state.ipv6Stats, reason)
            TestMode.IPV4_THEN_IPV6 -> if (state.ipv6Stats.totalAttempts > 0) stoppedStatsFor(IpProtocol.IPV6, state.ipv6Stats, reason) else null
            TestMode.IPV4_ONLY -> null
        }
        val summary = SessionSummary(
            startedAtEpochMs = if (currentStartedAt > 0L) currentStartedAt else System.currentTimeMillis(),
            host = config.host,
            port = config.port,
            mode = config.mode,
            ipv4Stats = ipv4,
            ipv6Stats = ipv6
        )
        scope.launch {
            historyStore.append(summary)
            historyStore.trim(100)
            refreshHistory()
            state = state.copy(
                summary = summary,
                ipv4Stats = ipv4 ?: state.ipv4Stats,
                ipv6Stats = ipv6 ?: state.ipv6Stats
            )
        }
    }

    fun stopAdding() {
        manualStopRequested = true
        runningJob?.cancel()
        runningJob = null
        alignPingWithSessionEnd()
        pingJob?.cancel()
        pingJob = null
        appendLog(LogLine(level = LogLevel.WARN, text = "手动停止；已停止新增并保存历史。"))
        saveManualStopSummary("手动停止")
        scope.launch {
            val closed = tester.release()
            appendLog(LogLine(level = LogLevel.WARN, text = "手动停止，已立即释放连接：$closed 条"))
            state = state.copy(
                isAdding = false,
                status = "手动停止 · 已释放",
                ipv4Stats = state.ipv4Stats.copy(activeSessions = 0, phase = if (state.ipv4Stats.totalAttempts > 0) "已释放" else state.ipv4Stats.phase),
                ipv6Stats = state.ipv6Stats.copy(activeSessions = 0, phase = if (state.ipv6Stats.totalAttempts > 0) "已释放" else state.ipv6Stats.phase)
            )
            stopForegroundNotice(context)
        }
    }

    fun releaseAll() {
        val wasRunning = state.isAdding
        if (wasRunning) {
            manualStopRequested = true
            runningJob?.cancel()
            runningJob = null
            alignPingWithSessionEnd()
            pingJob?.cancel()
            pingJob = null
            appendLog(LogLine(level = LogLevel.WARN, text = "强制释放；已停止测试并保存历史。"))
            saveManualStopSummary("强制释放")
        } else {
            runningJob?.cancel()
            runningJob = null
            alignPingWithSessionEnd()
            pingJob?.cancel()
            pingJob = null
        }
        scope.launch {
            val closed = tester.release()
            appendLog(LogLine(level = LogLevel.WARN, text = "已强制释放连接：$closed 条"))
            state = state.copy(
                isAdding = false,
                status = "已强制释放",
                ipv4Stats = state.ipv4Stats.copy(activeSessions = 0, phase = if (state.ipv4Stats.totalAttempts > 0) "已释放" else state.ipv4Stats.phase),
                ipv6Stats = state.ipv6Stats.copy(activeSessions = 0, phase = if (state.ipv6Stats.totalAttempts > 0) "已释放" else state.ipv6Stats.phase)
            )
            stopForegroundNotice(context)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    onSave = { scope.launch { snackbarHostState.showSnackbar("参数已保存") } },
                    onRestoreDefault = {
                        host = "www.baidu.com"; port = "80"; mode = TestMode.IPV4_THEN_IPV6
                        batchSize = "100"; intervalMs = "500"; timeoutMs = "3000"
                        successLimit = "65535"; failureLimit = "200"; keepConnections = true; maskPrivacy = false; historyLimit = "30"
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
                    isAdding = state.isAdding,
                    onStart = { startTest() },
                    onStopAdding = { stopAdding() },
                    onRelease = { releaseAll() },
                    onExport = { exportLogs() },
                    ipv4Stats = state.ipv4Stats,
                    ipv6Stats = state.ipv6Stats,
                    showIpv4 = mode != TestMode.IPV6_ONLY,
                    showIpv6 = mode != TestMode.IPV4_ONLY,
                    maskPrivacy = maskPrivacy,
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
                    onHistoryDetail = { summary ->
                        detailSummary = summary
                    }
                )
            }
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
                summary.ipv4Stats?.let { HistoryProtocolCard("IPv4 结果", it, maskPrivacy) }
                summary.ipv6Stats?.let { HistoryProtocolCard("IPv6 结果", it, maskPrivacy) }
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
            MetricTile("新增", stats.lastAdded.toString(), Blue, Modifier.weight(1f))
            MetricTile("CPS", "${stats.cps}/s", Blue, Modifier.weight(1f))
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
        Text("摘要条形图用于截图分享，完整过程以当前测试页曲线为准。", color = Muted, fontSize = 10.sp)
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
    isAbnormalPhase(s.phase) || s.errorSummary.keys.any { it.contains("中断") || it.contains("地址丢失") || it.contains("异常") }
}

private fun historyConclusion(summary: SessionSummary): String {
    val abnormal = isAbnormalSummary(summary)
    val v4 = summary.ipv4Stats
    val v6 = summary.ipv6Stats
    val v4Peak = v4?.let { protocolPeak(it) } ?: 0
    val v6Peak = v6?.let { protocolPeak(it) } ?: 0
    val fd = listOfNotNull(v4, v6).any { it.phase.contains("FD", true) || it.errorSummary.keys.any { k -> k.contains("FD", true) } }
    return when {
        abnormal -> "本次非正常中断，结果仅供参考。"
        fd -> "触发手机 FD/Socket 上限，不一定代表宽带到顶。"
        v4 != null && v6 != null && v4Peak in 1..999 && v6Peak >= 5000 -> "IPv4 可能是主要瓶颈，优先怀疑 NAT/CGNAT。"
        v4 != null && v6 == null && v4Peak in 1..999 -> "仅 IPv4 偏低，建议补测 IPv6。"
        else -> "结果可作为本次网络会话能力参考。"
    }
}

private fun historyAdvice(summary: SessionSummary): List<String> {
    val v4 = summary.ipv4Stats
    val v6 = summary.ipv6Stats
    val v4Peak = v4?.let { protocolPeak(it) } ?: 0
    val v6Peak = v6?.let { protocolPeak(it) } ?: 0
    val fd = listOfNotNull(v4, v6).any { it.phase.contains("FD", true) || it.errorSummary.keys.any { k -> k.contains("FD", true) } }
    return when {
        isAbnormalSummary(summary) -> listOf("保持网络不切换后重新测试。", "检查 VPN、WiFi/蜂窝、4G/5G 是否变化。", "非正常中断结果不要直接作为会话上限。")
        fd -> listOf("降低新增批次或分协议测试。", "观察路由器连接数表，判断宽带侧是否还在增长。", "FD上限更像手机端资源限制。")
        v4 != null && v6 != null && v4Peak in 1..999 && v6Peak >= 5000 -> listOf("IPv4 低、IPv6 高，优先看 IPv4 NAT/CGNAT。", "蜂窝网络可换 APN 或切换 4G/5G 复测。", "WiFi 网络可观察路由器 NAT/连接数表。")
        v4 != null && v6 == null && v4Peak in 1..999 -> listOf("补测 IPv6 或使用分别测试模式。", "如果 IPv6 明显更高，优先怀疑 IPv4 出口策略。", "更换目标站复测排除目标限制。")
        else -> listOf("保存截图对比不同运营商和网络。", "分别测试 IPv4/IPv6 更利于判断瓶颈。", "异常结果可结合失败原因卡判断。")
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
    onSave: () -> Unit,
    onRestoreDefault: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { PageTitle("宽带会话测试器", "TCP 会话保持 · IPv4 / IPv6 分别测试") }
        item {
            SoftCard {
                SectionTitle("◎", "目标设置", Blue)
                FieldLabel("地址")
                CleanField(host, onHostChange, "www.baidu.com")
                FieldLabel("端口")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CleanField(port, { onPortChange(it.onlyDigits()) }, "80", Modifier.weight(1f), KeyboardType.Number)
                    OutlinedButton(onClick = onResolve, shape = ShapeM, modifier = Modifier.height(44.dp).width(104.dp)) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.width(15.dp).height(15.dp)); Spacer(Modifier.width(3.dp)); Text("解析", fontSize = 12.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarkBox("□", Color(0xFFEFF6FF), Blue)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("打码", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 13.sp)
                        Text("隐藏 IP / 公网地址显示和导出", color = Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    Switch(checked = maskPrivacy, onCheckedChange = onMaskPrivacyChange)
                }
                if (result.ipv4.isNotEmpty() || result.ipv6.isNotEmpty() || result.error != null) {
                    HorizontalDivider(color = Border)
                    InfoLine("IPv4", displayIpList(result.ipv4, maskPrivacy))
                    InfoLine("IPv6", displayIpList(result.ipv6, maskPrivacy))
                    result.error?.let { InfoLine("错误", it, ErrorRed) }
                }
            }
        }
        item {
            NetworkEnvironmentSettingsCard(
                env = networkEnvironment,
                publicIpResult = publicIpResult,
                publicIpLoading = publicIpLoading,
                maskPrivacy = maskPrivacy,
                onRefresh = onRefreshPublicIp,
                onCopyPublicIpv4 = onCopyPublicIpv4,
                onCopyPublicIpv6 = onCopyPublicIpv6
            )
        }
        item {
            SoftCard {
                SectionTitle("∿", "测试模式", Purple)
                ModeSelector(mode, onModeChange)
                Text("同时对 IPv4 与 IPv6 分别进行会话保持测试", color = Muted, fontSize = 12.sp, maxLines = 1)
            }
        }
        item {
            SoftCard {
                SectionTitle("≡", "会话参数", Green)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ParamField("新增（条）", batchSize, onBatchSizeChange, Modifier.weight(1f))
                    ParamField("间隔（ms）", intervalMs, onIntervalMsChange, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ParamField("超时（ms）", timeoutMs, onTimeoutMsChange, Modifier.weight(1f))
                    ParamField("失败停（次）", failureLimit, onFailureLimitChange, Modifier.weight(1f))
                }
                ParamField("目标会话（条）", successLimit, onSuccessLimitChange, Modifier.fillMaxWidth())
                Text("参数将用于后续测试，可随时修改并保存。", color = Muted, fontSize = 12.sp)
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
    isAdding: Boolean,
    onStart: () -> Unit,
    onStopAdding: () -> Unit,
    onRelease: () -> Unit,
    onExport: () -> Unit,
    ipv4Stats: ProtocolStats,
    ipv6Stats: ProtocolStats,
    showIpv4: Boolean,
    showIpv6: Boolean,
    maskPrivacy: Boolean,
    logs: List<LogLine>,
    onMoreLogs: () -> Unit,
    onMoreFailure: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            PageTitle("宽带会话测试器", null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusChip(mode.label, BlueSoft, Blue)
                StatusChip(if (isAdding) "● 运行中" else status, GreenSoft, Green)
                StatusChip("◎ 目标 $target", Color.White, TextDark)
            }
        }
        item {
            SoftCard {
                SectionTitle("∿", "测试控制", Blue)
                Text(if (isAdding) "● 正在运行 · 已连接目标" else "状态：$status", color = if (isAdding) Green else Muted, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onStart, enabled = !isAdding, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("开始", fontSize = 13.sp)
                    }
                    Button(
                        onClick = onStopAdding,
                        enabled = isAdding,
                        colors = ButtonDefaults.buttonColors(containerColor = RedSoft, contentColor = ErrorRed),
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = ShapeM
                    ) { Icon(Icons.Filled.Stop, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("停止", fontSize = 13.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRelease, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) { Icon(Icons.Filled.DeleteOutline, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("强制释放", fontSize = 13.sp) }
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) { Icon(Icons.Filled.Download, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("导出", fontSize = 13.sp) }
                }
            }
        }
        item { PingCompactChartCard(pingPoints = pingPoints) }
        if (showIpv4) item { SessionStatsCard("IPv4 会话", ipv4Stats, maskPrivacy, chartPoints.filter { it.protocol == IpProtocol.IPV4 }, chartMode, onChartModeChange) }
        if (showIpv6) item { SessionStatsCard("IPv6 会话", ipv6Stats, maskPrivacy, chartPoints.filter { it.protocol == IpProtocol.IPV6 }, chartMode, onChartModeChange) }
        item { DiagnosisAdviceInlineCard(mode, ipv4Stats, ipv6Stats) }
        item { RecentLogCard(logs, maskPrivacy, onMoreLogs) }
        item { Spacer(Modifier.height(70.dp)) }
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
                    Text("检测历史", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
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
            items(history.take(historyLimit.toIntOrNull()?.coerceIn(10, 100) ?: 30)) { item ->
                HistoryCard(
                    item = item,
                    maskPrivacy = maskPrivacy,
                    onClick = { onHistoryDetail(item) },
                    onEditRemark = { onEditRemark(item) }
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
private fun PageTitle(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
            if (title == "宽带会话测试器") {
                Spacer(Modifier.width(8.dp))
                Text("v0.9.8", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        subtitle?.let {
            Text(it, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextDark)
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
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        shape = ShapeM,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().height(52.dp)
    )
}

@Composable
private fun ParamField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.onlyDigits()) },
        label = { Text(label, maxLines = 1, fontSize = 11.sp) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        shape = ShapeM,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.height(56.dp)
    )
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
            Text(stats.phase, color = Blue, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricTile("活动", stats.activeSessions.toString(), Blue, Modifier.weight(1f))
            MetricTile("上次活动", if (stats.maxStableSessions > 0) stats.maxStableSessions.toString() else "—", Navy, Modifier.weight(1f))
            MetricTile("失败", stats.totalFailure.toString(), ErrorRed, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricTile("总计", stats.totalAttempts.toString(), Navy, Modifier.weight(1f))
            MetricTile("新增", stats.lastAdded.toString(), Blue, Modifier.weight(1f))
            MetricTile("CPS", "${stats.cps}/s", Blue, Modifier.weight(1f))
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
    else -> 10
}

private fun sessionYAxisMax(peak: Int): Int = when {
    peak <= 1000 -> 1000
    peak <= 2500 -> 2500
    peak <= 5000 -> 5000
    peak <= 10000 -> 10000
    peak <= 35000 -> 35000
    else -> 65535
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
    if (labels.lastOrNull() != maxX) labels.add(maxX)
    return labels.distinct()
}

@Composable
private fun SessionGrowthChart(points: List<ChartPoint>) {
    val sorted = points.sortedBy { it.elapsedSec }
    val minX = sorted.firstOrNull()?.elapsedSec ?: 0
    val maxXRaw = sorted.lastOrNull()?.elapsedSec ?: (minX + 1)
    val maxX = maxOf(minX + 1, maxXRaw)
    val maxY = sessionYAxisMax(sorted.maxOfOrNull { maxOf(it.active, it.failure, it.total) } ?: 0)
    val step = chartXAxisStep(maxX - minX)
    val last = sorted.lastOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("会话数", color = Muted, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(last?.let { "${it.elapsedSec}s 活动 ${it.active}｜失败 ${it.failure}" } ?: "", color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        Row(modifier = Modifier.fillMaxWidth().height(158.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(34.dp).height(145.dp), verticalArrangement = Arrangement.SpaceBetween) {
                axisLabels(maxY).forEach { Text(it.toString(), color = Muted, fontSize = 9.sp, maxLines = 1) }
            }
            Canvas(modifier = Modifier.weight(1f).height(145.dp).background(Color(0xFFF8FAFC), ShapeS).padding(6.dp)) {
                val w = size.width
                val h = size.height
                repeat(4) { idx ->
                    val y = h * (idx + 1) / 5f
                    drawLine(Border.copy(alpha = 0.55f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                fun xOf(p: ChartPoint) = w * ((p.elapsedSec - minX).toFloat() / (maxX - minX).toFloat())
                fun yOf(v: Int) = h - h * (v.coerceIn(0, maxY).toFloat() / maxY.toFloat())
                fun pathOf(value: (ChartPoint) -> Int): Path {
                    val path = Path()
                    sorted.forEachIndexed { index, p ->
                        val x = xOf(p); val y = yOf(value(p))
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    return path
                }
                if (sorted.size > 1) {
                    drawPath(pathOf { it.active }, color = Blue, style = Stroke(width = 4f))
                    drawPath(pathOf { it.failure }, color = ErrorRed, style = Stroke(width = 3f))
                }
                sorted.forEach { p ->
                    drawCircle(Blue, radius = 3f, center = Offset(xOf(p), yOf(p.active)))
                    if (p.failure > 0) drawCircle(ErrorRed, radius = 3f, center = Offset(xOf(p), yOf(p.failure)))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 34.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            timeLabels(minX, maxX, step).forEach { Text("${it}s", color = Muted, fontSize = 10.sp) }
        }
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
private fun NetworkEnvironmentSettingsCard(
    env: NetworkEnvironment,
    publicIpResult: PublicIpResult,
    publicIpLoading: Boolean,
    maskPrivacy: Boolean,
    onRefresh: () -> Unit,
    onCopyPublicIpv4: () -> Unit,
    onCopyPublicIpv6: () -> Unit
) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("∿", "网络环境", Purple)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text(if (publicIpLoading) "检测中" else "刷新", fontSize = 12.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("网络", env.typeLabel, Blue, Modifier.weight(1f))
            MetricTile("运营商", displayCarrierFromEnv(env, publicIpResult.ipv6), Purple, Modifier.weight(1f))
        }
        PublicExitLine(
            label = "IPv4 出口",
            value = if (maskPrivacy) maskIpText(publicIpResult.ipv4) else publicIpResult.ipv4,
            copyValue = publicIpResult.ipv4,
            onCopy = onCopyPublicIpv4
        )
        PublicExitLine(
            label = "IPv6 出口",
            value = if (maskPrivacy) maskIpText(publicIpResult.ipv6) else publicIpResult.ipv6,
            copyValue = publicIpResult.ipv6,
            onCopy = onCopyPublicIpv6
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(if (env.hasInternet) "可访问互联网" else "无互联网能力", if (env.hasInternet) GreenSoft else RedSoft, if (env.hasInternet) Green else ErrorRed, compact = true)
            if (env.hasVpn) StatusChip("VPN开启", Color(0xFFF3E8FF), Purple, compact = true)
        }
    }
}

@Composable
private fun PingCompactChartCard(pingPoints: List<PingPoint>) {
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
            StatusChip("200ms/次", BlueSoft, Blue, compact = true)
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
        Text("图表仅显示当前测试过程，下次测试将重新开始记录。", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun MiniMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0xFFF8FAFC), ShapeM).padding(horizontal = 5.dp, vertical = 5.dp)) {
        Column {
            Text(label, color = Muted, fontSize = 9.sp, maxLines = 1)
            Text(value, color = color, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PingLineChart(points: List<PingPoint>) {
    val sorted = points.sortedBy { it.elapsedSec }.takeLast(120)
    val values = sorted.mapNotNull { it.latencyMs }
    val maxY = pingYAxisMax(values.maxOrNull() ?: 0)
    val minX = sorted.firstOrNull()?.elapsedSec ?: 0
    val maxX = maxOf(minX + 1, sorted.lastOrNull()?.elapsedSec ?: 1)
    val step = chartXAxisStep(maxX - minX)
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
                    val path = Path()
                    var started = false
                    sorted.forEach { p ->
                        val x = w * ((p.elapsedSec - minX).toFloat() / (maxX - minX).toFloat())
                        val latency = p.latencyMs
                        if (latency == null) {
                            drawCircle(ErrorRed, radius = 4f, center = Offset(x, h - 5f))
                        } else {
                            val clipped = latency.coerceIn(0, maxY)
                            val y = h - h * (clipped.toFloat() / maxY.toFloat())
                            if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                            drawCircle(Blue, radius = 3f, center = Offset(x, y))
                        }
                    }
                    drawPath(path, color = Blue, style = Stroke(width = 3f))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 34.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            timeLabels(minX, maxX, step).forEach { Text("${it}s", color = Muted, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun DiagnosisAdviceInlineCard(mode: TestMode, ipv4Stats: ProtocolStats, ipv6Stats: ProtocolStats) {
    val v4Peak = maxOf(ipv4Stats.activeSessions, ipv4Stats.maxStableSessions, ipv4Stats.totalSuccess)
    val v6Peak = maxOf(ipv6Stats.activeSessions, ipv6Stats.maxStableSessions, ipv6Stats.totalSuccess)
    val hasV4 = mode != TestMode.IPV6_ONLY && ipv4Stats.totalAttempts > 0
    val hasV6 = mode != TestMode.IPV4_ONLY && ipv6Stats.totalAttempts > 0
    val conclusion = when {
        !hasV4 && !hasV6 -> "测试完成后生成实际诊断"
        hasV4 && hasV6 && v4Peak in 1..999 && v6Peak >= 5000 -> "IPv4 低、IPv6 高，优先怀疑 IPv4 NAT / CGNAT 限制"
        hasV4 && !hasV6 && v4Peak in 1..999 -> "仅 IPv4 偏低，建议补测 IPv6 或使用分别测试"
        hasV4 && hasV6 && v4Peak < 1000 && v6Peak < 1000 -> "IPv4 / IPv6 都偏低，建议排查目标站、信号或本机资源"
        else -> "结果可作为本次网络会话能力参考"
    }
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
        Text("图表看过程，峰值卡看分享结果；异常中断结果仅供参考。", color = Muted, fontSize = 11.sp)
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
            Text(label, color = Muted, fontSize = 10.sp, maxLines = 1)
            Text(value, color = color, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(tag, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                fontSize = 12.sp
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
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onClick)) {
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
                DetailItem(Icons.Filled.Shield, "稳定", (mainStats?.maxStableSessions ?: mainStats?.activeSessions ?: 0).toString(), Green, Modifier.weight(1f))
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
    "◎" -> Icons.Filled.Assessment
    "∿" -> Icons.Filled.SignalCellularAlt
    "≡" -> Icons.Filled.Tune
    "□" -> Icons.Filled.Article
    "▮" -> Icons.Filled.SignalCellularAlt
    "!" -> Icons.Filled.WarningAmber
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
            .height(60.dp)
            .background(Color(0xFFEAF2FF))
            .padding(horizontal = 22.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
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
                    .width(86.dp)
                    .height(56.dp)
                    .background(if (selected) Color(0xFFEBDCFD) else Color.Transparent, RoundedCornerShape(24.dp))
                    .clickable { onSelect(tab) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    image,
                    contentDescription = tab.label,
                    tint = if (selected) Blue else Muted,
                    modifier = Modifier.width(22.dp).height(22.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text(tab.label, color = TextDark, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
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
