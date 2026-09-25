package com.tomjxyz.shipinfo.service

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.tomjxyz.shipinfo.app
import com.tomjxyz.shipinfo.core.Channel
import com.tomjxyz.shipinfo.core.SampleColumns
import com.tomjxyz.shipinfo.core.Units
import com.tomjxyz.shipinfo.data.SampleEntity
import com.tomjxyz.shipinfo.data.SessionEntity
import com.tomjxyz.shipinfo.data.SessionType
import com.tomjxyz.shipinfo.sensors.LiveReadings
import com.tomjxyz.shipinfo.sensors.SensorHub
import com.tomjxyz.shipinfo.ui.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max

data class LiveRecordingState(
    val recording: Boolean = false,
    val sessionId: Long? = null,
    val startMs: Long = 0,
    val sampleCount: Int = 0,
    val channels: Set<Channel> = emptySet(),
    val readings: LiveReadings = LiveReadings(),
    val taring: Boolean = false,
    /** Recent samples for the live mini charts. */
    val history: List<SampleEntity> = emptyList(),
    val maxRollStbdDeg: Double = 0.0,
    val maxRollPortDeg: Double = 0.0,
)

/** Foreground service for the main "live" recording mode. */
class RecordingService : LifecycleService() {
    companion object {
        private const val ACTION_START = "com.tomjxyz.shipinfo.record.START"
        private const val ACTION_STOP = "com.tomjxyz.shipinfo.record.STOP"
        private const val ACTION_TARE = "com.tomjxyz.shipinfo.record.TARE"
        private const val HISTORY_SIZE = 300
        const val TARE_MS = 5_000L

        private val _state = MutableStateFlow(LiveRecordingState())
        val state: StateFlow<LiveRecordingState> = _state.asStateFlow()

        fun start(context: Context) = ContextCompat.startForegroundService(
            context, Intent(context, RecordingService::class.java).setAction(ACTION_START),
        )

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }

