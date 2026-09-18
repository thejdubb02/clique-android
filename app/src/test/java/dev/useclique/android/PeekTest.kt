package dev.useclique.android

import dev.useclique.android.api.parsePeek
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PeekTest {

    @Test
    fun normalArrayKeepsBothLines() {
        val raw = JSONObject(
            """{"lines":["Ran 1 shell command","Flummoxing... (4m 41s)"],"alive":true,"activity":1787200000}""",
        )
        assertEquals(
            listOf("Ran 1 shell command", "Flummoxing... (4m 41s)"),
            parsePeek(raw),
        )
    }

    @Test
    fun missingLinesKeyIsEmpty() {
        assertEquals(emptyList<String>(), parsePeek(JSONObject("""{"alive":true}""")))
    }

    @Test
    fun emptyArrayIsEmpty() {
        assertEquals(emptyList<String>(), parsePeek(JSONObject("""{"lines":[]}""")))
    }

    @Test
    fun nullInsideArrayIsSkipped() {
        val lines = JSONArray()
        lines.put("keep")
        lines.put(JSONObject.NULL)
        lines.put("after")
        val raw = JSONObject().put("lines", lines)
        assertEquals(listOf("keep", "after"), parsePeek(raw))
    }

    @Test
    fun orderIsPreserved() {
        val raw = JSONObject("""{"lines":["oldest","middle","newest"]}""")
        assertEquals(listOf("oldest", "middle", "newest"), parsePeek(raw))
    }

    @Test
    fun nullLinesArrayIsEmpty() {
        val raw = JSONObject().put("lines", JSONObject.NULL)
        assertEquals(emptyList<String>(), parsePeek(raw))
    }
}
