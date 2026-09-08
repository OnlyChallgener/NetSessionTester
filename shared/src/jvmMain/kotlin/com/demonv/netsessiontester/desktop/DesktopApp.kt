package com.demonv.netsessiontester.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.demonv.netsessiontester.engine.DesktopPingTester
import com.demonv.netsessiontester.engine.DesktopTcpTester
import com.demonv.netsessiontester.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Cursor
import java.awt.Window

private val AppleBlue = Color(0xFF007AFF)
private val AppleGreen = Color(0xFF34C759)
private val AppleOrange = Color(0xFFFF9500)
private val AppleRed = Color(0xFFFF3B30)
private val ApplePurple = Color(0xFF5856D6)
private val WindowBg = Color(0xFFF3F3F5)
private val CardBg = Color(0xFFFFFFFF)
private val CardBorder = Color(0x18000000)
private val TextPrimary = Color(0xFF1C1C1E)
private val TextSecondary = Color(0xFF8E8E93)
private val InputBg = Color(0xFFF6F6F8)
private val InputBorder = Color(0xFFDCDCE2)

@Composable
fun WindowScope.DesktopApp(
    windowState: WindowState,
    window: Window? = null,
    onClose: () -> Unit = {},
    isWindows: Boolean = true
) {
    val sessionTester = remember { DesktopTcpTester() }
    val pingTester = remember { DesktopPingTester() }
    val scope = rememberCoroutineScope()

    var appMode by remember { mutableStateOf(AppMode.UNDERLOAD_PING) }
    var host by remember { mutableStateOf("www.baidu.com") }
    var port by remember { mutableStateOf("80") }
    var testMode by remember { mutableStateOf(TestMode.IPV4_ONLY) }
    var targetCps by remember { mutableStateOf(500) }
    var successLimit by remember { mutableStateOf(10000) }
    var failureLimit by remember { mutableStateOf(2000) }
    var keepConnections by remember { mutableStateOf(true) }
    var pingIntervalMs by remember { mutableStateOf(400L) }

    var isRunning by remember { mutableStateOf(false) }
    var currentStats by remember { mutableStateOf(ProtocolStats(IpProtocol.IPV4)) }
    var currentPingStats by remember { mutableStateOf(PingStats()) }
    var chartSamples by remember { mutableStateOf(listOf<DualChartPoint>()) }
    var logs by remember { mutableStateOf(listOf(LogLine(text = "NetSessionTester 桌面版就绪 (Windows 11 Fluent / macOS Tahoe)"))) }

    var testJob by remember { mutableStateOf<Job?>(null) }
    val logListState = rememberLazyListState()

    fun log(text: String, level: LogLevel = LogLevel.INFO) {
        logs = logs + LogLine(text = text, level = level)
    }

    // 动态采样图表数据
    LaunchedEffect(isRunning, appMode) {
        if (isRunning) {
            val startMs = System.currentTimeMillis()
            while (isRunning) {
                val sec = ((System.currentTimeMillis() - startMs) / 1000L).toInt()
                chartSamples = chartSamples + DualChartPoint(
                    elapsedSec = sec,
                    activeSessions = currentStats.activeSessions,
                    pingLatencyMs = currentPingStats.currentLatencyMs
                )
                delay(300L)
            }
        }
    }

    // 日志平滑滚动
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    val isMaximized = windowState.placement == WindowPlacement.Maximized

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WindowBg)
            .then(
                if (isWindows && !isMaximized) {
                    Modifier.border(1.dp, CardBorder, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. 顶栏：完全融合式现代标题栏 (消除原生黑边与双层顶栏)
            DesktopTitleBar(
                appMode = appMode,
                isRunning = isRunning,
                isWindows = isWindows,
                isMaximized = isMaximized,
                window = window,
                onModeChange = {
                    if (!isRunning) {
                        appMode = it
                        chartSamples = emptyList()
                    }
                },
                onMinimize = { windowState.isMinimized = true },
                onMaximizeToggle = {
                    windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
                },
                onClose = onClose
            )

            // 2. 主双栏结构 (左侧紧凑表单，右侧数据仪表盘)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 左侧紧凑控制表单 (330dp)
                DesktopControlCard(
                    modifier = Modifier.width(330.dp).fillMaxHeight(),
                    appMode = appMode,
                    host = host,
                    port = port,
                    testMode = testMode,
                    targetCps = targetCps,
                    successLimit = successLimit,
                    failureLimit = failureLimit,
                    keepConnections = keepConnections,
                    pingIntervalMs = pingIntervalMs,
                    isRunning = isRunning,
                    onHostChange = { host = it },
                    onPortChange = { port = it },
                    onTestModeChange = { testMode = it },
                    onCpsChange = { targetCps = it },
                    onSuccessLimitChange = { successLimit = it },
                    onFailureLimitChange = { failureLimit = it },
                    onKeepChange = { keepConnections = it },
                    onPingIntervalChange = { pingIntervalMs = it },
                    onStartStop = {
                        if (isRunning) {
                            testJob?.cancel()
                            pingTester.stop()
                            isRunning = false
                            log("用户主动停止测试", LogLevel.WARN)
                        } else {
                            isRunning = true
                            chartSamples = emptyList()
                            when (appMode) {
                                AppMode.SESSION_HOLD -> {
                                    val config = SessionConfig(
                                        host = host,
                                        port = port.toIntOrNull() ?: 80,
                                        mode = testMode,
                                        batchSize = targetCps,
                                        successLimit = successLimit,
                                        failureLimit = failureLimit,
                                        keepConnectionsAfterStop = keepConnections
                                    )
                                    testJob = scope.launch {
                                        sessionTester.runSessionHoldTest(
                                            rawConfig = config,
                                            onStats = { currentStats = it },
                                            onLog = { log(it.text, it.level) }
                                        )
                                        isRunning = false
                                    }
                                }
                                AppMode.PING_STANDALONE -> {
                                    testJob = scope.launch {
                                        pingTester.runContinuousPing(
                                            host = host,
                                            port = port.toIntOrNull() ?: 80,
                                            intervalMs = pingIntervalMs,
                                            timeoutMs = 1000,
                                            onStats = { currentPingStats = it },
                                            onLog = { log(it.text, it.level) }
                                        )
                                        isRunning = false
                                    }
                                }
                                AppMode.UNDERLOAD_PING -> {
                                    val config = SessionConfig(
                                        host = host,
                                        port = port.toIntOrNull() ?: 80,
                                        mode = testMode,
                                        batchSize = targetCps,
                                        successLimit = successLimit,
                                        failureLimit = failureLimit,
                                        keepConnectionsAfterStop = keepConnections
                                    )
                                    testJob = scope.launch {
                                        sessionTester.runSessionHoldTest(
                                            rawConfig = config,
                                            onStats = { currentStats = it },
                                            onLog = { log(it.text, it.level) },
                                            onPingSample = { latency ->
                                                currentPingStats = currentPingStats.copy(currentLatencyMs = latency)
                                            }
                                        )
                                        isRunning = false
                                    }
                                }
                            }
                        }
                    },
                    onRelease = {
                        scope.launch {
                            val released = sessionTester.release()
                            currentStats = currentStats.copy(activeSessions = 0, phase = "已释放")
                            log("已强制释放全部 $released 条长连接", LogLevel.WARN)
                        }
                    }
                )

                // 右侧专业数据仪表盘 (弹性宽屏)
                DesktopDashboardCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    appMode = appMode,
                    isRunning = isRunning,
                    currentStats = currentStats,
                    currentPingStats = currentPingStats,
                    chartSamples = chartSamples,
                    successLimit = successLimit,
                    logs = logs,
                    logListState = logListState
                )
            }
        }

        // 3. 在 Windows 浮动无边框模式下提供边缘与四角平滑拖拽缩放手柄
        if (isWindows && window != null && !isMaximized) {
            WindowResizeBorders(window = window)
        }
    }
}

