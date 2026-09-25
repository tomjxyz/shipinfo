package com.tomjxyz.shipinfo.core

import kotlin.math.atan2

const val STANDARD_GRAVITY = 9.80665

/** Which way the top edge of the phone points while it lies on the table / mount. */
enum class PhoneOrientation(val label: String) {
    TOP_TO_BOW("Top of phone points to bow"),
    TOP_TO_STERN("Top of phone points to stern"),
    TOP_TO_STARBOARD("Top of phone points to starboard"),
    TOP_TO_PORT("Top of phone points to port");

    /** Device-frame vector (x = right edge, y = top edge, z = out of screen) that points to the bow. */
    val bowAxis: Vec3
        get() = when (this) {
            TOP_TO_BOW -> Vec3(0.0, 1.0, 0.0)
            TOP_TO_STERN -> Vec3(0.0, -1.0, 0.0)
            // Top to starboard -> right edge points aft -> bow is -x.
            TOP_TO_STARBOARD -> Vec3(-1.0, 0.0, 0.0)
            // Top to port -> right edge points to the bow.
            TOP_TO_PORT -> Vec3(1.0, 0.0, 0.0)
        }
}

/** Roll/pitch in degrees. Roll > 0 = starboard side down; pitch > 0 = bow up. */
data class Attitude(val rollDeg: Double, val pitchDeg: Double)

/**
 * Ship reference frame expressed in device coordinates, built from a "tare" reading of the
 * gravity sensor (Android convention: the gravity sensor reports the *up* direction, i.e.
 * +9.81 on z when the phone lies screen-up).
 */
class ShipFrame(referenceUp: Vec3, orientation: PhoneOrientation) {
    val up: Vec3 = referenceUp.normalized()
    val fore: Vec3
    val starboard: Vec3

    init {
        val axis = orientation.bowAxis
        var f = axis - up * (axis dot up)
        if (f.length() < 1e-6) {
            // Phone axis is (almost) vertical; fall back to the device y axis.
            val alt = Vec3(0.0, 1.0, 0.0)
            f = alt - up * (alt dot up)
        }
        fore = f.normalized()
        starboard = (fore cross up).normalized()
    }

    /** Attitude of the ship given the current up vector (gravity sensor reading) in device coords. */
    fun attitude(currentUp: Vec3): Attitude {
        val u = currentUp.normalized()
        val vertical = u dot up
        val roll = Math.toDegrees(atan2(-(u dot starboard), vertical))
        val pitch = Math.toDegrees(atan2(u dot fore, vertical))
        return Attitude(roll, pitch)
    }

    /** Athwartships acceleration in g (positive toward starboard). */
    fun lateralG(linearAccel: Vec3): Double = (linearAccel dot starboard) / STANDARD_GRAVITY

    /** Vertical (heave) acceleration in g (positive up). */
    fun verticalG(linearAccel: Vec3): Double = (linearAccel dot up) / STANDARD_GRAVITY

    companion object {
        /** Frame assuming the phone lies perfectly flat, screen up. */
        fun flat(orientation: PhoneOrientation) = ShipFrame(Vec3(0.0, 0.0, 1.0), orientation)
    }
}

/**
 * Averages gravity readings over the tare period so the table's own tilt becomes the zero reference.
 */
class TareAccumulator {
    private var sum = Vec3.ZERO
    var count = 0
        private set

    fun add(up: Vec3) {
        sum += up.normalized()
        count++
    }

    fun result(): Vec3? = if (count == 0) null else (sum * (1.0 / count)).normalized()
}

/**
 * Simple low-pass filter separating gravity from the raw accelerometer on phones without a
 * fused gravity sensor.
 */
class GravityLowPass(private val timeConstantS: Double = 0.8) {
    private var gravity: Vec3? = null
    private var lastNanos: Long = 0

    fun update(accel: Vec3, timestampNanos: Long): Vec3 {
        val g = gravity
        if (g == null) {
            gravity = accel
            lastNanos = timestampNanos
            return accel
        }
        val dt = ((timestampNanos - lastNanos) / 1e9).coerceIn(0.0, 1.0)
        lastNanos = timestampNanos
        val alpha = dt / (timeConstantS + dt)
        val next = g + (accel - g) * alpha
        gravity = next
        return next
    }
}

/**
 * Estimates the roll period from upward zero crossings of the roll signal, with hysteresis so
 * that sensor noise around zero is ignored.
 */
class RollPeriodEstimator(
    private val hysteresisDeg: Double = 0.5,
    private val minPeriodS: Double = 2.0,
    private val maxPeriodS: Double = 60.0,
) {
    private var state = 0 // -1 below -h, +1 above +h, 0 unknown
    private var lastUpCrossMs: Long? = null
    private var periodSum = 0.0
    private var periodCount = 0

    fun add(timeMs: Long, rollDeg: Double) {
        if (rollDeg > hysteresisDeg) {
            if (state == -1) {
                val last = lastUpCrossMs
                if (last != null) {
                    val period = (timeMs - last) / 1000.0
                    if (period in minPeriodS..maxPeriodS) {
                        periodSum += period
                        periodCount++
                    }
                }
                lastUpCrossMs = timeMs
            }
            state = 1
        } else if (rollDeg < -hysteresisDeg) {
            state = -1
        }
    }

    /** Mean period in seconds, or null when no full cycle was seen. */
    fun meanPeriodS(): Double? = if (periodCount == 0) null else periodSum / periodCount

    fun reset() {
        // Keep the crossing state so a cycle spanning two windows is not lost.
        periodSum = 0.0
        periodCount = 0
    }
}

/**
 * Removes a slowly varying offset (e.g. a permanent list of the ship) from a signal so that roll
 * oscillation can be detected around the mean.
 */
class MeanRemover(private val timeConstantS: Double = 60.0) {
    private var mean: Double? = null
    private var lastMs: Long = 0

    fun update(timeMs: Long, value: Double): Double {
        val m = mean
        if (m == null) {
            mean = value
            lastMs = timeMs
            return 0.0
        }
        val dt = ((timeMs - lastMs) / 1000.0).coerceIn(0.0, 10.0)
        lastMs = timeMs
        val alpha = dt / (timeConstantS + dt)
        val next = m + (value - m) * alpha
        mean = next
        return value - next
    }
}

