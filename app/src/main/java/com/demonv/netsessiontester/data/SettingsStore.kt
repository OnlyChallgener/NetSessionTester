package com.demonv.netsessiontester.data

import android.content.Context
import com.demonv.netsessiontester.model.TestMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedSettings(
    val host: String = "",
    val port: String = "80",
    val mode: TestMode = TestMode.IPV4_THEN_IPV6,
    val batchSize: String = "100",
    val intervalMs: String = "500",
    val timeoutMs: String = "3000",
    val successLimit: String = "65535",
    val failureLimit: String = "200",
    val keepConnections: Boolean = true,
    val maskPrivacy: Boolean = false
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("net_session_settings", Context.MODE_PRIVATE)

    suspend fun load(): SavedSettings = withContext(Dispatchers.IO) {
        SavedSettings(
            host = prefs.getString(KEY_HOST, "") ?: "",
            port = prefs.getString(KEY_PORT, "80") ?: "80",
            mode = runCatching { TestMode.valueOf(prefs.getString(KEY_MODE, TestMode.IPV4_THEN_IPV6.name) ?: TestMode.IPV4_THEN_IPV6.name) }
                .getOrDefault(TestMode.IPV4_THEN_IPV6),
            batchSize = prefs.getString(KEY_BATCH_SIZE, "100") ?: "100",
            intervalMs = prefs.getString(KEY_INTERVAL_MS, "500") ?: "500",
            timeoutMs = prefs.getString(KEY_TIMEOUT_MS, "3000") ?: "3000",
            successLimit = prefs.getString(KEY_SUCCESS_LIMIT, "65535") ?: "65535",
            failureLimit = prefs.getString(KEY_FAILURE_LIMIT, "200") ?: "200",
            keepConnections = prefs.getBoolean(KEY_KEEP_CONNECTIONS, true),
            maskPrivacy = prefs.getBoolean(KEY_MASK_PRIVACY, false)
        )
    }

    suspend fun save(settings: SavedSettings) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_HOST, settings.host)
            .putString(KEY_PORT, settings.port)
            .putString(KEY_MODE, settings.mode.name)
            .putString(KEY_BATCH_SIZE, settings.batchSize)
            .putString(KEY_INTERVAL_MS, settings.intervalMs)
            .putString(KEY_TIMEOUT_MS, settings.timeoutMs)
            .putString(KEY_SUCCESS_LIMIT, settings.successLimit)
            .putString(KEY_FAILURE_LIMIT, settings.failureLimit)
            .putBoolean(KEY_KEEP_CONNECTIONS, settings.keepConnections)
            .putBoolean(KEY_MASK_PRIVACY, settings.maskPrivacy)
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
    }
}
