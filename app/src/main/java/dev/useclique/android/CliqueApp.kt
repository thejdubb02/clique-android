package dev.useclique.android

import android.app.Application
import dev.useclique.android.store.ServerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex

class CliqueApp : Application() {
    val draftScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val draftSaveMutex = Mutex()
    lateinit var store: ServerStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = ServerStore(this)
    }
}