        fun tare(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_TARE))
        }
    }

    private var hub: SensorHub? = null
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var session: SessionEntity? = null
    private var channels: Set<Channel> = emptySet()

    // Running statistics.
    private var maxSpeed = 0.0
    private var speedSum = 0.0
    private var speedCount = 0
    private var distanceNm = 0.0
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private val rollLock = Any()
    private var maxStbd = 0.0
    private var maxPort = 0.0
    private var maxPitch = 0.0
    private var maxLatG = 0.0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
            ACTION_TARE -> hub?.let { h ->
                lifecycleScope.launch {
                    _state.value = _state.value.copy(taring = true)
                    h.tare(TARE_MS)
                    _state.value = _state.value.copy(taring = false)
                }
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopIntent() = Notifications.serviceIntent(this, RecordingService::class.java, ACTION_STOP)

    private fun startRecording() {
        if (job != null) return
        startForegroundCompat(
            Notifications.ID_RECORDING,
            Notifications.ongoing(this, "Recording ship data", "Starting…", "live", stopIntent()),
            useLocation = hasLocationPermission(),
        )
        wakeLock = partialWakeLock("record").also { it.acquire(24 * 60 * 60 * 1000L) }

        job = lifecycleScope.launch {
            val settings = app.settings.current()
            val channels = settings.channels
            this@RecordingService.channels = channels
            val interval = settings.sampleIntervalMs
            val h = SensorHub(this@RecordingService, this, channels, settings.orientation, gpsIntervalMs = interval)
            h.onMotion = { r ->
                synchronized(rollLock) {
                    maxStbd = max(maxStbd, r.rollDeg)
                    maxPort = max(maxPort, -r.rollDeg)
                    maxPitch = max(maxPitch, kotlin.math.abs(r.pitchDeg))
                    maxLatG = max(maxLatG, kotlin.math.abs(r.lateralG))
                }
            }
            h.start()
            hub = h

            val startMs = System.currentTimeMillis()
            val newSession = SessionEntity(
                type = SessionType.LIVE,
                startMs = startMs,
                channels = SampleColumns.encode(channels),
                orientation = settings.orientation.name,
                intervalMs = interval,
            )
            val id = app.db.sessions().insert(newSession)
            session = newSession.copy(id = id)
            _state.value = LiveRecordingState(recording = true, sessionId = id, startMs = startMs, channels = channels)

            var count = 0
            var next = System.currentTimeMillis() + interval
            // Give the sensors a moment before the first sample.
            delay(interval)
            while (isActive) {
                val now = System.currentTimeMillis()
                val sample = buildSample(id, now, channels, h.readings.value, interval)
                app.db.samples().insert(sample)
                count++
                updateStats(sample)
                val cur = _state.value
                _state.value = cur.copy(
                    sampleCount = count,
                    readings = h.readings.value,
                    history = (cur.history + sample).takeLast(HISTORY_SIZE),
                    maxRollStbdDeg = synchronized(rollLock) { maxStbd },
                    maxRollPortDeg = synchronized(rollLock) { maxPort },
                )
                if (count % 5 == 0) updateNotification(startMs, sample)
                if (count % 30 == 0) saveSummary(endMs = null, count = count)
                next += interval
                delay(max(0L, next - System.currentTimeMillis()))
            }
        }
        // Keep the readings in the live state fresh between samples too.
        lifecycleScope.launch {
            while (isActive) {
                hub?.let { h -> _state.value = _state.value.copy(readings = h.readings.value) }
                delay(100)
            }
        }
    }

    private fun buildSample(
        sessionId: Long,
        now: Long,
        channels: Set<Channel>,
        r: LiveReadings,
        interval: Long,
    ): SampleEntity {
        val fixFresh = r.fixTimeMs != null && now - r.fixTimeMs < max(10_000L, 3 * interval)
        val motionFresh = r.motionTimeMs != null && now - r.motionTimeMs < 5_000L
        val gps = Channel.GPS in channels && fixFresh
        val speed = Channel.SPEED in channels && fixFresh
        val heading = Channel.HEADING in channels
        val roll = Channel.ROLL in channels && motionFresh
        return SampleEntity(
            sessionId = sessionId,
            timeMs = now,
            lat = if (gps) r.lat else null,
            lon = if (gps) r.lon else null,
            accuracyM = if (gps) r.accuracyM else null,
            speedKn = if (speed) r.speedKn else null,
            cogDeg = if (heading && fixFresh) r.cogDeg else null,
            compassDeg = if (heading && motionFresh) r.compassDeg else null,
            rollDeg = if (roll) r.rollDeg else null,
            pitchDeg = if (roll) r.pitchDeg else null,
            lateralG = if (roll) r.lateralG else null,
            verticalG = if (roll) r.verticalG else null,
        )
    }

    private fun updateStats(s: SampleEntity) {
        s.speedKn?.let {
            maxSpeed = max(maxSpeed, it)
            speedSum += it
            speedCount++
        }
        val lat = s.lat
        val lon = s.lon
        if (lat != null && lon != null && (s.accuracyM ?: 0.0) <= 50.0) {
            val pLat = lastLat
            val pLon = lastLon
            if (pLat != null && pLon != null) {
                val d = Units.distanceNm(pLat, pLon, lat, lon)
                val moving = (s.speedKn ?: 0.0) >= 0.5
                // Ignore GPS jitter while stationary.
                if (moving || d * 1852.0 > 2 * (s.accuracyM ?: 10.0)) {
                    distanceNm += d
                    lastLat = lat
                    lastLon = lon
                }
            } else {
                lastLat = lat
                lastLon = lon
            }
        }
    }

    private suspend fun saveSummary(endMs: Long?, count: Int) {
        val s = session ?: return
        val updated = synchronized(rollLock) {
            s.copy(
                endMs = endMs,
                sampleCount = count,
                maxSpeedKn = if (speedCount > 0) maxSpeed else null,
                avgSpeedKn = if (speedCount > 0) speedSum / speedCount else null,
                distanceNm = if (lastLat != null) distanceNm else null,
                maxRollStbdDeg = if (Channel.ROLL in channels) maxStbd else null,
                maxRollPortDeg = if (Channel.ROLL in channels) maxPort else null,
                maxPitchDeg = if (Channel.ROLL in channels) maxPitch else null,
                maxLateralG = if (Channel.ROLL in channels) maxLatG else null,
            )
        }
        session = updated
        app.db.sessions().update(updated)
    }

    private fun updateNotification(startMs: Long, s: SampleEntity) {
        val parts = buildList {
            add(formatDuration(System.currentTimeMillis() - startMs))
            s.speedKn?.let { add(String.format(Locale.US, "%.1f kn", it)) }
            s.rollDeg?.let { add(String.format(Locale.US, "roll %.1f°", it)) }
        }
        runCatching {
            NotificationManagerCompat.from(this).notify(
                Notifications.ID_RECORDING,
                Notifications.ongoing(this, "Recording ship data", parts.joinToString(" · "), "live", stopIntent()),
            )
        }
    }

    private fun stopRecording() {
        val j = job
        job = null
        hub?.stop()
        hub = null
        val count = _state.value.sampleCount
        _state.value = LiveRecordingState()
        app.appScope.launch {
            j?.cancel()
            j?.join()
            saveSummary(endMs = System.currentTimeMillis(), count = count)
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (job != null) stopRecording()
        super.onDestroy()
    }
}