/**
 * 融合式现代标题栏 (参考 Antigravity IDE 质感)
 */
@Composable
private fun DesktopTitleBar(
    appMode: AppMode,
    isRunning: Boolean,
    isWindows: Boolean,
    isMaximized: Boolean,
    window: Window?,
    onModeChange: (AppMode) -> Unit,
    onMinimize: () -> Unit,
    onMaximizeToggle: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(CardBg)
            .border(0.5.dp, Color(0x14000000))
            .pointerInput(isMaximized, window) {
                detectDragGestures { _, dragAmount ->
                    window?.let { w ->
                        if (!isMaximized) {
                            w.setLocation(w.x + dragAmount.x.toInt(), w.y + dragAmount.y.toInt())
                        }
                    }
                }
            }
            .padding(start = 12.dp, end = if (isWindows) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
            // 应用小波形图标
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(AppleBlue, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    val p = Path().apply {
                        moveTo(0f, size.height * 0.5f)
                        lineTo(size.width * 0.22f, size.height * 0.5f)
                        lineTo(size.width * 0.40f, size.height * 0.12f)
                        lineTo(size.width * 0.58f, size.height * 0.88f)
                        lineTo(size.width * 0.74f, size.height * 0.5f)
                        lineTo(size.width, size.height * 0.5f)
                    }
                    drawPath(p, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round))
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = "NetSessionTester",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(AppleBlue.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
            ) {
                Text("v1.0.22", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = AppleBlue)
            }

            Spacer(Modifier.width(16.dp))

            // 核心模式切换分段器
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(WindowBg)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AppMode.entries.forEach { mode ->
                    val selected = appMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (selected) CardBg else Color.Transparent)
                            .border(
                                if (selected) 0.5.dp else 0.dp,
                                if (selected) Color(0x20000000) else Color.Transparent,
                                RoundedCornerShape(5.dp)
                            )
                            .clickable(enabled = !isRunning) { onModeChange(mode) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) AppleBlue else TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f)) // 可拖动空白区域

            // 运行状态指示器
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isRunning) AppleGreen else TextSecondary.copy(alpha = 0.5f), CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "运行中" else "待命就绪",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isRunning) AppleGreen else TextSecondary
                )
            }

            // Windows 平台专属：原生样式融合三键
            if (isWindows) {
                DesktopWindowControls(
                    isMaximized = isMaximized,
                    onMinimize = onMinimize,
                    onMaximizeToggle = onMaximizeToggle,
                    onClose = onClose
                )
            }
        }
    }

