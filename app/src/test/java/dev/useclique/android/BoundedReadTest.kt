package dev.useclique.android

import dev.useclique.android.api.readCapped
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.io.InputStream

class BoundedReadTest {

    @Test
    fun oversizedBodyIsRefusedWithoutReadingItAll() {
        val limit = 100_000
        var served = 0
        val endless = object : InputStream() {
            override fun read(): Int = error("bulk reads expected")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                served += len
                return len
            }
        }
        try {
            readCapped(endless, limit)
            fail("expected IOException")
        } catch (_: IOException) {
        }
        assertEquals(limit + 1, served)
    }

    @Test
    fun bodyAtTheLimitIsReturnedWhole() {
        val body = ByteArray(5000) { 1 }
        assertEquals(5000, readCapped(body.inputStream(), 5000).size)
    }
}
