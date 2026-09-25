package com.tomjxyz.shipinfo.core

import java.time.Duration
import java.time.ZonedDateTime

object PinSchedule {
    val INTERVAL_OPTIONS_HOURS = listOf(0, 1, 3, 6, 12, 24)

    /**
     * Delay until the first automatic pin. With an anchor time (minutes after local midnight), pins are
     * aligned to anchor + k * interval, e.g. anchor 12:00 with 6 h interval -> 00:00, 06:00, 12:00, 18:00.
     */
    fun initialDelayMs(now: ZonedDateTime, intervalHours: Int, anchorMinuteOfDay: Int?): Long {
        if (anchorMinuteOfDay == null || intervalHours <= 0) return 0L
        val interval = Duration.ofHours(intervalHours.toLong())
        var next = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(anchorMinuteOfDay.toLong())
        // Step back to the earliest aligned slot today, then forward to the first one after now.
        while (next.minus(interval).isAfter(now.toLocalDate().atStartOfDay(now.zone).minusNanos(1))) {
            next = next.minus(interval)
        }
        while (!next.isAfter(now)) next = next.plus(interval)
        return Duration.between(now, next).toMillis()
    }
}
