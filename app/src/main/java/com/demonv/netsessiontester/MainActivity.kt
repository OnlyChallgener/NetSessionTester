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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private enum class MainTab(val label: String, val mark: String) {
    SETTINGS("设置", "设"),
    TEST("测试", "测"),
    LOGS("日志", "志")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.rgb(246, 248, 252)
        window.navigationBarColor = android.graphics.Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            NetSessionTesterTheme(darkTheme = false) {
                NetSessionTesterApp()
            }
        }
    }
}

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
                    snackbarHostState.showSnackbar("已导出")
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
        settingsLoaded = true
    }

    LaunchedEffect(
        settingsLoaded, host, port, mode, batchSize, intervalMs, timeoutMs,
        successLimit, failureLimit, keepConnections, maskPrivacy
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
                host = host.ifBlank { "www.baidu.com" },
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
        return config
    }

    fun resolve() {
        val target = host.ifBlank { "www.baidu.com" }.trim()
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
        startForegroundNotice(context, "建连中：${config.mode.label}，目标 ${config.successLimit}")
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
        appendLog(LogLine(level = LogLevel.INFO, text = "目标：${config.host}:${config.port}｜${config.mode.label}｜新增：${config.batchSize}｜间隔：${config.intervalMs}ms"))

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
                    updateForegroundNotice(context, "测试完成，连接保持中")
                } else {
                    stopForegroundNotice(context)
                }
            }.onFailure { error ->
                val msg = error.message ?: error.javaClass.simpleName
                appendLog(LogLine(level = LogLevel.ERROR, text = "测试中断：$msg"))
                state = state.copy(isAdding = false, status = "已停止新增", error = msg)
                updateForegroundNotice(context, "测试中断：$msg")
            }
        }
    }

    fun stopAdding() {
        runningJob?.cancel()
        runningJob = null
        appendLog(LogLine(level = LogLevel.WARN, text = "已停止新增；连接继续保持。"))
        state = state.copy(isAdding = false, status = "已停止新增")
        updateForegroundNotice(context, "已停止新增，连接保持中")
    }

    fun releaseAll() {
        runningJob?.cancel()
        runningJob = null
        scope.launch {
            val closed = tester.release()
            appendLog(LogLine(level = LogLevel.WARN, text = "已释放连接：$closed 条"))
            state = state.copy(
                isAdding = false,
                status = "已释放",
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
            snackbarHostState.showSnackbar("已清理")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNav(selectedTab = selectedTab, onSelect = { selectedTab = it })
        },
        containerColor = Bg
    ) { padding ->
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
                onSave = { scope.launch { snackbarHostState.showSnackbar("参数已保存") } },
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
                onClear = { clearLogsAndHistory() }
            )
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageTitle("宽带会话测试器", "TCP 会话保持 · IPv4 / IPv6 分别测试") }
        item {
            Panel {
                SectionTitle("目标设置")
                FieldLabel("地址")
                CleanTextField(host, onHostChange, "www.baidu.com")
                FieldLabel("端口")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CleanNumberField(port, onPortChange, "80", Modifier.weight(1f))
                    Button(
                        onClick = onResolve,
                        shape = ShapeM,
                        modifier = Modifier.height(56.dp).width(110.dp)
                    ) { Text("解析") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("隐私打码", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = maskPrivacy, onCheckedChange = onMaskPrivacyChange)
                }
                if (result.ipv4.isNotEmpty() || result.ipv6.isNotEmpty() || result.error != null) {
                    Divider(color = Border)
                    InfoLine("IPv4", displayIpList(result.ipv4, maskPrivacy))
                    InfoLine("IPv6", displayIpList(result.ipv6, maskPrivacy))
                    result.error?.let { InfoLine("错误", it, ErrorRed) }
                }
            }
        }
        item {
            Panel {
                SectionTitle("测试模式")
                ModeSelector(mode, onModeChange)
            }
        }
        item {
            Panel {
                SectionTitle("会话参数")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamField("新增", batchSize, onBatchSizeChange, Modifier.weight(1f))
                    ParamField("间隔ms", intervalMs, onIntervalMsChange, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamField("超时ms", timeoutMs, onTimeoutMsChange, Modifier.weight(1f))
                    ParamField("失败停", failureLimit, onFailureLimitChange, Modifier.weight(1f))
                }
                ParamField("目标会话", successLimit, onSuccessLimitChange, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("完成后保持连接", modifier = Modifier.weight(1f))
                    Switch(checked = keepConnections, onCheckedChange = onKeepConnectionsChange)
                }
                Text("默认：新增 100，间隔 500ms", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onSave, modifier = Modifier.weight(1f).height(52.dp), shape = ShapeM) { Text("保存参数") }
                OutlinedButton(onClick = onRestoreDefault, modifier = Modifier.weight(1f).height(52.dp), shape = ShapeM) { Text("恢复默认") }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageTitle("宽带会话测试器", null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip(shortMode(mode), BlueSoft, Blue)
                SmallChip(if (isAdding) "运行中" else status, GreenSoft, Green)
                SmallChip("目标 $target", Color.White, TextDark)
            }
        }
        item {
            Panel {
                SectionTitle("测试控制")
                Text(if (isAdding) "正在运行 · 已连接目标" else "状态：$status", color = if (isAdding) Green else Muted, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onStart, enabled = !isAdding, modifier = Modifier.weight(1f).height(52.dp), shape = ShapeM) { Text("开始") }
                    OutlinedButton(onClick = onStopAdding, enabled = isAdding, modifier = Modifier.weight(1f).height(52.dp), shape = ShapeM) { Text("停止") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onRelease, modifier = Modifier.weight(1f).height(48.dp), shape = ShapeM) { Text("释放") }
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f).height(48.dp), shape = ShapeM) { Text("导出") }
                }
            }
        }
        if (showIpv4) item { SessionCard("IPv4 会话", ipv4Stats, maskPrivacy) }
        if (showIpv6) item { SessionCard("IPv6 会话", ipv6Stats, maskPrivacy) }
        item { FailureCard(listOf(ipv4Stats, ipv6Stats), onMoreLogs) }
        item { RecentLogCard(logs, maskPrivacy, onMoreLogs) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun LogsPage(
    modifier: Modifier,
    logs: List<LogLine>,
    history: List<SessionSummary>,
    maskPrivacy: Boolean,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("日志与历史", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = TextDark)
                OutlinedButton(onClick = onClear, shape = ShapeM) { Text("清理") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip("全部", BlueSoft, Blue)
                SmallChip("运行日志", Color.White, TextDark)
                SmallChip("检测历史", Color.White, TextDark)
            }
        }
        item {
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), shape = ShapeM) { Text("导出日志") }
                    OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f), shape = ShapeM) { Text("清理日志") }
                }
            }
        }
        item { RecentLogCard(logs, maskPrivacy, {}) }
        item { Text("检测历史", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextDark) }
        if (history.isEmpty()) {
            item { Panel { Text("暂无历史记录", color = TextDark) } }
        } else {
            items(history.take(20)) { HistoryItem(it, maskPrivacy) }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        Text(title, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
        subtitle?.let { Text(it, fontSize = 14.sp, color = Muted, maxLines = 1) }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeL,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextDark)
}

