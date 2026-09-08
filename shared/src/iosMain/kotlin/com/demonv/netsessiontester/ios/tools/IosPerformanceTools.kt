@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.demonv.netsessiontester.ios.tools

import kotlinx.cinterop.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.AF_UNSPEC
import platform.posix.AI_ADDRCONFIG
import platform.posix.EAGAIN
import platform.posix.EINPROGRESS
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.IPPROTO_TCP
import platform.posix.O_NONBLOCK
import platform.posix.POLLERR
import platform.posix.POLLHUP
import platform.posix.POLLIN
import platform.posix.POLLOUT
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_ERROR
import platform.posix.SO_NOSIGPIPE
import platform.posix.addrinfo
import platform.posix.CLOCK_MONOTONIC
import platform.posix.close
import platform.posix.clock_gettime
import platform.posix.connect
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.freeaddrinfo
import platform.posix.gai_strerror
import platform.posix.getaddrinfo
import platform.posix.getsockopt
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.socklen_tVar
import platform.posix.socket
import platform.posix.strerror
import platform.posix.timespec
import kotlin.math.abs
import kotlin.math.round
import kotlin.random.Random

/** iOS-only tools that speak the iPerf3 protocol and measure TCP handshake latency. */
object IosPerformanceTools {
    private const val COOKIE_SIZE = 37
    private const val TEST_START = 1
    private const val TEST_RUNNING = 2
    private const val TEST_END = 4
    private const val PARAM_EXCHANGE = 9
    private const val CREATE_STREAMS = 10
    private const val SERVER_TERMINATE = 11
    private const val EXCHANGE_RESULTS = 13
    private const val DISPLAY_RESULTS = 14
    private const val IPERF_DONE = 16
    private const val ACCESS_DENIED = 0xff
    private const val SERVER_ERROR = 0xfe
    private const val CONTROL_TIMEOUT_MS = 10_000
    private const val TCP_BLOCK_SIZE = 128 * 1024
    private const val MAX_JSON_SIZE = 4 * 1024 * 1024