/**
 * Windows 现代窗口控制三键 (最小化、最大化/还原、关闭)
 */
@Composable
private fun DesktopWindowControls(
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onMaximizeToggle: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WindowControlButton(
            hoverBg = Color(0x12000000),
            onClick = onMinimize
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawLine(
                    color = TextPrimary,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.2f
                )
            }
        }

        WindowControlButton(
            hoverBg = Color(0x12000000),
            onClick = onMaximizeToggle
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                if (isMaximized) {
                    drawRect(
                        color = TextPrimary,
                        topLeft = Offset(2f, 0f),
                        size = Size(size.width - 2f, size.height - 2f),
                        style = Stroke(width = 1.2f)
                    )
                    drawRect(
                        color = TextPrimary,
                        topLeft = Offset(0f, 2f),
                        size = Size(size.width - 2f, size.height - 2f),
                        style = Stroke(width = 1.2f)
                    )
                } else {
                    drawRect(
                        color = TextPrimary,
                        topLeft = Offset.Zero,
                        size = size,
                        style = Stroke(width = 1.2f)
                    )
                }
            }
        }

        WindowControlButton(
            hoverBg = Color(0xFFE81123),
            onClick = onClose
        ) { isHovered ->
            Canvas(modifier = Modifier.size(10.dp)) {
                val c = if (isHovered) Color.White else TextPrimary
                drawLine(c, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.2f)
                drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.2f)
            }
        }
    }
}

@Composable
private fun WindowControlButton(
    hoverBg: Color,
    onClick: () -> Unit,
    content: @Composable (isHovered: Boolean) -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(if (isHovered) hoverBg else Color.Transparent)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content(isHovered)
    }
}

/**
 * Windows 无边框窗口平滑缩放手柄
 */
