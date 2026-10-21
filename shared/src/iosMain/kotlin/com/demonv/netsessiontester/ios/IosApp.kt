@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.demonv.netsessiontester.ios

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * iOS 导出入口：提供原生 UIViewController 挂载 Compose Multiplatform
 */
fun createMainViewController(): UIViewController = ComposeUIViewController {
    IosApp()
}

// ==========================================
// Apple HIG 语义色彩 Token
// ==========================================
private val AppleBlueLight = Color(0xFF007AFF)
private val AppleBlueDark = Color(0xFF0A84FF)
private val AppleGreenLight = Color(0xFF34C759)
private val AppleGreenDark = Color(0xFF30D158)
private val AppleOrangeLight = Color(0xFFFF9500)
private val AppleOrangeDark = Color(0xFFFF9F0A)
private val AppleRedLight = Color(0xFFFF3B30)
private val AppleRedDark = Color(0xFFFF453A)

private val IosBgLight = Color(0xFFF2F2F7)
private val IosBgDark = Color(0xFF000000)
private val IosCardLight = Color(0xF5FFFFFF)
private val IosCardDark = Color(0xE01C1C1E)

private val IosTextPrimaryLight = Color(0xFF000000)
private val IosTextPrimaryDark = Color(0xFFFFFFFF)
private val IosTextSecondaryLight = Color(0x993C3C43)
private val IosTextSecondaryDark = Color(0x99EBEBF5)

/**
 * NetSessionTester iOS 原生全功能界面
 */
