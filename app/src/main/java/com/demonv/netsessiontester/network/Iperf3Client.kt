package com.demonv.netsessiontester.network

import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.UUID

internal data class Iperf3Sample(
    val elapsedSeconds: Double,
    val mbps: Double,
    val bytes: Long,
    val totalBytes: Long
)

internal data class Iperf3Result(
    val averageMbps: Double,
    val peakMbps: Double,
    val totalBytes: Long
)

/** Single-stream TCP client. Wire states/framing follow ESnet iperf's client API.
 * The control connection carries only states/JSON; payload uses a second connection.
 * Closing the client interrupts connects, handshakes and transfers, including blocked I/O.
 */
internal class Iperf3Client : Closeable {
    private val channels = mutableListOf<SocketChannel>()
    @Volatile private var closed = false

    override fun close() {
        val open = synchronized(channels) {
            closed = true
            channels.toList().also { channels.clear() }
        }
        open.forEach { runCatching { it.close() } }
    }

    fun run(
        host: String,
        port: Int,
        durationSeconds: Int,
        reverse: Boolean,
        onSample: (Iperf3Sample) -> Unit = {}
    ): Iperf3Result {
        require(host.isNotBlank()) { "请输入 iPerf3 服务器地址" }
        require(port in 1..65535) { "端口必须为 1–65535" }
        require(durationSeconds in 1..60) { "测试时长必须为 1–60 秒" }
        try {
            val control = connect(InetSocketAddress(host, port))
            val input = DataInputStream(control.socket().getInputStream())
            val output = DataOutputStream(control.socket().getOutputStream())
            val cookie = (UUID.randomUUID().toString() + '\u0000').toByteArray(Charsets.US_ASCII)
            output.write(cookie)
            output.flush()
            expectState(input, PARAM_EXCHANGE)
            val parameters = JSONObject()
                .put("tcp", true).put("omit", 0).put("time", durationSeconds)
                .put("parallel", 1).put("len", BLOCK_SIZE)
                .put("client_version", "NetSessionTester")
            // iperf treats the presence of this key as true, even when its value is 0.
            if (reverse) parameters.put("reverse", true)
            writeJson(output, parameters)
            expectState(input, CREATE_STREAMS)

            // Reuse the resolved control peer, not a second DNS answer for the data stream.
            val data = connect(control.socket().remoteSocketAddress as InetSocketAddress)
            data.socket().getOutputStream().apply { write(cookie); flush() }
            expectState(input, TEST_START)
            expectState(input, TEST_RUNNING)

            val measurement = transfer(data, input, durationSeconds, reverse, onSample)
            output.writeByte(TEST_END)
            output.flush()
            expectState(input, EXCHANGE_RESULTS)
            writeJson(output, localResults(measurement, reverse))
            val remote = readJson(input)
            expectState(input, DISPLAY_RESULTS)
            output.writeByte(IPERF_DONE)
            output.flush()

            val stream = remote.getJSONArray("streams").getJSONObject(0)
            if (stream.getInt("id") != 1) throw IOException("iPerf3 返回了无效的数据流编号")
            val receivedBytes = if (reverse) measurement.totalBytes else stream.getLong("bytes")
            val seconds = if (reverse) measurement.elapsedSeconds else
                stream.optDouble("end_time", measurement.elapsedSeconds) - stream.optDouble("start_time", 0.0)
            if (receivedBytes <= 0 || !seconds.isFinite() || seconds <= 0.0) {
                throw IOException("未收到有效测速数据，请确认目标运行的是 iPerf3 服务端")
            }
            return Iperf3Result(receivedBytes * 8.0 / seconds / 1_000_000.0, measurement.peakMbps, receivedBytes)
        } finally {
            close()
        }
    }

    private fun connect(address: InetSocketAddress): SocketChannel {
        val channel = SocketChannel.open()
        synchronized(channels) {
            if (closed) {
                channel.close()
                throw IOException("测速已停止")
            }
            channels.add(channel)
        }
        channel.socket().apply {
            tcpNoDelay = true
            soTimeout = 5000
            connect(address, 3500)
        }
        return channel
    }

    private data class Measurement(val totalBytes: Long, val elapsedSeconds: Double, val peakMbps: Double)

