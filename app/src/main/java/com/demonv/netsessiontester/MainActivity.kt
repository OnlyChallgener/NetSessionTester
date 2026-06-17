package com.demonv.netsessiontester

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.demonv.netsessiontester.data.HistoryStore
import com.demonv.netsessiontester.model.AppUiState
import com.demonv.netsessiontester.model.BatchResult
import com.demonv.netsessiontester.model.IpMode
import com.demonv.netsessiontester.model.TestConfig
import com.demonv.netsessiontester.model.TestSummary
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
    var port by remember { mutableStateOf("443") }
    var ipMode by remember { mutableStateOf(IpMode.AUTO) }
    var startConcurrency by remember { mutableStateOf("10") }
    var maxConcurrency by remember { mutableStateOf("200") }
    var step by remember { mutableStateOf("10") }
    var timeoutMs by remember { mutableStateOf("3000") }
    var holdMs by remember { mutableStateOf("1000") }
    var failureStopRate by remember { mutableStateOf("0.25") }

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

    fun buildConfig(): TestConfig? {
        val config = runCatching {
            TestConfig(
                host = host,
                port = port.toInt(),
                ipMode = ipMode,
                startConcurrency = startConcurrency.toInt(),
                maxConcurrency = maxConcurrency.toInt(),
                step = step.toInt(),
                timeoutMs = timeoutMs.toInt(),
                holdMs = holdMs.toLong(),
                failureStopRate = failureStopRate.toDouble()
            ).normalized()
        }.getOrElse { error ->
            scope.launch { snackbarHostState.showSnackbar("参数错误：${error.message}") }
            return null
        }
        if (config.host.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("请填写你自己的 VPS、路由器、内网设备或已授权服务器地址") }
            return null
        }
        return config
    }

    fun startTest() {
        val config = buildConfig() ?: return
        runningJob?.cancel()
        state = AppUiState(isRunning = true, status = "准备开始", history = state.history)
        runningJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            runCatching {
                tester.runIncrementalTest(
                    rawConfig = config,
                    onStatus = { message -> state = state.copy(status = message) },
                    onBatch = { batch -> state = state.copy(batches = state.batches + batch) }
                )
            }.onSuccess { batches ->
                val stable = batches.lastOrNull { it.failureRate < config.failureStopRate }?.concurrency ?: 0
                val summary = TestSummary(
                    startedAtEpochMs = startedAt,
                    host = config.host,
                    port = config.port,
                    ipMode = config.ipMode,
                    stableConcurrency = stable,
                    peakConcurrency = batches.maxOfOrNull { it.concurrency } ?: 0,
                    finalSuccessRate = batches.lastOrNull()?.successRate ?: 0.0,
                    batches = batches
                )
                historyStore.append(summary)
                val history = historyStore.load()
                state = state.copy(
                    isRunning = false,
                    status = "测试完成",
                    summary = summary,
                    history = history
                )
            }.onFailure { error ->
                state = state.copy(
                    isRunning = false,
                    status = "测试停止",
                    error = error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    fun stopTest() {
        runningJob?.cancel()
        runningJob = null
        state = state.copy(isRunning = false, status = "已手动停止")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("宽带会话测试器", fontWeight = FontWeight.Bold)
                        Text("IPv4 / IPv6 TCP Connect", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .safeDrawingPadding()
        ) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            SafetyCard()
                        }
                        item {
                            ConfigCard(
                                host = host,
                                onHostChange = { host = it },
                                port = port,
                                onPortChange = { port = it },
                                ipMode = ipMode,
                                onIpModeChange = { ipMode = it },
                                startConcurrency = startConcurrency,
                                onStartConcurrencyChange = { startConcurrency = it },
                                maxConcurrency = maxConcurrency,
                                onMaxConcurrencyChange = { maxConcurrency = it },
                                step = step,
                                onStepChange = { step = it },
                                timeoutMs = timeoutMs,
                                onTimeoutMsChange = { timeoutMs = it },
                                holdMs = holdMs,
                                onHoldMsChange = { holdMs = it },
                                failureStopRate = failureStopRate,
                                onFailureStopRateChange = { failureStopRate = it },
                                isRunning = state.isRunning,
                                onStart = ::startTest,
                                onStop = ::stopTest,
                                onExport = {
                                    val summary = state.summary
                                    if (summary == null) {
                                        scope.launch { snackbarHostState.showSnackbar("还没有可导出的测试结果") }
                                    } else {
                                        pendingCsv = CsvExporter.currentSessionCsv(summary)
                                        exportLauncher.launch("net-session-${summary.startedAtEpochMs}.csv")
                                    }
                                }
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item { StatusCard(state) }
                        item { ResultCard(state.batches, state.summary) }
                        item { HistoryCard(state.history) }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    item { SafetyCard() }
                    item {
                        ConfigCard(
                            host = host,
                            onHostChange = { host = it },
                            port = port,
                            onPortChange = { port = it },
                            ipMode = ipMode,
                            onIpModeChange = { ipMode = it },
                            startConcurrency = startConcurrency,
                            onStartConcurrencyChange = { startConcurrency = it },
                            maxConcurrency = maxConcurrency,
                            onMaxConcurrencyChange = { maxConcurrency = it },
                            step = step,
                            onStepChange = { step = it },
                            timeoutMs = timeoutMs,
                            onTimeoutMsChange = { timeoutMs = it },
                            holdMs = holdMs,
                            onHoldMsChange = { holdMs = it },
                            failureStopRate = failureStopRate,
                            onFailureStopRateChange = { failureStopRate = it },
                            isRunning = state.isRunning,
                            onStart = ::startTest,
                            onStop = ::stopTest,
                            onExport = {
                                val summary = state.summary
                                if (summary == null) {
                                    scope.launch { snackbarHostState.showSnackbar("还没有可导出的测试结果") }
                                } else {
                                    pendingCsv = CsvExporter.currentSessionCsv(summary)
                                    exportLauncher.launch("net-session-${summary.startedAtEpochMs}.csv")
                                }
                            }
                        )
                    }
                    item { StatusCard(state) }
                    item { ResultCard(state.batches, state.summary) }
                    item { HistoryCard(state.history) }
                }
            }
        }
    }
}

