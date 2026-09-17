package dev.useclique.android.term

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
import dev.useclique.android.api.CliqueClient
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pipes the panel's /ws stream into a local xterm.js WebView.
 * The WebView is display-only. Keystrokes never go through it.
 */
/** Base64 characters per evaluateJavascript call. A multiple of four, so
 *  each chunk decodes on its own, and far enough under the Binder transaction
 *  ceiling that escaping and the JS wrapper cannot push a chunk over it. */
private const val CHUNK_CHARS = 48 * 1024

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

    /**
     * Hand the pane to the page in pieces.
     *
     * evaluateJavascript crosses a process boundary, and that transaction has
     * a ceiling of roughly a megabyte. A running session sends its entire
     * scrollback the moment a client attaches, and the server keeps 20,000
     * lines, which base64s to several megabytes. Handing that over in one call
     * does not fail gracefully, it kills the app: opening a busy session
     * crashed every time while an empty one was fine, which is exactly the
     * shape of a size limit rather than a bug in the terminal.
     *
     * Chunked on a multiple of four so every piece is valid base64 on its own,
     * because the page decodes each one as it arrives rather than buffering.
     * Posted in order to a FIFO queue, so the terminal sees the bytes in the
     * order tmux sent them. No escaping: the base64 alphabet contains neither
     * a quote nor a backslash.
     */
    private fun write(b64: String) {
        var at = 0
        while (at < b64.length) {
            val end = minOf(at + CHUNK_CHARS, b64.length)
            val piece = b64.substring(at, end)
            main.post {
                webView.evaluateJavascript("window.termWrite && window.termWrite('" + piece + "')", null)
            }
            at = end
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
