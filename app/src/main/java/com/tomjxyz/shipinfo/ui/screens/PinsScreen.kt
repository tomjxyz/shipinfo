package com.tomjxyz.shipinfo.ui.screens

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.tomjxyz.shipinfo.core.PinSchedule
import com.tomjxyz.shipinfo.core.Units
import com.tomjxyz.shipinfo.data.AppSettings
import com.tomjxyz.shipinfo.data.PinEntity
import com.tomjxyz.shipinfo.export.CsvExporter
import com.tomjxyz.shipinfo.ui.Perms
import com.tomjxyz.shipinfo.ui.components.GeoPoint
import com.tomjxyz.shipinfo.ui.components.SectionCard
import com.tomjxyz.shipinfo.ui.components.TrackPlot
import com.tomjxyz.shipinfo.ui.fmt
import com.tomjxyz.shipinfo.ui.formatDateTime
import com.tomjxyz.shipinfo.ui.rememberPermissionRequest
import com.tomjxyz.shipinfo.work.PinResult
import com.tomjxyz.shipinfo.work.PinScheduler
import com.tomjxyz.shipinfo.work.PinTaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class PinsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    val pins: StateFlow<List<PinEntity>> =
        app.db.pins().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<AppSettings> =
        app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val taking = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun dropPin() {
        if (taking.value) return
        taking.value = true
        // App scope so the pin is still saved if the user leaves the screen while waiting for GPS.
        app.appScope.launch {
            message.value = when (val r = PinTaker.take(app, auto = false)) {
                is PinResult.Saved -> if (r.pin.stale) "No fresh GPS fix, used last known position" else "Position saved"
                PinResult.NoPermission -> "Location permission is needed"
                PinResult.NoFix -> "No GPS fix available. Try near a window or on deck."
            }
            taking.value = false
        }
    }

    fun setAutoPin(intervalHours: Int, anchorMinutes: Int?) = viewModelScope.launch {
        app.settings.setAutoPin(intervalHours, anchorMinutes)
        PinScheduler.apply(app, intervalHours, anchorMinutes)
    }

    fun delete(pin: PinEntity) = viewModelScope.launch { app.db.pins().delete(pin) }

    fun share() = viewModelScope.launch { CsvExporter.share(app, CsvExporter.pinsFile(app)) }

    fun saveTo(uri: Uri) = viewModelScope.launch {
        CsvExporter.writeToUri(app, uri) { CsvExporter.writePins(app, it) }
        message.value = "Saved"
    }
}

private fun intervalName(h: Int) = when (h) {
    0 -> "Off"
    1 -> "Every hour"
    24 -> "Once a day"
    else -> "Every $h hours"
}

private fun hhmm(min: Int) = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

