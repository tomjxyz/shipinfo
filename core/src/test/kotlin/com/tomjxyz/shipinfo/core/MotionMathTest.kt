package com.tomjxyz.shipinfo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class MotionMathTest {
    private fun rad(d: Double) = Math.toRadians(d)

    /** Up vector in device coords when a flat phone (top to bow) is rolled to starboard by [deg]. */
    private fun upRolledStbd(deg: Double) = Vec3(-sin(rad(deg)), 0.0, cos(rad(deg)))

    private fun upPitchedBowUp(deg: Double) = Vec3(0.0, sin(rad(deg)), cos(rad(deg)))

    @Test
    fun flatPhoneIsLevel() {
        val a = ShipFrame.flat(PhoneOrientation.TOP_TO_BOW).attitude(Vec3(0.0, 0.0, 9.81))
        assertEquals(0.0, a.rollDeg, 1e-9)
        assertEquals(0.0, a.pitchDeg, 1e-9)
    }

    @Test
    fun rollToStarboardIsPositive() {
        val a = ShipFrame.flat(PhoneOrientation.TOP_TO_BOW).attitude(upRolledStbd(12.0) * 9.81)
        assertEquals(12.0, a.rollDeg, 1e-9)
        assertEquals(0.0, a.pitchDeg, 1e-9)
    }

    @Test
    fun bowUpIsPositivePitch() {
        val a = ShipFrame.flat(PhoneOrientation.TOP_TO_BOW).attitude(upPitchedBowUp(4.0))
        assertEquals(0.0, a.rollDeg, 1e-9)
        assertEquals(4.0, a.pitchDeg, 1e-9)
    }

    @Test
    fun orientationSwapsAxes() {
        // Phone top points to starboard: a starboard roll lowers the phone's top edge.
        val up = Vec3(0.0, -sin(rad(8.0)), cos(rad(8.0)))
        val a = ShipFrame.flat(PhoneOrientation.TOP_TO_STARBOARD).attitude(up)
        assertEquals(8.0, a.rollDeg, 1e-9)
        assertEquals(0.0, a.pitchDeg, 1e-9)

        val b = ShipFrame.flat(PhoneOrientation.TOP_TO_PORT).attitude(up)
        assertEquals(-8.0, b.rollDeg, 1e-9)

        val c = ShipFrame.flat(PhoneOrientation.TOP_TO_STERN).attitude(upRolledStbd(5.0))
        assertEquals(-5.0, c.rollDeg, 1e-9)
    }

    @Test
    fun tareRemovesTableTilt() {
        // Table is tilted 3 degrees; after taring, that becomes zero and further roll is relative to it.
        val tare = TareAccumulator()
        repeat(10) { tare.add(upRolledStbd(3.0) * 9.8) }
        val frame = ShipFrame(tare.result()!!, PhoneOrientation.TOP_TO_BOW)
        assertEquals(0.0, frame.attitude(upRolledStbd(3.0)).rollDeg, 1e-6)
        assertEquals(7.0, frame.attitude(upRolledStbd(10.0)).rollDeg, 1e-6)
        assertEquals(-2.0, frame.attitude(upRolledStbd(1.0)).rollDeg, 1e-6)
    }

    @Test
    fun emptyTareIsNull() {
        assertNull(TareAccumulator().result())
    }

    @Test
    fun lateralAndVerticalG() {
        val frame = ShipFrame.flat(PhoneOrientation.TOP_TO_BOW)
        assertEquals(0.5, frame.lateralG(Vec3(STANDARD_GRAVITY / 2, 0.0, 0.0)), 1e-9)
        assertEquals(-0.2, frame.verticalG(Vec3(0.0, 0.0, -0.2 * STANDARD_GRAVITY)), 1e-9)
    }

    @Test
    fun rollPeriodFromSine() {
        val est = RollPeriodEstimator()
        val periodS = 9.0
        var t = 0L
        while (t < 120_000) {
            est.add(t, 6.0 * sin(2 * Math.PI * t / 1000.0 / periodS))
            t += 40
        }
        assertNotNull(est.meanPeriodS())
        assertEquals(periodS, est.meanPeriodS()!!, 0.1)
    }

    @Test
    fun gravityLowPassConverges() {
        val lp = GravityLowPass(0.5)
        var g = Vec3.ZERO
        for (i in 0..500) g = lp.update(Vec3(0.0, 0.0, 9.81), i * 20_000_000L)
        assertEquals(9.81, g.z, 1e-6)
    }

    @Test
    fun headingFromRotationMatrix() {
        // Identity: device y axis points north.
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        assertEquals(0.0, Heading.fromRotationMatrix(identity, PhoneOrientation.TOP_TO_BOW.bowAxis), 1e-6)
        assertEquals(180.0, Heading.fromRotationMatrix(identity, PhoneOrientation.TOP_TO_STERN.bowAxis), 1e-6)
        // Top to port -> bow is device +x -> east.
        assertEquals(90.0, Heading.fromRotationMatrix(identity, PhoneOrientation.TOP_TO_PORT.bowAxis), 1e-6)
        assertEquals(270.0, Heading.fromRotationMatrix(identity, PhoneOrientation.TOP_TO_STARBOARD.bowAxis), 1e-6)
    }

    @Test
    fun headingDiffWraps() {
        assertEquals(20.0, Heading.diff(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Heading.diff(10.0, 350.0), 1e-9)
    }
}
