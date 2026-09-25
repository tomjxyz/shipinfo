package com.tomjxyz.shipinfo.core

import java.io.Writer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Minimal RFC 4180 CSV writer. */
class CsvWriter(private val out: Writer) {
    fun row(values: List<Any?>) {
        out.write(values.joinToString(",") { escape(format(it)) })
        out.write("\r\n")
    }

    fun row(vararg values: Any?) = row(values.toList())

    companion object {
        private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        fun isoUtc(epochMs: Long): String = ISO.format(Instant.ofEpochMilli(epochMs))

        fun format(v: Any?): String = when (v) {
            null -> ""
            is Double -> if (v.isNaN()) "" else String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
            is Float -> format(v.toDouble())
            is Boolean -> if (v) "true" else "false"
            else -> v.toString()
        }

        fun escape(s: String): String =
            if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"" + s.replace("\"", "\"\"") + "\""
            } else {
                s
            }
    }
}

/** The toggleable data channels. */
enum class Channel(val label: String) {
    SPEED("Speed"),
    GPS("GPS position"),
    HEADING("Heading"),
    ROLL("Roll & pitch"),
}

/** Column layout of a live recording CSV, depending on which channels were enabled. */
object SampleColumns {
    fun header(channels: Set<Channel>): List<String> = buildList {
        add("timestamp_utc")
        add("elapsed_s")
        if (Channel.GPS in channels) {
            add("latitude")
            add("longitude")
            add("gps_accuracy_m")
        }
        if (Channel.SPEED in channels) add("speed_kn")
        if (Channel.HEADING in channels) {
            add("course_over_ground_deg")
            add("compass_heading_deg")
        }
        if (Channel.ROLL in channels) {
            add("roll_deg")
            add("pitch_deg")
            add("lateral_g")
            add("vertical_g")
        }
    }

    fun row(
        channels: Set<Channel>,
        timeMs: Long,
        startMs: Long,
        lat: Double?, lon: Double?, accuracyM: Double?,
        speedKn: Double?,
        cogDeg: Double?, compassDeg: Double?,
        rollDeg: Double?, pitchDeg: Double?, lateralG: Double?, verticalG: Double?,
    ): List<Any?> = buildList {
        add(CsvWriter.isoUtc(timeMs))
        add((timeMs - startMs) / 1000.0)
        if (Channel.GPS in channels) {
            add(lat)
            add(lon)
            add(accuracyM)
        }
        if (Channel.SPEED in channels) add(speedKn)
        if (Channel.HEADING in channels) {
            add(cogDeg)
            add(compassDeg)
        }
        if (Channel.ROLL in channels) {
            add(rollDeg)
            add(pitchDeg)
            add(lateralG)
            add(verticalG)
        }
    }

    fun encode(channels: Set<Channel>): String = channels.sortedBy { it.ordinal }.joinToString(",") { it.name }

    fun decode(s: String): Set<Channel> =
        s.split(',').mapNotNull { n -> Channel.entries.firstOrNull { it.name == n.trim() } }.toSet()
}
