package dev.useclique.android.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import dev.useclique.android.store.Server
import java.io.IOException

class CliqueClient(
    val server: Server,
    private val token: String?,
    private val http: OkHttpClient = Tls.httpClient(server.caPem),
) {
    private val base: HttpUrl = server.baseUrl.trimEnd('/').toHttpUrl()
    private val waitHttp: OkHttpClient = http.newBuilder()
        .readTimeout(320, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun healthz(): Boolean {
        val req = Request.Builder().url(url("healthz")).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val body = resp.body?.string().orEmpty()
            return try {
                JSONObject(body).optBoolean("ok")
            } catch (_: Exception) {
                false
            }
        }
    }

    fun claim(code: String, name: String): Claim {
        val payload = JSONObject()
            .put("code", code)
            .put("name", name)
            .toString()
        val req = Request.Builder()
            .url(url("api/pair/claim"))
            .post(payload.toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code != 201) throw ApiException(resp.code, body)
            val o = JSONObject(body)
            return Claim(
                token = o.getString("token"),
                id = o.optString("id"),
                name = o.optString("name"),
            )
        }
    }

    fun state(): PanelState {
        val o = JSONObject(authed("api/state").get())
        return parseState(o)
    }

    fun send(sessionId: String, text: String, enter: Boolean = true) {
        val payload = JSONObject()
            .put("text", text)
            .put("enter", enter)
            .toString()
        authed("api/sessions/$sessionId/send").post(payload)
    }

    fun sendKey(sessionId: String, key: String) {
        val payload = JSONObject()
            .put("key", key)
            .toString()
        authed("api/sessions/$sessionId/send").post(payload)
    }

    fun updateSession(
        sessionId: String,
        name: String? = null,
        folder: String? = null,
        clearFolder: Boolean = false,
    ) {
        // Only the fields given. `"folder": null` is Ungrouped; omitting
        // folder leaves it where it is, which is why [clearFolder] is
        // separate from [folder] being absent.
        val payload = JSONObject()
        if (name != null) payload.put("name", name)
        if (clearFolder) payload.put("folder", JSONObject.NULL)
        else if (folder != null) payload.put("folder", folder)
        authed("api/sessions/$sessionId").patch(payload.toString())
    }

    fun createSession(cli: String, cwd: String, name: String, folder: String?): String {
        val payload = JSONObject()
            .put("cli", cli)
            .put("cwd", cwd)
        if (name.isNotBlank()) payload.put("name", name)
        if (!folder.isNullOrBlank()) payload.put("folder", folder)
        val body = authed("api/sessions").post(payload.toString(), expected = 201)
        return JSONObject(body).getString("id")
    }

    fun kill(sessionId: String) {
        authed("api/sessions/$sessionId/kill").post("{}")
    }

    fun start(sessionId: String) {
        authed("api/sessions/$sessionId/start").post("{}")
    }

    fun delete(sessionId: String) {
        authed("api/sessions/$sessionId").delete()
    }

    fun peek(id: String, lines: Int): List<String> {
        val body = authedQuery("api/sessions/$id/peek", "lines" to lines.toString()).get()
        return parsePeek(JSONObject(body))
    }

    fun prompts(limit: Int): List<Prompt> {
        val body = authedQuery("api/prompts", "limit" to limit.toString()).get()
        return parsePrompts(JSONArray(body))
    }

    fun wait(sessionId: String, forStates: String = "idle,waiting,error,stopped", timeout: Int = 300): JSONObject {
        val u = url("api/sessions/$sessionId/wait").newBuilder()
            .addQueryParameter("for", forStates)
            .addQueryParameter("timeout", timeout.toString())
            .build()
        val req = authedRequest(u).get().build()
        waitHttp.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            return JSONObject(body)
        }
    }

    fun openTerminal(
        sessionId: String,
        cols: Int,
        rows: Int,
        listener: WebSocketListener,
    ): WebSocket {
        // NOT wss:// here. OkHttp's URL builder accepts only http and https and
        // throws on anything else, which killed the app the instant a terminal
        // opened. newWebSocket does the upgrade itself from an http(s) URL, so
        // the scheme is left exactly as the panel's is.
        val wsUrl = base.newBuilder()
            .addPathSegment("ws")
            .addQueryParameter("id", sessionId)
            .addQueryParameter("cols", cols.coerceAtLeast(20).toString())
            .addQueryParameter("rows", rows.coerceAtLeast(8).toString())
            .build()
        val req = authedRequest(wsUrl).build()
        return http.newWebSocket(req, listener)
    }

    fun sendControl(ws: WebSocket, json: JSONObject) {
        ws.send(json.toString())
    }

    private fun url(path: String): HttpUrl {
        val builder = base.newBuilder()
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun authed(path: String) = Call(authedRequest(url(path)))

    private fun authedQuery(path: String, vararg params: Pair<String, String>): Call {
        val builder = url(path).newBuilder()
        for ((key, value) in params) builder.addQueryParameter(key, value)
        return Call(authedRequest(builder.build()))
    }

    private fun authedRequest(url: HttpUrl): Request.Builder {
        val b = Request.Builder().url(url)
        val t = token
        if (!t.isNullOrBlank()) b.header("Authorization", "Bearer $t")
        return b
    }

    inner class Call(private val builder: Request.Builder) {
        fun get(): String = execute(builder.get().build())

        fun post(json: String, expected: Int? = null): String =
            execute(builder.post(json.toRequestBody(JSON)).build(), expected)

        fun patch(json: String, expected: Int? = null): String =
            execute(builder.patch(json.toRequestBody(JSON)).build(), expected)

        fun delete(): String = execute(builder.delete().build())
    }

    private fun execute(request: Request, expected: Int? = null): String {
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val ok = if (expected != null) resp.code == expected else resp.isSuccessful
            if (!ok) throw ApiException(resp.code, body)
            return body
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        // One client per server, not one per call. The session list polls every
        // three seconds and every CliqueClient brings its own OkHttp connection
        // pool and dispatcher threads with it.
        private val cached = HashMap<String, Pair<String, CliqueClient>>()

        fun forServer(server: Server, token: String?): CliqueClient {
            if (server.baseUrl.isBlank()) throw IOException("empty URL")
            val sig = "${server.baseUrl}|${server.caPem}|${token.orEmpty()}"
            synchronized(cached) {
                val hit = cached[server.id]
                if (hit != null && hit.first == sig) return hit.second
                val made = CliqueClient(server, token)
                cached[server.id] = sig to made
                return made
            }
        }
    }
}
