package com.tomjxyz.shipinfo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tomjxyz.shipinfo.core.Channel
import com.tomjxyz.shipinfo.core.PhoneOrientation
import com.tomjxyz.shipinfo.core.SampleColumns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val channels: Set<Channel> = Channel.entries.toSet(),
    val sampleIntervalMs: Long = 1000,
    val orientation: PhoneOrientation = PhoneOrientation.TOP_TO_BOW,
    /** 0 = automatic pins off. */
    val autoPinIntervalHours: Int = 0,
    /** Minutes after local midnight to align automatic pins to, or null. */
    val autoPinAnchorMinutes: Int? = 12 * 60,
    val rollWindowMinutes: Int = 10,
    /** 0 = run until stopped. */
    val rollAutoStopHours: Int = 0,
    val rollIncludeGps: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val channels = stringPreferencesKey("channels")
        val interval = longPreferencesKey("sample_interval_ms")
        val orientation = stringPreferencesKey("orientation")
        val pinInterval = intPreferencesKey("auto_pin_interval_h")
        val pinAnchor = intPreferencesKey("auto_pin_anchor_min")
        val rollWindow = intPreferencesKey("roll_window_min")
        val rollAutoStop = intPreferencesKey("roll_auto_stop_h")
        val rollGps = booleanPreferencesKey("roll_include_gps")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            channels = this[Keys.channels]?.let { SampleColumns.decode(it) } ?: d.channels,
            sampleIntervalMs = this[Keys.interval] ?: d.sampleIntervalMs,
            orientation = this[Keys.orientation]
                ?.let { n -> PhoneOrientation.entries.firstOrNull { it.name == n } } ?: d.orientation,
            autoPinIntervalHours = this[Keys.pinInterval] ?: d.autoPinIntervalHours,
            autoPinAnchorMinutes = this[Keys.pinAnchor].let { if (it == null) d.autoPinAnchorMinutes else if (it < 0) null else it },
            rollWindowMinutes = this[Keys.rollWindow] ?: d.rollWindowMinutes,
            rollAutoStopHours = this[Keys.rollAutoStop] ?: d.rollAutoStopHours,
            rollIncludeGps = this[Keys.rollGps] ?: d.rollIncludeGps,
        )
    }

    suspend fun setChannel(channel: Channel, enabled: Boolean) = context.dataStore.edit {
        val current = it[Keys.channels]?.let { s -> SampleColumns.decode(s) } ?: AppSettings().channels
        it[Keys.channels] = SampleColumns.encode(if (enabled) current + channel else current - channel)
    }

    suspend fun setSampleInterval(ms: Long) = context.dataStore.edit { it[Keys.interval] = ms }
    suspend fun setOrientation(o: PhoneOrientation) = context.dataStore.edit { it[Keys.orientation] = o.name }
    suspend fun setAutoPin(intervalHours: Int, anchorMinutes: Int?) = context.dataStore.edit {
        it[Keys.pinInterval] = intervalHours
        it[Keys.pinAnchor] = anchorMinutes ?: -1
    }

    suspend fun setRollWindow(min: Int) = context.dataStore.edit { it[Keys.rollWindow] = min }
    suspend fun setRollAutoStop(h: Int) = context.dataStore.edit { it[Keys.rollAutoStop] = h }
    suspend fun setRollIncludeGps(b: Boolean) = context.dataStore.edit { it[Keys.rollGps] = b }
}
