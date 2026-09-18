package dev.useclique.android

import dev.useclique.android.api.parseFolderColor
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderColorTest {

    private val fallback = 0xFF8B949E.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()

    @Test
    fun hashRgbAndRrggbbAgree() {
        assertEquals(white, parseFolderColor("#FFF", fallback))
        assertEquals(white, parseFolderColor("#FFFFFF", fallback))
        assertEquals(parseFolderColor("#FFF", fallback), parseFolderColor("#FFFFFF", fallback))
        assertEquals(red, parseFolderColor("#F00", fallback))
        assertEquals(red, parseFolderColor("#FF0000", fallback))
    }

    @Test
    fun withoutHashMatchesHashed() {
        assertEquals(white, parseFolderColor("FFF", fallback))
        assertEquals(white, parseFolderColor("FFFFFF", fallback))
        assertEquals(parseFolderColor("#FFF", fallback), parseFolderColor("FFF", fallback))
        assertEquals(parseFolderColor("#AABBCC", fallback), parseFolderColor("AABBCC", fallback))
    }

    @Test
    fun aarrggbbKeepsAlpha() {
        val halfRed = 0x80FF0000.toInt()
        assertEquals(halfRed, parseFolderColor("#80FF0000", fallback))
        assertEquals(halfRed, parseFolderColor("80FF0000", fallback))
        assertEquals(white, parseFolderColor("#FFFFFFFF", fallback))
    }

    @Test
    fun lowercaseHexIsAccepted() {
        assertEquals(parseFolderColor("#aabbcc", fallback), parseFolderColor("#AABBCC", fallback))
        assertEquals(parseFolderColor("#abc", fallback), parseFolderColor("#AABBCC", fallback))
    }

    @Test
    fun nullAndBlankAreFallback() {
        assertEquals(fallback, parseFolderColor(null, fallback))
        assertEquals(fallback, parseFolderColor("", fallback))
    }

    @Test
    fun nonsenseIsFallback() {
        assertEquals(fallback, parseFolderColor("nonsense", fallback))
        assertEquals(fallback, parseFolderColor("#", fallback))
        assertEquals(fallback, parseFolderColor("xyzxyz", fallback))
        assertEquals(fallback, parseFolderColor("#GGHHII", fallback))
        assertEquals(fallback, parseFolderColor("#FF00GG", fallback))
        assertEquals(fallback, parseFolderColor("#FFF ", fallback))
    }

    @Test
    fun wrongLengthIsFallback() {
        assertEquals(fallback, parseFolderColor("12", fallback))
        assertEquals(fallback, parseFolderColor("#12", fallback))
        assertEquals(fallback, parseFolderColor("1234", fallback))
        assertEquals(fallback, parseFolderColor("#1234", fallback))
        assertEquals(fallback, parseFolderColor("12345", fallback))
        assertEquals(fallback, parseFolderColor("1234567", fallback))
        assertEquals(fallback, parseFolderColor("#1234567", fallback))
        assertEquals(fallback, parseFolderColor("123456789", fallback))
    }

    @Test
    fun unparseableDoesNotBecomeBlack() {
        assertEquals(fallback, parseFolderColor("nonsense", fallback))
        assertEquals(0xFF000000.toInt(), parseFolderColor("#000", 0))
        assertEquals(0xFF000000.toInt(), parseFolderColor("#000000", 0))
    }
}
