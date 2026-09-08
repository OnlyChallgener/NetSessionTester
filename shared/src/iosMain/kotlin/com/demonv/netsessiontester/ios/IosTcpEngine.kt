package com.demonv.netsessiontester.ios

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.posix.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * iOS 原生高性能 Socket 与网络质量诊断引擎。
 * 遵循 Apple 官方网络规范 (NAT64 / IPv6-only 兼容)，采用 POSIX 非阻塞多路复用。
 */
class IosTcpEngine {
    private val socketMutex = Mutex()
    private val heldSocketFds = mutableListOf<Int>()
    private var isTesting = false

    /**
     * 控制 iOS 屏幕常亮，防止高并发压测时系统因空闲睡眠而挂起网络连接
     */
    fun setScreenKeepAwake(keepAwake: Boolean) {
        runCatching {
            UIApplication.sharedApplication.idleTimerDisabled = keepAwake
        }
    }

    /**
     * 触发 iOS 系统原生 Taptic Engine 震动反馈
     */
    fun triggerHapticFeedback(isHeavy: Boolean = false) {
        runCatching {
            val style = if (isHeavy) {
                UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
            } else {
                UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            }
            val generator = UIImpactFeedbackGenerator(style)
            generator.prepare()
            generator.impactOccurred()
        }
    }

    /**
     * 解析域名 DNS，支持 IPv4 与 IPv6 双栈寻址 (符合 Apple App Store NAT64 规范)
     */
    @OptIn(ExperimentalForeignApi::class)
    suspend fun resolveHost(host: String): IosResolveResult = withContext(Dispatchers.Default) {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        val v4List = mutableListOf<String>()
        val v6List = mutableListOf<String>()
        var errorMsg: String? = null

        memScoped {
            val hints = alloc<addrinfo>()
            hints.ai_family = AF_UNSPEC
            hints.ai_socktype = SOCK_STREAM
            val res = allocPointerTo<addrinfo>()

            val status = getaddrinfo(cleanHost, null, hints.ptr, res.ptr)
            if (status == 0) {
                var curr = res.value
                val hostBuf = allocArray<ByteVar>(128)
                while (curr != null) {
                    val family = curr.pointed.ai_family
                    if (family == AF_INET) {
                        val sin = curr.pointed.ai_addr?.reinterpret<sockaddr_in>()
                        if (sin != null) {
                            val addrVal = sin.pointed.sin_addr.s_addr
                            val b0 = addrVal and 0xFFu
                            val b1 = (addrVal shr 8) and 0xFFu
                            val b2 = (addrVal shr 16) and 0xFFu
                            val b3 = (addrVal shr 24) and 0xFFu
                            val ip = "$b0.$b1.$b2.$b3"
                            if (ip !in v4List) v4List.add(ip)
                        }
                    } else if (family == AF_INET6) {
                        val nameRet = getnameinfo(
                            curr.pointed.ai_addr,
                            curr.pointed.ai_addrlen,
                            hostBuf,
                            128u,
                            null,
                            0u,
                            NI_NUMERICHOST
                        )
                        if (nameRet == 0) {
                            val ip = hostBuf.toKString()
                            if (ip !in v6List) v6List.add(ip)
                        }
                    }
                    curr = curr.pointed.ai_next
                }
                freeaddrinfo(res.value)
            } else {
                errorMsg = gai_strerror(status)?.toKString() ?: "DNS 解析失败 (Code: $status)"
            }
        }

        IosResolveResult(cleanHost, v4List, v6List, errorMsg)
    }

