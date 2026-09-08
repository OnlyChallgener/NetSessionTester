package com.demonv.netsessiontester.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.demonv.netsessiontester.engine.DesktopPingTester
import com.demonv.netsessiontester.engine.DesktopTcpTester
import com.demonv.netsessiontester.model.*
import com.demonv.netsessiontester.history.DesktopHistoryStore
import kotlinx.coroutines.*
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Window
import java.net.URI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal val AppleBlue = Color(0xFF007AFF)
internal val AppleGreen = Color(0xFF34C759)
internal val AppleOrange = Color(0xFFFF9500)
internal val AppleRed = Color(0xFFFF3B30)
internal val ApplePurple = Color(0xFF5856D6)
internal val AppleCyan = Color(0xFF32ADE6)
internal val WindowBg = Color(0xFFF3F3F5)
internal val CardBg = Color(0xFFFFFFFF)
internal val CardBorder = Color(0x18000000)
internal val TextPrimary = Color(0xFF1C1C1E)
internal val TextSecondary = Color(0xFF8E8E93)
internal val InputBg = Color(0xFFF6F6F8)
internal val InputBorder = Color(0xFFDCDCE2)

const val CURRENT_APP_VERSION = "v1.0.22-beta1"
const val GITHUB_REPO_URL = "https://github.com/OnlyChallgener/NetSessionTester/releases"

enum class DesktopTab(val label: String, val appMode: AppMode? = null, val isHistory: Boolean = false) {
    SESSION_HOLD("并发压测", AppMode.SESSION_HOLD, false),
    PING_STANDALONE("独立 Ping", AppMode.PING_STANDALONE, false),
    UNDERLOAD_PING("联动诊断", AppMode.UNDERLOAD_PING, false),
    HISTORY("测试历史", null, true)
}

