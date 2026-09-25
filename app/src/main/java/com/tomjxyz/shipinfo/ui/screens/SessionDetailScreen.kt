package com.tomjxyz.shipinfo.ui.screens

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomjxyz.shipinfo.app
import com.tomjxyz.shipinfo.core.Channel
import com.tomjxyz.shipinfo.core.SampleColumns
import com.tomjxyz.shipinfo.core.Units
import com.tomjxyz.shipinfo.data.RollWindowEntity
import com.tomjxyz.shipinfo.data.SampleEntity
import com.tomjxyz.shipinfo.data.SessionEntity
import com.tomjxyz.shipinfo.data.SessionType
import com.tomjxyz.shipinfo.export.CsvExporter
import com.tomjxyz.shipinfo.service.durationMs
import com.tomjxyz.shipinfo.ui.components.ChartSeries
import com.tomjxyz.shipinfo.ui.components.GeoPoint
import com.tomjxyz.shipinfo.ui.components.LineChart
import com.tomjxyz.shipinfo.ui.components.SectionCard
import com.tomjxyz.shipinfo.ui.components.Stat
import com.tomjxyz.shipinfo.ui.components.StatGrid
import com.tomjxyz.shipinfo.ui.components.TrackPlot
import com.tomjxyz.shipinfo.ui.fmt
import com.tomjxyz.shipinfo.ui.formatDateTime
import com.tomjxyz.shipinfo.ui.formatDuration
import com.tomjxyz.shipinfo.ui.theme.ChartColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    private var id: Long = -1
    lateinit var session: StateFlow<SessionEntity?>
        private set
    lateinit var samples: StateFlow<List<SampleEntity>>
        private set
    lateinit var windows: StateFlow<List<RollWindowEntity>>
        private set

    fun bind(sessionId: Long) {
        if (id == sessionId) return
        id = sessionId
        session = app.db.sessions().observe(sessionId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
        samples = app.db.samples().observeForSession(sessionId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        windows = app.db.rollWindows().observeForSession(sessionId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    fun share(s: SessionEntity) = viewModelScope.launch { CsvExporter.share(app, CsvExporter.sessionFile(app, s)) }

    fun saveTo(s: SessionEntity, uri: Uri, done: () -> Unit) = viewModelScope.launch {
        CsvExporter.writeToUri(app, uri) { CsvExporter.writeSession(app, s, it) }
        done()
    }

    fun rename(name: String) = viewModelScope.launch { app.db.sessions().rename(id, name.ifBlank { null }) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        app.db.sessions().delete(id)
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(sessionId: Long, onBack: () -> Unit, vm: SessionDetailViewModel = viewModel()) {
    vm.bind(sessionId)
    val context = LocalContext.current
    val session by vm.session.collectAsStateWithLifecycle()
    val samples by vm.samples.collectAsStateWithLifecycle()
    val windows by vm.windows.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val s = session
        if (uri != null && s != null) vm.saveTo(s, uri) { Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0.dp),
            title = { Text(session?.let { sessionTitle(it) } ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                val s = session
                if (s != null) {
                    IconButton(onClick = { renaming = s.name ?: "" }) { Icon(Icons.Filled.Edit, "Rename") }
                    IconButton(onClick = { vm.share(s) }) { Icon(Icons.Filled.Share, "Share CSV") }
                    IconButton(onClick = { saveLauncher.launch(CsvExporter.fileName(s)) }) { Icon(Icons.Filled.Save, "Save CSV") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            },
        )
        session?.let { s ->
            ScreenColumn(
                title = formatDateTime(s.startMs),
                subtitle = if (s.endMs == null) "In progress" else "Duration ${formatDuration(s.durationMs())}",
            ) {
                if (s.type == SessionType.ROLL_WATCH) RollWatchDetail(s, windows) else LiveDetail(s, samples)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete recording?") },
            text = { Text("This can't be undone. Export it first if you want to keep the data.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onBack)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    renaming?.let { current ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Name") },
            text = { OutlinedTextField(value = current, onValueChange = { renaming = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(current)
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

private fun series(
    name: String,
    color: androidx.compose.ui.graphics.Color,
    samples: List<SampleEntity>,
    t0: Long,
    dashed: Boolean = false,
    pick: (SampleEntity) -> Double?,
) = ChartSeries(
    name,
    color,
    FloatArray(samples.size) { (samples[it].timeMs - t0) / 1000f },
    FloatArray(samples.size) { pick(samples[it])?.toFloat() ?: Float.NaN },
    dashed,
)

@Composable
private fun LiveDetail(s: SessionEntity, samples: List<SampleEntity>) {
    val channels = SampleColumns.decode(s.channels)
    val t0 = s.startMs
    val stats = buildList {
        add(Stat("Samples", "${samples.size}", "every ${fmt(s.intervalMs / 1000.0, 1)} s"))
        if (Channel.SPEED in channels) {
            add(Stat("Max speed", fmt(s.maxSpeedKn ?: samples.mapNotNull { it.speedKn }.maxOrNull()), "kn", ChartColors.speed))
            add(Stat("Avg speed", fmt(s.avgSpeedKn ?: samples.mapNotNull { it.speedKn }.average().takeIf { !it.isNaN() }), "kn", ChartColors.speed))
        }
        if (Channel.GPS in channels) add(Stat("Distance", fmt(s.distanceNm, 2), "NM"))
        if (Channel.ROLL in channels) {
            add(Stat("Max roll port", fmt(s.maxRollPortDeg), "°", ChartColors.port))
            add(Stat("Max roll stbd", fmt(s.maxRollStbdDeg), "°", ChartColors.starboard))
            add(Stat("Max pitch", fmt(s.maxPitchDeg), "°", ChartColors.pitch))
            add(Stat("Max sideways", fmt(s.maxLateralG, 2), "g", ChartColors.lateral))
        }
    }
    StatGrid(stats)

    if (samples.isEmpty()) {
        Text("No samples recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text(
        "Pinch to zoom, drag or tap to read values, double-tap to reset.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (Channel.SPEED in channels) {
        SectionCard(title = "Speed") {
            LineChart(listOf(series("Speed", ChartColors.speed, samples, t0) { it.speedKn }), unit = " kn", includeZero = true)
        }
    }
    if (Channel.HEADING in channels) {
        SectionCard(title = "Heading") {
            LineChart(
                listOf(
                    series("GPS course", ChartColors.cog, samples, t0) { it.cogDeg },
                    series("Compass", ChartColors.compass, samples, t0, dashed = true) { it.compassDeg },
                ),
                unit = "°",
                fixedRange = 0f..360f,
                wrap = 360f,
                valueDecimals = 0,
            )
        }
    }
    if (Channel.ROLL in channels) {
        SectionCard(title = "Roll & pitch") {
            LineChart(
                listOf(
                    series("Roll (+stbd)", ChartColors.roll, samples, t0) { it.rollDeg },
                    series("Pitch (+bow up)", ChartColors.pitch, samples, t0) { it.pitchDeg },
                ),
                unit = "°",
                symmetric = true,
            )
        }
        SectionCard(title = "Acceleration") {
            LineChart(
                listOf(
                    series("Sideways", ChartColors.lateral, samples, t0) { it.lateralG },
                    series("Vertical", ChartColors.vertical, samples, t0) { it.verticalG },
                ),
                unit = " g",
                symmetric = true,
                valueDecimals = 3,
            )
        }
    }
    if (Channel.GPS in channels) {
        SectionCard(title = "Track") {
            val pts = samples.mapNotNull { sm -> sm.lat?.let { la -> sm.lon?.let { lo -> GeoPoint(la, lo) } } }
            TrackPlot(pts)
            if (pts.isNotEmpty()) {
                Text(
                    "Start ${Units.formatLat(pts.first().lat)} ${Units.formatLon(pts.first().lon)}\n" +
                        "End   ${Units.formatLat(pts.last().lat)} ${Units.formatLon(pts.last().lon)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RollWatchDetail(s: SessionEntity, windows: List<RollWindowEntity>) {
    val records = windows.filter { it.isRecord }
    StatGrid(
        listOf(
            Stat("Max roll port", fmt(s.maxRollPortDeg), "°", ChartColors.port),
            Stat("Max roll stbd", fmt(s.maxRollStbdDeg), "°", ChartColors.starboard),
            Stat("Max pitch", fmt(s.maxPitchDeg), "°", ChartColors.pitch),
            Stat("Max sideways", fmt(s.maxLateralG, 2), "g", ChartColors.lateral),
            Stat("Records", "${records.size}", "of ${windows.size} checks", ChartColors.record),
            Stat("Check every", "${s.intervalMs / 60_000}", "min"),
        ),
    )
    if (windows.isEmpty()) {
        Text("No completed checks in this session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val t0 = s.startMs
    val xs = FloatArray(windows.size) { (windows[it].endMs - t0) / 1000f }
    val marks = records.map { (it.endMs - t0) / 1000f }
    SectionCard(title = "Peak roll per period") { WindowRollChart(windows, t0) }
    SectionCard(title = "Peak pitch & g-force") {
        LineChart(
            listOf(ChartSeries("Pitch", ChartColors.pitch, xs, FloatArray(windows.size) { windows[it].maxPitchDeg.toFloat() })),
            unit = "°",
            includeZero = true,
            markersX = marks,
            height = 150.dp,
        )
        LineChart(
            listOf(
                ChartSeries("Sideways", ChartColors.lateral, xs, FloatArray(windows.size) { windows[it].maxLateralG.toFloat() }),
                ChartSeries("Vertical", ChartColors.vertical, xs, FloatArray(windows.size) { windows[it].maxVerticalG.toFloat() }),
            ),
            unit = " g",
            includeZero = true,
            markersX = marks,
            valueDecimals = 3,
            height = 150.dp,
        )
    }
    if (windows.any { it.rollPeriodS != null }) {
        SectionCard(title = "Roll period") {
            LineChart(
                listOf(ChartSeries("Period", ChartColors.roll, xs, FloatArray(windows.size) { windows[it].rollPeriodS?.toFloat() ?: Float.NaN })),
                unit = " s",
                height = 150.dp,
            )
        }
    }
    val located = records.mapNotNull { w -> w.lat?.let { la -> w.lon?.let { lo -> GeoPoint(la, lo) } } }
    if (located.isNotEmpty()) {
        SectionCard(title = "Where records happened") { TrackPlot(located, drawDots = true, height = 200.dp) }
    }
    SectionCard(title = "Records") { records.reversed().forEach { WindowRow(it) } }
    SectionCard(title = "All checks") { windows.reversed().forEach { WindowRow(it) } }
}
