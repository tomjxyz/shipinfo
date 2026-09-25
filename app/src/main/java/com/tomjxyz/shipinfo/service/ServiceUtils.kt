package com.tomjxyz.shipinfo.service

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tomjxyz.shipinfo.data.AppDatabase
import com.tomjxyz.shipinfo.data.SessionEntity
import com.tomjxyz.shipinfo.data.SessionType

fun Context.hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun Service.startForegroundCompat(id: Int, notification: Notification, useLocation: Boolean) {
    val type = when {
        useLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else -> 0
    }
    ServiceCompat.startForeground(this, id, notification, type)
}

fun Context.partialWakeLock(tag: String): PowerManager.WakeLock =
    getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShipInfo:$tag")
        .apply { setReferenceCounted(false) }

object SessionFinalizer {
    /** Gives sessions without an end time (process killed while recording) a sensible end. */
    suspend fun closeUnfinished(db: AppDatabase) {
        for (s in db.sessions().unfinished()) {
            val last = when (s.type) {
                SessionType.ROLL_WATCH -> db.rollWindows().lastTime(s.id)
                else -> db.samples().lastTime(s.id)
            }
            db.sessions().update(s.copy(endMs = last ?: s.startMs))
        }
    }
}

fun SessionEntity.durationMs(now: Long = System.currentTimeMillis()) = (endMs ?: now) - startMs
