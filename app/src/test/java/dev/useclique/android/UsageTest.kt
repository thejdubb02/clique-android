package dev.useclique.android

import dev.useclique.android.api.parseUsage
import dev.useclique.android.api.resetMillis
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageTest {

    // 2026-10-30T02:00:00Z
    private val expected = 1_793_325_600_000L

    @Test
    fun resetTimeReadsEveryShapeVendorsSend() {
        assertEquals(expected, resetMillis("2026-10-30T02:00:00+00:00"))
        assertEquals(expected, resetMillis("2026-10-30T02:00:00Z"))
        assertEquals(expected + 123, resetMillis("2026-10-30T02:00:00.123456+00:00"))
        assertEquals(expected, resetMillis("2026-10-29T19:00:00-07:00"))
        assertEquals(expected, resetMillis("2026-10-30T02:00:00"))
    }

    @Test
    fun aResetTimeThatIsNotOneIsNull() {
        assertNull(resetMillis(""))
        assertNull(resetMillis("soon"))
    }

    @Test
    fun checkedTimeComesThrough() {
        val raw = JSONObject("""{"usage":[{"cli":"claude","windows":[],"checked":1793325600},{"cli":"codex"}]}""")
        val usage = parseUsage(raw)
        assertEquals(1_793_325_600L, usage[0].checked)
        assertEquals(0L, usage[1].checked)
    }
}
