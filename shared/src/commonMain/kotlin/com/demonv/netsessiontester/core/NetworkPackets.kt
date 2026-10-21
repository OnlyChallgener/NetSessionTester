package com.demonv.netsessiontester.core

/** Small, dependency-free codecs shared by native network diagnostic transports. */
object NetworkPackets {
    const val DNS_A = 1
    const val DNS_AAAA = 28

    data class DnsMessage(
        val id: Int,
        val responseCode: Int,
        val truncated: Boolean,
        val addresses: List<String>
    )

    data class SocketAddress(val host: String, val port: Int)

    data class StunResponse(
        val mapped: SocketAddress,
        val other: SocketAddress?,
        val responseOrigin: SocketAddress?
    )

    data class IcmpReply(
        val echoReply: Boolean,
        val timeExceeded: Boolean,
        val packetTooBig: Boolean,
        val nextHopMtu: Int?,
        val destinationUnreachable: Boolean = false,
        val code: Int? = null
    )

    fun dnsQuery(host: String, type: Int, id: Int): ByteArray {
        require(type == DNS_A || type == DNS_AAAA)
        val labels = host.trim().trimEnd('.').split('.').filter { it.isNotEmpty() }
        require(labels.isNotEmpty() && labels.all { it.encodeToByteArray().size in 1..63 })
        val size = 12 + labels.sumOf { 1 + it.encodeToByteArray().size } + 1 + 4
        val out = ByteArray(size)
        put16(out, 0, id)
        put16(out, 2, 0x0100) // RD
        put16(out, 4, 1)
        var pos = 12
        labels.forEach { label ->
            val bytes = label.encodeToByteArray()
            out[pos++] = bytes.size.toByte()
            bytes.copyInto(out, pos)
            pos += bytes.size
        }
        out[pos++] = 0
        put16(out, pos, type)
        put16(out, pos + 2, 1)
        return out
    }

    fun parseDnsResponse(data: ByteArray, expectedId: Int): DnsMessage? {
        if (data.size < 12 || u16(data, 0) != (expectedId and 0xffff)) return null
        val flags = u16(data, 2)
        if (flags and 0x8000 == 0) return null
        val questions = u16(data, 4)
        val answers = u16(data, 6)
        var pos = 12
        repeat(questions) {
            pos = skipDnsName(data, pos) ?: return null
            if (pos + 4 > data.size) return null
            pos += 4
        }
        val addresses = mutableListOf<String>()
        repeat(answers) {
            pos = skipDnsName(data, pos) ?: return null
            if (pos + 10 > data.size) return null
            val type = u16(data, pos)
            val rdLength = u16(data, pos + 8)
            pos += 10
            if (pos + rdLength > data.size) return null
            when {
                type == DNS_A && rdLength == 4 -> addresses += ipv4(data, pos)
                type == DNS_AAAA && rdLength == 16 -> addresses += ipv6(data, pos)
            }
            pos += rdLength
        }
        return DnsMessage(expectedId and 0xffff, flags and 0x0f, flags and 0x0200 != 0, addresses.distinct())
    }

    fun stunBindingRequest(transactionId: ByteArray, changeIp: Boolean = false, changePort: Boolean = false): ByteArray {
        require(transactionId.size == 12)
        val hasChange = changeIp || changePort
        val out = ByteArray(if (hasChange) 28 else 20)
        put16(out, 0, 0x0001)
        put16(out, 2, if (hasChange) 8 else 0)
        put32(out, 4, 0x2112A442)
        transactionId.copyInto(out, 8)
        if (hasChange) {
            put16(out, 20, 0x0003)
            put16(out, 22, 4)
            var flags = 0
            if (changeIp) flags = flags or 0x04
            if (changePort) flags = flags or 0x02
            put32(out, 24, flags)
        }
        return out
    }

    fun parseStunResponse(data: ByteArray, transactionId: ByteArray): StunResponse? {
        if (data.size < 20 || transactionId.size != 12 || u16(data, 0) != 0x0101 || u32(data, 4) != 0x2112A442) return null
        for (i in transactionId.indices) if (data[8 + i] != transactionId[i]) return null
        val messageEnd = 20 + u16(data, 2)
        if (messageEnd > data.size) return null
        var mapped: SocketAddress? = null
        var other: SocketAddress? = null
        var origin: SocketAddress? = null
        var pos = 20
        while (pos + 4 <= messageEnd) {
            val type = u16(data, pos)
            val length = u16(data, pos + 2)
            val value = pos + 4
            if (value + length > messageEnd) return null
            val address = parseStunAddress(data, value, length, type, transactionId)
            when (type) {
                0x0020 -> if (address != null) mapped = address
                0x0001 -> if (mapped == null) mapped = address
                0x802c, 0x0005 -> if (other == null) other = address
                0x802b -> origin = address
            }
            pos = value + ((length + 3) / 4) * 4
        }
        return mapped?.let { StunResponse(it, other, origin) }
    }

    /** [icmpSize] includes the eight-byte ICMP header, but excludes the IP header. */
    fun icmpEchoRequest(sequence: Int, icmpSize: Int, ipv6: Boolean): ByteArray {
        require(icmpSize >= 8)
        val out = ByteArray(icmpSize)
        out[0] = (if (ipv6) 128 else 8).toByte()
        put16(out, 4, 0x4e53) // "NS"; Darwin ping sockets may replace it.
        put16(out, 6, sequence)
        for (i in 8 until out.size) out[i] = ((i + sequence) and 0xff).toByte()
        if (!ipv6) put16(out, 2, internetChecksum(out))
        return out
    }

