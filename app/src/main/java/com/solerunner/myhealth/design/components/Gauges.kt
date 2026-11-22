package com.solerunner.myhealth.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.theme.NumericTextStyle

/** Health-score ring: one arc, one number, one band label. Empty when there is nothing to score. */
@Composable
fun RingGauge(
    score: Int?,
    bandLabel: String,
    modifier: Modifier = Modifier,
    size: Int = 132,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size.dp)
            .semantics { contentDescription = if (score == null) bandLabel else "$score, $bandLabel" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (score != null) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * (score.coerceIn(0, 100) / 100f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - stroke, this.size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                score?.toString() ?: "—",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = NumericTextStyle.fontFamily),
            )
            Text(
                bandLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small trend line without axes — used inside tiles where space is tight. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, height: Int = 36) {
    if (values.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxWidth().height(height.dp)) {
        val low = values.min()
        val span = (values.max() - low).takeIf { it > 0.0 } ?: 1.0
        val step = size.width / (values.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { index, value ->
            val px = step * index
            val py = size.height - (((value - low) / span).toFloat() * size.height * 0.85f) - size.height * 0.075f
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

/** Simple bar chart (sleep per night, steps per day). Bars scale to the tallest value. */
@Composable
fun BarChart(values: List<Float>, modifier: Modifier = Modifier, height: Int = 96) {
    if (values.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    Canvas(modifier = modifier.fillMaxWidth().height(height.dp)) {
        val max = values.max().takeIf { it > 0f } ?: 1f
        val slot = size.width / values.size
        val barWidth = (slot * 0.6f).coerceAtLeast(2f)
        values.forEachIndexed { index, value ->
            val barHeight = (value / max) * size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(slot * index + (slot - barWidth) / 2f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
