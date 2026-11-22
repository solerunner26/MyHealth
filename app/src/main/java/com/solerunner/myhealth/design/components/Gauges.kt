package com.solerunner.myhealth.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.rare.RareProgressRing
import com.solerunner.myhealth.design.theme.NumericTextStyle

/** Score ring: thin arc, large mono number, band label. */
@Composable
fun RingGauge(
    score: Int?,
    bandLabel: String,
    modifier: Modifier = Modifier,
    size: Int = 120,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .semantics { contentDescription = if (score == null) bandLabel else "$score, $bandLabel" },
        contentAlignment = Alignment.Center,
    ) {
        RareProgressRing(progress = (score ?: 0) / 100f, size = size, stroke = 5)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                score?.toString() ?: "—",
                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = NumericTextStyle.fontFamily),
            )
            Text(
                bandLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small trend line without axes, used where space is tight. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, height: Int = 32) {
    if (values.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxWidth().height(height.dp)) {
        val low = values.min()
        val span = (values.max() - low).takeIf { it > 0.0 } ?: 1.0
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val px = step * index
            val py = size.height - (((value - low) / span).toFloat() * size.height * 0.82f) - size.height * 0.09f
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color = color, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }
}

/** Simple bar chart (sleep per night, steps per day). */
@Composable
fun BarChart(values: List<Float>, modifier: Modifier = Modifier, height: Int = 88) {
    if (values.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    Canvas(modifier = modifier.fillMaxWidth().height(height.dp)) {
        val max = values.max().takeIf { it > 0f } ?: 1f
        val slot = size.width / values.size
        val barWidth = (slot * 0.55f).coerceAtLeast(2f)
        values.forEachIndexed { index, value ->
            val barHeight = (value / max) * size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(slot * index + (slot - barWidth) / 2f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
