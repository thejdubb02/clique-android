package dev.useclique.android.api

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
    val signal: String = "",
    val signalNote: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val branch: String = "",
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

data class Prompt(
    val text: String,
    val cli: String,
    val cwd: String,
    val project: String,
    val whenSeconds: Long,
)

data class ApiException(val status: Int, val body: String) : RuntimeException("HTTP $status $body")

/* The isNull check is not redundant, and a JVM test cannot prove it.
 *
 * Android's org.json answers the string "null" for a JSON null, because its
 * optString stringifies whatever opt returned and JSONObject.NULL.toString()
 * is "null". The reference org.json on the test classpath returns the default
 * instead. So swapping this for a bare optString passes every unit test here
 * and puts the word "null" in front of a person on a phone. */
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
            signal = it.optStr("signal"),
            signalNote = it.optStr("signal_note"),
            pinned = it.optBoolean("pinned"),
            archived = it.optBoolean("archived"),
            branch = it.optStr("branch"),
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

fun parsePeek(raw: JSONObject): List<String> {
    val arr = raw.optJSONArray("lines") ?: return emptyList()
    val out = ArrayList<String>(arr.length())
    for (i in 0 until arr.length()) {
        if (arr.isNull(i)) continue
        out.add(arr.optString(i))
    }
    return out
}

fun parsePrompts(raw: JSONArray): List<Prompt> {
    return raw.objects().mapNotNull {
        val text = it.optStr("text")
        if (text.isBlank()) return@mapNotNull null
        Prompt(
            text = text,
            cli = it.optStr("cli"),
            cwd = it.optStr("cwd"),
            project = it.optStr("project"),
            whenSeconds = it.optLong("when"),
        )
    }
}

fun parsePaste(raw: JSONObject): String = raw.optStr("path")
