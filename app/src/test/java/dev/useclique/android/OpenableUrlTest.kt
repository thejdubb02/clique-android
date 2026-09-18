package dev.useclique.android

import dev.useclique.android.api.openableUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenableUrlTest {

    @Test
    fun httpPasses() {
        assertEquals("http://example.com/x", openableUrl("http://example.com/x"))
    }

    @Test
    fun httpsPasses() {
        assertEquals("https://example.com/x", openableUrl("https://example.com/x"))
    }

    @Test
    fun intentRejected() {
        assertNull(openableUrl("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun fileRejected() {
        assertNull(openableUrl("file:///sdcard/x"))
    }

    @Test
    fun javascriptRejected() {
        assertNull(openableUrl("javascript:alert(1)"))
    }

    @Test
    fun marketRejected() {
        assertNull(openableUrl("market://details?id=x"))
    }

    /** A UNC share has a host and would reach an app if only the host were checked. */
    @Test
    fun uncFileRejected() {
        assertNull(openableUrl("file://attacker/share/payload.apk"))
    }

    /** These have an http scheme and no host, so only the host check stops them.
     *  "https://" on its own is not one of them: it fails to parse at all, so a
     *  test using it would pass with the host check deleted. */
    @Test
    fun hostlessHttpRejected() {
        assertNull(openableUrl("https:///x"))
        assertNull(openableUrl("http:example.com"))
        assertNull(openableUrl("http://:80/x"))
    }

    @Test
    fun bareWordRejected() {
        assertNull(openableUrl("example"))
    }

    @Test
    fun emptyRejected() {
        assertNull(openableUrl(""))
    }

    @Test
    fun nullRejected() {
        assertNull(openableUrl(null))
    }
}
