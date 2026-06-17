package com.demonv.netsessiontester

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.demonv.netsessiontester.data.HistoryStore
import com.demonv.netsessiontester.model.AppUiState
import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.SessionConfig
import com.demonv.netsessiontester.model.SessionSummary
import com.demonv.netsessiontester.model.TestMode
import com.demonv.netsessiontester.network.TcpTester
import com.demonv.netsessiontester.ui.theme.NetSessionTesterTheme
import com.demonv.netsessiontester.util.CsvExporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var runningJob by remember { mutableStateOf<Job?>(null) }
    var state by remember { mutableStateOf(AppUiState()) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var mode by remember { mutableStateOf(TestMode.IPV4_THEN_IPV6) }
    var batchSize by remember { mutableStateOf("16") }
    var intervalMs by remember { mutableStateOf("1000") }
    var timeoutMs by remember { mutableStateOf("3000") }
    var successLimit by remember { mutableStateOf("65535") }
    var failureLimit by remember { mutableStateOf("200") }
    var keepConnections by remember { mutableStateOf(true) }
    var authorizedTarget by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        state = state.copy(history = historyStore.load())
    }

    fun appendLog(line: LogLine) {
        state = state.copy(logs = (state.logs + line).takeLast(500))
    }

    fun buildConfig(): SessionConfig? {
        if (!authorizedTarget) {
            scope.launch { snackbarHostState.showSnackbar("请先勾选：确认目标是自有或已授权服务器") }
            return null
        }
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
        runningJob?.cancel()
        scope.launch { tester.release() }
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
        appendLog(LogLine(level = LogLevel.WARN, text = "本工具用于自有/已授权目标的宽带会话保持测试，不用于公共网站压测。"))
        appendLog(LogLine(level = LogLevel.INFO, text = "目标：${config.host}:${config.port} | 模式：${config.mode.label} | 每批：${config.batchSize} | 间隔：${config.intervalMs}ms | 成功上限：${config.successLimit} | 失败停止：${config.failureLimit}"))

        runningJob = scope.launch {
            runCatching {
                tester.runSessionHoldTest(
                    rawConfig = config,
                    onStats = { stats ->
                        state = when (stats.protocol) {
                            IpProtocol.IPV4 -> state.copy(ipv4Stats = stats, status = "${stats.protocol.label} ${stats.phase}")
                            IpProtocol.IPV6 -> state.copy(ipv6Stats = stats, status = "${stats.protocol.label} ${stats.phase}")
                        }
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
            }.onFailure { error ->
                val msg = error.message ?: error.javaClass.simpleName
                appendLog(LogLine(level = LogLevel.ERROR, text = "测试中断：$msg"))
                state = state.copy(isAdding = false, status = "已停止新增", error = msg)
            }
        }
    }

    fun stopAdding() {
        runningJob?.cancel()
        runningJob = null
        appendLog(LogLine(level = LogLevel.WARN, text = "已停止新增连接；已建立连接会继续保持，直到点击释放连接。"))
        state = state.copy(isAdding = false, status = "已停止新增，连接保持中")
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
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("宽带会话测试器", fontWeight = FontWeight.Bold)
                        Text("TCP 会话保持 · IPv4 / IPv6 分别测试", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WarningCard(authorizedTarget, onAuthorizedChange = { authorizedTarget = it })
            TargetCard(
                host = host,
                onHostChange = { host = it },
                port = port,
                onPortChange = { port = it },
                result = state.resolveResult,
                onResolve = { resolve() }
            )
            ModeCard(mode = mode, onModeChange = { mode = it })
            ParamsCard(
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
                onKeepConnectionsChange = { keepConnections = it }
            )
            ControlCard(
                status = state.status,
                isAdding = state.isAdding,
                onStart = { startTest() },
                onStopAdding = { stopAdding() },
                onRelease = { releaseAll() },
                onExport = {
                    val csv = state.summary?.let { CsvExporter.summaryCsv(it, state.logs) } ?: CsvExporter.logsCsv(state.logs)
                    pendingCsv = csv
                    exportLauncher.launch("net-session-test.csv")
                }
            )
            StatsCard(title = "IPv4 会话", stats = state.ipv4Stats)
            StatsCard(title = "IPv6 会话", stats = state.ipv6Stats)
            LogCard(logs = state.logs)
            HistoryCard(history = state.history)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WarningCard(authorized: Boolean, onAuthorizedChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("使用提醒", fontWeight = FontWeight.Bold)
            Text("这是宽带 TCP 会话保持测试，不是测速工具。请只测试自有 VPS、内网设备、路由器或已授权服务器；不要对公共网站做高会话测试。")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = authorized, onCheckedChange = onAuthorizedChange)
                Text("我确认目标服务器是自有或已授权测试目标")
            }
        }
    }
}

