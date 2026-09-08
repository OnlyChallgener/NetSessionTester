package com.demonv.netsessiontester.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.gettimeofday
import platform.posix.clock_gettime
import platform.posix.CLOCK_MONOTONIC
import platform.posix.timespec
import platform.posix.timeval

/**
 * 获取当前毫秒级时间戳 (基于 POSIX 极速时间系统)
 */
@OptIn(ExperimentalForeignApi::class)
fun getEpochMs(): Long = memScoped {
    val tv = alloc<timeval>()
    gettimeofday(tv.ptr, null)
    (tv.tv_sec * 1000L) + (tv.tv_usec / 1000L)
}

/** 测试计时使用单调时钟，避免系统时间校准导致 RTT、CPS 和横轴跳变。 */
@OptIn(ExperimentalForeignApi::class)
fun getMonotonicMs(): Long = memScoped {
    val ts = alloc<timespec>()
    if (clock_gettime(CLOCK_MONOTONIC, ts.ptr) == 0) {
        (ts.tv_sec * 1000L) + (ts.tv_nsec / 1_000_000L)
    } else {
        getEpochMs()
    }
}

/**
 * iOS 测试模式枚举
 */
enum class IosAppMode(val label: String) {
    SESSION_HOLD("并发压测"),
    PING_STANDALONE("独立 Ping"),
    UNDERLOAD_PING("联动诊断")
}

/**
 * IP 协议类型
 */
enum class IosIpProtocol(val label: String) {
    IPV4("IPv4"),
    IPV6("IPv6")
}

/**
 * 测试网络策略
 */
enum class IosTestMode(val label: String) {
    IPV4_ONLY("仅 IPv4"),
    IPV6_ONLY("仅 IPv6"),
    IPV4_THEN_IPV6("双栈分别轮测")
}

/**
 * 测试会话参数配置
 */
data class IosSessionConfig(
    val host: String = "www.baidu.com",
    val port: Int = 80,
    val mode: IosTestMode = IosTestMode.IPV4_ONLY,
    val batchSize: Int = 50,          // 目标发起速率（次/秒），保留字段名兼容现有配置
    val intervalMs: Long = 20L,       // 调度器刷新间隔，不代表批次间隔
    val pingIntervalMs: Long = 500L,
    val timeoutMs: Int = 1500,        // 握手超时 (ms)
    val successLimit: Int = 500,      // 累计成功握手目标；当前仍存活的连接单独统计
    val failureLimit: Int = 200,      // 失败容忍上限
    val keepConnectionsAfterStop: Boolean = true // 停止测试后保持连接
) {
    fun normalized(): IosSessionConfig {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        return copy(
            host = cleanHost,
            port = port.coerceIn(1, 65535),
            batchSize = batchSize.coerceIn(1, 5000),
            intervalMs = intervalMs.coerceIn(10L, 100L),
            pingIntervalMs = pingIntervalMs.coerceIn(100L, 5000L),
            timeoutMs = timeoutMs.coerceIn(200, 10000),
            successLimit = successLimit.coerceIn(10, 5000),
            failureLimit = failureLimit.coerceIn(10, 5000)
        )
    }
}

/**
 * 协议层实时并发数据统计
 */
data class IosProtocolStats(
    val protocol: IosIpProtocol = IosIpProtocol.IPV4,
    val phase: String = "待测试",
    val resolvedAddresses: List<String> = emptyList(),
    val activeSessions: Int = 0,
    val totalSuccess: Int = 0,
    val totalFailure: Int = 0,
    val totalAttempts: Int = 0,
    val lastAdded: Int = 0,
    val cps: Int = 0,
    val maxStableSessions: Int = 0,
    val averageConnectLatencyMs: Int = 0,
    val errorSummary: Map<String, Int> = emptyMap()
)

/**
 * 独立与联动 Ping 实时质量指标
 */
data class IosPingStats(
    val host: String = "",
    val port: Int = 80,
    val protocol: IosIpProtocol = IosIpProtocol.IPV4,
    val currentLatencyMs: Int? = null,
    val minLatencyMs: Int = 0,
    val maxLatencyMs: Int = 0,
    val avgLatencyMs: Int = 0,
    val jitterMs: Int = 0,
    val sentCount: Int = 0,
    val receivedCount: Int = 0,
    val lostCount: Int = 0,
    val lossPercent: Float = 0f,
    val isRunning: Boolean = false,
    val phase: String = "就绪"
)

/**
 * 专业双轴动态走势采样点
 */
data class IosDualChartPoint(
    val elapsedMs: Long,
    val activeSessions: Int? = null,
    val pingLatencyMs: Int? = null,
    val cps: Int = 0,
    val hasPingSample: Boolean = false,
    val protocol: IosIpProtocol = IosIpProtocol.IPV4
) {
    val elapsedSec: Double get() = elapsedMs / 1000.0
}

/**
 * 日志级别
 */
enum class IosLogLevel { INFO, SUCCESS, WARN, ERROR, STAT }

/**
 * 实时诊断日志项
 */
data class IosLogLine(
    val timeEpochMs: Long = 0L,
    val level: IosLogLevel = IosLogLevel.INFO,
    val text: String
) {
    val timeText: String
        get() {
            val totalSec = if (timeEpochMs > 0) timeEpochMs / 1000 else 0
            val s = totalSec % 60
            val m = (totalSec / 60) % 60
            val h = (totalSec / 3600 + 8) % 24
            return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        }
}

/**
 * DNS 寻址与多栈解析结果
 */
data class IosResolveResult(
    val host: String = "",
    val ipv4: List<String> = emptyList(),
    val ipv6: List<String> = emptyList(),
    val error: String? = null
)

/**
 * 智能诊断与 Bufferbloat 分析建议
 */
data class IosDiagnosticAdvice(
    val title: String,
    val level: IosLogLevel,
    val description: String
)
