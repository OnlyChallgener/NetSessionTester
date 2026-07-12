package com.demonv.netsessiontester.network

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * 公网地址轻量检测结果。
 *
 * “待检测”表示本轮没有得到有效结果，避免 VPN/网络切换后继续展示旧出口地址。
 */
data class PublicIpResult(
    val ipv4: String = "待检测",
    val ipv6: String = "待检测",
    val ipv4Error: String? = null,
    val ipv6Error: String? = null
)

object PublicIpDetector {
    private const val CACHE_TTL_MS = 10_000L
    private const val FAMILY_TIMEOUT_MS = 3_500L
    private const val CONNECT_TIMEOUT_MS = 1_200
    private const val READ_TIMEOUT_MS = 1_800

    private val ipv4Sources = listOf(
        "https://api.ipify.org?format=json",
        "https://ipv4.icanhazip.com",
        "https://checkip.amazonaws.com"
    )

    private val ipv6Sources = listOf(
        "https://api6.ipify.org?format=json",
        "https://ipv6.icanhazip.com"
    )

    private data class CacheEntry(
        val networkKey: String,
        val result: PublicIpResult,
        val checkedAtMs: Long
    )

    @Volatile
    private var cacheEntry: CacheEntry? = null

    fun invalidate() {
        cacheEntry = null
    }

    /**
     * IPv4 与 IPv6 并发检测；同一地址族的多个源也并发竞争，取第一个有效结果。
     * 整轮通常在 1~3 秒内结束，最坏不超过 FAMILY_TIMEOUT_MS 左右。
     */
    suspend fun detect(network: Network? = null): PublicIpResult = withContext(Dispatchers.IO) {
        val networkKey = network?.toString() ?: "default"
        val now = System.currentTimeMillis()
        cacheEntry?.takeIf {
            it.networkKey == networkKey && now - it.checkedAtMs < CACHE_TTL_MS
        }?.let { return@withContext it.result }

        val result = coroutineScope {
            val v4Deferred = async { detectFromSources(ipv4Sources, network, "IPv4") }
            val v6Deferred = async { detectFromSources(ipv6Sources, network, "IPv6") }
            val v4 = v4Deferred.await()
            val v6 = v6Deferred.await()
            PublicIpResult(
                ipv4 = v4.getOrDefault("待检测"),
                ipv6 = v6.getOrDefault("待检测"),
                ipv4Error = v4.exceptionOrNull()?.message,
                ipv6Error = v6.exceptionOrNull()?.message
            )
        }
        cacheEntry = CacheEntry(networkKey, result, now)
        result
    }

    private suspend fun detectFromSources(
        sources: List<String>,
        network: Network?,
        familyLabel: String
    ): Result<String> {
        return withTimeoutOrNull(FAMILY_TIMEOUT_MS) {
            supervisorScope {
                val results = Channel<Result<String>>(capacity = sources.size)
                val jobs = sources.map { url ->
                    launch(Dispatchers.IO) {
                        results.trySend(runCatching { fetchIp(url, network) })
                    }
                }

                var lastError: Throwable? = null
                repeat(sources.size) {
                    val result = results.receive()
                    val ip = result.getOrNull()?.trim().orEmpty()
                    if (ip.isUsablePublicIpText()) {
                        jobs.forEach { it.cancel() }
                        results.close()
                        return@supervisorScope Result.success(ip)
                    }
                    lastError = result.exceptionOrNull() ?: lastError
                }
                Result.failure(lastError ?: IllegalStateException("$familyLabel 检测源均不可用"))
            }
        } ?: Result.failure(SocketTimeoutException("$familyLabel 公网地址检测超时"))
    }

    private fun String.isUsablePublicIpText(): Boolean {
        val value = trim()
        return value.isNotBlank() && value != "不可用" && value != "待检测" &&
            (value.contains(':') || value.split('.').let { parts ->
                parts.size == 4 && parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
            })
    }

    private fun fetchIp(url: String, network: Network?): String {
        val target = URL(url)
        val rawConnection = if (network != null) network.openConnection(target) else target.openConnection()
        val conn = (rawConnection as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "NetSessionTester")
        }

        return try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { reader -> reader.readText().trim() }
            if (body.startsWith("{")) {
                JSONObject(body).optString("ip", "待检测").trim()
            } else {
                body.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "待检测"
            }
        } finally {
            conn.disconnect()
        }
    }
}