@Composable
private fun SafetyCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("使用提醒", fontWeight = FontWeight.Bold)
            Text(
                "请只测试自己的 VPS、路由器、内网服务器或已获得授权的目标。APP 默认不内置公共网站目标，并限制最大并发 ${TestConfig.SAFE_MAX_CONCURRENCY}。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigCard(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    ipMode: IpMode,
    onIpModeChange: (IpMode) -> Unit,
    startConcurrency: String,
    onStartConcurrencyChange: (String) -> Unit,
    maxConcurrency: String,
    onMaxConcurrencyChange: (String) -> Unit,
    step: String,
    onStepChange: (String) -> Unit,
    timeoutMs: String,
    onTimeoutMsChange: (String) -> Unit,
    holdMs: String,
    onHoldMsChange: (String) -> Unit,
    failureStopRate: String,
    onFailureStopRateChange: (String) -> Unit,
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onExport: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("测试参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("目标地址 / 域名") },
                placeholder = { Text("例如 192.168.1.1、你的 VPS 域名、IPv6 地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isRunning
            )
            OutlinedTextField(
                value = port,
                onValueChange = { onPortChange(it.filter(Char::isDigit).take(5)) },
                label = { Text("端口") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isRunning
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IpMode.entries.forEach { mode ->
                    FilterChip(
                        selected = ipMode == mode,
                        enabled = !isRunning,
                        onClick = { onIpModeChange(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallNumberField("起始并发", startConcurrency, onStartConcurrencyChange, !isRunning)
                SmallNumberField("最大并发", maxConcurrency, onMaxConcurrencyChange, !isRunning)
                SmallNumberField("步长", step, onStepChange, !isRunning)
                SmallNumberField("超时 ms", timeoutMs, onTimeoutMsChange, !isRunning)
                SmallNumberField("保持 ms", holdMs, onHoldMsChange, !isRunning)
                SmallNumberField("失败阈值", failureStopRate, onFailureStopRateChange, !isRunning, decimal = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onStart, enabled = !isRunning) { Text("开始测试") }
                Button(onClick = onStop, enabled = isRunning) { Text("停止") }
                Button(onClick = onExport, enabled = !isRunning) { Text("导出 CSV") }
            }
        }
    }
}

@Composable
private fun SmallNumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    decimal: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = if (decimal) raw.filter { it.isDigit() || it == '.' } else raw.filter(Char::isDigit)
            onChange(filtered.take(8))
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.width(150.dp),
        singleLine = true,
        enabled = enabled
    )
}

@Composable
private fun StatusCard(state: AppUiState) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                if (state.isRunning) CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
            }
            Text(state.status)
            if (state.isRunning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            state.error?.let {
                Text("错误：$it", color = MaterialTheme.colorScheme.error)
            }
            val latest = state.batches.lastOrNull()
            if (latest != null) {
                MetricGrid(
                    items = listOf(
                        "当前并发" to latest.concurrency.toString(),
                        "成功率" to latest.successRateText(),
                        "失败率" to latest.failureRateText(),
                        "P95" to (latest.p95LatencyMs?.let { "$it ms" } ?: "-"),
                        "平均" to (latest.avgLatencyMs?.let { "$it ms" } ?: "-"),
                        "耗时" to "${latest.elapsedMs} ms"
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricGrid(items: List<Pair<String, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.width(120.dp).padding(12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(batches: List<BatchResult>, summary: TestSummary?) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("测试结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (summary != null) {
                MetricGrid(
                    items = listOf(
                        "稳定会话" to summary.stableConcurrency.toString(),
                        "最高尝试" to summary.peakConcurrency.toString(),
                        "最终成功率" to "${(summary.finalSuccessRate * 100).toInt()}%"
                    )
                )
            }
            if (batches.isEmpty()) {
                Text("还没有测试数据。")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    batches.takeLast(8).reversed().forEach { batch ->
                        BatchRow(batch)
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchRow(batch: BatchResult) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${batch.concurrency} 并发", fontWeight = FontWeight.Bold)
            Text("成功 ${batch.successCount} / 失败 ${batch.failureCount}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("成功率 ${batch.successRateText()}") })
            AssistChip(onClick = {}, label = { Text("P95 ${batch.p95LatencyMs?.toString() ?: "-"} ms") })
        }
        if (batch.errorSummary.isNotEmpty()) {
            Text(
                batch.errorSummary.entries.joinToString("，") { "${it.key}:${it.value}" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), DividerDefaults.Thickness, DividerDefaults.color)
    }
}

@Composable
private fun HistoryCard(history: List<TestSummary>) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("历史记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (history.isEmpty()) {
                Text("暂无历史记录。")
            } else {
                history.take(10).forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.startedAtText, fontWeight = FontWeight.Bold)
                        Text("${item.host}:${item.port} · ${item.ipMode.label}")
                        Text("稳定 ${item.stableConcurrency}，最高 ${item.peakConcurrency}，最终成功率 ${(item.finalSuccessRate * 100).toInt()}%")
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
