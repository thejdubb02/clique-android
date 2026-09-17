package org.willhitestrategy.clique

import android.app.Application
import org.willhitestrategy.clique.store.ServerStore

class CliqueApp : Application() {
    lateinit var store: ServerStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = ServerStore(this)
    }
}
