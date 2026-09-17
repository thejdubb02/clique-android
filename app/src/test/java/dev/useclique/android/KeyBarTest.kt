package dev.useclique.android

import dev.useclique.android.ui.KEY_BAR_KEYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyBarTest {

    @Test
    fun tmuxNamesAreExactAndInOrder() {
        assertEquals(
            listOf("Escape", "C-c", "Tab", "Up", "Down", "Enter"),
            KEY_BAR_KEYS.map { it.tmuxName },
        )
    }

    @Test
    fun everyKeyHasALabelAndASpokenLabel() {
        for (key in KEY_BAR_KEYS) {
            assertTrue(key.labelRes != 0)
            // The visible labels are symbols. Without this a screen reader
            // announces "button" six times.
            assertTrue(key.spokenRes != 0)
            assertTrue(key.spokenRes != key.labelRes)
        }
    }

    @Test
    fun noDuplicates() {
        assertEquals(KEY_BAR_KEYS.size, KEY_BAR_KEYS.map { it.tmuxName }.distinct().size)
        assertEquals(KEY_BAR_KEYS.size, KEY_BAR_KEYS.map { it.labelRes }.distinct().size)
        assertEquals(KEY_BAR_KEYS.size, KEY_BAR_KEYS.map { it.spokenRes }.distinct().size)
    }
}
