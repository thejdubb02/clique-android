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
    val draft: String = "",
)

data class BarSnippet(
    val label: String,
    val text: String,
    val color: String,
)

data class PanelState(
    val version: String,
    val home: String,
    val folders: List<Folder>,
    val sessions: List<Session>,
    val clis: List<Cli>,
    val barSnippets: List<BarSnippet>,
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

data class ResumableConversation(
    val cli: String,
    val cliSessionId: String,
    val cwd: String,
    val project: String,
    val label: String,
    val updated: Double,
    val size: Int,
    val branch: String,
    val folder: String,
)

data class SessionDiff(
    val repo: Boolean,
    val name: String,
    val diff: String,
    val empty: Boolean,
    val truncated: Boolean,
    val untrackedHidden: Int,
)

data class TranscriptTurn(
    val role: String,
    val text: String,
)

data class Transcript(
    val cli: String,
    val name: String,
    val turns: List<TranscriptTurn>,
)

/* The phone only flips [done] on a top-level row and posts the same array
 * back. Children, reminders and the rest are set from the panel, so they stay
 * inside [raw] and go back untouched. */
data class NoteItem(val raw: JSONObject) {
    val id: String get() = raw.optStr("id")
    val text: String get() = raw.optStr("text")
    var done: Boolean
        get() = raw.optBoolean("done")
        set(value) {
            raw.put("done", value)
        }
}

data class NotesOutline(
    val rawItems: JSONArray,
    val items: List<NoteItem>,
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
            draft = it.optStr("draft"),
        )
    } ?: emptyList()

    val clis = raw.optJSONArray("clis")?.objects()?.map {
        Cli(
            id = it.optStr("id"),
            label = it.optStr("label").ifBlank { it.optStr("id") },
            installed = it.optBoolean("installed", true),
        )
    } ?: emptyList()

    // bar: true shows the snippet as a one-tap button, same list the text
    // expander uses in the pane; trigger-only (non-bar) snippets never show.
    val barSnippets = raw.optJSONObject("settings")?.optJSONArray("snippets")?.objects()
        ?.filter { it.optBoolean("bar") && it.optStr("text").isNotBlank() }
        ?.map { BarSnippet(label = it.optStr("label"), text = it.optStr("text"), color = it.optStr("color")) }
        ?: emptyList()

    return PanelState(
        version = raw.optStr("version"),
        home = raw.optStr("home"),
        folders = folders,
        sessions = sessions,
        clis = clis,
        barSnippets = barSnippets,
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

fun parseResumable(arr: JSONArray): List<ResumableConversation> {
    return arr.objects().map {
        ResumableConversation(
            cli = it.optStr("cli"),
            cliSessionId = it.optStr("cli_session_id"),
            cwd = it.optStr("cwd"),
            project = it.optStr("project"),
            label = it.optStr("label"),
            updated = it.optDouble("updated", 0.0),
            size = it.optInt("size", 0),
            branch = it.optStr("branch"),
            folder = it.optStr("folder"),
        )
    }
}

fun parseDiff(raw: JSONObject): SessionDiff {
    return SessionDiff(
        repo = raw.optBoolean("repo"),
        name = raw.optStr("name"),
        diff = raw.optStr("diff"),
        empty = raw.optBoolean("empty"),
        truncated = raw.optBoolean("truncated"),
        untrackedHidden = raw.optInt("untracked_hidden", 0),
    )
}

fun parseTranscript(raw: JSONObject): Transcript {
    val turns = raw.optJSONArray("turns")?.objects()?.map {
        TranscriptTurn(
            role = it.optStr("role"),
            text = it.optStr("text"),
        )
    } ?: emptyList()
    return Transcript(
        cli = raw.optStr("cli"),
        name = raw.optStr("name"),
        turns = turns,
    )
}

fun parseNotes(raw: JSONObject): NotesOutline {
    val arr = raw.optJSONArray("items") ?: JSONArray()
    val items = ArrayList<NoteItem>(arr.length())
    for (i in 0 until arr.length()) {
        if (arr.isNull(i)) continue
        val obj = arr.optJSONObject(i) ?: continue
        items.add(NoteItem(obj))
    }
    return NotesOutline(arr, items)
}

data class UsageWindow(
    val label: String,
    val percent: Double,
    val resetsAt: String,
)

data class CliUsage(
    val cli: String,
    val windows: List<UsageWindow>,
    val error: String,
)

fun parseUsage(raw: JSONObject): List<CliUsage> {
    val arr = raw.optJSONArray("usage") ?: JSONArray()
    return arr.objects().map { entry ->
        val windows = entry.optJSONArray("windows")?.objects()?.map {
            UsageWindow(
                label = it.optStr("label"),
                percent = it.optDouble("percent", 0.0),
                resetsAt = it.optStr("resets_at"),
            )
        } ?: emptyList()
        CliUsage(
            cli = entry.optStr("cli"),
            windows = windows,
            error = entry.optStr("error"),
        )
    }
}
