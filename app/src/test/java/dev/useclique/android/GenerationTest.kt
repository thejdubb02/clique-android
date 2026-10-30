package dev.useclique.android

import dev.useclique.android.term.Generation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationTest {
    @Test
    fun staleGenerationIsDropped() {
        val g = Generation()
        val old = g.next()
        assertTrue(g.isCurrent(old))
        val fresh = g.next()
        assertFalse(g.isCurrent(old))
        assertTrue(g.isCurrent(fresh))
    }
}