    /**
     * 单次非阻塞 TCP 建连探测 (可用于独立 Ping 或并发连接建立)
     * 返回 Pair(socketFd, latencyMs)，建连失败时返回 Pair(null, null)
     */
    @OptIn(ExperimentalForeignApi::class)
    fun connectSingle(
        host: String,
        port: Int,
        preferIpv6: Boolean,
        timeoutMs: Int
    ): Pair<Int?, Int?> {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        var resultFd: Int? = null
        var latencyMs: Int? = null

        memScoped {
            val hints = alloc<addrinfo>()
            hints.ai_family = if (preferIpv6) AF_INET6 else AF_INET
            hints.ai_socktype = SOCK_STREAM
            val res = allocPointerTo<addrinfo>()

            val status = getaddrinfo(cleanHost, port.toString(), hints.ptr, res.ptr)
            if (status != 0) {
                // 如果指定单栈解析失败，回退到 AF_UNSPEC 尝试
                hints.ai_family = AF_UNSPEC
                if (getaddrinfo(cleanHost, port.toString(), hints.ptr, res.ptr) != 0) {
                    return Pair(null, null)
                }
            }

            val targetAddrInfo = res.value
            if (targetAddrInfo != null) {
                val family = targetAddrInfo.pointed.ai_family
                val fd = socket(family, SOCK_STREAM, 0)
                if (fd >= 0) {
                    // 1. 设置 Darwin 平台防崩标志 SO_NOSIGPIPE
                    val nosigpipeVal = alloc<IntVar>()
                    nosigpipeVal.value = 1
                    setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, nosigpipeVal.ptr, sizeOf<IntVar>().toUInt())

                    // 2. 切换至非阻塞模式
                    val flags = fcntl(fd, F_GETFL, 0)
                    fcntl(fd, F_SETFL, flags or O_NONBLOCK)

                    val t0 = getEpochMs()
                    val connRet = connect(fd, targetAddrInfo.pointed.ai_addr, targetAddrInfo.pointed.ai_addrlen)

                    if (connRet == 0) {
                        val t1 = getEpochMs()
                        resultFd = fd
                        latencyMs = (t1 - t0).toInt().coerceAtLeast(1)
                    } else if (errno == EINPROGRESS) {
                        // 使用 poll 等待可写事件完成建连
                        val pfd = alloc<pollfd>()
                        pfd.fd = fd
                        pfd.events = (POLLOUT or POLLERR).toShort()

                        val pollRet = poll(pfd.ptr, 1u, timeoutMs)
                        if (pollRet > 0) {
                            val soErr = alloc<IntVar>()
                            val errLen = alloc<socklen_tVar>()
                            errLen.value = sizeOf<IntVar>().toUInt()
                            getsockopt(fd, SOL_SOCKET, SO_ERROR, soErr.ptr, errLen.ptr)

                            if (soErr.value == 0) {
                                val t1 = getEpochMs()
                                resultFd = fd
                                latencyMs = (t1 - t0).toInt().coerceAtLeast(1)
                            } else {
                                close(fd)
                            }
                        } else {
                            close(fd)
                        }
                    } else {
                        close(fd)
                    }
                }
                freeaddrinfo(res.value)
            }
        }