@Composable
fun IosApp() {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) IosBgDark else IosBgLight
    val cardBg = if (isDark) IosCardDark else IosCardLight
    val textPrimary = if (isDark) IosTextPrimaryDark else IosTextPrimaryLight
    val textSecondary = if (isDark) IosTextSecondaryDark else IosTextSecondaryLight
    val appleBlue = if (isDark) AppleBlueDark else AppleBlueLight
    val appleGreen = if (isDark) AppleGreenDark else AppleGreenLight
    val appleOrange = if (isDark) AppleOrangeDark else AppleOrangeLight
    val appleRed = if (isDark) AppleRedDark else AppleRedLight

    val scope = rememberCoroutineScope()
    val cleanupScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val engine = remember { IosTcpEngine() }

    // 运行状态
    var appMode by remember { mutableStateOf(IosAppMode.SESSION_HOLD) }
    var isRunning by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf(IosPreferences.loadConfig()) }
    var protocolStats by remember { mutableStateOf(IosProtocolStats()) }
    var pingStats by remember { mutableStateOf(IosPingStats()) }
    var heldSocketsCount by remember { mutableStateOf(0) }

    // 双轴走势历史采样
    var chartSamples by remember { mutableStateOf(listOf<IosDualChartPoint>()) }
    var activeTestJob by remember { mutableStateOf<Job?>(null) }
    var startedAtMs by remember { mutableStateOf(0L) }
    var runGeneration by remember { mutableStateOf(0) }

    // 实时日志流
    var logs by remember { mutableStateOf(listOf<IosLogLine>()) }
    var logFilter by remember { mutableStateOf<IosLogLevel?>(null) }
    val logListState = rememberLazyListState()

    fun appendLog(line: IosLogLine) {
        val timestamped = if (line.timeEpochMs == 0L) line.copy(timeEpochMs = getEpochMs()) else line
        logs = (logs + timestamped).takeLast(600)
        scope.launch {
            if (logs.isNotEmpty()) {
                logListState.animateScrollToItem(logs.size - 1)
            }
        }
    }

    val preferencesRevision by IosPreferences.revision.collectAsState()
    LaunchedEffect(preferencesRevision) {
        if (!isRunning) config = IosPreferences.loadConfig()
    }
    LaunchedEffect(isRunning) {
        if (!isRunning) while (isActive) {
            heldSocketsCount = engine.getHeldCount()
            protocolStats = protocolStats.copy(activeSessions = heldSocketsCount, cps = 0)
            delay(500L)
        }
    }

    fun appendPoint(active: Int? = null, ping: IosPingStats? = null, protocol: IosIpProtocol = protocolStats.protocol) {
        val point = IosDualChartPoint(
            elapsedMs = (getMonotonicMs() - startedAtMs).coerceAtLeast(0L),
            activeSessions = active, pingLatencyMs = ping?.currentLatencyMs,
            hasPingSample = ping != null, protocol = ping?.protocol ?: protocol,
            cps = protocolStats.cps
        )
        chartSamples = (chartSamples + point).takeLast(2400)
    }

    fun startTest(mode: IosAppMode) {
        if (isRunning || activeTestJob?.isActive == true) return
        if (config.host.isBlank() || config.port !in 1..65535) {
            appendLog(IosLogLine(level = IosLogLevel.ERROR, text = "请输入有效目标与端口"))
            return
        }
        val runId = ++runGeneration
        val runConfig = config.normalized()
        val protocol = if (runConfig.mode == IosTestMode.IPV6_ONLY) IosIpProtocol.IPV6 else IosIpProtocol.IPV4
        isRunning = true
        protocolStats = IosProtocolStats(protocol = protocol)
        pingStats = IosPingStats(host = runConfig.host, port = runConfig.port, protocol = protocol)
        chartSamples = emptyList()
        logs = emptyList()
        heldSocketsCount = 0
        startedAtMs = getMonotonicMs()
        if (mode != IosAppMode.PING_STANDALONE) appendPoint(active = 0, protocol = protocol)
        activeTestJob = scope.launch {
            var completion = "测试完成"
            val onPing: suspend (IosPingStats) -> Unit = { stats ->
                withContext(Dispatchers.Main) {
                    if (runGeneration == runId) {
                        val isNew = stats.sentCount > 0 &&
                            (stats.sentCount != pingStats.sentCount || stats.protocol != pingStats.protocol)
                        pingStats = stats
                        if (isNew) appendPoint(ping = stats)
                    }
                }
            }
            val onLog: suspend (IosLogLine) -> Unit = { line ->
                withContext(Dispatchers.Main) { if (runGeneration == runId) appendLog(line) }
            }
            try {
                IosTaskRegistry.awaitIdle()
                // Starting an independent probe must not inherit load from a retained earlier test.
                engine.releaseHeldSockets()
                if (mode == IosAppMode.PING_STANDALONE) {
                    engine.runStandalonePing(runConfig, onPing, onLog)
                } else {
                    engine.runSessionHoldTest(
                        rawConfig = runConfig,
                        onStats = { stats ->
                            withContext(Dispatchers.Main) {
                                if (runGeneration == runId) {
                                    if (protocolStats.protocol != stats.protocol) pingStats = IosPingStats(protocol = stats.protocol)
                                    protocolStats = stats
                                    heldSocketsCount = stats.activeSessions
                                    appendPoint(active = stats.activeSessions, protocol = stats.protocol)
                                }
                            }
                        },
                        onLog = onLog,
                        onPingStats = if (mode == IosAppMode.UNDERLOAD_PING) onPing else null
                    )
                }
            } catch (cancelled: CancellationException) {
                completion = "已停止"
                throw cancelled
            } catch (error: Exception) {
                completion = "测试失败：${error.message ?: "未知错误"}"
                appendLog(IosLogLine(level = IosLogLevel.ERROR, text = completion))
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (runGeneration == runId) {
                        heldSocketsCount = engine.getHeldCount()
                        protocolStats = protocolStats.copy(activeSessions = heldSocketsCount, cps = 0)
                        pingStats = pingStats.copy(isRunning = false)
                        val result = if (mode == IosAppMode.PING_STANDALONE)
                            "$completion · 探测 ${pingStats.sentCount} 次，失败 ${pingStats.lostCount} 次"
                            else "$completion · 成功 ${protocolStats.totalSuccess}，失败 ${protocolStats.totalFailure}，现存 $heldSocketsCount"
                        val rawPoints = chartSamples.joinToString("\n") {
                            "${it.elapsedMs},${it.protocol.label},${it.activeSessions ?: ""},${if (it.hasPingSample) it.pingLatencyMs?.toString() ?: "failed" else ""}"
                        }
                        IosHistoryStore.append(mode.label, "${runConfig.host}:${runConfig.port}", result,
                            logs.joinToString("\n") { "${it.timeText} ${it.level} ${it.text}" } +
                                "\n\nelapsed_ms,protocol,active,tcp_latency_ms\n" + rawPoints)
                        isRunning = false
                        activeTestJob = null
                    }
                }
            }
        }
    }

    fun stopAnyTest(reason: String = "用户主动停止测试") {
        if (!isRunning && activeTestJob == null) return
        engine.stopTest()
        activeTestJob?.cancel()
        appendLog(IosLogLine(level = IosLogLevel.WARN, text = reason))
    }

    DisposableEffect(engine) {
        IosTaskRegistry.register("main") {
            val previous = activeTestJob
            if (previous == null && heldSocketsCount == 0) return@register null
            stopAnyTest("页面切换或进入后台，停止测试")
            cleanupScope.launch {
                previous?.join()
                engine.releaseHeldSockets()
                withContext(Dispatchers.Main) {
                    heldSocketsCount = 0
                    protocolStats = protocolStats.copy(activeSessions = 0, cps = 0)
                }
            }
        }
        onDispose {
            IosTaskRegistry.unregister("main")
            val previous = activeTestJob
            engine.stopTest()
            engine.setScreenKeepAwake(false)
            cleanupScope.launch {
                previous?.cancelAndJoin()
                engine.releaseHeldSockets()
                cleanupScope.cancel()
            }
        }
    }

    fun releaseHeldConnections() {
        val previous = activeTestJob
        stopAnyTest("释放连接，停止新增任务")
        scope.launch {
            previous?.join()
            val count = engine.releaseHeldSockets()
            heldSocketsCount = 0
            protocolStats = protocolStats.copy(activeSessions = 0, cps = 0, phase = "已全部释放")
            appendLog(IosLogLine(level = IosLogLevel.WARN, text = "已释放 $count 个持链连接"))
        }
    }

    // 基于已完成样本的网络诊断说明
    val diagnosticAdvice = remember(protocolStats, pingStats, appMode) {
        generateDiagnosticAdvice(appMode, protocolStats, pingStats)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(10.dp))

            // 1. 顶部 iOS 大标题导航与运行状态胶囊
            IosHeaderBar(
                isRunning = isRunning,
                heldCount = heldSocketsCount,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                appleBlue = appleBlue,
                appleGreen = appleGreen
            )

            Spacer(Modifier.height(14.dp))

            // 2. iOS HIG 分段选择器 (Segmented Control)
            IosSegmentedControl(
                selectedMode = appMode,
                enabled = !isRunning,
                cardBg = cardBg,
                appleBlue = appleBlue,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onSelectMode = { newMode ->
                    appMode = newMode
                    engine.triggerHapticFeedback(false)
                }
            )

            Spacer(Modifier.height(14.dp))

            // 3. 四项核心指标大数字卡片 (MetricMega Cards)
            IosMetricCardsGrid(
                appMode = appMode,
                protocolStats = protocolStats,
                pingStats = pingStats,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                appleBlue = appleBlue,
                appleOrange = appleOrange,
                appleGreen = appleGreen,
                appleRed = appleRed
            )

            Spacer(Modifier.height(16.dp))

            // 4. 专业双轴走势折线图 (带触摸交互发丝探针与气泡卡片)
            IosDualAxisChartCard(
                appMode = appMode,
                samples = chartSamples,
                successLimit = config.successLimit,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                appleBlue = appleBlue,
                appleOrange = appleOrange
            )

            Spacer(Modifier.height(16.dp))

            // 5. 目标参数与快速预设配置面板
            IosConfigPanel(
                config = config,
                enabled = !isRunning,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                appleBlue = appleBlue,
                onConfigChange = { config = it }
            )

            Spacer(Modifier.height(16.dp))

            // 6. 主操作控制按钮条
            IosActionControls(
                isRunning = isRunning,
                appMode = appMode,
                heldCount = heldSocketsCount,
                appleBlue = appleBlue,
                appleRed = appleRed,
                appleOrange = appleOrange,
                onStart = {
                    when (appMode) {
                        IosAppMode.SESSION_HOLD -> startTest(IosAppMode.SESSION_HOLD)
                        IosAppMode.PING_STANDALONE -> startTest(IosAppMode.PING_STANDALONE)
                        IosAppMode.UNDERLOAD_PING -> startTest(IosAppMode.UNDERLOAD_PING)
                    }
                },
                onStop = { stopAnyTest() },
                onRelease = { releaseHeldConnections() }
            )

            Spacer(Modifier.height(16.dp))

            // 7. 智能 Bufferbloat 与网络专家诊断建议
            IosDiagnosticAdviceCard(
                advice = diagnosticAdvice,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                appleBlue = appleBlue,
                appleGreen = appleGreen,
                appleOrange = appleOrange,
                appleRed = appleRed
            )

            Spacer(Modifier.height(16.dp))

            // 8. 实时诊断日志终端流
            IosLogConsoleCard(
                logs = logs,
                filter = logFilter,
                listState = logListState,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                appleBlue = appleBlue,
                appleGreen = appleGreen,
                appleOrange = appleOrange,
                appleRed = appleRed,
                onFilterChange = { logFilter = it },
                onClearLogs = { logs = emptyList() }
            )

            Spacer(Modifier.height(36.dp))
        }
    }
}

