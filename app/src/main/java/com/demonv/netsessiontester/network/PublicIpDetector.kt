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
    suspend fun detect(): PublicIpResult = withContext(Dispatchers.IO) {
        val v4 = runCatching { fetchIp("https://api.ipify.org?format=json") }
        val v6 = runCatching { fetchIp("https://api6.ipify.org?format=json") }
        PublicIpResult(
            ipv4 = v4.getOrDefault("不可用"),
            ipv6 = v6.getOrDefault("不可用"),
            ipv4Error = v4.exceptionOrNull()?.message,
            ipv6Error = v6.exceptionOrNull()?.message
        )
    }

    private fun fetchIp(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 3500
            setRequestProperty("User-Agent", "NetSessionTester")
        }
        return conn.inputStream.bufferedReader().use { reader ->
            val body = reader.readText()
            JSONObject(body).optString("ip", "不可用")
        }
    }
}
