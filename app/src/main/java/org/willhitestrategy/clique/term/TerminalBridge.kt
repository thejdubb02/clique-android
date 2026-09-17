package org.willhitestrategy.clique.term

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import org.willhitestrategy.clique.api.CliqueClient
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pipes the panel's /ws stream into a local xterm.js WebView.
 * The WebView is display-only. Keystrokes never go through it.
 */
class TerminalBridge(
    private val webView: WebView,
    private val client: CliqueClient,
    private val sessionId: String,
) : WebSocketListener() {

    private val main = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private val ready = AtomicBoolean(false)
    private var lastCols = 80
    private var lastRows = 24
    private var opened = false

    fun attach() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = true
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        webView.addJavascriptInterface(Js(), "CliqueBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript("window.termFit && window.termFit()", null)
            }
        }
        webView.loadUrl("file:///android_asset/terminal.html")
    }

    fun detach() {
        sendControl(JSONObject().put("type", "release"))
        socket?.close(1000, "bye")
        socket = null
        opened = false
        ready.set(false)
        main.post {
            webView.removeJavascriptInterface("CliqueBridge")
            webView.loadUrl("about:blank")
        }
    }

    fun hold() {
        sendControl(JSONObject().put("type", "hold"))
    }

    fun resize(cols: Int, rows: Int) {
        if (cols < 20 || rows < 8) return
        if (cols == lastCols && rows == lastRows && opened) {
            sendControl(JSONObject().put("type", "hold"))
            return
        }
        lastCols = cols
        lastRows = rows
        sendControl(
            JSONObject()
                .put("type", "resize")
                .put("cols", cols)
                .put("rows", rows)
                .put("handheld", true),
        )
    }

    private fun connect() {
        if (opened) return
        opened = true
        socket = client.openTerminal(sessionId, lastCols, lastRows, this)
    }

    private fun sendControl(obj: JSONObject) {
        val ws = socket ?: return
        client.sendControl(ws, obj)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        write(bytes.base64())
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        write(text.toByteArray(Charsets.UTF_8).let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) })
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
        opened = false
        main.postDelayed({
            if (ready.get()) {
                opened = false
                connect()
            }
        }, 1500)
    }

    private fun write(b64: String) {
        main.post {
            val safe = b64.replace("\\", "\\\\").replace("'", "\\'")
            webView.evaluateJavascript("window.termWrite && window.termWrite('$safe')", null)
        }
    }

    inner class Js {
        @JavascriptInterface
        fun onReady(cols: Int, rows: Int) {
            lastCols = cols.coerceAtLeast(20)
            lastRows = rows.coerceAtLeast(8)
            ready.set(true)
            main.post { connect() }
        }

        @JavascriptInterface
        fun onSize(cols: Int, rows: Int) {
            main.post { resize(cols, rows) }
        }
    }
}
