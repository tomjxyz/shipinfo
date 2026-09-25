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
import com.tomjxyz.shipinfo.core.PeakTracker
import com.tomjxyz.shipinfo.core.SampleColumns
import com.tomjxyz.shipinfo.core.WindowResult
import com.tomjxyz.shipinfo.data.RollWindowEntity
import com.tomjxyz.shipinfo.data.SessionEntity
import com.tomjxyz.shipinfo.data.SessionType
import com.tomjxyz.shipinfo.sensors.SensorHub
import com.tomjxyz.shipinfo.ui.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class RollWatchPhase { IDLE, WATCHING }

data class RollWatchState(
    val phase: RollWatchPhase = RollWatchPhase.IDLE,
    val sessionId: Long? = null,
    val startMs: Long = 0,
    val windowStartMs: Long = 0,
    val windowMs: Long = 0,
    val autoStopAtMs: Long? = null,
    val rollDeg: Double? = null,
    val pitchDeg: Double? = null,
    val lateralG: Double? = null,
    val windowMaxRollDeg: Double = 0.0,
    val sessionMaxRollDeg: Double = 0.0,
    val sessionMaxPitchDeg: Double = 0.0,
    val sessionMaxLateralG: Double = 0.0,
    val windows: Int = 0,
    val records: Int = 0,
    val lastRecordMs: Long? = null,
) {
    val running get() = phase != RollWatchPhase.IDLE
}

/**
 * Roll watch mode: the phone lies on a table (screen may be locked) and motion is sampled
 * continuously. At the end of every window the peaks are stored, and flagged as a record when
 * they beat the session maximum.
 */
class RollWatchService : LifecycleService() {
    companion object {
        private const val ACTION_START = "com.tomjxyz.shipinfo.roll.START"
        private const val ACTION_STOP = "com.tomjxyz.shipinfo.roll.STOP"

        private val _state = MutableStateFlow(RollWatchState())
        val state: StateFlow<RollWatchState> = _state.asStateFlow()

        fun start(context: Context) = ContextCompat.startForegroundService(
            context, Intent(context, RollWatchService::class.java).setAction(ACTION_START),
        )

        fun stop(context: Context) {
            context.startService(Intent(context, RollWatchService::class.java).setAction(ACTION_STOP))
        }
    }

    private var hub: SensorHub? = null
    private var job: Job? = null
    private var uiJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var session: SessionEntity? = null
    private var includeGps = false
    private val tracker = PeakTracker()
    private var windowCount = 0
    private var recordCount = 0
    private var maxStbd = 0.0
    private var maxPort = 0.0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startWatch()
            ACTION_STOP -> stopWatch()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopIntent() = Notifications.serviceIntent(this, RollWatchService::class.java, ACTION_STOP)

    private fun notify(text: String) {
        runCatching {
            NotificationManagerCompat.from(this).notify(
                Notifications.ID_ROLL_WATCH,
                Notifications.ongoing(this, "Roll watch", text, "roll", stopIntent()),
            )
        }
    }

    private fun startWatch() {
        if (job != null) return
        startForegroundCompat(
            Notifications.ID_ROLL_WATCH,
            Notifications.ongoing(this, "Roll watch", "Starting…", "roll", stopIntent()),
            useLocation = hasLocationPermission(),
        )
        wakeLock = partialWakeLock("rollwatch").also { it.acquire(7 * 24 * 60 * 60 * 1000L) }

        job = lifecycleScope.launch {
            val settings = app.settings.current()
            includeGps = settings.rollIncludeGps && hasLocationPermission()
            val channels = buildSet {
                add(Channel.ROLL)
                if (includeGps) add(Channel.GPS)
            }
            val h = SensorHub(this@RollWatchService, this, channels, settings.orientation, gpsIntervalMs = 60_000)
            hub = h
            h.start()
            startUiUpdates(h)

            val windowMs = settings.rollWindowMinutes * 60_000L
            val startMs = System.currentTimeMillis()
            val autoStopAt = if (settings.rollAutoStopHours > 0) startMs + settings.rollAutoStopHours * 3_600_000L else null
            val newSession = SessionEntity(
                type = SessionType.ROLL_WATCH,
                startMs = startMs,
                channels = SampleColumns.encode(channels),
                orientation = settings.orientation.name,
                intervalMs = windowMs,
            )
            val id = app.db.sessions().insert(newSession)
            session = newSession.copy(id = id)

            h.onMotion = { r ->
                synchronized(tracker) { tracker.addSample(r.timeMs, r.rollDeg, r.pitchDeg, r.lateralG, r.verticalG) }
            }
            _state.update {
                it.copy(
                    phase = RollWatchPhase.WATCHING,
                    sessionId = id,
                    startMs = startMs,
                    windowStartMs = startMs,
                    windowMs = windowMs,
                    autoStopAtMs = autoStopAt,
                )
            }
            notify("Watching. Checking every ${settings.rollWindowMinutes} min.")

            var windowEnd = startMs + windowMs
            while (isActive) {
                val wakeAt = if (autoStopAt != null) min(windowEnd, autoStopAt) else windowEnd
                delay(max(0L, wakeAt - System.currentTimeMillis()))
                val now = System.currentTimeMillis()
                if (now >= windowEnd || (autoStopAt != null && now >= autoStopAt)) {
                    closeWindow(now, h)
                    windowEnd = now + windowMs
                    _state.update { it.copy(windowStartMs = now) }
                }
                if (autoStopAt != null && now >= autoStopAt) {
                    stopWatch(windowAlreadyClosed = true)
                    break
                }
            }
        }
    }

