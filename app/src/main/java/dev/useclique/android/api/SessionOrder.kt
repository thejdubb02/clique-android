package dev.useclique.android.api

const val GROUP_RUNNING = "running"
const val GROUP_UNGROUPED = "ungrouped"
const val GROUP_ARCHIVED = "archived"

sealed class SessionListItem {
    data class Header(val id: String) : SessionListItem()
    data class Row(val session: Session) : SessionListItem()
}

/**
 * Sidebar order matching the web panel: Running, Ungrouped, each folder in
 * [folders] order, then Archived. Empty groups are omitted. Pinned sessions
 * float to the top of their group; [sortedBy] is stable so the server's
 * order is kept within the pinned and within the rest.
 */
fun groupSessions(sessions: List<Session>, folders: List<Folder>): List<SessionListItem> {
    val known = folders.map { it.id }.toHashSet()
    fun filed(s: Session) = s.folder != null && s.folder in known
    fun pinFirst(group: List<Session>) = group.sortedBy { if (it.pinned) 0 else 1 }

    val out = ArrayList<SessionListItem>()
    fun emit(id: String, group: List<Session>) {
        if (group.isEmpty()) return
        out.add(SessionListItem.Header(id))
        for (session in pinFirst(group)) out.add(SessionListItem.Row(session))
    }

    val live = sessions.filter { !it.archived }
    val running = live.filter { it.alive && !filed(it) }
    emit(GROUP_RUNNING, running)

    val runningIds = running.map { it.id }.toHashSet()
    emit(GROUP_UNGROUPED, live.filter { it.id !in runningIds && !filed(it) })

    for (folder in folders) {
        emit(folder.id, live.filter { it.folder == folder.id })
    }

    emit(GROUP_ARCHIVED, sessions.filter { it.archived })
    return out
}
