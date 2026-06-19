@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.demonv.netsessiontester

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import kotlin.math.roundToInt

private const val APP_GITHUB_URL = "https://github.com/OnlyChallgener/NetSessionTester"

private fun appVersionLabel(context: Context): String {
    return runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        "v${info.versionName.orEmpty().substringBefore("-").ifBlank { "0.9.8" }}"
    }.getOrDefault("v0.9.8")
}

private enum class MainTab(val label: String, val mark: String) {
    SETTINGS("设置", "settings"),
    TEST("测试", "play"),
    LOGS("历史", "logs")
}

private enum class ChartUiMode(val label: String) {
    SHARE("分享优先"),
    ADVANCED("分析增强")
}

private data class ChartPoint(
    val protocol: IpProtocol,
    val elapsedSec: Int,
    val active: Int,
    val failure: Int,
    val cps: Int,
    val phase: String,
    val timeEpochMs: Long = System.currentTimeMillis()
)

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
    var testNetworkSignature by remember { mutableStateOf("") }
    var chartUiMode by remember { mutableStateOf(ChartUiMode.SHARE) }
    var chartPoints by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }
    var lastChartSampleAt by remember { mutableStateOf<Map<IpProtocol, Long>>(emptyMap()) }

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

    fun appendLog(line: LogLine) {
        state = state.copy(logs = (state.logs + line).takeLast(500))
        scope.launch {
            runCatching {
                logStore.append(line)
                logSizeKb = logStore.sizeKb()
            }
        }
    }

    fun resetCurrentChart() {
        chartPoints = emptyList()
        lastChartSampleAt = emptyMap()
    }

    fun recordChartPoint(stats: ProtocolStats) {
        if (currentStartedAt <= 0L) return
        val now = System.currentTimeMillis()
        val terminal = stats.phase.contains("测试完成") ||
            stats.phase.contains("已释放") ||
            stats.phase.contains("地址丢失") ||
            stats.phase.contains("解析失败") ||
            stats.phase.contains("中断") ||
            stats.phase.contains("FD上限") ||
            stats.phase.contains("失败上限")

        val last = lastChartSampleAt[stats.protocol] ?: 0L
        if (!terminal && now - last < 1_000L) return

        val elapsedSec = ((now - currentStartedAt) / 1_000L).toInt().coerceAtLeast(0)
        val point = ChartPoint(
            protocol = stats.protocol,
            elapsedSec = elapsedSec,
            active = stats.activeSessions,
            failure = stats.totalFailure,
            cps = stats.cps,
            phase = stats.phase,
            timeEpochMs = now
        )

        // 当前测试曲线只保留本次数据，最多 240 点；同一协议同一秒只保留最新点。
        chartPoints = (chartPoints
            .filterNot { it.protocol == point.protocol && it.elapsedSec == point.elapsedSec } + point)
            .sortedWith(compareBy<ChartPoint> { it.protocol.ordinal }.thenBy { it.elapsedSec })
            .takeLast(240)

        lastChartSampleAt = lastChartSampleAt + (stats.protocol to now)
    }

    fun isFdLimitSummary(summary: SessionSummary): Boolean {
        return listOfNotNull(summary.ipv4Stats, summary.ipv6Stats).any { stats ->
            stats.phase.contains("FD", ignoreCase = true) ||
                stats.errorSummary.keys.any { key ->
                    key.contains("FD", ignoreCase = true) ||
                        key.contains("too many open files", ignoreCase = true) ||
                        key.contains("EMFILE", ignoreCase = true)
                }
        }
    }

    suspend fun persistHistorySafely(summary: SessionSummary) {
        val fdLimited = isFdLimitSummary(summary)

        if (fdLimited) {
            val closed = tester.release()
            appendLog(LogLine(level = LogLevel.WARN, text = "检测到FD上限，为避免闪退已先释放本机连接：$closed 条，然后保存历史"))
        }

        val first = runCatching {
            historyStore.append(summary)
            historyStore.trim(100)
        }

        if (first.isFailure) {
            val message = first.exceptionOrNull()?.message ?: first.exceptionOrNull()?.javaClass?.simpleName ?: "未知错误"
            appendLog(LogLine(level = LogLevel.ERROR, text = "保存历史失败：$message，尝试释放连接后重试"))
            val closed = tester.release()
            appendLog(LogLine(level = LogLevel.WARN, text = "为保存历史已释放连接：$closed 条"))
            runCatching {
                historyStore.append(summary)
                historyStore.trim(100)
            }.onFailure { retryError ->
                appendLog(LogLine(level = LogLevel.ERROR, text = "历史重试保存失败：${retryError.message ?: retryError.javaClass.simpleName}"))
            }
        }

        refreshHistory()
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
        testNetworkSignature = currentNetworkSignature(context)
        manualStopRequested = false
        resetCurrentChart()
        state = state.copy(
            isAdding = true,
            status = "建连中",
            ipv4Stats = ProtocolStats(IpProtocol.IPV4),
            ipv6Stats = ProtocolStats(IpProtocol.IPV6),
            summary = null,
            error = null
        )
        appendLog(LogLine(level = LogLevel.INFO, text = "目标：${config.host}:${config.port} | 模式：${config.mode.label} | 新增：${config.batchSize} | 间隔：${config.intervalMs}ms"))

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
                        val terminal = stats.phase.contains("测试完成") ||
                            stats.phase.contains("已释放") ||
                            stats.phase.contains("地址丢失") ||
                            stats.phase.contains("解析失败") ||
                            stats.phase.contains("中断")
                        state = when (stats.protocol) {
                            IpProtocol.IPV4 -> state.copy(
                                ipv4Stats = stats,
                                status = "${stats.protocol.label} ${stats.phase}",
                                isAdding = !terminal
                            )
                            IpProtocol.IPV6 -> state.copy(
                                ipv6Stats = stats,
                                status = "${stats.protocol.label} ${stats.phase}",
                                isAdding = !terminal
                            )
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
                val hitFdGuard = listOfNotNull(summary?.ipv4Stats, summary?.ipv6Stats).any {
                    it.phase.contains("FD上限") || it.errorSummary.containsKey("FD上限")
                }
                state = state.copy(
                    isAdding = false,
                    status = if (hitFdGuard) "FD上限 · 收尾中" else "测试完成 · 收尾中",
                    summary = summary
                )
                persistHistorySafely(summary!!)
                completedNormally = true
            } catch (error: Exception) {
                if (!manualStopRequested) {
                    failureMsg = error.message ?: error.javaClass.simpleName
                    state = state.copy(isAdding = false, status = "测试中断 · 收尾中", error = failureMsg)
                    appendLog(LogLine(level = LogLevel.ERROR, text = "测试中断：$failureMsg"))
                    val abortReason = failureMsg ?: "测试中断"
                    val abortIpv4 = if (config.mode != TestMode.IPV6_ONLY) {
                        state.ipv4Stats.copy(
                            phase = abortReason,
                            errorSummary = state.ipv4Stats.errorSummary + (abortReason to 1)
                        )
                    } else null
                    val abortIpv6 = if (config.mode != TestMode.IPV4_ONLY && state.ipv6Stats.totalAttempts > 0) {
                        state.ipv6Stats.copy(
                            phase = abortReason,
                            errorSummary = state.ipv6Stats.errorSummary + (abortReason to 1)
                        )
                    } else null
                    val abortSummary = SessionSummary(
                        startedAtEpochMs = startedAt,
                        host = config.host,
                        port = config.port,
                        mode = config.mode,
                        ipv4Stats = abortIpv4,
                        ipv6Stats = abortIpv6
                    )
                    summary = abortSummary
                    persistHistorySafely(abortSummary)
                }
            } finally {
                if (!manualStopRequested) {
                    val closed = tester.release()
                    if (completedNormally) {
                        appendLog(LogLine(level = LogLevel.WARN, text = "测试完成，本机已释放：$closed 条；路由器会话表可能延迟数秒下降"))
                    } else {
                        appendLog(LogLine(level = LogLevel.WARN, text = "测试结束收尾，本机已释放：$closed 条；路由器会话表可能延迟数秒下降"))
                    }
                    snackbarHostState.showSnackbar("本机已释放，路由器会话表可能延迟数秒下降")

                    val finalSummary = summary
                    val finalIpv4 = finalSummary?.ipv4Stats ?: state.ipv4Stats
                    val finalIpv6 = finalSummary?.ipv6Stats ?: state.ipv6Stats

                    val hitFdGuard = listOfNotNull(finalIpv4, finalIpv6).any {
                        it.phase.contains("FD上限") || it.errorSummary.containsKey("FD上限")
                    }

                    state = state.copy(
                        isAdding = false,
                        status = when {
                            hitFdGuard -> "FD上限 · 已释放"
                            completedNormally -> "测试完成 · 已释放"
                            else -> "测试中断 · 已释放"
                        },
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
            persistHistorySafely(summary)
            state = state.copy(
                summary = summary,
                ipv4Stats = ipv4 ?: state.ipv4Stats,
                ipv6Stats = ipv6 ?: state.ipv6Stats
            )
        }
    }

    fun stopAdding() {
        manualStopRequested = true
        state = state.copy(isAdding = false, status = "手动停止 · 收尾中")
        runningJob?.cancel()
        runningJob = null
        appendLog(LogLine(level = LogLevel.WARN, text = "手动停止；已停止新增并保存历史。"))
        saveManualStopSummary("手动停止")
        scope.launch {
            val closed = tester.release()
            appendLog(LogLine(level = LogLevel.WARN, text = "手动停止，本机已释放：$closed 条；路由器会话表可能延迟数秒下降"))
            snackbarHostState.showSnackbar("本机已释放，路由器会话表可能延迟数秒下降")
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
            state = state.copy(isAdding = false, status = "强制释放 · 收尾中")
            runningJob?.cancel()
            runningJob = null
            appendLog(LogLine(level = LogLevel.WARN, text = "强制释放；已停止测试并保存历史。"))
            saveManualStopSummary("强制释放")
        } else {
            runningJob?.cancel()
            runningJob = null
        }
        scope.launch {
            val closed = tester.release()
            appendLog(LogLine(level = LogLevel.WARN, text = "本机已释放：$closed 条；路由器会话表可能延迟数秒下降"))
            snackbarHostState.showSnackbar("本机已释放，路由器会话表可能延迟数秒下降")
            state = state.copy(
                isAdding = false,
                status = "本机已释放",
                ipv4Stats = state.ipv4Stats.copy(activeSessions = 0, phase = if (state.ipv4Stats.totalAttempts > 0) "已释放" else state.ipv4Stats.phase),
                ipv6Stats = state.ipv6Stats.copy(activeSessions = 0, phase = if (state.ipv6Stats.totalAttempts > 0) "已释放" else state.ipv6Stats.phase)
            )
            stopForegroundNotice(context)
        }
    }

    fun abortRunningTest(reason: String) {
        if (!state.isAdding) return
        manualStopRequested = true
        state = state.copy(isAdding = false, status = "$reason · 已中止")
        runningJob?.cancel()
        runningJob = null
        appendLog(LogLine(level = LogLevel.ERROR, text = "$reason，测试已中止"))
        saveManualStopSummary(reason)
        scope.launch {
            val closed = tester.release()
            appendLog(LogLine(level = LogLevel.WARN, text = "$reason，本机已释放：$closed 条；路由器会话表可能延迟数秒下降"))
            snackbarHostState.showSnackbar("本机已释放，路由器会话表可能延迟数秒下降")
            state = state.copy(
                isAdding = false,
                status = "$reason · 已中止",
                ipv4Stats = state.ipv4Stats.copy(activeSessions = 0, phase = if (state.ipv4Stats.totalAttempts > 0) "已中止" else state.ipv4Stats.phase),
                ipv6Stats = state.ipv6Stats.copy(activeSessions = 0, phase = if (state.ipv6Stats.totalAttempts > 0) "已中止" else state.ipv6Stats.phase)
            )
            updateForegroundNotice(context, "$reason，测试已中止")
            stopForegroundNotice(context)
        }
    }

    LaunchedEffect(state.isAdding, testNetworkSignature) {
        if (state.isAdding && testNetworkSignature.isNotBlank()) {
            while (state.isAdding) {
                delay(1000)
                val nowSignature = currentNetworkSignature(context)
                if (nowSignature == "NONE" || nowSignature != testNetworkSignature) {
                    abortRunningTest("网络切换或地址丢失")
                    break
                }
            }
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
        HistoryDetailDialog(
            summary = detailSummary!!,
            maskPrivacy = maskPrivacy,
            onDismiss = { detailSummary = null }
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
                    isAdding = state.isAdding ||
                        (runningJob?.isActive == true) ||
                        state.status.contains("建连中") ||
                        state.status.contains("运行") ||
                        state.ipv4Stats.phase.contains("建连中") ||
                        state.ipv6Stats.phase.contains("建连中"),
                    onStart = { startTest() },
                    onStopAdding = { stopAdding() },
                    onRelease = { releaseAll() },
                    onExport = { exportLogs() },
                    ipv4Stats = state.ipv4Stats,
                    ipv6Stats = state.ipv6Stats,
                    lastIpv4Active = latestActiveFromHistory(state.history, IpProtocol.IPV4),
                    lastIpv6Active = latestActiveFromHistory(state.history, IpProtocol.IPV6),
                    chartUiMode = chartUiMode,
                    chartPoints = chartPoints,
                    batchSizeSetting = batchSize,
                    failureLimitSetting = failureLimit,
                    onChartModeChange = { chartUiMode = it },
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
                    onDeleteHistory = { summary ->
                        scope.launch {
                            historyStore.delete(summary.id)
                            refreshHistory()
                            snackbarHostState.showSnackbar("已删除该条检测历史")
                        }
                    },
                    onHistoryDetail = { summary ->
                        detailSummary = summary
                    }
                )
            }
        }
    }
}