@Composable
fun WindowScope.DesktopApp(
    windowState: WindowState,
    window: Window? = null,
    onClose: () -> Unit = {},
    closeRequested: Boolean = false,
    isWindows: Boolean = true
) {
    val sessionTester = remember { DesktopTcpTester() }
    val pingTester = remember { DesktopPingTester() }
    val scope = rememberCoroutineScope()

    var historyVisible by remember { mutableStateOf(false) }
    var updateDialogVisible by remember { mutableStateOf(false) }
    var localCloseRequested by remember { mutableStateOf(false) }
    val historyError by DesktopHistoryStore.error.collectAsState()
    LaunchedEffect(Unit) { DesktopHistoryStore.load() }

    var appMode by remember { mutableStateOf(AppMode.UNDERLOAD_PING) }
    var host by remember { mutableStateOf("www.baidu.com") }
    var port by remember { mutableStateOf("80") }
    var testMode by remember { mutableStateOf(TestMode.IPV4_ONLY) }
    var targetCps by remember { mutableStateOf(500) }
    var successLimit by remember { mutableStateOf(10000) }
    var failureLimit by remember { mutableStateOf(2000) }
    var keepConnections by remember { mutableStateOf(true) }
    var pingIntervalMs by remember { mutableStateOf(500L) }
    var timeoutMs by remember { mutableStateOf(1500) }
    var intervalMs by remember { mutableStateOf(50L) }

    var isRunning by remember { mutableStateOf(false) }
    var isReleasing by remember { mutableStateOf(false) }
    var currentStats by remember { mutableStateOf(ProtocolStats(IpProtocol.IPV4)) }
    var currentPingStats by remember { mutableStateOf(PingStats()) }
    var chartSamples by remember { mutableStateOf(listOf<DualChartPoint>()) }
    var logs by remember { mutableStateOf(listOf(LogLine(text = "NetSessionTester 桌面版就绪 (Windows 11 Fluent / macOS Tahoe)"))) }

    var testJob by remember { mutableStateOf<Job?>(null) }
    var isStopping by remember { mutableStateOf(false) }
    var releaseOnStop by remember { mutableStateOf(false) }
    var runId by remember { mutableStateOf(0L) }
    var startedAtNanos by remember { mutableStateOf(0L) }
    val logListState = rememberLazyListState()

    fun log(text: String, level: LogLevel = LogLevel.INFO) {
        logs = (logs + LogLine(text = text, level = level)).takeLast(600)
    }

    fun appendPoint(active: Int? = null, ping: PingStats? = null, protocol: IpProtocol = currentStats.protocol) {
        val sampleAt = ping?.sampleTimeNanos?.takeIf { it > 0L } ?: System.nanoTime()
        val elapsedMs = ((sampleAt - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
        val point = DualChartPoint(
            elapsedMs = maxOf(elapsedMs, chartSamples.lastOrNull()?.elapsedMs ?: 0L),
            activeSessions = active,
            pingLatencyMs = ping?.currentLatencyMs,
            hasPingSample = ping != null,
            protocol = ping?.protocol ?: protocol
        )
        chartSamples = (chartSamples + point).takeLast(2400)
    }

    LaunchedEffect(closeRequested, localCloseRequested) {
        if (closeRequested || localCloseRequested) {
            isStopping = true
            releaseOnStop = true
            log("关闭程序，停止测试并释放连接", LogLevel.WARN)
            sessionTester.stop()
            pingTester.stop()
            testJob?.cancelAndJoin()
            sessionTester.close()
            pingTester.close()
            onClose()
        }
    }

    DisposableEffect(sessionTester, pingTester) {
        onDispose {
            testJob?.cancel()
            pingTester.close()
            sessionTester.close()
        }
    }

    LaunchedEffect(isRunning, currentStats.protocol) {
        if (!isRunning) {
            while (isActive) {
                val count = sessionTester.activeCount(currentStats.protocol)
                currentStats = currentStats.copy(activeSessions = count, cps = 0)
                delay(500L)
            }
        }
    }

    fun stopTest(release: Boolean = false) {
        if (isStopping) return
        isStopping = true
        val id = runId
        val runningJob = testJob
        releaseOnStop = release
        log(if (release) "停止测试并释放连接" else "用户主动停止测试", LogLevel.WARN)
        sessionTester.stop()
        pingTester.stop()
        runningJob?.cancel()
        scope.launch {
            runningJob?.join()
            if (release) {
                isReleasing = true
                val released = sessionTester.release()
                isReleasing = false
                if (runningJob == null) log("已释放 $released 条长连接", LogLevel.WARN)
            }
            if (id == runId) {
                currentStats = currentStats.copy(
                    activeSessions = sessionTester.activeCount(currentStats.protocol),
                    cps = 0,
                    phase = if (release) "已释放" else "已停止"
                )
                currentPingStats = currentPingStats.copy(isRunning = false)
                isRunning = false
                isStopping = false
                testJob = null
            }
        }
    }

    fun startTest() {
        if (isRunning || isStopping || testJob?.isActive == true) return
        val targetPort = port.toIntOrNull()
        if (host.isBlank() || targetPort == null || targetPort !in 1..65535) {
            log("请输入有效目标和 1–65535 范围内的端口", LogLevel.ERROR)
            return
        }
        val selectedMode = appMode
        val selectedProtocol = if (testMode == TestMode.IPV6_ONLY) IpProtocol.IPV6 else IpProtocol.IPV4
        val selectedInterval = pingIntervalMs
        val config = SessionConfig(
            host = host.trim(), port = targetPort, mode = testMode,
            batchSize = targetCps, intervalMs = intervalMs, timeoutMs = timeoutMs,
            successLimit = successLimit, failureLimit = failureLimit,
            keepConnectionsAfterStop = keepConnections
        ).normalized()
        val id = ++runId
        releaseOnStop = false
        val startedEpoch = System.currentTimeMillis()
        currentStats = ProtocolStats(selectedProtocol)
        currentPingStats = PingStats(protocol = selectedProtocol)
        chartSamples = emptyList()
        logs = emptyList()
        startedAtNanos = System.nanoTime()
        if (selectedMode != AppMode.PING_STANDALONE) appendPoint(active = 0, protocol = selectedProtocol)
        isRunning = true
        val statsCallback: suspend (ProtocolStats) -> Unit = { stats ->
            withContext(Dispatchers.Main) {
                if (id == runId) {
                    if (currentStats.protocol != stats.protocol) currentPingStats = PingStats(protocol = stats.protocol)
                    currentStats = stats
                    appendPoint(active = stats.activeSessions, protocol = stats.protocol)
                }
            }
        }
        val pingCallback: suspend (PingStats) -> Unit = { stats ->
            withContext(Dispatchers.Main) {
                if (id == runId) {
                    val isNewSample = stats.isRunning && stats.sentCount > 0 && (
                        stats.sampleTimeNanos != currentPingStats.sampleTimeNanos ||
                            stats.protocol != currentPingStats.protocol
                        )
                    currentPingStats = stats
                    if (isNewSample) appendPoint(ping = stats)
                }
            }
        }
        val logCallback: suspend (LogLine) -> Unit = { line ->
            withContext(Dispatchers.Main) { if (id == runId) log(line.text, line.level) }
        }
        testJob = scope.launch {
            var outcome = "已完成"
            try {
                val staleConnections = sessionTester.release()
                if (staleConnections > 0) log("新测试前已释放 $staleConnections 条旧连接")
                if (selectedMode == AppMode.PING_STANDALONE) {
                    pingTester.runContinuousPing(
                        host = config.host, port = config.port, intervalMs = selectedInterval,
                        timeoutMs = config.timeoutMs, protocol = selectedProtocol, onStats = pingCallback, onLog = logCallback
                    )
                } else {
                    sessionTester.runSessionHoldTest(
                        rawConfig = config, onStats = statsCallback, onLog = logCallback,
                        pingIntervalMs = selectedInterval,
                        onPingStats = if (selectedMode == AppMode.UNDERLOAD_PING) pingCallback else null
                    )
                }
            } catch (cancelled: CancellationException) {
                outcome = "已停止"
                throw cancelled
            } catch (error: Exception) {
                outcome = "测试异常：${error.message ?: error.javaClass.simpleName}"
                if (!isStopping) log("测试异常：${error.message ?: error.javaClass.simpleName}", LogLevel.ERROR)
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (id == runId) {
                        if (releaseOnStop) {
                            val released = sessionTester.release()
                            log("已释放 $released 条长连接", LogLevel.WARN)
                        }
                        val finalSession = currentStats.copy(
                            activeSessions = sessionTester.activeCount(currentStats.protocol), cps = 0,
                            phase = if (releaseOnStop) "已释放" else if (outcome == "已停止") "已停止" else currentStats.phase
                        )
                        val finalPing = currentPingStats.copy(isRunning = false)
                        val finalOutcome = if (outcome == "已停止" && releaseOnStop) "已停止并释放"
                            else if (outcome != "已完成") outcome
                            else if (selectedMode == AppMode.PING_STANDALONE && finalPing.sentCount == 0) finalPing.phase
                            else if (finalSession.totalFailure >= config.failureLimit) "达到失败上限"
                            else if (finalSession.phase.contains("失败")) finalSession.phase
                            else outcome
                        try {
                            DesktopHistoryStore.append(DesktopHistoryRecord(
                                id = startedEpoch, startedAtEpochMs = startedEpoch,
                                durationMs = ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L),
                                outcome = finalOutcome, appMode = selectedMode, config = config,
                                pingIntervalMs = selectedInterval, sessionStats = finalSession,
                                pingStats = finalPing, points = chartSamples.toList(), logs = logs.toList()
                            ))
                        } catch (error: Exception) {
                            log("保存历史失败：${error.message ?: "未知错误"}", LogLevel.ERROR)
                        }
                    }
                    if (id == runId && !isStopping) {
                        currentStats = currentStats.copy(activeSessions = sessionTester.activeCount(currentStats.protocol), cps = 0)
                        currentPingStats = currentPingStats.copy(isRunning = false)
                        isRunning = false
                        testJob = null
                    }
                }
            }
        }
    }

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
            // 1. 顶栏：完全融合式现代标题栏 (4-Tab 统一分段器)
            DesktopTitleBar(
                appMode = appMode,
                isRunning = isRunning || isStopping,
                historyVisible = historyVisible,
                onHistoryChange = { historyVisible = it },
                onCheckUpdate = { updateDialogVisible = true },
                isWindows = isWindows,
                isMaximized = isMaximized,
                window = window,
                onModeChange = {
                    historyVisible = false
                    if (!isRunning && !isStopping) {
                        appMode = it
                        if (it == AppMode.PING_STANDALONE && testMode == TestMode.IPV4_THEN_IPV6) testMode = TestMode.IPV4_ONLY
                        chartSamples = emptyList()
                        currentPingStats = PingStats()
                    }
                },
                onMinimize = { windowState.isMinimized = true },
                onMaximizeToggle = {
                    windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
                },
                onClose = { localCloseRequested = true }
            )
            if (historyError != null) {
                Text(historyError.orEmpty(), fontSize = 11.sp, color = AppleRed, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
            if (historyVisible) {
                DesktopHistoryPage(
                    modifier = Modifier.fillMaxSize().weight(1f).padding(12.dp),
                    window = window,
                    onBack = { historyVisible = false }
                )
            } else {
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
                        timeoutMs = timeoutMs,
                        intervalMs = intervalMs,
                        successLimit = successLimit,
                        failureLimit = failureLimit,
                        keepConnections = keepConnections,
                        pingIntervalMs = pingIntervalMs,
                        isRunning = isRunning || isStopping,
                        isReleasing = isReleasing,
                        onHostChange = { if (!isRunning && !isStopping) host = it },
                        onPortChange = { if (!isRunning && !isStopping) port = it },
                        onTestModeChange = { if (!isRunning && !isStopping) testMode = it },
                        onCpsChange = { if (!isRunning && !isStopping) targetCps = it },
                        onTimeoutChange = { if (!isRunning && !isStopping) timeoutMs = it },
                        onIntervalChange = { if (!isRunning && !isStopping) intervalMs = it },
                        onSuccessLimitChange = { if (!isRunning && !isStopping) successLimit = it },
                        onFailureLimitChange = { if (!isRunning && !isStopping) failureLimit = it },
                        onKeepChange = { if (!isRunning && !isStopping) keepConnections = it },
                        onPingIntervalChange = { if (!isRunning && !isStopping) pingIntervalMs = it },
                        onStartStop = { if (isRunning) stopTest() else startTest() },
                        onRelease = { stopTest(release = true) }
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
        }

        // 3. 在 Windows 浮动无边框模式下提供边缘与四角平滑拖拽缩放手柄
        if (isWindows && window != null && !isMaximized) {
            WindowResizeBorders(window = window)
        }

        // 4. 版本更新与更新日志弹窗
        if (updateDialogVisible) {
            DesktopUpdateDialog(
                onDismiss = { updateDialogVisible = false }
            )
        }
    }
}

/**
 * 融合式现代标题栏 (4-Tab 统一分段器，支持无缝切换测试与历史，点击版本号检查更新)
 */
@Composable
private fun DesktopTitleBar(
    appMode: AppMode,
    isRunning: Boolean,
    historyVisible: Boolean,
    onHistoryChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
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
                .clip(RoundedCornerShape(4.dp))
                .background(AppleBlue.copy(alpha = 0.10f))
                .clickable(onClick = onCheckUpdate)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(CURRENT_APP_VERSION, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = AppleBlue)
                Box(Modifier.size(4.5.dp).background(AppleGreen, CircleShape))
            }
        }

        Spacer(Modifier.width(16.dp))

        // 统一 4-Tab 分段器 (并发压测 | 独立 Ping | 联动诊断 | 测试历史)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(WindowBg)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            DesktopTab.values().forEach { tab ->
                val selected = if (tab.isHistory) historyVisible else (!historyVisible && appMode == tab.appMode)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (selected) CardBg else Color.Transparent)
                        .border(
                            if (selected) 0.5.dp else 0.dp,
                            if (selected) Color(0x20000000) else Color.Transparent,
                            RoundedCornerShape(5.dp)
                        )
                        .clickable {
                            if (tab.isHistory) {
                                onHistoryChange(true)
                            } else {
                                onHistoryChange(false)
                                onModeChange(tab.appMode!!)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) AppleBlue else TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

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
 * Windows 现代窗口控制三键
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
 * 左侧紧凑配置面板
 */
@Composable
private fun DesktopControlCard(
    modifier: Modifier,
    appMode: AppMode,
    host: String,
    port: String,
    testMode: TestMode,
    targetCps: Int,
    timeoutMs: Int,
    intervalMs: Long,
    successLimit: Int,
    failureLimit: Int,
    keepConnections: Boolean,
    pingIntervalMs: Long,
    isRunning: Boolean,
    isReleasing: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTestModeChange: (TestMode) -> Unit,
    onCpsChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onIntervalChange: (Long) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("配置参数", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        // 快捷预设目标胶囊
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val presets = listOf(
                "百度" to "www.baidu.com",
                "腾讯" to "www.qq.com",
                "Cloudflare" to "1.1.1.1",
                "网关" to "192.168.1.1"
            )
            presets.forEach { (label, presetHost) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (host == presetHost) AppleBlue.copy(alpha = 0.12f) else InputBg)
                        .clickable(enabled = !isRunning) { onHostChange(presetHost) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 10.5.sp,
                        fontWeight = if (host == presetHost) FontWeight.Bold else FontWeight.Normal,
                        color = if (host == presetHost) AppleBlue else TextSecondary
                    )
                }
            }
        }

        // 目标地址与端口
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("目标", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.width(36.dp))
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
                modifier = Modifier.width(54.dp)
            )
        }

        // 快捷常用端口
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("常用端口", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.width(48.dp))
            listOf("80", "443", "8080", "22").forEach { p ->
                val sel = port == p
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (sel) AppleBlue.copy(alpha = 0.12f) else InputBg)
                        .clickable(enabled = !isRunning) { onPortChange(p) }
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(p, fontSize = 9.5.sp, color = if (sel) AppleBlue else TextSecondary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        // 网络协议选择
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("协议", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.width(36.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(InputBg)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TestMode.entries.filter { appMode != AppMode.PING_STANDALONE || it != TestMode.IPV4_THEN_IPV6 }.forEach { item ->
                    val selected = testMode == item
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selected) CardBg else Color.Transparent)
                            .clickable(enabled = !isRunning) { onTestModeChange(item) }
                            .padding(vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) AppleBlue else TextSecondary
                        )
                    }
                }
            }
        }

        if (appMode != AppMode.PING_STANDALONE) {
            // 发射速率 CPS
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("目标速率", fontSize = 11.sp, color = TextSecondary)
                    Text("$targetCps CPS", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AppleBlue)
                }
                Slider(
                    enabled = !isRunning,
                    value = targetCps.toFloat(),
                    onValueChange = { onCpsChange(it.toInt()) },
                    valueRange = 50f..5000f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                )
            }

            // 目标连接数与失败上限
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("目标", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.width(36.dp))
                DesktopCompactInput(
                    value = successLimit.toString(),
                    onValueChange = { onSuccessLimitChange(it.toIntOrNull() ?: 10000) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text("失败上限", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.width(4.dp))
                DesktopCompactInput(
                    value = failureLimit.toString(),
                    onValueChange = { onFailureLimitChange(it.toIntOrNull() ?: 2000) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            // 建连超时 Timeout 与发射步进 Interval
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("超时", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(36.dp))
                Row(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(InputBg).padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(500, 1000, 1500, 2000, 3000).forEach { to ->
                        val sel = timeoutMs == to
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp))
                                .background(if (sel) CardBg else Color.Transparent)
                                .clickable(enabled = !isRunning) { onTimeoutChange(to) }
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${to}ms", fontSize = 9.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) AppleBlue else TextSecondary)
                        }
                    }
                }
            }

            // 保持连接开关
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("测试完成后保持连接", fontSize = 11.5.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                Switch(enabled = !isRunning, checked = keepConnections, onCheckedChange = onKeepChange, modifier = Modifier.height(24.dp))
            }
        }

        if (appMode == AppMode.PING_STANDALONE || appMode == AppMode.UNDERLOAD_PING) {
            // Ping 探测周期设置
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ping周期", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(52.dp))
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
                                .clickable(enabled = !isRunning) { onPingIntervalChange(iv) }
                                .padding(vertical = 3.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${iv}ms",
                                fontSize = 10.sp,
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
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().height(34.dp)
            ) {
                if (isReleasing) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val angle by infiniteTransition.animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing))
                    )
                    Canvas(modifier = Modifier.size(12.dp).rotate(angle)) {
                        drawArc(color = AppleOrange, startAngle = 0f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 2f, cap = StrokeCap.Round))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("正在释放...", fontSize = 11.5.sp, color = AppleOrange, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("一键释放长连接", fontSize = 11.5.sp, color = TextSecondary)
                }
            }
        }
    }
}

