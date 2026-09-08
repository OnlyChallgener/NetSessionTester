package com.demonv.netsessiontester.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demonv.netsessiontester.engine.DesktopTcpTester
import com.demonv.netsessiontester.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AppleBlue = Color(0xFF007AFF)
private val AppleGreen = Color(0xFF34C759)
private val AppleOrange = Color(0xFFFF9500)
private val AppleRed = Color(0xFFFF3B30)
private val ApplePurple = Color(0xFF5856D6)
private val AppleGrayBg = Color(0xFFF2F2F7)
private val AppleCardBg = Color(0xFFFFFFFF)
private val AppleTextPrimary = Color(0xFF1C1C1E)
private val AppleTextSecondary = Color(0xFF8E8E93)
private val AppleBorderHighlight = Color(0x18000000)

@Composable
fun DesktopApp() {
    val tester = remember { DesktopTcpTester() }
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("www.baidu.com") }
    var port by remember { mutableStateOf("80") }
    var mode by remember { mutableStateOf(TestMode.IPV4_ONLY) }
    var targetCps by remember { mutableStateOf(500) }
    var successLimit by remember { mutableStateOf(10000) }
    var failureLimit by remember { mutableStateOf(2000) }
    var keepConnections by remember { mutableStateOf(true) }

    var isRunning by remember { mutableStateOf(false) }
    var currentStats by remember { mutableStateOf(ProtocolStats(IpProtocol.IPV4)) }
    var chartPoints by remember { mutableStateOf(listOf<ChartPoint>()) }
    var logs by remember { mutableStateOf(listOf(LogLine(text = "NetSessionTester 桌面版就绪 (Windows / macOS)"))) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    val logListState = rememberLazyListState()

    fun log(text: String, level: LogLevel = LogLevel.INFO) {
        logs = logs + LogLine(text = text, level = level)
    }

    // 定时采样折线图数据
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val startMs = System.currentTimeMillis()
            while (isRunning) {
                val sec = ((System.currentTimeMillis() - startMs) / 1000L).toInt()
                chartPoints = chartPoints + ChartPoint(
                    elapsedSec = sec,
                    active = currentStats.activeSessions,
                    failure = currentStats.totalFailure,
                    protocol = currentStats.protocol
                )
                delay(300L)
            }
        }
    }

    // 日志自动滚动到底部
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleGrayBg)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // 顶部导航栏
            DesktopHeader(host = host, isRunning = isRunning, currentStats = currentStats)

            Spacer(Modifier.height(12.dp))

            // 主双栏内容区
            Row(modifier = Modifier.fillMaxSize().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // 左侧控制面板 (360dp)
                DesktopControlPanel(
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                    host = host,
                    port = port,
                    mode = mode,
                    targetCps = targetCps,
                    successLimit = successLimit,
                    failureLimit = failureLimit,
                    keepConnections = keepConnections,
                    isRunning = isRunning,
                    currentStats = currentStats,
                    onHostChange = { host = it },
                    onPortChange = { port = it },
                    onModeChange = { mode = it },
                    onCpsChange = { targetCps = it },
                    onSuccessLimitChange = { successLimit = it },
                    onFailureLimitChange = { failureLimit = it },
                    onKeepChange = { keepConnections = it },
                    onStartStop = {
                        if (isRunning) {
                            testJob?.cancel()
                            isRunning = false
                            log("用户主动停止测试", LogLevel.WARN)
                        } else {
                            isRunning = true
                            chartPoints = emptyList()
                            val config = SessionConfig(
                                host = host,
                                port = port.toIntOrNull() ?: 80,
                                mode = mode,
                                batchSize = targetCps,
                                successLimit = successLimit,
                                failureLimit = failureLimit,
                                keepConnectionsAfterStop = keepConnections
                            )
                            testJob = scope.launch {
                                tester.runSessionHoldTest(
                                    rawConfig = config,
                                    onStats = { currentStats = it },
                                    onLog = { log(it.text, it.level) }
                                )
                                isRunning = false
                            }
                        }
                    },
                    onRelease = {
                        scope.launch {
                            val released = tester.release()
                            currentStats = currentStats.copy(activeSessions = 0, phase = "已释放")
                            log("已释放全部 $released 条 Socket 会话", LogLevel.WARN)
                        }
                    }
                )

                // 右侧展示区 (图表 + 诊断建议 + 实时日志流)
                DesktopVisualPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    chartPoints = chartPoints,
                    currentStats = currentStats,
                    logs = logs,
                    logListState = logListState
                )
            }
        }
    }
}

