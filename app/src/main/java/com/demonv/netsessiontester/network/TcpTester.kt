package com.demonv.netsessiontester.network

import com.demonv.netsessiontester.model.BatchResult
import com.demonv.netsessiontester.model.IpMode
import com.demonv.netsessiontester.model.SingleConnectionResult
import com.demonv.netsessiontester.model.TestConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.ceil
import kotlin.system.measureTimeMillis

class TcpTester {
    suspend fun runIncrementalTest(
        rawConfig: TestConfig,
        onStatus: suspend (String) -> Unit,
        onBatch: suspend (BatchResult) -> Unit
    ): List<BatchResult> {
        val config = rawConfig.normalized()
        require(config.host.isNotBlank()) { "请填写目标地址，例如你的 VPS 域名、路由器 IP 或内网服务器。" }
        require(config.maxConcurrency >= config.startConcurrency) { "最大并发不能小于起始并发。" }

        val addresses = resolveAddresses(config.host, config.ipMode)
        require(addresses.isNotEmpty()) { "没有解析到符合 ${config.ipMode.label} 的地址。" }

        val batches = mutableListOf<BatchResult>()
        var concurrency = config.startConcurrency

        while (currentCoroutineContext().isActive && concurrency <= config.maxConcurrency) {
            onStatus("正在测试 $concurrency 并发，目标 ${config.host}:${config.port}")
            val batch = runBatch(config, addresses, concurrency)
            batches += batch
            onBatch(batch)

            if (batch.failureRate >= config.failureStopRate) {
                onStatus("失败率达到 ${batch.failureRateText()}，已自动停止。")
                break
            }

            concurrency += config.step
            if (concurrency <= config.maxConcurrency) {
                delay(600)
            }
        }

        return batches
    }

    suspend fun resolveAddresses(host: String, mode: IpMode): List<InetAddress> = withContext(Dispatchers.IO) {
        val cleaned = host.trim().removePrefix("[").removeSuffix("]")
        val all = InetAddress.getAllByName(cleaned).toList()
        all.filter { address ->
            when (mode) {
                IpMode.AUTO -> address is Inet4Address || address is Inet6Address
                IpMode.IPV4_ONLY -> address is Inet4Address
                IpMode.IPV6_ONLY -> address is Inet6Address
            }
        }.distinctBy { it.hostAddress }
    }

    private suspend fun runBatch(
        config: TestConfig,
        addresses: List<InetAddress>,
        concurrency: Int
    ): BatchResult = coroutineScope {
        val results = mutableListOf<SingleConnectionResult>()
        val elapsed = measureTimeMillis {
            val jobs = (0 until concurrency).map { index ->
                async(Dispatchers.IO) {
                    val address = addresses[index % addresses.size]
                    testSingleConnection(address, config.port, config.timeoutMs, config.holdMs)
                }
            }
            results += jobs.awaitAll()
        }

        val latencies = results.mapNotNull { it.latencyMs }.sorted()
        val errors = results.filter { !it.success }
            .groupingBy { it.error ?: "Unknown" }
            .eachCount()
            .toSortedMap(compareByDescending<String> { errors ->
                results.count { it.error == errors }
            }.thenBy { it })

        BatchResult(
            concurrency = concurrency,
            successCount = results.count { it.success },
            failureCount = results.count { !it.success },
            avgLatencyMs = latencies.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            p95LatencyMs = percentile(latencies, 0.95),
            minLatencyMs = latencies.firstOrNull(),
            maxLatencyMs = latencies.lastOrNull(),
            elapsedMs = elapsed,
            errorSummary = errors,
            addresses = addresses.map { it.hostAddress ?: it.hostName }
        )
    }

    private fun testSingleConnection(
        address: InetAddress,
        port: Int,
        timeoutMs: Int,
        holdMs: Long
    ): SingleConnectionResult {
        val target = address.hostAddress ?: address.hostName
        val start = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                Thread.sleep(holdMs)
            }
            val latency = (System.nanoTime() - start) / 1_000_000
            SingleConnectionResult(true, latency, target, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SingleConnectionResult(false, null, target, e.javaClass.simpleName)
        }
    }

    private fun percentile(sortedValues: List<Long>, percentile: Double): Long? {
        if (sortedValues.isEmpty()) return null
        val index = ceil(percentile * sortedValues.size).toInt().coerceIn(1, sortedValues.size) - 1
        return sortedValues[index]
    }
}
