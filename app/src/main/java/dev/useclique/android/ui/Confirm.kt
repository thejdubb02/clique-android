package dev.useclique.android.ui

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import dev.useclique.android.R

/**
 * Delete is one tap from a live Claude Code session on a phone, and a phone is
 * where mis-taps happen. Both places that offer it ask first, in the same words.
 */
fun Fragment.confirmDelete(name: String, then: () -> Unit) {
    AlertDialog.Builder(requireContext())
        .setMessage(getString(R.string.delete_confirm, name))
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.delete) { _, _ -> then() }
        .show()
}
