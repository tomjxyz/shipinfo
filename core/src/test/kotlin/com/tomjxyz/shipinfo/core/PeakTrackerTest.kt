package com.tomjxyz.shipinfo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeakTrackerTest {
    private fun PeakTracker.feed(startMs: Long, roll: Double, pitch: Double = 0.0, lat: Double = 0.0) {
        addSample(startMs, roll, pitch, lat, 0.0)
        addSample(startMs + 1000, -roll, -pitch, -lat, 0.0)
    }

    @Test
    fun emptyWindowReturnsNull() {
        assertNull(PeakTracker().closeWindow(1000))
    }

    @Test
    fun firstWindowIsAlwaysRecord() {
        val t = PeakTracker()
        t.feed(0, 2.0)
        val r = t.closeWindow(2000)!!
        assertTrue(r.isNewMax)
        assertEquals(2.0, r.peaks.maxRollStbdDeg, 1e-9)
        assertEquals(2.0, r.peaks.maxRollPortDeg, 1e-9)
    }

    @Test
    fun onlyBiggerWindowsAreRecords() {
        val t = PeakTracker()
        t.feed(0, 5.0)
        assertTrue(t.closeWindow(2000)!!.isNewMax)

        t.feed(3000, 4.0)
        val calmer = t.closeWindow(5000)!!
        assertFalse(calmer.isNewMax)

        t.feed(6000, 5.05) // within noise margin
        assertFalse(t.closeWindow(8000)!!.isNewMax)

        t.feed(9000, 7.5)
        val bigger = t.closeWindow(11000)!!
        assertEquals(setOf(NewMaxReason.ROLL), bigger.reasons)
        assertEquals(7.5, t.sessionMaxRollDeg, 1e-9)
    }

    @Test
    fun lateralGAloneTriggersRecord() {
        val t = PeakTracker()
        t.feed(0, 5.0, lat = 0.05)
        t.closeWindow(2000)
        t.feed(3000, 3.0, lat = 0.12)
        val r = t.closeWindow(5000)!!
        assertEquals(setOf(NewMaxReason.LATERAL_G), r.reasons)
        assertEquals(0.12, r.peaks.maxLateralG, 1e-9)
    }

    @Test
    fun pitchTriggersRecord() {
        val t = PeakTracker()
        t.feed(0, 5.0, pitch = 1.0)
        t.closeWindow(2000)
        t.feed(3000, 1.0, pitch = 3.0)
        assertEquals(setOf(NewMaxReason.PITCH), t.closeWindow(5000)!!.reasons)
    }

    @Test
    fun restoredMaxIsRespected() {
        val t = PeakTracker()
        t.restoreSessionMax(10.0, 2.0, 0.1)
        t.feed(0, 8.0, 1.0, 0.05)
        assertFalse(t.closeWindow(2000)!!.isNewMax)
    }
}
