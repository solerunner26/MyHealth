package com.solerunner.myhealth.design.rare

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.theme.MyHealthTokens

/** Toast style snackbar: dark inverse surface, 12dp radius, one optional action. */
@Composable
fun RareSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.inverseSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val label = data.visuals.actionLabel
                if (label != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { data.performAction() }) {
                        Text(label, color = MaterialTheme.colorScheme.inversePrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Two-pixel progress line for loading states that do not know their length yet. */
@Composable
fun RareLinearProgress(progress: Float?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth().height(2.dp),
            color = colors.primary,
            trackColor = colors.surfaceContainerHigh,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth().height(2.dp),
            color = colors.primary,
            trackColor = colors.surfaceContainerHigh,
        )
    }
}

/** Hairline scroll progress pinned above content, mirroring the Rare scroll-progress component. */
@Composable
fun RareScrollProgress(scrollState: androidx.compose.foundation.ScrollState, modifier: Modifier = Modifier) {
    val max = scrollState.maxValue
    val target = if (max <= 0) 0f else scrollState.value.toFloat() / max
    val fraction by animateFloatAsState(target, tween(MyHealthTokens.motion.fast), label = "scroll-progress")
    Box(modifier = modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}

/** Shimmer block for skeletons; the only looping animation in the app. */
@Composable
fun RareSkeleton(modifier: Modifier = Modifier, height: Int = 16) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "skeleton-shift",
    )
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(height.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.surfaceContainerHigh,
                        colors.surfaceContainerHighest,
                        colors.surfaceContainerHigh,
                    ),
                    start = androidx.compose.ui.geometry.Offset(shift * 600f - 300f, 0f),
                    end = androidx.compose.ui.geometry.Offset(shift * 600f, 0f),
                ),
            ),
    )
}

@Composable
fun RareSpinner(size: Int = 18, modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(size.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Ring of the score / hero metric, drawn as a thin arc with a quiet track. */
@Composable
fun RareProgressRing(progress: Float, modifier: Modifier = Modifier, size: Int = 56, stroke: Int = 4) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val accentColor = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(size.dp)) {
            val strokePx = stroke.dp.toPx()
            val inset = strokePx / 2
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(this.size.width - strokePx, this.size.height - strokePx),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx),
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(this.size.width - strokePx, this.size.height - strokePx),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}