@Composable
private fun DesktopHeader(host: String, isRunning: Boolean, currentStats: ProtocolStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppleCardBg)
            .border(0.5.dp, AppleBorderHighlight, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NetSessionTester", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppleTextPrimary)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(AppleBlue.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Desktop Edition", color = AppleBlue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("全平台并发网络会话与路由承载能力测试", fontSize = 11.sp, color = AppleTextSecondary)
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isRunning) AppleGreen else AppleOrange, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (isRunning) "压测运行中 (${currentStats.phase})" else "待命就绪",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isRunning) AppleGreen else AppleTextSecondary
            )
        }
    }
}

@Composable
private fun DesktopControlPanel(
    modifier: Modifier = Modifier,
    host: String,
    port: String,
    mode: TestMode,
    targetCps: Int,
    successLimit: Int,
    failureLimit: Int,
    keepConnections: Boolean,
    isRunning: Boolean,
    currentStats: ProtocolStats,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onModeChange: (TestMode) -> Unit,
    onCpsChange: (Int) -> Unit,
    onSuccessLimitChange: (Int) -> Unit,
    onFailureLimitChange: (Int) -> Unit,
    onKeepChange: (Boolean) -> Unit,
    onStartStop: () -> Unit,
    onRelease: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppleCardBg)
            .border(0.5.dp, AppleBorderHighlight, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("测试参数配置", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppleTextPrimary)

        // 目标域名与端口
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("目标地址", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("端口", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp)
            )
        }

        // 测试模式选择
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TestMode.entries.forEach { item ->
                val selected = mode == item
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) AppleBlue else AppleGrayBg)
                        .clickable { onModeChange(item) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.label,
                        color = if (selected) Color.White else AppleTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // CPS 速率滑块
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("目标速率 (CPS)", fontSize = 12.sp, color = AppleTextSecondary)
                Text("$targetCps /s", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppleBlue)
            }
            Slider(
                value = targetCps.toFloat(),
                onValueChange = { onCpsChange(it.toInt()) },
                valueRange = 50f..5000f,
                steps = 19
            )
        }

        // 会话目标与失败上限
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = successLimit.toString(),
                onValueChange = { onSuccessLimitChange(it.toIntOrNull() ?: 10000) },
                label = { Text("目标连接数", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = failureLimit.toString(),
                onValueChange = { onFailureLimitChange(it.toIntOrNull() ?: 2000) },
                label = { Text("失败保护上限", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        // 保持连接开关
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("测试后保持会话", fontSize = 12.sp, color = AppleTextPrimary, modifier = Modifier.weight(1f))
            Switch(checked = keepConnections, onCheckedChange = onKeepChange)
        }

        Spacer(Modifier.weight(1f))

        // 当前指标快照卡片
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AppleGrayBg)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MetricPill("活动会话", currentStats.activeSessions.toString(), AppleBlue)
            MetricPill("成功", currentStats.totalSuccess.toString(), AppleGreen)
            MetricPill("失败", currentStats.totalFailure.toString(), AppleRed)
            MetricPill("瞬时CPS", "${currentStats.cps}/s", ApplePurple)
        }

        // 开始/停止控制大按钮
        Button(
            onClick = onStartStop,
            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) AppleRed else AppleBlue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text(if (isRunning) "停止测试" else "开始并发压测", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onRelease,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            Text("一键释放全部连接", fontSize = 12.sp, color = AppleTextSecondary)
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = AppleTextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun DesktopVisualPanel(
    modifier: Modifier = Modifier,
    chartPoints: List<ChartPoint>,
    currentStats: ProtocolStats,
    logs: List<LogLine>,
    logListState: androidx.compose.foundation.lazy.LazyListState
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 折线图卡片 (纯净极简 Apple 风格，无蓝绿分层色块)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .clip(RoundedCornerShape(16.dp))
                .background(AppleCardBg)
                .border(0.5.dp, AppleBorderHighlight, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("实时会话承载走势", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppleTextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("峰值: ${currentStats.maxStableSessions}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppleBlue)
                }

                Spacer(Modifier.height(8.dp))

                // 折线图画布
                DesktopChartCanvas(points = chartPoints, modifier = Modifier.fillMaxSize().weight(1f))

                Spacer(Modifier.height(4.dp))
                Text("说明：折线为实时活动会话；底色纯净无分层；桌面端无移动端沙盒套接字上限限制。", fontSize = 10.sp, color = AppleTextSecondary)
            }
        }

        // 诊断建议与实时日志流 (下半部分)
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // 诊断建议卡片 (严格对齐序号与换行文本)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleCardBg)
                    .border(0.5.dp, AppleBorderHighlight, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("诊断分析建议", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppleOrange)
                    AdviceRow(1, "当前为桌面操作系统，TCP 协议栈可支持数万并发会话。")
                    AdviceRow(2, "若出现连接超时或拒绝，请排查目标服务器防火墙与端口限制。")
                    AdviceRow(3, "观察路由器 CPU 与 NAT 会话表，评估路由器硬件承载能力。")
                }
            }

            // 实时日志流卡片 (Apple 原生无分割线设计)
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleCardBg)
                    .border(0.5.dp, AppleBorderHighlight, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("运行事件日志", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppleTextPrimary)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(logs) { line ->
                            DesktopLogItem(line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopChartCanvas(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 绘制 4 条水平参考网格线
        repeat(4) { i ->
            val y = h * (i + 1) / 5f
            drawLine(
                color = Color(0x10000000),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        if (points.size < 2) return@Canvas

        val maxVal = points.maxOfOrNull { it.active }?.coerceAtLeast(100) ?: 100
        val maxSec = points.maxOfOrNull { it.elapsedSec }?.coerceAtLeast(1) ?: 1

        fun xOf(sec: Int): Float = (sec.toFloat() / maxSec.toFloat()) * w
        fun yOf(active: Int): Float = h - (active.toFloat() / maxVal.toFloat()) * (h * 0.9f)

        val path = Path()
        points.forEachIndexed { idx, pt ->
            val px = xOf(pt.elapsedSec)
            val py = yOf(pt.active)
            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }

        // 绘制柔和纯净折线
        drawPath(
            path = path,
            color = AppleBlue,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun AdviceRow(index: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$index.",
            color = AppleBlue,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = text,
            color = AppleTextPrimary.copy(alpha = 0.85f),
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DesktopLogItem(line: LogLine) {
    val tagColor = when (line.level) {
        LogLevel.INFO -> AppleTextSecondary
        LogLevel.SUCCESS -> AppleGreen
        LogLevel.WARN -> AppleOrange
        LogLevel.ERROR -> AppleRed
        LogLevel.STAT -> ApplePurple
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(line.timeText, fontSize = 10.sp, color = AppleTextSecondary, fontFamily = FontFamily.Monospace, modifier = Modifier.width(54.dp))
        Box(
            modifier = Modifier
                .background(tagColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(line.level.name, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = tagColor)
        }
        Spacer(Modifier.width(8.dp))
        Text(line.text, fontSize = 11.sp, color = AppleTextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
    }
}
