@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.demonv.netsessiontester.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import com.demonv.netsessiontester.ios.tools.*
import kotlinx.coroutines.*
import platform.UIKit.UIViewController
import platform.Foundation.*

fun createToolsViewController(): UIViewController = ComposeUIViewController { IosExtraTheme { IosToolsScreen() } }

fun createHistoryViewController(): UIViewController = ComposeUIViewController { IosExtraTheme { IosHistoryScreen() } }

fun createSettingsViewController(): UIViewController = ComposeUIViewController { IosExtraTheme { IosSettingsScreen() } }

/** Typed Objective-C API. The launcher imports the generated header rather than guessing symbols. */
@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@kotlin.native.ObjCName(name = "NSTAppFactory", exact = true)
object IosAppFactory {
    fun mainController(): UIViewController = createMainViewController()
    fun toolsController(): UIViewController = createToolsViewController()
    fun historyController(): UIViewController = createHistoryViewController()
    fun settingsController(): UIViewController = createSettingsViewController()
    fun stopActiveTests() = stopIosActiveTasks()
}

@Composable
private fun IosExtraTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF0A84FF))
        else lightColorScheme(primary = Color(0xFF007AFF))
    MaterialTheme(colorScheme = colors, content = content)
}

private enum class IosTool(val title: String, val description: String) {
    DNS("NSLookup", "系统 DNS 或指定服务器查询 A / AAAA 记录"),
    NAT("NAT 诊断", "STUN 映射探测；按服务器实际能力分析 NAT"),
    IPV6("IPv6 专项诊断", "接口地址、解析和 TCP IPv6 可达性"),
    TRACEROUTE("Traceroute", "逐跳 ICMP 探测，超时与权限受限明确标记"),
    MTU("MTU / PMTU", "非分片探测与路径 MTU 分析，未确认不报确定值"),
    IPERF("iPerf3 TCP 单流", "连接你指定的 iPerf3 服务器，测量上行或下行吞吐"),
    LOADED("负载延迟对比", "比较空载与 TCP 握手压力下的延迟变化")
}

