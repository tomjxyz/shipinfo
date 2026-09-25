package com.tomjxyz.shipinfo.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomjxyz.shipinfo.app
import com.tomjxyz.shipinfo.core.SampleColumns
import com.tomjxyz.shipinfo.data.SessionEntity
import com.tomjxyz.shipinfo.data.SessionType
import com.tomjxyz.shipinfo.export.CsvExporter
import com.tomjxyz.shipinfo.service.durationMs
import com.tomjxyz.shipinfo.ui.fmt
import com.tomjxyz.shipinfo.ui.formatDateTime
import com.tomjxyz.shipinfo.ui.formatDuration
import com.tomjxyz.shipinfo.ui.theme.ChartColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    val sessions: StateFlow<List<SessionEntity>> =
        app.db.sessions().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun exportAll() = viewModelScope.launch { CsvExporter.share(app, CsvExporter.allZip(app)) }
}

fun sessionTitle(s: SessionEntity) = s.name ?: if (s.type == SessionType.ROLL_WATCH) "Roll watch" else "Live recording"

@Composable
fun RecordingsScreen(onOpen: (Long) -> Unit, vm: RecordingsViewModel = viewModel()) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    ScreenColumn(
        title = "Recordings",
        subtitle = "${sessions.size} sessions",
        actions = {
            IconButton(onClick = { vm.exportAll() }, enabled = sessions.isNotEmpty()) {
                Icon(Icons.Filled.Archive, "Export everything as zip")
            }
        },
    ) {
        if (sessions.isEmpty()) {
            Text(
                "Nothing recorded yet. Start a recording on the Live tab or a Roll watch.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sessions.forEach { s -> SessionCard(s, onClick = { onOpen(s.id) }) }
    }
}

@Composable
private fun SessionCard(s: SessionEntity, onClick: () -> Unit) {
    val roll = s.type == SessionType.ROLL_WATCH
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (roll) Icons.Filled.Waves else Icons.Filled.Timeline,
                null,
                tint = if (roll) ChartColors.roll else ChartColors.speed,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(sessionTitle(s), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    formatDateTime(s.startMs) + " · " + if (s.endMs == null) "in progress" else formatDuration(s.durationMs()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val stats = buildList {
                    if (roll) {
                        add("${s.recordCount} records")
                        s.maxRollPortDeg?.let { p -> s.maxRollStbdDeg?.let { st -> add("max roll ${fmt(maxOf(p, st))}°") } }
                        s.maxLateralG?.let { add("${fmt(it, 2)} g") }
                    } else {
                        add(SampleColumns.decode(s.channels).joinToString("/") { it.label.split(' ').first().lowercase() })
                        s.maxSpeedKn?.let { add("max ${fmt(it)} kn") }
                        s.distanceNm?.let { add("${fmt(it)} NM") }
                        s.maxRollPortDeg?.let { p -> s.maxRollStbdDeg?.let { st -> add("roll ${fmt(maxOf(p, st))}°") } }
                    }
                }
                Text(stats.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
