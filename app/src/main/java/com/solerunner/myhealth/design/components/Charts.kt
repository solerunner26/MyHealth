package com.solerunner.myhealth.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
 * Line chart with a reference band. The Rare treatment: a thin line, dashed grid, no area fill, a
 * small ring on the selected point and a faint crosshair. Tap to select; the exact date and value are
 * printed underneath and exposed through semantics.
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
    val locale = LocalConfiguration.current.locales[0]
    val values = points.map { it.value }
    val low = minOf(values.min(), referenceLow ?: values.min())
    val high = maxOf(values.max(), referenceHigh ?: values.max())
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 6f), 0f)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
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
            fun x(index: Int): Float = if (points.size == 1) chartWidth / 2f else chartWidth * index / (points.size - 1)
            fun y(value: Double): Float =
                (chartHeight - ((value - low) / span * chartHeight * 0.74f)).toFloat() + chartHeight * 0.16f

            if (referenceLow != null && referenceHigh != null) {
                val top = y(referenceHigh)
                val bottom = y(referenceLow)
                drawRoundRect(
                    color = colors.referenceBand,
                    topLeft = Offset(0f, top),
                    size = Size(chartWidth, (bottom - top).coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(8f, 8f),
                )
            } else {
                // One-sided ranges still deserve a visible threshold line.
                val bound = referenceHigh ?: referenceLow
                if (bound != null) {
                    val gy = y(bound)
                    drawLine(
                        color = colors.lineSoft.copy(alpha = 0.55f),
                        start = Offset(0f, gy),
                        end = Offset(chartWidth, gy),
                        strokeWidth = 1.2f,
                        pathEffect = dash,
                    )
                }
            }
            repeat(3) { line ->
                val gy = chartHeight * line / 2f
                drawLine(
                    color = colors.grid,
                    start = Offset(0f, gy),
                    end = Offset(chartWidth, gy),
                    strokeWidth = 1f,
                    pathEffect = dash,
                )
            }
            points.getOrNull(selected)?.let { point ->
                drawLine(
                    color = colors.point.copy(alpha = 0.18f),
                    start = Offset(x(selected), 0f),
                    end = Offset(x(selected), chartHeight),
                    strokeWidth = 1f,
                )
                drawCircle(
                    color = colors.line.copy(alpha = 0.12f),
                    radius = 12f,
                    center = Offset(x(selected), y(point.value)),
                )
            }
            val path = Path()
            points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(x(index), y(point.value)) else path.lineTo(x(index), y(point.value))
            }
            drawPath(path, color = colors.line, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            points.forEachIndexed { index, point ->
                if (index != selected) {
                    drawCircle(color = colors.point, radius = 2.6f, center = Offset(x(index), y(point.value)))
                }
            }
            points.getOrNull(selected)?.let { point ->
                drawCircle(color = colors.pointSelected, radius = 4.5f, center = Offset(x(selected), y(point.value)))
                drawCircle(color = colors.referenceBand, radius = 2f, center = Offset(x(selected), y(point.value)))
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Dates.format(points.first().date, locale, "MMM yy"), style = MaterialTheme.typography.labelSmall, color = colors.axisLabel)
            Text(Dates.format(points.last().date, locale, "MMM yy"), style = MaterialTheme.typography.labelSmall, color = colors.axisLabel)
        }
        val point = points.getOrNull(selected)
        if (point != null) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    Dates.format(point.date, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatValue(point.value, decimals)} ${unit.orEmpty()}".trim(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun formatValue(value: Double, decimals: Int): String =
    String.format(Locale.getDefault(), if (decimals <= 0) "%.0f" else "%.${decimals}f", value)
