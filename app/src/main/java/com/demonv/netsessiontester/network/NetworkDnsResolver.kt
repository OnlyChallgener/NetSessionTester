package com.demonv.netsessiontester.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

/**
 * 使用当前 activeNetwork 的本机 DNS 进行解析。
 *
 * 关键规则：
 * 1. 先使用 LinkProperties 中的 IPv4 DNS，再使用 IPv6 DNS。
 * 2. A / AAAA 都可以发送给 IPv4 DNS，DNS 服务器地址族不限制记录类型。
 * 3. 每个 UDP socket 绑定到本轮 Network，避免 VPN 断开后的旧网络结果污染。
 * 4. 直接 DNS 查询都失败时才回退到 Network.getAllByName()。
 */
data class LocalDnsResolution(
    val addresses: List<InetAddress>,
    val usedDnsServers: List<String> = emptyList()
)

object NetworkDnsResolver {
    private const val DNS_PORT = 53
    @Volatile private var installedContext: Context? = null

    fun install(context: Context) {
        installedContext = context.applicationContext
    }

    private fun requireContext(): Context =
        installedContext ?: error("NetworkDnsResolver 尚未初始化")

    fun activeNetwork(context: Context): Network? =
        context.getSystemService(ConnectivityManager::class.java)?.activeNetwork

    fun resolveBlocking(
        hostInput: String,
        network: Network? = activeNetwork(requireContext()),
        includeIpv4: Boolean = true,
        includeIpv6: Boolean = true,
        timeoutMs: Int = 1_200
    ): LocalDnsResolution = resolveBlocking(
        context = requireContext(),
        hostInput = hostInput,
        network = network,
        includeIpv4 = includeIpv4,
        includeIpv6 = includeIpv6,
        timeoutMs = timeoutMs
    )

    fun resolveAddressesBlocking(
        host: String,
        network: Network? = activeNetwork(requireContext()),
        includeIpv4: Boolean = true,
        includeIpv6: Boolean = true,
        timeoutMs: Int = 1_200
    ): List<InetAddress> = resolveBlocking(
        hostInput = host,
        network = network,
        includeIpv4 = includeIpv4,
        includeIpv6 = includeIpv6,
        timeoutMs = timeoutMs
    ).addresses

