package com.demonv.netsessiontester

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.demonv.netsessiontester.data.HistoryStore
import com.demonv.netsessiontester.data.LogStore
import com.demonv.netsessiontester.model.AppUiState
import com.demonv.netsessiontester.model.IpProtocol
import com.demonv.netsessiontester.model.LogLevel
import com.demonv.netsessiontester.model.LogLine
import com.demonv.netsessiontester.model.ProtocolStats
import com.demonv.netsessiontester.model.ReleaseUiState
import com.demonv.netsessiontester.model.ResolveResult
import com.demonv.netsessiontester.model.RunPhase
import com.demonv.netsessiontester.model.SessionConfig
import com.demonv.netsessiontester.model.SessionSummary
import com.demonv.netsessiontester.model.TestMode
import com.demonv.netsessiontester.network.TcpTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class RuntimeTask { NONE, PING, CONNECTION, ROAMING }

data class ConnectionRuntimeState(
    val revision: Long = 0L,
    val runId: Long = 0L,
    val startedAtEpochMs: Long = 0L,
    val config: SessionConfig? = null,
    val ui: AppUiState = AppUiState()
)

internal data class PingRuntimeState(
    val revision: Long = 0L,
    val sessionId: Long = 0L,
    val running: Boolean = false,
    val target: String = "",
    val protocol: PingProtocolMode = PingProtocolMode.AUTO,
    val intervalLabel: String = "停止",
    val startedAtEpochMs: Long = 0L,
    val startedAtElapsedMs: Long = 0L,
    val endedAtEpochMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentLatencyMs: Int? = null,
    val sent: Int = 0,
    val lost: Int = 0,
    val jitterMs: Double? = null,
    val points: List<PingPoint> = emptyList(),
    val logs: List<PingLogEntry> = emptyList()
)

/**
 * Process-level owner for work that must outlive an Activity/Compose tree.
 * It only retains applicationContext and immutable StateFlow values.
 */
object AppTestRuntime {
    private val runtimeJob = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(runtimeJob + Dispatchers.IO)

    private val commandMutex = Mutex()
    private val connectionFinishing = AtomicBoolean(false)
    private val _connectionState = MutableStateFlow(ConnectionRuntimeState())
    val connectionState: StateFlow<ConnectionRuntimeState> = _connectionState.asStateFlow()
    private val _pingState = MutableStateFlow(PingRuntimeState())
    internal val pingState: StateFlow<PingRuntimeState> = _pingState.asStateFlow()

    @Volatile private var appContext: Context? = null
    @Volatile private var tester: TcpTester? = null
    @Volatile private var connectionJob: Job? = null
    @Volatile private var pingJob: Job? = null
    @Volatile private var pingRestartForConnectionJob: Job? = null
    @Volatile private var pingLogSaveJob: Job? = null
    @Volatile private var pingCoupledToConnection = false
    @Volatile private var networkWatchJob: Job? = null
    @Volatile private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var lastNoticeAtElapsedMs = 0L
    @Volatile private var lastConnectionUiAtElapsedMs = 0L
    @Volatile private var lastPingLogSaveAtElapsedMs = 0L

    /** Small process-level snapshot used only to restore the release card after Activity recreation. */
    @Volatile var releaseUiSnapshot: ReleaseUiState = ReleaseUiState()
    val activeTask: RuntimeTask
        get() = when {
            connectionJob?.isActive == true || connectionFinishing.get() -> RuntimeTask.CONNECTION
            pingJob?.isActive == true -> RuntimeTask.PING
            else -> RuntimeTask.NONE
        }

    fun initialize(context: Context) {
        if (appContext != null) return
        synchronized(this) {
            if (appContext == null) {
                val application = context.applicationContext
                appContext = application
                tester = TcpTester(application)
                restoreInterruptedCheckpoint(application)
            }
        }
    }