    fun parseIcmp(data: ByteArray, ipv6: Boolean, expectedSequence: Int): IcmpReply? {
        val offset = outerIcmpOffset(data, ipv6) ?: return null
        if (offset + 8 > data.size) return null
        val type = data[offset].toInt() and 0xff
        val code = data[offset + 1].toInt() and 0xff
        val echoType = if (ipv6) 129 else 0
        if (type == echoType && u16(data, offset + 6) == (expectedSequence and 0xffff)) {
            return IcmpReply(echoReply = true, timeExceeded = false, packetTooBig = false, nextHopMtu = null)
        }
        val timeExceededType = if (ipv6) 3 else 11
        val tooBig = if (ipv6) type == 2 else type == 3 && code == 4
        val unreachable = if (ipv6) type == 1 else type == 3 && code != 4
        if (type != timeExceededType && !tooBig && !unreachable) return null
        if (!embeddedSequenceMatches(data, offset + 8, ipv6, expectedSequence)) return null
        val mtu = when {
            ipv6 && tooBig && offset + 8 <= data.size -> u32(data, offset + 4).takeIf { it > 0 }
            !ipv6 && tooBig && offset + 8 <= data.size -> u16(data, offset + 6).takeIf { it > 0 }
            else -> null
        }
        return IcmpReply(false, type == timeExceededType, tooBig, mtu, unreachable, code)
    }

    private fun skipDnsName(data: ByteArray, start: Int): Int? {
        var pos = start
        var labels = 0
        while (pos < data.size && labels++ < 128) {
            val length = data[pos].toInt() and 0xff
            if (length == 0) return pos + 1
            if (length and 0xc0 == 0xc0) return if (pos + 1 < data.size) pos + 2 else null
            if (length > 63 || pos + 1 + length > data.size) return null
            pos += 1 + length
        }
        return null
    }

    private fun parseStunAddress(data: ByteArray, pos: Int, length: Int, type: Int, tx: ByteArray): SocketAddress? {
        if (length < 8 || pos + length > data.size) return null
        val family = data[pos + 1].toInt() and 0xff
        val xor = type == 0x0020
        var port = u16(data, pos + 2)
        if (xor) port = port xor 0x2112
        return when (family) {
            1 -> {
                val bytes = ByteArray(4) { i ->
                    val mask = if (xor) byteAt32(0x2112A442, i) else 0
                    ((data[pos + 4 + i].toInt() and 0xff) xor mask).toByte()
                }
                SocketAddress(ipv4(bytes, 0), port)
            }
            2 -> {
                if (length < 20) return null
                val mask = ByteArray(16)
                put32(mask, 0, 0x2112A442)
                tx.copyInto(mask, 4)
                val bytes = ByteArray(16) { i ->
                    val m = if (xor) mask[i].toInt() and 0xff else 0
                    ((data[pos + 4 + i].toInt() and 0xff) xor m).toByte()
                }
                SocketAddress(ipv6(bytes, 0), port)
            }
            else -> null
        }
    }

    private fun outerIcmpOffset(data: ByteArray, ipv6: Boolean): Int? {
        if (data.isEmpty()) return null
        val version = (data[0].toInt() ushr 4) and 0xf
        return when {
            version == 4 -> ((data[0].toInt() and 0x0f) * 4).takeIf { it >= 20 && it + 8 <= data.size }
            version == 6 -> 40.takeIf { it + 8 <= data.size }
            (!ipv6 && (data[0].toInt() and 0xff) in setOf(0, 3, 8, 11)) -> 0
            (ipv6 && (data[0].toInt() and 0xff) in setOf(2, 3, 128, 129)) -> 0
            else -> null
        }
    }

    private fun embeddedSequenceMatches(data: ByteArray, embeddedIp: Int, ipv6: Boolean, sequence: Int): Boolean {
        if (embeddedIp >= data.size) return false
        val version = (data[embeddedIp].toInt() ushr 4) and 0xf
        val innerIcmp = when (version) {
            4 -> embeddedIp + (data[embeddedIp].toInt() and 0x0f) * 4
            6 -> embeddedIp + 40
            else -> return false
        }
        val requestType = if (ipv6) 128 else 8
        return innerIcmp + 8 <= data.size &&
            (data[innerIcmp].toInt() and 0xff) == requestType &&
            u16(data, innerIcmp + 6) == (sequence and 0xffff)
    }

    private fun internetChecksum(data: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i + 1 < data.size) {
            sum += u16(data, i).toLong()
            i += 2
        }
        if (i < data.size) sum += (data[i].toInt() and 0xff).toLong() shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun ipv4(data: ByteArray, pos: Int): String = (0..3).joinToString(".") { (data[pos + it].toInt() and 0xff).toString() }

    private fun ipv6(data: ByteArray, pos: Int): String = (0 until 8).joinToString(":") { i -> u16(data, pos + i * 2).toString(16) }

    private fun u16(data: ByteArray, pos: Int): Int = ((data[pos].toInt() and 0xff) shl 8) or (data[pos + 1].toInt() and 0xff)

    private fun u32(data: ByteArray, pos: Int): Int =
        ((data[pos].toInt() and 0xff) shl 24) or ((data[pos + 1].toInt() and 0xff) shl 16) or
            ((data[pos + 2].toInt() and 0xff) shl 8) or (data[pos + 3].toInt() and 0xff)

    private fun put16(data: ByteArray, pos: Int, value: Int) {
        data[pos] = (value ushr 8).toByte()
        data[pos + 1] = value.toByte()
    }

    private fun put32(data: ByteArray, pos: Int, value: Int) {
        data[pos] = (value ushr 24).toByte()
        data[pos + 1] = (value ushr 16).toByte()
        data[pos + 2] = (value ushr 8).toByte()
        data[pos + 3] = value.toByte()
    }

    private fun byteAt32(value: Int, index: Int): Int = (value ushr (24 - index * 8)) and 0xff
}
