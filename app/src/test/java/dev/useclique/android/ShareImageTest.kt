package dev.useclique.android

import dev.useclique.android.ui.appendToDraft
import dev.useclique.android.ui.MAX_IMAGE_BYTES
import dev.useclique.android.ui.readImageBytes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream

class ShareImageTest {

    @Test
    fun imageReadStopsAfterLimitPlusOneByte() {
        var bytesRead = 0
        val input = object : InputStream() {
            override fun read(): Int = error("bulk reads expected")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val remaining = MAX_IMAGE_BYTES + 2 - bytesRead
                if (remaining == 0) return -1
                val count = minOf(length, remaining)
                bytesRead += count
                return count
            }
        }

        assertEquals(MAX_IMAGE_BYTES + 1, readImageBytes(input).size)
        assertEquals(MAX_IMAGE_BYTES + 1, bytesRead)
    }

    @Test
    fun anEmptyDraftJustGetsThePath() {
        assertEquals("/a/b.png ", appendToDraft("", "/a/b.png"))
    }

    @Test
    fun aBlankDraftIsTreatedAsEmpty() {
        assertEquals("/a/b.png ", appendToDraft("  \n", "/a/b.png"))
    }

    @Test
    fun anExistingDraftIsKeptAndThePathFollowsIt() {
        assertEquals("what is wrong here /a/b.png ", appendToDraft("what is wrong here ", "/a/b.png"))
    }
}
