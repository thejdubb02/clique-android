package dev.useclique.android.api

import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

// A reply bigger than this is refused rather than held in memory. The biggest
// legitimate one is a file preview: up to 10 MB of text, JSON-escaped.
const val MAX_FILE_BYTES = 32 * 1024 * 1024

/* Reads at most limit + 1 bytes, so a caller can tell "exactly limit" from
   "more than limit" without ever holding the rest. */
fun readUpTo(input: InputStream, limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0
    while (total <= limit) {
        val read = input.read(chunk, 0, minOf(chunk.size, limit + 1 - total))
        if (read < 0) break
        out.write(chunk, 0, read)
        total += read
    }
    return out.toByteArray()
}

fun readCapped(input: InputStream, limit: Int): ByteArray {
    val bytes = readUpTo(input, limit)
    if (bytes.size > limit) throw IOException("Reply larger than ${limit / (1024 * 1024)} MB")
    return bytes
}

fun ResponseBody?.capped(limit: Int): ByteArray =
    if (this == null) ByteArray(0) else byteStream().use { readCapped(it, limit) }

fun ResponseBody?.cappedString(limit: Int = MAX_FILE_BYTES): String =
    String(capped(limit), Charsets.UTF_8)
