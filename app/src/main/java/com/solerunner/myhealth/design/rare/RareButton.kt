package com.solerunner.myhealth.design.rare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.theme.MyHealthTokens

enum class RareButtonStyle { PRIMARY, SECONDARY, GHOST, DESTRUCTIVE }

/**
 * The single button primitive. 46dp tall, 10dp radius, 1dp border on the secondary variant.
 *
 * Rare UI ships a black primary; this product keeps its one pine accent in that slot so the palette
 * rule survives the redesign. Destructive actions use solid ink rather than red, and rely on wording
 * plus the confirmation sheet for safety.
 */
@Composable
fun RareButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RareButtonStyle = RareButtonStyle.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = when (style) {
        RareButtonStyle.PRIMARY -> colors.primary
        RareButtonStyle.SECONDARY -> Color.Transparent
        RareButtonStyle.GHOST -> Color.Transparent
        RareButtonStyle.DESTRUCTIVE -> colors.onSurface
    }
    val content = when (style) {
        RareButtonStyle.PRIMARY -> colors.onPrimary
        RareButtonStyle.SECONDARY -> colors.onSurface
        RareButtonStyle.GHOST -> colors.primary
        RareButtonStyle.DESTRUCTIVE -> colors.surface
    }
    val border = when (style) {
        RareButtonStyle.SECONDARY -> BorderStroke(1.dp, colors.outline)
        else -> null
    }
    val interaction = rememberInteraction()
    Surface(
        modifier = modifier
            .heightIn(min = MyHealthTokens.dims.controlHeight)
            .pressScale(interaction, enabled && !loading)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.45f),
        shape = MaterialTheme.shapes.medium,
        color = container,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp).heightIn(min = MyHealthTokens.dims.controlHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = content,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

/** Square-ish icon action with an optional count badge. */
@Composable
fun RareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    badge: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = rememberInteraction()
    Box(modifier = modifier.size(MyHealthTokens.dims.iconButton)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.medium)
                .background(if (filled) colors.primaryContainer else Color.Transparent)
                .then(if (filled) Modifier else Modifier.hairline(colors.outlineVariant, MaterialTheme.shapes.medium))
                .pressScale(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (filled) colors.onPrimaryContainer else colors.onSurface,
                modifier = Modifier.size(19.dp),
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = colors.onPrimary)
            }
        }
    }
}

/** Full-width variant used for sticky footers. */
@Composable
fun RarePrimaryButtonWide(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    RareButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
    )
}
