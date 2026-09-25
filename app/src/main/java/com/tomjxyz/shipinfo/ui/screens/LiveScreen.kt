package com.tomjxyz.shipinfo.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomjxyz.shipinfo.app
import com.tomjxyz.shipinfo.core.Channel
import com.tomjxyz.shipinfo.core.PhoneOrientation
import com.tomjxyz.shipinfo.core.Units
import com.tomjxyz.shipinfo.data.AppSettings
import com.tomjxyz.shipinfo.sensors.LiveReadings
import com.tomjxyz.shipinfo.sensors.SensorHub
import com.tomjxyz.shipinfo.service.RecordingService
import com.tomjxyz.shipinfo.ui.Perms
import com.tomjxyz.shipinfo.ui.components.ChartSeries
import com.tomjxyz.shipinfo.ui.components.CompassRose
import com.tomjxyz.shipinfo.ui.components.Inclinometer
import com.tomjxyz.shipinfo.ui.components.LineChart
import com.tomjxyz.shipinfo.ui.components.SectionCard
import com.tomjxyz.shipinfo.ui.fmt
import com.tomjxyz.shipinfo.ui.formatDuration
import com.tomjxyz.shipinfo.ui.rememberPermissionRequest
import com.tomjxyz.shipinfo.ui.theme.ChartColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LiveViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = application.app.settings
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val recording = RecordingService.state

    private var preview: SensorHub? = null
    val previewReadings = MutableStateFlow(LiveReadings())
    val previewTaring = MutableStateFlow(false)
    private var previewJob: kotlinx.coroutines.Job? = null

    /** Runs the sensors while the screen is visible and nothing is being recorded. */
    fun startPreview(orientation: PhoneOrientation) {
        stopPreview()
        if (recording.value.recording) return
        val hub = SensorHub(getApplication(), viewModelScope, Channel.entries.toSet(), orientation)
        hub.start()
        preview = hub
        previewJob = viewModelScope.launch { hub.readings.collect { previewReadings.value = it } }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        preview?.stop()
        preview = null
    }

    fun setChannel(c: Channel, on: Boolean) = viewModelScope.launch { settingsRepo.setChannel(c, on) }
    fun setInterval(ms: Long) = viewModelScope.launch { settingsRepo.setSampleInterval(ms) }
    fun setOrientation(o: PhoneOrientation) = viewModelScope.launch { settingsRepo.setOrientation(o) }

    fun setLevel() {
        if (recording.value.recording) {
            RecordingService.tare(getApplication())
        } else {
            val hub = preview ?: return
            viewModelScope.launch {
                previewTaring.value = true
                hub.tare(RecordingService.TARE_MS)
                previewTaring.value = false
            }
        }
    }

    fun start() {
        stopPreview()
        RecordingService.start(getApplication())
    }

    fun stop() = RecordingService.stop(getApplication())

    override fun onCleared() {
        stopPreview()
    }
}

private val intervalOptions = listOf(500L, 1000L, 2000L, 5000L, 10_000L, 30_000L, 60_000L)

