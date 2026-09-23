package dev.useclique.android

import dev.useclique.android.api.ResumableConversation
import dev.useclique.android.api.parseResumable
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumableTest {

    @Test
    fun normalRowKeepsFields() {
        val raw = JSONArray(
            """[{
              "cli":"claude","cli_session_id":"abc-123","cwd":"/home/you/project",
              "project":"project","label":"fix the peek","updated":1710000000.5,
              "size":12,"branch":"main","folder":"f-abc123"
            }]""",
        )
        assertEquals(
            listOf(
                ResumableConversation(
                    cli = "claude",
                    cliSessionId = "abc-123",
                    cwd = "/home/you/project",
                    project = "project",
                    label = "fix the peek",
                    updated = 1710000000.5,
                    size = 12,
                    branch = "main",
                    folder = "f-abc123",
                ),
            ),
            parseResumable(raw),
        )
    }

    @Test
    fun emptyArrayIsEmpty() {
        assertEquals(emptyList<ResumableConversation>(), parseResumable(JSONArray()))
    }

    @Test
    fun nullStringsStayEmpty() {
        val row = JSONObject()
            .put("cli", "claude")
            .put("label", JSONObject.NULL)
            .put("folder", JSONObject.NULL)
            .put("branch", JSONObject.NULL)
        val got = parseResumable(JSONArray().put(row)).single()
        assertEquals("", got.label)
        assertEquals("", got.folder)
        assertEquals("", got.branch)
        assertEquals("", got.cliSessionId)
        assertEquals(0.0, got.updated, 0.0)
        assertEquals(0, got.size)
    }

    @Test
    fun orderIsPreserved() {
        val raw = JSONArray(
            """[{"label":"older","cli":"a"},{"label":"newer","cli":"b"}]""",
        )
        assertEquals(listOf("older", "newer"), parseResumable(raw).map { it.label })
    }
}
