package com.demonv.netsessiontester.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PublicIpResult(
    val ipv4: String = "检测中",
    val ipv6: String = "检测中",
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

    suspend fun detect(): PublicIpResult = withContext(Dispatchers.IO) {
        val v4 = detectFromSources(ipv4Sources)
        val v6 = detectFromSources(ipv6Sources)
        PublicIpResult(
            ipv4 = v4.getOrDefault("不可用"),
            ipv6 = v6.getOrDefault("不可用"),
            ipv4Error = v4.exceptionOrNull()?.message,
            ipv6Error = v6.exceptionOrNull()?.message
        )
    }

    private fun detectFromSources(sources: List<String>): Result<String> {
        var lastError: Throwable? = null
        for (url in sources) {
            val result = runCatching { fetchIp(url) }
            val ip = result.getOrNull()
            if (!ip.isNullOrBlank() && ip != "不可用") {
                return Result.success(ip)
            }
            lastError = result.exceptionOrNull()
        }
        return Result.failure(lastError ?: IllegalStateException("所有检测源均不可用"))
    }

    private fun fetchIp(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
            setRequestProperty("User-Agent", "NetSessionTester")
        }

        val body = conn.inputStream.bufferedReader().use { reader ->
            reader.readText().trim()
        }

        if (body.startsWith("{")) {
            return JSONObject(body).optString("ip", "不可用").trim()
        }

        return body
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: "不可用"
    }
}