    private fun restoreInterruptedCheckpoint(context: Context) {
        val prefs = context.getSharedPreferences(RUNTIME_CHECKPOINT_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return
        val runId = prefs.getLong("run_id", 0L)
        val task = prefs.getString("task", RuntimeTask.CONNECTION.name).orEmpty()
        val line = LogLine(level = LogLevel.ERROR, text = "上一轮${if (task == RuntimeTask.PING.name) "Ping" else "连接数测试"}被系统中断，后台任务未被幽灵重启")
        _connectionState.value = ConnectionRuntimeState(
            revision = 1L,
            runId = runId,
            startedAtEpochMs = runId,
            ui = AppUiState(runPhase = RunPhase.Failed, status = "系统中断", error = "系统中断", logs = listOf(line))
        )
        prefs.edit().clear().apply()
        scope.launch { runCatching { LogStore(context).append(line) } }
    }

    internal suspend fun resolveHost(host: String): ResolveResult {
        return tester?.resolveHost(host) ?: ResolveResult(host = host, error = "运行控制器尚未初始化")
    }

    @Synchronized
    fun startConnection(config: SessionConfig, existingLogs: List<LogLine> = emptyList()): Boolean {
        val context = appContext ?: return false
        if (connectionJob?.isActive == true || connectionFinishing.get()) return false
        val runId = System.currentTimeMillis()
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            commandMutex.withLock {
                acquireWakeLock(context, CONNECTION_WAKE_LOCK_TIMEOUT_MS)
                context.getSharedPreferences(RUNTIME_CHECKPOINT_PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("active", true)
                    .putLong("run_id", runId)
                    .putString("task", RuntimeTask.CONNECTION.name)
                    .apply()
                startForeground(context, "建连中：${config.mode.label}，目标 ${config.successLimit}")
                val initialUi = AppUiState(
                    isAdding = true,
                    runPhase = RunPhase.Running,
                    status = "建连中",
                    ipv4Stats = ProtocolStats(IpProtocol.IPV4),
                    ipv6Stats = ProtocolStats(IpProtocol.IPV6),
                    logs = existingLogs.takeLast(MAX_RUNTIME_LOGS)
                )
                releaseUiSnapshot = ReleaseUiState()
                publish(runId, runId, config, initialUi)
                appendLog(LogLine(text = "目标：${config.host}:${config.port} | 模式：${config.mode.label} | 目标CPS：${config.batchSize}/s | 调度间隔：${config.intervalMs}ms | 固定CPS核心"))
                runConnection(runId, config)
            }
        }
        connectionJob = newJob
        newJob.start()
        return true
    }

    fun stopConnection(reason: String = "手动停止", force: Boolean = false) {
        scope.launch {
            val snapshot = _connectionState.value
            if (activeTask != RuntimeTask.CONNECTION && !snapshot.ui.releaseUi.visible && !force) return@launch
            val summary = stoppedSummary(snapshot, reason)
            finishConnection(
                runId = snapshot.runId.takeIf { it != 0L } ?: System.nanoTime(),
                reason = when {
                    force -> ConnectionFinishReason.FORCE_RELEASE
                    reason.contains("服务销毁") -> ConnectionFinishReason.SERVICE_DESTROYED
                    else -> ConnectionFinishReason.MANUAL_STOP
                },
                summary = summary,
                saveHistory = !force,
                cancelRunner = true
            )
        }
    }

    fun stopActiveTask(reason: String = "通知栏停止") {
        when (activeTask) {
            RuntimeTask.CONNECTION -> stopConnection(reason)
            RuntimeTask.PING -> stopPing(reason)
            RuntimeTask.ROAMING -> stopRoaming(reason)
            RuntimeTask.NONE -> appContext?.let(::stopForeground)
        }
    }

    fun onServiceDestroyed() {
        when (activeTask) {
            RuntimeTask.CONNECTION -> stopConnection("服务销毁保护")
            RuntimeTask.PING -> stopPing("服务销毁保护")
            RuntimeTask.ROAMING -> stopRoaming("服务销毁保护")
            RuntimeTask.NONE -> releaseWakeLock()
        }
    }

    private suspend fun runConnection(runId: Long, config: SessionConfig) {
        var summary: SessionSummary? = null
        var completedNormally = false
        try {
            val tcp = tester ?: return
            val oldClosed = tcp.release()
            if (oldClosed > 0) appendLog(LogLine(level = LogLevel.WARN, text = "开始新测试前释放旧连接：$oldClosed 条"))
            startNetworkWatch(runId)
            val pair = tcp.runSessionHoldTest(
                rawConfig = config,
                onStats = statsHandler@ { stats ->
                    if (_connectionState.value.runId != runId || !_connectionState.value.ui.isAdding) return@statsHandler
                    val nextPhase = if (stats.phase.contains("无增长") || stats.phase.contains("确认")) RunPhase.TopConfirm else RunPhase.Running
                    val current = _connectionState.value
                    val nextUi = when (stats.protocol) {
                        IpProtocol.IPV4 -> current.ui.copy(ipv4Stats = stats, status = "IPv4 ${stats.phase}", runPhase = nextPhase)
                        IpProtocol.IPV6 -> current.ui.copy(ipv6Stats = stats, status = "IPv6 ${stats.phase}", runPhase = nextPhase)
                    }
                    val now = SystemClock.elapsedRealtime()
                    val terminal = stats.phase.contains("完成") || stats.phase.contains("释放") || stats.phase.contains("中断") || stats.phase.contains("上限")
                    if (terminal || now - lastConnectionUiAtElapsedMs >= CONNECTION_UI_THROTTLE_MS) {
                        lastConnectionUiAtElapsedMs = now
                        publishUi(nextUi)
                    }
                    updateNoticeThrottled("${stats.protocol.label} ${stats.phase}｜活动 ${stats.activeSessions}｜失败 ${stats.totalFailure}")
                },
                onLog = { appendLog(it) }
            )
            val current = _connectionState.value.ui
            summary = SessionSummary(
                startedAtEpochMs = runId,
                host = config.host,
                port = config.port,
                mode = config.mode,
                ipv4Stats = when (config.mode) {
                    TestMode.IPV4_ONLY, TestMode.IPV4_THEN_IPV6 -> pair.first ?: current.ipv4Stats
                    TestMode.IPV6_ONLY -> null
                },
                ipv6Stats = when (config.mode) {
                    TestMode.IPV6_ONLY, TestMode.IPV4_THEN_IPV6 -> pair.second ?: current.ipv6Stats
                    TestMode.IPV4_ONLY -> null
                }
            )
            completedNormally = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Error) {
            appendLog(LogLine(level = LogLevel.ERROR, text = "测试发生严重资源错误：${error.javaClass.simpleName}"))
            throw error
        } catch (error: Throwable) {
            appendLog(LogLine(level = LogLevel.ERROR, text = "测试中断：${error.message ?: error.javaClass.simpleName}"))
        } finally {
            networkWatchJob?.cancel()
            networkWatchJob = null
            if (!connectionFinishing.get() && _connectionState.value.runId == runId) {
                val finalSummary = summary ?: stoppedSummary(_connectionState.value, "测试中断")
                    ?.takeIf { (it.ipv4Stats?.totalAttempts ?: 0) + (it.ipv6Stats?.totalAttempts ?: 0) > 0 }
                val reason = finishReason(finalSummary, completedNormally)
                finishConnection(runId, reason, finalSummary, reason.saveHistory, cancelRunner = false)
            }
        }
    }

    private fun startNetworkWatch(runId: Long) {
        networkWatchJob?.cancel()
        val initial = networkSignature()
        networkWatchJob = scope.launch {
            delay(1_500L)
            var changedCount = 0
            while (currentCoroutineContext().isActive && _connectionState.value.runId == runId && _connectionState.value.ui.isAdding) {
                if (networkSignature() != initial) {
                    changedCount++
                    if (changedCount >= 2) {
                        val snapshot = _connectionState.value
                        appendLog(LogLine(level = LogLevel.ERROR, text = "网络环境变化，已中断测试并保存历史。"))
                        finishConnection(runId, ConnectionFinishReason.NETWORK_CHANGE, stoppedSummary(snapshot, "网络环境变化"), true, true)
                        break
                    }
                } else changedCount = 0
                delay(1_000L)
            }
        }
    }

    private suspend fun finishConnection(
        runId: Long,
        reason: ConnectionFinishReason,
        summary: SessionSummary?,
        saveHistory: Boolean,
        cancelRunner: Boolean
    ) {
        if (!connectionFinishing.compareAndSet(false, true)) return
        try {
            pingRestartForConnectionJob?.cancel()
            pingRestartForConnectionJob = null
            if (pingCoupledToConnection) stopPing("连接数测试结束")
            val currentJob = currentCoroutineContext()[Job]
            if (cancelRunner && connectionJob != currentJob) connectionJob?.cancel()
            networkWatchJob?.cancel()
            networkWatchJob = null
            val tcp = tester ?: return
            val sockets = tcp.detachForRelease()
            val started = SystemClock.elapsedRealtime()
            val releaseRunId = _connectionState.value.ui.releaseUi.runId.takeIf { it != 0L } ?: runId
            val baseUi = _connectionState.value.ui
            val releasing = ReleaseUiState(
                runId = releaseRunId,
                visible = true,
                total = sockets.size,
                message = if (sockets.isEmpty()) "没有需要释放的连接" else "正在关闭 Socket 连接，请勿退出页面"
            )
            releaseUiSnapshot = releasing
            publishUi(baseUi.copy(isAdding = false, runPhase = RunPhase.Releasing, status = "${reason.label} · 正在释放", summary = summary ?: baseUi.summary, releaseUi = releasing))
            appendLog(LogLine(level = LogLevel.WARN, text = "${reason.label}：已停止新增，开始释放 ${sockets.size} 条 socket"))
            updateNoticeNow("正在释放连接 0/${sockets.size}")
            delay(80L)
            var lastReleaseNoticeAt = SystemClock.elapsedRealtime()
            val closed = runCatching {
                tcp.closeDetachedSockets(sockets, batchSize = 512, workerCount = 6, progressIntervalMs = 300L) { done, total, elapsed ->
                    if (done < total) {
                        val speed = if (done <= 0) 0 else (done * 1_000L / elapsed.coerceAtLeast(1L)).toInt().coerceAtLeast(1)
                        val progress = ReleaseUiState(
                            runId = releaseRunId,
                            visible = true,
                            total = total,
                            closed = done,
                            speedPerSecond = speed,
                            elapsedMs = elapsed,
                            message = "正在关闭 Socket 连接，请勿退出页面"
                        )
                        releaseUiSnapshot = progress
                        publishUi(_connectionState.value.ui.copy(releaseUi = progress))
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastReleaseNoticeAt >= NOTICE_THROTTLE_MS) {
                            lastReleaseNoticeAt = now
                            updateNoticeNow("正在释放连接 $done/$total｜${progress.percent}%")
                        }
                    }
                }
            }.getOrElse { error ->
                appendLog(LogLine(level = LogLevel.ERROR, text = "${reason.label}：close 异常：${error.message ?: error.javaClass.simpleName}"))
                0
            }
            if (saveHistory && summary != null) {
                runCatching { appContext?.let { HistoryStore(it).append(summary) } }
                    .onFailure { appendLog(LogLine(level = LogLevel.ERROR, text = "保存历史失败：${it.message ?: it.javaClass.simpleName}")) }
            }
            val elapsed = SystemClock.elapsedRealtime() - started
            val failed = reason !in listOf(ConnectionFinishReason.COMPLETED, ConnectionFinishReason.FORCE_RELEASE)
            val old = _connectionState.value.ui
            val finished = old.releaseUi.copy(
                visible = true,
                closed = old.releaseUi.total.takeIf { it > 0 } ?: closed,
                elapsedMs = elapsed,
                finished = true,
                finishedAtEpochMs = System.currentTimeMillis(),
                message = "释放完成"
            )
            releaseUiSnapshot = finished
            publishUi(old.copy(
                isAdding = false,
                runPhase = if (failed) RunPhase.Failed else RunPhase.Finished,
                status = if (reason == ConnectionFinishReason.FORCE_RELEASE) "已释放" else "${reason.label} · 已释放",
                summary = summary ?: old.summary,
                error = reason.label.takeIf { failed },
                releaseUi = finished
            ))
            scope.launch {
                delay(10_000L)
                if (_connectionState.value.ui.releaseUi == finished) {
                    releaseUiSnapshot = ReleaseUiState()
                    publishUi(_connectionState.value.ui.copy(releaseUi = ReleaseUiState()))
                }
            }
        } finally {
            connectionJob = null
            connectionFinishing.set(false)
            if (pingJob?.isActive != true) {
                releaseWakeLock()
                appContext?.let(::stopForeground)
            }
            appContext?.getSharedPreferences(RUNTIME_CHECKPOINT_PREFS, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
        }
    }

    private fun stoppedSummary(snapshot: ConnectionRuntimeState, reason: String): SessionSummary? {
        val config = snapshot.config ?: return null
        fun stopped(stats: ProtocolStats) = stats.copy(phase = reason, errorSummary = stats.errorSummary + (reason to 1))
        return SessionSummary(
            startedAtEpochMs = snapshot.startedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            host = config.host,
            port = config.port,
            mode = config.mode,
            ipv4Stats = when (config.mode) {
                TestMode.IPV4_ONLY -> stopped(snapshot.ui.ipv4Stats)
                TestMode.IPV4_THEN_IPV6 -> snapshot.ui.ipv4Stats.takeIf { it.totalAttempts > 0 }?.let(::stopped)
                TestMode.IPV6_ONLY -> null
            },
            ipv6Stats = when (config.mode) {
                TestMode.IPV6_ONLY -> stopped(snapshot.ui.ipv6Stats)
                TestMode.IPV4_THEN_IPV6 -> snapshot.ui.ipv6Stats.takeIf { it.totalAttempts > 0 }?.let(::stopped)
                TestMode.IPV4_ONLY -> null
            }
        )
    }

    private fun finishReason(summary: SessionSummary?, completedNormally: Boolean): ConnectionFinishReason {
        if (summary == null) return ConnectionFinishReason.INTERRUPTED
        val stats = listOfNotNull(summary.ipv4Stats, summary.ipv6Stats)
        if (stats.isNotEmpty() && stats.all { it.phase == "解析失败" || it.errorSummary.keys.any { key -> key.contains("DNS") } }) return ConnectionFinishReason.DNS_FAIL
        if (stats.any { it.phase.contains("FD", true) || it.errorSummary.keys.any { key -> key.contains("FD", true) } }) return ConnectionFinishReason.FD_LIMIT
        if (stats.any { it.phase.contains("无增长") }) return ConnectionFinishReason.NO_GROWTH
        if (stats.any { it.phase.contains("连续失败") }) return ConnectionFinishReason.CONSECUTIVE_FAILURE
        if (stats.any { it.phase.contains("失败上限") }) return ConnectionFinishReason.FAILURE_LIMIT
        return if (completedNormally) ConnectionFinishReason.COMPLETED else ConnectionFinishReason.INTERRUPTED
    }

    private suspend fun appendLog(line: LogLine) {
        val current = _connectionState.value
        publishUi(current.ui.copy(logs = (current.ui.logs + line).takeLast(MAX_RUNTIME_LOGS)))
        runCatching { appContext?.let { LogStore(it).append(line) } }
    }

    private fun publish(runId: Long, startedAt: Long, config: SessionConfig?, ui: AppUiState) {
        val previous = _connectionState.value
        _connectionState.value = ConnectionRuntimeState(previous.revision + 1L, runId, startedAt, config, ui)
    }

    private fun publishUi(ui: AppUiState) {
        val current = _connectionState.value
        _connectionState.value = current.copy(revision = current.revision + 1L, ui = ui)
    }

    private fun networkSignature(): String {
        val context = appContext ?: return "none"
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "none"
        val network: Network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network)
        val transports = buildList {
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("wifi")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("cell")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("vpn")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("ethernet")
        }.joinToString("+")
        val link = cm.getLinkProperties(network)
        return "${network.hashCode()}|$transports|${link?.interfaceName}|${link?.dnsServers?.joinToString()}"
    }

