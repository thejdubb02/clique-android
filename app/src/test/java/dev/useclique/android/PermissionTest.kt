package dev.useclique.android

import dev.useclique.android.api.Session
import dev.useclique.android.api.wantsPermission
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionTest {

    @Test
    fun waitingPermissionAndAlive() {
        assertTrue(wantsPermission(session(alive = true, signal = "waiting", signalNote = "permission")))
    }

    @Test
    fun waitingIdleNoteIsNotPermission() {
        assertFalse(wantsPermission(session(alive = true, signal = "waiting", signalNote = "idle")))
    }

    @Test
    fun waitingEmptyNoteIsNotPermission() {
        assertFalse(wantsPermission(session(alive = true, signal = "waiting", signalNote = "")))
    }

    @Test
    fun errorWinsOverPermissionNote() {
        assertFalse(wantsPermission(session(alive = true, signal = "error", signalNote = "permission")))
    }

    @Test
    fun notAliveIsNeverPermission() {
        assertFalse(wantsPermission(session(alive = false, signal = "waiting", signalNote = "permission")))
    }

    @Test
    fun busyWithNoSignalIsNotPermission() {
        assertFalse(wantsPermission(session(alive = true, busy = true, signal = "", signalNote = "")))
    }

    private fun session(
        alive: Boolean,
        busy: Boolean = false,
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
        busy = busy,
        state = "",
        saying = "",
        signal = signal,
        signalNote = signalNote,
    )
}
