@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.demonv.netsessiontester.ios.tools

import com.demonv.netsessiontester.core.NetworkPackets
import com.demonv.netsessiontester.ios.getMonotonicMs
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.posix.*
import kotlin.random.Random

enum class IosDiagnosticKind { DNS, NAT, IPV6, TRACEROUTE, MTU }

data class ToolReport(
    val title: String,
    val summary: String,
    val lines: List<String>,
    val success: Boolean
)

/** Native iOS diagnostics. No socket is retained after [run] returns or is cancelled. */
object IosNetworkTools {
    suspend fun run(
        kind: IosDiagnosticKind,
        host: String,
        port: Int = 80,
        server: String = "223.5.5.5",
        ipv6: Boolean = false,
        onLine: suspend (String) -> Unit = {}
    ): ToolReport = withContext(Dispatchers.Default) {
        val output = Output(onLine)
        try {
            when (kind) {
                IosDiagnosticKind.DNS -> runDns(cleanHost(host), server, output)
                IosDiagnosticKind.NAT -> runNat(cleanHost(host), port.coerceIn(1, 65535), output)
                IosDiagnosticKind.IPV6 -> runIpv6(cleanHost(host), port.coerceIn(1, 65535), output)
                IosDiagnosticKind.TRACEROUTE -> runTraceroute(cleanHost(host), ipv6, output)
                IosDiagnosticKind.MTU -> runMtu(cleanHost(host), ipv6, output)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val detail = error.message ?: "未知错误"
            output.add("失败：$detail")
            ToolReport(title(kind), "检测失败：$detail", output.lines, false)
        }
    }

    private suspend fun runDns(host: String, server: String, out: Output): ToolReport {
        out.add("系统 DNS：解析 $host")
        val system = resolve(host, AF_UNSPEC, SOCK_STREAM, 0)
        if (system.error != null) out.add("系统 DNS 失败：${system.error}")
        else {
            out.add("系统 A：${system.v4.joinToString().ifBlank { "无记录" }}")
            out.add("系统 AAAA：${system.v6.joinToString().ifBlank { "无记录" }}")
        }

        if (server.isBlank()) {
            out.add("未配置自定义 DNS，本轮仅使用系统解析")
            val success = system.error == null
            return ToolReport("NSLookup", if (success) "系统 DNS 查询完成" else "系统 DNS 查询失败", out.lines, success)
        }
        val dns = parseEndpoint(server, 53)
        out.add("自定义 UDP DNS：${dns.host}:${dns.port}")
        val customA = customDns(host, NetworkPackets.DNS_A, dns)
        reportDnsResult("A", customA, out)
        val customAaaa = customDns(host, NetworkPackets.DNS_AAAA, dns)
        reportDnsResult("AAAA", customAaaa, out)
        val customSucceeded = listOf(customA, customAaaa).any {
            it.message?.let { message -> message.responseCode == 0 && !message.truncated } == true
        }
        val systemSucceeded = system.error == null
        val success = systemSucceeded || customSucceeded
        val summary = when {
            systemSucceeded && customSucceeded -> "系统与自定义 DNS 查询完成"
            systemSucceeded -> "系统 DNS 正常；自定义 UDP DNS 未完成"
            customSucceeded -> "自定义 UDP DNS 完成；系统解析失败"
            else -> "系统与自定义 DNS 均未取得有效响应"
        }
        return ToolReport("NSLookup", summary, out.lines, success)
    }

    private suspend fun reportDnsResult(label: String, result: DnsResult, out: Output) {
        when {
            result.message == null -> out.add("自定义 $label 失败：${result.error ?: "无有效响应"}")
            result.message.responseCode != 0 -> out.add("自定义 $label：DNS RCODE ${result.message.responseCode}")
            result.message.truncated -> out.add("自定义 $label：响应被截断；本工具按要求仅使用 UDP，未将截断结果当作完整答案")
            else -> out.add("自定义 $label：${result.message.addresses.joinToString().ifBlank { "无记录" }}（${result.rttMs}ms）")
        }
    }

    private suspend fun customDns(host: String, type: Int, server: Endpoint): DnsResult {
        val id = Random.nextInt(0, 65536)
        val request = NetworkPackets.dnsQuery(host, type, id)
        val started = getMonotonicMs()
        val result = udpExchange(server, request, 1800) { packet -> NetworkPackets.parseDnsResponse(packet, id) != null }
        val parsed = result.packet?.let { NetworkPackets.parseDnsResponse(it, id) }
        return DnsResult(parsed, (getMonotonicMs() - started).coerceAtLeast(0), result.error)
    }

    private suspend fun runNat(stunHost: String, stunPort: Int, out: Output): ToolReport {
        out.add("解析 STUN 服务器：$stunHost:$stunPort")
        val resolved = resolveEndpoints(stunHost, stunPort, AF_INET).firstOrNull()
        if (resolved == null) {
            out.add("STUN 服务器没有可用 IPv4 地址")
            return ToolReport("NAT / STUN", "STUN 服务器无可用 IPv4 地址", out.lines, false)
        }
        out.add("STUN Binding：$stunHost:$stunPort（${resolved.host}）")

        val mapping = mappingBehavior(resolved, out)
        val base = mapping.base
        if (base == null) {
            out.add("STUN 无有效响应；没有推测公网地址或 NAT 类型")
            return ToolReport("NAT / STUN", "STUN 基础映射失败", out.lines, false)
        }
        out.add("公网映射：${base.response.mapped.host}:${base.response.mapped.port}")
        out.add("本地 UDP 端口：${base.localPort ?: "未读取到"}")
        val other = base.response.other
        if (other == null) {
            out.add("服务器未返回 OTHER-ADDRESS/CHANGED-ADDRESS，仅能确认基础映射")
            out.add("NAT 类型：未验证（公网映射本身不能证明 NAT 类型）")
            return ToolReport("NAT / STUN", "基础 STUN 映射完成；RFC5780 不受服务器支持", out.lines, true)
        }

        out.add("RFC5780 Mapping：${mapping.behavior}；${mapping.detail}")
        val filtering = filteringBehavior(resolved, other, out)
        out.add("RFC5780 Filtering：${filtering.behavior}；${filtering.detail}")
        val conclusive = mapping.conclusive && filtering.conclusive
        val summary = if (conclusive) {
            "RFC5780 完成：映射=${mapping.behavior}，过滤=${filtering.behavior}"
        } else {
            "基础映射完成；RFC5780 结果不完整，NAT 类型未验证"
        }
        if (!conclusive) out.add("NAT 类型：未验证；没有根据公网 IP 或单次超时强行分类")
        return ToolReport("NAT / STUN", summary, out.lines, true)
    }

    private suspend fun mappingBehavior(server: Endpoint, out: Output): MappingResult {
        val fd = openDatagram(AF_INET, IPPROTO_UDP)
        if (fd < 0) return MappingResult(null, "未知", "无法创建 UDP socket：${lastError()}", false)
        try {
            val base = stunExchange(fd, server)
                ?: return MappingResult(null, "未知", "Binding 无响应", false)
            val other = base.response.other ?: return MappingResult(base, "未验证", "没有备用地址", false)
            val origin = base.response.responseOrigin ?: NetworkPackets.SocketAddress(base.source.host, base.source.port)
            val alternateIpSamePort = resolveEndpoints(other.host, origin.port, AF_INET).firstOrNull()
                ?: return MappingResult(base, "未验证", "备用 IP 无法解析", false)
            out.add("RFC5780 Mapping Test II：备用 IP、原端口")
            val second = stunExchange(fd, alternateIpSamePort)
            if (sameMapping(base, second)) return MappingResult(base, "端点无关", "Test I 与 Test II 映射一致", true)
            val alternate = resolveEndpoints(other.host, other.port, AF_INET).firstOrNull()
                ?: return MappingResult(base, "未验证", "备用地址无效", false)
            out.add("RFC5780 Mapping Test III：备用 IP、备用端口")
            val third = stunExchange(fd, alternate)
            return when {
                second == null || third == null -> MappingResult(base, "未验证", "备用测试无响应，不能把超时当作映射变化", false)
                sameMapping(second, third) -> MappingResult(base, "地址相关", "Test II 与 Test III 一致，且不同于 Test I", true)
                else -> MappingResult(base, "地址和端口相关", "三个目标产生不同映射", true)
            }
        } finally {
            close(fd)
        }
    }

    private suspend fun filteringBehavior(server: Endpoint, other: NetworkPackets.SocketAddress, out: Output): FilteringResult {
        val strictAlternate = other.host != server.host && other.port != server.port
        if (!strictAlternate) return FilteringResult("未验证", "备用地址不是不同 IP 且不同端口", false)
        out.add("RFC5780 Filtering Test II：请求改变来源 IP 和端口")
        val test2 = filterTest(server, changeIp = true, changePort = true)
        if (test2 == FilterOutcome.VALID_CHANGED_SOURCE) {
            return FilteringResult("端点无关", "从声明的备用 IP 和端口收到响应", true)
        }
        if (test2 == FilterOutcome.NO_BASELINE) return FilteringResult("未验证", "基础 Binding 无响应", false)
        if (test2 == FilterOutcome.INVALID_SOURCE) return FilteringResult("未验证", "响应来源与服务器声明不一致", false)
        out.add("RFC5780 Filtering Test III：请求仅改变来源端口")
        val test3 = filterTest(server, changeIp = false, changePort = true)
        return when (test3) {
            FilterOutcome.VALID_CHANGED_SOURCE -> FilteringResult("地址相关", "同一服务器 IP 的备用端口可回包", true)
            FilterOutcome.TIMED_OUT -> FilteringResult("地址和端口相关", "基础 Binding 正常，Test II/III 均无改变来源回包", true)
            FilterOutcome.NO_BASELINE -> FilteringResult("未验证", "Test III 基础 Binding 无响应", false)
            FilterOutcome.INVALID_SOURCE -> FilteringResult("未验证", "收到响应但来源与服务器声明不一致", false)
        }
    }

    private suspend fun filterTest(
        server: Endpoint,
        changeIp: Boolean,
        changePort: Boolean
    ): FilterOutcome {
        val fd = openDatagram(AF_INET, IPPROTO_UDP)
        if (fd < 0) return FilterOutcome.NO_BASELINE
        try {
            val base = stunExchange(fd, server) ?: return FilterOutcome.NO_BASELINE
            val declaredOther = base.response.other ?: return FilterOutcome.INVALID_SOURCE
            val origin = base.response.responseOrigin ?: NetworkPackets.SocketAddress(base.source.host, base.source.port)
            val changed = stunExchange(fd, server, changeIp, changePort) ?: return FilterOutcome.TIMED_OUT
            val expectedHost = if (changeIp) declaredOther.host else origin.host
            val expectedPort = if (changePort) declaredOther.port else origin.port
            return if (changed.source.host == expectedHost && changed.source.port == expectedPort) {
                FilterOutcome.VALID_CHANGED_SOURCE
            } else FilterOutcome.INVALID_SOURCE
        } finally {
            close(fd)
        }
    }

    private suspend fun runIpv6(host: String, port: Int, out: Output): ToolReport {
        val local = localIpv6Addresses()
        if (local.isEmpty()) out.add("本地接口：没有发现已启用的非回环 IPv6 地址")
        else local.forEach { out.add("本地接口 ${it.first}：${it.second}") }

        out.add("系统 DNS：查询 $host 的 AAAA")
        val dns = resolve(host, AF_INET6, SOCK_STREAM, IPPROTO_TCP)
        if (dns.error != null) out.add("AAAA 解析失败：${dns.error}")
        else out.add("AAAA：${dns.v6.joinToString().ifBlank { "无记录" }}")

        val tcp = tcpProbe(host, port, AF_INET6, 2500)
        if (tcp.latencyMs != null) out.add("IPv6 TCP：$host:$port 可达（${tcp.latencyMs}ms）")
        else out.add("IPv6 TCP：未建立连接（${tcp.error ?: "超时"}）")
        val success = local.isNotEmpty() && dns.v6.isNotEmpty() && tcp.latencyMs != null
        val summary = when {
            success -> "本机 IPv6、AAAA 与 TCP 可达性均通过"
            local.isEmpty() -> "本机未发现可用 IPv6 接口地址"
            dns.v6.isEmpty() -> "本机有 IPv6，但目标没有可用 AAAA"
            else -> "本机与 DNS 具备 IPv6，目标 TCP 未连通"
        }
        return ToolReport("IPv6 专项", summary, out.lines, success)
    }

    private suspend fun runTraceroute(host: String, ipv6: Boolean, out: Output): ToolReport {
        val family = if (ipv6) AF_INET6 else AF_INET
        out.add("解析目标：$host（${if (ipv6) "IPv6" else "IPv4"}）")
        val target = resolveEndpoints(host, 0, family).firstOrNull()
        if (target == null) {
            val detail = "目标没有可用的 ${if (ipv6) "IPv6" else "IPv4"} 地址"
            out.add(detail)
            return ToolReport("Traceroute", detail, out.lines, false)
        }
        val protocol = if (ipv6) IPPROTO_ICMPV6 else IPPROTO_ICMP
        val fd = openDatagram(family, protocol)
        if (fd < 0) {
            val detail = if (errno == EACCES || errno == EPERM) "系统拒绝 ICMP datagram 权限" else lastError()
            out.add("无法开始：$detail")
            return ToolReport("Traceroute", detail, out.lines, false)
        }
        var reached = false
        try {
            out.add("目标：${target.host}；最多 20 跳；每跳超时 1200ms")
            for (ttl in 1..20) {
                currentCoroutineContext().ensureActive()
                if (!setHopLimit(fd, ipv6, ttl)) {
                    out.add("系统不支持设置 ${if (ipv6) "IPv6 hop limit" else "IPv4 TTL"}：${lastError()}")
                    return ToolReport("Traceroute", "当前系统接口不支持逐跳探测", out.lines, false)
                }
                val probe = icmpProbe(fd, target, ipv6, ttl, if (ipv6) 48 else 28, 1200)
                val hop = ttl.toString().padStart(2, '0')
                val line = when (probe.status) {
                    ProbeStatus.SUCCESS -> "$hop  ${probe.source?.host ?: target.host}  ${probe.rttMs ?: 0}ms  已到达"
                    ProbeStatus.TIME_EXCEEDED -> "$hop  ${probe.source?.host ?: "?"}  ${probe.rttMs ?: 0}ms"
                    ProbeStatus.TIMEOUT -> "$hop  *  超时"
                    ProbeStatus.TOO_BIG -> "$hop  ${probe.source?.host ?: "?"}  Packet Too Big"
                    ProbeStatus.ERROR -> "$hop  错误：${probe.error ?: "未知"}"
                }
                out.add(line)
                if (probe.status == ProbeStatus.SUCCESS) {
                    reached = true
                    break
                }
            }
        } finally {
            close(fd)
        }
        return ToolReport("Traceroute", if (reached) "已到达目标" else "追踪结束，目标未确认到达", out.lines, reached)
    }

    private suspend fun runMtu(host: String, ipv6: Boolean, out: Output): ToolReport {
        val family = if (ipv6) AF_INET6 else AF_INET
        out.add("解析 PMTU 目标：$host（${if (ipv6) "IPv6" else "IPv4"}）")
        val target = resolveEndpoints(host, 0, family).firstOrNull()
        if (target == null) {
            out.add("目标地址解析失败")
            return ToolReport("MTU / PMTU", "目标地址解析失败", out.lines, false)
        }
        val fd = openDatagram(family, if (ipv6) IPPROTO_ICMPV6 else IPPROTO_ICMP)
        if (fd < 0) {
            val detail = if (errno == EACCES || errno == EPERM) "系统拒绝 ICMP datagram 权限" else lastError()
            out.add(detail)
            return ToolReport("MTU / PMTU", detail, out.lines, false)
        }
        try {
            if (!enableDontFragment(fd, ipv6)) {
                val detail = "系统不支持为该 socket 启用非分片探测：${lastError()}"
                out.add(detail)
                return ToolReport("MTU / PMTU", detail, out.lines, false)
            }
            val ipHeader = if (ipv6) 40 else 20
            val minimum = if (ipv6) 1280 else 576
            val candidates = listOf(1500, 1492, 1472, 1450, 1400, 1280, minimum).distinct().filter { it >= minimum }
            var largestPassed: Int? = null
            var explicitUpper: Int? = null
            var advertisedMtu: Int? = null
            var inconclusive = false
            out.add("非分片 ICMP 探测：${target.host}")
            for ((index, totalSize) in candidates.withIndex()) {
                currentCoroutineContext().ensureActive()
                val probe = icmpProbe(fd, target, ipv6, 200 + index, totalSize - ipHeader, 1400)
                when (probe.status) {
                    ProbeStatus.SUCCESS -> {
                        largestPassed = maxOf(largestPassed ?: 0, totalSize)
                        out.add("$totalSize 字节：通过（${probe.rttMs}ms）")
                        break
                    }
                    ProbeStatus.TOO_BIG -> {
                        explicitUpper = minOf(explicitUpper ?: Int.MAX_VALUE, totalSize - 1)
                        advertisedMtu = probe.nextHopMtu?.takeIf { it in minimum..65535 } ?: advertisedMtu
                        val evidence = if (probe.nextHopMtu != null) "收到 ICMP Packet Too Big，下一跳 MTU=${probe.nextHopMtu}"
                        else "系统明确拒绝该非分片报文${probe.error?.let { "（$it）" }.orEmpty()}"
                        out.add("$totalSize 字节：$evidence")
                    }
                    ProbeStatus.TIMEOUT -> {
                        inconclusive = true
                        out.add("$totalSize 字节：无响应（不作为 MTU 上界）")
                    }
                    ProbeStatus.ERROR -> out.add("$totalSize 字节：发送/接收错误（${probe.error}）")
                    ProbeStatus.TIME_EXCEEDED -> out.add("$totalSize 字节：收到 Time Exceeded，未用于判定 MTU")
                }
                if (largestPassed != null && explicitUpper != null && largestPassed >= explicitUpper) break
            }
            if (advertisedMtu == null && largestPassed != null && explicitUpper != null) {
                var lower = largestPassed ?: error("missing MTU lower bound")
                var upper = explicitUpper ?: error("missing MTU upper bound")
                var sequence = 400
                while (upper - lower > 1) {
                    currentCoroutineContext().ensureActive()
                    val candidate = (lower + upper + 1) / 2
                    val probe = icmpProbe(fd, target, ipv6, sequence++, candidate - ipHeader, 1400)
                    when (probe.status) {
                        ProbeStatus.SUCCESS -> {
                            lower = candidate
                            out.add("细化 $candidate 字节：通过")
                        }
                        ProbeStatus.TOO_BIG -> {
                            upper = candidate - 1
                            out.add("细化 $candidate 字节：协议或系统明确报告过大")
                        }
                        ProbeStatus.TIMEOUT -> {
                            inconclusive = true
                            out.add("细化 $candidate 字节：无响应，停止收窄（不把超时当作边界）")
                            break
                        }
                        ProbeStatus.ERROR, ProbeStatus.TIME_EXCEEDED -> {
                            out.add("细化 $candidate 字节：未取得 MTU 协议证据，停止收窄")
                            break
                        }
                    }
                }
                largestPassed = lower
                explicitUpper = upper
            }
            val summary = when {
                advertisedMtu != null -> "路径明确报告 MTU $advertisedMtu 字节；已通过下界 ${largestPassed ?: "未确认"} 字节"
                largestPassed != null && explicitUpper == largestPassed -> "路径 MTU 已由通过与明确 Too Big 收敛到 $largestPassed 字节"
                largestPassed != null && explicitUpper != null -> "路径 MTU 位于 $largestPassed..$explicitUpper 字节（由成功与明确 Too Big 夹定）"
                largestPassed != null -> "已确认至少 $largestPassed 字节可通过；未收到明确 Too Big，不能给出精确上界"
                inconclusive -> "探测无响应；无法判断路径 MTU"
                else -> "没有取得可用于判断路径 MTU 的协议证据"
            }
            val success = largestPassed != null || advertisedMtu != null
            return ToolReport("MTU / PMTU", summary, out.lines, success)
        } finally {
            close(fd)
        }
    }

    private suspend fun stunExchange(
        fd: Int,
        endpoint: Endpoint,
        changeIp: Boolean = false,
        changePort: Boolean = false
    ): StunPacket? {
        val tx = Random.nextBytes(12)
        val request = NetworkPackets.stunBindingRequest(tx, changeIp, changePort)
        val result = udpExchange(fd, endpoint, request, 1300) { NetworkPackets.parseStunResponse(it, tx) != null }
        val parsed = result.packet?.let { NetworkPackets.parseStunResponse(it, tx) } ?: return null
        return StunPacket(parsed, result.source ?: return null, localPort(fd))
    }

    private fun sameMapping(first: StunPacket?, second: StunPacket?): Boolean =
        first != null && second != null && first.response.mapped == second.response.mapped

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun icmpProbe(
        fd: Int,
        target: Endpoint,
        ipv6: Boolean,
        sequence: Int,
        icmpSize: Int,
        timeoutMs: Int
    ): IcmpProbe {
        val request = NetworkPackets.icmpEchoRequest(sequence, icmpSize, ipv6)
        val started = getMonotonicMs()
        val sent = sendPacket(fd, target, request)
        if (sent < 0L) {
            return if (errno == EMSGSIZE) IcmpProbe(ProbeStatus.TOO_BIG, error = lastError())
            else IcmpProbe(ProbeStatus.ERROR, error = lastError())
        }
        val deadline = started + timeoutMs
        val buffer = ByteArray(65535)
        while (getMonotonicMs() < deadline) {
            currentCoroutineContext().ensureActive()
            val ready = waitReadable(fd, (deadline - getMonotonicMs()).coerceAtMost(50).toInt())
            if (ready < 0) return IcmpProbe(ProbeStatus.ERROR, error = lastError())
            if (ready == 0) continue
            val received = receivePacket(fd, buffer)
            if (received == null) {
                if (errno == EMSGSIZE) return IcmpProbe(ProbeStatus.TOO_BIG, error = lastError())
                if (errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR) {
                    return IcmpProbe(ProbeStatus.ERROR, error = lastError())
                }
                continue
            }
            val parsed = NetworkPackets.parseIcmp(received.bytes, ipv6, sequence) ?: continue
            val elapsed = (getMonotonicMs() - started).toInt().coerceAtLeast(0)
            return when {
                parsed.echoReply -> IcmpProbe(ProbeStatus.SUCCESS, received.source, elapsed)
                parsed.timeExceeded -> IcmpProbe(ProbeStatus.TIME_EXCEEDED, received.source, elapsed)
                parsed.packetTooBig -> IcmpProbe(ProbeStatus.TOO_BIG, received.source, elapsed, parsed.nextHopMtu)
                parsed.destinationUnreachable -> IcmpProbe(
                    ProbeStatus.ERROR,
                    received.source,
                    elapsed,
                    error = "ICMP Destination Unreachable (code ${parsed.code})"
                )
                else -> continue
            }
        }
        return IcmpProbe(ProbeStatus.TIMEOUT)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun setHopLimit(fd: Int, ipv6: Boolean, value: Int): Boolean = memScoped {
        val option = alloc<IntVar>().apply { this.value = value }
        val level = if (ipv6) IPPROTO_IPV6 else IPPROTO_IP
        val name = if (ipv6) IPV6_UNICAST_HOPS else IP_TTL
        setsockopt(fd, level, name, option.ptr, sizeOf<IntVar>().convert()) == 0
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun enableDontFragment(fd: Int, ipv6: Boolean): Boolean = memScoped {
        val option = alloc<IntVar>().apply { value = 1 }
        // Values are stable Darwin socket ABI constants from <netinet/in.h>.
        val level = if (ipv6) IPPROTO_IPV6 else IPPROTO_IP
        val name = if (ipv6) 62 /* IPV6_DONTFRAG */ else 28 /* IP_DONTFRAG */
        setsockopt(fd, level, name, option.ptr, sizeOf<IntVar>().convert()) == 0
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun tcpProbe(host: String, port: Int, family: Int, timeoutMs: Int): TcpResult {
        val endpoints = resolveEndpoints(host, port, family)
        if (endpoints.isEmpty()) return TcpResult(error = "没有可用地址")
        var last = "连接失败"
        for (endpoint in endpoints) {
            currentCoroutineContext().ensureActive()
            val fd = socket(family, SOCK_STREAM, IPPROTO_TCP)
            if (fd < 0) {
                last = lastError()
                continue
            }
            try {
                val flags = fcntl(fd, F_GETFL, 0)
                if (flags < 0 || fcntl(fd, F_SETFL, flags or O_NONBLOCK) < 0) {
                    last = "无法启用非阻塞连接：${lastError()}"
                    continue
                }
                val started = getMonotonicMs()
                val result = withSockaddr(endpoint) { address, length -> connect(fd, address, length) }
                if (result == 0) return TcpResult((getMonotonicMs() - started).toInt().coerceAtLeast(1))
                if (errno != EINPROGRESS) {
                    last = lastError()
                    continue
                }
                val deadline = started + timeoutMs
                while (getMonotonicMs() < deadline) {
                    currentCoroutineContext().ensureActive()
                    val wait = waitWritable(fd, (deadline - getMonotonicMs()).coerceAtMost(50).toInt())
                    if (wait < 0) {
                        last = lastError()
                        break
                    }
                    if (wait == 0) continue
                    val socketError = memScoped {
                        val error = alloc<IntVar>()
                        val size = alloc<socklen_tVar>().apply { value = sizeOf<IntVar>().convert() }
                        if (getsockopt(fd, SOL_SOCKET, SO_ERROR, error.ptr, size.ptr) == 0) error.value else errno
                    }
                    if (socketError == 0) return TcpResult((getMonotonicMs() - started).toInt().coerceAtLeast(1))
                    last = platform.posix.strerror(socketError)?.toKString() ?: "错误 $socketError"
                    break
                }
            } finally {
                close(fd)
            }
        }
        return TcpResult(error = last)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun localIpv6Addresses(): List<Pair<String, String>> = memScoped {
        val result = mutableListOf<Pair<String, String>>()
        val head = allocPointerTo<ifaddrs>()
        if (getifaddrs(head.ptr) != 0) return@memScoped emptyList()
        try {
            var current = head.value
            while (current != null) {
                val item = current.pointed
                val address = item.ifa_addr
                val flags = item.ifa_flags
                if (address != null && address.pointed.sa_family.toInt() == AF_INET6 &&
                    flags and IFF_UP.toUInt() != 0u && flags and IFF_LOOPBACK.toUInt() == 0u
                ) {
                    val host = numericHost(address, address.pointed.sa_len.toUInt())
                    if (host != null) result += item.ifa_name?.toKString().orEmpty() to host
                }
                current = item.ifa_next
            }
        } finally {
            freeifaddrs(head.value)
        }
        result.distinct()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun resolve(host: String, family: Int, socketType: Int, protocol: Int): ResolveResult {
        val endpoints = mutableListOf<Endpoint>()
        val error = memScoped {
            val hints = alloc<addrinfo>().apply {
                ai_family = family
                ai_socktype = socketType
                ai_protocol = protocol
            }
            val output = allocPointerTo<addrinfo>()
            val status = getaddrinfo(host, null, hints.ptr, output.ptr)
            val head = output.value
            if (status != 0 || head == null) return@memScoped gai_strerror(status)?.toKString() ?: "解析错误 $status"
            try {
                var current: CPointer<addrinfo>? = head
                while (current != null) {
                    val item = current.pointed
                    val numeric = numericHost(item.ai_addr, item.ai_addrlen)
                    if (numeric != null) endpoints += Endpoint(numeric.substringBefore('%'), 0, item.ai_family)
                    current = item.ai_next
                }
            } finally {
                freeaddrinfo(head)
            }
            null
        }
        return ResolveResult(
            endpoints.filter { it.family == AF_INET }.map { it.host }.distinct(),
            endpoints.filter { it.family == AF_INET6 }.map { it.host }.distinct(),
            error
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun resolveEndpoints(host: String, port: Int, family: Int): List<Endpoint> = memScoped {
        val endpoints = mutableListOf<Endpoint>()
        val hints = alloc<addrinfo>().apply {
            ai_family = family
            ai_socktype = SOCK_DGRAM
        }
        val output = allocPointerTo<addrinfo>()
        if (getaddrinfo(host, port.toString(), hints.ptr, output.ptr) != 0) return@memScoped emptyList()
        val head = output.value ?: return@memScoped emptyList()
        try {
            var current: CPointer<addrinfo>? = head
            while (current != null) {
                val item = current.pointed
                val numeric = numericHost(item.ai_addr, item.ai_addrlen)
                if (numeric != null) endpoints += Endpoint(numeric.substringBefore('%'), port, item.ai_family)
                current = item.ai_next
            }
        } finally {
            freeaddrinfo(head)
        }
        endpoints.distinct()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun numericHost(address: CPointer<sockaddr>?, length: socklen_t): String? = memScoped {
        if (address == null) return@memScoped null
        val buffer = allocArray<ByteVar>(NI_MAXHOST)
        if (getnameinfo(address, length, buffer, NI_MAXHOST.convert(), null, 0u, NI_NUMERICHOST) == 0) buffer.toKString() else null
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun openDatagram(family: Int, protocol: Int): Int {
        val fd = socket(family, SOCK_DGRAM, protocol)
        if (fd >= 0) {
            val flags = fcntl(fd, F_GETFL, 0)
            if (flags < 0 || fcntl(fd, F_SETFL, flags or O_NONBLOCK) < 0) {
                close(fd)
                return -1
            }
        }
        return fd
    }

    private suspend fun udpExchange(
        endpoint: Endpoint,
        request: ByteArray,
        timeoutMs: Int,
        accept: (ByteArray) -> Boolean
    ): ExchangeResult {
        val fd = openDatagram(endpoint.family, IPPROTO_UDP)
        if (fd < 0) return ExchangeResult(error = lastError())
        return try {
            udpExchange(fd, endpoint, request, timeoutMs, accept)
        } finally {
            close(fd)
        }
    }

    private suspend fun udpExchange(
        fd: Int,
        endpoint: Endpoint,
        request: ByteArray,
        timeoutMs: Int,
        accept: (ByteArray) -> Boolean
    ): ExchangeResult {
        if (sendPacket(fd, endpoint, request) < 0) return ExchangeResult(error = lastError())
        val deadline = getMonotonicMs() + timeoutMs
        val resendAt = getMonotonicMs() + timeoutMs / 2
        var resent = false
        val buffer = ByteArray(65535)
        while (getMonotonicMs() < deadline) {
            currentCoroutineContext().ensureActive()
            if (!resent && getMonotonicMs() >= resendAt) {
                sendPacket(fd, endpoint, request)
                resent = true
            }
            val ready = waitReadable(fd, (deadline - getMonotonicMs()).coerceAtMost(50).toInt())
            if (ready < 0) return ExchangeResult(error = lastError())
            if (ready == 0) continue
            val packet = receivePacket(fd, buffer) ?: continue
            if (accept(packet.bytes)) return ExchangeResult(packet.bytes, packet.source)
        }
        return ExchangeResult(error = "超时")
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun sendPacket(fd: Int, endpoint: Endpoint, packet: ByteArray): Long = packet.usePinned { pinned ->
        withSockaddr(endpoint) { address, length ->
            sendto(fd, pinned.addressOf(0), packet.size.convert(), 0, address, length).toLong()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun receivePacket(fd: Int, buffer: ByteArray): ReceivedPacket? = memScoped {
        val storage = alloc<sockaddr_storage>()
        val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_storage>().convert() }
        val count = buffer.usePinned { pinned ->
            recvfrom(fd, pinned.addressOf(0), buffer.size.convert(), 0, storage.ptr.reinterpret(), length.ptr)
        }
        if (count <= 0L) return@memScoped null
        val sourcePointer = storage.ptr.reinterpret<sockaddr>()
        val host = numericHost(sourcePointer, length.value) ?: return@memScoped null
        val port = when (sourcePointer.pointed.sa_family.toInt()) {
            AF_INET -> ntohs(storage.ptr.reinterpret<sockaddr_in>().pointed.sin_port).toInt()
            AF_INET6 -> ntohs(storage.ptr.reinterpret<sockaddr_in6>().pointed.sin6_port).toInt()
            else -> 0
        }
        ReceivedPacket(buffer.copyOf(count.toInt()), Endpoint(host.substringBefore('%'), port, sourcePointer.pointed.sa_family.toInt()))
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun localPort(fd: Int): Int? = memScoped {
        val storage = alloc<sockaddr_storage>()
        val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_storage>().convert() }
        if (getsockname(fd, storage.ptr.reinterpret(), length.ptr) != 0) return@memScoped null
        when (storage.ptr.reinterpret<sockaddr>().pointed.sa_family.toInt()) {
            AF_INET -> ntohs(storage.ptr.reinterpret<sockaddr_in>().pointed.sin_port).toInt()
            AF_INET6 -> ntohs(storage.ptr.reinterpret<sockaddr_in6>().pointed.sin6_port).toInt()
            else -> null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun <T> withSockaddr(endpoint: Endpoint, block: (CPointer<sockaddr>, socklen_t) -> T): T = memScoped {
        val hints = alloc<addrinfo>().apply {
            ai_family = endpoint.family
            ai_socktype = SOCK_DGRAM
            ai_flags = AI_NUMERICHOST
        }
        val output = allocPointerTo<addrinfo>()
        val status = getaddrinfo(endpoint.host, endpoint.port.toString(), hints.ptr, output.ptr)
        val head = output.value
        if (status != 0 || head == null) error("无效地址 ${endpoint.host}:${endpoint.port}")
        try {
            block(head.pointed.ai_addr!!, head.pointed.ai_addrlen)
        } finally {
            freeaddrinfo(head)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun waitReadable(fd: Int, timeoutMs: Int): Int = memScoped {
        val item = alloc<pollfd>().apply {
            this.fd = fd
            events = (POLLIN or POLLERR).toShort()
        }
        val result = poll(item.ptr, 1u, timeoutMs.coerceAtLeast(0))
        if (result > 0 && item.revents.toInt() and POLLNVAL != 0) -1 else result
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun waitWritable(fd: Int, timeoutMs: Int): Int = memScoped {
        val item = alloc<pollfd>().apply {
            this.fd = fd
            events = (POLLOUT or POLLERR).toShort()
        }
        poll(item.ptr, 1u, timeoutMs.coerceAtLeast(0))
    }

    private fun parseEndpoint(raw: String, defaultPort: Int): Endpoint {
        val clean = raw.trim().removePrefix("stun:").ifBlank { "223.5.5.5" }
        val host: String
        val port: Int
        if (clean.startsWith("[") && clean.contains(']')) {
            host = clean.substringAfter('[').substringBefore(']')
            port = clean.substringAfter("]:", defaultPort.toString()).toIntOrNull() ?: defaultPort
        } else if (clean.count { it == ':' } == 1) {
            host = clean.substringBefore(':')
            port = clean.substringAfter(':').toIntOrNull() ?: defaultPort
        } else {
            host = clean
            port = defaultPort
        }
        require(host.isNotBlank() && port in 1..65535) { "服务器地址无效" }
        val resolved = resolveEndpoints(host, port, AF_INET).firstOrNull()
            ?: resolveEndpoints(host, port, AF_INET6).firstOrNull()
            ?: error("无法解析服务器 $host")
        return resolved.copy(port = port)
    }

    private fun cleanHost(value: String): String = value.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }

    private fun title(kind: IosDiagnosticKind): String = when (kind) {
        IosDiagnosticKind.DNS -> "NSLookup"
        IosDiagnosticKind.NAT -> "NAT / STUN"
        IosDiagnosticKind.IPV6 -> "IPv6 专项"
        IosDiagnosticKind.TRACEROUTE -> "Traceroute"
        IosDiagnosticKind.MTU -> "MTU / PMTU"
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun lastError(): String = strerror(errno)?.toKString() ?: "系统错误 $errno"

    private class Output(private val callback: suspend (String) -> Unit) {
        private val values = mutableListOf<String>()
        val lines: List<String> get() = values.toList()
        suspend fun add(line: String) {
            values += line
            callback(line)
        }
    }

    private data class Endpoint(val host: String, val port: Int, val family: Int)
    private data class ResolveResult(val v4: List<String>, val v6: List<String>, val error: String?)
    private data class DnsResult(val message: NetworkPackets.DnsMessage?, val rttMs: Long, val error: String?)
    private data class ExchangeResult(val packet: ByteArray? = null, val source: Endpoint? = null, val error: String? = null)
    private data class ReceivedPacket(val bytes: ByteArray, val source: Endpoint)
    private data class StunPacket(val response: NetworkPackets.StunResponse, val source: Endpoint, val localPort: Int?)
    private data class MappingResult(val base: StunPacket?, val behavior: String, val detail: String, val conclusive: Boolean)
    private data class FilteringResult(val behavior: String, val detail: String, val conclusive: Boolean)
    private data class TcpResult(val latencyMs: Int? = null, val error: String? = null)
    private data class IcmpProbe(
        val status: ProbeStatus,
        val source: Endpoint? = null,
        val rttMs: Int? = null,
        val nextHopMtu: Int? = null,
        val error: String? = null
    )
    private enum class ProbeStatus { SUCCESS, TIME_EXCEEDED, TOO_BIG, TIMEOUT, ERROR }
    private enum class FilterOutcome { VALID_CHANGED_SOURCE, TIMED_OUT, NO_BASELINE, INVALID_SOURCE }
}
