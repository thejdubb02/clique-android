package dev.useclique.android.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.useclique.android.CliqueApp
import dev.useclique.android.MainActivity
import dev.useclique.android.R
import dev.useclique.android.api.CliqueClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * Long-polls GET /api/sessions/<id>/wait until a working session stops.
 *
 * A foreground service is required: the wait call holds the connection for up
 * to 300 seconds, and a backgrounded app will not keep that socket. This is
 * the battery and review cost the spec asked to name.
 */
class WaitService : Service() {

    private val pool = Executors.newCachedThreadPool()
    private val jobs = ConcurrentHashMap<String, Future<*>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        val notif = watchingNotification(getString(R.string.fg_text))
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                FG_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(FG_ID, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP || intent == null) {
            stopAll()
            return START_NOT_STICKY
        }
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return START_NOT_STICKY
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
        val name = intent.getStringExtra(EXTRA_NAME) ?: sessionId
        val key = "$serverId:$sessionId"
        if (jobs.containsKey(key)) return START_STICKY
        val future = pool.submit { watch(serverId, sessionId, name, key) }
        jobs[key] = future
        return START_STICKY
    }

    private fun watch(serverId: String, sessionId: String, name: String, key: String) {
        try {
            val app = application as CliqueApp
            val server = app.store.get(serverId) ?: return
            val token = app.store.token(serverId) ?: return
            val client = CliqueClient.forServer(server, token)
            while (!Thread.currentThread().isInterrupted) {
                val result = client.wait(sessionId)
                val matched = result.optBoolean("matched")
                val state = result.optString("state")
                if (matched) {
                    notifyDone(name, state, serverId, sessionId)
                    return
                }
            }
        } catch (_: Exception) {
            // Network drop or session gone: give up this watch rather than loop hot.
        } finally {
            jobs.remove(key)
            if (jobs.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun notifyDone(name: String, state: String, serverId: String, sessionId: String) {
        val text = when (state) {
            "waiting" -> getString(R.string.notif_waiting, name)
            "error" -> getString(R.string.notif_error, name)
            "stopped", "gone" -> getString(R.string.notif_stopped, name)
            else -> getString(R.string.notif_idle, name)
        }
        val open = PendingIntent.getActivity(
            this,
            sessionId.hashCode(),
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SERVER_ID, serverId)
                .putExtra(MainActivity.EXTRA_SESSION_ID, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(nextId(), notif)
    }

    private fun watchingNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_FG)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.fg_title))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FG, getString(R.string.fg_title), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, getString(R.string.channel_wait), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun stopAll() {
        jobs.values.forEach { it.cancel(true) }
        jobs.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAll()
        pool.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_WATCH = "dev.useclique.android.WATCH"
        const val ACTION_STOP = "dev.useclique.android.STOP_WATCH"
        const val EXTRA_SERVER_ID = "serverId"
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_NAME = "name"
        private const val CHANNEL_FG = "clique-watch"
        private const val CHANNEL_DONE = "clique-done"
        private const val FG_ID = 3200
        private val ids = AtomicInteger(4000)

        private fun nextId() = ids.incrementAndGet()

        fun watch(context: Context, serverId: String, sessionId: String, name: String) {
            val i = Intent(context, WaitService::class.java)
                .setAction(ACTION_WATCH)
                .putExtra(EXTRA_SERVER_ID, serverId)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_NAME, name)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
