package dev.useclique.android

import dev.useclique.android.api.Folder
import dev.useclique.android.api.GROUP_ARCHIVED
import dev.useclique.android.api.GROUP_RUNNING
import dev.useclique.android.api.GROUP_UNGROUPED
import dev.useclique.android.api.Session
import dev.useclique.android.api.SessionListItem
import dev.useclique.android.api.groupSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOrderTest {

    @Test
    fun runningUnfiledSortsAboveIdleUnfiled() {
        val idle = session("idle")
        val live = session("live", alive = true)
        val items = groupSessions(listOf(idle, live), emptyList())
        assertEquals(
            listOf("H:$GROUP_RUNNING", "R:live", "H:$GROUP_UNGROUPED", "R:idle"),
            labels(items),
        )
    }

    @Test
    fun filedAliveStaysInFolderAndNotInRunning() {
        val work = folder("f-work", "Work")
        val filed = session("filed", alive = true, folder = work.id)
        val items = groupSessions(listOf(filed), listOf(work))
        assertEquals(listOf("H:f-work", "R:filed"), labels(items))
        assertFalse(labels(items).contains("H:$GROUP_RUNNING"))
    }

    @Test
    fun archivedAppearsOnlyInArchivedAndThatGroupIsLast() {
        val work = folder("f-work", "Work")
        val live = session("live", alive = true)
        val filed = session("filed", folder = work.id)
        val archivedFiled = session("old-filed", alive = true, folder = work.id, archived = true)
        val archivedLoose = session("old-loose", archived = true)
        val items = groupSessions(listOf(archivedFiled, live, archivedLoose, filed), listOf(work))
        val ids = labels(items)
        assertEquals(listOf("R:old-filed", "R:old-loose"), ids.dropWhile { it != "H:$GROUP_ARCHIVED" }.drop(1))
        assertEquals("H:$GROUP_ARCHIVED", ids.last { it.startsWith("H:") })
        assertEquals(1, ids.count { it == "R:old-filed" })
        assertEquals(1, ids.count { it == "R:old-loose" })
        assertFalse(ids.takeWhile { it != "H:$GROUP_ARCHIVED" }.any { it == "R:old-filed" || it == "R:old-loose" })
    }

    @Test
    fun pinnedFloatsToTopAndUnpinnedKeepServerOrder() {
        val a = session("a")
        val b = session("b", pinned = true)
        val c = session("c")
        val d = session("d", pinned = true)
        val items = groupSessions(listOf(a, b, c, d), emptyList())
        assertEquals(
            listOf("H:$GROUP_UNGROUPED", "R:b", "R:d", "R:a", "R:c"),
            labels(items),
        )
    }

    @Test
    fun emptyGroupProducesNoHeader() {
        val work = folder("f-work", "Work")
        val empty = folder("f-empty", "Empty")
        val filed = session("filed", folder = work.id)
        val items = groupSessions(listOf(filed), listOf(work, empty))
        val headers = items.filterIsInstance<SessionListItem.Header>().map { it.id }
        assertEquals(listOf("f-work"), headers)
        assertTrue(GROUP_RUNNING !in headers)
        assertTrue(GROUP_UNGROUPED !in headers)
        assertTrue(GROUP_ARCHIVED !in headers)
        assertTrue("f-empty" !in headers)
    }

    @Test
    fun activeOnlyHidesStoppedAndKeepsLive() {
        val idle = session("idle")
        val live = session("live", alive = true)
        val items = groupSessions(listOf(idle, live), emptyList(), query = "", activeOnly = true)
        assertEquals(listOf("H:$GROUP_RUNNING", "R:live"), labels(items))
    }

    @Test
    fun queryBringsStoppedBackDespiteActiveOnly() {
        val live = session("live", alive = true)
        val stopped = session("old-notes")
        val items = groupSessions(listOf(live, stopped), emptyList(), query = "notes", activeOnly = true)
        assertEquals(listOf("H:$GROUP_UNGROUPED", "R:old-notes"), labels(items))
    }

    @Test
    fun queryMatchesNameCwdAndBranchIgnoringCase() {
        val byName = session("n", name = "Alpha")
        val byCwd = session("c", name = "other", cwd = "/tmp/AlphaProject")
        val byBranch = session("b", name = "other", branch = "feature/Alpha")
        val miss = session("m", name = "nope", cwd = "/tmp", branch = "main")
        val items = groupSessions(listOf(byName, byCwd, byBranch, miss), emptyList(), query = "aLpHa")
        assertEquals(listOf("R:n", "R:c", "R:b"), labels(items).filter { it.startsWith("R:") })
    }

    @Test
    fun filteredOutGroupProducesNoHeader() {
        val work = folder("f-work", "Work")
        val live = session("live", alive = true)
        val filed = session("filed", folder = work.id)
        val items = groupSessions(listOf(live, filed), listOf(work), query = "", activeOnly = true)
        val headers = items.filterIsInstance<SessionListItem.Header>().map { it.id }
        assertEquals(listOf(GROUP_RUNNING), headers)
        assertTrue("f-work" !in headers)
    }

    @Test
    fun emptyQueryActiveOnlyOffMatchesPreviousOrder() {
        val work = folder("f-work", "Work")
        val live = session("live", alive = true)
        val idle = session("idle")
        val filed = session("filed", folder = work.id)
        val archived = session("old", archived = true)
        val sessions = listOf(live, idle, filed, archived)
        val folders = listOf(work)
        assertEquals(
            groupSessions(sessions, folders),
            groupSessions(sessions, folders, query = "", activeOnly = false),
        )
    }

    private fun labels(items: List<SessionListItem>): List<String> {
        return items.map {
            when (it) {
                is SessionListItem.Header -> "H:${it.id}"
                is SessionListItem.Row -> "R:${it.session.id}"
            }
        }
    }

    private fun folder(id: String, name: String) = Folder(id = id, name = name, color = "", order = 0)

    private fun session(
        id: String,
        alive: Boolean = false,
        folder: String? = null,
        pinned: Boolean = false,
        archived: Boolean = false,
        name: String = id,
        cwd: String = "/",
        branch: String = "",
    ) = Session(
        id = id,
        name = name,
        cli = "claude",
        cliLabel = "Claude",
        cwd = cwd,
        folder = folder,
        alive = alive,
        busy = false,
        state = if (alive) "idle" else "stopped",
        saying = "",
        pinned = pinned,
        archived = archived,
        branch = branch,
    )
}
