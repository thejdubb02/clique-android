package dev.useclique.android.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.useclique.android.CliqueApp
import dev.useclique.android.R
import dev.useclique.android.api.CliqueClient
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Handles Approve / Deny on a permission notification. Sends the key on a
 * background thread and does not open the app: opening a session attaches a
 * tmux client and clears the waiting signal.
 */
class PermissionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        if (notifId == 0) return
        val name = intent.getStringExtra(EXTRA_NAME) ?: sessionId
        val saying = intent.getStringExtra(EXTRA_SAYING).orEmpty()
        val appContext = context.applicationContext
        val pending = goAsync()
        executor.execute {
            try {
                send(appContext, serverId, sessionId, key)
                appContext.getSystemService(NotificationManager::class.java).cancel(notifId)
            } catch (_: Exception) {
                appContext.getSystemService(NotificationManager::class.java).notify(
                    notifId,
                    permissionNotification(
                        appContext,
                        name,
                        saying,
                        serverId,
                        sessionId,
                        notifId,
                        failed = true,
                    ),
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun send(context: Context, serverId: String, sessionId: String, key: String) {
        val app = context as CliqueApp
        val server = app.store.get(serverId) ?: throw IOException("no server")
        val token = app.store.token(serverId) ?: throw IOException("no token")
        CliqueClient.forServer(server, token).sendKey(sessionId, key)
    }

    companion object {
        const val EXTRA_SERVER_ID = "serverId"
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_KEY = "key"
        const val EXTRA_NOTIF_ID = "notifId"
        const val EXTRA_NAME = "name"
        const val EXTRA_SAYING = "saying"
        const val CHANNEL = "clique-permission"

        private val executor = Executors.newCachedThreadPool()
    }
}

internal fun ensurePermissionChannel(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel(
            PermissionActionReceiver.CHANNEL,
            context.getString(R.string.channel_permission),
            NotificationManager.IMPORTANCE_HIGH,
        ),
    )
}

internal fun permissionNotification(
    context: Context,
    name: String,
    saying: String,
    serverId: String,
    sessionId: String,
    notifId: Int,
    failed: Boolean = false,
): Notification {
    ensurePermissionChannel(context)
    val title = context.getString(R.string.notif_permission, name)
    val text = if (failed) context.getString(R.string.notif_action_failed) else saying
    val builder = NotificationCompat.Builder(context, PermissionActionReceiver.CHANNEL)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle(title)
        .setAutoCancel(false)
        .setOnlyAlertOnce(failed)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .addAction(
            0,
            context.getString(R.string.approve),
            permissionAction(context, serverId, sessionId, APPROVE_KEY, notifId, name, saying),
        )
        .addAction(
            0,
            context.getString(R.string.deny),
            permissionAction(context, serverId, sessionId, DENY_KEY, notifId, name, saying),
        )
    if (text.isNotEmpty()) {
        builder.setContentText(text)
        if (!failed && saying.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(saying))
        }
    }
    return builder.build()
}

private fun permissionAction(
    context: Context,
    serverId: String,
    sessionId: String,
    key: String,
    notifId: Int,
    name: String,
    saying: String,
): PendingIntent {
    val intent = Intent(context, PermissionActionReceiver::class.java)
        .putExtra(PermissionActionReceiver.EXTRA_SERVER_ID, serverId)
        .putExtra(PermissionActionReceiver.EXTRA_SESSION_ID, sessionId)
        .putExtra(PermissionActionReceiver.EXTRA_KEY, key)
        .putExtra(PermissionActionReceiver.EXTRA_NOTIF_ID, notifId)
        .putExtra(PermissionActionReceiver.EXTRA_NAME, name)
        .putExtra(PermissionActionReceiver.EXTRA_SAYING, saying)
    return PendingIntent.getBroadcast(
        context,
        actionRequestCode(sessionId, key),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