@Composable
private fun BoxScope.WindowResizeBorders(
    window: Window,
    minWidth: Int = 920,
    minHeight: Int = 620
) {
    val edgeThickness = 5.dp
    val cornerSize = 10.dp

    // Top
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(edgeThickness)
            .align(Alignment.TopCenter)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val dy = dragAmount.y.toInt()
                    val newH = (window.height - dy).coerceAtLeast(minHeight)
                    val newY = window.y + (window.height - newH)
                    window.setBounds(window.x, newY, window.width, newH)
                }
            }
    )
    // Bottom
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(edgeThickness)
            .align(Alignment.BottomCenter)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val dy = dragAmount.y.toInt()
                    val newH = (window.height + dy).coerceAtLeast(minHeight)
                    window.setSize(window.width, newH)
                }
            }
    )
    // Left
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(edgeThickness)
            .align(Alignment.CenterStart)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val dx = dragAmount.x.toInt()
                    val newW = (window.width - dx).coerceAtLeast(minWidth)
                    val newX = window.x + (window.width - newW)
                    window.setBounds(newX, window.y, newW, window.height)
                }
            }
    )
    // Right
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(edgeThickness)
            .align(Alignment.CenterEnd)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val dx = dragAmount.x.toInt()
                    val newW = (window.width + dx).coerceAtLeast(minWidth)
                    window.setSize(newW, window.height)
                }
            }
    )
    // Bottom-Right Corner
    Box(
        modifier = Modifier
            .size(cornerSize)
            .align(Alignment.BottomEnd)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val dx = dragAmount.x.toInt()
                    val dy = dragAmount.y.toInt()
                    val newW = (window.width + dx).coerceAtLeast(minWidth)
                    val newH = (window.height + dy).coerceAtLeast(minHeight)
                    window.setSize(newW, newH)
                }
            }
    )
    // Bottom-Left Corner
    Box(
        modifier = Modifier
            .size(cornerSize)
            .align(Alignment.BottomStart)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val dx = dragAmount.x.toInt()
                    val dy = dragAmount.y.toInt()
                    val newW = (window.width - dx).coerceAtLeast(minWidth)
                    val newX = window.x + (window.width - newW)
                    val newH = (window.height + dy).coerceAtLeast(minHeight)
                    window.setBounds(newX, window.y, newW, newH)
                }
            }
    )
    // Top-Right Corner
    Box(
        modifier = Modifier
            .size(cornerSize)
            .align(Alignment.TopEnd)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val dx = dragAmount.x.toInt()
                    val dy = dragAmount.y.toInt()
                    val newW = (window.width + dx).coerceAtLeast(minWidth)
                    val newH = (window.height - dy).coerceAtLeast(minHeight)
                    val newY = window.y + (window.height - newH)
                    window.setBounds(window.x, newY, newW, newH)
                }
            }
    )
    // Top-Left Corner
    Box(
        modifier = Modifier
            .size(cornerSize)
            .align(Alignment.TopStart)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val dx = dragAmount.x.toInt()
                    val dy = dragAmount.y.toInt()
                    val newW = (window.width - dx).coerceAtLeast(minWidth)
                    val newX = window.x + (window.width - newW)
                    val newH = (window.height - dy).coerceAtLeast(minHeight)
                    val newY = window.y + (window.height - newH)
                    window.setBounds(newX, window.y, newW, newH)
                }
            }
    )
}

/**
 * 左侧紧凑表单面板 (基于桌面紧凑规范，彻底淘汰 Android 粗框 OutlinedTextField)
 */
@Composable
private fun DesktopControlCard(
    modifier: Modifier,
    appMode: AppMode,
    host: String,
    port: String,
    testMode: TestMode,
    targetCps: Int,
    successLimit: Int,
    failureLimit: Int,
    keepConnections: Boolean,
    pingIntervalMs: Long,
    isRunning: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTestModeChange: (TestMode) -> Unit,
    onCpsChange: (Int) -> Unit,
    onSuccessLimitChange: (Int) -> Unit,
    onFailureLimitChange: (Int) -> Unit,
    onKeepChange: (Boolean) -> Unit,
    onPingIntervalChange: (Long) -> Unit,
    onStartStop: () -> Unit,
    onRelease: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("配置参数", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        // 目标地址与端口 (紧凑同行)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("目标", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.width(42.dp))
            DesktopCompactInput(
                value = host,
                onValueChange = onHostChange,
                placeholder = "IP 或 域名",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            DesktopCompactInput(
                value = port,
                onValueChange = onPortChange,
                placeholder = "端口",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.width(58.dp)
            )
        }

        if (appMode != AppMode.PING_STANDALONE) {
            // 网络模式选择
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("协议", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.width(42.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(InputBg)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TestMode.entries.forEach { item ->
                        val selected = testMode == item
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) CardBg else Color.Transparent)
                                .clickable { onTestModeChange(item) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                item.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) AppleBlue else TextSecondary
                            )
                        }
                    }
                }
            }

            // 发射速率 CPS
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("目标速率", fontSize = 11.5.sp, color = TextSecondary)
                    Text("$targetCps CPS", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AppleBlue)
                }
                Slider(
                    value = targetCps.toFloat(),
                    onValueChange = { onCpsChange(it.toInt()) },
                    valueRange = 50f..5000f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )
            }

            // 目标连接数与上限
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("目标", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.width(42.dp))
                DesktopCompactInput(
                    value = successLimit.toString(),
                    onValueChange = { onSuccessLimitChange(it.toIntOrNull() ?: 10000) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text("上限", fontSize = 11.5.sp, color = TextSecondary)
                Spacer(Modifier.width(4.dp))
                DesktopCompactInput(
                    value = failureLimit.toString(),
                    onValueChange = { onFailureLimitChange(it.toIntOrNull() ?: 2000) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            // 保持连接开关
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("测试完成后保持连接", fontSize = 11.5.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                Switch(checked = keepConnections, onCheckedChange = onKeepChange, modifier = Modifier.height(26.dp))
            }
        }

        if (appMode == AppMode.PING_STANDALONE || appMode == AppMode.UNDERLOAD_PING) {
            // Ping 探测周期设置
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ping周期", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.width(58.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(InputBg)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(200L, 500L, 1000L).forEach { iv ->
                        val selected = pingIntervalMs == iv
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) CardBg else Color.Transparent)
                                .clickable { onPingIntervalChange(iv) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${iv}ms",
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) AppleOrange else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 操作控制区
        Button(
            onClick = onStartStop,
            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) AppleRed else AppleBlue),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            Text(
                if (isRunning) "停止测试" else when (appMode) {
                    AppMode.SESSION_HOLD -> "开始并发压测"
                    AppMode.PING_STANDALONE -> "开始 Ping 诊断"
                    AppMode.UNDERLOAD_PING -> "启动联动全量测试"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (appMode != AppMode.PING_STANDALONE) {
            OutlinedButton(
                onClick = onRelease,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(32.dp)
            ) {
                Text("一键释放长连接", fontSize = 11.5.sp, color = TextSecondary)
            }
        }
    }
}

/**
 * 桌面标准紧凑输入控件 (高度 32dp，0.5dp 柔边，浅微灰底色，彻底摒弃移动端粗框)
 */
@Composable
private fun DesktopCompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 12.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(InputBg)
            .border(0.5.dp, InputBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.7f))
                }
                innerTextField()
            }
        }
    )
}

