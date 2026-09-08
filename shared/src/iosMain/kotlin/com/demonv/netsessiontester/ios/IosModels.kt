package com.demonv.netsessiontester.ios

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.timeIntervalSince1970

/**
 * iOS 测试模式枚举
 */
enum class IosAppMode(val label: String) {
    SESSION_HOLD("并发压测"),
    PING_STANDALONE("独立 Ping"),
    UNDERLOAD_PING("联动诊断 (Bufferbloat)")
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
 * 测试会话参数配置 (严格符合 iOS 沙盒与 HIG 规范)
 */
data class IosSessionConfig(
    val host: String = "www.baidu.com",
    val port: Int = 80,
    val mode: IosTestMode = IosTestMode.IPV4_ONLY,
    val batchSize: Int = 50,          // 单批并发建立数 (CPS 速率)
    val intervalMs: Long = 50L,       // 建连步进间隔 (ms)
    val timeoutMs: Int = 1500,        // 握手超时 (ms)
    val successLimit: Int = 500,      // 目标保持并发会话数 (iOS 安全上限 100~2000)
    val failureLimit: Int = 200,      // 失败容忍上限
    val keepConnectionsAfterStop: Boolean = true // 停止测试后保持连接
) {
    fun normalized(): IosSessionConfig {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        return copy(
            host = cleanHost,
            port = port.coerceIn(1, 65535),
            batchSize = batchSize.coerceIn(1, 5000),
            intervalMs = intervalMs.coerceIn(20L, 1000L),
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
    val currentLatencyMs: Int = 0,
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
    val elapsedSec: Int,
    val activeSessions: Int = 0,
    val pingLatencyMs: Int = 0,
    val cps: Int = 0
)

/**
 * 日志级别
 */
enum class IosLogLevel { INFO, SUCCESS, WARN, ERROR, STAT }

/**
 * 实时诊断日志项
 */
data class IosLogLine(
    val timeEpochMs: Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong(),
    val level: IosLogLevel = IosLogLevel.INFO,
    val text: String
) {
    val timeText: String
        get() {
            val date = NSDate(timeIntervalSince1970 = timeEpochMs / 1000.0)
            val formatter = NSDateFormatter().apply {
                dateFormat = "HH:mm:ss"
                timeZone = NSTimeZone.systemTimeZone
            }
            return formatter.stringFromDate(date)
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
