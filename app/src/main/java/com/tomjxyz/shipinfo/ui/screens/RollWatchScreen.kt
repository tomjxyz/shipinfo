package com.tomjxyz.shipinfo.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomjxyz.shipinfo.app
import com.tomjxyz.shipinfo.core.PhoneOrientation
import com.tomjxyz.shipinfo.data.AppSettings
import com.tomjxyz.shipinfo.data.RollWindowEntity
import com.tomjxyz.shipinfo.service.RollWatchPhase
import com.tomjxyz.shipinfo.service.RollWatchService
import com.tomjxyz.shipinfo.ui.Perms
import com.tomjxyz.shipinfo.ui.components.ChartSeries
import com.tomjxyz.shipinfo.ui.components.Inclinometer
import com.tomjxyz.shipinfo.ui.components.LevelControls
import com.tomjxyz.shipinfo.ui.components.LineChart
import com.tomjxyz.shipinfo.ui.components.SectionCard
import com.tomjxyz.shipinfo.ui.components.Stat
import com.tomjxyz.shipinfo.ui.components.StatGrid
import com.tomjxyz.shipinfo.ui.fmt
import com.tomjxyz.shipinfo.ui.formatDuration
import com.tomjxyz.shipinfo.ui.formatTime
import com.tomjxyz.shipinfo.ui.rememberPermissionRequest
import com.tomjxyz.shipinfo.ui.theme.ChartColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RollWatchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    val state = RollWatchService.state
    val settings: StateFlow<AppSettings> =
        app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    @OptIn(ExperimentalCoroutinesApi::class)
    val windows: StateFlow<List<RollWindowEntity>> = state.map { it.sessionId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else app.db.rollWindows().observeForSession(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setOrientation(o: PhoneOrientation) = viewModelScope.launch { app.settings.setOrientation(o) }
    fun setWindow(min: Int) = viewModelScope.launch { app.settings.setRollWindow(min) }
    fun setAutoStop(h: Int) = viewModelScope.launch { app.settings.setRollAutoStop(h) }
    fun setIncludeGps(b: Boolean) = viewModelScope.launch { app.settings.setRollIncludeGps(b) }
    fun start() = RollWatchService.start(app)
    fun stop() = RollWatchService.stop(app)
}

private val windowOptions = listOf(1, 2, 5, 10, 15, 30, 60)
private val autoStopOptions = listOf(0, 1, 2, 4, 8, 12, 24, 48)

@Composable
fun RollWatchScreen(onOpenSession: (Long) -> Unit, vm: RollWatchViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val windows by vm.windows.collectAsStateWithLifecycle()
    val startWithPerms = rememberPermissionRequest { vm.start() }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.running) {
        while (state.running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    ScreenColumn(
        title = "Roll watch",
        subtitle = if (state.running) null else "Leave the phone on a table, screen locked. It keeps the biggest rolls.",
    ) {
        when (state.phase) {
            RollWatchPhase.IDLE -> {
                SectionCard(title = "Setup") {
                    Dropdown(
                        label = "Phone placement",
                        options = PhoneOrientation.entries,
                        selected = settings.orientation,
                        optionLabel = { it.label },
                        onSelect = vm::setOrientation,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LevelControls(settings.orientation)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dropdown(
                            label = "Check every",
                            options = windowOptions,
                            selected = settings.rollWindowMinutes,
                            optionLabel = { "$it min" },
                            onSelect = vm::setWindow,
                            modifier = Modifier.weight(1f),
                        )
                        Dropdown(
                            label = "Stop after",
                            options = autoStopOptions,
                            selected = settings.rollAutoStopHours,
                            optionLabel = { if (it == 0) "Never" else "$it h" },
                            onSelect = vm::setAutoStop,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Save GPS position with records")
                            Text(
                                "Uses more battery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = settings.rollIncludeGps, onCheckedChange = vm::setIncludeGps)
                    }
                    Text(
                        "How it works: the phone samples motion continuously, relative to the level above. Every ${settings.rollWindowMinutes} min it saves the peak " +
                            "roll, pitch and sideways g-force of that period, and marks it as a new record when the roll/pitch " +
                            "angle or g-force beats the biggest so far.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            val perms = buildList {
                                if (settings.rollIncludeGps && !Perms.hasLocation(context)) addAll(Perms.location)
                                addAll(Perms.notifications.filterNot { Perms.granted(context, it) })
                            }
                            startWithPerms(perms)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start roll watch")
                    }
                }
                if (!Perms.ignoresBatteryOptimizations(context)) {
                    SectionCard(title = "Battery optimisation") {
                        Text(
                            "Some phones stop apps that run for hours with the screen off. Allow ShipInfo to run " +
                                "unrestricted so long roll watches aren't cut short.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { Perms.requestIgnoreBatteryOptimizations(context) }) { Text("Allow") }
                    }
                }
            }

            RollWatchPhase.WATCHING -> {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Watching  ${formatDuration(now - state.startMs)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val nextIn = state.windowStartMs + state.windowMs - now
                            Text(
                                "Next check in ${formatDuration(nextIn)}" +
                                    (state.autoStopAtMs?.let { " · stops in ${formatDuration(it - now)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = vm::stop,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Filled.Stop, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Stop")
                        }
                    }
                    Text(
                        "You can lock the phone now. Recording continues in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val maxPort = windows.maxOfOrNull { it.maxRollPortDeg }
                val maxStbd = windows.maxOfOrNull { it.maxRollStbdDeg }
                SectionCard(title = "Now") {
                    Inclinometer(state.rollDeg, maxPortDeg = maxPort, maxStbdDeg = maxStbd)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        LabeledValue("Roll", state.rollDeg?.let { rollText(it) } ?: "–")
                        LabeledValue("Pitch", fmt(state.pitchDeg, 1, "°"))
                        LabeledValue("This period", fmt(state.windowMaxRollDeg, 1, "°"))
                    }
                }

                StatGrid(
                    listOf(
                        Stat("Max roll", fmt(state.sessionMaxRollDeg), "°", ChartColors.roll),
                        Stat("Max pitch", fmt(state.sessionMaxPitchDeg), "°", ChartColors.pitch),
                        Stat("Max sideways", fmt(state.sessionMaxLateralG, 2), "g", ChartColors.lateral),
                        Stat("Records", "${state.records}", "of ${state.windows} checks", ChartColors.record),
                    ),
                )

                if (windows.isNotEmpty()) {
                    SectionCard(title = "Peak roll per period") {
                        WindowRollChart(windows, state.startMs)
                    }
                    SectionCard(title = "Records") {
                        windows.filter { it.isRecord }.reversed().forEach { w -> WindowRow(w) }
                    }
                }
                state.sessionId?.let { id ->
                    TextButton(onClick = { onOpenSession(id) }) { Text("Open full session view") }
                }
            }
        }
    }
}

@Composable
fun WindowRollChart(windows: List<RollWindowEntity>, startMs: Long) {
    val xs = FloatArray(windows.size) { (windows[it].endMs - startMs) / 1000f }
    LineChart(
        series = listOf(
            ChartSeries("Starboard", ChartColors.starboard, xs, FloatArray(windows.size) { windows[it].maxRollStbdDeg.toFloat() }),
            ChartSeries("Port", ChartColors.port, xs, FloatArray(windows.size) { -windows[it].maxRollPortDeg.toFloat() }),
        ),
        unit = "°",
        bars = true,
        symmetric = true,
        markersX = windows.filter { it.isRecord }.map { (it.endMs - startMs) / 1000f },
    )
    Text(
        "Bars above zero: roll to starboard, below: to port. Dashed lines mark new records.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun reasonText(reasons: String) = reasons.split(',').filter { it.isNotBlank() }.joinToString(", ") {
    when (it) {
        "ROLL" -> "roll"
        "PITCH" -> "pitch"
        "LATERAL_G" -> "g-force"
        else -> it.lowercase()
    }
}

@Composable
fun WindowRow(w: RollWindowEntity, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "${formatTime(w.startMs)} – ${formatTime(w.endMs)}" + if (w.isRecord) "  ★ new max ${reasonText(w.reasons)}" else "",
            style = MaterialTheme.typography.titleSmall,
            color = if (w.isRecord) ChartColors.record else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Roll P ${fmt(w.maxRollPortDeg)}° / S ${fmt(w.maxRollStbdDeg)}° · pitch ${fmt(w.maxPitchDeg)}° · " +
                "${fmt(w.maxLateralG, 2)} g" + (w.rollPeriodS?.let { " · period ${fmt(it)} s" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
