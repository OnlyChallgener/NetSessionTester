package com.demonv.netsessiontester

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.ConnectException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TcpSessionManager(
    private val context: Context,
    private val emitter: NativeEventEmitter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("net_session_settings_v5", Context.MODE_PRIVATE)
    private val historyFile = File(context.filesDir, "session_history_v5.jsonl")
    private val heldSockets: MutableMap<String, MutableList<Socket>> = mutableMapOf(
        "IPv4" to Collections.synchronizedList(mutableListOf()),
        "IPv6" to Collections.synchronizedList(mutableListOf())
    )
    private val logs = Collections.synchronizedList(mutableListOf<JSONObject>())
    private val running = AtomicBoolean(false)
    private var testJob: Job? = null

    private var ipv4Stats = emptyStats("IPv4")
    private var ipv6Stats = emptyStats("IPv6")
    private var status = "待测试"

    fun initialState(): JSONObject {
        return JSONObject()
            .put("settings", loadSettings())
            .put("status", status)
            .put("isAdding", running.get())
            .put("ipv4Stats", ipv4Stats)
            .put("ipv6Stats", ipv6Stats)
            .put("logs", JSONArray(logs.takeLast(50)))
            .put("history", loadHistory())
    }

    fun saveSettings(settings: JSONObject) {
        val normalized = normalizeSettings(settings)
        prefs.edit()
            .putString("settings", normalized.toString())
            .apply()
    }

    private fun loadSettings(): JSONObject {
        val raw = prefs.getString("settings", null)
        if (!raw.isNullOrBlank()) return normalizeSettings(JSONObject(raw))
        return defaultSettings()
    }

    private fun defaultSettings(): JSONObject {
        return JSONObject()
            .put("host", "www.baidu.com")
            .put("port", "80")
            .put("mode", "IPV4_THEN_IPV6")
            .put("batchSize", "100")
            .put("intervalMs", "500")
            .put("timeoutMs", "3000")
            .put("successLimit", "65535")
            .put("failureLimit", "200")
            .put("keepConnections", true)
            .put("maskPrivacy", false)
    }

    private fun normalizeSettings(input: JSONObject): JSONObject {
        val d = defaultSettings()
        return JSONObject()
            .put("host", input.optString("host", d.getString("host")).ifBlank { "www.baidu.com" })
            .put("port", input.optString("port", d.getString("port")).digitsOrDefault("80"))
            .put("mode", input.optString("mode", d.getString("mode")))
            .put("batchSize", input.optString("batchSize", d.getString("batchSize")).digitsOrDefault("100"))
            .put("intervalMs", input.optString("intervalMs", d.getString("intervalMs")).digitsOrDefault("500"))
            .put("timeoutMs", input.optString("timeoutMs", d.getString("timeoutMs")).digitsOrDefault("3000"))
            .put("successLimit", input.optString("successLimit", d.getString("successLimit")).digitsOrDefault("65535"))
            .put("failureLimit", input.optString("failureLimit", d.getString("failureLimit")).digitsOrDefault("200"))
            .put("keepConnections", input.optBoolean("keepConnections", true))
            .put("maskPrivacy", input.optBoolean("maskPrivacy", false))
    }

    fun resolve(host: String) {
        scope.launch {
            val cleanHost = host.trim().removePrefix("[").removeSuffix("]").ifBlank { "www.baidu.com" }
            addLog("INFO", "开始解析：$cleanHost")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val all = InetAddress.getAllByName(cleanHost).toList()
                    JSONObject()
                        .put("host", cleanHost)
                        .put("ipv4", JSONArray(all.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.distinct()))
                        .put("ipv6", JSONArray(all.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress }.distinct()))
                }.getOrElse {
                    JSONObject().put("host", cleanHost).put("ipv4", JSONArray()).put("ipv6", JSONArray()).put("error", it.message ?: it.javaClass.simpleName)
                }
            }
            emitter.emit("resolve", result)
            addLog(if (result.has("error")) "ERROR" else "SUCCESS", if (result.has("error")) "解析失败：${result.optString("error")}" else "解析完成")
        }
    }

    fun start(settings: JSONObject) {
        val config = normalizeSettings(settings)
        saveSettings(config)
        testJob?.cancel()
        scope.launch { releaseProtocol(null, emitLog = false) }
        logs.clear()
        ipv4Stats = emptyStats("IPv4")
        ipv6Stats = emptyStats("IPv6")
        status = "建连中"
        running.set(true)
        emitState()
        startForegroundNotice(context, "建连中：${modeLabel(config.optString("mode"))}，目标 ${config.optString("successLimit")}")
        addLog("INFO", "目标：${config.optString("host")}:${config.optString("port")}｜${modeLabel(config.optString("mode"))}｜新增：${config.optString("batchSize")}｜间隔：${config.optString("intervalMs")}ms")

        val started = System.currentTimeMillis()
        testJob = scope.launch {
            runCatching {
                when (config.optString("mode")) {
                    "IPV4_ONLY" -> runProtocol(config, "IPv4")
                    "IPV6_ONLY" -> runProtocol(config, "IPv6")
                    else -> {
                        runProtocol(config, "IPv4")
                        releaseProtocol("IPv4", emitLog = true)
                        delay(1200)
                        runProtocol(config, "IPv6")
                    }
                }
            }.onSuccess {
                running.set(false)
                status = "测试完成"
                val summary = JSONObject()
                    .put("time", started)
                    .put("timeText", timeText(started, "MM-dd HH:mm:ss"))
                    .put("host", config.optString("host"))
                    .put("port", config.optInt("port", 80))
                    .put("mode", config.optString("mode"))
                    .put("ipv4Stats", ipv4Stats)
                    .put("ipv6Stats", ipv6Stats)
                appendHistory(summary)
                emitState()
                if (config.optBoolean("keepConnections", true)) {
                    updateForegroundNotice(context, "测试完成，连接保持中")
                } else {
                    releaseProtocol(null, emitLog = true)
                    stopForegroundNotice(context)
                }
            }.onFailure {
                if (it !is CancellationException) {
                    addLog("ERROR", "测试中断：${it.message ?: it.javaClass.simpleName}")
                }
                running.set(false)
                status = "已停止新增"
                emitState()
                updateForegroundNotice(context, "已停止新增，连接保持中")
            }
        }
    }

    private suspend fun runProtocol(config: JSONObject, protocol: String) {
        releaseProtocol(protocol, emitLog = false)
        val host = config.optString("host", "www.baidu.com").removePrefix("[").removeSuffix("]")
        val port = config.optString("port", "80").toInt().coerceIn(1, 65535)
        val batchSize = config.optString("batchSize", "100").toInt().coerceIn(1, 1000)
        val intervalMs = config.optString("intervalMs", "500").toLong().coerceIn(100, 60000)
        val timeoutMs = config.optString("timeoutMs", "3000").toInt().coerceIn(300, 30000)
        val successLimit = config.optString("successLimit", "65535").toInt().coerceIn(1, 70000)
        val failureLimit = config.optString("failureLimit", "200").toInt().coerceIn(1, 100000)

        val addresses = resolveAddresses(host, protocol)
        val addressText = addresses.mapNotNull { it.hostAddress }.distinct()
        if (addresses.isEmpty()) {
            updateStats(protocol, emptyStats(protocol).put("phase", "解析失败"))
            addLog("ERROR", "$protocol 没有解析到可用地址")
            return
        }

        addLog("SUCCESS", "$protocol 解析成功：${addressText.joinToString(" / ")}")

        var success = 0
        var failure = 0
        var attempts = 0
        var lastAttempts = 0
        val errors = linkedMapOf<String, Int>()

        while (running.get() && success < successLimit && failure < failureLimit) {
            val count = minOf(batchSize, successLimit - success)
            if (count <= 0) break
            val result = openBatch(addresses, port, timeoutMs, count)
            success += result.sockets.size
            val failAdd = result.errors.values.sum()
            failure += failAdd
            attempts += result.sockets.size + failAdd
            result.errors.forEach { (k, v) -> errors[k] = (errors[k] ?: 0) + v }

            heldSockets.getValue(protocol).addAll(result.sockets)
            val active = activeCount(protocol)
            val added = attempts - lastAttempts
            lastAttempts = attempts
            val cps = (added * 1000L / intervalMs.coerceAtLeast(1)).toInt()

            val stats = JSONObject()
                .put("protocol", protocol)
                .put("phase", "建连中")
                .put("addresses", JSONArray(addressText))
                .put("active", active)
                .put("failure", failure)
                .put("total", attempts)
                .put("added", added)
                .put("cps", cps)
                .put("errors", JSONObject(errors as Map<*, *>))
            updateStats(protocol, stats)
            addLog("STAT", "$protocol 统计 - 成功：$success(+${result.sockets.size})｜失败：$failure(+$failAdd)｜活动：$active｜总计：$attempts｜新增：$added｜CPS：${cps}/秒")
            updateForegroundNotice(context, "$protocol 建连中｜活动 $active｜失败 $failure")

            if (failure >= failureLimit) {
                addLog("ERROR", "$protocol 达到失败上限：$failureLimit")
                break
            }
            delay(intervalMs)
        }

        val finalStats = currentStats(protocol)
            .put("phase", "测试完成")
            .put("active", activeCount(protocol))
        updateStats(protocol, finalStats)
        addLog("INFO", "$protocol 测试完成 - 活动：${finalStats.optInt("active")} 失败：${finalStats.optInt("failure")} 总计：${finalStats.optInt("total")}")
    }

    fun stopAdding() {
        testJob?.cancel()
        running.set(false)
        status = "已停止新增"
        addLog("WARN", "已停止新增；已建立连接继续保持。")
        emitState()
    }

    fun releaseAll() {
        scope.launch {
            val count = releaseProtocol(null, emitLog = false)
            addLog("WARN", "已释放连接：$count 条")
            ipv4Stats.put("active", 0).put("phase", "已释放")
            ipv6Stats.put("active", 0).put("phase", "已释放")
            status = "已释放"
            running.set(false)
            emitState()
            stopForegroundNotice(context)
        }
    }

    fun clearLogsHistory() {
        logs.clear()
        if (historyFile.exists()) historyFile.delete()
        emitState()
    }

    fun exportCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("type,time,level,text")
        synchronized(logs) {
            logs.forEach {
                sb.appendLine("log,${it.optString("timeText")},${it.optString("level")},${csv(it.optString("text"))}")
            }
        }
        loadHistory().let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                sb.appendLine("history,${item.optString("timeText")},,${csv(item.toString())}")
            }
        }
        return sb.toString()
    }

    private suspend fun resolveAddresses(host: String, protocol: String): List<InetAddress> = withContext(Dispatchers.IO) {
        InetAddress.getAllByName(host).filter {
            if (protocol == "IPv4") it is Inet4Address else it is Inet6Address
        }.distinctBy { it.hostAddress }
    }

    private suspend fun openBatch(addresses: List<InetAddress>, port: Int, timeoutMs: Int, count: Int): BatchResult = coroutineScope {
        val jobs = (0 until count).map { index ->
            async(Dispatchers.IO) {
                val address = addresses[index % addresses.size]
                openOne(address, port, timeoutMs)
            }
        }
        val results = jobs.awaitAll()
        val sockets = mutableListOf<Socket>()
        val errors = linkedMapOf<String, Int>()
        results.forEach { r ->
            r.socket?.let { sockets += it }
            r.error?.let { errors[it] = (errors[it] ?: 0) + 1 }
        }
        BatchResult(sockets, errors)
    }

    private fun openOne(address: InetAddress, port: Int, timeoutMs: Int): OpenResult {
        return try {
            val socket = Socket()
            socket.keepAlive = true
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            OpenResult(socket = socket)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OpenResult(error = classifyError(e))
        }
    }

    private suspend fun releaseProtocol(protocol: String?, emitLog: Boolean): Int = withContext(Dispatchers.IO) {
        val keys = if (protocol == null) listOf("IPv4", "IPv6") else listOf(protocol)
        var closed = 0
        keys.forEach { key ->
            val list = heldSockets.getValue(key)
            synchronized(list) {
                list.forEach { runCatching { it.close() }; closed++ }
                list.clear()
            }
        }
        if (emitLog && closed > 0) addLog("WARN", "已释放连接：$closed 条")
        closed
    }

    private fun activeCount(protocol: String): Int {
        val list = heldSockets.getValue(protocol)
        synchronized(list) {
            list.removeAll { it.isClosed }
            return list.size
        }
    }

    private fun updateStats(protocol: String, stats: JSONObject) {
        if (protocol == "IPv4") ipv4Stats = stats else ipv6Stats = stats
        status = "$protocol ${stats.optString("phase", "建连中")}"
        emitState()
    }

    private fun currentStats(protocol: String): JSONObject = if (protocol == "IPv4") ipv4Stats else ipv6Stats

    private fun emptyStats(protocol: String): JSONObject {
        return JSONObject()
            .put("protocol", protocol)
            .put("phase", "待测试")
            .put("addresses", JSONArray())
            .put("active", 0)
            .put("failure", 0)
            .put("total", 0)
            .put("added", 0)
            .put("cps", 0)
            .put("errors", JSONObject())
    }

    private fun emitState() {
        emitter.emit("state", initialState())
    }

    private fun addLog(level: String, text: String) {
        val now = System.currentTimeMillis()
        val item = JSONObject()
            .put("time", now)
            .put("timeText", timeText(now, "HH:mm:ss"))
            .put("level", level)
            .put("text", text)
        logs.add(item)
        while (logs.size > 800) logs.removeAt(0)
        emitter.emit("log", item)
        emitState()
    }

    private fun appendHistory(item: JSONObject) {
        historyFile.appendText(item.toString() + "\n")
    }

    private fun loadHistory(): JSONArray {
        val arr = JSONArray()
        if (!historyFile.exists()) return arr
        val lines = historyFile.readLines().takeLast(30).reversed()
        lines.forEach { line ->
            runCatching { arr.put(JSONObject(line)) }
        }
        return arr
    }

    private fun classifyError(e: Exception): String {
        val msg = e.message.orEmpty().lowercase()
        return when (e) {
            is SocketTimeoutException -> "超时"
            is ConnectException -> when {
                "refused" in msg -> "拒绝"
                "timed out" in msg -> "超时"
                else -> "连接失败"
            }
            is NoRouteToHostException -> "无路由"
            is PortUnreachableException -> "端口不可达"
            is UnknownHostException -> "DNS失败"
            is SocketException -> when {
                "too many open files" in msg || "emfile" in msg -> "FD上限"
                "cannot assign requested address" in msg -> "端口耗尽"
                "network is unreachable" in msg -> "网络不可达"
                "connection reset" in msg -> "重置"
                "broken pipe" in msg -> "已断开"
                else -> "Socket异常"
            }
            else -> e.javaClass.simpleName
        }
    }

    private fun String.digitsOrDefault(default: String): String {
        val v = filter { it.isDigit() }
        return v.ifBlank { default }
    }

    private fun modeLabel(mode: String): String = when (mode) {
        "IPV4_ONLY" -> "仅 IPv4"
        "IPV6_ONLY" -> "仅 IPv6"
        else -> "分别测试"
    }

    private fun timeText(ms: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private data class OpenResult(val socket: Socket? = null, val error: String? = null)
    private data class BatchResult(val sockets: List<Socket>, val errors: Map<String, Int>)
}
