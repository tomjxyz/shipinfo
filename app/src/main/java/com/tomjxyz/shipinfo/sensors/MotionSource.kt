package com.tomjxyz.shipinfo.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.tomjxyz.shipinfo.core.GravityLowPass
import com.tomjxyz.shipinfo.core.Heading
import com.tomjxyz.shipinfo.core.PhoneOrientation
import com.tomjxyz.shipinfo.core.ShipFrame
import com.tomjxyz.shipinfo.core.TareAccumulator
import com.tomjxyz.shipinfo.core.Units
import com.tomjxyz.shipinfo.core.Vec3
import kotlinx.coroutines.delay

data class MotionReading(
    val timeMs: Long,
    val rollDeg: Double,
    val pitchDeg: Double,
    val lateralG: Double,
    val verticalG: Double,
    /** True heading of the bow from the compass, if a rotation vector sensor exists. */
    val compassDeg: Double?,
)

/**
 * Roll, pitch, accelerations and compass heading from the phone's motion sensors. Uses the fused
 * gravity sensor when available, otherwise a low-pass filtered accelerometer.
 */
class MotionSource(context: Context, private val orientation: PhoneOrientation) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravity = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val rotation = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    @Volatile
    var frame: ShipFrame = LevelReference.frame(orientation)
        private set

    /** Magnetic declination (east positive) added to the compass to get a true heading. */
    @Volatile
    var declinationDeg: Double = 0.0

    @Volatile
    private var tare: TareAccumulator? = null

    private var thread: HandlerThread? = null
    private var listener: ((MotionReading) -> Unit)? = null
    private val lowPass = GravityLowPass()
    private var lastUp: Vec3? = null
    private var lastLinear: Vec3 = Vec3.ZERO
    private var lastCompass: Double? = null
    private val rotationMatrix = FloatArray(9)

    val hasSensors: Boolean get() = accel != null || gravity != null
    val hasCompass: Boolean get() = rotation != null

    private val eventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_GRAVITY -> {
                    val up = Vec3.of(event.values)
                    lastUp = up
                    emit(up)
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    val a = Vec3.of(event.values)
                    if (gravity != null) {
                        lastUp?.let { lastLinear = a - it }
                    } else {
                        val up = lowPass.update(a, event.timestamp)
                        lastUp = up
                        lastLinear = a - up
                        emit(up)
                    }
                }

                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val magnetic = Heading.fromRotationMatrix(rotationMatrix, frame.fore)
                    lastCompass = Units.normalizeBearing(magnetic + declinationDeg)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun emit(up: Vec3) {
        tare?.add(up)
        val f = frame
        val att = f.attitude(up)
        listener?.invoke(
            MotionReading(
                timeMs = System.currentTimeMillis(),
                rollDeg = att.rollDeg,
                pitchDeg = att.pitchDeg,
                lateralG = f.lateralG(lastLinear),
                verticalG = f.verticalG(lastLinear),
                compassDeg = lastCompass,
            ),
        )
    }

    fun start(samplingPeriodUs: Int = 40_000, onReading: (MotionReading) -> Unit) {
        val sm = sensors ?: return
        if (thread != null) return
        listener = onReading
        val t = HandlerThread("motion").also { it.start() }
        thread = t
        val handler = Handler(t.looper)
        accel?.let { sm.registerListener(eventListener, it, samplingPeriodUs, handler) }
        gravity?.let { sm.registerListener(eventListener, it, samplingPeriodUs, handler) }
        rotation?.let { sm.registerListener(eventListener, it, 100_000, handler) }
    }

    fun stop() {
        sensors?.unregisterListener(eventListener)
        thread?.quitSafely()
        thread = null
        listener = null
    }

    /** Averages the gravity direction for [durationMs] and uses it as the level reference. */
    suspend fun tare(durationMs: Long) {
        val acc = TareAccumulator()
        tare = acc
        delay(durationMs)
        tare = null
        acc.result()?.let {
            LevelReference.up = it
            frame = ShipFrame(it, orientation)
        }
    }
}
