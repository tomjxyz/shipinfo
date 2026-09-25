package com.tomjxyz.shipinfo.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.location.Location
import com.tomjxyz.shipinfo.core.Channel
import com.tomjxyz.shipinfo.core.PhoneOrientation
import com.tomjxyz.shipinfo.core.Units
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Snapshot of the latest values from all enabled sensors. */
data class LiveReadings(
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Double? = null,
    val speedKn: Double? = null,
    val cogDeg: Double? = null,
    val fixTimeMs: Long? = null,
    val rollDeg: Double? = null,
    val pitchDeg: Double? = null,
    val lateralG: Double? = null,
    val verticalG: Double? = null,
    val compassDeg: Double? = null,
    val motionTimeMs: Long? = null,
)

/** Below this speed the GPS course over ground is meaningless. */
private const val MIN_SPEED_FOR_COG_KN = 0.5

fun Location.toReadings(base: LiveReadings): LiveReadings {
    val kn = if (hasSpeed()) Units.msToKnots(speed.toDouble()) else null
    return base.copy(
        lat = latitude,
        lon = longitude,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
        speedKn = kn,
        cogDeg = if (hasBearing() && (kn ?: 0.0) >= MIN_SPEED_FOR_COG_KN) bearing.toDouble() else null,
        fixTimeMs = time,
    )
}

/**
 * Runs the location and motion sources needed for a set of channels and exposes their latest
 * values as a [StateFlow].
 */
class SensorHub(
    context: Context,
    private val scope: CoroutineScope,
    private val channels: Set<Channel>,
    orientation: PhoneOrientation,
    private val gpsIntervalMs: Long = 1000,
) {
    val location = LocationSource(context)
    val motion = MotionSource(context, orientation)

    private val _readings = MutableStateFlow(LiveReadings())
    val readings: StateFlow<LiveReadings> = _readings.asStateFlow()

    /** Extra consumer for every high-rate motion reading (runs on the sensor thread). */
    @Volatile
    var onMotion: ((MotionReading) -> Unit)? = null

    private var locationJob: Job? = null
    private var declinationSet = false

    val needsLocation get() = Channel.GPS in channels || Channel.SPEED in channels || Channel.HEADING in channels
    val needsMotion get() = Channel.ROLL in channels || Channel.HEADING in channels

    fun start() {
        if (needsLocation && location.hasPermission() && locationJob == null) {
            locationJob = scope.launch {
                location.updates(gpsIntervalMs).collect { loc ->
                    if (!declinationSet) {
                        motion.declinationDeg = GeomagneticField(
                            loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(), loc.time,
                        ).declination.toDouble()
                        declinationSet = true
                    }
                    _readings.update { loc.toReadings(it) }
                }
            }
        }
        if (needsMotion) {
            motion.start { r ->
                _readings.update {
                    it.copy(
                        rollDeg = r.rollDeg,
                        pitchDeg = r.pitchDeg,
                        lateralG = r.lateralG,
                        verticalG = r.verticalG,
                        compassDeg = r.compassDeg,
                        motionTimeMs = r.timeMs,
                    )
                }
                onMotion?.invoke(r)
            }
        }
    }

    fun stop() {
        locationJob?.cancel()
        locationJob = null
        motion.stop()
    }

    suspend fun tare(durationMs: Long) = motion.tare(durationMs)
}