/**
 * 桌面标准紧凑输入控件
 */
@Composable
internal fun DesktopCompactInput(
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
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(InputBg)
            .border(0.5.dp, InputBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 11.5.sp, color = TextSecondary.copy(alpha = 0.7f))
                }
                innerTextField()
            }
        }
    )
}

/**
 * 右侧专业数据仪表盘 (8 核心指标瓷片矩阵 + 动态紧凑双轴折线图 + 诊断建议 + 事件日志)
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
        // 顶部核心数据瓷片 (8 块紧凑胶囊网格)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (appMode) {
                    AppMode.SESSION_HOLD -> {
                        CompactMetricCard(Modifier.weight(1f), "活跃并发会话", currentStats.activeSessions.toString(), AppleBlue)
                        CompactMetricCard(Modifier.weight(1f), "瞬时新建速率", "${currentStats.cps}/s", ApplePurple)
                        CompactMetricCard(Modifier.weight(1f), "成功会话计数", currentStats.totalSuccess.toString(), AppleGreen)
                        CompactMetricCard(Modifier.weight(1f), "建连失败计数", currentStats.totalFailure.toString(), if (currentStats.totalFailure > 0) AppleRed else TextSecondary)
                    }
                    AppMode.PING_STANDALONE -> {
                        CompactMetricCard(Modifier.weight(1f), "TCP 握手延迟", formatLatency(currentPingStats.currentLatencyMs), AppleOrange)
                        CompactMetricCard(Modifier.weight(1f), "平均握手耗时", if (currentPingStats.receivedCount > 0) "${currentPingStats.avgLatencyMs} ms" else "—", AppleBlue)
                        CompactMetricCard(Modifier.weight(1f), "最小 / 最大 RTT", if (currentPingStats.receivedCount > 0) "${currentPingStats.minLatencyMs}/${currentPingStats.maxLatencyMs} ms" else "—", AppleCyan)
                        CompactMetricCard(Modifier.weight(1f), "网络抖动 (Jitter)", if (currentPingStats.receivedCount > 1) "${currentPingStats.jitterMs} ms" else "—", ApplePurple)
                    }
                    AppMode.UNDERLOAD_PING -> {
                        CompactMetricCard(Modifier.weight(1f), "活跃并发会话", currentStats.activeSessions.toString(), AppleBlue)
                        CompactMetricCard(Modifier.weight(1f), "瞬时新建速率", "${currentStats.cps}/s", ApplePurple)
                        CompactMetricCard(Modifier.weight(1f), "TCP 握手延迟", formatLatency(currentPingStats.currentLatencyMs), AppleOrange)
                        CompactMetricCard(Modifier.weight(1f), "平均握手耗时", if (currentPingStats.receivedCount > 0) "${currentPingStats.avgLatencyMs} ms" else "—", AppleCyan)
                    }
                }
            }

            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (appMode) {
                    AppMode.SESSION_HOLD -> {
                        CompactMetricCard(Modifier.weight(1f), "历史峰值承载", currentStats.maxStableSessions.toString(), AppleBlue)
                        CompactMetricCard(Modifier.weight(1f), "累计建连尝试", currentStats.totalAttempts.toString(), TextPrimary)
                        CompactMetricCard(Modifier.weight(1f), "平均握手耗时", if (currentStats.averageConnectLatencyMs > 0) "${currentStats.averageConnectLatencyMs} ms" else "—", AppleOrange)
                        CompactMetricCard(Modifier.weight(1f), "当前运行状态", currentStats.phase, AppleGreen)
                    }
                    AppMode.PING_STANDALONE -> {
                        CompactMetricCard(Modifier.weight(1f), "探测失败率", if (currentPingStats.sentCount > 0) String.format("%.1f%%", currentPingStats.lossPercent) else "—", if (currentPingStats.lossPercent > 0) AppleRed else AppleGreen)
                        CompactMetricCard(Modifier.weight(1f), "已发送 / 成功", "${currentPingStats.sentCount} / ${currentPingStats.receivedCount}", TextPrimary)
                        CompactMetricCard(Modifier.weight(1f), "丢包/超时计数", currentPingStats.lostCount.toString(), if (currentPingStats.lostCount > 0) AppleRed else TextSecondary)
                        CompactMetricCard(Modifier.weight(1f), "探针链路状态", currentPingStats.phase, AppleGreen)
                    }
                    AppMode.UNDERLOAD_PING -> {
                        CompactMetricCard(Modifier.weight(1f), "网络抖动 (Jitter)", if (currentPingStats.receivedCount > 1) "${currentPingStats.jitterMs} ms" else "—", ApplePurple)
                        CompactMetricCard(Modifier.weight(1f), "探测失败率", if (currentPingStats.sentCount > 0) String.format("%.1f%%", currentPingStats.lossPercent) else "—", if (currentPingStats.lossPercent > 0) AppleRed else AppleGreen)
                        CompactMetricCard(Modifier.weight(1f), "峰值承载记录", currentStats.maxStableSessions.toString(), AppleBlue)
                        CompactMetricCard(Modifier.weight(1f), "测试总体状态", if (currentPingStats.isRunning) "联动测试中" else currentStats.phase, AppleGreen)
                    }
                }
            }
        }

        // 双轴走势图卡片 (带紧凑自适应量程、悬浮跟随气泡、防崩溃安全视口)
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
            // 诊断建议
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
                            DesktopAdviceRow("1", "目标 CPS 表示每秒尝试建连数，实际成功速率取决于目标和网络。")
                            DesktopAdviceRow("2", "若出现大量超时或拒绝，请排查目标服务防火墙与端口限制。")
                            DesktopAdviceRow("3", "观察路由器 CPU 与 NAT 表，评估路由器硬件并发极限。")
                        }
                        AppMode.PING_STANDALONE -> {
                            DesktopAdviceRow("1", "当前为 TCP 建连探测，延迟不包含 DNS 解析，与 ICMP Ping 口径不同。")
                            DesktopAdviceRow("2", "探测失败包括超时、拒绝和路由错误，不能直接等同于 ICMP 丢包。")
                            DesktopAdviceRow("3", "曲线缺口表示失败探测，底部红标表示失败事件。")
                        }
                        AppMode.UNDERLOAD_PING -> {
                            DesktopAdviceRow("1", "观察建连压力下的延迟变化；若公网目标开启防 SYN 洪水/CC 会主动拦截客户端。")
                            DesktopAdviceRow("2", "高并发压测建议针对局域网网关或具备授权的压测服务器。")
                            DesktopAdviceRow("3", "Ping 独立采样通道；对端丢包保持缺口，协议切换时曲线分段。")
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
internal fun CompactMetricCard(modifier: Modifier, label: String, value: String, color: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 9.dp)
    ) {
        Column {
            Text(label, fontSize = 9.5.sp, color = TextSecondary, maxLines = 1)
            Spacer(Modifier.height(1.dp))
            Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

/**
 * 紧凑自适应量程计算
 */
