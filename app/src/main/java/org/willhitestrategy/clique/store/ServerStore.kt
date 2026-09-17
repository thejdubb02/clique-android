package org.willhitestrategy.clique.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Servers and their pairing tokens. Tokens live in EncryptedSharedPreferences,
 * keyed per server id, which is what the spec asks for.
 */
class ServerStore(context: Context) {

    private val prefs: SharedPreferences = openSecure(context.applicationContext)

    val servers: List<Server>
        get() {
            val raw = prefs.getString(KEY_SERVERS, "[]") ?: "[]"
            val arr = JSONArray(raw)
            val out = ArrayList<Server>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Server(
                        id = o.getString("id"),
                        name = o.optString("name"),
                        baseUrl = o.getString("baseUrl"),
                        caPem = o.optString("caPem"),
                    ),
                )
            }
            return out
        }

    var selectedId: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    val selected: Server?
        get() {
            val id = selectedId
            return servers.firstOrNull { it.id == id } ?: servers.firstOrNull()
        }

    fun get(id: String): Server? = servers.firstOrNull { it.id == id }

    fun token(serverId: String): String? = prefs.getString(tokenKey(serverId), null)

    fun put(server: Server, token: String? = null): Server {
        val list = servers.toMutableList()
        val idx = list.indexOfFirst { it.id == server.id }
        if (idx >= 0) list[idx] = server else list.add(server)
        writeServers(list)
        if (token != null) {
            prefs.edit().putString(tokenKey(server.id), token).apply()
        }
        if (selectedId == null) selectedId = server.id
        return server
    }

    fun newServer(name: String, baseUrl: String, caPem: String = ""): Server {
        return Server(id = UUID.randomUUID().toString(), name = name, baseUrl = baseUrl, caPem = caPem)
    }

    fun remove(id: String) {
        writeServers(servers.filter { it.id != id })
        prefs.edit().remove(tokenKey(id)).apply()
        if (selectedId == id) selectedId = servers.firstOrNull()?.id
    }

    private fun writeServers(list: List<Server>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("baseUrl", s.baseUrl)
                    .put("caPem", s.caPem),
            )
        }
        prefs.edit().putString(KEY_SERVERS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_SERVERS = "servers"
        private const val KEY_SELECTED = "selectedId"

        private fun tokenKey(serverId: String) = "token.$serverId"

        private fun openSecure(context: Context): SharedPreferences {
            val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                "clique_secure",
                alias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
