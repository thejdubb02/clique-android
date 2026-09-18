package dev.useclique.android.api

/**
 * Packed ARGB from the panel's folder colour string. Accepts "#rgb",
 * "#rrggbb", "#aarrggbb", and the same without the leading "#". Anything
 * else, including null and blank, is [fallback] so a bad value cannot
 * become black by accident.
 */
fun parseFolderColor(raw: String?, fallback: Int): Int {
    if (raw.isNullOrEmpty()) return fallback
    val hex = if (raw.startsWith("#")) raw.substring(1) else raw
    if (hex.length !in HEX_LENGTHS) return fallback
    if (hex.any { it !in HEX_DIGITS }) return fallback
    val rgb = if (hex.length == 3) {
        buildString(6) {
            for (c in hex) {
                append(c)
                append(c)
            }
        }
    } else {
        hex
    }
    val value = rgb.toLong(16)
    val argb = if (rgb.length == 6) 0xFF000000L or value else value
    return argb.toInt()
}

private const val HEX_DIGITS = "0123456789abcdefABCDEF"
private val HEX_LENGTHS = intArrayOf(3, 6, 8)
