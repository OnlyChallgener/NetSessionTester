package com.demonv.netsessiontester

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.demonv.netsessiontester.data.HistoryStore
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
import com.demonv.netsessiontester.service.TestForegroundService
import com.demonv.netsessiontester.ui.theme.NetSessionTesterTheme
import com.demonv.netsessiontester.util.CsvExporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MainTab(val label: String, val icon: String) {
    SETTINGS("设置", "⚙"),
    TEST("测试", "▶"),
    LOGS("日志", "▣")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetSessionTesterApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val tester = remember { TcpTester() }
    val historyStore = remember { HistoryStore(context.applicationContext) }
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
                    snackbarHostState.showSnackbar("CSV / 日志已导出")
                }.onFailure {
                    snackbarHostState.showSnackbar("导出失败：${it.message}")
                }
                pendingCsv = null
            }
        } else {
            pendingCsv = null
        }
    }

    LaunchedEffect(Unit) {
        state = state.copy(history = historyStore.load())
        val saved = settingsStore.load()
        host = saved.host
        port = saved.port
        mode = saved.mode
        batchSize = saved.batchSize
        intervalMs = saved.intervalMs
        timeoutMs = saved.timeoutMs
        successLimit = saved.successLimit
        failureLimit = saved.failureLimit
        keepConnections = saved.keepConnections
        maskPrivacy = saved.maskPrivacy
        settingsLoaded = true
    }

    LaunchedEffect(
        settingsLoaded, host, port, mode, batchSize, intervalMs, timeoutMs,
        successLimit, failureLimit, keepConnections, maskPrivacy
    ) {
        if (settingsLoaded) {
            settingsStore.save(
                SavedSettings(
                    host = host,
                    port = port,
                    mode = mode,
                    batchSize = batchSize,
                    intervalMs = intervalMs,
                    timeoutMs = timeoutMs,
                    successLimit = successLimit,
                    failureLimit = failureLimit,
                    keepConnections = keepConnections,
                    maskPrivacy = maskPrivacy
                )
            )
        }
    }

    fun appendLog(line: LogLine) {
        state = state.copy(logs = (state.logs + line).takeLast(800))
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
                host = host,
                port = port.toInt(),
                mode = mode,
                batchSize = batchSize.toInt(),
                intervalMs = intervalMs.toLong(),
                timeoutMs = timeoutMs.toInt(),
                successLimit = successLimit.toInt(),
                failureLimit = failureLimit.toInt(),
                keepConnectionsAfterStop = keepConnections
            ).normalized()
        }.getOrElse { error ->
            scope.launch { snackbarHostState.showSnackbar("参数错误：${error.message}") }
            return null
        }
        if (config.host.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("请填写域名、IPv4 或 IPv6 地址") }
            return null
        }
        return config
    }

    fun resolve() {
        val target = host.trim()
        if (target.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("请先填写域名或 IP") }
            return
        }
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
        scope.launch { tester.release() }
        selectedTab = MainTab.TEST
        startForegroundNotice(context, "建连中：${config.mode.label}，目标会话 ${config.successLimit}")
        val startedAt = System.currentTimeMillis()
        state = state.copy(
            isAdding = true,
            status = "建连中",
            ipv4Stats = ProtocolStats(IpProtocol.IPV4),
            ipv6Stats = ProtocolStats(IpProtocol.IPV6),
            logs = emptyList(),
            summary = null,
            error = null
        )
        appendLog(LogLine(level = LogLevel.INFO, text = "目标：${config.host}:${config.port}｜模式：${config.mode.label}｜新增：${config.batchSize}｜间隔：${config.intervalMs}ms"))
        appendLog(LogLine(level = LogLevel.WARN, text = "请只测试自有或已授权目标。"))

        runningJob = scope.launch {
            runCatching {
                tester.runSessionHoldTest(
                    rawConfig = config,
                    onStats = { stats ->
                        state = when (stats.protocol) {
                            IpProtocol.IPV4 -> state.copy(ipv4Stats = stats, status = "${stats.protocol.label} ${stats.phase}")
                            IpProtocol.IPV6 -> state.copy(ipv6Stats = stats, status = "${stats.protocol.label} ${stats.phase}")
                        }
                        updateForegroundNotice(context, "${stats.protocol.label} ${stats.phase}｜活动 ${stats.activeSessions}｜失败 ${stats.totalFailure}")
                    },
                    onLog = { line -> appendLog(line) }
                )
            }.onSuccess { pair ->
                val summary = SessionSummary(
                    startedAtEpochMs = startedAt,
                    host = config.host,
                    port = config.port,
                    mode = config.mode,
                    ipv4Stats = pair.first ?: state.ipv4Stats,
                    ipv6Stats = pair.second ?: state.ipv6Stats
                )
                historyStore.append(summary)
                state = state.copy(
                    isAdding = false,
                    status = "测试完成",
                    summary = summary,
                    history = historyStore.load()
                )
                if (config.keepConnectionsAfterStop) {
                    updateForegroundNotice(context, "测试完成，连接保持中；需要关闭时请返回 APP 点击释放连接")
                } else {
                    stopForegroundNotice(context)
                }
            }.onFailure { error ->
                val msg = error.message ?: error.javaClass.simpleName
                appendLog(LogLine(level = LogLevel.ERROR, text = "测试中断：$msg"))
                state = state.copy(isAdding = false, status = "已停止新增", error = msg)
                updateForegroundNotice(context, "测试中断：$msg；如有保持连接，请返回 APP 释放")
            }
        }
    }

    fun stopAdding() {
        runningJob?.cancel()
        runningJob = null
        appendLog(LogLine(level = LogLevel.WARN, text = "已停止新增；已建立连接继续保持。"))
        state = state.copy(isAdding = false, status = "已停止新增")
        updateForegroundNotice(context, "已停止新增，连接保持中；点击释放连接可关闭 socket")
    }

    fun releaseAll() {
        runningJob?.cancel()
        runningJob = null
        scope.launch {
            val closed = tester.release()
            appendLog(LogLine(level = LogLevel.WARN, text = "已释放连接：$closed 条"))
            state = state.copy(
                isAdding = false,
                status = "已释放连接",
                ipv4Stats = state.ipv4Stats.copy(activeSessions = 0, phase = "已释放"),
                ipv6Stats = state.ipv6Stats.copy(activeSessions = 0, phase = "已释放")
            )
            stopForegroundNotice(context)
        }
    }

    fun exportLogs() {
        val logs = if (maskPrivacy) maskedLogs(state.logs) else state.logs
        val summary = state.summary?.let { if (maskPrivacy) maskedSummary(it) else it }
        pendingCsv = summary?.let { CsvExporter.summaryCsv(it, logs) } ?: CsvExporter.logsCsv(logs)
        exportLauncher.launch("net-session-test-v04.csv")
    }

    fun clearLogsAndHistory() {
        scope.launch {
            historyStore.clear()
            state = state.copy(logs = emptyList(), history = emptyList())
            snackbarHostState.showSnackbar("日志与历史已清理")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            FrostedBottomBar(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF7FAFF), Color(0xFFF4F1F8), Color(0xFFFFFFFF))
                    )
                )
        ) {
            when (selectedTab) {
                MainTab.SETTINGS -> SettingsPage(
                    modifier = Modifier.padding(padding),
                    host = host,
                    onHostChange = { host = it },
                    port = port,
                    onPortChange = { port = it },
                    result = state.resolveResult,
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
                    keepConnections = keepConnections,
                    onKeepConnectionsChange = { keepConnections = it },
                    onSave = {
                        scope.launch { snackbarHostState.showSnackbar("参数已保存") }
                    },
                    onRestoreDefault = {
                        host = "www.baidu.com"
                        port = "80"
                        mode = TestMode.IPV4_THEN_IPV6
                        batchSize = "100"
                        intervalMs = "500"
                        timeoutMs = "3000"
                        successLimit = "65535"
                        failureLimit = "200"
                        keepConnections = true
                        maskPrivacy = false
                        scope.launch { snackbarHostState.showSnackbar("已恢复默认") }
                    }
                )
                MainTab.TEST -> TestPage(
                    modifier = Modifier.padding(padding),
                    mode = mode,
                    status = state.status,
                    target = successLimit,
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
                    onMoreLogs = { selectedTab = MainTab.LOGS }
                )
                MainTab.LOGS -> LogsPage(
                    modifier = Modifier.padding(padding),
                    logs = state.logs,
                    history = state.history,
                    maskPrivacy = maskPrivacy,
                    onExport = { exportLogs() },
                    onClear = { clearLogsAndHistory() },
                    onBack = { selectedTab = MainTab.TEST }
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(
    modifier: Modifier,
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    result: com.demonv.netsessiontester.model.ResolveResult,
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
    keepConnections: Boolean,
    onKeepConnectionsChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRestoreDefault: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { AppTitle(subtitle = "TCP 会话保持 · IPv4 / IPv6 分别测试") }
        item {
            GlassCard {
                SectionTitle(icon = "◎", title = "目标设置")
                ShortLabel("地址")
                SoftTextField(
                    value = host,
                    onValueChange = onHostChange,
                    placeholder = "www.baidu.com",
                    leading = "🌐"
                )
                ShortLabel("端口")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoftTextField(
                        value = port,
                        onValueChange = { onPortChange(it.filter(Char::isDigit)) },
                        placeholder = "80",
                        leading = "▣",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onResolve,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(56.dp)
                    ) { Text("🔍 解析") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("打码", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("隐藏 IP / 公网地址", color = Color(0xFF64748B), modifier = Modifier.weight(1f), maxLines = 1)
                    Switch(checked = maskPrivacy, onCheckedChange = onMaskPrivacyChange)
                }
                if (result.ipv4.isNotEmpty() || result.ipv6.isNotEmpty() || result.error != null) {
                    Text("IPv4：${displayIpList(result.ipv4, maskPrivacy)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("IPv6：${displayIpList(result.ipv6, maskPrivacy)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    result.error?.let { Text("错误：$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            GlassCard {
                SectionTitle(icon = "∿", title = "测试模式")
                ModeSegmented(mode = mode, onModeChange = onModeChange)
                Text("对 IPv4 / IPv6 分别进行会话保持测试。", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        item {
            GlassCard {
                SectionTitle(icon = "≡", title = "会话参数")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactNumberField("新增", batchSize, onBatchSizeChange, Modifier.weight(1f))
                    CompactNumberField("间隔ms", intervalMs, onIntervalMsChange, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactNumberField("超时ms", timeoutMs, onTimeoutMsChange, Modifier.weight(1f))
                    CompactNumberField("失败停", failureLimit, onFailureLimitChange, Modifier.weight(1f))
                }
                CompactNumberField("目标会话", successLimit, onSuccessLimitChange, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = keepConnections, onCheckedChange = onKeepConnectionsChange)
                    Spacer(Modifier.width(8.dp))
                    Text("完成后保持连接", style = MaterialTheme.typography.bodyMedium)
                }
                Text("默认：新增100，间隔500ms。", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSave,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).height(54.dp)
                ) { Text("保存参数", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onRestoreDefault,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).height(54.dp)
                ) { Text("恢复默认") }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun TestPage(
    modifier: Modifier,
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
    showIpv4: Boolean,
    showIpv6: Boolean,
    maskPrivacy: Boolean,
    logs: List<LogLine>,
    onMoreLogs: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AppTitle(null)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(modeLabel(mode), Color(0xFFEFF6FF), Color(0xFF2563EB))
                Chip(if (isAdding) "● 运行中" else status, Color(0xFFEAFBF0), Color(0xFF16A34A))
                Chip("◎ 目标 $target", Color(0xFFF8FAFC), Color(0xFF334155))
            }
        }
        item {
            GlassCard {
                SectionTitle(icon = "∿", title = "测试控制")
                Text(if (isAdding) "● 正在运行 · 已连接目标" else "状态：$status", color = if (isAdding) Color(0xFF16A34A) else Color(0xFF64748B), maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onStart, enabled = !isAdding, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(54.dp)) { Text("▶ 开始") }
                    OutlinedButton(onClick = onStopAdding, enabled = isAdding, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(54.dp)) { Text("■ 停止") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onRelease, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(50.dp)) { Text("释放") }
                    OutlinedButton(onClick = onExport, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(50.dp)) { Text("导出") }
                }
            }
        }
        if (showIpv4) item { SessionStatsCard("IPv4 会话", ipv4Stats, maskPrivacy) }
        if (showIpv6) item { SessionStatsCard("IPv6 会话", ipv6Stats, maskPrivacy) }
        item { FailureReasonCard(stats = listOf(ipv4Stats, ipv6Stats), onMore = onMoreLogs) }
        item { RecentLogCard(logs = logs, maskPrivacy = maskPrivacy, onMore = onMoreLogs) }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun LogsPage(
    modifier: Modifier,
    logs: List<LogLine>,
    history: List<SessionSummary>,
    maskPrivacy: Boolean,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("‹", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(end = 12.dp))
                Text("日志与历史", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onClear, shape = RoundedCornerShape(16.dp)) { Text("清理") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                Chip("全部", Color(0xFFEFF6FF), Color(0xFF2563EB))
                Chip("运行日志", Color.White.copy(alpha = 0.72f), Color(0xFF334155))
                Chip("检测历史", Color.White.copy(alpha = 0.72f), Color(0xFF334155))
                Chip("失败原因", Color.White.copy(alpha = 0.72f), Color(0xFF334155))
            }
        }
        item {
            GlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onExport, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)) { Text("导出日志") }
                    OutlinedButton(onClick = onClear, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)) { Text("清理日志") }
                }
            }
        }
        item { RecentLogCard(logs = logs, maskPrivacy = maskPrivacy, onMore = {}) }
        item {
            Text("检测历史", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
        if (history.isEmpty()) {
            item { GlassCard { Text("暂无历史记录") } }
        } else {
            items(history.take(20)) { item ->
                HistorySummaryCard(item, maskPrivacy)
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun AppTitle(subtitle: String?) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) {
        Text("宽带会话测试器", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
        subtitle?.let { Text(it, color = Color(0xFF64748B), style = MaterialTheme.typography.titleMedium, maxLines = 1) }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(icon: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEFF6FF))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(icon, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}

@Composable
private fun ShortLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        leadingIcon = { Text(leading) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun CompactNumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label, maxLines = 1) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSegmented(mode: TestMode, onModeChange: (TestMode) -> Unit) {
    val values = listOf(TestMode.IPV4_ONLY, TestMode.IPV6_ONLY, TestMode.IPV4_THEN_IPV6)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        values.forEachIndexed { index, item ->
            SegmentedButton(
                selected = mode == item,
                onClick = { onModeChange(item) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = values.size),
                label = { Text(if (item == TestMode.IPV4_THEN_IPV6) "分别测试" else item.label.replace("仅 ", "仅")) }
            )
        }
    }
}

@Composable
private fun SessionStatsCard(title: String, stats: ProtocolStats, maskPrivacy: Boolean) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(icon = "▮", title = title)
            Spacer(Modifier.weight(1f))
            Text(stats.phase, color = Color(0xFF2563EB), maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("活动", stats.activeSessions.toString(), Color(0xFF2563EB), Modifier.weight(1f))
            MetricTile("失败", stats.totalFailure.toString(), Color(0xFFEF4444), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("总计", stats.totalAttempts.toString(), Color(0xFF0F2F6E), Modifier.weight(1f))
            MetricTile("新增", stats.lastAdded.toString(), Color(0xFF1D4ED8), Modifier.weight(1f))
            MetricTile("CPS", "${stats.cps}/s", Color(0xFF2563EB), Modifier.weight(1f))
        }
        if (stats.resolvedAddresses.isNotEmpty()) {
            Text("地址：${displayIpList(stats.resolvedAddresses, maskPrivacy)}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.58f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Color(0xFF475569), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun FailureReasonCard(stats: List<ProtocolStats>, onMore: () -> Unit) {
    val merged = linkedMapOf<String, Int>()
    stats.forEach { s ->
        s.errorSummary.forEach { (k, v) -> merged[k.shortReason()] = (merged[k.shortReason()] ?: 0) + v }
    }
    if (merged.isEmpty()) return
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(icon = "!", title = "失败原因")
            Spacer(Modifier.weight(1f))
            Text("更多 ›", color = Color(0xFF64748B))
        }
        FlowReasonChips(merged)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowReasonChips(reasons: Map<String, Int>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reasons.entries.sortedByDescending { it.value }.take(6).forEach { (name, count) ->
            Chip("$name $count", Color(0xFFFFF1F2), Color(0xFFEF4444))
        }
    }
}

@Composable
private fun RecentLogCard(logs: List<LogLine>, maskPrivacy: Boolean, onMore: () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(icon = "□", title = "最近日志")
            Spacer(Modifier.weight(1f))
            Text("更多 ›", color = Color(0xFF64748B))
        }
        if (logs.isEmpty()) {
            Text("暂无日志", color = Color(0xFF64748B))
        } else {
            logs.takeLast(5).forEach { line ->
                CompactLogLine(line, maskPrivacy)
            }
        }
    }
}

@Composable
private fun CompactLogLine(line: LogLine, maskPrivacy: Boolean) {
    val tag = when (line.level) {
        LogLevel.STAT -> "统计"
        LogLevel.SUCCESS -> "成功"
        LogLevel.WARN -> "告警"
        LogLevel.ERROR -> "错误"
        LogLevel.INFO -> "信息"
    }
    val bg = when (line.level) {
        LogLevel.STAT -> Color(0xFFEFF6FF)
        LogLevel.SUCCESS -> Color(0xFFEAFBF0)
        LogLevel.WARN -> Color(0xFFFFF7ED)
        LogLevel.ERROR -> Color(0xFFFFF1F2)
        LogLevel.INFO -> Color(0xFFF8FAFC)
    }
    val fg = when (line.level) {
        LogLevel.STAT -> Color(0xFF2563EB)
        LogLevel.SUCCESS -> Color(0xFF16A34A)
        LogLevel.WARN -> Color(0xFFF97316)
        LogLevel.ERROR -> Color(0xFFEF4444)
        LogLevel.INFO -> Color(0xFF475569)
    }
    val text = if (maskPrivacy) maskIpText(line.text) else line.text
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(line.timeText.take(8), color = Color(0xFF64748B), modifier = Modifier.width(72.dp), maxLines = 1)
        Box(Modifier.clip(RoundedCornerShape(9.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(tag, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF334155), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HistorySummaryCard(item: SessionSummary, maskPrivacy: Boolean) {
    val mainStats = item.ipv4Stats ?: item.ipv6Stats
    val shownHost = if (maskPrivacy) maskIpText(item.host) else item.host
    val protocol = when {
        item.ipv4Stats != null && item.ipv6Stats != null -> "分别测试"
        item.ipv4Stats != null -> "IPv4 完成"
        item.ipv6Stats != null -> "IPv6 完成"
        else -> "完成"
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("▣ ${item.startedAtText.drop(5)}", color = Color(0xFF64748B), modifier = Modifier.weight(1f), maxLines = 1)
            Chip(protocol, if (protocol.contains("IPv")) Color(0xFFEAFBF0) else Color(0xFFEFF6FF), if (protocol.contains("IPv")) Color(0xFF16A34A) else Color(0xFF2563EB))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniInfo("目标", shownHost, Modifier.weight(1f))
            MiniInfo("端口", item.port.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniInfo("活动", (mainStats?.activeSessions ?: mainStats?.maxStableSessions ?: 0).toString(), Modifier.weight(1f))
            MiniInfo("失败", (mainStats?.totalFailure ?: 0).toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniInfo("总计", (mainStats?.totalAttempts ?: 0).toString(), Modifier.weight(1f))
            MiniInfo("CPS", "${mainStats?.cps ?: 0}/s", Modifier.weight(1f))
        }
        val reason = mainStats?.errorSummary.orEmpty().mapKeys { it.key.shortReason() }
        if (reason.isNotEmpty()) FlowReasonChips(reason)
    }
}

@Composable
private fun MiniInfo(label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.58f)), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color(0xFF64748B), modifier = Modifier.width(42.dp), maxLines = 1)
            Text(value, color = Color(0xFF334155), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 13.dp, vertical = 8.dp)) {
        Text(text, color = fg, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun FrostedBottomBar(selectedTab: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(
        containerColor = Color.White.copy(alpha = 0.82f),
        modifier = Modifier.navigationBarsPadding()
    ) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                icon = { Text(tab.icon) },
                label = { Text(tab.label) }
            )
        }
    }
}

private fun modeLabel(mode: TestMode): String = when (mode) {
    TestMode.IPV4_ONLY -> "🌐 IPv4"
    TestMode.IPV6_ONLY -> "🌐 IPv6"
    TestMode.IPV4_THEN_IPV6 -> "分别测试"
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

private fun String.shortReason(): String {
    return when {
        contains("超时") -> "超时"
        contains("拒绝") -> "拒绝"
        contains("端口耗尽") -> "端口耗尽"
        contains("FD") || contains("open files") -> "FD上限"
        contains("网络不可达") -> "网络不可达"
        contains("DNS") -> "DNS失败"
        contains("重置") -> "重置"
        else -> substringBefore("(").take(6)
    }
}

private val IPV4_REGEX = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
private val IPV6_REGEX = Regex("""(?i)(?<![\w.])(?:[0-9a-f]{1,4}:){2,}[0-9a-f]{0,4}(?:%[\w.]+)?(?![\w.])""")
