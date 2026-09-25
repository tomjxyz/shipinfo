package com.tomjxyz.shipinfo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomjxyz.shipinfo.core.PhoneOrientation
import com.tomjxyz.shipinfo.core.ShipFrame
import com.tomjxyz.shipinfo.sensors.LEVEL_SAMPLE_MS
import com.tomjxyz.shipinfo.sensors.LevelReference
import com.tomjxyz.shipinfo.ui.fmt
import com.tomjxyz.shipinfo.ui.formatShortDateTime
import kotlinx.coroutines.launch
import kotlin.math.abs

/** "Set level" / "Reset to default" buttons with a line describing the current level reference. */
@Composable
fun LevelControls(orientation: PhoneOrientation, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val level by LevelReference.state.collectAsStateWithLifecycle()
    var measuring by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    measuring = true
                    scope.launch {
                        LevelReference.measure(context)
                        measuring = false
                    }
                },
                enabled = !measuring,
            ) { Text(if (measuring) "Measuring…" else "Set level") }
            TextButton(onClick = { LevelReference.reset() }, enabled = !measuring && !level.isDefault) {
                Text("Reset to default")
            }
        }
        val up = level.up
        val status = if (up == null) {
            "Level: table assumed level (default)"
        } else {
            val offset = ShipFrame.flat(orientation).attitude(up)
            val roll = offset.rollDeg
            val pitch = offset.pitchDeg
            "Level set ${level.setAtMs?.let { formatShortDateTime(it) } ?: ""}: offset roll ${fmt(abs(roll))}° " +
                (if (roll >= 0) "stbd" else "port") + ", pitch ${fmt(abs(pitch))}° " + (if (pitch >= 0) "bow up" else "bow down")
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
            "Press Set level when the ship is momentarily upright (it samples for ${LEVEL_SAMPLE_MS / 1000} s). " +
                "Reset to go back to assuming the table is level.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