@Composable
private fun HistoryDetailDialog(summary: SessionSummary, maskPrivacy: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("检测详情", fontWeight = FontWeight.ExtraBold, color = TextDark, fontSize = 22.sp)
                Text(summary.startedAtText, color = Muted, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryTopResultCard(summary, maskPrivacy)
                HistoryNetworkSnapshotCard()
                summary.ipv4Stats?.let { HistoryProtocolResultCard("IPv4 结果", it, maskPrivacy) }
                summary.ipv6Stats?.let { HistoryProtocolResultCard("IPv6 结果", it, maskPrivacy) }
                HistoryResultChartCard(summary)
                HistoryDiagnosisDetailCard(summary)
                HistoryFailureSummaryCard(summary)
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
private fun HistoryTopResultCard(summary: SessionSummary, maskPrivacy: Boolean) {
    val abnormal = isAbnormalSummary(summary)
    val credibility = if (abnormal) "可信度低" else "可作为参考"
    val host = if (maskPrivacy) maskIpText(summary.host) else summary.host
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("◎", "结果摘要", if (abnormal) ErrorRed else Blue)
            Spacer(Modifier.weight(1f))
            StatusChip(if (abnormal) "异常中断" else "检测记录", if (abnormal) RedSoft else GreenSoft, if (abnormal) ErrorRed else Green, compact = true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(summary.mode.label, BlueSoft, Blue, compact = true)
            StatusChip(credibility, if (abnormal) RedSoft else GreenSoft, if (abnormal) ErrorRed else Green, compact = true)
        }
        InfoLine("目标", "$host:${summary.port}")
        InfoLine("时间", summary.startedAtText)
        if (abnormal) {
            Text("本次测试过程被打断，不能直接当作宽带/路由器会话上限。建议保持网络不切换后重新测试。", color = Orange, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoryNetworkSnapshotCard() {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("∿", "网络环境", Purple)
            Spacer(Modifier.weight(1f))
            StatusChip("历史快照", Color(0xFFF3E8FF), Purple, compact = true)
        }
        Text(
            "该历史详情目前按测试结果推断网络问题。后续可保存 WiFi/蜂窝、运营商、4G/5G、VPN、IPv4/IPv6出口快照，用于更准确诊断。",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun HistoryProtocolResultCard(title: String, stats: ProtocolStats, maskPrivacy: Boolean) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("▮", title, Blue)
            Spacer(Modifier.weight(1f))
            Text(stats.phase, color = if (isAbnormalPhase(stats.phase)) ErrorRed else Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("活动", stats.activeSessions.toString(), Blue, Modifier.weight(1f))
            MetricTile("失败", stats.totalFailure.toString(), ErrorRed, Modifier.weight(1f))
            MetricTile("总计", stats.totalAttempts.toString(), Navy, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun HistoryResultChartCard(summary: SessionSummary) {
    val stats = listOfNotNull(summary.ipv4Stats, summary.ipv6Stats)
    if (stats.isEmpty()) return
    val maxValue = stats.maxOf { maxOf(1, protocolPeak(it), it.totalFailure, it.totalAttempts) }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("▮", "历史图表", Green)
            Spacer(Modifier.weight(1f))
            Text("摘要条形图", color = Muted, fontSize = 11.sp)
        }
        Text("旧历史记录未保存完整曲线，这里用活动/失败/总计摘要展示，便于截图分享。", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        stats.forEach { s ->
            HistoryBarRow("${s.protocol.label} 活动", protocolPeak(s), maxValue, Blue)
            HistoryBarRow("${s.protocol.label} 失败", s.totalFailure, maxValue, ErrorRed)
            HistoryBarRow("${s.protocol.label} 总计", s.totalAttempts, maxValue, Navy)
        }
    }
}

@Composable
private fun HistoryBarRow(label: String, value: Int, maxValue: Int, color: Color) {
    val fraction = (value.toFloat() / maxValue.toFloat()).coerceIn(0.02f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(24.dp)) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.width(72.dp), maxLines = 1)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(color.copy(alpha = 0.13f), RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .background(color, RoundedCornerShape(8.dp))
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(value.toString(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp), maxLines = 1)
    }
}

@Composable
private fun HistoryDiagnosisDetailCard(summary: SessionSummary) {
    val abnormal = isAbnormalSummary(summary)
    val ipv4 = summary.ipv4Stats
    val ipv6 = summary.ipv6Stats
    val v4Peak = ipv4?.let { protocolPeak(it) } ?: 0
    val v6Peak = ipv6?.let { protocolPeak(it) } ?: 0
    val onlyV4 = ipv4 != null && ipv6 == null
    val onlyV6 = ipv6 != null && ipv4 == null
    val fdLimit = listOfNotNull(ipv4, ipv6).any { s -> s.phase.contains("FD", true) || s.errorSummary.keys.any { it.contains("FD", true) } }

    val title: String
    val lines: List<String>
    when {
        abnormal -> {
            title = "测试非正常中断，结果可信度低"
            lines = listOf(
                "保持 WiFi/蜂窝/VPN 不切换后重新测试。",
                "如果出现地址丢失，先重新解析 DNS，再分 IPv4 / IPv6 单独测试。",
                "蜂窝网络频繁中断时，建议切换 4G/5G 或更换 APN 复测。",
                "如 APP 被后台限制，请关闭省电限制并保持前台。"
            )
        }
        fdLimit -> {
            title = "可能达到手机端 FD / Socket 上限"
            lines = listOf(
                "这不一定代表宽带或路由器会话数到顶。",
                "建议降低新增批次，或分 IPv4 / IPv6 单独测试。",
                "如果路由器会话表仍在增长，以路由器侧数据为重要参考。"
            )
        }
        onlyV4 && v4Peak in 1..999 -> {
            title = "仅 IPv4 低会话，建议补测 IPv6"
            lines = listOf(
                "本次只有 IPv4，无法直接判断是否为运营商 IPv4 CGNAT。",
                "建议再做 IPv6 单独测试或分别测试。",
                "如果 IPv6 明显高于 IPv4，优先怀疑 IPv4 NAT / CGNAT 出口限制。"
            )
        }
        onlyV6 -> {
            title = "仅 IPv6 结果，可作为公网链路参考"
            lines = listOf(
                "建议补测 IPv4，用于对比是否存在 IPv4 CGNAT/NAT限制。",
                "如果 IPv6 高、IPv4 低，优先看 IPv4 出口策略。"
            )
        }
        ipv4 != null && ipv6 != null && v4Peak in 1..999 && v6Peak >= 5000 -> {
            title = "IPv4低、IPv6高，优先怀疑 IPv4 NAT / CGNAT 限制"
            lines = listOf(
                "手机本身限制概率较低，因为 IPv6 能维持较高会话。",
                "蜂窝网络下优先看运营商 IPv4 出口策略。",
                "WiFi 下优先看路由器 IPv4 NAT 或宽带 IPv4 会话策略。"
            )
        }
        ipv4 != null && ipv6 != null && v4Peak < 1000 && v6Peak < 1000 -> {
            title = "IPv4 / IPv6 都偏低，建议排查环境和目标站"
            lines = listOf(
                "可能是目标站限制、网络质量差、基站拥塞或本机资源异常。",
                "建议更换目标站、换时间段、切换 WiFi/蜂窝复测。"
            )
        }
        else -> {
            title = "结果可作为本次网络会话能力参考"
            lines = listOf(
                "建议保存截图，对比不同运营商、WiFi/蜂窝、IPv4/IPv6。",
                "如果需要判断 IPv4 是否为瓶颈，优先使用分别测试模式。"
            )
        }
    }

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("!", "诊断建议", Orange)
            Spacer(Modifier.weight(1f))
            StatusChip(if (abnormal) "异常中断" else "自动分析", if (abnormal) RedSoft else BlueSoft, if (abnormal) ErrorRed else Blue, compact = true)
        }
        Text(title, color = TextDark, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            lines.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("${index + 1}.", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                    Text(item, color = Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HistoryFailureSummaryCard(summary: SessionSummary) {
    val errors = mergedErrors(listOfNotNull(summary.ipv4Stats, summary.ipv6Stats))
    if (errors.isEmpty()) return
    SoftCard {
        SectionTitle("!", "失败原因", ErrorRed)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            errors.entries.sortedByDescending { it.value }.take(8).forEach { (name, count) ->
                ReasonChip("$name $count")
            }
        }
    }
}

private fun protocolPeak(stats: ProtocolStats): Int = maxOf(stats.activeSessions, stats.maxStableSessions, stats.totalSuccess)

private fun isAbnormalSummary(summary: SessionSummary): Boolean {
    return listOfNotNull(summary.ipv4Stats, summary.ipv6Stats).any { stats ->
        isAbnormalPhase(stats.phase) || stats.errorSummary.keys.any { key ->
            key.contains("地址丢失") || key.contains("中断") || key.contains("解析失败") || key.contains("异常中断")
        }
    }
}

private fun isAbnormalPhase(phase: String): Boolean {
    return phase.contains("中断") || phase.contains("地址丢失") || phase.contains("解析失败") || phase.contains("异常")
}


@Composable
private fun SettingsPage(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    result: com.demonv.netsessiontester.model.ResolveResult,
    publicIpResult: PublicIpResult,
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
        item { PageTitle("宽带会话测试器", "网络总会话数测试 - IPV4/IPV6分别测试。") }
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
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("◎", "公网出口", Blue)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onRefreshPublicIp) {
                        Text(if (publicIpLoading) "检测中" else "刷新", fontSize = 12.sp)
                    }
                }
                PublicExitLine(
                    label = "IPv4 出口地址",
                    value = if (maskPrivacy) maskIpText(publicIpResult.ipv4) else publicIpResult.ipv4,
                    copyValue = publicIpResult.ipv4,
                    onCopy = onCopyPublicIpv4
                )
                PublicExitLine(
                    label = "IPv6 出口地址",
                    value = if (maskPrivacy) maskIpText(publicIpResult.ipv6) else publicIpResult.ipv6,
                    copyValue = publicIpResult.ipv6,
                    onCopy = onCopyPublicIpv6
                )
                Text("点击地址可复制。显示当前网络访问互联网时对外可见的出口地址", color = Muted, fontSize = 10.sp, maxLines = 1)
            }
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
    isAdding: Boolean,
    onStart: () -> Unit,
    onStopAdding: () -> Unit,
    onRelease: () -> Unit,
    onExport: () -> Unit,
    ipv4Stats: ProtocolStats,
    ipv6Stats: ProtocolStats,
    lastIpv4Active: Int,
    lastIpv6Active: Int,
    chartUiMode: ChartUiMode,
    chartPoints: List<ChartPoint>,
    batchSizeSetting: String,
    failureLimitSetting: String,
    onChartModeChange: (ChartUiMode) -> Unit,
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
            PageTitle("宽带会话测试器", "网络总会话数测试 - IPV4/IPV6分别测试。")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusChip(mode.label, BlueSoft, Blue)
                StatusChip(if (isAdding) "● 运行中" else status, GreenSoft, Green)
                StatusChip("◎ 目标 $target", Color.White, TextDark)
            }
        }
        item {
            SoftCard {
                SectionTitle("∿", "测试控制", Blue)
                Text(if (isAdding) "● 正在运行 · 已连接目标" else "状态：$status", color = if (isAdding) Green else Muted, fontWeight = FontWeight.Medium, fontSize = 12.sp)
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
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRelease, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) { Icon(Icons.Filled.DeleteOutline, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("强制释放", fontSize = 13.sp) }
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f).height(38.dp), shape = ShapeM) { Icon(Icons.Filled.Download, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("导出", fontSize = 13.sp) }
                }
            }
        }
        if (showIpv4) item { SessionStatsCard("IPv4 会话", ipv4Stats, maskPrivacy, lastIpv4Active) }
        if (showIpv6) item { SessionStatsCard("IPv6 会话", ipv6Stats, maskPrivacy, lastIpv6Active) }
        item {
            CurrentTestChartCard(
                mode = mode,
                chartUiMode = chartUiMode,
                chartPoints = chartPoints,
                ipv4Stats = ipv4Stats,
                ipv6Stats = ipv6Stats,
                batchSizeSetting = batchSizeSetting,
                failureLimitSetting = failureLimitSetting,
                onModeChange = onChartModeChange
            )
        }
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
private fun PageTitle(title: String, subtitle: String?) {
    var showVersionDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val versionLabel = remember { appVersionLabel(context) }

    Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
            if (title == "宽带会话测试器") {
                Spacer(Modifier.width(8.dp))
                Text(
                    versionLabel,
                    fontSize = 10.sp,
                    color = Muted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .clickable { showVersionDialog = true }
                )
            }
        }
        subtitle?.let {
            Text(it, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showVersionDialog = false
                    uriHandler.openUri(APP_GITHUB_URL)
                }) {
                    Text("打开 GitHub", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVersionDialog = false }) {
                    Text("关闭", fontSize = 13.sp)
                }
            },
            title = {
                Column {
                    Text("当前版本 $versionLabel", fontWeight = FontWeight.ExtraBold, color = TextDark, fontSize = 18.sp)
                    Text("网络总会话数测试", color = Muted, fontSize = 12.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VersionLine("v0.9.8", "当前版本：升级检测详情为 OneUI/Material3 卡片式详情，增加历史摘要图表和非正常中断诊断建议。")
                    VersionLine("v0.9.7", "修复 FD 上限附近闪退；真实 FD上限时先释放文件句柄再保存历史，避免有日志无历史。")
                    VersionLine("v0.9.5", "修复停止按钮、通知跳转、新增批次被 200 锁死的问题。")
                    VersionLine("v0.9.4", "分批并发普通释放；增加本机已释放、路由器会话表可能延迟下降提示。")
                    VersionLine("v0.9.3", "修复 GitHub Actions 编译错误。")
                    VersionLine("v0.9.2", "首页副标题改为网络总会话数测试 - IPV4/IPV6分别测试。")
                }
            },
            shape = ShapeL,
            containerColor = Color.White
        )
    }
}

