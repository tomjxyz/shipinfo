package com.tomjxyz.shipinfo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.tomjxyz.shipinfo.ui.theme.ChartColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun polar(c: Offset, r: Float, deg: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
}

/**
 * North-up compass dial. The ship silhouette points along [primaryDeg] (GPS course over ground,
 * or compass when there is no course); the orange needle shows the compass heading.
 */
@Composable
fun CompassRose(primaryDeg: Double?, compassDeg: Double?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tm = rememberTextMeasurer()
    val cardinalStyle = TextStyle(color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val northStyle = cardinalStyle.copy(color = ChartColors.port)
    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        val c = center
        val r = min(size.width, size.height) / 2f * 0.95f
        drawCircle(colors.surfaceVariant, r, c)
        drawCircle(colors.outline, r, c, style = Stroke(2f))
        for (d in 0 until 360 step 5) {
            // Screen angle 0 = east; compass 0 = north -> subtract 90.
            val a = d - 90f
            val len = when {
                d % 90 == 0 -> r * 0.14f
                d % 30 == 0 -> r * 0.10f
                d % 10 == 0 -> r * 0.06f
                else -> r * 0.03f
            }
            drawLine(colors.onSurfaceVariant, polar(c, r, a), polar(c, r - len, a), if (d % 30 == 0) 3f else 1.5f)
        }
        listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, d) ->
            val layout = tm.measure(label, if (label == "N") northStyle else cardinalStyle)
            val p = polar(c, r * 0.72f, d - 90f)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        compassDeg?.let { h ->
            rotate(h.toFloat(), c) {
                val tip = Offset(c.x, c.y - r * 0.85f)
                val path = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(c.x - r * 0.04f, c.y)
                    lineTo(c.x + r * 0.04f, c.y)
                    close()
                }
                drawPath(path, ChartColors.compass.copy(alpha = 0.9f))
            }
        }
        (primaryDeg ?: compassDeg)?.let { h ->
            rotate(h.toFloat(), c) { drawShipTopView(c, r * 0.5f, colors.primary) }
        }
        drawCircle(colors.onSurface, r * 0.03f, c)
    }
}

/** Ship seen from above, bow pointing up, centered at [c] with length [len]. */
private fun DrawScope.drawShipTopView(c: Offset, len: Float, color: Color) {
    val w = len * 0.28f
    val path = Path().apply {
        moveTo(c.x, c.y - len / 2)
        cubicTo(c.x + w * 0.6f, c.y - len * 0.3f, c.x + w / 2, c.y - len * 0.1f, c.x + w / 2, c.y)
        lineTo(c.x + w / 2, c.y + len * 0.42f)
        lineTo(c.x + w * 0.35f, c.y + len / 2)
        lineTo(c.x - w * 0.35f, c.y + len / 2)
        lineTo(c.x - w / 2, c.y + len * 0.42f)
        lineTo(c.x - w / 2, c.y)
        cubicTo(c.x - w / 2, c.y - len * 0.1f, c.x - w * 0.6f, c.y - len * 0.3f, c.x, c.y - len / 2)
        close()
    }
    drawPath(path, color.copy(alpha = 0.85f))
    drawRect(Color.White.copy(alpha = 0.7f), Offset(c.x - w * 0.3f, c.y + len * 0.18f), Size(w * 0.6f, len * 0.14f))
}

/**
 * Clinometer seen from astern: the hull and mast lean with the roll (starboard down = lean right),
 * the fixed scale on top reads the angle. Red/green marks show the maximum roll to port/starboard.
 */
@Composable
fun Inclinometer(
    rollDeg: Double?,
    modifier: Modifier = Modifier,
    maxPortDeg: Double? = null,
    maxStbdDeg: Double? = null,
    rangeDeg: Float = 40f,
) {
    val colors = MaterialTheme.colorScheme
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val sideStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Canvas(modifier.fillMaxWidth().aspectRatio(1.6f)) {
        val r = min(size.width / 2f, size.height * 0.85f) * 0.92f
        val c = Offset(size.width / 2f, size.height * 0.92f)
        // Sea.
        drawRect(
            Color(0xFF1565C0).copy(alpha = 0.25f),
            topLeft = Offset(0f, c.y - r * 0.12f),
            size = Size(size.width, size.height - (c.y - r * 0.12f)),
        )
        // Scale arc.
        drawArc(
            colors.outline,
            startAngle = 270f - rangeDeg,
            sweepAngle = 2 * rangeDeg,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(2 * r, 2 * r),
            style = Stroke(2f),
        )
        var d = -rangeDeg.toInt()
        while (d <= rangeDeg) {
            val a = 270f + d
            val len = if (d % 10 == 0) r * 0.08f else r * 0.04f
            drawLine(colors.onSurfaceVariant, polar(c, r, a), polar(c, r - len, a), if (d % 10 == 0) 3f else 1.5f)
            if (d % 10 == 0 && d != 0) {
                val layout = tm.measure("${kotlin.math.abs(d)}", labelStyle)
                val p = polar(c, r + 12f, a)
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
            d += 5
        }
        val port = tm.measure("P", sideStyle.copy(color = ChartColors.port))
        val stbd = tm.measure("S", sideStyle.copy(color = ChartColors.starboard))
        val portPos = polar(c, r * 0.8f, 270f - rangeDeg - 4f)
        val stbdPos = polar(c, r * 0.8f, 270f + rangeDeg + 4f)
        drawText(port, topLeft = Offset(portPos.x - port.size.width / 2f, portPos.y))
        drawText(stbd, topLeft = Offset(stbdPos.x - stbd.size.width / 2f, stbdPos.y))

        fun marker(deg: Double, color: Color) {
            val a = 270f + deg.toFloat().coerceIn(-rangeDeg, rangeDeg)
            val tip = polar(c, r - 2f, a)
            val b1 = polar(c, r + 14f, a - 2.5f)
            val b2 = polar(c, r + 14f, a + 2.5f)
            drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }, color)
        }
        maxPortDeg?.let { if (it > 0) marker(-it, ChartColors.port) }
        maxStbdDeg?.let { if (it > 0) marker(it, ChartColors.starboard) }

        val roll = (rollDeg ?: 0.0).toFloat().coerceIn(-rangeDeg - 5, rangeDeg + 5)
        rotate(roll, c) {
            // Mast / pointer.
            drawLine(
                if (rollDeg == null) colors.outline else colors.secondary,
                Offset(c.x, c.y - r * 0.2f),
                Offset(c.x, c.y - r * 0.93f),
                4f,
            )
            // Hull cross-section.
            val w = r * 0.9f
            val h = r * 0.28f
            val deck = c.y - r * 0.18f
            val hull = Path().apply {
                moveTo(c.x - w / 2, deck)
                lineTo(c.x + w / 2, deck)
                lineTo(c.x + w * 0.42f, deck + h * 0.75f)
                quadraticBezierTo(c.x + w * 0.38f, deck + h, c.x + w * 0.25f, deck + h)
                lineTo(c.x - w * 0.25f, deck + h)
                quadraticBezierTo(c.x - w * 0.38f, deck + h, c.x - w * 0.42f, deck + h * 0.75f)
                close()
            }
            drawPath(hull, colors.primary)
            drawRect(
                colors.onSurface.copy(alpha = 0.85f),
                topLeft = Offset(c.x - w * 0.18f, deck - r * 0.14f),
                size = Size(w * 0.36f, r * 0.14f),
            )
        }
        // Waterline.
        drawLine(Color(0xFF42A5F5), Offset(0f, c.y - r * 0.12f), Offset(size.width, c.y - r * 0.12f), 2f)
    }
}
