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
import app.myhealth.core.designsystem.theme.MyHealthTheme

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
    // Gradient of the single accent hue (deep → bright) with a soft neon glow underneath.
    val bg = if (enabled) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(c.accent, androidx.compose.ui.graphics.lerp(c.accent, c.accentBright, if (c.isDark) 0.55f else 0.2f)))
        else androidx.compose.ui.graphics.SolidColor(c.surfaceSunken)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 52.dp)
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = c.glow, spotColor = c.glow)
            .background(bg, shape),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = c.onAccent,
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
