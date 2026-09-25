package com.tomjxyz.shipinfo.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tomjxyz.shipinfo.app
import com.tomjxyz.shipinfo.core.PinSchedule
import com.tomjxyz.shipinfo.core.Units
import com.tomjxyz.shipinfo.data.PinEntity
import com.tomjxyz.shipinfo.sensors.LocationSource
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

sealed interface PinResult {
    data class Saved(val pin: PinEntity) : PinResult
    data object NoPermission : PinResult
    data object NoFix : PinResult
}

object PinTaker {
    private const val FIX_TIMEOUT_MS = 120_000L

    /** Takes a GPS fix (falling back to the last known one, marked stale) and stores it as a pin. */
    suspend fun take(context: Context, auto: Boolean): PinResult {
        val source = LocationSource(context)
        if (!source.hasPermission()) return PinResult.NoPermission
        val now = System.currentTimeMillis()
        val fresh = source.currentFix(FIX_TIMEOUT_MS)
        val loc = fresh ?: source.lastKnown() ?: return PinResult.NoFix
        val speedKn = if (loc.hasSpeed()) Units.msToKnots(loc.speed.toDouble()) else null
        val pin = PinEntity(
            timeMs = now,
            fixTimeMs = loc.time,
            lat = loc.latitude,
            lon = loc.longitude,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
            speedKn = speedKn,
            cogDeg = if (loc.hasBearing() && (speedKn ?: 0.0) >= 0.5) loc.bearing.toDouble() else null,
            auto = auto,
            stale = fresh == null,
        )
        val id = context.app.db.pins().insert(pin)
        return PinResult.Saved(pin.copy(id = id))
    }
}

class PinWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (PinTaker.take(applicationContext, auto = true)) {
        is PinResult.Saved, PinResult.NoPermission -> Result.success()
        PinResult.NoFix -> Result.retry()
    }
}

object PinScheduler {
    private const val WORK_NAME = "auto_pin"

    fun apply(context: Context, intervalHours: Int, anchorMinutes: Int?) {
        val wm = WorkManager.getInstance(context)
        if (intervalHours <= 0) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = PinSchedule.initialDelayMs(ZonedDateTime.now(), intervalHours, anchorMinutes)
        val request = PeriodicWorkRequestBuilder<PinWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }
}
