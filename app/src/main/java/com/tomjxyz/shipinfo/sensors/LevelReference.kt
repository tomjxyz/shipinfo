package com.tomjxyz.shipinfo.sensors

import com.tomjxyz.shipinfo.core.PhoneOrientation
import com.tomjxyz.shipinfo.core.ShipFrame
import com.tomjxyz.shipinfo.core.Vec3

/** Last "set level" reference, shared between the live preview, live recording and roll watch. */
object LevelReference {
    @Volatile
    var up: Vec3? = null

    fun frame(orientation: PhoneOrientation): ShipFrame =
        up?.let { ShipFrame(it, orientation) } ?: ShipFrame.flat(orientation)
}
