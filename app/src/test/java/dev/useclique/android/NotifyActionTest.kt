package dev.useclique.android

import dev.useclique.android.api.BarSnippet
import dev.useclique.android.notify.SnippetAction
import dev.useclique.android.notify.actionRequestCode
import dev.useclique.android.notify.firstSnippetAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotifyActionTest {

    @Test
    fun sameSessionIdOnTwoServersGivesDifferentRequestCodes() {
        assertNotEquals(
            actionRequestCode("serverA", "s1", "Enter"),
            actionRequestCode("serverB", "s1", "Enter"),
        )
    }

    @Test
    fun actionsOfOneSessionDiffer() {
        assertNotEquals(actionRequestCode("a", "s1", "Enter"), actionRequestCode("a", "s1", "Escape"))
        assertNotEquals(actionRequestCode("a", "s1", "Enter"), actionRequestCode("a", "s2", "Enter"))
    }

    @Test
    fun firstSnippetWithTextWins() {
        val got = firstSnippetAction(
            listOf(BarSnippet("", " ", ""), BarSnippet("Go", "continue", ""), BarSnippet("x", "y", "")),
        )
        assertEquals(SnippetAction("Go", "continue"), got)
    }

    @Test
    fun blankLabelFallsBackToText() {
        assertEquals(SnippetAction("continue", "continue"), firstSnippetAction(listOf(BarSnippet("", "continue", ""))))
    }

    @Test
    fun noSnippetsNoAction() {
        assertNull(firstSnippetAction(emptyList()))
    }
}
