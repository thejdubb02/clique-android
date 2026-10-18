package dev.useclique.android

import dev.useclique.android.ui.appendToDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareImageTest {

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
