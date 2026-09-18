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
    private val attached = AtomicBoolean(false)
    private var lastCols = 80
    private var lastRows = 24

    /** Whoever asked for the pane's text and has not been answered yet. Main thread only. */
    private var pendingText: ((String) -> Unit)? = null
    private var opened = false

    fun attach() {
        attached.set(true)
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
        attached.set(false)
        release()
        socket?.close(1000, "bye")
        socket = null
        opened = false
        ready.set(false)
        main.post {
            webView.removeJavascriptInterface("CliqueBridge")
            webView.loadUrl("about:blank")
        }
    }

    fun release() {
        sendControl(JSONObject().put("type", "release"))
    }

    fun hold() {
        sendControl(JSONObject().put("type", "hold"))
    }

    /**
     * Last [lines] rows of pane text, scrollback included, ending at the
     * cursor. The callback runs on the main thread. Does nothing if this
     * bridge has already been detached.
     */
    /**
     * The pane's text, for something that can select it.
     *
     * The page hands it back through CliqueBridge rather than through
     * evaluateJavascript's return value. That return value is a JSON literal,
     * so taking it would mean decoding one, and the decoder that runs on a
     * phone is not the decoder a JVM unit test can reach: android.jar is a stub
     * there, so the tested path would be a fallback that never ships. A
     * @JavascriptInterface parameter is a plain String and there is nothing to
     * encode or decode at all.
     *
     * Answered once. If the page never calls back, the callback still fires
     * with nothing after a moment, because a menu item that silently does
     * nothing is the worst of the available failures.
     */
    fun readText(lines: Int, onText: (String) -> Unit) {
        val n = lines.coerceAtLeast(0)
        main.post {
            if (!attached.get()) return@post
            pendingText = onText
            try {
                webView.evaluateJavascript("window.termText && window.termText($n)", null)
            } catch (_: Exception) {
                deliverText("")
            }
            main.postDelayed({ if (pendingText === onText) deliverText("") }, 1500)
        }
    }

    /** Fires the pending reader once and clears it, so nothing answers twice. */
    private fun deliverText(text: String) {
        val waiting = pendingText ?: return
        pendingText = null
        if (attached.get()) waiting(text)
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

        @JavascriptInterface
        fun onText(text: String?) {
            main.post { deliverText(text ?: "") }
        }
    }
}
