package com.tomjxyz.shipinfo.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.tomjxyz.shipinfo.app
import com.tomjxyz.shipinfo.core.CsvWriter
import com.tomjxyz.shipinfo.core.SampleColumns
import com.tomjxyz.shipinfo.data.SessionEntity
import com.tomjxyz.shipinfo.data.SessionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes recordings to CSV and shares them. */
object CsvExporter {
    private fun stamp(ms: Long) = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(ms))

    fun fileName(session: SessionEntity): String {
        val mode = if (session.type == SessionType.ROLL_WATCH) "rollwatch" else "live"
        return "shipinfo_${mode}_${stamp(session.startMs)}.csv"
    }

    fun pinsFileName() = "shipinfo_pins_${stamp(System.currentTimeMillis())}.csv"

    suspend fun writeSession(context: Context, session: SessionEntity, out: Writer) = withContext(Dispatchers.IO) {
        val db = context.app.db
        val csv = CsvWriter(out)
        if (session.type == SessionType.ROLL_WATCH) {
            csv.row(
                "window_start_utc", "window_end_utc", "is_record", "record_reason",
                "max_roll_starboard_deg", "max_roll_port_deg", "max_pitch_bow_up_deg", "max_pitch_bow_down_deg",
                "max_lateral_g", "max_vertical_g", "roll_period_s", "samples", "latitude", "longitude",
            )
            for (w in db.rollWindows().forSession(session.id)) {
                csv.row(
                    CsvWriter.isoUtc(w.startMs), CsvWriter.isoUtc(w.endMs), w.isRecord, w.reasons,
                    w.maxRollStbdDeg, w.maxRollPortDeg, w.maxPitchUpDeg, w.maxPitchDownDeg,
                    w.maxLateralG, w.maxVerticalG, w.rollPeriodS, w.sampleCount, w.lat, w.lon,
                )
            }
        } else {
            val channels = SampleColumns.decode(session.channels)
            csv.row(SampleColumns.header(channels))
            for (s in db.samples().forSession(session.id)) {
                csv.row(
                    SampleColumns.row(
                        channels, s.timeMs, session.startMs,
                        s.lat, s.lon, s.accuracyM, s.speedKn, s.cogDeg, s.compassDeg,
                        s.rollDeg, s.pitchDeg, s.lateralG, s.verticalG,
                    ),
                )
            }
        }
        out.flush()
    }

    suspend fun writePins(context: Context, out: Writer) = withContext(Dispatchers.IO) {
        val csv = CsvWriter(out)
        csv.row(
            "timestamp_utc", "fix_time_utc", "latitude", "longitude", "gps_accuracy_m",
            "speed_kn", "course_over_ground_deg", "automatic", "stale_fix", "note",
        )
        for (p in context.app.db.pins().allChronological()) {
            csv.row(
                CsvWriter.isoUtc(p.timeMs), CsvWriter.isoUtc(p.fixTimeMs), p.lat, p.lon, p.accuracyM,
                p.speedKn, p.cogDeg, p.auto, p.stale, p.note,
            )
        }
        out.flush()
    }

    private fun exportDir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    private suspend fun toFile(context: Context, name: String, write: suspend (Writer) -> Unit): File =
        withContext(Dispatchers.IO) {
            val f = File(exportDir(context), name)
            f.writer(Charsets.UTF_8).use { write(it) }
            f
        }

    suspend fun sessionFile(context: Context, session: SessionEntity) =
        toFile(context, fileName(session)) { writeSession(context, session, it) }

    suspend fun pinsFile(context: Context) = toFile(context, pinsFileName()) { writePins(context, it) }

    /** Zip with one CSV per session plus the position pins. */
    suspend fun allZip(context: Context): File = withContext(Dispatchers.IO) {
        val f = File(exportDir(context), "shipinfo_all_${stamp(System.currentTimeMillis())}.zip")
        ZipOutputStream(f.outputStream()).use { zip ->
            val writer = OutputStreamWriter(zip, Charsets.UTF_8)
            val used = mutableSetOf<String>()
            for (s in context.app.db.sessions().all()) {
                var name = fileName(s)
                if (!used.add(name)) name = name.removeSuffix(".csv") + "_${s.id}.csv"
                zip.putNextEntry(ZipEntry(name))
                writeSession(context, s, writer)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(pinsFileName()))
            writePins(context, writer)
            zip.closeEntry()
        }
        f
    }

    /** Writes into a user-chosen document (Storage Access Framework). */
    suspend fun writeToUri(context: Context, uri: Uri, write: suspend (Writer) -> Unit) = withContext(Dispatchers.IO) {
        val stream: OutputStream = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot open $uri")
        stream.writer(Charsets.UTF_8).use { write(it) }
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = if (file.name.endsWith(".zip")) "application/zip" else "text/csv"
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Export ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
