package com.tomjxyz.shipinfo.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

object Units {
    const val MS_TO_KNOTS = 1.943844492
    private const val EARTH_RADIUS_NM = 3440.065

    fun msToKnots(ms: Double) = ms * MS_TO_KNOTS

    fun normalizeBearing(deg: Double): Double {
        val d = deg % 360.0
        return if (d < 0) d + 360.0 else d
    }

    /** Great-circle distance in nautical miles. */
    fun distanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_NM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Nautical style degrees and decimal minutes, e.g. 54°12.345'N. */
    fun formatLat(lat: Double) = formatDm(lat, if (lat >= 0) 'N' else 'S', 2)

    fun formatLon(lon: Double) = formatDm(lon, if (lon >= 0) 'E' else 'W', 3)

    private fun formatDm(value: Double, hemi: Char, degDigits: Int): String {
        val a = abs(value)
        var deg = floor(a).toInt()
        var min = (a - deg) * 60.0
        if (min >= 59.9995) {
            min = 0.0
            deg += 1
        }
        return String.format(Locale.US, "%0${degDigits}d°%06.3f'%c", deg, min, hemi)
    }

    /** Short cardinal name for a bearing, e.g. 47 -> "NE". */
    fun cardinal(deg: Double): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[((normalizeBearing(deg) + 22.5) / 45.0).toInt() % 8]
    }
}