    private fun startUiUpdates(h: SensorHub) {
        uiJob = lifecycleScope.launch {
            while (isActive) {
                val r = h.readings.value
                val windowMax = synchronized(tracker) { tracker.currentWindowMaxRollDeg }
                _state.update {
                    it.copy(
                        rollDeg = r.rollDeg,
                        pitchDeg = r.pitchDeg,
                        lateralG = r.lateralG,
                        windowMaxRollDeg = windowMax,
                    )
                }
                delay(200)
            }
        }
    }

    private suspend fun closeWindow(now: Long, h: SensorHub?) {
        val s = session ?: return
        val result: WindowResult = synchronized(tracker) { tracker.closeWindow(now) } ?: return
        val p = result.peaks
        val r = h?.readings?.value
        val window = RollWindowEntity(
            sessionId = s.id,
            startMs = p.startMs,
            endMs = p.endMs,
            sampleCount = p.sampleCount,
            maxRollStbdDeg = p.maxRollStbdDeg,
            maxRollPortDeg = p.maxRollPortDeg,
            maxPitchUpDeg = p.maxPitchUpDeg,
            maxPitchDownDeg = p.maxPitchDownDeg,
            maxLateralG = p.maxLateralG,
            maxVerticalG = p.maxVerticalG,
            rollPeriodS = p.rollPeriodS,
            isRecord = result.isNewMax,
            reasons = result.reasons.joinToString(",") { it.name },
            lat = if (includeGps) r?.lat else null,
            lon = if (includeGps) r?.lon else null,
        )
        app.db.rollWindows().insert(window)
        windowCount++
        if (result.isNewMax) recordCount++
        maxStbd = max(maxStbd, p.maxRollStbdDeg)
        maxPort = max(maxPort, p.maxRollPortDeg)
        val (maxRoll, maxPitch, maxLat) = synchronized(tracker) {
            Triple(tracker.sessionMaxRollDeg, tracker.sessionMaxPitchDeg, tracker.sessionMaxLateralG)
        }
        val updated = s.copy(
            sampleCount = windowCount,
            recordCount = recordCount,
            maxRollStbdDeg = maxStbd,
            maxRollPortDeg = maxPort,
            maxPitchDeg = maxPitch,
            maxLateralG = maxLat,
            endMs = now,
        )
        session = updated
        app.db.sessions().update(updated)
        _state.update {
            it.copy(
                sessionMaxRollDeg = maxRoll,
                sessionMaxPitchDeg = maxPitch,
                sessionMaxLateralG = maxLat,
                windows = windowCount,
                records = recordCount,
                lastRecordMs = if (result.isNewMax) now else it.lastRecordMs,
            )
        }
        notify(
            String.format(
                Locale.US,
                "Max roll %.1f° (P %.1f° / S %.1f°) · %.2f g · %d records · %s",
                maxRoll, maxPort, maxStbd, maxLat, recordCount, formatDuration(now - s.startMs),
            ),
        )
    }

    private fun stopWatch(windowAlreadyClosed: Boolean = false) {
        val j = job
        job = null
        uiJob?.cancel()
        uiJob = null
        val h = hub
        hub = null
        h?.onMotion = null
        _state.value = RollWatchState()
        app.appScope.launch {
            if (!windowAlreadyClosed) {
                j?.cancel()
                j?.join()
                // Keep the partially completed last window: it may hold the biggest roll.
                closeWindow(System.currentTimeMillis(), h)
            }
            h?.stop()
            session?.let { app.db.sessions().update(it.copy(endMs = System.currentTimeMillis())) }
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (job != null) stopWatch()
        super.onDestroy()
    }
}
