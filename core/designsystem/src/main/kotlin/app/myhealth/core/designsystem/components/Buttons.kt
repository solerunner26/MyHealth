package app.myhealth.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import app.myhealth.core.designsystem.theme.MyHealthTheme

private const val PRESS = "press" // animation inspector id, not UI copy

/** The ONE primary action of a screen. */
@Composable
fun MyHealthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = MyHealthTheme.colors
    val shape = MyHealthTheme.radii.pillShape
    // Glass: gradient of the single hue (deep → bright) with a soft glow. Solid: the deep hero shade, like ink.
    val bg = when {
        !enabled -> androidx.compose.ui.graphics.SolidColor(c.surfaceSunken)
        else -> androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(c.accent, androidx.compose.ui.graphics.lerp(c.accent, c.accentBright, if (c.isDark) 0.55f else 0.25f)))
    }
    val fg = c.onAccent
    val pressed = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by pressed.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isPressed) 0.97f else 1f, label = PRESS)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = pressed,
        modifier = modifier.defaultMinSize(minHeight = 52.dp).scale(scale)
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = if (c.isGlass) c.glow else c.shadow, spotColor = if (c.isGlass) c.glow else c.hero)
            .background(bg, shape).then(if (c.isGlass && enabled) Modifier.gloss(shape, 0.38f) else Modifier),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = fg,
            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent, disabledContentColor = c.textTertiary,
        ),
        contentPadding = PaddingValues(horizontal = MyHealthTheme.spacing.xl, vertical = MyHealthTheme.spacing.md),
    ) { ButtonContent(text, icon) }
}

@Composable
fun MyHealthSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = MyHealthTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = MyHealthTheme.spacing.touchTarget),
        shape = MyHealthTheme.radii.pillShape,
        border = BorderStroke(MyHealthTheme.stroke.regular, if (enabled) c.outlineStrong else c.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary, disabledContentColor = c.textTertiary),
        contentPadding = PaddingValues(horizontal = MyHealthTheme.spacing.xl, vertical = MyHealthTheme.spacing.md),
    ) { ButtonContent(text, icon) }
}

@Composable
fun MyHealthTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = MyHealthTheme.colors
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = MyHealthTheme.spacing.touchTarget),
        colors = ButtonDefaults.textButtonColors(contentColor = c.accent, disabledContentColor = c.textTertiary),
    ) { ButtonContent(text, icon) }
}

@Composable
fun MyHealthIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(MyHealthTheme.spacing.touchTarget)) {
        Icon(icon, contentDescription = contentDescription, tint = MyHealthTheme.colors.textPrimary)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    Row {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(MyHealthTheme.spacing.sm))
        }
        Text(text, style = MyHealthTheme.typography.label)
    }
}

/** Quick action: icon in a rounded well with the label below (reference boards: action rows under the hero). */
@Composable
fun MyHealthQuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    androidx.compose.foundation.layout.Column(
        modifier.clip(MyHealthTheme.radii.mdShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(58.dp).material(SurfaceKind.Card, shape),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { Icon(icon, null, tint = if (c.isGlass && c.isDark) c.accentBright else c.accent, modifier = Modifier.size(24.dp)) }
        Text(label, style = MyHealthTheme.typography.caption, color = c.textPrimary, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp))
    }
}
