package com.tomjxyz.shipinfo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomjxyz.shipinfo.ui.formatDuration
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One line in a chart. [ys] may contain NaN for gaps. */
class ChartSeries(
    val name: String,
    val color: Color,
    val xs: FloatArray,
    val ys: FloatArray,
    val dashed: Boolean = false,
)

private fun niceStep(range: Float, targetTicks: Int = 4): Float {
    if (range <= 0f || range.isNaN()) return 1f
    val raw = range / targetTicks
    val mag = 10f.pow(floor(log10(raw)))
    val norm = raw / mag
    val step = when {
        norm < 1.5f -> 1f
        norm < 3f -> 2f
        norm < 7f -> 5f
        else -> 10f
    }
    return step * mag
}

private fun formatTick(v: Float, step: Float): String {
    val decimals = when {
        step >= 1f -> 0
        step >= 0.1f -> 1
        step >= 0.01f -> 2
        else -> 3
    }
    return String.format(Locale.US, "%.${decimals}f", v)
}

/**
 * Time series line chart drawn on a Canvas.
 * Pinch to zoom / two-finger pan, drag or tap to show a cursor, double tap to reset.
 *
 * @param wrap if set (e.g. 360 for headings) lines are broken where the value wraps around.
 * @param symmetric keep zero centered (roll/pitch).
 * @param markersX x positions to highlight (e.g. roll-watch records).
 * @param bars draw series as bars instead of lines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    unit: String = "",
    fixedRange: ClosedFloatingPointRange<Float>? = null,
    wrap: Float? = null,
    symmetric: Boolean = false,
    includeZero: Boolean = false,
    markersX: List<Float> = emptyList(),
    bars: Boolean = false,
    valueDecimals: Int = 1,
    xLabel: (Float) -> String = { formatDuration((it * 1000).toLong()) },
) {
    val allXs = series.flatMap { s -> s.xs.asList() }
    val dataMinX = allXs.minOrNull() ?: 0f
    val dataMaxX = max(allXs.maxOrNull() ?: 1f, dataMinX + 1f)
    val key = Triple(series.size, dataMinX, allXs.size)

    var viewMin by remember(key) { mutableFloatStateOf(dataMinX) }
    var viewMax by remember(key) { mutableFloatStateOf(dataMaxX) }
    var cursorX by remember(key) { mutableStateOf<Float?>(null) }

    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val leftPad = 44.dp
    val bottomPad = 18.dp

    fun indexAt(s: ChartSeries, x: Float): Int? {
        if (s.xs.isEmpty()) return null
        var lo = 0
        var hi = s.xs.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (s.xs[mid] < x) lo = mid + 1 else hi = mid
        }
        val i = if (lo > 0 && abs(s.xs[lo - 1] - x) < abs(s.xs[lo] - x)) lo - 1 else lo
        return i
    }

    Column(modifier.fillMaxWidth()) {
        // Legend with values at the cursor.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            val cx = cursorX
            if (cx != null) {
                Text(xLabel(cx), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(s.color, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    val value = cx?.let { x -> indexAt(s, x)?.let { s.ys[it] } }
                    val text = if (value != null && !value.isNaN()) {
                        "${s.name}: ${String.format(Locale.US, "%.${valueDecimals}f", value)}$unit"
                    } else {
                        s.name
                    }
                    Text(text, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(key) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                val plotW = size.width - leftPad.toPx()
                                val span = viewMax - viewMin
                                val fx = ((centroid.x - leftPad.toPx()) / plotW).coerceIn(0f, 1f)
                                val anchor = viewMin + fx * span
                                val fullSpan = dataMaxX - dataMinX
                                val newSpan = (span / zoom).coerceIn(fullSpan / 500f, fullSpan)
                                var newMin = anchor - fx * newSpan - pan.x / plotW * newSpan
                                newMin = newMin.coerceIn(dataMinX, max(dataMinX, dataMaxX - newSpan))
                                viewMin = newMin
                                viewMax = newMin + newSpan
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(key) {
                    detectHorizontalDragGestures { change, _ ->
                        val plotW = size.width - leftPad.toPx()
                        val f = ((change.position.x - leftPad.toPx()) / plotW).coerceIn(0f, 1f)
                        cursorX = viewMin + f * (viewMax - viewMin)
                    }
                }
                .pointerInput(key) {
                    detectTapGestures(
                        onTap = { pos ->
                            val plotW = size.width - leftPad.toPx()
                            val f = ((pos.x - leftPad.toPx()) / plotW).coerceIn(0f, 1f)
                            cursorX = viewMin + f * (viewMax - viewMin)
                        },
                        onDoubleTap = {
                            viewMin = dataMinX
                            viewMax = dataMaxX
                            cursorX = null
                        },
                    )
                },
        ) {
            val left = leftPad.toPx()
            val bottom = size.height - bottomPad.toPx()
            val plotW = size.width - left
            val plotH = bottom

            // Y range from visible data.
            var yMin = Float.POSITIVE_INFINITY
            var yMax = Float.NEGATIVE_INFINITY
            if (fixedRange != null) {
                yMin = fixedRange.start
                yMax = fixedRange.endInclusive
            } else {
                series.forEach { s ->
                    for (i in s.xs.indices) {
                        val x = s.xs[i]
                        val y = s.ys[i]
                        if (x < viewMin || x > viewMax || y.isNaN()) continue
                        yMin = min(yMin, y)
                        yMax = max(yMax, y)
                    }
                }
                if (yMin == Float.POSITIVE_INFINITY) {
                    yMin = 0f
                    yMax = 1f
                }
                if (includeZero || bars) {
                    yMin = min(yMin, 0f)
                    yMax = max(yMax, 0f)
                }
                if (symmetric) {
                    val m = max(abs(yMin), abs(yMax))
                    yMin = -m
                    yMax = m
                }
                if (yMax - yMin < 1e-3f) {
                    yMin -= 1f
                    yMax += 1f
                }
                val pad = (yMax - yMin) * 0.08f
                if (!(includeZero || bars) || yMin < 0f) yMin -= pad
                yMax += pad
            }
            val step = niceStep(yMax - yMin)
            if (fixedRange == null) {
                yMin = floor(yMin / step) * step
                yMax = ceil(yMax / step) * step
            }

            fun px(x: Float) = left + (x - viewMin) / (viewMax - viewMin) * plotW
            fun py(y: Float) = plotH - (y - yMin) / (yMax - yMin) * plotH

            // Grid + y labels.
            var t = ceil(yMin / step) * step
            while (t <= yMax + step * 0.01f) {
                val y = py(t)
                drawLine(
                    color = if (abs(t) < step * 0.01f) colors.outline else colors.outlineVariant,
                    start = Offset(left, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                val label = textMeasurer.measure(formatTick(t, step), labelStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 6f, y - label.size.height / 2f))
                t += step
            }

            // X labels.
            val xTicks = 4
            for (i in 0..xTicks) {
                val xv = viewMin + (viewMax - viewMin) * i / xTicks
                val label = textMeasurer.measure(xLabel(xv), labelStyle)
                val lx = (px(xv) - label.size.width / 2f).coerceIn(left, size.width - label.size.width)
                drawText(label, topLeft = Offset(lx, bottom + 2f))
            }

            // Record markers.
            markersX.forEach { mx ->
                if (mx in viewMin..viewMax) {
                    drawLine(
                        color = Color(0x55FF5252),
                        start = Offset(px(mx), 0f),
                        end = Offset(px(mx), plotH),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
            }

            // Data.
            series.forEachIndexed { si, s ->
                if (bars) {
                    val n = series.size
                    val visible = s.xs.count { it in viewMin..viewMax }.coerceAtLeast(1)
                    val slot = plotW / visible
                    val barW = (slot * 0.7f / n).coerceAtLeast(1f)
                    for (i in s.xs.indices) {
                        val x = s.xs[i]
                        val y = s.ys[i]
                        if (x < viewMin || x > viewMax || y.isNaN()) continue
                        val cx = px(x) + (si - (n - 1) / 2f) * barW
                        val top = py(max(y, 0f))
                        val base = py(min(y, 0f))
                        drawRect(s.color, topLeft = Offset(cx - barW / 2, top), size = androidx.compose.ui.geometry.Size(barW, base - top))
                    }
                    return@forEachIndexed
                }
                val path = Path()
                var penDown = false
                var prevY = Float.NaN
                val visibleCount = s.xs.count { it in viewMin..viewMax }
                val stride = max(1, visibleCount / max(1, (plotW * 1.5f).toInt()))
                var i = 0
                var lastX = Float.NaN
                while (i < s.xs.size) {
                    val x = s.xs[i]
                    val y = s.ys[i]
                    if (x >= viewMin - 1 && x <= viewMax + 1) {
                        if (y.isNaN()) {
                            penDown = false
                        } else {
                            val jump = wrap != null && !prevY.isNaN() && abs(y - prevY) > wrap / 2
                            // Break the line over long gaps in time as well.
                            val gap = !lastX.isNaN() && x - lastX > 60f * stride
                            if (!penDown || jump || gap) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y))
                            penDown = true
                            prevY = y
                            lastX = x
                        }
                    }
                    i += stride
                }
                clipRect(left, 0f, size.width, plotH) {
                    drawPath(
                        path,
                        s.color,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                        ),
                    )
                }
            }

            // Cursor.
            cursorX?.let { cx ->
                if (cx in viewMin..viewMax) {
                    drawLine(colors.onSurface.copy(alpha = 0.6f), Offset(px(cx), 0f), Offset(px(cx), plotH), 1.5f)
                    series.forEach { s ->
                        val idx = indexAt(s, cx) ?: return@forEach
                        val y = s.ys[idx]
                        if (!y.isNaN()) drawCircle(s.color, 4.dp.toPx(), Offset(px(s.xs[idx]), py(y)))
                    }
                }
            }
        }
    }
}