// ==========================================
// 1. 顶部 iOS 大标题导航与状态栏
// ==========================================
@Composable
private fun IosHeaderBar(
    isRunning: Boolean,
    heldCount: Int,
    textPrimary: Color,
    textSecondary: Color,
    appleBlue: Color,
    appleGreen: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NetSessionTester",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(appleBlue.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "iOS v1.0.22-beta1",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appleBlue
                    )
                }
            }
            Text(
                text = "连接数测试 · TCP Ping · 负载延迟对照",
                fontSize = 12.sp,
                color = textSecondary
            )
        }

        // 运行状态指示胶囊
        Box(
            modifier = Modifier
                .background(
                    if (isRunning) appleGreen.copy(alpha = 0.12f) else textSecondary.copy(alpha = 0.10f),
                    RoundedCornerShape(999.dp)
                )
                .border(
                    0.5.dp,
                    if (isRunning) appleGreen.copy(alpha = 0.35f) else Color.Transparent,
                    RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isRunning) appleGreen else textSecondary, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "压测进行中" else if (heldCount > 0) "持链 $heldCount" else "待命就绪",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isRunning) appleGreen else textSecondary
                )
            }
        }
    }
}

// ==========================================
// 2. iOS HIG 分段选择器
// ==========================================
@Composable
private fun IosSegmentedControl(
    selectedMode: IosAppMode,
    enabled: Boolean,
    cardBg: Color,
    appleBlue: Color,
    textPrimary: Color,
    textSecondary: Color,
    onSelectMode: (IosAppMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(0.5.dp, Color(0x18000000), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IosAppMode.entries.forEach { mode ->
            val selected = (mode == selectedMode)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) appleBlue else Color.Transparent)
                    .clickable(enabled = enabled) { onSelectMode(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.label,
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==========================================
// 3. 四项核心指标大数字卡片矩阵
// ==========================================
@Composable
private fun IosMetricCardsGrid(
    appMode: IosAppMode,
    protocolStats: IosProtocolStats,
    pingStats: IosPingStats,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    appleBlue: Color,
    appleOrange: Color,
    appleGreen: Color,
    appleRed: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: 活跃会话数
            IosSingleMetricCard(
                modifier = Modifier.weight(1f),
                title = "活跃并发会话",
                value = "${protocolStats.activeSessions}",
                valueColor = appleBlue,
                subText = "峰值: ${protocolStats.maxStableSessions} | 成功: ${protocolStats.totalSuccess}",
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            // Card 2: 实时握手延迟
            val latText = if (appMode == IosAppMode.PING_STANDALONE || appMode == IosAppMode.UNDERLOAD_PING) {
                pingStats.currentLatencyMs?.let { "${it}ms" } ?: "--"
            } else {
                if (protocolStats.averageConnectLatencyMs > 0) "${protocolStats.averageConnectLatencyMs}ms" else "--"
            }

            val latSub = if (appMode == IosAppMode.PING_STANDALONE || appMode == IosAppMode.UNDERLOAD_PING) {
                "均值: ${pingStats.avgLatencyMs}ms | 抖动: ${pingStats.jitterMs}ms"
            } else {
                "TCP 均握手耗时"
            }

            IosSingleMetricCard(
                modifier = Modifier.weight(1f),
                title = "实时握手延迟",
                value = latText,
                valueColor = appleOrange,
                subText = latSub,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: 瞬时新建速率 (CPS)
            IosSingleMetricCard(
                modifier = Modifier.weight(1f),
                title = "握手速率 (CPS)",
                value = "${protocolStats.cps}",
                valueColor = appleGreen,
                subText = "建连速度 / 秒",
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            // Card 4: 丢包与异常
            val showsPingLoss = appMode == IosAppMode.PING_STANDALONE || appMode == IosAppMode.UNDERLOAD_PING
            val lossVal = if (showsPingLoss) {
                "${pingStats.lossPercent.roundToInt()}%"
            } else {
                "${protocolStats.totalFailure}"
            }

            val lossSub = if (showsPingLoss) {
                "${pingStats.protocol.label} · 丢失 ${pingStats.lostCount} / 发送 ${pingStats.sentCount}"
            } else {
                "建连被拒 / 超时计数"
            }

            IosSingleMetricCard(
                modifier = Modifier.weight(1f),
                title = if (showsPingLoss) "探测丢包率" else "累计异常失败",
                value = lossVal,
                valueColor = appleRed,
                subText = lossSub,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }
    }
}

@Composable
private fun IosSingleMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    valueColor: Color,
    subText: String,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(
                0.5.dp,
                Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x08000000))),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Text(text = title, fontSize = 12.sp, color = textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subText,
                fontSize = 11.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// 4. 专业双轴走势折线图 (带交互发丝探针与气泡)
// ==========================================
@Composable
private fun IosDualAxisChartCard(
    appMode: IosAppMode,
    samples: List<IosDualChartPoint>,
    successLimit: Int,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    appleBlue: Color,
    appleOrange: Color
) {
    var touchFraction by remember(appMode) { mutableStateOf<Float?>(null) }
    val minTime = samples.firstOrNull()?.elapsedMs ?: 0L
    val maxTime = maxOf(minTime + 1000L, samples.lastOrNull()?.elapsedMs ?: 1000L)
    val actualMaxSessions = samples.mapNotNull { it.activeSessions }.maxOrNull() ?: 10
    val maxSessions = calculateIosSmartMaxSessions(actualMaxSessions)
    val actualMaxLatency = samples.mapNotNull { it.pingLatencyMs }.maxOrNull() ?: 10
    val maxLatency = calculateIosSmartMaxLatency(actualMaxLatency)
    fun closest(fraction: Float): IosDualChartPoint? {
        val target = minTime + (maxTime - minTime) * fraction
        return samples.minByOrNull { kotlin.math.abs(it.elapsedMs - target) }
    }


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                0.5.dp,
                Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x10000000))),
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            // 图表顶栏说明与图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "网络动态双轴走势",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 蓝色并发图例
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp, 2.5.dp).background(appleBlue, RoundedCornerShape(1.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text("并发数 (左)", fontSize = 10.5.sp, color = textSecondary)
                    }

                    // 橙色延迟图例
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp, 2.5.dp).background(appleOrange, RoundedCornerShape(1.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text("延迟 ms (右)", fontSize = 10.5.sp, color = textSecondary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 绘图区域 (包含双轴刻度与触摸探针交互)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(samples) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                tryAwaitRelease()
                                touchFraction = null
                            }
                        )
                    }
                    .pointerInput(samples) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                touchFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                touchFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            },
                            onDragEnd = { touchFraction = null },
                            onDragCancel = { touchFraction = null }
                        )
                    }
            ) {

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val bottomY = h - 20f
                    val topY = 15f
                    val chartHeight = bottomY - topY

                    // 1. 绘制水平背景刻度网格线 (3条基准线)
                    for (i in 0..2) {
                        val y = topY + (chartHeight / 2f) * i
                        drawLine(
                            color = Color(0x14000000),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 0.8f
                        )
                    }

                    fun xOf(point: IosDualChartPoint): Float =
                        ((point.elapsedMs - minTime).toFloat() / (maxTime - minTime)) * w
                    fun drawSeries(points: List<IosDualChartPoint>, color: Color, valueOf: (IosDualChartPoint) -> Int?, maximum: Int) {
                        var previous: IosDualChartPoint? = null
                        for (point in points) {
                            val value = valueOf(point)
                            if (value == null) { previous = null; continue }
                            val x = xOf(point)
                            val y = bottomY - (value.toFloat() / maximum).coerceIn(0f, 1f) * chartHeight
                            val before = previous
                            val beforeValue = before?.let(valueOf)
                            if (before != null && beforeValue != null && before.protocol == point.protocol) {
                                val from = Offset(xOf(before), bottomY - (beforeValue.toFloat() / maximum).coerceIn(0f, 1f) * chartHeight)
                                val fill = Path().apply {
                                    moveTo(from.x, bottomY); lineTo(from.x, from.y)
                                    lineTo(x, y); lineTo(x, bottomY); close()
                                }
                                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent)))
                                drawLine(color, from, Offset(x, y), strokeWidth = 2.2f, cap = StrokeCap.Round)
                            } else drawCircle(color, radius = 2.2f, center = Offset(x, y))
                            previous = point
                        }
                    }
                    if (appMode != IosAppMode.PING_STANDALONE) {
                        drawSeries(samples.filter { it.activeSessions != null }, appleBlue, { it.activeSessions }, maxSessions)
                    }
                    if (appMode != IosAppMode.SESSION_HOLD) {
                        val probes = samples.filter { it.hasPingSample }
                        drawSeries(probes, appleOrange, { it.pingLatencyMs }, maxLatency)
                        probes.filter { it.pingLatencyMs == null }.forEach {
                            drawLine(Color(0xFFFF3B30), Offset(xOf(it), bottomY - 5f), Offset(xOf(it), bottomY), strokeWidth = 2f)
                        }
                    }
                    touchFraction?.let { fraction ->
                        closest(fraction)?.let { point ->
                            val x = xOf(point)
                            drawLine(Color(0x807E7E7E), Offset(x, topY), Offset(x, bottomY), strokeWidth = 1f)
                            point.activeSessions?.let { value ->
                                val y = bottomY - (value.toFloat() / maxSessions) * chartHeight
                                drawCircle(appleBlue, radius = 3f, center = Offset(x, y))
                            }
                            if (point.hasPingSample) point.pingLatencyMs?.let { value ->
                                val y = bottomY - (value.toFloat() / maxLatency) * chartHeight
                                drawCircle(appleOrange, radius = 3f, center = Offset(x, y))
                            }
                        }
                    }
                }

                // 触摸悬浮气泡卡片
                touchFraction?.let { frac ->
                    if (samples.isNotEmpty()) {
                        val sample = closest(frac) ?: samples.first()

                        Box(
                            modifier = Modifier
                                .align(if (frac < 0.5f) Alignment.TopEnd else Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(cardBg.copy(alpha = 0.95f))
                                .border(0.5.dp, Color(0x30000000), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Column {
                                Text(
                                    text = "时间: ${sample.elapsedSec}s",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Text(
                                    text = "会话: ${sample.activeSessions ?: "--"} | 延迟: ${sample.pingLatencyMs?.let { "${it}ms" } ?: "--"}",
                                    fontSize = 9.5.sp,
                                    color = textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 轴刻度文字标注
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("左轴: 0 ~ $maxSessions 会话", fontSize = 10.sp, color = appleBlue)
                Text("触摸划动拾取数据点", fontSize = 9.5.sp, color = textSecondary)
                Text("右轴: 0 ~ $maxLatency ms", fontSize = 10.sp, color = appleOrange)
            }
        }
    }
}

// ==========================================
// 5. 目标参数与快速预设配置面板
// ==========================================
@Composable
private fun IosConfigPanel(
    config: IosSessionConfig,
    enabled: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    appleBlue: Color,
    onConfigChange: (IosSessionConfig) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                0.5.dp,
                Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x10000000))),
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "测试目标与网络配置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )

            Spacer(Modifier.height(10.dp))

            // 快速预设药丸切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(
                    "百度" to "www.baidu.com",
                    "腾讯" to "www.qq.com",
                    "Cloudflare" to "1.1.1.1",
                    "网关" to "192.168.1.1"
                )
                presets.forEach { (label, host) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(appleBlue.copy(alpha = 0.08f))
                            .clickable(enabled = enabled) {
                                onConfigChange(config.copy(host = host))
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = appleBlue)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 目标主机输入
            OutlinedTextField(
                value = config.host,
                onValueChange = { onConfigChange(config.copy(host = it)) },
                enabled = enabled,
                label = { Text("目标主机 (Host / 域名 / IP)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = appleBlue,
                    unfocusedBorderColor = Color(0x20000000)
                )
            )

            Spacer(Modifier.height(10.dp))

            // 地址族必须显式选择，测试期间不允许静默回退到另一协议。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IosTestMode.values().forEach { testMode ->
                    FilterChip(
                        selected = config.mode == testMode,
                        onClick = { onConfigChange(config.copy(mode = testMode)) },
                        enabled = enabled,
                        label = {
                            Text(
                                text = testMode.label,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 端口与并发目标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = "${config.port}",
                    onValueChange = { onConfigChange(config.copy(port = it.toIntOrNull() ?: config.port)) },
                    enabled = enabled,
                    label = { Text("目标端口") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appleBlue,
                        unfocusedBorderColor = Color(0x20000000)
                    )
                )

                OutlinedTextField(
                    value = "${config.successLimit}",
                    onValueChange = { onConfigChange(config.copy(successLimit = it.toIntOrNull() ?: config.successLimit)) },
                    enabled = enabled,
                    label = { Text("目标并发数 (iOS 建议 100~1000)") },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appleBlue,
                        unfocusedBorderColor = Color(0x20000000)
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            // 单批并发数与超时
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = "${config.batchSize}",
                    onValueChange = { onConfigChange(config.copy(batchSize = it.toIntOrNull() ?: config.batchSize)) },
                    enabled = enabled,
                    label = { Text("并发速率 (CPS)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appleBlue,
                        unfocusedBorderColor = Color(0x20000000)
                    )
                )

                OutlinedTextField(
                    value = "${config.timeoutMs}",
                    onValueChange = { onConfigChange(config.copy(timeoutMs = it.toIntOrNull() ?: config.timeoutMs)) },
                    enabled = enabled,
                    label = { Text("建连超时 (ms)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appleBlue,
                        unfocusedBorderColor = Color(0x20000000)
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            // 测试停止后维持连接开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("测试结束后维持连接", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                    Text("保持 Socket 打开以测试长连接保活与心跳耗电", fontSize = 11.sp, color = textSecondary)
                }
                Switch(
                    checked = config.keepConnectionsAfterStop,
                    onCheckedChange = { onConfigChange(config.copy(keepConnectionsAfterStop = it)) },
                    enabled = enabled
                )
            }
        }
    }
}

// ==========================================
// 6. 主操作控制按钮条
// ==========================================
@Composable
private fun IosActionControls(
    isRunning: Boolean,
    appMode: IosAppMode,
    heldCount: Int,
    appleBlue: Color,
    appleRed: Color,
    appleOrange: Color,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRelease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 主操作按钮
        Button(
            onClick = { if (isRunning) onStop() else onStart() },
            modifier = Modifier
                .weight(2f)
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) appleRed else appleBlue
            )
        ) {
            Text(
                text = if (isRunning) "停止测试" else when (appMode) {
                    IosAppMode.SESSION_HOLD -> "开始并发压测"
                    IosAppMode.PING_STANDALONE -> "开始独立 Ping"
                    IosAppMode.UNDERLOAD_PING -> "开始联动诊断"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // 释放持链按钮
        if (!isRunning && heldCount > 0) {
            OutlinedButton(
                onClick = onRelease,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, appleOrange)
            ) {
                Text(
                    text = "释放($heldCount)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appleOrange
                )
            }
        }
    }
}

// ==========================================
// 7. 智能 Bufferbloat 与网络专家诊断建议
// ==========================================
@Composable
private fun IosDiagnosticAdviceCard(
    advice: IosDiagnosticAdvice,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    appleBlue: Color,
    appleGreen: Color,
    appleOrange: Color,
    appleRed: Color
) {
    val levelColor = when (advice.level) {
        IosLogLevel.SUCCESS -> appleGreen
        IosLogLevel.WARN -> appleOrange
        IosLogLevel.ERROR -> appleRed
        else -> appleBlue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(
                0.5.dp,
                Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x10000000))),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(levelColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = advice.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = advice.description,
                fontSize = 12.sp,
                color = textSecondary,
                lineHeight = 17.sp
            )
        }
    }
}