        return Pair(resultFd, latencyMs)
    }

    /**
     * 运行并发持链压测 (Session Hold Test)
     */
    suspend fun runSessionHoldTest(
        rawConfig: IosSessionConfig,
        onStats: suspend (IosProtocolStats) -> Unit,
        onLog: suspend (IosLogLine) -> Unit,
        onPingSample: (suspend (Int) -> Unit)? = null
    ): Pair<IosProtocolStats?, IosProtocolStats?> = coroutineScope {
        val config = rawConfig.normalized()
        isTesting = true
        setScreenKeepAwake(true)
        triggerHapticFeedback(false)

        var ipv4Stats: IosProtocolStats? = null
        var ipv6Stats: IosProtocolStats? = null

        // 联动 Ping 协程 (Bufferbloat 实时探测)
        val pingJob: Job? = if (onPingSample != null) {
            launch(Dispatchers.Default) {
                while (isActive && isTesting) {
                    val (_, rtt) = connectSingle(config.host, config.port, false, 1200)
                    if (rtt != null) {
                        onPingSample(rtt)
                    }
                    delay(400L)
                }
            }
        } else null

        try {
            when (config.mode) {
                IosTestMode.IPV4_ONLY -> {
                    ipv4Stats = runOneProtocol(config, IosIpProtocol.IPV4, onStats, onLog)
                }
                IosTestMode.IPV6_ONLY -> {
                    ipv6Stats = runOneProtocol(config, IosIpProtocol.IPV6, onStats, onLog)
                }
                IosTestMode.IPV4_THEN_IPV6 -> {
                    ipv4Stats = runOneProtocol(config.copy(mode = IosTestMode.IPV4_ONLY), IosIpProtocol.IPV4, onStats, onLog)
                    val released = releaseHeldSockets()
                    onLog(IosLogLine(timeEpochMs = getEpochMs(), level = IosLogLevel.WARN, text = "IPv4 压测结束并释放 $released 条连接，切换 IPv6 压测..."))
                    delay(300L)
                    ipv6Stats = runOneProtocol(config.copy(mode = IosTestMode.IPV6_ONLY), IosIpProtocol.IPV6, onStats, onLog)
                }
            }
        } finally {
            isTesting = false
            pingJob?.cancel()
            setScreenKeepAwake(false)
            triggerHapticFeedback(false)
            if (!config.keepConnectionsAfterStop) {
                releaseHeldSockets()
            }
        }

        Pair(ipv4Stats, ipv6Stats)
    }

    private suspend fun runOneProtocol(
        config: IosSessionConfig,
        protocol: IosIpProtocol,
        onStats: suspend (IosProtocolStats) -> Unit,
        onLog: suspend (IosLogLine) -> Unit
    ): IosProtocolStats = coroutineScope {
        val preferIpv6 = (protocol == IosIpProtocol.IPV6)
        onLog(IosLogLine(timeEpochMs = getEpochMs(), level = IosLogLevel.INFO, text = "开始 ${protocol.label} 握手压测: ${config.host}:${config.port}"))

        val resolveRes = resolveHost(config.host)
        val targetIps = if (preferIpv6) resolveRes.ipv6 else resolveRes.ipv4
        if (targetIps.isEmpty()) {
            onLog(IosLogLine(timeEpochMs = getEpochMs(), level = IosLogLevel.WARN, text = "未发现 ${protocol.label} 对应解析 IP，尝试使用默认路由握手"))
        } else {
            onLog(IosLogLine(timeEpochMs = getEpochMs(), level = IosLogLevel.INFO, text = "${protocol.label} 解析目标: ${targetIps.joinToString(", ")}"))
        }

        var activeSessions = 0
        var totalSuccess = 0
        var totalFailure = 0
        var totalAttempts = 0
        var maxStable = 0
        var totalLatencySum = 0L
        val errorMap = mutableMapOf<String, Int>()

        var stats = IosProtocolStats(
            protocol = protocol,
            phase = "正在建连",
            resolvedAddresses = targetIps,
            activeSessions = 0
        )
        onStats(stats)

        var lastSecondMark = getEpochMs()
        var batchSuccessInSecond = 0

        while (isActive && isTesting && totalSuccess < config.successLimit && totalFailure < config.failureLimit) {
            val needed = min(config.batchSize, config.successLimit - totalSuccess)
            if (needed <= 0) break

            val currentBatchJobs = (0 until needed).map {
                async(Dispatchers.Default) {
                    connectSingle(config.host, config.port, preferIpv6, config.timeoutMs)
                }
            }

            val batchResults = currentBatchJobs.awaitAll()
            var batchSuccessCount = 0

            for ((fd, latency) in batchResults) {
                totalAttempts++
                if (fd != null && latency != null) {
                    socketMutex.withLock {
                        heldSocketFds.add(fd)
                    }
                    activeSessions++
                    totalSuccess++
                    batchSuccessCount++
                    batchSuccessInSecond++
                    totalLatencySum += latency
                    maxStable = max(maxStable, activeSessions)
                } else {
                    totalFailure++
                    val errKey = "连接超时/被拒"
                    errorMap[errKey] = (errorMap[errKey] ?: 0) + 1
                }
            }

            val now = getEpochMs()
            val cps = if (now - lastSecondMark >= 1000L) {
                val currentCps = (batchSuccessInSecond * 1000.0 / (now - lastSecondMark)).toInt()
                lastSecondMark = now
                batchSuccessInSecond = 0
                currentCps
            } else {
                stats.cps
            }

            val avgLatency = if (totalSuccess > 0) (totalLatencySum / totalSuccess).toInt() else 0

            stats = stats.copy(
                phase = "持续建连中",
                activeSessions = activeSessions,
                totalSuccess = totalSuccess,
                totalFailure = totalFailure,
                totalAttempts = totalAttempts,
                lastAdded = batchSuccessCount,
                cps = cps,
                maxStableSessions = maxStable,
                averageConnectLatencyMs = avgLatency,
                errorSummary = errorMap.toMap()
            )
            onStats(stats)

            if (totalSuccess % 100 < config.batchSize || totalFailure % 50 == 0) {
                onLog(IosLogLine(
                    timeEpochMs = getEpochMs(),
                    level = if (totalFailure > 0) IosLogLevel.WARN else IosLogLevel.STAT,
                    text = "活跃: $activeSessions | 成功: $totalSuccess | 失败: $totalFailure | 均延: ${avgLatency}ms | CPS: $cps"
                ))
            }

            delay(config.intervalMs)
        }

        stats = stats.copy(phase = if (config.keepConnectionsAfterStop) "连接已维持" else "测试完成")
        onStats(stats)
        onLog(IosLogLine(timeEpochMs = getEpochMs(), level = IosLogLevel.SUCCESS, text = "${protocol.label} 压测结束: 稳定维持 $activeSessions 链路, 峰值 $maxStable"))
        stats
    }

    /**
     * 运行独立 Ping 质量监测
     */
    suspend fun runStandalonePing(
        host: String,
        port: Int,
        onStats: suspend (IosPingStats) -> Unit,
        onLog: suspend (IosLogLine) -> Unit
    ) = coroutineScope {
        isTesting = true
        setScreenKeepAwake(true)
        triggerHapticFeedback(false)

        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
        var sent = 0
        var received = 0
        var lost = 0
        var minLat = Int.MAX_VALUE
        var maxLat = 0
        var sumLat = 0L
        var prevLat: Int? = null
        var jitterSum = 0.0
        var jitterCount = 0

        onLog(IosLogLine(timeEpochMs = getEpochMs(), level = IosLogLevel.INFO, text = "启动独立高频 Ping 探测: $cleanHost:$port"))

        try {
            while (isActive && isTesting) {
                sent++
                val (fd, rtt) = connectSingle(cleanHost, port, false, 1500)
                if (fd != null) {
                    close(fd) // Ping 探测不保留链路，测完即关闭
                }

                if (rtt != null) {
                    received++
                    minLat = min(minLat, rtt)
                    maxLat = max(maxLat, rtt)
                    sumLat += rtt

                    if (prevLat != null) {
                        jitterSum += abs(rtt - prevLat!!)
                        jitterCount++
                    }
                    prevLat = rtt

                    val avg = (sumLat / received).toInt()
                    val jitter = if (jitterCount > 0) (jitterSum / jitterCount).toInt() else 0
                    val lossPct = (lost.toFloat() / sent) * 100f

                    val pingStats = IosPingStats(
                        host = cleanHost,
                        port = port,
                        currentLatencyMs = rtt,
                        minLatencyMs = if (minLat == Int.MAX_VALUE) 0 else minLat,
                        maxLatencyMs = maxLat,
                        avgLatencyMs = avg,
                        jitterMs = jitter,
                        sentCount = sent,
                        receivedCount = received,
                        lostCount = lost,
                        lossPercent = lossPct,
                        isRunning = true,
                        phase = "探测中"
                    )
                    onStats(pingStats)
                    onLog(IosLogLine(timeEpochMs = getEpochMs(), level = IosLogLevel.STAT, text = "Ping #$sent: 耗时=${rtt}ms, 均值=${avg}ms, 抖动=${jitter}ms"))
                } else {
                    lost++
                    val lossPct = (lost.toFloat() / sent) * 100f
                    val avg = if (received > 0) (sumLat / received).toInt() else 0
                    val pingStats = IosPingStats(
                        host = cleanHost,
                        port = port,
                        currentLatencyMs = -1,
                        minLatencyMs = if (minLat == Int.MAX_VALUE) 0 else minLat,
                        maxLatencyMs = maxLat,
                        avgLatencyMs = avg,
                        jitterMs = 0,
                        sentCount = sent,
                        receivedCount = received,
                        lostCount = lost,
                        lossPercent = lossPct,
                        isRunning = true,
                        phase = "超时丢包"
                    )
                    onStats(pingStats)
                    onLog(IosLogLine(timeEpochMs = getEpochMs(), level = IosLogLevel.ERROR, text = "Ping #$sent: 请求超时无响应 (丢包率: ${lossPct.toInt()}%)"))
                }

                delay(600L)
            }
        } finally {
            isTesting = false
            setScreenKeepAwake(false)
            triggerHapticFeedback(false)
        }
    }

    /**
     * 停止正在进行的测试
     */
    fun stopTest() {
        isTesting = false
    }

    /**
     * 释放所有已维持的套接字描述符
     */
    suspend fun releaseHeldSockets(): Int = socketMutex.withLock {
        val count = heldSocketFds.size
        for (fd in heldSocketFds) {
            close(fd)
        }
        heldSocketFds.clear()
        count
    }

    /**
     * 获取当前持链数
     */
    fun getHeldCount(): Int = heldSocketFds.size
}