@Composable
fun PinsScreen(vm: PinsViewModel = viewModel()) {
    val context = LocalContext.current
    val pins by vm.pins.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val taking by vm.taking.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<PinEntity?>(null) }
    var selected by remember { mutableStateOf<PinEntity?>(null) }
    var askBackground by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.message.value = null
        }
    }

    val requestThenPin = rememberPermissionRequest { vm.dropPin() }
    val requestBackground = rememberPermissionRequest { }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.saveTo(it) }
    }

    ScreenColumn(
        title = "Positions",
        subtitle = "${pins.size} pinned positions",
        actions = {
            IconButton(onClick = { vm.share() }, enabled = pins.isNotEmpty()) { Icon(Icons.Filled.Share, "Share CSV") }
            IconButton(onClick = { saveLauncher.launch(CsvExporter.pinsFileName()) }, enabled = pins.isNotEmpty()) {
                Icon(Icons.Filled.Save, "Save CSV")
            }
        },
    ) {
        Button(
            onClick = {
                if (Perms.hasLocation(context)) vm.dropPin() else requestThenPin(Perms.location)
            },
            enabled = !taking,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (taking) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Waiting for GPS fix…")
            } else {
                Icon(Icons.Filled.AddLocationAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Pin current position")
            }
        }

        SectionCard(title = "Automatic pins") {
            Dropdown(
                label = "Frequency",
                options = PinSchedule.INTERVAL_OPTIONS_HOURS,
                selected = settings.autoPinIntervalHours,
                optionLabel = ::intervalName,
                onSelect = { h ->
                    vm.setAutoPin(h, settings.autoPinAnchorMinutes)
                    if (h > 0 && !Perms.hasBackgroundLocation(context)) askBackground = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (settings.autoPinIntervalHours > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Align to a time of day", Modifier.weight(1f))
                    Switch(
                        checked = settings.autoPinAnchorMinutes != null,
                        onCheckedChange = { on -> vm.setAutoPin(settings.autoPinIntervalHours, if (on) 12 * 60 else null) },
                    )
                }
                settings.autoPinAnchorMinutes?.let { anchor ->
                    Dropdown(
                        label = "At",
                        options = (0 until 24).map { it * 60 },
                        selected = anchor - anchor % 60,
                        optionLabel = ::hhmm,
                        onSelect = { vm.setAutoPin(settings.autoPinIntervalHours, it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Android runs scheduled work in batches, so pins may arrive up to ~15 minutes late.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!Perms.hasBackgroundLocation(context)) {
                    TextButton(onClick = { askBackground = true }) { Text("Allow location in the background…") }
                }
            }
        }

        if (pins.isNotEmpty()) {
            SectionCard(title = "Track") {
                TrackPlot(
                    points = pins.reversed().map { GeoPoint(it.lat, it.lon) },
                    drawDots = true,
                    highlight = selected?.let { GeoPoint(it.lat, it.lon) },
                )
                val chronological = pins.reversed()
                var total = 0.0
                for (i in 1 until chronological.size) {
                    total += Units.distanceNm(chronological[i - 1].lat, chronological[i - 1].lon, chronological[i].lat, chronological[i].lon)
                }
                Text("Total distance between pins: ${fmt(total, 1)} NM", style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard(title = "Pins") {
            if (pins.isEmpty()) {
                Text("No positions yet. Tap “Pin current position”, or turn on automatic pins.")
            }
            pins.forEachIndexed { index, p ->
                val previous = pins.getOrNull(index + 1)
                PinRow(
                    pin = p,
                    distanceNm = previous?.let { Units.distanceNm(it.lat, it.lon, p.lat, p.lon) },
                    onClick = { selected = if (selected?.id == p.id) null else p },
                    onOpen = {
                        val uri = Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(${Uri.encode(formatDateTime(p.timeMs))})")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            .onFailure { Toast.makeText(context, "No map app installed", Toast.LENGTH_SHORT).show() }
                    },
                    onDelete = { confirmDelete = p },
                )
                if (index < pins.lastIndex) HorizontalDivider()
            }
        }
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete pin?") },
            text = { Text(formatDateTime(p.timeMs)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(p)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    if (askBackground) {
        AlertDialog(
            onDismissRequest = { askBackground = false },
            title = { Text("Background location") },
            text = {
                Text(
                    "Automatic pins are taken while the app is closed, which needs location access “All the time”. " +
                        "On the next screen choose “Allow all the time”.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askBackground = false
                    when {
                        !Perms.hasLocation(context) -> requestBackground(Perms.location)
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Perms.openAppSettings(context)
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                            requestBackground(listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                    }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { askBackground = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun PinRow(
    pin: PinEntity,
    distanceNm: Double?,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatDateTime(pin.timeMs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${Units.formatLat(pin.lat)}  ${Units.formatLon(pin.lon)}", style = MaterialTheme.typography.bodyMedium)
            val details = buildList {
                pin.speedKn?.let { add("${fmt(it)} kn") }
                pin.cogDeg?.let { add("${fmt(it, 0)}°") }
                pin.accuracyM?.let { add("±${fmt(it, 0)} m") }
                distanceNm?.let { add("${fmt(it, 1)} NM since previous") }
            }
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pin.auto) AssistChip(onClick = onClick, label = { Text("auto") })
                if (pin.stale) AssistChip(onClick = onClick, label = { Text("old fix") })
            }
        }
        IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in maps") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
    }
}