    private fun acquireWakeLock(context: Context, timeoutMs: Long) {
        releaseWakeLock()
        val manager = context.getSystemService(PowerManager::class.java) ?: return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NetSessionTester:runtime").apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        wakeLock = null
    }

    private fun startForeground(context: Context, text: String) {
        val intent = Intent(context, TestForegroundService::class.java)
            .setAction(TestForegroundService.ACTION_START)
            .putExtra(TestForegroundService.EXTRA_TEXT, text)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun updateNoticeThrottled(text: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNoticeAtElapsedMs < NOTICE_THROTTLE_MS) return
        lastNoticeAtElapsedMs = now
        updateNoticeNow(text)
    }

    private fun updateNoticeNow(text: String) {
        val context = appContext ?: return
        val intent = Intent(context, TestForegroundService::class.java)
            .setAction(TestForegroundService.ACTION_UPDATE)
            .putExtra(TestForegroundService.EXTRA_TEXT, text)
        runCatching { context.startService(intent) }
    }

    private fun stopForeground(context: Context) {
        val intent = Intent(context, TestForegroundService::class.java).setAction(TestForegroundService.ACTION_FINISH)
        runCatching { context.startService(intent) }
    }

    @Synchronized
    internal fun startPing(
        target: String,
        intervalMs: Long,
        timeoutMs: Int,
        maxCount: Int?,
        protocol: PingProtocolMode,
        existingLogs: List<PingLogEntry>,
        reset: Boolean,
        coupledToConnection: Boolean = false
    ): Boolean {
        val context = appContext ?: return false
        if (pingJob?.isActive == true) return false
        val sessionId = System.currentTimeMillis()
        pingCoupledToConnection = coupledToConnection
        if (connectionJob?.isActive != true) {
            context.getSharedPreferences(RUNTIME_CHECKPOINT_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("active", true)
                .putLong("run_id", sessionId)
                .putString("task", RuntimeTask.PING.name)
                .apply()
        }
        val previous = _pingState.value
        _pingState.value = PingRuntimeState(
            revision = previous.revision + 1L,
            sessionId = sessionId,
            running = true,
            target = target,
            protocol = protocol,
            intervalLabel = "准备中",
            logs = trimPingLogSessions(existingLogs + PingLogEntry(
                timeEpochMs = sessionId,
                target = target,
                protocol = protocol.label,
                latencyMs = null,
                status = "开始",
                note = "间隔${intervalMs}ms · 超时${timeoutMs}ms",
                sessionId = sessionId
            )),
            points = if (reset) emptyList() else previous.points,
            jitterMs = if (reset) null else previous.jitterMs
        )
        schedulePingLogSave(_pingState.value.logs)
        startForeground(context, "Ping准备中：$target")
        acquireWakeLock(context, PING_WAKE_LOCK_TIMEOUT_MS)
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            runPingSession(sessionId, target, intervalMs, timeoutMs, maxCount, protocol)
        }
        pingJob = newJob
        newJob.start()
        return true
    }

