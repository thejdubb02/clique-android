package dev.useclique.android.notify

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

/**
 * PendingIntent request code for a notification action. Unique per session
 * and key so two sessions cannot overwrite each other's buttons, and so
 * Approve and Deny on the same session stay distinct.
 */
fun actionRequestCode(sessionId: String, key: String): Int {
    return 31 * sessionId.hashCode() + key.hashCode()
}
