package com.solerunner.myhealth.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.theme.MyHealthTokens

/**
 * Shared layout vocabulary: cards, section headers, status pills and table containers. Every screen
 * uses these so spacing, radii and status wording stay identical across the product.
 *
 * Status is never communicated by colour alone: each pill carries a label and an icon, which is also
 * what screen readers announce.
 */

enum class PillTone { NEUTRAL, GOOD, WARN, ALERT, INFO }

@Composable
fun HealthCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MyHealthTokens.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(spacing.hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(spacing.cardPadding), content = content)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = MyHealthTokens.spacing
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = MaterialTheme.colorScheme
    val background: Color
    val foreground: Color
    when (tone) {
        PillTone.GOOD -> { background = colors.primaryContainer; foreground = colors.onPrimaryContainer }
        PillTone.ALERT -> { background = colors.errorContainer; foreground = colors.onErrorContainer }
        PillTone.WARN -> { background = colors.surfaceContainerHighest; foreground = colors.onSurface }
        PillTone.INFO -> { background = colors.secondaryContainer; foreground = colors.onSecondaryContainer }
        PillTone.NEUTRAL -> { background = colors.surfaceContainerHigh; foreground = colors.onSurfaceVariant }
    }
    Row(
        modifier = modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(13.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = foreground)
    }
}

@Composable
fun StatusDot(tone: PillTone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        PillTone.GOOD -> MaterialTheme.colorScheme.primary
        PillTone.ALERT -> MaterialTheme.colorScheme.onSurface
        PillTone.WARN -> MaterialTheme.colorScheme.outline
        PillTone.INFO, PillTone.NEUTRAL -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** Hairline-bordered container for dense tables (analyte rows, trend history). */
@Composable
fun TableContainer(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
fun BorderBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(MyHealthTokens.spacing.md),
        content = content,
    )
}
