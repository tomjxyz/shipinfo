package com.tomjxyz.shipinfo.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PinScheduleTest {
    private val zone = ZoneId.of("UTC")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 3, 1, h, m, 0, 0, zone)
    private val hour = 3_600_000L

    @Test
    fun noAnchorStartsImmediately() {
        assertEquals(0L, PinSchedule.initialDelayMs(at(10, 0), 24, null))
    }

    @Test
    fun dailyNoonPin() {
        assertEquals(2 * hour, PinSchedule.initialDelayMs(at(10, 0), 24, 12 * 60))
        assertEquals(23 * hour, PinSchedule.initialDelayMs(at(13, 0), 24, 12 * 60))
    }

    @Test
    fun alignedToAnchorWithShorterInterval() {
        // Anchor 12:00, every 6 h -> slots 00, 06, 12, 18.
        assertEquals(hour / 2, PinSchedule.initialDelayMs(at(5, 30), 6, 12 * 60))
        assertEquals(5 * hour, PinSchedule.initialDelayMs(at(19, 0), 6, 12 * 60))
    }
}
