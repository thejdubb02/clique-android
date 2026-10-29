package dev.useclique.android

import dev.useclique.android.api.Session
import dev.useclique.android.notify.WaitNotice
import dev.useclique.android.notify.waitNotice
import org.junit.Assert.assertEquals
import org.junit.Test

class WaitNotifyTest {

    @Test
    fun waitingWithPermissionNoteIsApproval() {
        assertEquals(
            WaitNotice.APPROVAL,
            waitNotice("waiting", session(signal = "waiting", signalNote = "permission")),
        )
    }

    @Test
    fun waitingWithoutTheNoteIsFinished() {
        assertEquals(
            WaitNotice.FINISHED,
            waitNotice("waiting", session(signal = "waiting", signalNote = "")),
        )
    }

    @Test
    fun idleIsFinished() {
        assertEquals(WaitNotice.FINISHED, waitNotice("idle", session(signal = "", signalNote = "")))
    }

    @Test
    fun stoppedIsFinished() {
        assertEquals(
            WaitNotice.FINISHED,
            waitNotice("stopped", session(alive = false, signal = "", signalNote = "")),
        )
    }

    @Test
    fun waitingWithNoSessionIsFinished() {
        assertEquals(WaitNotice.FINISHED, waitNotice("waiting", null))
    }

    private fun session(
        alive: Boolean = true,
        signal: String,
        signalNote: String,
    ) = Session(
        id = "s",
        name = "s",
        cli = "claude",
        cliLabel = "Claude",
        cwd = "/",
        folder = null,
        alive = alive,
        busy = false,
        state = "",
        saying = "",
        signal = signal,
        signalNote = signalNote,
    )
}
