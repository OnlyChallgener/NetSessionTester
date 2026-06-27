package com.demonv.netsessiontester.data

import android.content.Context
import com.demonv.netsessiontester.model.TestMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedSettings(
    val host: String = "www.baidu.com",
    val port: String = "80",
    val mode: TestMode = TestMode.IPV4_THEN_IPV6,
    val batchSize: String = "200",
    val intervalMs: String = "100",
    val timeoutMs: String = "1200",
    val successLimit: String = "65535",
    val failureLimit: String = "600",
    val keepConnections: Boolean = true,
    val maskPrivacy: Boolean = false,
    val historyLimit: String = "30",
    val pingEnabled: Boolean = true,
    val pingTarget: String = "223.5.5.5",
    val pingIntervalMs: String = "1000",
    val pingCount: String = "无限",
    val pingTimeoutMs: String = "1000",
    val pingProtocol: String = "AUTO",
    val natRfc5780Servers: String = "stunserver2025.stunprotocol.org:3478",
    val natRfc3489Servers: String = "stun.voip.aebc.com:3478"
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("net_session_settings_v6", Context.MODE_PRIVATE)

    suspend fun load(): SavedSettings = withContext(Dispatchers.IO) {
        val savedBatchSize = prefs.getString(KEY_BATCH_SIZE, "200") ?: "200"
        val migratedFix3 = prefs.getBoolean(KEY_PERF_FIX3_MIGRATED, false)
        val migratedV86Failure = prefs.getBoolean(KEY_V86_FAILURE_MIGRATED, false)
        val performanceBatchSize = when {
            savedBatchSize.isBlank() -> "200"
            savedBatchSize.trim() == "128" -> "200"
            !migratedFix3 && savedBatchSize.trim() == "1000" -> "200"
            else -> savedBatchSize
        }
        val savedFailureLimit = prefs.getString(KEY_FAILURE_LIMIT, "600") ?: "600"
        val performanceFailureLimit = when {
            savedFailureLimit.isBlank() -> "600"
            !migratedV86Failure && savedFailureLimit.trim() == "1200" -> "600"
            else -> savedFailureLimit
        }
        if (!migratedFix3 || !migratedV86Failure) {
            prefs.edit()
                .putBoolean(KEY_PERF_FIX3_MIGRATED, true)
                .putBoolean(KEY_V86_FAILURE_MIGRATED, true)
                .putString(KEY_BATCH_SIZE, performanceBatchSize)
                .putString(KEY_FAILURE_LIMIT, performanceFailureLimit)
                .apply()
        }
        SavedSettings(
            host = prefs.getString(KEY_HOST, "www.baidu.com") ?: "www.baidu.com",
            port = prefs.getString(KEY_PORT, "80") ?: "80",
            mode = runCatching { TestMode.valueOf(prefs.getString(KEY_MODE, TestMode.IPV4_THEN_IPV6.name) ?: TestMode.IPV4_THEN_IPV6.name) }
                .getOrDefault(TestMode.IPV4_THEN_IPV6),
            batchSize = performanceBatchSize,
            intervalMs = prefs.getString(KEY_INTERVAL_MS, "100") ?: "100",
            timeoutMs = prefs.getString(KEY_TIMEOUT_MS, "1200") ?: "1200",
            successLimit = prefs.getString(KEY_SUCCESS_LIMIT, "65535") ?: "65535",
            failureLimit = performanceFailureLimit,
            keepConnections = prefs.getBoolean(KEY_KEEP_CONNECTIONS, true),
            maskPrivacy = prefs.getBoolean(KEY_MASK_PRIVACY, false),
            historyLimit = prefs.getString(KEY_HISTORY_LIMIT, "30") ?: "30",
            pingEnabled = prefs.getBoolean(KEY_PING_ENABLED, true),
            pingTarget = prefs.getString(KEY_PING_TARGET, "223.5.5.5") ?: "223.5.5.5",
            pingIntervalMs = prefs.getString(KEY_PING_INTERVAL_MS, "1000") ?: "1000",
            pingCount = prefs.getString(KEY_PING_COUNT, "无限") ?: "无限",
            pingTimeoutMs = prefs.getString(KEY_PING_TIMEOUT_MS, "1000") ?: "1000",
            pingProtocol = prefs.getString(KEY_PING_PROTOCOL, "AUTO") ?: "AUTO",
            natRfc5780Servers = prefs.getString(KEY_NAT_RFC5780_SERVERS, "stunserver2025.stunprotocol.org:3478") ?: "stunserver2025.stunprotocol.org:3478",
            natRfc3489Servers = prefs.getString(KEY_NAT_RFC3489_SERVERS, "stun.voip.aebc.com:3478") ?: "stun.voip.aebc.com:3478"
        )
    }

    suspend fun save(settings: SavedSettings) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_HOST, settings.host.ifBlank { "www.baidu.com" })
            .putString(KEY_PORT, settings.port)
            .putString(KEY_MODE, settings.mode.name)
            .putString(KEY_BATCH_SIZE, settings.batchSize)
            .putString(KEY_INTERVAL_MS, settings.intervalMs)
            .putString(KEY_TIMEOUT_MS, settings.timeoutMs)
            .putString(KEY_SUCCESS_LIMIT, settings.successLimit)
            .putString(KEY_FAILURE_LIMIT, settings.failureLimit)
            .putBoolean(KEY_KEEP_CONNECTIONS, settings.keepConnections)
            .putBoolean(KEY_MASK_PRIVACY, settings.maskPrivacy)
            .putString(KEY_HISTORY_LIMIT, settings.historyLimit)
            .putBoolean(KEY_PING_ENABLED, settings.pingEnabled)
            .putString(KEY_PING_TARGET, settings.pingTarget.ifBlank { "223.5.5.5" })
            .putString(KEY_PING_INTERVAL_MS, settings.pingIntervalMs)
            .putString(KEY_PING_COUNT, settings.pingCount)
            .putString(KEY_PING_TIMEOUT_MS, settings.pingTimeoutMs)
            .putString(KEY_PING_PROTOCOL, settings.pingProtocol)
            .putString(KEY_NAT_RFC5780_SERVERS, settings.natRfc5780Servers)
            .putString(KEY_NAT_RFC3489_SERVERS, settings.natRfc3489Servers)
            .apply()
    }

    companion object {
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_MODE = "mode"
        private const val KEY_BATCH_SIZE = "batch_size"
        private const val KEY_INTERVAL_MS = "interval_ms"
        private const val KEY_TIMEOUT_MS = "timeout_ms"
        private const val KEY_SUCCESS_LIMIT = "success_limit"
        private const val KEY_FAILURE_LIMIT = "failure_limit"
        private const val KEY_KEEP_CONNECTIONS = "keep_connections"
        private const val KEY_MASK_PRIVACY = "mask_privacy"
        private const val KEY_HISTORY_LIMIT = "history_limit"
        private const val KEY_PING_ENABLED = "ping_enabled"
        private const val KEY_PING_TARGET = "ping_target"
        private const val KEY_PING_INTERVAL_MS = "ping_interval_ms"
        private const val KEY_PING_COUNT = "ping_count"
        private const val KEY_PING_TIMEOUT_MS = "ping_timeout_ms"
        private const val KEY_PING_PROTOCOL = "ping_protocol"
        private const val KEY_NAT_RFC5780_SERVERS = "nat_rfc5780_servers"
        private const val KEY_NAT_RFC3489_SERVERS = "nat_rfc3489_servers"
        private const val KEY_PERF_FIX3_MIGRATED = "perf_fix3_migrated"
        private const val KEY_V86_FAILURE_MIGRATED = "v86_failure_migrated"
    }
}
