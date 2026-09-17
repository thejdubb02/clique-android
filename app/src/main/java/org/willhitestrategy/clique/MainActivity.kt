package org.willhitestrategy.clique

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.willhitestrategy.clique.ui.EditServerFragment
import org.willhitestrategy.clique.ui.NewSessionFragment
import org.willhitestrategy.clique.ui.ServersFragment
import org.willhitestrategy.clique.ui.SessionFragment
import org.willhitestrategy.clique.ui.SessionsFragment

class MainActivity : AppCompatActivity() {

    val app: CliqueApp get() = application as CliqueApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

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
