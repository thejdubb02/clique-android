package dev.useclique.android.api

/**
 * Same rule as the panel inbox: a session is asking when signal is
 * "waiting", error wins over that, and a stopped session is neither.
 * It wants permission only when it is asking and the note is "permission".
 */
fun wantsPermission(session: Session): Boolean {
    if (!session.alive) return false
    if (session.signal == "error") return false
    return session.signal == "waiting" && session.signalNote == "permission"
}