    private fun transfer(
        data: SocketChannel,
        control: DataInputStream,
        seconds: Int,
        reverse: Boolean,
        onSample: (Iperf3Sample) -> Unit
    ): Measurement {
        val payload = ByteArray(BLOCK_SIZE)
        if (!reverse) kotlin.random.Random.nextBytes(payload)
        val buffer = ByteBuffer.wrap(payload)
        var total = 0L
        var sampledBytes = 0L
        var peak = 0.0
        val started = System.nanoTime()
        var sampledAt = started
        val deadline = started + seconds * 1_000_000_000L
        var finished = started

        fun sample(now: Long) {
            val elapsed = (now - sampledAt) / 1_000_000_000.0
            if (elapsed <= 0) return
            val bytes = total - sampledBytes
            val mbps = bytes * 8.0 / elapsed / 1_000_000.0
            peak = maxOf(peak, mbps)
            onSample(Iperf3Sample((now - started) / 1_000_000_000.0, mbps, bytes, total))
            sampledBytes = total
            sampledAt = now
        }

        data.configureBlocking(false)
        Selector.open().use { selector ->
            data.register(selector, if (reverse) SelectionKey.OP_READ else SelectionKey.OP_WRITE)
            while (!closed) {
                val now = System.nanoTime()
                finished = now
                if (now >= deadline) break
                if (control.available() > 0) {
                    val state = readState(control)
                    throw IOException("iPerf3 在传输期间返回了意外状态：$state")
                }
                if (now - sampledAt >= 1_000_000_000L) sample(now)
                selector.select(minOf(100L, ((deadline - now) / 1_000_000L).coerceAtLeast(1)))
                val ready = selector.selectedKeys().isNotEmpty()
                selector.selectedKeys().clear()
                if (!ready) continue
                val count = if (reverse) {
                    buffer.clear()
                    data.read(buffer)
                } else {
                    if (!buffer.hasRemaining()) buffer.rewind()
                    data.write(buffer)
                }
                if (count < 0) {
                    finished = System.nanoTime()
                    // The server's timer starts just before TEST_RUNNING reaches the client.
                    if (deadline - finished > 500_000_000L) throw IOException("iPerf3 数据连接提前关闭")
                    break
                }
                total += count
            }
        }
        if (closed) throw IOException("测速已停止")
        if (total > sampledBytes || sampledAt == started) sample(finished)
        return Measurement(total, (finished - started) / 1_000_000_000.0, peak)
    }

    private fun localResults(measurement: Measurement, reverse: Boolean): JSONObject {
        val stream = JSONObject().put("id", 1).put("bytes", measurement.totalBytes)
            .put("retransmits", -1).put("jitter", 0).put("errors", 0).put("packets", 0)
            .put("start_time", 0).put("end_time", measurement.elapsedSeconds)
        return JSONObject().put("cpu_util_total", 0).put("cpu_util_user", 0).put("cpu_util_system", 0)
            .put("sender_has_retransmits", if (reverse) -1 else 0)
            .put("streams", JSONArray().put(stream))
    }

    private fun readState(input: DataInputStream): Int {
        val state = input.read()
        when (state) {
            -1 -> throw IOException("服务器关闭了控制连接，请确认目标是 iPerf3 服务端")
            255 -> throw IOException("iPerf3 服务器正忙，请稍后重试")
            254 -> {
                val code = input.readInt()
                val systemCode = input.readInt()
                throw IOException("iPerf3 服务端拒绝测试（错误 $code，系统错误 $systemCode）")
            }
            SERVER_TERMINATE -> throw IOException("iPerf3 服务端终止了测试")
        }
        return state
    }

    private fun expectState(input: DataInputStream, expected: Int) {
        val state = readState(input)
        if (state != expected) throw IOException("iPerf3 协议不匹配（状态 $state，预期 $expected），请使用 iPerf3 而非 iPerf2/HTTP 服务")
    }

    private fun writeJson(output: DataOutputStream, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun readJson(input: DataInputStream): JSONObject {
        val size = input.readInt()
        if (size !in 1..1_048_576) throw IOException("iPerf3 返回了无效的结果长度：$size")
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private companion object {
        const val BLOCK_SIZE = 65536
        const val TEST_START = 1
        const val TEST_RUNNING = 2
        const val TEST_END = 4
        const val PARAM_EXCHANGE = 9
        const val CREATE_STREAMS = 10
        const val SERVER_TERMINATE = 11
        const val EXCHANGE_RESULTS = 13
        const val DISPLAY_RESULTS = 14
        const val IPERF_DONE = 16
    }
}
