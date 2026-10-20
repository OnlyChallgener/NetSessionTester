package com.demonv.netsessiontester.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Loopback peer verifies actual production-client bytes, not a second client implementation. */
class Iperf3ClientTest {
    private fun readJson(input: DataInputStream): JSONObject {
        val bytes = ByteArray(input.readInt().also { assertTrue(it in 1..1_048_576) })
        input.readFully(bytes)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private fun writeJson(output: DataOutputStream, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        // Fragment both the length and body to exercise readFully/framing.
        val header = byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(),
            (bytes.size ushr 8).toByte(), bytes.size.toByte())
        header.forEach { output.writeByte(it.toInt()); output.flush(); Thread.sleep(1) }
        bytes.asList().chunked(11).forEach { part -> output.write(part.toByteArray()); output.flush() }
    }

    private fun <T> withServer(script: (ServerSocket) -> Unit, test: (Int) -> T): T {
        ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 5000
            val executor = Executors.newSingleThreadExecutor()
            val peer = executor.submit(Callable { script(listener) })
            try {
                val result = test(listener.localPort)
                peer.get(8, TimeUnit.SECONDS)
                return result
            } finally {
                listener.close()
                executor.shutdownNow()
            }
        }
    }

    private fun cookie(input: DataInputStream): ByteArray = ByteArray(37).also {
        input.readFully(it)
        assertEquals(0.toByte(), it.last())
    }

    private fun parameters(socket: Socket, reverse: Boolean): ByteArray {
        socket.soTimeout = 5000
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val cookie = cookie(input)
        output.writeByte(9)
        val parameters = readJson(input)
        assertTrue(parameters.getBoolean("tcp"))
        assertEquals(65536, parameters.getInt("len"))
        assertEquals(1, parameters.getInt("parallel"))
        assertEquals(1, parameters.getInt("time"))
        assertEquals(reverse, parameters.has("reverse"))
        if (reverse) assertTrue(parameters.getBoolean("reverse"))
        output.writeByte(10)
        return cookie
    }

    private fun peer(reverse: Boolean, empty: Boolean = false, invalidLength: Boolean = false): (ServerSocket) -> Unit = { listener ->
        listener.accept().use { control ->
            val expectedCookie = parameters(control, reverse)
            val input = DataInputStream(control.getInputStream())
            val output = DataOutputStream(control.getOutputStream())
            listener.accept().use { data ->
                data.soTimeout = 100
                assertArrayEquals(expectedCookie, cookie(DataInputStream(data.getInputStream())))
                output.writeByte(1)
                output.writeByte(2)
                output.flush()
                if (reverse) {
                    if (!empty) data.getOutputStream().write(ByteArray(65536) { 42 })
                } else {
                    val buffer = ByteArray(65536)
                    var bytes = 0L
                    while (input.available() == 0) {
                        try {
                            val count = data.getInputStream().read(buffer)
                            if (count < 0) break
                            bytes += count
                        } catch (_: SocketTimeoutException) { }
                    }
                    assertTrue("Upload must use the data connection", bytes > 0)
                }
                assertEquals(4, input.readUnsignedByte())
                data.close()
                output.writeByte(13)
                val results = readJson(input)
                assertEquals(if (reverse) -1 else 0, results.getInt("sender_has_retransmits"))
                val local = results.getJSONArray("streams").getJSONObject(0)
                assertEquals(1, local.getInt("id"))
                assertTrue(local.getDouble("end_time") > 0)
                if (reverse) assertEquals(if (empty) 0L else 65536L, local.getLong("bytes"))
                if (invalidLength) {
                    output.writeInt(1_048_577)
                    output.flush()
                    assertEquals(-1, input.read())
                } else {
                    writeJson(output, JSONObject().put("streams", JSONArray().put(
                        JSONObject().put("id", 1).put("bytes", if (empty) 0 else 12345)
                            .put("start_time", 0.0).put("end_time", 2.0)
                    )))
                    output.writeByte(14)
                    output.flush()
                    assertEquals(16, input.readUnsignedByte())
                }
            }
        }
    }

    @Test fun downloadUsesSeparateStreamAndExchangesFragmentedResults() {
        val samples = mutableListOf<Iperf3Sample>()
        val result = withServer(peer(reverse = true)) { port ->
            Iperf3Client().run("127.0.0.1", port, 1, true) { samples.add(it) }
        }
        assertEquals(65536L, result.totalBytes)
        assertTrue(result.averageMbps > 0)
        assertEquals(result.totalBytes, samples.sumOf { it.bytes })
    }

    @Test fun uploadOmitsReverseAndUsesServerReceivedBytesAndDuration() {
        val result = withServer(peer(reverse = false)) { port ->
            Iperf3Client().run("127.0.0.1", port, 1, false)
        }
        assertEquals(12345L, result.totalBytes)
        assertEquals(12345 * 8.0 / 2 / 1_000_000, result.averageMbps, 0.000001)
    }

    @Test fun zeroBytesCannotBecomeSuccessfulSpeedResult() {
        withServer(peer(reverse = true, empty = true)) { port ->
            assertThrows(IOException::class.java) { Iperf3Client().run("127.0.0.1", port, 1, true) }
        }
    }

    @Test fun boundsServerJsonAllocation() {
        withServer(peer(reverse = true, invalidLength = true)) { port ->
            val error = assertThrows(IOException::class.java) { Iperf3Client().run("127.0.0.1", port, 1, true) }
            assertTrue(error.message.orEmpty().contains("结果长度"))
        }
    }

    @Test fun busyAndServerErrorsAreNotTreatedAsPayload() {
        for (state in listOf(255, 254, 11, 72)) {
            withServer({ listener ->
                listener.accept().use { socket ->
                    cookie(DataInputStream(socket.getInputStream()))
                    val output = DataOutputStream(socket.getOutputStream())
                    output.writeByte(state)
                    if (state == 254) { output.writeInt(37); output.writeInt(0) }
                }
            }) { port ->
                assertThrows(IOException::class.java) { Iperf3Client().run("127.0.0.1", port, 1, true) }
            }
        }
    }

    @Test fun closeInterruptsHandshakeAndAllowsImmediateRetry() {
        val ready = CountDownLatch(1)
        withServer({ listener ->
            listener.accept().use { socket ->
                socket.soTimeout = 5000
                val input = DataInputStream(socket.getInputStream())
                cookie(input)
                ready.countDown()
                assertEquals(-1, input.read())
            }
        }) { port ->
            val executor = Executors.newSingleThreadExecutor()
            val client = Iperf3Client()
            try {
                val task = executor.submit(Callable {
                    assertThrows(IOException::class.java) { client.run("127.0.0.1", port, 1, true) }
                })
                assertTrue(ready.await(2, TimeUnit.SECONDS))
                client.close()
                task.get(2, TimeUnit.SECONDS)
            } finally { client.close(); executor.shutdownNow() }
        }
        withServer(peer(reverse = true)) { port ->
            assertTrue(Iperf3Client().run("127.0.0.1", port, 1, true).totalBytes > 0)
        }
    }

    @Test fun rejectsInvalidPortBeforeConnecting() {
        assertThrows(IllegalArgumentException::class.java) { Iperf3Client().run("127.0.0.1", 65536, 1, true) }
    }

    @Test fun closeInterruptsUploadWhenReceiverStopsReading() {
        val ready = CountDownLatch(1)
        withServer({ listener ->
            listener.accept().use { control ->
                val expectedCookie = parameters(control, false)
                val input = DataInputStream(control.getInputStream())
                val output = DataOutputStream(control.getOutputStream())
                listener.accept().use { data ->
                    val dataInput = DataInputStream(data.getInputStream())
                    assertArrayEquals(expectedCookie, cookie(dataInput))
                    output.writeByte(1)
                    output.writeByte(2)
                    output.flush()
                    assertTrue(dataInput.read() >= 0)
                    ready.countDown()
                    assertEquals(-1, input.read())
                }
            }
        }) { port ->
            val executor = Executors.newSingleThreadExecutor()
            val client = Iperf3Client()
            try {
                val task = executor.submit(Callable {
                    assertThrows(IOException::class.java) { client.run("127.0.0.1", port, 1, false) }
                })
                assertTrue(ready.await(2, TimeUnit.SECONDS))
                client.close()
                task.get(2, TimeUnit.SECONDS)
            } finally { client.close(); executor.shutdownNow() }
        }
    }
}
