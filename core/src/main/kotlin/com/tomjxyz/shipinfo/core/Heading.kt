package com.tomjxyz.shipinfo.core

import kotlin.math.atan2

object Heading {
    /**
     * Compass heading of the bow in degrees [0, 360) from an Android rotation matrix
     * (row-major 3x3, device -> world East/North/Up) and the device axis pointing to the bow.
     */
    fun fromRotationMatrix(r: FloatArray, bowAxis: Vec3): Double {
        val east = r[0] * bowAxis.x + r[1] * bowAxis.y + r[2] * bowAxis.z
        val north = r[3] * bowAxis.x + r[4] * bowAxis.y + r[5] * bowAxis.z
        return Units.normalizeBearing(Math.toDegrees(atan2(east, north)))
    }

    /** Smallest signed difference b - a in degrees, in (-180, 180]. */
    fun diff(a: Double, b: Double): Double {
        var d = (b - a) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }
}