    fun orderedDnsServers(context: Context, network: Network? = activeNetwork(context)): List<InetAddress> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val servers = network?.let { cm.getLinkProperties(it) }?.dnsServers.orEmpty()
        return servers
            .distinctBy { it.hostAddress?.substringBefore('%').orEmpty().lowercase(Locale.ROOT) }
            .sortedWith(
                compareBy<InetAddress> {
                    when (it) {
                        is Inet4Address -> 0
                        is Inet6Address -> 1
                        else -> 2
                    }
                }.thenBy { it.hostAddress.orEmpty() }
            )
    }

    fun resolveBlocking(
        context: Context,
        hostInput: String,
        network: Network? = activeNetwork(context),
        includeIpv4: Boolean = true,
        includeIpv6: Boolean = true,
        timeoutMs: Int = 1_200
    ): LocalDnsResolution {
        val host = hostInput.trim().removePrefix("[").removeSuffix("]").trimEnd('.')
        require(host.isNotBlank()) { "目标为空" }

        parseLiteral(host)?.let { literal ->
            val accepted = (literal is Inet4Address && includeIpv4) || (literal is Inet6Address && includeIpv6)
            return LocalDnsResolution(if (accepted) listOf(literal) else emptyList())
        }

        val servers = orderedDnsServers(context, network)
        val addresses = mutableListOf<InetAddress>()
        val used = linkedSetOf<String>()

        if (includeIpv4) {
            queryAcrossServers(host, 1, servers, network, timeoutMs).let { result ->
                addresses += result.first
                result.second?.let(used::add)
            }
        }
        if (includeIpv6) {
            queryAcrossServers(host, 28, servers, network, timeoutMs).let { result ->
                addresses += result.first
                result.second?.let(used::add)
            }
        }

        val distinct = addresses.distinctBy { it.hostAddress?.substringBefore('%').orEmpty() }
        if (distinct.isNotEmpty()) return LocalDnsResolution(distinct, used.toList())

        val fallback = runCatching {
            val all = if (network != null) network.getAllByName(host).toList() else InetAddress.getAllByName(host).toList()
            all.filter {
                (includeIpv4 && it is Inet4Address) || (includeIpv6 && it is Inet6Address)
            }.distinctBy { it.hostAddress?.substringBefore('%').orEmpty() }
        }.getOrDefault(emptyList())
        return LocalDnsResolution(fallback, used.toList())
    }

    fun resolveAddressesBlocking(
        context: Context,
        host: String,
        network: Network? = activeNetwork(context),
        includeIpv4: Boolean = true,
        includeIpv6: Boolean = true,
        timeoutMs: Int = 1_200
    ): List<InetAddress> = resolveBlocking(
        context = context,
        hostInput = host,
        network = network,
        includeIpv4 = includeIpv4,
        includeIpv6 = includeIpv6,
        timeoutMs = timeoutMs
    ).addresses

    private fun queryAcrossServers(
        host: String,
        qType: Int,
        servers: List<InetAddress>,
        network: Network?,
        timeoutMs: Int
    ): Pair<List<InetAddress>, String?> {
        for (server in servers) {
            val answers = runCatching {
                queryServer(host, qType, server, network, timeoutMs)
            }.getOrDefault(emptyList())
            if (answers.isNotEmpty()) {
                return answers to server.hostAddress?.substringBefore('%')
            }
        }
        return emptyList<InetAddress>() to null
    }

    private fun queryServer(
        host: String,
        qType: Int,
        server: InetAddress,
        network: Network?,
        timeoutMs: Int
    ): List<InetAddress> {
        val txId = (System.nanoTime().toInt() and 0xFFFF)
        val query = buildQuery(host, txId, qType)
        DatagramSocket().use { socket ->
            network?.bindSocket(socket)
            socket.soTimeout = timeoutMs.coerceIn(400, 10_000)
            socket.send(DatagramPacket(query, query.size, server, DNS_PORT))
            val buffer = ByteArray(4_096)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            return parseAnswers(packet.data, packet.length, txId, qType)
        }
    }

    private fun buildQuery(host: String, txId: Int, qType: Int): ByteArray {
        val labels = host.split('.').filter { it.isNotBlank() }
        val size = 12 + labels.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 } + 1 + 4
        val out = ByteArray(size)
        out[0] = ((txId ushr 8) and 0xFF).toByte()
        out[1] = (txId and 0xFF).toByte()
        out[2] = 0x01 // recursion desired
        out[5] = 0x01 // one question
        var pos = 12
        labels.forEach { label ->
            val bytes = label.toByteArray(Charsets.UTF_8)
            require(bytes.size in 1..63) { "域名标签无效" }
            out[pos++] = bytes.size.toByte()
            bytes.copyInto(out, destinationOffset = pos)
            pos += bytes.size
        }
        out[pos++] = 0
        out[pos++] = ((qType ushr 8) and 0xFF).toByte()
        out[pos++] = (qType and 0xFF).toByte()
        out[pos++] = 0
        out[pos] = 1 // IN
        return out
    }

    private fun parseAnswers(data: ByteArray, length: Int, txId: Int, qType: Int): List<InetAddress> {
        if (length < 12) return emptyList()
        val responseId = u16(data, 0)
        if (responseId != txId) return emptyList()
        val flags = u16(data, 2)
        if ((flags and 0x000F) != 0) return emptyList()
        val qdCount = u16(data, 4)
        val anCount = u16(data, 6)
        var pos = 12
        repeat(qdCount) {
            pos = skipName(data, length, pos)
            if (pos < 0 || pos + 4 > length) return emptyList()
            pos += 4
        }

        val answers = mutableListOf<InetAddress>()
        repeat(anCount) {
            pos = skipName(data, length, pos)
            if (pos < 0 || pos + 10 > length) return@repeat
            val type = u16(data, pos)
            val clazz = u16(data, pos + 2)
            val rdLength = u16(data, pos + 8)
            pos += 10
            if (pos + rdLength > length) return@repeat
            if (clazz == 1 && type == qType) {
                when {
                    qType == 1 && rdLength == 4 -> runCatching {
                        answers += InetAddress.getByAddress(data.copyOfRange(pos, pos + 4))
                    }
                    qType == 28 && rdLength == 16 -> runCatching {
                        answers += InetAddress.getByAddress(data.copyOfRange(pos, pos + 16))
                    }
                }
            }
            pos += rdLength
        }
        return answers.distinctBy { it.hostAddress?.substringBefore('%').orEmpty() }
    }

    private fun skipName(data: ByteArray, length: Int, start: Int): Int {
        var pos = start
        var steps = 0
        while (pos in 0 until length && steps++ < 128) {
            val value = data[pos].toInt() and 0xFF
            when {
                value == 0 -> return pos + 1
                (value and 0xC0) == 0xC0 -> return if (pos + 1 < length) pos + 2 else -1
                value in 1..63 -> {
                    pos += 1 + value
                    if (pos > length) return -1
                }
                else -> return -1
            }
        }
        return -1
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun parseLiteral(value: String): InetAddress? {
        val ipv4 = Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")
        val ipv6 = value.contains(':') && Regex("""^[0-9A-Fa-f:.%]+$""").matches(value)
        if (!ipv4.matches(value) && !ipv6) return null
        return runCatching { InetAddress.getByName(value) }.getOrNull()
    }
}
