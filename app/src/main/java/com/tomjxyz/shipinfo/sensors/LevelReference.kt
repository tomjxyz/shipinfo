package com.tomjxyz.shipinfo.sensors

import android.content.Context
import com.tomjxyz.shipinfo.core.PhoneOrientation
import com.tomjxyz.shipinfo.core.ShipFrame
import com.tomjxyz.shipinfo.core.Vec3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long "Set level" samples gravity. Short, so the ship's rolling isn't averaged in. */
const val LEVEL_SAMPLE_MS = 1_000L

/** User-set level reference. `up == null` means the default: the phone lies on a level table. */
data class LevelState(val up: Vec3? = null, val setAtMs: Long? = null) {
    val isDefault get() = up == null
}

/**
 * The level reference shared by the live preview, live recording and roll watch. Every
 * [MotionSource] follows changes immediately, including while recording.
 */
object LevelReference {
    private val _state = MutableStateFlow(LevelState())
    val state: StateFlow<LevelState> = _state.asStateFlow()

    fun set(up: Vec3, atMs: Long = System.currentTimeMillis()) {
        _state.value = LevelState(up.normalized(), atMs)
    }

    fun reset() {
        _state.value = LevelState()
    }

    fun restore(s: LevelState) {
        _state.value = s
    }

    fun frame(orientation: PhoneOrientation, s: LevelState = state.value): ShipFrame =
        s.up?.let { ShipFrame(it, orientation) } ?: ShipFrame.flat(orientation)

    /** Samples the gravity direction for [LEVEL_SAMPLE_MS] and makes it the new level. */
    suspend fun measure(context: Context): Boolean {
        val source = MotionSource(context, PhoneOrientation.TOP_TO_BOW)
        if (!source.hasSensors) return false
        source.start { }
        return try {
            source.measureLevel(LEVEL_SAMPLE_MS)
        } finally {
            source.stop()
        }
    }
}