    suspend fun iperf(
        host: String,
        port: Int = 5201,
        reverse: Boolean = true,
        durationSec: Int = 10,
        onLine: suspend (String) -> Unit
    ): ToolReport = withContext(Dispatchers.Default) {
        val cleanHost = cleanHost(host)
        val cleanPort = port.coerceIn(1, 65_535)
        val duration = durationSec.coerceIn(1, 60)
        val lines = mutableListOf<String>()
        suspend fun emit(line: String) {
            lines += line
            onLine(line)
        }

        var controlFd = -1
        var dataFd = -1
        try {
            emit("连接 iPerf3 服务端 $cleanHost:$cleanPort")
            controlFd = connectTcp(cleanHost, cleanPort, AF_UNSPEC, CONTROL_TIMEOUT_MS)
            val cookie = cookie()
            writeAll(controlFd, cookie, deadlineAfter(CONTROL_TIMEOUT_MS), "发送 COOKIE")

            expectState(controlFd, PARAM_EXCHANGE, "PARAM_EXCHANGE")
            val parameters = buildJsonObject {
                put("tcp", true)
                put("omit", 0)
                put("time", duration)
                put("num", 0)
                put("blockcount", 0)
                put("parallel", 1)
                put("len", TCP_BLOCK_SIZE)
                if (reverse) put("reverse", true)
                put("client_version", "3.17")
            }
            writeJson(controlFd, parameters, "发送测试参数")
            emit("参数已交换：TCP 单流，${if (reverse) "下载" else "上传"}，${duration}s")

            expectState(controlFd, CREATE_STREAMS, "CREATE_STREAMS")
            dataFd = connectTcp(cleanHost, cleanPort, AF_UNSPEC, CONTROL_TIMEOUT_MS)
            writeAll(dataFd, cookie, deadlineAfter(CONTROL_TIMEOUT_MS), "发送数据流 COOKIE")
            expectState(controlFd, TEST_START, "TEST_START")
            expectState(controlFd, TEST_RUNNING, "TEST_RUNNING")
            emit("数据流已建立，开始测量")

            val transfer = transfer(controlFd, dataFd, reverse, duration, ::emit)
            writeState(controlFd, TEST_END, "TEST_END")
            expectState(controlFd, EXCHANGE_RESULTS, "EXCHANGE_RESULTS")

            val elapsedSeconds = transfer.elapsedNanos.toDouble() / 1_000_000_000.0
            val localResults = buildJsonObject {
                put("cpu_util_total", 0.0)
                put("cpu_util_user", 0.0)
                put("cpu_util_system", 0.0)
                put("sender_has_retransmits", -1)
                put("streams", buildJsonArray {
                    add(buildJsonObject {
                        put("id", 1)
                        put("bytes", transfer.bytes)
                        put("retransmits", -1)
                        put("jitter", 0.0)
                        put("errors", 0)
                        put("omitted_errors", 0)
                        put("packets", 0)
                        put("omitted_packets", 0)
                        put("start_time", 0.0)
                        put("end_time", elapsedSeconds)
                    })
                })
            }
            writeJson(controlFd, localResults, "发送客户端结果")
            val serverResults = readJson(controlFd, "读取服务端结果")
            expectState(controlFd, DISPLAY_RESULTS, "DISPLAY_RESULTS")
            writeState(controlFd, IPERF_DONE, "IPERF_DONE")

            val serverBytes = serverResults["streams"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("bytes")?.jsonPrimitive?.longOrNull
            val serverRetransmits = serverResults["streams"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("retransmits")?.jsonPrimitive?.longOrNull
            val serverCpu = serverResults["cpu_util_total"]?.jsonPrimitive?.doubleOrNull
            val bitsPerSecond = if (transfer.elapsedNanos > 0L) {
                transfer.bytes.toDouble() * 8.0 * 1_000_000_000.0 / transfer.elapsedNanos.toDouble()
            } else 0.0
            val direction = if (reverse) "下载" else "上传"
            val rate = formatRate(bitsPerSecond)
            emit("$direction 完成：本机传输 ${formatBytes(transfer.bytes)}，平均 $rate")
            if (serverBytes != null || serverRetransmits != null || serverCpu != null) {
                emit(
                        "服务端结果：传输 ${serverBytes?.let(::formatBytes) ?: "未报告"}，" +
                        "重传 ${serverRetransmits?.toString() ?: "未报告"}，" +
                        "CPU ${serverCpu?.let { "${decimal(it, 1)}%" } ?: "未报告"}"
                )
            }
            ToolReport(
                title = "iPerf3 TCP 单流",
                summary = "$direction $rate（${duration}s）",
                lines = lines,
                success = true
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val message = error.message ?: error::class.simpleName ?: "未知错误"
            runCatching { emit("测试失败：$message") }
            ToolReport(
                title = "iPerf3 TCP 单流",
                summary = "测试失败：$message",
                lines = lines,
                success = false
            )
        } finally {
            if (dataFd >= 0) close(dataFd)
            if (controlFd >= 0) close(controlFd)
        }
    }

    suspend fun loadedLatency(
        host: String,
        port: Int = 80,
        ipv6: Boolean = false,
        onLine: suspend (String) -> Unit
    ): ToolReport = withContext(Dispatchers.Default) {
        val cleanHost = cleanHost(host)
        val cleanPort = port.coerceIn(1, 65_535)
        val family = if (ipv6) AF_INET6 else AF_INET
        val familyName = if (ipv6) "IPv6" else "IPv4"
        val lines = mutableListOf<String>()
        suspend fun emit(line: String) {
            lines += line
            onLine(line)
        }

        try {
            emit("开始 $familyName TCP 握手延迟对比：$cleanHost:$cleanPort")
            val baseline = mutableListOf<Double>()
            repeat(8) { index ->
                currentCoroutineContext().ensureActive()
                val sample = connectBatch(cleanHost, cleanPort, family, 1, 1_500).firstOrNull()
                if (sample != null) baseline += sample
                emit("空载 ${index + 1}/8：${sample?.let(::formatMs) ?: "失败"}")
                delay(120L)
            }

            if (baseline.isEmpty()) {
                val message = "空载探测全部失败，无法形成延迟结论"
                emit(message)
                return@withContext ToolReport("TCP 负载延迟", message, lines, false)
            }

            val loaded = mutableListOf<Double>()
            repeat(8) { index ->
                currentCoroutineContext().ensureActive()
                // Index 0 is the observed handshake; the other 11 sockets create bounded handshake pressure.
                val sample = connectBatch(cleanHost, cleanPort, family, 12, 1_500).firstOrNull()
                if (sample != null) loaded += sample
                emit("负载 ${index + 1}/8：${sample?.let(::formatMs) ?: "失败"}（12 路握手）")
                delay(120L)
            }

            if (loaded.isEmpty()) {
                val message = "负载阶段探测全部失败，无法形成增量结论"
                emit(message)
                return@withContext ToolReport("TCP 负载延迟", message, lines, false)
            }

            val baselineMedian = percentile(baseline, 0.5)
            val loadedMedian = percentile(loaded, 0.5)
            val baselineP95 = percentile(baseline, 0.95)
            val loadedP95 = percentile(loaded, 0.95)
            val delta = loadedMedian - baselineMedian
            val percent = if (baselineMedian > 0.0) delta * 100.0 / baselineMedian else null
            val summary = buildString {
                append("中位数增量 ${formatSignedMs(delta)}")
                percent?.let { append(" (${formatSignedPercent(it)})") }
            }
            emit("空载中位数 ${formatMs(baselineMedian)}，P95 ${formatMs(baselineP95)}")
            emit("负载中位数 ${formatMs(loadedMedian)}，P95 ${formatMs(loadedP95)}")
            emit("该结果仅表示 TCP 握手压力下的延迟变化，不代表带宽或 Bufferbloat 评分")
            ToolReport("TCP 负载延迟", summary, lines, true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val message = "测试失败，无结论：${error.message ?: error::class.simpleName ?: "未知错误"}"
            runCatching { emit(message) }
            ToolReport("TCP 负载延迟", message, lines, false)
        }
    }

    private suspend fun transfer(
        controlFd: Int,
        dataFd: Int,
        reverse: Boolean,
        durationSec: Int,
        emit: suspend (String) -> Unit
    ): TransferResult {
        val buffer = ByteArray(TCP_BLOCK_SIZE) { (it and 0x7f).toByte() }
        val startedAt = monotonicNanos()
        val deadline = startedAt + durationSec.toLong() * 1_000_000_000L
        var lastReportAt = startedAt
        var lastReportBytes = 0L
        var bytes = 0L

        buffer.usePinned { pinned ->
            while (monotonicNanos() < deadline) {
                currentCoroutineContext().ensureActive()
                memScoped {
                    val fds = allocArray<pollfd>(2)
                    fds[0].fd = controlFd
                    fds[0].events = (POLLIN or POLLERR or POLLHUP).toShort()
                    fds[1].fd = dataFd
                    fds[1].events = ((if (reverse) POLLIN else POLLOUT) or POLLERR or POLLHUP).toShort()
                    val remainingMs = ((deadline - monotonicNanos()) / 1_000_000L).coerceIn(1L, 50L).toInt()
                    val ready = poll(fds, 2u, remainingMs)
                    if (ready < 0 && errno != EINTR) failErrno("传输 poll")
                    if (ready > 0) {
                        val controlEvents = fds[0].revents.toInt()
                        if (controlEvents and (POLLIN or POLLERR or POLLHUP) != 0) {
                            val state = readState(controlFd, "传输控制消息")
                            throw stateFailure(controlFd, state, "传输阶段")
                        }
                        val dataEvents = fds[1].revents.toInt()
                        if (dataEvents and (POLLERR or POLLHUP) != 0) {
                            throw ToolFailure("数据流被服务端关闭")
                        }
                        if (reverse && dataEvents and POLLIN != 0) {
                            val count = recv(dataFd, pinned.addressOf(0), buffer.size.convert(), 0)
                            when {
                                count > 0 -> bytes += count.toLong()
                                count == 0L -> throw ToolFailure("数据流提前结束")
                                errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR -> failErrno("接收数据")
                            }
                        } else if (!reverse && dataEvents and POLLOUT != 0) {
                            val count = send(dataFd, pinned.addressOf(0), buffer.size.convert(), 0)
                            when {
                                count > 0 -> bytes += count.toLong()
                                count == 0L -> throw ToolFailure("数据流无法继续发送")
                                errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR -> failErrno("发送数据")
                            }
                        }
                    }
                }

                val now = monotonicNanos()
                if (now - lastReportAt >= 1_000_000_000L) {
                    val intervalBps = (bytes - lastReportBytes).toDouble() * 8.0 * 1_000_000_000.0 /
                        (now - lastReportAt).toDouble()
                    emit("${((now - startedAt) / 1_000_000_000L).coerceAtLeast(1L)}s：${formatRate(intervalBps)}")
                    lastReportAt = now
                    lastReportBytes = bytes
                }
            }
        }
        return TransferResult(bytes, (monotonicNanos() - startedAt).coerceAtLeast(1L))
    }

    private suspend fun connectTcp(host: String, port: Int, family: Int, timeoutMs: Int): Int {
        val deadline = deadlineAfter(timeoutMs)
        var connectedFd = -1
        var lastError = "无可用地址"
        memScoped {
            val hints = alloc<addrinfo>().apply {
                ai_family = family
                ai_socktype = SOCK_STREAM
                ai_protocol = IPPROTO_TCP
                ai_flags = if (family == AF_UNSPEC) AI_ADDRCONFIG else 0
            }
            val result = allocPointerTo<addrinfo>()
            result.value = null
            val status = getaddrinfo(host, port.toString(), hints.ptr, result.ptr)
            val head = result.value
            if (status != 0 || head == null) {
                throw ToolFailure(gai_strerror(status)?.toKString() ?: "DNS 解析失败 ($status)")
            }
            try {
                var current: CPointer<addrinfo>? = head
                while (current != null && connectedFd < 0) {
                    currentCoroutineContext().ensureActive()
                    val fd = socket(current.pointed.ai_family, SOCK_STREAM, IPPROTO_TCP)
                    if (fd >= 0) {
                        try {
                            try {
                                configure(fd)
                                val resultCode = connect(fd, current.pointed.ai_addr, current.pointed.ai_addrlen)
                                if (resultCode == 0 || (errno == EINPROGRESS && waitConnected(fd, deadline))) {
                                    connectedFd = fd
                                } else {
                                    lastError = errnoText("连接")
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: ToolFailure) {
                                lastError = error.message ?: "连接失败"
                            }
                        } finally {
                            if (connectedFd != fd) close(fd)
                        }
                    }
                    current = current.pointed.ai_next
                }
            } finally {
                freeaddrinfo(head)
            }
        }
        if (connectedFd < 0) throw ToolFailure(lastError)
        return connectedFd
    }

    private suspend fun connectBatch(
        host: String,
        port: Int,
        family: Int,
        count: Int,
        timeoutMs: Int
    ): List<Double?> {
        val attempts = mutableListOf<ConnectAttempt>()
        try {
            memScoped {
                val hints = alloc<addrinfo>().apply {
                    ai_family = family
                    ai_socktype = SOCK_STREAM
                    ai_protocol = IPPROTO_TCP
                }
                val result = allocPointerTo<addrinfo>()
                result.value = null
                val status = getaddrinfo(host, port.toString(), hints.ptr, result.ptr)
                val address = result.value
                if (status != 0 || address == null) {
                    throw ToolFailure(gai_strerror(status)?.toKString() ?: "DNS 解析失败 ($status)")
                }
                try {
                    repeat(count.coerceIn(1, 32)) { index ->
                        currentCoroutineContext().ensureActive()
                        val fd = socket(address.pointed.ai_family, SOCK_STREAM, IPPROTO_TCP)
                        if (fd < 0) {
                            attempts += ConnectAttempt(index, -1, monotonicNanos(), done = true)
                        } else {
                            try {
                                configure(fd)
                                val startedAt = monotonicNanos()
                                val resultCode = connect(fd, address.pointed.ai_addr, address.pointed.ai_addrlen)
                                val immediateLatency = if (resultCode == 0) {
                                    (monotonicNanos() - startedAt) / 1_000_000.0
                                } else null
                                attempts += ConnectAttempt(index, fd, startedAt, latencyMs = immediateLatency, done = resultCode == 0)
                                if (resultCode != 0 && errno != EINPROGRESS) attempts.last().done = true
                            } catch (error: Throwable) {
                                close(fd)
                                throw error
                            }
                        }
                    }

                    val deadline = deadlineAfter(timeoutMs)
                    while (attempts.any { !it.done } && monotonicNanos() < deadline) {
                        currentCoroutineContext().ensureActive()
                        val pending = attempts.filterNot { it.done }
                        memScoped {
                            val fds = allocArray<pollfd>(pending.size)
                            pending.forEachIndexed { index, attempt ->
                                fds[index].fd = attempt.fd
                                fds[index].events = (POLLOUT or POLLERR or POLLHUP).toShort()
                            }
                            val waitMs = ((deadline - monotonicNanos()) / 1_000_000L).coerceIn(1L, 50L).toInt()
                            val ready = poll(fds, pending.size.convert(), waitMs)
                            if (ready < 0 && errno != EINTR) failErrno("握手 poll")
                            if (ready > 0) pending.forEachIndexed { index, attempt ->
                                if (fds[index].revents.toInt() != 0) {
                                    attempt.done = true
                                    if (socketError(attempt.fd) == 0) {
                                        attempt.latencyMs = (monotonicNanos() - attempt.startedAtNanos) / 1_000_000.0
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    freeaddrinfo(address)
                }
            }
            return attempts.sortedBy { it.index }.map { it.latencyMs }
        } finally {
            attempts.forEach { if (it.fd >= 0) close(it.fd) }
        }
    }

    private suspend fun expectState(fd: Int, expected: Int, name: String) {
        val state = readState(fd, name)
        if (state != expected) throw stateFailure(fd, state, "等待 $name")
    }

    private suspend fun readState(fd: Int, operation: String): Int =
        readExact(fd, 1, deadlineAfter(CONTROL_TIMEOUT_MS), operation)[0].toInt() and 0xff

    private suspend fun stateFailure(fd: Int, state: Int, stage: String): ToolFailure = when (state) {
        ACCESS_DENIED -> ToolFailure("服务端忙或拒绝访问")
        SERVER_TERMINATE -> ToolFailure("服务端提前终止测试")
        SERVER_ERROR -> {
            val values = readExact(fd, 8, deadlineAfter(CONTROL_TIMEOUT_MS), "读取服务端错误")
            val iperfError = readInt32(values, 0)
            val systemError = readInt32(values, 4)
            val systemDetail = if (systemError > 0) {
                strerror(systemError)?.toKString()?.let { ": $it" }.orEmpty()
            } else ""
            ToolFailure("服务端错误 i_errno=$iperfError, errno=$systemError$systemDetail")
        }
        else -> ToolFailure("$stage 收到意外状态 $state (${stateName(state)})")
    }

    private suspend fun writeState(fd: Int, state: Int, name: String) {
        writeAll(fd, byteArrayOf(state.toByte()), deadlineAfter(CONTROL_TIMEOUT_MS), "发送 $name")
    }

    private suspend fun writeJson(fd: Int, value: JsonObject, operation: String) {
        val payload = value.toString().encodeToByteArray()
        require(payload.size in 1..MAX_JSON_SIZE)
        val frame = ByteArray(4 + payload.size)
        writeInt32(frame, 0, payload.size)
        payload.copyInto(frame, 4)
        writeAll(fd, frame, deadlineAfter(CONTROL_TIMEOUT_MS), operation)
    }

    private suspend fun readJson(fd: Int, operation: String): JsonObject {
        val deadline = deadlineAfter(CONTROL_TIMEOUT_MS)
        val length = readInt32(readExact(fd, 4, deadline, "$operation 长度"), 0)
        if (length !in 1..MAX_JSON_SIZE) throw ToolFailure("$operation 的 JSON 长度无效: $length")
        val payload = readExact(fd, length, deadline, operation)
        return try {
            Json.parseToJsonElement(payload.decodeToString()).jsonObject
        } catch (error: Throwable) {
            throw ToolFailure("$operation JSON 无效: ${error.message ?: "解析失败"}")
        }
    }

    private suspend fun readExact(fd: Int, count: Int, deadline: Long, operation: String): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        bytes.usePinned { pinned ->
            while (offset < count) {
                currentCoroutineContext().ensureActive()
                val received = recv(fd, pinned.addressOf(offset), (count - offset).convert(), 0)
                when {
                    received > 0 -> offset += received.toInt()
                    received == 0L -> throw ToolFailure("$operation 时连接关闭")
                    errno == EINTR -> Unit
                    errno == EAGAIN || errno == EWOULDBLOCK -> waitFd(fd, POLLIN, deadline, operation)
                    else -> failErrno(operation)
                }
            }
        }
        return bytes
    }

    private suspend fun writeAll(fd: Int, bytes: ByteArray, deadline: Long, operation: String) {
        var offset = 0
        bytes.usePinned { pinned ->
            while (offset < bytes.size) {
                currentCoroutineContext().ensureActive()
                val written = send(fd, pinned.addressOf(offset), (bytes.size - offset).convert(), 0)
                when {
                    written > 0 -> offset += written.toInt()
                    written == 0L -> throw ToolFailure("$operation 无法继续")
                    errno == EINTR -> Unit
                    errno == EAGAIN || errno == EWOULDBLOCK -> waitFd(fd, POLLOUT, deadline, operation)
                    else -> failErrno(operation)
                }
            }
        }
    }

    private suspend fun waitConnected(fd: Int, deadline: Long): Boolean {
        waitFd(fd, POLLOUT, deadline, "连接")
        return socketError(fd) == 0
    }

    private suspend fun waitFd(fd: Int, event: Int, deadline: Long, operation: String) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val remainingNanos = deadline - monotonicNanos()
            if (remainingNanos <= 0L) throw ToolFailure("$operation 超时")
            memScoped {
                val item = alloc<pollfd>().apply {
                    this.fd = fd
                    events = (event or POLLERR or POLLHUP).toShort()
                }
                val result = poll(item.ptr, 1u, (remainingNanos / 1_000_000L).coerceIn(1L, 50L).toInt())
                if (result > 0) {
                    if (item.revents.toInt() and (POLLERR or POLLHUP) != 0) {
                        val error = socketError(fd)
                        if (error != 0) failErrno(operation, error)
                    }
                    return
                }
                if (result < 0 && errno != EINTR) failErrno(operation)
            }
        }
    }

    private fun configure(fd: Int) = memScoped {
        val one = alloc<IntVar>().apply { value = 1 }
        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, one.ptr, sizeOf<IntVar>().convert())
        val flags = fcntl(fd, F_GETFL, 0)
        if (flags < 0 || fcntl(fd, F_SETFL, flags or O_NONBLOCK) < 0) failErrno("设置非阻塞模式")
    }

    private fun socketError(fd: Int): Int = memScoped {
        val value = alloc<IntVar>()
        val length = alloc<socklen_tVar>().apply { this.value = sizeOf<IntVar>().convert() }
        if (getsockopt(fd, SOL_SOCKET, SO_ERROR, value.ptr, length.ptr) < 0) errno else value.value
    }

    private fun cookie(): ByteArray {
        val alphabet = "abcdefghijklmnopqrstuvwxyz234567"
        return ByteArray(COOKIE_SIZE).also { bytes ->
            repeat(COOKIE_SIZE - 1) { bytes[it] = alphabet[Random.nextInt(alphabet.length)].code.toByte() }
            bytes[COOKIE_SIZE - 1] = 0
        }
    }

    private fun writeInt32(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt32(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xff) shl 24) or
            ((source[offset + 1].toInt() and 0xff) shl 16) or
            ((source[offset + 2].toInt() and 0xff) shl 8) or
            (source[offset + 3].toInt() and 0xff)

    private fun stateName(state: Int): String = when (state) {
        TEST_START -> "TEST_START"
        TEST_RUNNING -> "TEST_RUNNING"
        TEST_END -> "TEST_END"
        PARAM_EXCHANGE -> "PARAM_EXCHANGE"
        CREATE_STREAMS -> "CREATE_STREAMS"
        SERVER_TERMINATE -> "SERVER_TERMINATE"
        EXCHANGE_RESULTS -> "EXCHANGE_RESULTS"
        DISPLAY_RESULTS -> "DISPLAY_RESULTS"
        IPERF_DONE -> "IPERF_DONE"
        ACCESS_DENIED -> "ACCESS_DENIED"
        SERVER_ERROR -> "SERVER_ERROR"
        else -> "UNKNOWN"
    }

    private fun percentile(values: List<Double>, quantile: Double): Double {
        val sorted = values.sorted()
        if (sorted.size == 1) return sorted[0]
        val position = (sorted.size - 1) * quantile.coerceIn(0.0, 1.0)
        val lower = position.toInt()
        val upper = minOf(lower + 1, sorted.lastIndex)
        val fraction = position - lower
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction
    }

    private fun formatRate(bitsPerSecond: Double): String = when {
        bitsPerSecond >= 1_000_000_000.0 -> "${decimal(bitsPerSecond / 1_000_000_000.0, 2)} Gbit/s"
        bitsPerSecond >= 1_000_000.0 -> "${decimal(bitsPerSecond / 1_000_000.0, 2)} Mbit/s"
        bitsPerSecond >= 1_000.0 -> "${decimal(bitsPerSecond / 1_000.0, 2)} Kbit/s"
        else -> "${decimal(bitsPerSecond, 0)} bit/s"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "${decimal(bytes / 1_073_741_824.0, 2)} GiB"
        bytes >= 1_048_576L -> "${decimal(bytes / 1_048_576.0, 2)} MiB"
        bytes >= 1_024L -> "${decimal(bytes / 1_024.0, 2)} KiB"
        else -> "$bytes B"
    }

    private fun formatMs(value: Double): String = "${decimal(value, 2)} ms"
    private fun formatSignedMs(value: Double): String = "${decimal(value, 2, alwaysSign = true)} ms"
    private fun formatSignedPercent(value: Double): String = "${decimal(value, 1, alwaysSign = true)}%"

    /** Locale-independent finite decimal rendering for Kotlin/Native. */
    private fun decimal(value: Double, digits: Int, alwaysSign: Boolean = false): String {
        if (!value.isFinite()) return "--"
        require(digits in 0..3)
        val factor = when (digits) {
            0 -> 1L
            1 -> 10L
            2 -> 100L
            else -> 1_000L
        }
        val magnitude = abs(value)
        if (magnitude > Long.MAX_VALUE.toDouble() / factor) {
            val sign = if (value < 0.0) "-" else if (alwaysSign) "+" else ""
            return sign + magnitude.toLong().toString()
        }
        val scaled = round(magnitude * factor).toLong()
        val whole = scaled / factor
        val sign = if (value < 0.0) "-" else if (alwaysSign) "+" else ""
        if (digits == 0) return "$sign$whole"
        val fraction = (scaled % factor).toString().padStart(digits, '0')
        return "$sign$whole.$fraction"
    }
    private fun cleanHost(host: String) = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "localhost" }
    private fun deadlineAfter(timeoutMs: Int) = monotonicNanos() + timeoutMs.toLong() * 1_000_000L
    private fun errnoText(operation: String, code: Int = errno) =
        "$operation失败 (errno=$code: ${strerror(code)?.toKString() ?: "unknown"})"
    private fun failErrno(operation: String, code: Int = errno): Nothing = throw ToolFailure(errnoText(operation, code))

    private fun monotonicNanos(): Long = memScoped {
        val time = alloc<timespec>()
        if (clock_gettime(CLOCK_MONOTONIC, time.ptr) == 0) {
            time.tv_sec * 1_000_000_000L + time.tv_nsec
        } else {
            com.demonv.netsessiontester.ios.getMonotonicMs() * 1_000_000L
        }
    }

    private data class TransferResult(val bytes: Long, val elapsedNanos: Long)
    private data class ConnectAttempt(
        val index: Int,
        val fd: Int,
        val startedAtNanos: Long,
        var latencyMs: Double? = null,
        var done: Boolean = false
    )

    private class ToolFailure(message: String) : Exception(message)
}
