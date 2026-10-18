package dev.useclique.android.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import dev.useclique.android.MainActivity
import dev.useclique.android.R
import dev.useclique.android.api.CliqueClient
import dev.useclique.android.api.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

// What the panel's /paste accepts. Checked here so a 12 MB photo is refused
// with a sentence rather than a 413 nobody sees.
const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

fun imageName(resolver: ContentResolver, uri: Uri): String {
    val shown = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
    // The server sniffs the real type from the bytes, so this is a label
    // rather than a claim about what the file is.
    return shown?.takeIf { it.isNotBlank() } ?: "shot.png"
}

fun appendToDraft(draft: String, path: String): String =
    if (draft.isBlank()) "$path " else draft.trimEnd() + " $path "

/* A share arrives with no session attached, so ask which one. The path lands
   in that session's draft and stops there: it never sends, the same rule the
   attach button follows. */
fun shareImage(act: MainActivity, uri: Uri) {
    val store = act.app.store
    val serverId = store.selectedId
    val server = serverId?.let { store.get(it) }
    val token = serverId?.let { store.token(it) }
    if (server == null || token.isNullOrBlank()) {
        Toast.makeText(act, R.string.share_no_server, Toast.LENGTH_LONG).show()
        return
    }
    act.lifecycleScope.launch {
        try {
            val client = CliqueClient.forServer(server, token)
            val live = withContext(Dispatchers.IO) { client.state().sessions }
                .filter { it.alive && !it.archived }
            if (live.isEmpty()) {
                Toast.makeText(act, R.string.share_no_sessions, Toast.LENGTH_LONG).show()
                return@launch
            }
            AlertDialog.Builder(act)
                .setTitle(R.string.share_pick_session)
                .setItems(live.map { it.name }.toTypedArray()) { _, i -> upload(act, client, live[i], uri) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(act, e.message ?: act.getString(R.string.load_failed), Toast.LENGTH_LONG).show()
        }
    }
}

private fun upload(act: MainActivity, client: CliqueClient, session: Session, uri: Uri) {
    act.lifecycleScope.launch {
        try {
            withContext(Dispatchers.IO) {
                val resolver = act.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException(act.getString(R.string.attach_failed))
                if (bytes.size > MAX_IMAGE_BYTES) throw IOException(act.getString(R.string.attach_too_big))
                val saved = client.paste(session.id, bytes, imageName(resolver, uri))
                if (saved.isBlank()) throw IOException(act.getString(R.string.attach_failed))
                // Read the draft again: the list the person picked from may be
                // minutes old, and overwriting a newer draft loses it.
                val draft = client.state().sessions.firstOrNull { it.id == session.id }?.draft ?: session.draft
                client.updateSession(session.id, draft = appendToDraft(draft, saved))
            }
            Toast.makeText(act, act.getString(R.string.share_added, session.name), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(act, e.message ?: act.getString(R.string.attach_failed), Toast.LENGTH_LONG).show()
        }
    }
}