fun calculateSmartMaxSessions(actualMax: Int): Int {
    if (actualMax <= 0) return 10
    val raw = (actualMax * 1.15).toInt()
    return when {
        raw <= 20 -> ((raw + 4) / 5) * 5
        raw <= 100 -> ((raw + 9) / 10) * 10
        raw <= 500 -> ((raw + 49) / 50) * 50
        raw <= 2000 -> ((raw + 99) / 100) * 100
        raw <= 10000 -> ((raw + 499) / 500) * 500
        else -> ((raw + 999) / 1000) * 1000
    }
}

fun calculateSmartMaxLatency(actualMax: Int): Int {
    if (actualMax <= 0) return 20
    val raw = (actualMax * 1.20).toInt()
    return when {
        raw <= 20 -> 20
        raw <= 50 -> ((raw + 4) / 5) * 5
        raw <= 100 -> ((raw + 9) / 10) * 10
        raw <= 300 -> ((raw + 19) / 20) * 20
        raw <= 1000 -> ((raw + 49) / 50) * 50
        else -> ((raw + 99) / 100) * 100
    }
}

/**
 * 专业双轴走势图卡片 (带紧凑自适应动态量程、鼠标实时跟随气泡、工具栏、滚轮缩放、拖拽平移与均值基准线)
 */
