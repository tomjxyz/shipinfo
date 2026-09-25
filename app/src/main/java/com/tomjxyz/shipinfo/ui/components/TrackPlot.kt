package com.tomjxyz.shipinfo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomjxyz.shipinfo.ui.theme.ChartColors
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

data class GeoPoint(val lat: Double, val lon: Double)

/**
 * Plots a GPS track (or a set of pins) on a simple equirectangular projection with a
 * nautical-mile scale bar. Works offline; no map tiles.
 */
@Composable
fun TrackPlot(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    connect: Boolean = true,
    drawDots: Boolean = false,
    highlight: GeoPoint? = null,
) {
    val colors = MaterialTheme.colorScheme
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    Canvas(modifier.fillMaxWidth().height(height)) {
        drawRect(colors.surfaceVariant.copy(alpha = 0.5f))
        if (points.isEmpty()) {
            val l = tm.measure("No position data", labelStyle)
            drawText(l, topLeft = Offset((size.width - l.size.width) / 2, (size.height - l.size.height) / 2))
            return@Canvas
        }
        val meanLat = points.map { it.lat }.average()
        val k = cos(Math.toRadians(meanLat))
        val xs = points.map { it.lon * k }
        val ys = points.map { it.lat }
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        val pad = 24.dp.toPx()
        val spanDeg = max(max(maxX - minX, maxY - minY), 0.002) // at least ~0.1 NM
        val scale = minOf((size.width - 2 * pad), (size.height - 2 * pad)) / spanDeg
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        fun toScreen(p: GeoPoint) = Offset(
            (size.width / 2 + (p.lon * k - cx) * scale).toFloat(),
            (size.height / 2 - (p.lat - cy) * scale).toFloat(),
        )

        if (connect && points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val o = toScreen(p)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, colors.primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        if (drawDots) {
            points.forEach { drawCircle(colors.secondary, 4.dp.toPx(), toScreen(it)) }
        }
        drawCircle(ChartColors.starboard, 6.dp.toPx(), toScreen(points.first()))
        drawCircle(ChartColors.port, 6.dp.toPx(), toScreen(points.last()))
        highlight?.let {
            drawCircle(colors.onSurface, 9.dp.toPx(), toScreen(it), style = Stroke(2.dp.toPx()))
        }

        // Scale bar: 1 degree of latitude = 60 NM.
        val pxPerNm = scale / 60.0
        val targetNm = (size.width * 0.25) / pxPerNm
        val mag = 10.0.pow(floor(log10(targetNm)))
        val nice = listOf(1.0, 2.0, 5.0, 10.0).map { it * mag }.last { it <= targetNm * 1.01 }
        val barPx = (nice * pxPerNm).toFloat()
        val y = size.height - 10.dp.toPx()
        val x0 = 10.dp.toPx()
        drawLine(colors.onSurface, Offset(x0, y), Offset(x0 + barPx, y), 3f)
        drawLine(colors.onSurface, Offset(x0, y - 6f), Offset(x0, y + 1f), 3f)
        drawLine(colors.onSurface, Offset(x0 + barPx, y - 6f), Offset(x0 + barPx, y + 1f), 3f)
        val label = if (nice >= 1) String.format(Locale.US, "%.0f NM", nice) else String.format(Locale.US, "%.0f m", nice * 1852)
        val l = tm.measure(label, labelStyle)
        drawText(l, topLeft = Offset(x0 + barPx + 6f, y - l.size.height / 2f - 2f))
        val n = tm.measure("N ↑", labelStyle)
        drawText(n, topLeft = Offset(size.width - n.size.width - 8f, 6f))
    }
}
