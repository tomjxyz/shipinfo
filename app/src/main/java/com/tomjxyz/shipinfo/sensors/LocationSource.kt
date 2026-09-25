package com.tomjxyz.shipinfo.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** GPS access through the platform LocationManager, so no Google Play services are needed. */
class LocationSource(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isGpsEnabled(): Boolean = manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

    private fun provider(): String? {
        val m = manager ?: return null
        val all = m.allProviders
        return when {
            LocationManager.GPS_PROVIDER in all -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in all -> LocationManager.NETWORK_PROVIDER
            else -> all.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        }
    }

    @SuppressLint("MissingPermission")
    fun updates(minTimeMs: Long): Flow<Location> = callbackFlow {
        val m = manager
        val p = provider()
        if (m == null || p == null || !hasPermission()) {
            close()
            return@callbackFlow
        }
        // Implement every method: on API < 30 the framework interface has no default methods.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        m.requestLocationUpdates(p, minTimeMs, 0f, listener, Looper.getMainLooper())
        awaitClose { m.removeUpdates(listener) }
    }

    /** Waits for a fresh fix, or returns null on timeout. */
    suspend fun currentFix(timeoutMs: Long): Location? =
        withTimeoutOrNull(timeoutMs) { updates(1000).first() }

    @SuppressLint("MissingPermission")
    fun lastKnown(): Location? {
        val m = manager ?: return null
        if (!hasPermission()) return null
        return m.allProviders.mapNotNull { runCatching { m.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
