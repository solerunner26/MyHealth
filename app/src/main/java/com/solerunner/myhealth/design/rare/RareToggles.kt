package com.solerunner.myhealth.design.rare

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.theme.MyHealthTokens

/** 44 x 26 track with a 20dp knob; the knob is what moves, not the whole control. */
@Composable
fun RareSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = rememberInteraction()
    val offset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = indicatorSpring(),
        label = "switch-knob",
    )
    val track by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.surfaceContainerHighest,
        label = "switch-track",
    )
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(track)
            .hairline(if (checked) colors.primary else colors.outlineVariant, CircleShape)
            .pressScale(interaction, enabled, pressedScale = 0.94f)
            .toggleable(
                value = checked,
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) colors.onPrimary else colors.surface)
                .hairline(if (checked) colors.primary else colors.outline, CircleShape),
        )
    }
}

/** 22dp rounded checkbox with a check mark that appears with the fill. */
@Composable
fun RareCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = rememberInteraction()
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (checked) colors.primary else colors.surface)
            .hairline(if (checked) colors.primary else colors.outline, MaterialTheme.shapes.extraSmall)
            .pressScale(interaction, enabled, pressedScale = 0.9f)
            .toggleable(
                value = checked,
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