@Composable
private fun IosToolsScreen() {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<IosTool?>(null) }
    var host by remember { mutableStateOf(IosPreferences.loadConfig().host) }
    var port by remember { mutableStateOf("80") }
    var server by remember { mutableStateOf("223.5.5.5") }
    var ipv6 by remember { mutableStateOf(false) }
    var reverse by remember { mutableStateOf(true) }
    var duration by remember { mutableStateOf("10") }
    var running by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var lines by remember { mutableStateOf(emptyList<String>()) }
    var summary by remember { mutableStateOf("") }
    var generation by remember { mutableStateOf(0L) }

    fun stop() {
        if (running) {
            stopping = true
            job?.cancel()
        }
    }
    DisposableEffect(Unit) {
        IosTaskRegistry.register("tools") { val previous = job; stop(); previous }
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            platform.UIKit.UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue
        ) { stop() }
        onDispose {
            job?.cancel()
            IosTaskRegistry.unregister("tools")
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }

    fun start() {
        val tool = selected ?: return
        if (running) return
        val targetPort = port.toIntOrNull()
        if (targetPort == null || targetPort !in 1..65535 || host.isBlank()) {
            summary = "请输入有效目标和端口（1–65535）"
            return
        }
        val seconds = duration.toIntOrNull()
        if (tool == IosTool.IPERF && (seconds == null || seconds !in 1..60)) {
            summary = "测速时长须为 1–60 秒"
            return
        }
        val target = host.trim()
        val resolver = server.trim()
        val useIpv6 = ipv6
        val download = reverse
        val id = ++generation
        lines = emptyList()
        summary = "正在测试…"
        stopping = false
        running = true
        job = scope.launch {
            val onLine: suspend (String) -> Unit = { line ->
                withContext(Dispatchers.Main) { if (id == generation) lines = (lines + line).takeLast(400) }
            }
            try {
                IosTaskRegistry.awaitIdle()
                val report = when (tool) {
                    IosTool.DNS -> IosNetworkTools.run(IosDiagnosticKind.DNS, target, targetPort, resolver, useIpv6, onLine)
                    IosTool.NAT -> IosNetworkTools.run(IosDiagnosticKind.NAT, target, targetPort, resolver, useIpv6, onLine)
                    IosTool.IPV6 -> IosNetworkTools.run(IosDiagnosticKind.IPV6, target, targetPort, resolver, true, onLine)
                    IosTool.TRACEROUTE -> IosNetworkTools.run(IosDiagnosticKind.TRACEROUTE, target, targetPort, resolver, useIpv6, onLine)
                    IosTool.MTU -> IosNetworkTools.run(IosDiagnosticKind.MTU, target, targetPort, resolver, useIpv6, onLine)
                    IosTool.IPERF -> IosPerformanceTools.iperf(target, targetPort, download, seconds ?: 10, onLine)
                    IosTool.LOADED -> IosPerformanceTools.loadedLatency(target, targetPort, useIpv6, onLine)
                }
                summary = report.summary
                lines = report.lines.takeLast(400)
            } catch (cancelled: CancellationException) {
                summary = "已停止；保留已完成的探测记录"
                throw cancelled
            } catch (error: Exception) {
                summary = "测试失败：${error.message ?: "未知错误"}"
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (id == generation) {
                        IosHistoryStore.append(tool.title, target, summary, lines.joinToString("\n"))
                        running = false
                        stopping = false
                        job = null
                    }
                }
            }
        }
    }

    ExtraPage("网络工具") {
        if (selected == null) {
            IosTool.entries.forEach { tool ->
                OutlinedCard(onClick = {
                    selected = tool
                    summary = ""
                    lines = emptyList()
                    port = if (tool == IosTool.IPERF) "5201" else if (tool == IosTool.NAT) "3478" else "80"
                    if (tool == IosTool.NAT) host = "stun.cloudflare.com"
                    else host = IosPreferences.loadConfig().host
                }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tool.title, style = MaterialTheme.typography.titleMedium)
                        Text(tool.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            val tool = selected!!
            TextButton(enabled = !running, onClick = { selected = null }) { Text("返回工具列表") }
            Text(tool.title, style = MaterialTheme.typography.titleLarge)
            Text(tool.description, style = MaterialTheme.typography.bodyMedium)
            run {
                OutlinedTextField(host, { host = it }, label = { Text(if (tool == IosTool.NAT) "STUN 服务器" else "目标 IP 或域名") }, enabled = !running, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (tool !in listOf(IosTool.DNS, IosTool.TRACEROUTE, IosTool.MTU)) {
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("端口") }, enabled = !running, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (tool == IosTool.DNS) {
                    OutlinedTextField(server, { server = it }, label = { Text("DNS 服务器（留空使用系统解析）") }, enabled = !running, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (tool in listOf(IosTool.DNS, IosTool.TRACEROUTE, IosTool.MTU, IosTool.LOADED, IosTool.NAT)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("使用 IPv6")
                        Switch(ipv6, { ipv6 = it }, enabled = !running)
                    }
                }
                if (tool == IosTool.IPERF) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (reverse) "下行测速" else "上行测速")
                        Switch(reverse, { reverse = it }, enabled = !running)
                    }
                    OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("时长（1–60 秒）") }, enabled = !running, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            Button(onClick = { if (running) stop() else start() }, enabled = !stopping, modifier = Modifier.fillMaxWidth()) {
                Text(if (stopping) "正在停止…" else if (running) "停止测试" else "开始测试")
            }
            if (summary.isNotBlank()) Text(summary, color = MaterialTheme.colorScheme.primary)
            SelectionContainer { Text(lines.joinToString("\n"), fontSize = 13.sp) }
        }
    }
}

@Composable
private fun IosHistoryScreen() {
    val records by IosHistoryStore.records.collectAsState()
    var selected by remember { mutableStateOf<IosHistoryRecord?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    ExtraPage("测试历史") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = records.isNotEmpty(), onClick = {
                runCatching { shareIosCsv(IosHistoryStore.csv()) }.onFailure { error = it.message.orEmpty() }
            }) { Text("导出 CSV") }
            TextButton(enabled = records.isNotEmpty(), onClick = { confirmClear = true }) { Text("清空历史") }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Text("最多保存最近 100 次测试，包含停止与失败记录。", style = MaterialTheme.typography.bodySmall)
        if (records.isEmpty()) Text("暂无测试记录")
        records.forEach { record ->
            OutlinedCard(onClick = { selected = record }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${record.kind} · ${record.target}", style = MaterialTheme.typography.titleSmall)
                    Text(record.summary, style = MaterialTheme.typography.bodySmall)
                    val formatter = remember { NSDateFormatter().apply { dateFormat = "yyyy-MM-dd HH:mm:ss" } }
                    Text(formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(record.id / 1000.0)), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, title = { Text("清空全部历史？") },
        text = { Text("此操作会删除本机保存的测试记录。") },
        confirmButton = { TextButton(onClick = { IosHistoryStore.clear(); confirmClear = false }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
    )
    selected?.let { record ->
        AlertDialog(
            onDismissRequest = { selected = null }, title = { Text(record.kind) },
            text = { SelectionContainer { Text("${record.target}\n${record.summary}\n\n${record.details}", modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = {
                runCatching { shareIosCsv(IosHistoryStore.csv(listOf(record))) }.onFailure { error = it.message.orEmpty() }
                selected = null
            }) { Text("导出") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("关闭") } }
        )
    }
}

@Composable
private fun IosSettingsScreen() {
    val saved = remember { IosPreferences.loadConfig() }
    var host by remember { mutableStateOf(saved.host) }
    var port by remember { mutableStateOf(saved.port.toString()) }
    var cps by remember { mutableStateOf(saved.batchSize.toString()) }
    var success by remember { mutableStateOf(saved.successLimit.toString()) }
    var failure by remember { mutableStateOf(saved.failureLimit.toString()) }
    var timeout by remember { mutableStateOf(saved.timeoutMs.toString()) }
    var pingInterval by remember { mutableStateOf(saved.pingIntervalMs.toString()) }
    var keep by remember { mutableStateOf(saved.keepConnectionsAfterStop) }
    var mode by remember { mutableStateOf(saved.mode) }
    var message by remember { mutableStateOf("") }
    ExtraPage("设置") {
        Text("默认测试参数", style = MaterialTheme.typography.titleMedium)
        SettingsField("默认目标", host, false) { host = it }
        SettingsField("默认端口（1–65535）", port) { port = it }
        SettingsField("目标 CPS（1–5000）", cps) { cps = it }
        SettingsField("累计成功目标（10–5000）", success) { success = it }
        SettingsField("失败上限（10–5000）", failure) { failure = it }
        SettingsField("建连超时（200–10000 ms）", timeout) { timeout = it }
        SettingsField("Ping 周期（100–5000 ms）", pingInterval) { pingInterval = it }
        IosTestMode.entries.forEach { option ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(mode == option, onClick = { mode = option })
                Text(option.label, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("停止后保留测试连接")
            Switch(keep, { keep = it })
        }
        Button(onClick = {
            val values = listOf(port, cps, success, failure, timeout, pingInterval).map { it.toIntOrNull() }
            val ranges = listOf(1..65535, 1..5000, 10..5000, 10..5000, 200..10000, 100..5000)
            if (host.isBlank() || values.zip(ranges).any { (value, range) -> value == null || value !in range }) {
                message = "请填写有效目标，并按字段标注范围输入数值"
            } else {
                IosPreferences.saveConfig(saved.copy(
                    host = host.trim(), port = values[0]!!, batchSize = values[1]!!,
                    successLimit = values[2]!!, failureLimit = values[3]!!,
                    timeoutMs = values[4]!!, pingIntervalMs = values[5]!!.toLong(),
                    keepConnectionsAfterStop = keep, mode = mode
                ))
                message = "已保存，测试页默认参数已更新"
            }
        }) { Text("保存默认参数") }
        if (message.isNotBlank()) Text(message)
        HorizontalDivider()
        Text("测量与平台能力", style = MaterialTheme.typography.titleMedium)
        Text("主测试 Ping 测量 TCP 建连耗时；失败率包括超时、拒绝及路由错误。工具页会明确实际采用的探测方法。")
        Text("本版本专注主动网络诊断，不提供蜂窝工参、WiFi 扫描、漫游监测或双网对测。")
        Text("切换页面或进入后台会停止进行中的测试；已完成记录保存在本机。")
        Text("iOS 26 的原生标签栏使用系统 Liquid Glass，旧系统采用标准外观，并遵循系统辅助功能设置。")
    }
}

@Composable
private fun SettingsField(label: String, value: String, numeric: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(if (numeric) it.filter(Char::isDigit) else it) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number
                else androidx.compose.ui.text.input.KeyboardType.Text
        )
    )
}

@Composable
private fun ExtraPage(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        content()
        Spacer(Modifier.height(20.dp))
    }
}
