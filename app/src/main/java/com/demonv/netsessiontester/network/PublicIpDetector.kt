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
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/**
 * 公网地址轻量检测结果。
 *
 * “待检测”表示本轮没有取得有效公网地址。
 */
data class PublicIpResult(
    val ipv4: String = "待检测",
    val ipv6: String = "待检测",
    val ipv4Error: String? = null,
    val ipv6Error: String? = null
)

object PublicIpDetector {
    private const val SUCCESS_CACHE_TTL_MS = 10_000L
    private const val FAILURE_CACHE_TTL_MS = 1_500L
    private const val FAMILY_TIMEOUT_MS = 3_500L
    private const val CONNECT_TIMEOUT_MS = 1_200
    private const val READ_TIMEOUT_MS = 1_800
    private const val MAX_BODY_LENGTH = 4_096

    /*
     * 国内/国内访问相对友好的检测源放在前面；同地址族仍并发竞争，
     * 因此单个接口异常不会拖住整轮检测。
     */
    private val ipv4Sources = listOf(
        "https://4.ipw.cn/",
        "https://api-ipv4.ip.sb/ip",
        "https://api.ipify.org?format=json",
        "https://ipv4.icanhazip.com",
        "https://checkip.amazonaws.com"
    )

    private val ipv6Sources = listOf(
        "https://6.ipw.cn/",
        "https://api-ipv6.ip.sb/ip",
        "https://api6.ipify.org?format=json",
        "https://ipv6.icanhazip.com",
        "https://v6.ident.me"
    )

    private data class CacheEntry(
        val networkKey: String,
        val result: PublicIpResult,
        val checkedAtMs: Long,
        val ttlMs: Long
    )

    @Volatile
    private var cacheEntry: CacheEntry? = null

    fun invalidate() {
        cacheEntry = null
    }

    /**
     * IPv4 与 IPv6 并发检测；同一地址族的多个源也并发竞争，
     * 第一个返回有效地址的源获胜。
     */
    suspend fun detect(network: Network? = null): PublicIpResult = withContext(Dispatchers.IO) {
        val networkKey = network?.toString() ?: "default"
        val now = System.currentTimeMillis()

        cacheEntry?.takeIf {
            it.networkKey == networkKey && now - it.checkedAtMs < it.ttlMs
        }?.let { return@withContext it.result }

        val result = coroutineScope {
            val v4Deferred = async {
                detectFromSources(
                    sources = ipv4Sources,
                    network = network,
                    familyLabel = "IPv4",
                    expectIpv6 = false
                )
            }
            val v6Deferred = async {
                detectFromSources(
                    sources = ipv6Sources,
                    network = network,
                    familyLabel = "IPv6",
                    expectIpv6 = true
                )
            }

            val v4 = v4Deferred.await()
            val v6 = v6Deferred.await()

            PublicIpResult(
                ipv4 = v4.getOrDefault("待检测"),
                ipv6 = v6.getOrDefault("待检测"),
                ipv4Error = v4.exceptionOrNull()?.message,
                ipv6Error = v6.exceptionOrNull()?.message
            )
        }

        val hasAnyValidResult =
            result.ipv4.isUsablePublicIpText(expectIpv6 = false) ||
                result.ipv6.isUsablePublicIpText(expectIpv6 = true)

        cacheEntry = CacheEntry(
            networkKey = networkKey,
            result = result,
            checkedAtMs = now,
            ttlMs = if (hasAnyValidResult) SUCCESS_CACHE_TTL_MS else FAILURE_CACHE_TTL_MS
        )
        result
    }

    private suspend fun detectFromSources(
        sources: List<String>,
        network: Network?,
        familyLabel: String,
        expectIpv6: Boolean
    ): Result<String> {
        return withTimeoutOrNull(FAMILY_TIMEOUT_MS) {
            supervisorScope {
                val results = Channel<Result<String>>(capacity = sources.size)
                val jobs = sources.map { url ->
                    launch(Dispatchers.IO) {
                        results.trySend(
                            try {
                                Result.success(fetchIp(
                                    url = url,
                                    network = network,
                                    expectIpv6 = expectIpv6
                                ))
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Error) {
                                throw error
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }
                        )
                    }
                }

                var lastError: Throwable? = null
                repeat(sources.size) {
                    val result = results.receive()
                    val ip = result.getOrNull()?.trim().orEmpty()
                    if (ip.isUsablePublicIpText(expectIpv6)) {
                        jobs.forEach { it.cancel() }
                        results.close()
                        return@supervisorScope Result.success(ip.substringBefore('%'))
                    }
                    lastError = result.exceptionOrNull() ?: lastError
                }

                Result.failure(
                    lastError ?: IllegalStateException("$familyLabel 检测源均不可用")
                )
            }
        } ?: Result.failure(SocketTimeoutException("$familyLabel 公网地址检测超时"))
    }

    private fun String.isUsablePublicIpText(expectIpv6: Boolean): Boolean {
        val value = trim().substringBefore('%')
        if (value.isBlank() || value == "不可用" || value == "待检测") return false

        val address = parseLiteralAddress(value) ?: return false
        if (expectIpv6 && address !is Inet6Address) return false
        if (!expectIpv6 && address !is Inet4Address) return false

        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }

        if (address is Inet6Address) {
            val firstByte = address.address.firstOrNull()?.toInt()?.and(0xFF) ?: return false
            if ((firstByte and 0xFE) == 0xFC) return false // fc00::/7 ULA
        }
        return true
    }

    /**
     * 只接受文本形式的 IP 字面量，避免把异常响应再次当域名解析。
     */
    private fun parseLiteralAddress(value: String): InetAddress? {
        val candidate = value.trim().removePrefix("[").removeSuffix("]")
        val looksIpv6 = candidate.contains(':')
        val looksIpv4 = candidate.count { it == '.' } == 3
        if (!looksIpv6 && !looksIpv4) return null
        return runCatching { InetAddress.getByName(candidate) }.getOrNull()
    }

    private fun fetchIp(
        url: String,
        network: Network?,
        expectIpv6: Boolean
    ): String {
        val target = URL(url)
        val rawConnection = if (network != null) {
            network.openConnection(target)
        } else {
            target.openConnection()
        }

        val conn = (rawConnection as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "text/plain, application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", "NetSessionTester/Android")
        }

        return try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code: $url")

            val body = conn.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                if (text.length > MAX_BODY_LENGTH) text.take(MAX_BODY_LENGTH) else text
            }.trim()

            extractIpFromBody(body, expectIpv6)
                ?: error("响应中没有有效${if (expectIpv6) "IPv6" else "IPv4"}地址: $url")
        } finally {
            conn.disconnect()
        }
    }

    private fun extractIpFromBody(body: String, expectIpv6: Boolean): String? {
        if (body.isBlank()) return null

        val candidates = LinkedHashSet<String>()

        if (body.startsWith("{")) {
            runCatching {
                val json = JSONObject(body)
                listOf("ip", "query", "address", "client_ip").forEach { key ->
                    json.optString(key).takeIf { it.isNotBlank() }?.let(candidates::add)
                }
                val data = json.optJSONObject("data")
                data?.optString("client_ip")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(candidates::add)
            }
        }

        body.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(8)
            .forEach { line ->
                candidates += line
                line.split(' ', '\t', ',', ';', '"', '\'', '<', '>', '(', ')')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach(candidates::add)
            }

        return candidates.firstOrNull { it.isUsablePublicIpText(expectIpv6) }
            ?.trim()
            ?.substringBefore('%')
            ?.removePrefix("[")
            ?.removeSuffix("]")
    }
}
