package dev.useclique.android

import dev.useclique.android.api.parsePaste
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PasteTest {

    @Test
    fun theSavedPathComesBack() {
        val raw = JSONObject(
            """{"path":"/srv/app/.claude-images/shot.png","bytes":1204,"name":"shot.png"}""",
        )
        assertEquals("/srv/app/.claude-images/shot.png", parsePaste(raw))
    }

    @Test
    fun aMissingPathIsBlankRatherThanACrash() {
        assertEquals("", parsePaste(JSONObject("""{"bytes":0}""")))
    }

    /* This one passes either way on the JVM. The test classpath carries the
     * reference org.json, which returns the default for a JSON null, while
     * Android returns the string "null" and only optStr catches it. Kept
     * because it states the contract; the comment on optStr is what protects
     * it. */
    @Test
    fun aJsonNullPathIsBlankRatherThanTheWordNull() {
        assertEquals("", parsePaste(JSONObject().put("path", JSONObject.NULL)))
    }

    @Test
    fun anotherFieldIsNotMistakenForThePath() {
        assertEquals("", parsePaste(JSONObject("""{"relative":".claude-images/shot.png"}""")))
    }
}