@Composable
private fun VersionLine(version: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(version, color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
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
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark)
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
private fun CurrentTestChartCard(
    mode: TestMode,
    chartUiMode: ChartUiMode,
    chartPoints: List<ChartPoint>,
    ipv4Stats: ProtocolStats,
    ipv6Stats: ProtocolStats,
    batchSizeSetting: String,
    failureLimitSetting: String,
    onModeChange: (ChartUiMode) -> Unit
) {
    val hasIpv4 = mode != TestMode.IPV6_ONLY
    val hasIpv6 = mode != TestMode.IPV4_ONLY
    val activeProtocol = when {
        chartPoints.any { it.protocol == IpProtocol.IPV6 } && ipv6Stats.phase !in listOf("待测试") -> IpProtocol.IPV6
        chartPoints.any { it.protocol == IpProtocol.IPV4 } -> IpProtocol.IPV4
        hasIpv6 && !hasIpv4 -> IpProtocol.IPV6
        else -> IpProtocol.IPV4
    }

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("▮", "本次测试图表", Blue)
            Spacer(Modifier.weight(1f))
            Text("仅当前一次", color = Muted, fontSize = 11.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = chartUiMode == ChartUiMode.SHARE,
                onClick = { onModeChange(ChartUiMode.SHARE) },
                label = { Text("分享优先", fontSize = 12.sp) }
            )
            FilterChip(
                selected = chartUiMode == ChartUiMode.ADVANCED,
                onClick = { onModeChange(ChartUiMode.ADVANCED) },
                label = { Text("分析增强", fontSize = 12.sp) }
            )
        }

        if (chartPoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .background(Color(0xFFF8FAFC), ShapeM)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("开始测试后显示本次曲线；下次测试会自动清空重绘。", color = Muted, fontSize = 12.sp)
            }
            return@SoftCard
        }

        when (chartUiMode) {
            ChartUiMode.SHARE -> {
                ProtocolOverviewRow(
                    ipv4Stats = if (hasIpv4) ipv4Stats else null,
                    ipv6Stats = if (hasIpv6) ipv6Stats else null
                )
                val currentStats = if (activeProtocol == IpProtocol.IPV6) ipv6Stats else ipv4Stats
                val currentPoints = chartPoints.filter { it.protocol == activeProtocol }
                ChartSection(
                    title = "${activeProtocol.label} 会话增长曲线",
                    subtitle = "图表 1s 采样，不影响实际连接测试",
                    points = currentPoints
                )
                BatchDiagnosisCard(
                    stats = currentStats,
                    batchSizeSetting = batchSizeSetting,
                    failureLimitSetting = failureLimitSetting
                )
            }

            ChartUiMode.ADVANCED -> {
                if (hasIpv4) {
                    ChartSection(
                        title = "IPv4 独立曲线",
                        subtitle = "非公网/NAT限制时，单独Y轴显示更清楚",
                        points = chartPoints.filter { it.protocol == IpProtocol.IPV4 }
                    )
                }
                if (hasIpv6) {
                    ChartSection(
                        title = "IPv6 独立曲线",
                        subtitle = "公网链路通常耗时更长，独立显示避免压扁IPv4",
                        points = chartPoints.filter { it.protocol == IpProtocol.IPV6 }
                    )
                }
                PingSummaryPlaceholder()
            }
        }
    }
}

