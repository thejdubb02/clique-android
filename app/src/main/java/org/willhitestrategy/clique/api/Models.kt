package org.willhitestrategy.clique.api

import org.json.JSONArray
import org.json.JSONObject

data class Folder(
    val id: String,
    val name: String,
    val color: String,
    val order: Int,
)

data class Cli(
    val id: String,
    val label: String,
    val installed: Boolean,
)

data class Session(
    val id: String,
    val name: String,
    val cli: String,
    val cliLabel: String,
    val cwd: String,
    val folder: String?,
    val alive: Boolean,
    val busy: Boolean,
    val state: String,
    val saying: String,
)

data class PanelState(
    val version: String,
    val home: String,
    val folders: List<Folder>,
    val sessions: List<Session>,
    val clis: List<Cli>,
)

data class Claim(
    val token: String,
    val id: String,
    val name: String,
)

data class ApiException(val status: Int, val body: String) : RuntimeException("HTTP $status $body")

fun JSONObject.optStr(key: String): String = if (isNull(key)) "" else optString(key, "")

fun JSONArray.objects(): List<JSONObject> {
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) out.add(getJSONObject(i))
    return out
}

fun parseState(raw: JSONObject): PanelState {
    val folders = raw.optJSONArray("folders")?.objects()?.map {
        Folder(
            id = it.optStr("id"),
            name = it.optStr("name"),
            color = it.optStr("color"),
            order = it.optInt("order"),
        )
    }?.sortedBy { it.order } ?: emptyList()

    val sessions = raw.optJSONArray("sessions")?.objects()?.map {
        val folder = if (it.isNull("folder")) null else it.optString("folder").ifBlank { null }
        Session(
            id = it.optStr("id"),
            name = it.optStr("name"),
            cli = it.optStr("cli"),
            cliLabel = it.optStr("cli_label").ifBlank { it.optStr("cli") },
            cwd = it.optStr("cwd"),
            folder = folder,
            alive = it.optBoolean("alive"),
            busy = it.optBoolean("busy"),
            state = it.optStr("state").ifBlank {
                when {
                    !it.optBoolean("alive") -> "stopped"
                    it.optBoolean("busy") -> "working"
                    else -> "idle"
                }
            },
            saying = it.optStr("saying"),
        )
    } ?: emptyList()

    val clis = raw.optJSONArray("clis")?.objects()?.map {
        Cli(
            id = it.optStr("id"),
            label = it.optStr("label").ifBlank { it.optStr("id") },
            installed = it.optBoolean("installed", true),
        )
    } ?: emptyList()

    return PanelState(
        version = raw.optStr("version"),
        home = raw.optStr("home"),
        folders = folders,
        sessions = sessions,
        clis = clis,
    )
}
