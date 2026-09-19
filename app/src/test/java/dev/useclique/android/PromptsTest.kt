package dev.useclique.android

import dev.useclique.android.api.Prompt
import dev.useclique.android.api.parsePrompts
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptsTest {

    @Test
    fun normalArrayKeepsRows() {
        val raw = JSONArray(
            """[
              {"cli":"claude","cwd":"/root/platform/clique","project":"clique",
               "text":"fix the peek","when":1787200000,"cli_session_id":"abc"},
              {"cli":"codex","cwd":"/tmp","project":"tmp","text":"hello","when":1}
            ]""",
        )
        assertEquals(
            listOf(
                Prompt("fix the peek", "claude", "/root/platform/clique", "clique", 1787200000L),
                Prompt("hello", "codex", "/tmp", "tmp", 1L),
            ),
            parsePrompts(raw),
        )
    }

    @Test
    fun emptyArrayIsEmpty() {
        assertEquals(emptyList<Prompt>(), parsePrompts(JSONArray()))
    }

    @Test
    fun blankTextIsSkipped() {
        val raw = JSONArray(
            """[
              {"text":"keep","cli":"c","cwd":"/","project":"p","when":1},
              {"text":"","cli":"c","cwd":"/","project":"p","when":2},
              {"text":"   ","cli":"c","cwd":"/","project":"p","when":3},
              {"cli":"c","cwd":"/","project":"p","when":4},
              {"text":"also","cli":"c","cwd":"/","project":"p","when":5}
            ]""",
        )
        assertEquals(listOf("keep", "also"), parsePrompts(raw).map { it.text })
    }

    @Test
    fun missingOptionalFieldsDefault() {
        val got = parsePrompts(JSONArray("""[{"text":"only text"}]"""))
        assertEquals(
            listOf(Prompt("only text", "", "", "", 0L)),
            got,
        )
    }

    @Test
    fun orderIsPreserved() {
        val raw = JSONArray("""[{"text":"newest"},{"text":"middle"},{"text":"oldest"}]""")
        assertEquals(listOf("newest", "middle", "oldest"), parsePrompts(raw).map { it.text })
    }
}
