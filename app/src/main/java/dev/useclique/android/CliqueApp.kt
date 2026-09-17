package dev.useclique.android

import android.app.Application
import dev.useclique.android.store.ServerStore

class CliqueApp : Application() {
    lateinit var store: ServerStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = ServerStore(this)
    }
}
