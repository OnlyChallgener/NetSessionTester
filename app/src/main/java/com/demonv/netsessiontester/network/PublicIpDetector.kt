package com.demonv.netsessiontester.network

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
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
    private val ipv4Sources = listOf(
        "https://api.ipify.org?format=json",
        "https://ipv4.icanhazip.com",
        "https://checkip.amazonaws.com"
    )

    private val ipv6Sources = listOf(
        "https://api6.ipify.org?format=json",
        "https://ipv6.icanhazip.com"
    )

    /**
     * 将 HTTP 请求明确绑定到调用时的 activeNetwork。
     * 这样 VPN 断开后，旧 VPN Network 的 DNS/连接结果不会继续污染当前网络信息。
     */
    suspend fun detect(network: Network? = null): PublicIpResult = withContext(Dispatchers.IO) {
        val v4 = detectFromSources(ipv4Sources, network)
        val v6 = detectFromSources(ipv6Sources, network)
        PublicIpResult(
            ipv4 = v4.getOrDefault("待检测"),
            ipv6 = v6.getOrDefault("待检测"),
            ipv4Error = v4.exceptionOrNull()?.message,
            ipv6Error = v6.exceptionOrNull()?.message
        )
    }

    private fun detectFromSources(sources: List<String>, network: Network?): Result<String> {
        var lastError: Throwable? = null
        for (url in sources) {
            val result = runCatching { fetchIp(url, network) }
            val ip = result.getOrNull()?.trim().orEmpty()
            if (ip.isNotBlank() && ip != "不可用" && ip != "待检测") {
                return Result.success(ip)
            }
            lastError = result.exceptionOrNull()
        }
        return Result.failure(lastError ?: IllegalStateException("所有检测源均不可用"))
    }

    private fun fetchIp(url: String, network: Network?): String {
        val target = URL(url)
        val rawConnection = if (network != null) network.openConnection(target) else target.openConnection()
        val conn = (rawConnection as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 3500
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
