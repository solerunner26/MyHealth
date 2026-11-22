package com.solerunner.myhealth.design.rare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class RareTone { ACCENT, NEUTRAL, OUTLINE, SOLID }

/** Filter chip: a pill that fills when selected, so choice never relies on colour alone. */
@Composable
fun RareChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = rememberInteraction()
    Surface(
        modifier = modifier.pressScale(interaction, pressedScale = 0.95f).clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (selected) colors.primary else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leading != null) {
                Icon(
                    leading,
                    contentDescription = null,
                    tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onPrimary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Status badge. Severity is always icon + wording; tone only adds fill weight. */
@Composable
fun RareBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: RareTone = RareTone.NEUTRAL,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    when (tone) {
        RareTone.ACCENT -> {
            container = colors.primaryContainer
            content = colors.onPrimaryContainer
        }
        RareTone.NEUTRAL -> {
            container = colors.surfaceContainerHigh
            content = colors.onSurfaceVariant
        }
        RareTone.OUTLINE -> {
            container = Color.Transparent
            content = colors.onSurface
        }
        RareTone.SOLID -> {
            container = colors.onSurface
            content = colors.surface
        }
    }
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = container,
        border = if (tone == RareTone.OUTLINE) BorderStroke(1.dp, colors.outline) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
            }
            Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = content)
        }
    }
}

/** Small dot used for status legends and nav indicators. */
@Composable
fun RareDot(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(6.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color),
    )
}
