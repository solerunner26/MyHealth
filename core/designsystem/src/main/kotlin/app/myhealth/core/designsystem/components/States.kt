package app.myhealth.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.myhealth.core.designsystem.theme.MyHealthTheme

/** Explains what the screen is for and offers ONE next step. */
@Composable
fun MyHealthEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CenteredState(modifier) {
        StateIcon(icon)
        Text(title, style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = MyHealthTheme.typography.body, color = MyHealthTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(MyHealthTheme.spacing.sm))
            MyHealthPrimaryButton(actionLabel, onAction, icon = MyHealthIcons.Add)
        }
    }
}

/** Actionable error: what happened, that data is safe, and how to recover. */
@Composable
fun MyHealthErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    CenteredState(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        StateIcon(MyHealthIcons.StatusError)
        Text(title, style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = MyHealthTheme.typography.body, color = MyHealthTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(MyHealthTheme.spacing.sm))
            MyHealthPrimaryButton(actionLabel, onAction, icon = MyHealthIcons.Sync)
        }
        if (secondaryLabel != null && onSecondary != null) MyHealthTextButton(secondaryLabel, onSecondary)
    }
}

/** Offline is the normal mode of this app; this state is used where a feature depends on another app/provider. */
@Composable
fun MyHealthOfflineState(title: String, message: String, modifier: Modifier = Modifier) {
    CenteredState(modifier) {
        StateIcon(MyHealthIcons.Offline)
        Text(title, style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = MyHealthTheme.typography.body, color = MyHealthTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

/** Skeleton list with a shimmer; announces [loadingLabel] to accessibility services. */
@Composable
fun MyHealthLoadingState(loadingLabel: String, modifier: Modifier = Modifier, rows: Int = 4) {
    Column(
        modifier.fillMaxWidth().padding(MyHealthTheme.spacing.gutter).semantics { contentDescription = loadingLabel },
        verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.md),
    ) {
        MyHealthSkeleton(height = 96.dp)
        repeat(rows) { MyHealthSkeleton(height = 56.dp) }
    }
}

@Composable
fun MyHealthSkeleton(height: Dp, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    val transition = rememberInfiniteTransition(label = ShimmerLabel)
    val x by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(MyHealthTheme.motion.shimmerMillis, easing = LinearEasing), RepeatMode.Restart),
        label = ShimmerLabel,
    )
    val brush = Brush.linearGradient(
        colors = listOf(c.surfaceSunken, c.outline, c.surfaceSunken),
        start = androidx.compose.ui.geometry.Offset(x * 600f, 0f),
        end = androidx.compose.ui.geometry.Offset(x * 600f + 600f, 0f),
    )
    Box(modifier.fillMaxWidth().height(height).clip(MyHealthTheme.radii.mdShape).background(brush))
}

@Composable
fun MyHealthProgress(label: String, progress: Float?, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    Column(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(label, style = MyHealthTheme.typography.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(MyHealthTheme.spacing.xs))
        if (progress == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceSunken)
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surfaceSunken)
        }
    }
}

@Composable
private fun CenteredState(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(MyHealthTheme.spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.md),
        ) { content() }
    }
}

@Composable
private fun StateIcon(icon: ImageVector) {
    val c = MyHealthTheme.colors
    Box(Modifier.size(72.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(36.dp))
    }
}

private const val ShimmerLabel = "shimmer" // animation inspector id, not UI copy
@Suppress("unused")
private val Transparent = Color.Transparent