@Composable
private fun FieldLabel(label: String) {
    Text(label, fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Medium)
}

@Composable
private fun CleanTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = ShapeM,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CleanNumberField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = ShapeM,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun ParamField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        shape = ShapeM,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeSelector(mode: TestMode, onModeChange: (TestMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(TestMode.IPV4_ONLY, TestMode.IPV6_ONLY, TestMode.IPV4_THEN_IPV6).forEach { item ->
            FilterChip(
                selected = mode == item,
                onClick = { onModeChange(item) },
                label = { Text(if (item == TestMode.IPV4_THEN_IPV6) "分别测试" else item.label) }
            )
        }
    }
}

@Composable
private fun SessionCard(title: String, stats: ProtocolStats, maskPrivacy: Boolean) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title)
            Spacer(Modifier.weight(1f))
            Text(stats.phase, color = Blue, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("活动", stats.activeSessions.toString(), Blue, Modifier.weight(1f))
            Metric("失败", stats.totalFailure.toString(), ErrorRed, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("总计", stats.totalAttempts.toString(), Navy, Modifier.weight(1f))
            Metric("新增", stats.lastAdded.toString(), Blue, Modifier.weight(1f))
            Metric("CPS", "${stats.cps}/s", Blue, Modifier.weight(1f))
        }
        if (stats.resolvedAddresses.isNotEmpty()) {
            Text("地址：${displayIpList(stats.resolvedAddresses, maskPrivacy)}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = ShapeM,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Muted, fontSize = 12.sp, maxLines = 1)
            Text(value, color = color, fontSize = 23.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun FailureCard(stats: List<ProtocolStats>, onMore: () -> Unit) {
    val merged = linkedMapOf<String, Int>()
    stats.forEach { s ->
        s.errorSummary.forEach { (k, v) -> merged[k.shortReason()] = (merged[k.shortReason()] ?: 0) + v }
    }
    if (merged.isEmpty()) return
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("失败原因")
            Spacer(Modifier.weight(1f))
            Text("更多", color = Muted)
        }
        FlowReasons(merged)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowReasons(reasons: Map<String, Int>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reasons.entries.sortedByDescending { it.value }.take(6).forEach { (name, count) ->
            SmallChip("$name $count", RedSoft, ErrorRed)
        }
    }
}

@Composable
private fun RecentLogCard(logs: List<LogLine>, maskPrivacy: Boolean, onMore: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("最近日志")
            Spacer(Modifier.weight(1f))
            Text("更多", color = Muted)
        }
        if (logs.isEmpty()) {
            Text("暂无日志", color = Muted)
        } else {
            logs.takeLast(5).forEach { CompactLog(it, maskPrivacy) }
        }
    }
}

@Composable
private fun CompactLog(line: LogLine, maskPrivacy: Boolean) {
    val tag = when (line.level) {
        LogLevel.STAT -> "统计"
        LogLevel.SUCCESS -> "成功"
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(line.timeText.take(8), color = Muted, modifier = Modifier.width(70.dp), maxLines = 1)
        Text(tag, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp), maxLines = 1)
        Text(
            if (maskPrivacy) maskIpText(line.text) else line.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = TextDark,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun HistoryItem(item: SessionSummary, maskPrivacy: Boolean) {
    val mainStats = item.ipv4Stats ?: item.ipv6Stats
    val protocol = when {
        item.ipv4Stats != null && item.ipv6Stats != null -> "分别测试"
        item.ipv4Stats != null -> "IPv4 完成"
        item.ipv6Stats != null -> "IPv6 完成"
        else -> "完成"
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.startedAtText.drop(5), color = Muted, modifier = Modifier.weight(1f), maxLines = 1)
            SmallChip(protocol, GreenSoft, Green)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniInfo("目标", if (maskPrivacy) maskIpText(item.host) else item.host, Modifier.weight(1f))
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
        val reasons = mainStats?.errorSummary.orEmpty().mapKeys { it.key.shortReason() }
        if (reasons.isNotEmpty()) FlowReasons(reasons)
    }
}

@Composable
private fun MiniInfo(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = ShapeS,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Muted, modifier = Modifier.width(42.dp), fontSize = 13.sp, maxLines = 1)
            Text(value, color = TextDark, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SmallChip(text: String, bg: Color, fg: Color) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = bg), elevation = CardDefaults.cardElevation(0.dp)) {
        Text(text, color = fg, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), maxLines = 1)
    }
}

@Composable
private fun InfoLine(label: String, value: String, color: Color = TextDark) {
    Row {
        Text("$label：", color = Muted, fontSize = 13.sp, modifier = Modifier.width(54.dp))
        Text(value, color = color, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BottomNav(selectedTab: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 4.dp, modifier = Modifier.navigationBarsPadding()) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                icon = { Text(tab.mark, fontWeight = FontWeight.Bold) },
                label = { Text(tab.label) }
            )
        }
    }
}

private fun shortMode(mode: TestMode): String = when (mode) {
    TestMode.IPV4_ONLY -> "IPv4"
    TestMode.IPV6_ONLY -> "IPv6"
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
private val Navy = Color(0xFF0F2F6E)
private val ShapeL = RoundedCornerShape(22.dp)
private val ShapeM = RoundedCornerShape(16.dp)
private val ShapeS = RoundedCornerShape(12.dp)

private val IPV4_REGEX = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
private val IPV6_REGEX = Regex("""(?i)(?<![\w.])(?:[0-9a-f]{1,4}:){2,}[0-9a-f]{0,4}(?:%[\w.]+)?(?![\w.])""")
