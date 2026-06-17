package com.demonv.netsessiontester

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import org.json.JSONObject
import java.io.OutputStreamWriter

class MainActivity : ComponentActivity(), NativeEventEmitter {
    private lateinit var webView: WebView
    private lateinit var manager: TcpSessionManager
    private var pendingExport: String? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val data = pendingExport
        if (uri != null && data != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(data) }
                } ?: error("无法打开导出文件")
            }.onSuccess {
                emit("toast", JSONObject().put("message", "已导出"))
            }.onFailure {
                emit("toast", JSONObject().put("message", "导出失败：${it.message}"))
            }
        }
        pendingExport = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.rgb(246, 248, 252)
        window.navigationBarColor = android.graphics.Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        manager = TcpSessionManager(applicationContext, this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(NativeBridge(this@MainActivity, manager), "NativeBridge")
            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun emit(event: String, payload: JSONObject) {
        runOnUiThread {
            val script = "window.NativeEvents&&window.NativeEvents.receive(${JSONObject.quote(event)},${payload});"
            webView.evaluateJavascript(script, null)
        }
    }

    fun openExport(csv: String) {
        runOnUiThread {
            pendingExport = csv
            exportLauncher.launch("net-session-test-v05.csv")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 不自动释放连接。用户点“释放”时释放。
    }
}

class NativeBridge(
    private val activity: MainActivity,
    private val manager: TcpSessionManager
) {
    @JavascriptInterface
    fun getInitialState(): String = manager.initialState().toString()

    @JavascriptInterface
    fun saveSettings(json: String) {
        manager.saveSettings(JSONObject(json))
    }

    @JavascriptInterface
    fun resolve(json: String) {
        manager.resolve(JSONObject(json).optString("host", "www.baidu.com"))
    }

    @JavascriptInterface
    fun startTest(json: String) {
        manager.start(JSONObject(json))
    }

    @JavascriptInterface
    fun stopAdding() {
        manager.stopAdding()
    }

    @JavascriptInterface
    fun release() {
        manager.releaseAll()
    }

    @JavascriptInterface
    fun clearLogsHistory() {
        manager.clearLogsHistory()
    }

    @JavascriptInterface
    fun exportData() {
        activity.openExport(manager.exportCsv())
    }
}

interface NativeEventEmitter {
    fun emit(event: String, payload: JSONObject)
}

fun startForegroundNotice(context: Context, text: String) {
    val intent = Intent(context, TestForegroundService::class.java)
        .setAction(TestForegroundService.ACTION_START)
        .putExtra(TestForegroundService.EXTRA_TEXT, text)
    ContextCompat.startForegroundService(context, intent)
}

fun updateForegroundNotice(context: Context, text: String) {
    val intent = Intent(context, TestForegroundService::class.java)
        .setAction(TestForegroundService.ACTION_UPDATE)
        .putExtra(TestForegroundService.EXTRA_TEXT, text)
    ContextCompat.startForegroundService(context, intent)
}

fun stopForegroundNotice(context: Context) {
    val intent = Intent(context, TestForegroundService::class.java)
        .setAction(TestForegroundService.ACTION_STOP)
    runCatching { context.startService(intent) }
}
