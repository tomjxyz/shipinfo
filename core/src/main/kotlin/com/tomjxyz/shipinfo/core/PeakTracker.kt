package com.tomjxyz.shipinfo.core

import kotlin.math.abs
import kotlin.math.max

/** Peak motion values seen during one roll-watch window. */
data class WindowPeaks(
    val startMs: Long,
    val endMs: Long,
    val sampleCount: Int,
    /** Largest roll to starboard (positive degrees). */
    val maxRollStbdDeg: Double,
    /** Largest roll to port (positive degrees). */
    val maxRollPortDeg: Double,
    val maxPitchUpDeg: Double,
    val maxPitchDownDeg: Double,
    /** Largest absolute athwartships acceleration in g. */
    val maxLateralG: Double,
    /** Largest absolute vertical (heave) acceleration in g. */
    val maxVerticalG: Double,
    val rollPeriodS: Double?,
) {
    val maxRollDeg get() = max(maxRollStbdDeg, maxRollPortDeg)
    val maxPitchDeg get() = max(maxPitchUpDeg, maxPitchDownDeg)
}

enum class NewMaxReason { ROLL, PITCH, LATERAL_G }

data class WindowResult(val peaks: WindowPeaks, val reasons: Set<NewMaxReason>) {
    val isNewMax get() = reasons.isNotEmpty()
}

/**
 * Tracks motion peaks for the roll-watch mode. Samples are fed continuously; each time a window
 * is closed its peaks are compared with the session maximum. The window counts as a new record
 * when its roll/pitch angle or lateral acceleration beats the session max by a small margin.
 */
class PeakTracker(
    private val minAngleIncreaseDeg: Double = 0.1,
    private val minGIncrease: Double = 0.002,
) {
    var sessionMaxRollDeg = 0.0
        private set
    var sessionMaxPitchDeg = 0.0
        private set
    var sessionMaxLateralG = 0.0
        private set
    var hasSessionMax = false
        private set

    private var windowStartMs: Long? = null
    private var count = 0
    private var rollStbd = 0.0
    private var rollPort = 0.0
    private var pitchUp = 0.0
    private var pitchDown = 0.0
    private var latG = 0.0
    private var vertG = 0.0
    private val period = RollPeriodEstimator()
    private val rollMean = MeanRemover()

    /** Live peak roll of the current window (for UI), absolute degrees. */
    val currentWindowMaxRollDeg get() = max(rollStbd, rollPort)

    /** Restores the session maxima, e.g. after the service restarts. */
    fun restoreSessionMax(rollDeg: Double, pitchDeg: Double, lateralG: Double) {
        sessionMaxRollDeg = rollDeg
        sessionMaxPitchDeg = pitchDeg
        sessionMaxLateralG = lateralG
        hasSessionMax = true
    }

    fun addSample(timeMs: Long, rollDeg: Double, pitchDeg: Double, lateralG: Double, verticalG: Double) {
        if (windowStartMs == null) windowStartMs = timeMs
        count++
        if (rollDeg > rollStbd) rollStbd = rollDeg
        if (-rollDeg > rollPort) rollPort = -rollDeg
        if (pitchDeg > pitchUp) pitchUp = pitchDeg
        if (-pitchDeg > pitchDown) pitchDown = -pitchDeg
        latG = max(latG, abs(lateralG))
        vertG = max(vertG, abs(verticalG))
        period.add(timeMs, rollMean.update(timeMs, rollDeg))
    }

    /** Closes the current window. Returns null if no samples were collected. */
    fun closeWindow(endMs: Long): WindowResult? {
        val start = windowStartMs
        if (start == null || count == 0) return null
        val peaks = WindowPeaks(
            startMs = start,
            endMs = endMs,
            sampleCount = count,
            maxRollStbdDeg = rollStbd,
            maxRollPortDeg = rollPort,
            maxPitchUpDeg = pitchUp,
            maxPitchDownDeg = pitchDown,
            maxLateralG = latG,
            maxVerticalG = vertG,
            rollPeriodS = period.meanPeriodS(),
        )
        val reasons = mutableSetOf<NewMaxReason>()
        if (!hasSessionMax) {
            reasons += NewMaxReason.ROLL
        } else {
            if (peaks.maxRollDeg > sessionMaxRollDeg + minAngleIncreaseDeg) reasons += NewMaxReason.ROLL
            if (peaks.maxPitchDeg > sessionMaxPitchDeg + minAngleIncreaseDeg) reasons += NewMaxReason.PITCH
            if (peaks.maxLateralG > sessionMaxLateralG + minGIncrease) reasons += NewMaxReason.LATERAL_G
        }
        sessionMaxRollDeg = max(sessionMaxRollDeg, peaks.maxRollDeg)
        sessionMaxPitchDeg = max(sessionMaxPitchDeg, peaks.maxPitchDeg)
        sessionMaxLateralG = max(sessionMaxLateralG, peaks.maxLateralG)
        hasSessionMax = true

        windowStartMs = null
        count = 0
        rollStbd = 0.0
        rollPort = 0.0
        pitchUp = 0.0
        pitchDown = 0.0
        latG = 0.0
        vertG = 0.0
        period.reset()
        return WindowResult(peaks, reasons)
    }
}
