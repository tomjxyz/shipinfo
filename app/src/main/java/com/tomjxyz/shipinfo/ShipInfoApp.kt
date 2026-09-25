package com.tomjxyz.shipinfo

import android.app.Application
import com.tomjxyz.shipinfo.data.AppDatabase
import com.tomjxyz.shipinfo.data.SettingsRepository
import com.tomjxyz.shipinfo.service.Notifications
import com.tomjxyz.shipinfo.service.SessionFinalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShipInfoApp : Application() {
    /** Scope for work that must outlive a screen or a service (e.g. final database writes). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db by lazy { AppDatabase.get(this) }
    val settings by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // No service can be running yet in a fresh process: close sessions left open by a crash.
        appScope.launch { SessionFinalizer.closeUnfinished(db) }
    }
}

val android.content.Context.app: ShipInfoApp get() = applicationContext as ShipInfoApp