@Composable
internal fun DesktopDualChartCard(
    samples: List<DualChartPoint>,
    appMode: AppMode,
    currentStats: ProtocolStats,
    currentPingStats: PingStats,
    successLimit: Int,
    modifier: Modifier = Modifier
) {
    var hoveredPoint by remember(appMode) { mutableStateOf<DualChartPoint?>(null) }
    var pointerOffset by remember(appMode) { mutableStateOf<Offset?>(null) }
    LaunchedEffect(samples) {
        if (hoveredPoint !in samples) {
            hoveredPoint = null
            pointerOffset = null
        }
    }

    var zoomScale by remember { mutableStateOf(1.0f) }
    var panOffsetX by remember { mutableStateOf(0.0f) }

    val rawMaxSec = samples.maxOfOrNull { it.elapsedSec }?.coerceAtLeast(5.0) ?: 5.0
    val rawMinSec = samples.firstOrNull()?.elapsedSec ?: 0.0

    // 计算紧凑动态量程 (拒绝死板直线)
    val actualMaxSessions = samples.mapNotNull { it.activeSessions }.maxOrNull() ?: if (appMode != AppMode.PING_STANDALONE) currentStats.activeSessions else 0
    val maxSessions = calculateSmartMaxSessions(actualMaxSessions)

    val actualMaxLatency = samples.mapNotNull { it.pingLatencyMs }.maxOrNull() ?: if (currentPingStats.receivedCount > 0) currentPingStats.maxLatencyMs else 0
    val maxLatency = calculateSmartMaxLatency(actualMaxLatency)

    // 防崩溃的安全时间视口算法
    val totalSpan = max(0.001, rawMaxSec - rawMinSec)
    val windowSpan = (totalSpan / zoomScale.toDouble()).coerceIn(0.001, totalSpan)
    val maxPanSec = max(0.0, totalSpan - windowSpan)
    val viewMinSec = rawMinSec + (panOffsetX.toDouble() * totalSpan).coerceIn(0.0, maxPanSec)
    val viewMaxSec = viewMinSec + windowSpan

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部走势图标题与工具栏
            Row(
                modifier = Modifier.fillMaxWidth().height(26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (appMode) {
                        AppMode.SESSION_HOLD -> "并发会话承载走势"
                        AppMode.PING_STANDALONE -> "TCP 建连延迟走势"
                        AppMode.UNDERLOAD_PING -> "会话数与 TCP 建连延迟对照"
                    },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(Modifier.width(12.dp))

                // 图表缩放与总览交互工具条
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(InputBg)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 全景总览 Fit All
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (zoomScale == 1.0f) AppleBlue.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { zoomScale = 1.0f; panOffsetX = 0f }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("全景", fontSize = 9.5.sp, fontWeight = if (zoomScale == 1.0f) FontWeight.Bold else FontWeight.Normal, color = if (zoomScale == 1.0f) AppleBlue else TextSecondary)
                    }
                    // 放大 +
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { zoomScale = (zoomScale * 1.3f).coerceAtMost(10f) }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("+", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    // 缩小 -
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                zoomScale = (zoomScale / 1.3f).coerceAtLeast(1.0f)
                                if (zoomScale == 1.0f) panOffsetX = 0f
                            }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("-", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    // 倍率显示
                    if (zoomScale > 1.0f) {
                        Text("${String.format("%.1f", zoomScale)}x", fontSize = 9.sp, color = AppleBlue, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }

                Spacer(Modifier.weight(1f))

                // 常态图例指示
                if (appMode == AppMode.UNDERLOAD_PING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(AppleBlue, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text("并发数", fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(8.dp).background(AppleOrange, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text("延迟", fontSize = 10.sp, color = TextSecondary)
                    }
                } else if (appMode == AppMode.SESSION_HOLD) {
                    Text("峰值: ${currentStats.maxStableSessions}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppleBlue)
                } else {
                    Text("均值: ${if (currentPingStats.receivedCount > 0) formatLatency(currentPingStats.avgLatencyMs) else "—"}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppleOrange)
                }
            }

            Spacer(Modifier.height(4.dp))

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

                // 核心走势图画布与精准鼠标位置跟随浮动卡片
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val boxWidthPx = constraints.maxWidth.toFloat()
                    val boxHeightPx = constraints.maxHeight.toFloat()
                    val density = LocalDensity.current

                    DesktopDualChartCanvas(
                        samples = samples,
                        appMode = appMode,
                        rawMinSec = rawMinSec,
                        rawMaxSec = rawMaxSec,
                        viewMinSec = viewMinSec,
                        viewMaxSec = viewMaxSec,
                        maxSessions = maxSessions,
                        maxLatency = maxLatency,
                        zoomScale = zoomScale,
                        panOffsetX = panOffsetX,
                        onZoomChange = { zoomScale = it },
                        onPanChange = { panOffsetX = it },
                        avgLatencyMs = if (currentPingStats.receivedCount > 0) currentPingStats.avgLatencyMs else null,
                        hoveredPoint = hoveredPoint,
                        onHoverChange = { hoveredPoint = it },
                        onPointerPositionChange = { pointerOffset = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 鼠标悬停精准跟随浮动指示卡片 (紧贴鼠标指针跟随，智能防溢出)
                    if (hoveredPoint != null && pointerOffset != null) {
                        val hp = hoveredPoint!!
                        val pOffset = pointerOffset!!

                        val cardEstimatedWidthPx = with(density) { 190.dp.toPx() }
                        val cardEstimatedHeightPx = with(density) { 34.dp.toPx() }

                        // X 轴跟随：右侧不够时翻转到左侧
                        val tooltipX = if (pOffset.x + cardEstimatedWidthPx + 16f > boxWidthPx) {
                            (pOffset.x - cardEstimatedWidthPx - 12f).coerceAtLeast(4f)
                        } else {
                            (pOffset.x + 14f).coerceAtLeast(4f)
                        }

                        // Y 轴跟随：上方不够时翻转到下方
                        val tooltipY = if (pOffset.y - cardEstimatedHeightPx - 10f < 4f) {
                            (pOffset.y + 16f).coerceAtMost(boxHeightPx - cardEstimatedHeightPx - 4f)
                        } else {
                            (pOffset.y - cardEstimatedHeightPx - 8f).coerceAtLeast(4f)
                        }

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(tooltipX.toInt(), tooltipY.toInt()) }
                                .clip(RoundedCornerShape(7.dp))
                                .background(CardBg.copy(alpha = 0.96f))
                                .border(0.5.dp, AppleBlue.copy(alpha = 0.40f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 8.dp, vertical = 4.5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text("⏱ ${String.format("%.2f", hp.elapsedSec)}s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                if (appMode != AppMode.PING_STANDALONE) {
                                    Text("● 会话: ${hp.activeSessions?.let { formatCompactNumber(it) } ?: "—"}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppleBlue)
                                }
                                if (appMode != AppMode.SESSION_HOLD) {
                                    Text("● 延迟: ${if (hp.hasPingSample) formatLatency(hp.pingLatencyMs) else "—"}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppleOrange)
                                }
                            }
                        }
                    }
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
                    val sec = viewMinSec + (viewMaxSec - viewMinSec) * i / 4
                    Text(
                        text = "${String.format("%.1f", sec)}s",
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
    rawMinSec: Double,
    rawMaxSec: Double,
    viewMinSec: Double,
    viewMaxSec: Double,
    maxSessions: Int,
    maxLatency: Int,
    zoomScale: Float,
    panOffsetX: Float,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    avgLatencyMs: Int?,
    hoveredPoint: DualChartPoint?,
    onHoverChange: (DualChartPoint?) -> Unit,
    onPointerPositionChange: (Offset?) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(samples, zoomScale, panOffsetX, rawMinSec, rawMaxSec) {
                detectTapGestures(
                    onDoubleTap = {
                        onZoomChange(1.0f)
                        onPanChange(0.0f)
                    }
                )
            }
            .pointerInput(samples, zoomScale, panOffsetX, rawMinSec, rawMaxSec) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (zoomScale > 1.0f) {
                        val deltaRatio = -dragAmount.x / size.width.toFloat()
                        val newOffset = (panOffsetX + deltaRatio / zoomScale).coerceIn(0f, 1f - 1f / zoomScale)
                        onPanChange(newOffset)
                    }
                }
            }
            .pointerInput(samples, viewMinSec, viewMaxSec) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move -> {
                                val pos = event.changes.firstOrNull()?.position
                                onPointerPositionChange(pos)
                                if (pos != null && samples.isNotEmpty()) {
                                    val ratio = (pos.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val secTarget = viewMinSec + ratio * (viewMaxSec - viewMinSec)
                                    val closest = samples.minByOrNull { abs(it.elapsedSec - secTarget) }
                                    onHoverChange(closest)
                                }
                            }
                            PointerEventType.Scroll -> {
                                val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (scrollDelta != 0f) {
                                    val factor = if (scrollDelta < 0) 1.15f else 0.87f
                                    val newZoom = (zoomScale * factor).coerceIn(1.0f, 10.0f)
                                    onZoomChange(newZoom)
                                    if (newZoom == 1.0f) onPanChange(0f)
                                }
                            }
                            PointerEventType.Exit -> {
                                onPointerPositionChange(null)
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

        // 绘制延迟平均值基准虚线 (Average Latency Baseline)
        if (appMode != AppMode.SESSION_HOLD && avgLatencyMs != null && avgLatencyMs > 0 && maxLatency > 0) {
            val avgY = h - (avgLatencyMs.toFloat() / maxLatency.toFloat()) * h
            if (avgY in 0f..h) {
                drawLine(
                    color = AppleOrange.copy(alpha = 0.35f),
                    start = Offset(0f, avgY),
                    end = Offset(w, avgY),
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }
        }

        if (samples.isEmpty()) return@Canvas

        fun xOf(sec: Double): Float = (((sec - viewMinSec) / (viewMaxSec - viewMinSec).coerceAtLeast(0.001)).toFloat() * w)
        fun ySession(sessions: Int): Float = h - (sessions.toFloat() / maxSessions.toFloat()) * h
        fun yPing(latency: Int): Float = h - (latency.toFloat() / maxLatency.toFloat()) * h

        fun drawSeries(points: List<DualChartPoint>, color: Color, valueOf: (DualChartPoint) -> Int?, yOf: (Int) -> Float) {
            var previous: DualChartPoint? = null
            for (point in points) {
                val value = valueOf(point)
                if (value == null) {
                    previous = null
                    continue
                }
                val x = xOf(point.elapsedSec)
                val y = yOf(value)
                val before = previous
                val beforeValue = before?.let(valueOf)
                if (before != null && beforeValue != null && before.protocol == point.protocol) {
                    val from = Offset(xOf(before.elapsedSec), yOf(beforeValue))
                    val fill = Path().apply {
                        moveTo(from.x, h)
                        lineTo(from.x, from.y)
                        lineTo(x, y)
                        lineTo(x, h)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.14f), Color.Transparent)))
                    drawLine(color, from, Offset(x, y), strokeWidth = 2.2f, cap = StrokeCap.Round)
                } else {
                    if (x in -5f..(w + 5f)) {
                        drawCircle(color, radius = 2.2f, center = Offset(x, y))
                    }
                }
                previous = point
            }
        }

        if (appMode != AppMode.PING_STANDALONE) {
            drawSeries(samples.filter { it.activeSessions != null }, AppleBlue, { it.activeSessions }, ::ySession)
        }
        if (appMode != AppMode.SESSION_HOLD) {
            val probes = samples.filter { it.hasPingSample }
            drawSeries(probes, AppleOrange, { it.pingLatencyMs }, ::yPing)
            probes.filter { it.pingLatencyMs == null }.forEach {
                val x = xOf(it.elapsedSec)
                if (x in 0f..w) {
                    drawLine(AppleRed, Offset(x, h - 6f), Offset(x, h), strokeWidth = 2f)
                }
            }
        }

        // 鼠标悬浮拾取交互：发丝对齐线与高亮节点圆环
        if (hoveredPoint != null) {
            val hx = xOf(hoveredPoint.elapsedSec)
            if (hx in 0f..w) {
                drawLine(
                    color = Color(0x60007AFF),
                    start = Offset(hx, 0f),
                    end = Offset(hx, h),
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
                )

                // 会话数节点高亮环
                if (appMode != AppMode.PING_STANDALONE && hoveredPoint.activeSessions != null) {
                    val sy = ySession(hoveredPoint.activeSessions)
                    drawCircle(color = AppleBlue.copy(alpha = 0.25f), radius = 6.5f, center = Offset(hx, sy))
                    drawCircle(color = Color.White, radius = 4f, center = Offset(hx, sy))
                    drawCircle(color = AppleBlue, radius = 2.5f, center = Offset(hx, sy))
                }

                // 延迟节点高亮环
                if (appMode != AppMode.SESSION_HOLD && hoveredPoint.hasPingSample && hoveredPoint.pingLatencyMs != null) {
                    val py = yPing(hoveredPoint.pingLatencyMs)
                    drawCircle(color = AppleOrange.copy(alpha = 0.25f), radius = 6.5f, center = Offset(hx, py))
                    drawCircle(color = Color.White, radius = 4f, center = Offset(hx, py))
                    drawCircle(color = AppleOrange, radius = 2.5f, center = Offset(hx, py))
                }
            }
        }
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
            lineHeight = 17.sp,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.2.sp,
            color = TextPrimary.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun DesktopLogItem(line: LogLine) {
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

private fun formatLatency(value: Int?): String = value?.let { "${it} ms" } ?: "—"