@Composable
private fun TargetCard(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    result: com.demonv.netsessiontester.model.ResolveResult,
    onResolve: () -> Unit
) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("目标设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("域名 / IPv4 / IPv6") },
                placeholder = { Text("例如你的 VPS 域名或 192.168.1.1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("端口") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onResolve, modifier = Modifier.weight(1f)) { Text("解析 DNS") }
            }
            HorizontalDivider()
            Text("IPv4：${result.ipv4.joinToString(" / ").ifBlank { "未解析 / 无" }}", maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("IPv6：${result.ipv6.joinToString(" / ").ifBlank { "未解析 / 无" }}", maxLines = 2, overflow = TextOverflow.Ellipsis)
            result.error?.let { Text("解析错误：$it", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeCard(mode: TestMode, onModeChange: (TestMode) -> Unit) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("测试模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TestMode.entries.forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = { onModeChange(item) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParamsCard(
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
    onKeepConnectionsChange: (Boolean) -> Unit
) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("会话参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("每批新增", batchSize, onBatchSizeChange, Modifier.weight(1f))
                NumberField("间隔 ms", intervalMs, onIntervalMsChange, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("超时 ms", timeoutMs, onTimeoutMsChange, Modifier.weight(1f))
                NumberField("失败停止", failureLimit, onFailureLimitChange, Modifier.weight(1f))
            }
            NumberField("目标成功会话数", successLimit, onSuccessLimitChange, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = keepConnections, onCheckedChange = onKeepConnectionsChange)
                Text("测试完成后保持连接，手动点击“释放连接”再关闭")
            }
            Text("建议：每批 16-100，间隔 1000ms。测 65535 时不要一口气建立，慢慢累加更接近宽带会话数。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { ch -> ch.isDigit() }) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun ControlCard(
    status: String,
    isAdding: Boolean,
    onStart: () -> Unit,
    onStopAdding: () -> Unit,
    onRelease: () -> Unit,
    onExport: () -> Unit
) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("操作控制", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("状态：$status")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = !isAdding, modifier = Modifier.weight(1f)) { Text("开始") }
                OutlinedButton(onClick = onStopAdding, enabled = isAdding, modifier = Modifier.weight(1f)) { Text("停止新增") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRelease, modifier = Modifier.weight(1f)) { Text("释放连接") }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("导出 CSV") }
            }
        }
    }
}

@Composable
private fun StatsCard(title: String, stats: ProtocolStats) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(stats.phase, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatItem("当前活动", stats.activeSessions.toString(), Modifier.weight(1f))
                StatItem("最大稳定", stats.maxStableSessions.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatItem("成功", stats.totalSuccess.toString(), Modifier.weight(1f))
                StatItem("失败", stats.totalFailure.toString(), Modifier.weight(1f))
                StatItem("总计", stats.totalAttempts.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatItem("新增", stats.lastAdded.toString(), Modifier.weight(1f))
                StatItem("CPS", "${stats.cps}/秒", Modifier.weight(1f))
            }
            if (stats.resolvedAddresses.isNotEmpty()) {
                Text("地址：${stats.resolvedAddresses.joinToString(" / ")}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            if (stats.errorSummary.isNotEmpty()) {
                Text("错误：${stats.errorSummary.entries.joinToString(" | ") { "${it.key}:${it.value}" }}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LogCard(logs: List<LogLine>) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("日志输出", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF050505))) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(10.dp)
                ) {
                    items(logs) { line ->
                        Text(
                            text = "[${line.timeText}] ${line.text}",
                            color = when (line.level) {
                                LogLevel.SUCCESS -> Color(0xFF00E676)
                                LogLevel.ERROR -> Color(0xFFFF5252)
                                LogLevel.WARN -> Color(0xFFFFD54F)
                                LogLevel.STAT -> Color(0xFF64B5F6)
                                LogLevel.INFO -> Color(0xFFE0E0E0)
                            },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(history: List<SessionSummary>) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("历史记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (history.isEmpty()) {
                Text("暂无历史记录")
            } else {
                history.take(5).forEach { item ->
                    Text(
                        "${item.startedAtText}  ${item.host}:${item.port}  IPv4最大:${item.ipv4Stats?.maxStableSessions ?: 0}  IPv6最大:${item.ipv6Stats?.maxStableSessions ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