    internal fun restartPingForConnection(
        target: String,
        intervalMs: Long,
        timeoutMs: Int,
        maxCount: Int?,
        protocol: PingProtocolMode,
        existingLogs: List<PingLogEntry>
    ) {
        if (connectionJob?.isActive != true || connectionFinishing.get()) return
        val previous = pingJob
        previous?.cancel(CancellationException("连接数测试开始，切换为同步 Ping"))
        pingRestartForConnectionJob?.cancel()
        pingRestartForConnectionJob = scope.launch {
            previous?.join()
            currentCoroutineContext().ensureActive()
            if (connectionJob?.isActive != true || connectionFinishing.get()) return@launch
            startPing(target, intervalMs, timeoutMs, maxCount, protocol, existingLogs, reset = true, coupledToConnection = true)
            pingRestartForConnectionJob = null
        }
    }

    private suspend fun runPingSession(
        sessionId: Long,
        target: String,
        intervalMs: Long,
        timeoutMs: Int,
        maxCount: Int?,
        requestedProtocol: PingProtocolMode
    ) {
        var resolved = resolvePingTarget(target, requestedProtocol)
        if (resolved.error != null) {
            appendRuntimePingLog(PingLogEntry(target = target, protocol = resolved.displayProtocol, latencyMs = null, status = resolved.error, note = "未开始", sessionId = sessionId))
            finishPingSession(sessionId, resolved.error)
            return
        }
        val startedEpoch = System.currentTimeMillis()
        val startedElapsed = SystemClock.elapsedRealtime()
        var currentNetwork = networkSignature()
        var currentBucketMs = 0L
        val bucketSamples = mutableListOf<Int?>()
        val pendingLogs = mutableListOf<PingLogEntry>()
        val jitter = RttJitterWindow(50)
        val resultMutex = Mutex()
        val sent = AtomicInteger(0)
        val lost = AtomicInteger(0)
        var consecutiveLossStartedAt = 0L
        var autoStop = false

        fun publishStarted(label: String) {
            val old = _pingState.value
            if (old.sessionId != sessionId) return
            _pingState.value = old.copy(
                revision = old.revision + 1L,
                intervalLabel = label,
                startedAtEpochMs = startedEpoch,
                startedAtElapsedMs = startedElapsed
            )
        }

        fun flushBucket(bucketMs: Long) {
            val old = _pingState.value
            if (old.sessionId != sessionId) return
            val valid = bucketSamples.mapNotNull { it }
            val point = if (bucketSamples.isEmpty()) null else PingPoint(
                elapsedSec = (bucketMs / 1_000L).toInt(),
                latencyMs = valid.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                lossCount = bucketSamples.count { it == null },
                highLatency = valid.any { it >= 100 },
                sampleCount = bucketSamples.size.coerceAtLeast(1),
                elapsedMs = bucketMs,
                minLatencyMs = valid.minOrNull(),
                maxLatencyMs = valid.maxOrNull()
            )
            val nextLogs = if (pendingLogs.isEmpty()) old.logs else trimPingLogSessions(old.logs + pendingLogs)
            _pingState.value = old.copy(
                revision = old.revision + 1L,
                durationMs = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L),
                currentLatencyMs = bucketSamples.lastOrNull(),
                sent = sent.get(),
                lost = lost.get(),
                jitterMs = jitter.currentJitterMs(),
                points = if (point == null) old.points else (old.points.filterNot { it.elapsedMs == bucketMs } + point).sortedBy { it.elapsedMs }.takeLast(2_400),
                logs = nextLogs
            )
            if (pendingLogs.isNotEmpty()) schedulePingLogSave(nextLogs)
            bucketSamples.clear()
            pendingLogs.clear()
        }

