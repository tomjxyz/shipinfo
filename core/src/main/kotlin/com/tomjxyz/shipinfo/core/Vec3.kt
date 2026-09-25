package com.tomjxyz.shipinfo.core

import kotlin.math.sqrt

/** Minimal 3D vector used for orientation math. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    fun length() = sqrt(this dot this)

    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-9) this else this * (1.0 / l)
    }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        fun of(v: FloatArray) = Vec3(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
    }
}