/**
 * 右侧仪表盘 (指标瓷片 + 双轴折线图 + 诊断建议 + 事件日志)
 */
@Composable
private fun DesktopDashboardCard(
    modifier: Modifier,
    appMode: AppMode,
    isRunning: Boolean,
    currentStats: ProtocolStats,
    currentPingStats: PingStats,
    chartSamples: List<DualChartPoint>,
    successLimit: Int,
    logs: List<LogLine>,
    logListState: androidx.compose.foundation.lazy.LazyListState
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 顶部核心数据瓷片 (4 块紧凑胶囊)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (appMode) {
                AppMode.SESSION_HOLD -> {
                    CompactMetricCard(Modifier.weight(1f), "活动会话", currentStats.activeSessions.toString(), AppleBlue)
                    CompactMetricCard(Modifier.weight(1f), "成功会话", currentStats.totalSuccess.toString(), AppleGreen)
                    CompactMetricCard(Modifier.weight(1f), "失败会话", currentStats.totalFailure.toString(), AppleRed)
                    CompactMetricCard(Modifier.weight(1f), "瞬时速率", "${currentStats.cps}/s", ApplePurple)
                }
                AppMode.PING_STANDALONE -> {
                    CompactMetricCard(Modifier.weight(1f), "当前延迟", "${currentPingStats.currentLatencyMs} ms", AppleOrange)
                    CompactMetricCard(Modifier.weight(1f), "最小 / 最大", "${currentPingStats.minLatencyMs} / ${currentPingStats.maxLatencyMs} ms", AppleBlue)
                    CompactMetricCard(Modifier.weight(1f), "网络抖动", "${currentPingStats.jitterMs} ms", ApplePurple)
                    CompactMetricCard(Modifier.weight(1f), "丢包率", String.format("%.1f%%", currentPingStats.lossPercent), if (currentPingStats.lossPercent > 0) AppleRed else AppleGreen)
                }
                AppMode.UNDERLOAD_PING -> {
                    CompactMetricCard(Modifier.weight(1f), "并发会话", currentStats.activeSessions.toString(), AppleBlue)
                    CompactMetricCard(Modifier.weight(1f), "实时延迟", "${currentPingStats.currentLatencyMs} ms", AppleOrange)
                    CompactMetricCard(Modifier.weight(1f), "丢包率", String.format("%.1f%%", currentPingStats.lossPercent), if (currentPingStats.lossPercent > 0) AppleRed else AppleGreen)
                    CompactMetricCard(Modifier.weight(1f), "瞬时速率", "${currentStats.cps}/s", ApplePurple)
                }
            }
        }

        // 双轴走势图卡片 (带双 Y 轴刻度、底部 X 轴时间刻度与 Hover 实时探针)
        DesktopDualChartCard(
            samples = chartSamples,
            appMode = appMode,
            currentStats = currentStats,
            currentPingStats = currentPingStats,
            successLimit = successLimit,
            modifier = Modifier.fillMaxWidth().weight(1.3f)
        )

        // 下方并列：诊断分析建议与事件日志
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 诊断建议 (修复中文字体叠字错位与基线对齐)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("诊断分析建议", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppleOrange)

                    when (appMode) {
                        AppMode.SESSION_HOLD -> {
                            DesktopAdviceRow("1", "桌面操作系统 TCP 协议栈可原生支撑上万级并发会话。")
                            DesktopAdviceRow("2", "若出现大量超时或拒绝，请排查目标服务防火墙与端口限制。")
                            DesktopAdviceRow("3", "观察路由器 CPU 与 NAT 表，评估路由器硬件并发极限。")
                        }
                        AppMode.PING_STANDALONE -> {
                            DesktopAdviceRow("1", "正常局域网 RTT 应 < 5ms，城域公网应稳定在 10 ~ 40ms。")
                            DesktopAdviceRow("2", "若抖动 (Jitter) 超过 30ms，说明上行链路存在排队竞争。")
                            DesktopAdviceRow("3", "出现丢包往往由无线信号衰减或运营商拥塞造成。")
                        }
                        AppMode.UNDERLOAD_PING -> {
                            DesktopAdviceRow("1", "并发数爬升时若延迟由 15ms 陡增至 300ms+，表明发生 Bufferbloat。")
                            DesktopAdviceRow("2", "若延迟平稳但连接失败，说明触碰到了目标端口最大并发连接限制。")
                            DesktopAdviceRow("3", "两者曲线可直接确定网络设备的健康承载拐点。")
                        }
                    }
                }
            }

            // 事件日志流 (Apple Console 规范)
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("运行事件日志", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
private fun CompactMetricCard(modifier: Modifier, label: String, value: String, color: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Column {
            Text(label, fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/**
 * 专业双轴走势图卡片 (带左 Y 轴会话数、右 Y 轴延迟毫秒、底部 X 轴时间刻度与 Hover 实时探针)
 */
@Composable
private fun DesktopDualChartCard(
    samples: List<DualChartPoint>,
    appMode: AppMode,
    currentStats: ProtocolStats,
    currentPingStats: PingStats,
    successLimit: Int,
    modifier: Modifier = Modifier
) {
    var hoveredPoint by remember { mutableStateOf<DualChartPoint?>(null) }

    // 计算刻度上下限
    val maxSec = samples.maxOfOrNull { it.elapsedSec }?.coerceAtLeast(10) ?: 10
    val rawMaxSessions = samples.maxOfOrNull { it.activeSessions }?.coerceAtLeast(100)
        ?: if (appMode != AppMode.PING_STANDALONE) successLimit.coerceAtLeast(1000) else 100
    val maxSessions = roundUpSessions(rawMaxSessions)

    val rawMaxLatency = samples.maxOfOrNull { it.pingLatencyMs }?.coerceAtLeast(50) ?: 100
    val maxLatency = roundUpLatency(rawMaxLatency)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部走势图标题与实时探针数据显示区
            Row(
                modifier = Modifier.fillMaxWidth().height(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (appMode) {
                        AppMode.SESSION_HOLD -> "并发会话承载走势"
                        AppMode.PING_STANDALONE -> "网络往返延迟 (RTT) 走势"
                        AppMode.UNDERLOAD_PING -> "会话承载与网络延迟对照走势 (Bufferbloat 检测)"
                    },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(Modifier.weight(1f))

                // 若鼠标正在悬浮拾取，显示高亮实时探针指示条
                if (hoveredPoint != null) {
                    val hp = hoveredPoint!!
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(AppleBlue.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, AppleBlue.copy(alpha = 0.20f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⏱ ${hp.elapsedSec}s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (appMode != AppMode.PING_STANDALONE) {
                            Text("● 会话: ${formatCompactNumber(hp.activeSessions)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppleBlue)
                        }
                        if (appMode != AppMode.SESSION_HOLD) {
                            Text("● 延迟: ${hp.pingLatencyMs} ms", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppleOrange)
                        }
                    }
                } else {
                    // 常态图例指示
                    if (appMode == AppMode.UNDERLOAD_PING) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(AppleBlue, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(4.dp))
                            Text("并发数(主轴)", fontSize = 10.sp, color = TextSecondary)
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.size(8.dp).background(AppleOrange, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(4.dp))
                            Text("延迟(副轴)", fontSize = 10.sp, color = TextSecondary)
                        }
                    } else if (appMode == AppMode.SESSION_HOLD) {
                        Text("峰值: ${currentStats.maxStableSessions}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppleBlue)
                    } else {
                        Text("均值: ${currentPingStats.avgLatencyMs} ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppleOrange)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 中部：双 Y 轴与画布容器
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                // 左侧主 Y 轴 (并发会话数)
                Column(
                    modifier = Modifier.width(36.dp).fillMaxHeight().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    if (appMode != AppMode.PING_STANDALONE) {
                        Text(formatCompactNumber(maxSessions), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = AppleBlue)
                        Text(formatCompactNumber((maxSessions * 0.75).toInt()), fontSize = 8.5.sp, color = AppleBlue.copy(alpha = 0.75f))
                        Text(formatCompactNumber((maxSessions * 0.50).toInt()), fontSize = 8.5.sp, color = AppleBlue.copy(alpha = 0.75f))
                        Text(formatCompactNumber((maxSessions * 0.25).toInt()), fontSize = 8.5.sp, color = AppleBlue.copy(alpha = 0.75f))
                        Text("0", fontSize = 9.sp, color = AppleBlue.copy(alpha = 0.6f))
                    } else {
                        repeat(5) { Spacer(Modifier.height(10.dp)) }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // 核心走势图画布与 Hover 实时探针
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    DesktopDualChartCanvas(
                        samples = samples,
                        appMode = appMode,
                        maxSec = maxSec,
                        maxSessions = maxSessions,
                        maxLatency = maxLatency,
                        hoveredPoint = hoveredPoint,
                        onHoverChange = { hoveredPoint = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.width(6.dp))

                // 右侧副 Y 轴 (延迟毫秒)
                Column(
                    modifier = Modifier.width(38.dp).fillMaxHeight().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    if (appMode != AppMode.SESSION_HOLD) {
                        Text("${maxLatency}ms", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = AppleOrange)
                        Text("${(maxLatency * 0.75).toInt()}ms", fontSize = 8.5.sp, color = AppleOrange.copy(alpha = 0.75f))
                        Text("${(maxLatency * 0.50).toInt()}ms", fontSize = 8.5.sp, color = AppleOrange.copy(alpha = 0.75f))
                        Text("${(maxLatency * 0.25).toInt()}ms", fontSize = 8.5.sp, color = AppleOrange.copy(alpha = 0.75f))
                        Text("0ms", fontSize = 9.sp, color = AppleOrange.copy(alpha = 0.6f))
                    } else {
                        repeat(5) { Spacer(Modifier.height(10.dp)) }
                    }
                }
            }

            // 底部 X 轴 (时间刻度)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 42.dp, end = 44.dp, top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(5) { i ->
                    val sec = (maxSec * i / 4)
                    Text(
                        text = "${sec}s",
                        fontSize = 9.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopDualChartCanvas(
    samples: List<DualChartPoint>,
    appMode: AppMode,
    maxSec: Int,
    maxSessions: Int,
    maxLatency: Int,
    hoveredPoint: DualChartPoint?,
    onHoverChange: (DualChartPoint?) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(samples, maxSec) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move -> {
                                val pos = event.changes.firstOrNull()?.position
                                if (pos != null && samples.isNotEmpty()) {
                                    val ratio = (pos.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val secTarget = ratio * maxSec
                                    val closest = samples.minByOrNull { kotlin.math.abs(it.elapsedSec - secTarget) }
                                    onHoverChange(closest)
                                }
                            }
                            PointerEventType.Exit -> {
                                onHoverChange(null)
                            }
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height

        // 绘制 4 条水平参考网格虚线
        repeat(4) { i ->
            val y = h * (i + 1) / 5f
            drawLine(
                color = Color(0x0E000000),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        if (samples.size < 2) return@Canvas

        fun xOf(sec: Int): Float = (sec.toFloat() / maxSec.toFloat()) * w
        fun ySession(sessions: Int): Float = h - (sessions.toFloat() / maxSessions.toFloat()) * (h * 0.88f)
        fun yPing(latency: Int): Float = h - (latency.toFloat() / maxLatency.toFloat()) * (h * 0.88f)

        // 1. 绘制会话数走势 (蓝色折线 + 渐变填充)
        if (appMode == AppMode.SESSION_HOLD || appMode == AppMode.UNDERLOAD_PING) {
            val sessionPath = Path()
            val fillPath = Path()
            samples.forEachIndexed { idx, s ->
                val px = xOf(s.elapsedSec)
                val py = ySession(s.activeSessions)
                if (idx == 0) {
                    sessionPath.moveTo(px, py)
                    fillPath.moveTo(px, h)
                    fillPath.lineTo(px, py)
                } else {
                    sessionPath.lineTo(px, py)
                    fillPath.lineTo(px, py)
                }
            }
            val lastX = xOf(samples.last().elapsedSec)
            fillPath.lineTo(lastX, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(AppleBlue.copy(alpha = 0.15f), Color.Transparent),
                    startY = 0f,
                    endY = h
                )
            )

            drawPath(
                path = sessionPath,
                color = AppleBlue,
                style = Stroke(width = 2.2f, cap = StrokeCap.Round)
            )
        }

        // 2. 绘制延迟走势 (橙色折线 + 渐变填充)
        if (appMode == AppMode.PING_STANDALONE || appMode == AppMode.UNDERLOAD_PING) {
            val pingPath = Path()
            val pingFillPath = Path()
            samples.forEachIndexed { idx, s ->
                val px = xOf(s.elapsedSec)
                val py = yPing(s.pingLatencyMs)
                if (idx == 0) {
                    pingPath.moveTo(px, py)
                    pingFillPath.moveTo(px, h)
                    pingFillPath.lineTo(px, py)
                } else {
                    pingPath.lineTo(px, py)
                    pingFillPath.lineTo(px, py)
                }
            }
            val lastX = xOf(samples.last().elapsedSec)
            pingFillPath.lineTo(lastX, h)
            pingFillPath.close()

            drawPath(
                path = pingFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(AppleOrange.copy(alpha = 0.12f), Color.Transparent),
                    startY = 0f,
                    endY = h
                )
            )

            drawPath(
                path = pingPath,
                color = AppleOrange,
                style = Stroke(width = 2.2f, cap = StrokeCap.Round)
            )
        }

        // 3. 鼠标悬浮拾取交互：发丝对齐线与高亮节点圆环
        if (hoveredPoint != null) {
            val hx = xOf(hoveredPoint.elapsedSec)
            // 绘制垂直发丝准星虚线
            drawLine(
                color = Color(0x60007AFF),
                start = Offset(hx, 0f),
                end = Offset(hx, h),
                strokeWidth = 1.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
            )

            // 会话数节点高亮环
            if (appMode == AppMode.SESSION_HOLD || appMode == AppMode.UNDERLOAD_PING) {
                val sy = ySession(hoveredPoint.activeSessions)
                drawCircle(color = AppleBlue.copy(alpha = 0.25f), radius = 6.5f, center = Offset(hx, sy))
                drawCircle(color = Color.White, radius = 4f, center = Offset(hx, sy))
                drawCircle(color = AppleBlue, radius = 2.5f, center = Offset(hx, sy))
            }

            // 延迟节点高亮环
            if (appMode == AppMode.PING_STANDALONE || appMode == AppMode.UNDERLOAD_PING) {
                val py = yPing(hoveredPoint.pingLatencyMs)
                drawCircle(color = AppleOrange.copy(alpha = 0.25f), radius = 6.5f, center = Offset(hx, py))
                drawCircle(color = Color.White, radius = 4f, center = Offset(hx, py))
                drawCircle(color = AppleOrange, radius = 2.5f, center = Offset(hx, py))
            }
        }
    }
}

private fun roundUpSessions(value: Int): Int {
    return when {
        value <= 500 -> 500
        value <= 1000 -> 1000
        value <= 2000 -> 2000
        value <= 5000 -> 5000
        value <= 10000 -> 10000
        value <= 20000 -> 20000
        else -> ((value + 4999) / 5000) * 5000
    }
}

private fun roundUpLatency(value: Int): Int {
    return when {
        value <= 50 -> 50
        value <= 100 -> 100
        value <= 200 -> 200
        value <= 300 -> 300
        value <= 500 -> 500
        value <= 1000 -> 1000
        else -> ((value + 199) / 200) * 200
    }
}

private fun formatCompactNumber(value: Int): String {
    return if (value >= 1000) {
        val k = value / 1000.0
        if (value % 1000 == 0) "${value / 1000}k" else String.format("%.1fk", k)
    } else {
        value.toString()
    }
}

/**
 * 修复叠字错位与基线对齐的诊断建议行
 */
@Composable
private fun DesktopAdviceRow(num: String, content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
                .background(AppleOrange.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = num,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = AppleOrange
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = content,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.2.sp,
            color = TextPrimary.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DesktopLogItem(line: LogLine) {
    val tagColor = when (line.level) {
        LogLevel.INFO -> TextSecondary
        LogLevel.SUCCESS -> AppleGreen
        LogLevel.WARN -> AppleOrange
        LogLevel.ERROR -> AppleRed
        LogLevel.STAT -> ApplePurple
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            line.timeText,
            fontSize = 10.sp,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(52.dp)
        )
        Box(
            modifier = Modifier
                .background(tagColor.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(line.level.name, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = tagColor)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            line.text,
            fontSize = 11.sp,
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}