@Composable
private fun ProtocolOverviewRow(ipv4Stats: ProtocolStats?, ipv6Stats: ProtocolStats?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ipv4Stats?.let {
            ProtocolMiniResult(
                title = "IPv4",
                tag = if (it.totalFailure > 0) "失败 ${it.totalFailure}" else "完成",
                active = maxOf(it.activeSessions, it.maxStableSessions, it.totalSuccess),
                attempts = it.totalAttempts,
                modifier = Modifier.weight(1f)
            )
        }
        ipv6Stats?.let {
            ProtocolMiniResult(
                title = "IPv6",
                tag = if (it.totalFailure > 0) "失败 ${it.totalFailure}" else "完成",
                active = maxOf(it.activeSessions, it.maxStableSessions, it.totalSuccess),
                attempts = it.totalAttempts,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProtocolMiniResult(title: String, tag: String, active: Int, attempts: Int, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = ShapeM,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(tag, color = if (tag.startsWith("失败")) ErrorRed else Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(active.toString(), color = Navy, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("活动会话｜总尝试 $attempts", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChartSection(title: String, subtitle: String, points: List<ChartPoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(Color(0xFFF8FAFC), ShapeM),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无该协议曲线", color = Muted, fontSize = 12.sp)
            }
        } else {
            SimpleSessionChart(points)
        }
    }
}

@Composable
private fun SimpleSessionChart(points: List<ChartPoint>) {
    val sorted = points.sortedBy { it.elapsedSec }
    val maxActive = sorted.maxOfOrNull { it.active } ?: 0
    val maxFailure = sorted.maxOfOrNull { it.failure } ?: 0
    val maxY = maxOf(1, maxActive, maxFailure)
    val maxX = maxOf(1, sorted.maxOfOrNull { it.elapsedSec } ?: 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFFFFF), ShapeM)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot("活动", Blue)
            Spacer(Modifier.width(10.dp))
            LegendDot("失败", ErrorRed)
            Spacer(Modifier.weight(1f))
            Text("峰值 $maxActive｜${maxX}s", color = Muted, fontSize = 10.sp)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(Color(0xFFF8FAFC), ShapeS)
                .padding(4.dp)
        ) {
            val w = size.width
            val h = size.height
            repeat(4) { index ->
                val y = h * (index + 1) / 5f
                drawLine(Border.copy(alpha = 0.55f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            fun makePath(valueOf: (ChartPoint) -> Int): Path {
                val path = Path()
                sorted.forEachIndexed { index, p ->
                    val x = w * (p.elapsedSec.toFloat() / maxX.toFloat())
                    val y = h - h * (valueOf(p).toFloat() / maxY.toFloat())
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                return path
            }
            if (sorted.size == 1) {
                val p = sorted.first()
                val x = w * (p.elapsedSec.toFloat() / maxX.toFloat())
                drawCircle(Blue, radius = 4f, center = Offset(x, h - h * (p.active.toFloat() / maxY.toFloat())))
                drawCircle(ErrorRed, radius = 4f, center = Offset(x, h - h * (p.failure.toFloat() / maxY.toFloat())))
            } else {
                drawPath(makePath { it.active }, color = Blue, style = Stroke(width = 4f))
                drawPath(makePath { it.failure }, color = ErrorRed, style = Stroke(width = 3f))
                sorted.lastOrNull()?.let { last ->
                    val x = w * (last.elapsedSec.toFloat() / maxX.toFloat())
                    drawCircle(Blue, radius = 5f, center = Offset(x, h - h * (last.active.toFloat() / maxY.toFloat())))
                    if (last.failure > 0) {
                        drawCircle(ErrorRed, radius = 5f, center = Offset(x, h - h * (last.failure.toFloat() / maxY.toFloat())))
                    }
                }
            }
        }

        Row {
            Text("0s", color = Muted, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text("${maxX}s", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun BatchDiagnosisCard(stats: ProtocolStats, batchSizeSetting: String, failureLimitSetting: String) {
    val batch = batchSizeSetting.toIntOrNull()?.coerceAtLeast(1) ?: 0
    val failureLimit = failureLimitSetting.toIntOrNull()?.coerceAtLeast(1) ?: 0
    val success = maxOf(stats.totalSuccess, stats.activeSessions)
    val failure = stats.totalFailure
    val attempted = stats.totalAttempts
    val notExecuted = if (batch > 0 && attempted < batch && failureLimit > 0 && failure >= failureLimit) batch - attempted else 0
    val shouldShow = batch > 0 && attempted > 0 && (attempted < batch || failure > 0)
    if (!shouldShow) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlueSoft.copy(alpha = 0.55f), ShapeM)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("本批诊断", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            if (notExecuted > 0) {
                "设定新增 $batch，实际尝试 $attempted；成功 $success，失败 $failure，未执行 $notExecuted。"
            } else {
                "设定新增 $batch，实际尝试 $attempted；成功 $success，失败 $failure。"
            },
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        if (notExecuted > 0) {
            Text("说明：新增是尝试上限；达到失败上限后会提前停止，属于正常保护。", color = Orange, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            DiagnosisBar("成功", success, maxOf(batch, attempted), Blue, Modifier.weight(success.coerceAtLeast(1).toFloat()))
            DiagnosisBar("失败", failure, maxOf(batch, attempted), ErrorRed, Modifier.weight(failure.coerceAtLeast(1).toFloat()))
            if (notExecuted > 0) DiagnosisBar("未执行", notExecuted, maxOf(batch, attempted), Muted, Modifier.weight(notExecuted.coerceAtLeast(1).toFloat()))
        }
    }
}

@Composable
private fun DiagnosisBar(label: String, value: Int, total: Int, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(color.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
        )
        Text("$label $value", color = color, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PingSummaryPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), ShapeM)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Ping摘要预留", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("建议：默认 1s Ping；高级模式可 200ms Ping，但图表仍按 1s 汇总平均/最高/超时。", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}


@Composable
private fun SessionStatsCard(title: String, stats: ProtocolStats, maskPrivacy: Boolean, lastActive: Int) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("▮", title, Blue)
            Spacer(Modifier.weight(1f))
            Text(stats.phase, color = Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("活动", stats.activeSessions.toString(), Blue, Modifier.weight(1f))
            MetricTile("上次活动", if (lastActive > 0) lastActive.toString() else "—", Navy, Modifier.weight(1f))
            MetricTile("失败", stats.totalFailure.toString(), ErrorRed, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                fontSize = 12.sp
            )
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
        Column(Modifier.padding(8.dp)) {
            Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
            Text(value, color = color, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
private fun SwipeDeleteHistoryCard(
    item: SessionSummary,
    maskPrivacy: Boolean,
    onClick: () -> Unit,
    onEditRemark: () -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember(item.id) { mutableStateOf(0f) }
    val density = LocalDensity.current
    val maxOffset = with(density) { -108.dp.toPx() }
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFFFF1F2), ErrorRed.copy(alpha = 0.92f))),
                        RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp, topStart = 12.dp, bottomStart = 12.dp)
                    )
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Color.White, modifier = Modifier.width(23.dp).height(23.dp))
                    Spacer(Modifier.height(5.dp))
                    Text("删除", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(maxOffset, 0f)
                        },
                        onDragEnd = {
                            offsetX = if (offsetX < maxOffset / 2f) maxOffset else 0f
                        },
                        onDragCancel = { offsetX = 0f }
                    )
                }
        ) {
            HistoryCard(
                item = item,
                maskPrivacy = maskPrivacy,
                onClick = onClick,
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
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = if (compact) 10.sp else 11.sp, maxLines = 1)
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
            .height(58.dp)
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
                    .height(54.dp)
                    .background(if (selected) Color(0xFFEBDCFD) else Color.Transparent, RoundedCornerShape(24.dp))
                    .clickable { onSelect(tab) }
                    .padding(vertical = 4.dp),
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
                Text(tab.label, color = TextDark, fontSize = 11.sp, lineHeight = 12.sp, maxLines = 1)
            }
        }
    }
}


private fun currentNetworkSignature(context: Context): String {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return "NONE"
    val caps = connectivityManager.getNetworkCapabilities(network) ?: return "NONE"
    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "NONE"
    val transport = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "OTHER"
    }
    return "$transport:${network.hashCode()}"
}

private fun latestActiveFromHistory(history: List<SessionSummary>, protocol: IpProtocol): Int {
    return history
        .asSequence()
        .sortedByDescending { it.startedAtEpochMs }
        .mapNotNull { summary ->
            val stats = when (protocol) {
                IpProtocol.IPV4 -> summary.ipv4Stats
                IpProtocol.IPV6 -> summary.ipv6Stats
            }
            stats?.let { maxOf(it.maxStableSessions, it.activeSessions, it.totalSuccess) }
        }
        .firstOrNull() ?: 0
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