private fun intervalLabel(ms: Long) = if (ms < 1000) "${ms / 1000.0} s" else if (ms < 60_000) "${ms / 1000} s" else "${ms / 60_000} min"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveScreen(vm: LiveViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rec by vm.recording.collectAsStateWithLifecycle()
    val preview by vm.previewReadings.collectAsStateWithLifecycle()
    val previewTaring by vm.previewTaring.collectAsStateWithLifecycle()

    val requestPerms = rememberPermissionRequest { vm.startPreview(settings.orientation) }
    LaunchedEffect(Unit) {
        if (!Perms.hasLocation(context)) requestPerms(Perms.location)
    }
    LifecycleResumeEffect(rec.recording, settings.orientation) {
        if (!rec.recording) vm.startPreview(settings.orientation)
        onPauseOrDispose { vm.stopPreview() }
    }

    val startWithPerms = rememberPermissionRequest { vm.start() }

    val channels = if (rec.recording) rec.channels else settings.channels
    val r = if (rec.recording) rec.readings else preview
    val taring = if (rec.recording) rec.taring else previewTaring

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(rec.recording) {
        while (rec.recording) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    ScreenColumn(title = "Live", subtitle = if (rec.recording) null else "Choose what to record, then press Start") {
        // Record control.
        SectionCard {
            if (rec.recording) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(ChartColors.record, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Recording  ${formatDuration(now - rec.startMs)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${rec.sampleCount} samples · every ${intervalLabel(settings.sampleIntervalMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { vm.stop() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.Stop, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Channel.entries.forEach { c ->
                        val on = c in settings.channels
                        FilterChip(
                            selected = on,
                            onClick = { vm.setChannel(c, !on) },
                            label = { Text(c.label) },
                            leadingIcon = if (on) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Dropdown(
                        label = "Sample every",
                        options = intervalOptions,
                        selected = settings.sampleIntervalMs,
                        optionLabel = ::intervalLabel,
                        onSelect = vm::setInterval,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val perms = buildList {
                                val needsLoc = Channel.GPS in settings.channels || Channel.SPEED in settings.channels ||
                                    Channel.HEADING in settings.channels
                                if (needsLoc && !Perms.hasLocation(context)) addAll(Perms.location)
                                addAll(Perms.notifications.filterNot { Perms.granted(context, it) })
                            }
                            startWithPerms(perms)
                        },
                        enabled = settings.channels.isNotEmpty(),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Icon(Icons.Filled.FiberManualRecord, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Start")
                    }
                }
            }
        }

        if (Channel.SPEED in channels) SpeedCard(r, rec.history.mapNotNull { s -> s.speedKn?.let { s.timeMs to it } })
        if (Channel.HEADING in channels) HeadingCard(r)
        if (Channel.ROLL in channels) {
            RollCard(
                r = r,
                maxPort = if (rec.recording) rec.maxRollPortDeg else null,
                maxStbd = if (rec.recording) rec.maxRollStbdDeg else null,
                taring = taring,
                orientation = settings.orientation,
                recording = rec.recording,
                onLevel = vm::setLevel,
                onOrientation = vm::setOrientation,
            )
        }
        if (Channel.GPS in channels) PositionCard(r, now)
        if (channels.isEmpty()) {
            Text("Nothing selected. Turn on at least one of Speed, GPS, Heading or Roll.")
        }
    }
}

@Composable
private fun BigValue(value: String, unit: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        Text(value, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(" $unit", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpeedCard(r: LiveReadings, history: List<Pair<Long, Double>>) {
    SectionCard(title = "Speed over ground") {
        BigValue(fmt(r.speedKn), "kn")
        if (history.size > 2) {
            val t0 = history.first().first
            LineChart(
                series = listOf(
                    ChartSeries(
                        "Speed",
                        ChartColors.speed,
                        FloatArray(history.size) { (history[it].first - t0) / 1000f },
                        FloatArray(history.size) { history[it].second.toFloat() },
                    ),
                ),
                height = 110.dp,
                unit = " kn",
                includeZero = true,
            )
        }
    }
}

@Composable
private fun HeadingCard(r: LiveReadings) {
    SectionCard(title = "Heading") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompassRose(r.cogDeg, r.compassDeg, Modifier.widthIn(max = 200.dp).weight(1f))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GPS course", style = MaterialTheme.typography.labelMedium, color = ChartColors.cog)
                Text(
                    r.cogDeg?.let { "${fmt(it, 0)}° ${Units.cardinal(it)}" } ?: "–",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("Compass (true)", style = MaterialTheme.typography.labelMedium, color = ChartColors.compass)
                Text(
                    r.compassDeg?.let { "${fmt(it, 0)}° ${Units.cardinal(it)}" } ?: "–",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (r.cogDeg == null) {
                    Text(
                        "GPS course needs the ship to be moving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RollCard(
    r: LiveReadings,
    maxPort: Double?,
    maxStbd: Double?,
    taring: Boolean,
    orientation: PhoneOrientation,
    recording: Boolean,
    onLevel: () -> Unit,
    onOrientation: (PhoneOrientation) -> Unit,
) {
    SectionCard(title = "Roll & pitch") {
        Inclinometer(r.rollDeg, maxPortDeg = maxPort, maxStbdDeg = maxStbd)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            LabeledValue("Roll", r.rollDeg?.let { rollText(it) } ?: "–")
            LabeledValue("Pitch", r.pitchDeg?.let { "${fmt(kotlin.math.abs(it))}° ${if (it >= 0) "bow up" else "bow down"}" } ?: "–")
            LabeledValue("Lateral", fmt(r.lateralG, 2, " g"))
        }
        if (maxPort != null && maxStbd != null) {
            Text(
                "Max this recording: port ${fmt(maxPort)}°, starboard ${fmt(maxStbd)}°",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown(
                label = "Phone placement",
                options = PhoneOrientation.entries,
                selected = orientation,
                optionLabel = { it.label },
                onSelect = onOrientation,
                enabled = !recording,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onLevel, enabled = !taring) {
                Text(if (taring) "Levelling…" else "Set level")
            }
        }
        Text(
            "Lay the phone flat, then press Set level while the ship is upright to zero out the table's tilt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun rollText(deg: Double) = when {
    kotlin.math.abs(deg) < 0.05 -> "0.0°"
    deg > 0 -> "${fmt(deg)}° stbd"
    else -> "${fmt(-deg)}° port"
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PositionCard(r: LiveReadings, now: Long) {
    SectionCard(title = "Position") {
        if (r.lat == null || r.lon == null) {
            Text("Waiting for GPS fix… (go near a window or out on deck)")
        } else {
            Text(Units.formatLat(r.lat), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(Units.formatLon(r.lon), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            val age = r.fixTimeMs?.let { ((now - it) / 1000).coerceAtLeast(0) }
            Text(
                "±${fmt(r.accuracyM, 0)} m" + (age?.let { " · fix ${it}s ago" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