// ==========================================
// 8. 实时诊断日志终端流
// ==========================================
@Composable
private fun IosLogConsoleCard(
    logs: List<IosLogLine>,
    filter: IosLogLevel?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    appleBlue: Color,
    appleGreen: Color,
    appleOrange: Color,
    appleRed: Color,
    onFilterChange: (IosLogLevel?) -> Unit,
    onClearLogs: () -> Unit
) {
    val filteredLogs = remember(logs, filter) {
        if (filter == null) logs else logs.filter { it.level == filter }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                0.5.dp,
                Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x10000000))),
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "实时诊断日志流",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "复制全部",
                        fontSize = 11.sp,
                        color = appleBlue,
                        modifier = Modifier.clickable {
                            val text = logs.joinToString("\n") { "[${it.timeText}] [${it.level.name}] ${it.text}" }
                            UIPasteboard.generalPasteboard.string = text
                        }
                    )
                    Text(
                        text = "清空",
                        fontSize = 11.sp,
                        color = appleRed,
                        modifier = Modifier.clickable { onClearLogs() }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 日志级别过滤器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val levels = listOf(null to "全部", IosLogLevel.INFO to "信息", IosLogLevel.WARN to "警告", IosLogLevel.ERROR to "错误")
                levels.forEach { (lvl, title) ->
                    val isSel = (filter == lvl)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) appleBlue.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onFilterChange(lvl) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) appleBlue else textSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 日志流列表
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x0A000000))
                    .padding(8.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    Text(
                        text = "暂无诊断日志...",
                        fontSize = 11.5.sp,
                        color = textSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(filteredLogs) { line ->
                            val lvlColor = when (line.level) {
                                IosLogLevel.SUCCESS -> appleGreen
                                IosLogLevel.WARN -> appleOrange
                                IosLogLevel.ERROR -> appleRed
                                IosLogLevel.STAT -> appleBlue
                                else -> textSecondary
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = line.timeText,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = textSecondary
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(lvlColor.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = line.level.name,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = lvlColor
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = line.text,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 辅助计算：Bufferbloat 与网络诊断建议
// ==========================================
private fun generateDiagnosticAdvice(
    appMode: IosAppMode,
    protocolStats: IosProtocolStats,
    pingStats: IosPingStats
): IosDiagnosticAdvice {
    if (appMode != IosAppMode.SESSION_HOLD) {
        if (pingStats.sentCount == 0) return IosDiagnosticAdvice(
            "等待探测结果", IosLogLevel.INFO,
            "收到真实样本后显示统计。主测试测量 TCP 建连耗时，不包含 DNS 解析。"
        )
        if (pingStats.lostCount > 0) return IosDiagnosticAdvice(
            "检测到探测失败", IosLogLevel.WARN,
            "已完成 ${pingStats.sentCount} 次探测，其中 ${pingStats.lostCount} 次失败。曲线缺口保留失败事件，不能据此单独判定路由器拥塞或 Bufferbloat。"
        )
        return IosDiagnosticAdvice(
            "已接收 ${pingStats.receivedCount} 个 TCP 样本", IosLogLevel.INFO,
            if (appMode == IosAppMode.UNDERLOAD_PING)
                "观察建连压力下的延迟变化。本测试未验证带宽饱和，不能给出 Bufferbloat 等级。"
            else "TCP 建连延迟与 ICMP Ping 口径不同；抖动只统计相邻成功探测，不跨失败样本计算。"
        )
    }
    if (protocolStats.totalFailure > 0) return IosDiagnosticAdvice(
        "连接测试出现失败", IosLogLevel.WARN,
        "成功 ${protocolStats.totalSuccess}，失败 ${protocolStats.totalFailure}。请结合日志区分本机资源限制、目标拒绝、超时和路由异常。"
    )
    return IosDiagnosticAdvice(
        if (protocolStats.totalSuccess > 0) "连接状态" else "等待开始测试", IosLogLevel.INFO,
        "累计成功与当前活动连接分别统计；对端关闭会降低活动数。CPS 设置表示每秒尝试建连数。"
    )
}

private fun calculateIosSmartMaxSessions(actualMax: Int): Int {
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

private fun calculateIosSmartMaxLatency(actualMax: Int): Int {
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
