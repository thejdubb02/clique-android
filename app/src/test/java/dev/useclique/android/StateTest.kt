package dev.useclique.android

import dev.useclique.android.api.parseState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class StateTest {

    @Test
    fun draftRoundTrips() {
        val raw = JSONObject(
            """{"sessions":[{"id":"s1","name":"box","cli":"claude","draft":"half typed"}]}""",
        )
        assertEquals("half typed", parseState(raw).sessions.single().draft)
    }

    /* Same contract as PasteTest. The reference org.json on this classpath
     * already returns the default for a JSON null, and Android would put the
     * word "null" on screen without optStr. */
    @Test
    fun aJsonNullDraftIsBlankRatherThanTheWordNull() {
        val session = JSONObject().put("id", "s1").put("draft", JSONObject.NULL)
        val raw = JSONObject().put("sessions", JSONArray().put(session))
        assertEquals("", parseState(raw).sessions.single().draft)
    }
}
