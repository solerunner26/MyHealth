package app.myhealth.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.myhealth.core.designsystem.theme.MyHealthTheme

@Immutable
data class ChartPoint(val x: Double, val y: Double, val outOfBand: Boolean = false)

@Immutable
data class ChartSeries(val points: List<ChartPoint>, val dashed: Boolean = false)

/**
 * Reference-band trend chart. Band = soft accent fill with dashed edges; series = accent line.
 * In-band points are small filled dots, out-of-band points are larger hollow rings with a heavy stroke,
 * so status survives greyscale printing and colour-vision differences. A second series (e.g. diastolic)
 * is drawn dashed with square markers.
 */
@Composable
fun MyHealthBandChart(
    series: List<ChartSeries>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    bandLow: Double? = null,
    bandHigh: Double? = null,
    yLabelTop: String = "",
    yLabelBottom: String = "",
    xLabelStart: String = "",
    xLabelEnd: String = "",
    height: androidx.compose.ui.unit.Dp = 200.dp,
) {
    val c = MyHealthTheme.colors
    val all = series.flatMap { it.points }
    Column(modifier.fillMaxWidth().semantics { this.contentDescription = contentDescription }) {
        Row {
            Text(yLabelTop, style = MyHealthTheme.typography.caption, color = c.textTertiary)
        }
        Box(Modifier.fillMaxWidth().height(height)) {
            Canvas(Modifier.fillMaxWidth().height(height)) {
                if (all.isEmpty()) return@Canvas
                val ys = all.map { it.y } + listOfNotNull(bandLow, bandHigh)
                var minY = ys.min(); var maxY = ys.max()
                if (maxY - minY < 1e-9) { minY -= 1.0; maxY += 1.0 }
                val pad = (maxY - minY) * 0.12
                minY -= pad; maxY += pad
                val minX = all.minOf { it.x }; var maxX = all.maxOf { it.x }
                if (maxX - minX < 1e-9) maxX = minX + 1.0
                val inset = 12.dp.toPx()
                val w = size.width - inset * 2; val h = size.height - inset * 2
                fun px(x: Double) = inset + ((x - minX) / (maxX - minX)).toFloat() * w
                fun py(y: Double) = inset + (1f - ((y - minY) / (maxY - minY)).toFloat()) * h
                val single = all.map { it.x }.distinct().size == 1
                fun pxs(x: Double) = if (single) inset + w / 2 else px(x)

                // gridlines
                for (i in 0..3) {
                    val gy = inset + h * i / 3f
                    drawLine(c.outline, Offset(inset, gy), Offset(inset + w, gy), strokeWidth = 1.dp.toPx())
                }
                // reference band
                if (bandLow != null || bandHigh != null) {
                    val top = py(bandHigh ?: maxY); val bottom = py(bandLow ?: minY)
                    drawRoundRect(c.accentSoft, topLeft = Offset(inset, top), size = Size(w, bottom - top), cornerRadius = CornerRadius(4.dp.toPx()))
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                    if (bandHigh != null) drawLine(c.accent, Offset(inset, top), Offset(inset + w, top), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                    if (bandLow != null) drawLine(c.accent, Offset(inset, bottom), Offset(inset + w, bottom), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                }
                series.forEach { s ->
                    val pts = s.points.sortedBy { it.x }
                    if (pts.size > 1) {
                        val path = Path()
                        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(pxs(p.x), py(p.y)) else path.lineTo(pxs(p.x), py(p.y)) }
                        drawPath(
                            path, c.accent,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round,
                                pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())) else null),
                        )
                    }
                    pts.forEach { p ->
                        val o = Offset(pxs(p.x), py(p.y))
                        if (s.dashed) {
                            val r = if (p.outOfBand) 6.dp.toPx() else 4.dp.toPx()
                            if (p.outOfBand) {
                                drawRect(c.surface, Offset(o.x - r, o.y - r), Size(r * 2, r * 2))
                                drawRect(c.accent, Offset(o.x - r, o.y - r), Size(r * 2, r * 2), style = Stroke(3.dp.toPx()))
                            } else drawRect(c.accent, Offset(o.x - r, o.y - r), Size(r * 2, r * 2))
                        } else if (p.outOfBand) {
                            drawCircle(c.surface, 7.dp.toPx(), o)
                            drawCircle(c.accent, 7.dp.toPx(), o, style = Stroke(3.dp.toPx()))
                        } else {
                            drawCircle(c.accent, 4.5.dp.toPx(), o)
                        }
                    }
                }
            }
        }
        Text(yLabelBottom, style = MyHealthTheme.typography.caption, color = c.textTertiary)
        Row(Modifier.fillMaxWidth()) {
            Text(xLabelStart, style = MyHealthTheme.typography.caption, color = c.textTertiary, modifier = Modifier.weight(1f))
            Text(xLabelEnd, style = MyHealthTheme.typography.caption, color = c.textTertiary)
        }
    }
}

/** Legend explaining non-colour encodings of the chart. */
@Composable
fun MyHealthChartLegend(inRangeLabel: String, outOfRangeLabel: String, bandLabel: String?, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) { drawCircle(c.accent, 4.5.dp.toPx()) }
        Spacer(Modifier.width(4.dp)); Text(inRangeLabel, style = MyHealthTheme.typography.caption, color = c.textSecondary)
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.size(14.dp)) { drawCircle(c.accent, 5.5.dp.toPx(), style = Stroke(2.5.dp.toPx())) }
        Spacer(Modifier.width(4.dp)); Text(outOfRangeLabel, style = MyHealthTheme.typography.caption, color = c.textSecondary)
        if (bandLabel != null) {
            Spacer(Modifier.width(12.dp))
            Canvas(Modifier.size(width = 16.dp, height = 10.dp)) { drawRect(c.accentSoft); drawRect(c.accent, style = Stroke(1.dp.toPx())) }
            Spacer(Modifier.width(4.dp)); Text(bandLabel, style = MyHealthTheme.typography.caption, color = c.textSecondary)
        }
    }
}

/** Health score ring: arc length encodes the score; null = insufficient data (dashed empty ring). */
@Composable
fun MyHealthScoreRing(score: Int?, centerLabel: String, caption: String, contentDescription: String, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    Box(modifier.size(132.dp).semantics { this.contentDescription = contentDescription }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(132.dp)) {
            val stroke = 12.dp.toPx()
            val d = size.minDimension - stroke
            val tl = Offset(stroke / 2, stroke / 2)
            if (score == null) {
                drawArc(c.outlineStrong, 0f, 360f, false, tl, Size(d, d),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
            } else {
                drawArc(c.surfaceSunken, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                drawArc(c.accent, -90f, 360f * score.coerceIn(0, 100) / 100f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerLabel, style = MyHealthTheme.typography.metric, color = c.textPrimary)
            Text(caption, style = MyHealthTheme.typography.caption, color = c.textSecondary)
        }
    }
}
