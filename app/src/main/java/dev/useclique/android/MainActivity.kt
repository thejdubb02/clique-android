package dev.useclique.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import dev.useclique.android.ui.EditServerFragment
import dev.useclique.android.ui.NewSessionFragment
import dev.useclique.android.ui.ServersFragment
import dev.useclique.android.ui.SessionFragment
import dev.useclique.android.ui.SessionsFragment

class MainActivity : AppCompatActivity() {

    val app: CliqueApp get() = application as CliqueApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        /* Pad for the system bars, once, for every screen.
         *
         * Targeting Android 15 means the app is laid out edge to edge whether
         * it asks for it or not, so anything that does not pad for the bars
         * draws underneath them: the title sits behind the status bar and the
         * button at the bottom is cut in half by the navigation bar. Only the
         * session screen was doing this, so every other screen was wrong.
         *
         * The keyboard is handled here too rather than per screen. Padding the
         * container shrinks whatever is inside it, which is what puts a prompt
         * above the keyboard instead of behind it.
         */
        val container = findViewById<android.view.View>(R.id.container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(ime.bottom, bars.bottom))
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(container)

        if (savedInstanceState == null) {
            val store = app.store
            val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: store.selectedId
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            when {
                serverId != null && sessionId != null && store.get(serverId) != null -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, ServersFragment())
                        .addToBackStack("servers")
                        .replace(R.id.container, SessionsFragment.newInstance(serverId))
                        .addToBackStack("sessions")
                        .replace(R.id.container, SessionFragment.newInstance(serverId, sessionId))
                        .commit()
                }
                serverId != null && store.get(serverId) != null && !store.token(serverId).isNullOrBlank() -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, SessionsFragment.newInstance(serverId))
                        .commit()
                }
                else -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, ServersFragment())
                        .commit()
                }
            }
        }
    }

    fun showServers() {
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, ServersFragment())
            .commit()
    }

    fun showEditServer(serverId: String?) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, EditServerFragment.newInstance(serverId))
            .addToBackStack("edit-server")
            .commit()
    }

    fun showSessions(serverId: String) {
        app.store.selectedId = serverId
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, SessionsFragment.newInstance(serverId))
            .addToBackStack("sessions")
            .commit()
    }

    fun showSession(serverId: String, sessionId: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, SessionFragment.newInstance(serverId, sessionId))
            .addToBackStack("session")
            .commit()
    }

    fun showNewSession(serverId: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, NewSessionFragment.newInstance(serverId))
            .addToBackStack("new-session")
            .commit()
    }

    companion object {
        const val EXTRA_SERVER_ID = "serverId"
        const val EXTRA_SESSION_ID = "sessionId"
    }
}