        suspend fun refreshAfterNetworkChange(): Boolean {
            val signature = networkSignature()
            if (signature == currentNetwork) return false
            currentNetwork = signature
            pendingLogs += PingLogEntry(
                target = target,
                protocol = requestedProtocol.label,
                latencyMs = null,
                status = "网络切换",
                note = "已取消旧网络探测并重新解析；等待期不计丢包",
                sessionId = sessionId,
                elapsedMs = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L)
            )
            flushBucket(currentBucketMs)
            resolved = resolvePingTarget(target, requestedProtocol)
            return true
        }

        fun handleResult(latency: Int?, failure: String?) {
            if (_pingState.value.sessionId != sessionId) return
            val elapsed = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L)
            val bucketMs = (elapsed / PING_UI_BUCKET_MS) * PING_UI_BUCKET_MS
            if (bucketMs != currentBucketMs) {
                flushBucket(currentBucketMs)
                currentBucketMs = bucketMs
            }
            sent.incrementAndGet()
            if (latency == null) {
                lost.incrementAndGet()
                if (consecutiveLossStartedAt == 0L) consecutiveLossStartedAt = SystemClock.elapsedRealtime()
                if (!autoStop && SystemClock.elapsedRealtime() - consecutiveLossStartedAt >= 5_000L) {
                    autoStop = true
                    pendingLogs += PingLogEntry(target = target, protocol = resolved.displayProtocol, latencyMs = null, status = "中断", note = "连续5秒100%丢包，已自动停止并保存记录", sessionId = sessionId, elapsedMs = elapsed)
                    pingJob?.cancel(CancellationException("连续5秒100%丢包"))
                }
            } else {
                consecutiveLossStartedAt = 0L
                jitter.onSuccess(latency.toDouble())
            }
            bucketSamples += latency
            pendingLogs += PingLogEntry(
                target = target,
                protocol = resolved.displayProtocol,
                latencyMs = latency,
                status = when { latency == null -> failure ?: "超时"; latency >= 100 -> "高延迟"; else -> "成功" },
                note = if (latency == null) failure ?: "timeout" else "",
                sessionId = sessionId,
                elapsedMs = elapsed
            )
            val lossPercent = if (sent.get() <= 0) 0 else lost.get() * 100 / sent.get()
            if (connectionJob?.isActive != true && !connectionFinishing.get()) {
                updateNoticeThrottled("Ping $target｜${latency?.let { "${it}ms" } ?: failure ?: "超时"}｜丢包 $lossPercent%｜${elapsed / 1_000L}s")
            }
        }

        publishStarted("${resolved.displayProtocol} · ${intervalMs}ms")
        try {
            if (intervalMs < 200L) {
                val tcpProbe = findTcpPingPort(resolved.address, timeoutMs)
                if (tcpProbe != null) {
                    kotlinx.coroutines.coroutineScope {
                        publishStarted("TCP高频${intervalMs}ms")
                        val inFlight = AtomicInteger(0)
                        var scheduled = 0
                        var nextTick = SystemClock.elapsedRealtime()
                        val maxInFlight = pingMaxInflight(intervalMs, timeoutMs)
                        while (currentCoroutineContext().isActive && (maxCount == null || scheduled < maxCount)) {
                            val wait = nextTick - SystemClock.elapsedRealtime()
                            if (wait > 0L) delay(wait)
                            nextTick += intervalMs
                            if (resultMutex.withLock { refreshAfterNetworkChange() } || resolved.error != null) continue
                            if (inFlight.get() >= maxInFlight) continue
                            scheduled++
                            inFlight.incrementAndGet()
                            val signature = currentNetwork
                            launch {
                                try {
                                    val result = tcpSocketPingResolved(resolved.address, tcpProbe.port, timeoutMs.coerceIn(180, 5_000))
                                    resultMutex.withLock {
                                        if (networkSignature() == signature) handleResult(result.latencyMs, result.failure)
                                    }
                                } finally { inFlight.decrementAndGet() }
                            }
                        }
                        while (inFlight.get() > 0 && currentCoroutineContext().isActive) delay(10L)
                    }
                } else {
                    publishStarted("ICMP高频${intervalMs}ms")
                    while (currentCoroutineContext().isActive && (maxCount == null || sent.get() < maxCount)) {
                        if (refreshAfterNetworkChange() || resolved.error != null) { delay(100L); continue }
                        val remaining = maxCount?.let { (it - sent.get()).coerceAtLeast(0) } ?: 50
                        if (remaining <= 0) break
                        val signature = currentNetwork
                        val streamed = streamIcmpPingResolved(resolved.address, timeoutMs, resolved.protocol, intervalMs, remaining.coerceAtMost(50)) { event ->
                            if (networkSignature() == signature) handleResult(event.latencyMs, event.failure)
                        }
                        if (streamed <= 0) {
                            val loop = SystemClock.elapsedRealtime()
                            val result = icmpPingResolved(resolved.address, timeoutMs, resolved.protocol)
                            if (networkSignature() == signature) handleResult(result.latencyMs, result.failure)
                            delay((intervalMs - (SystemClock.elapsedRealtime() - loop)).coerceAtLeast(0L))
                        }
                    }
                }
            } else {
                while (currentCoroutineContext().isActive && (maxCount == null || sent.get() < maxCount)) {
                    if (refreshAfterNetworkChange() || resolved.error != null) { delay(100L); continue }
                    val loop = SystemClock.elapsedRealtime()
                    val signature = currentNetwork
                    val result = icmpPingResolved(resolved.address, timeoutMs, resolved.protocol)
                    if (networkSignature() == signature) handleResult(result.latencyMs, result.failure)
                    delay((intervalMs - (SystemClock.elapsedRealtime() - loop)).coerceAtLeast(0L))
                }
            }
        } finally {
            flushBucket(currentBucketMs)
            finishPingSession(sessionId, if (autoStop) "连续丢包中断" else "已停止")
        }
    }

    private fun appendRuntimePingLog(entry: PingLogEntry) {
        val old = _pingState.value
        val next = trimPingLogSessions(old.logs + entry)
        _pingState.value = old.copy(revision = old.revision + 1L, logs = next)
        schedulePingLogSave(next)
    }

    private fun schedulePingLogSave(logs: List<PingLogEntry>) {
        val context = appContext ?: return
        pingLogSaveJob?.cancel()
        pingLogSaveJob = scope.launch {
            val now = SystemClock.elapsedRealtime()
            val waitMs = (PING_DISK_SAVE_INTERVAL_MS - (now - lastPingLogSaveAtElapsedMs)).coerceAtLeast(0L)
            if (waitMs > 0L) delay(waitMs)
            runCatching { savePingLogs(context, logs) }
            lastPingLogSaveAtElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    private fun finishPingSession(sessionId: Long, label: String) {
        val old = _pingState.value
        if (old.sessionId != sessionId) return
        val ended = System.currentTimeMillis()
        val duration = if (old.startedAtElapsedMs > 0L) (SystemClock.elapsedRealtime() - old.startedAtElapsedMs).coerceAtLeast(0L) else 0L
        val finalLog = PingLogEntry(target = old.target, protocol = old.protocol.label, latencyMs = null, status = "停止", note = "共${old.sent}次 · 时长${duration / 1_000L}秒", sessionId = sessionId, elapsedMs = duration)
        val logs = trimPingLogSessions(old.logs + finalLog)
        _pingState.value = old.copy(revision = old.revision + 1L, running = false, intervalLabel = label, endedAtEpochMs = ended, durationMs = duration, logs = logs)
        pingLogSaveJob?.cancel()
        appContext?.let { context -> runCatching { savePingLogs(context, logs) } }
        lastPingLogSaveAtElapsedMs = SystemClock.elapsedRealtime()
        pingJob = null
        pingCoupledToConnection = false
        if (connectionJob?.isActive != true && !connectionFinishing.get()) {
            releaseWakeLock()
            appContext?.let(::stopForeground)
            appContext?.getSharedPreferences(RUNTIME_CHECKPOINT_PREFS, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
        }
    }

    fun stopPing(reason: String) {
        val job = pingJob ?: return
        appendRuntimePingLog(PingLogEntry(target = _pingState.value.target.ifBlank { "Ping" }, protocol = _pingState.value.protocol.label, latencyMs = null, status = "中断", note = reason, sessionId = _pingState.value.sessionId, elapsedMs = _pingState.value.durationMs))
        job.cancel(CancellationException(reason))
    }

    fun clearPing() {
        if (pingJob?.isActive == true) return
        _pingState.value = PingRuntimeState(revision = _pingState.value.revision + 1L)
        schedulePingLogSave(emptyList())
    }

    fun stopRoaming(reason: String) { /* Roaming is accuracy-first and is managed by its paused UI checkpoint. */ }

    private enum class ConnectionFinishReason(val label: String, val saveHistory: Boolean) {
        COMPLETED("测试完成", true), FAILURE_LIMIT("失败上限", true), NO_GROWTH("无增长确认", true),
        CONSECUTIVE_FAILURE("连续失败", true), FD_LIMIT("FD上限", true), MANUAL_STOP("手动停止", true),
        FORCE_RELEASE("强制释放", false), NETWORK_CHANGE("网络环境变化", true), DNS_FAIL("解析失败", false),
        INTERRUPTED("测试中断", true), SERVICE_DESTROYED("服务销毁保护", true)
    }

    private const val MAX_RUNTIME_LOGS = 500
    private const val RUNTIME_CHECKPOINT_PREFS = "test_runtime_checkpoint"
    private const val NOTICE_THROTTLE_MS = 1_000L
    private const val CONNECTION_UI_THROTTLE_MS = 500L
    private const val PING_UI_BUCKET_MS = 500L
    private const val PING_DISK_SAVE_INTERVAL_MS = 2_000L
    private const val PING_WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1_000L
    private const val CONNECTION_WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1_000L
}
