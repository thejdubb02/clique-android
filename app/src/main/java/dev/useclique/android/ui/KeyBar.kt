package dev.useclique.android.ui

import dev.useclique.android.R

data class KeyBarKey(
    val labelRes: Int,
    val spokenRes: Int,
    val tmuxName: String,
)

val KEY_BAR_KEYS = listOf(
    KeyBarKey(R.string.key_esc, R.string.key_esc_spoken, "Escape"),
    KeyBarKey(R.string.key_ctrl_c, R.string.key_ctrl_c_spoken, "C-c"),
    KeyBarKey(R.string.key_tab, R.string.key_tab_spoken, "Tab"),
    KeyBarKey(R.string.key_up, R.string.key_up_spoken, "Up"),
    KeyBarKey(R.string.key_down, R.string.key_down_spoken, "Down"),
    KeyBarKey(R.string.key_enter, R.string.key_enter_spoken, "Enter"),
)
