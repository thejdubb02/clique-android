package dev.useclique.android.notify

import dev.useclique.android.api.BarSnippet
import dev.useclique.android.api.Session
import dev.useclique.android.api.wantsPermission

const val APPROVE_KEY = "Enter"
const val DENY_KEY = "Escape"

enum class WaitNotice {
    APPROVAL,
    FINISHED,
}

/**
 * After GET /wait matches, which notification to post.
 *
 * GET /wait returns `{id, state, matched, waited}` and carries no note, so
 * [session] is this session from GET /api/state fetched at that moment, or
 * null if it could not be read.
 */
fun waitNotice(waitState: String, session: Session?): WaitNotice {
    if (waitState == "waiting" && session != null && wantsPermission(session)) {
        return WaitNotice.APPROVAL
    }
    return WaitNotice.FINISHED
}

/** What the notification's third action sends: [text] then Enter, shown as [label]. */
data class SnippetAction(val label: String, val text: String)

/** The first bar snippet with text, as the panel's bar would send it; null if none. */
fun firstSnippetAction(snippets: List<BarSnippet>): SnippetAction? =
    snippets.firstOrNull { it.text.isNotBlank() }
        ?.let { SnippetAction(it.label.ifBlank { it.text }, it.text) }

/**
 * PendingIntent request code for one action button. Includes the server, so two
 * servers that both have session "s1" never share a code.
 */
fun actionRequestCode(serverId: String, sessionId: String, action: String): Int =
    "$serverId\u0000$sessionId\u0000$action".hashCode()
