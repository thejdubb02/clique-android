package dev.useclique.android

import dev.useclique.android.api.parseDiff
import dev.useclique.android.api.parseNotes
import dev.useclique.android.api.parseTranscript
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDetailTest {

    @Test
    fun diffKeepsAUnifiedDiff() {
        val raw = JSONObject(
            """{"repo":true,"name":"clique","diff":"@@ -1 +1 @@\n-old\n+new\n","empty":false,"truncated":true,"untracked_hidden":2}""",
        )
        val got = parseDiff(raw)
        assertTrue(got.repo)
        assertEquals("clique", got.name)
        assertEquals("@@ -1 +1 @@\n-old\n+new\n", got.diff)
        assertFalse(got.empty)
        assertTrue(got.truncated)
        assertEquals(2, got.untrackedHidden)
    }

    @Test
    fun diffWithoutARepoHasNoDiffKey() {
        val got = parseDiff(JSONObject("""{"repo":false,"name":"notes"}"""))
        assertFalse(got.repo)
        assertEquals("", got.diff)
        assertFalse(got.empty)
        assertEquals(0, got.untrackedHidden)
    }

    @Test
    fun transcriptKeepsTurnsAndAnUnknownRole() {
        val raw = JSONObject(
            """{"cli":"claude","name":"peek","turns":[
              {"role":"user","text":"hi"},
              {"role":"assistant","text":"hello"},
              {"role":"tool","text":"ran it"}
            ]}""",
        )
        val got = parseTranscript(raw)
        assertEquals("claude", got.cli)
        assertEquals("peek", got.name)
        assertEquals(listOf("user", "assistant", "tool"), got.turns.map { it.role })
        assertEquals(listOf("hi", "hello", "ran it"), got.turns.map { it.text })
    }

    @Test
    fun emptyTurnsAreValid() {
        val got = parseTranscript(JSONObject("""{"cli":"codex","name":"x","turns":[]}"""))
        assertTrue(got.turns.isEmpty())
    }

    @Test
    fun missingTurnsAreEmpty() {
        assertTrue(parseTranscript(JSONObject("""{"cli":"codex"}""")).turns.isEmpty())
    }

    @Test
    fun flippingDoneKeepsChildren() {
        val raw = JSONObject(
            """{"version":1,"updated":1.5,"items":[
              {"id":"a","text":"ship it","done":false,"collapsed":true,"created":1,"updated":2,
               "remindAt":null,"reminded":false,"children":[
                 {"id":"b","text":"child","done":true,"children":[]}
               ]}
            ]}""",
        )
        val outline = parseNotes(raw)
        assertEquals(1, outline.items.size)
        assertEquals("a", outline.items[0].id)
        assertEquals("ship it", outline.items[0].text)
        assertFalse(outline.items[0].done)
        outline.items[0].done = true
        val posted = JSONArray(outline.rawItems.toString())
        val top = posted.getJSONObject(0)
        assertTrue(top.getBoolean("done"))
        assertEquals("child", top.getJSONArray("children").getJSONObject(0).getString("text"))
        assertTrue(top.getJSONArray("children").getJSONObject(0).getBoolean("done"))
        assertTrue(top.getBoolean("collapsed"))
    }

    @Test
    fun emptyNotesAreEmpty() {
        val got = parseNotes(JSONObject("""{"version":1,"items":[],"updated":0}"""))
        assertEquals(0, got.rawItems.length())
        assertTrue(got.items.isEmpty())
    }

    @Test
    fun nullNoteTextIsEmpty() {
        val items = JSONArray().put(JSONObject().put("id", "a").put("text", JSONObject.NULL).put("done", true))
        val got = parseNotes(JSONObject().put("items", items))
        assertEquals("", got.items.single().text)
        assertTrue(got.items.single().done)
    }
}
