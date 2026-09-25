package com.tomjxyz.shipinfo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object SessionType {
    const val LIVE = "LIVE"
    const val ROLL_WATCH = "ROLL_WATCH"
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val startMs: Long,
    val endMs: Long? = null,
    /** Encoded [com.tomjxyz.shipinfo.core.Channel] set. */
    val channels: String,
    val orientation: String,
    val intervalMs: Long,
    val name: String? = null,
    val sampleCount: Int = 0,
    val maxSpeedKn: Double? = null,
    val avgSpeedKn: Double? = null,
    val distanceNm: Double? = null,
    val maxRollStbdDeg: Double? = null,
    val maxRollPortDeg: Double? = null,
    val maxPitchDeg: Double? = null,
    val maxLateralG: Double? = null,
    val recordCount: Int = 0,
)

@Entity(
    tableName = "samples",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timeMs: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Double? = null,
    val speedKn: Double? = null,
    val cogDeg: Double? = null,
    val compassDeg: Double? = null,
    val rollDeg: Double? = null,
    val pitchDeg: Double? = null,
    val lateralG: Double? = null,
    val verticalG: Double? = null,
)

@Entity(
    tableName = "roll_windows",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class RollWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startMs: Long,
    val endMs: Long,
    val sampleCount: Int,
    val maxRollStbdDeg: Double,
    val maxRollPortDeg: Double,
    val maxPitchUpDeg: Double,
    val maxPitchDownDeg: Double,
    val maxLateralG: Double,
    val maxVerticalG: Double,
    val rollPeriodS: Double?,
    val isRecord: Boolean,
    /** Comma separated [com.tomjxyz.shipinfo.core.NewMaxReason] names. */
    val reasons: String,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val maxRollDeg get() = maxOf(maxRollStbdDeg, maxRollPortDeg)
    val maxPitchDeg get() = maxOf(maxPitchUpDeg, maxPitchDownDeg)
}

@Entity(tableName = "pins")
data class PinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMs: Long,
    /** Time the GPS fix was taken; differs from [timeMs] for a stale fallback fix. */
    val fixTimeMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Double?,
    val speedKn: Double?,
    val cogDeg: Double?,
    val auto: Boolean,
    val stale: Boolean,
    val note: String? = null,
)
