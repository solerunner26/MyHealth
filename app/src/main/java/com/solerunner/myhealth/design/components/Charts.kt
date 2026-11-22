package com.solerunner.myhealth.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.core.util.Dates
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.SeriesPoint
import java.util.Locale

/**
 * Line chart with a reference band, drawn with the single accent hue.
 *
 * Tap anywhere to select the nearest result; the selected point gets a ring and its exact date and
 * value are printed underneath. The same numbers are exposed through semantics, so the chart is
 * readable by a screen reader.
 */
@Composable
fun LineChart(
    points: List<SeriesPoint>,
    referenceLow: Double?,
    referenceHigh: Double?,
    modifier: Modifier = Modifier,
    unit: String? = null,
    decimals: Int = 2,
    contentDescription: String? = null,
) {
    if (points.isEmpty()) {
        Text(
            stringResource(R.string.chart_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 16.dp),
        )
        return
    }

    val colors = MyHealthTokens.chart
    val locale = Locale.getDefault()
    val values = points.map { it.value }
    val low = minOf(values.min(), referenceLow ?: values.min())
    val high = maxOf(values.max(), referenceHigh ?: values.max())
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .semantics { contentDescription?.let { this.contentDescription = it } }
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val step = if (points.size > 1) size.width.toFloat() / (points.size - 1) else size.width.toFloat()
                        selected = (offset.x / step).toInt().coerceIn(0, points.lastIndex)
                    }
                },
        ) {
            val chartHeight = size.height
            val chartWidth = size.width
            fun x(index: Int): Float =
                if (points.size == 1) chartWidth / 2f else chartWidth * index / (points.size - 1)
            fun y(value: Double): Float =
                (chartHeight - ((value - low) / span * chartHeight * 0.88f)).toFloat() + chartHeight * 0.06f

            if (referenceLow != null && referenceHigh != null) {
                val top = y(referenceHigh)
                val bottom = y(referenceLow)
                drawRoundRect(
                    color = colors.referenceBand,
                    topLeft = Offset(0f, top),
                    size = Size(chartWidth, (bottom - top).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
            repeat(3) { line ->
                val gy = chartHeight * line / 2f
                drawLine(color = colors.grid, start = Offset(0f, gy), end = Offset(chartWidth, gy), strokeWidth = 1f)
            }
            val path = Path()
            points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(x(index), y(point.value)) else path.lineTo(x(index), y(point.value))
            }
            drawPath(path, color = colors.line, style = Stroke(width = 3f))
            points.forEachIndexed { index, point ->
                drawCircle(
                    color = if (index == selected) colors.pointSelected else colors.point,
                    radius = if (index == selected) 5.5f else 3f,
                    center = Offset(x(index), y(point.value)),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Dates.format(points.first().date, locale, "MMM yy"), style = MaterialTheme.typography.labelSmall, color = colors.axisLabel)
            Text(Dates.format(points.last().date, locale, "MMM yy"), style = MaterialTheme.typography.labelSmall, color = colors.axisLabel)
        }
        val point = points.getOrNull(selected)
        if (point != null) {
            Text(
                "${Dates.format(point.date, locale)} — ${formatValue(point.value, decimals)} ${unit ?: ""}".trim(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun formatValue(value: Double, decimals: Int): String =
    String.format(Locale.getDefault(), if (decimals <= 0) "%.0f" else "%.${decimals}f", value)
