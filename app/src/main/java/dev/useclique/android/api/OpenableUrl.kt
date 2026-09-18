package dev.useclique.android.api

import java.net.URI

/**
 * The pane is remote output, so a URL in it is not something we typed.
 * Only http and https may leave the app: intent:, file: and custom schemes
 * would let printed text launch another app. Parsed with java.net.URI
 * because android.net.Uri is a stub on the JVM and a test against it
 * would prove nothing.
 */
fun openableUrl(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    return try {
        val uri = URI(raw)
        val scheme = uri.scheme?.lowercase() ?: return null
        // A host is a separate check, not the same one: file://attacker/share
        // has a host, and https:// has a scheme and nothing else.
        if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()) raw else null
    } catch (_: Exception) {
        null
    }
}
