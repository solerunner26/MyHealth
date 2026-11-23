package app.myhealth.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.myhealth.core.designsystem.theme.MyHealthTheme

@Composable
fun MyHealthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = MyHealthTheme.colors
    val isError = errorText != null
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().let { if (isError) it.semantics { error(errorText!!) } else it },
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        trailingIcon = if (isError) ({ Icon(MyHealthIcons.StatusError, null) }) else null,
        supportingText = (errorText ?: supportingText)?.let { { Text(it) } },
        shape = MyHealthTheme.radii.mdShape,
        textStyle = MyHealthTheme.typography.body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.outlineStrong,
            errorBorderColor = c.textPrimary, errorLabelColor = c.textPrimary, errorSupportingTextColor = c.textPrimary,
            errorTrailingIconColor = c.textPrimary, errorCursorColor = c.textPrimary,
            focusedLabelColor = c.accent, unfocusedLabelColor = c.textSecondary, cursorColor = c.accent,
            focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
            focusedContainerColor = c.glassTop, unfocusedContainerColor = c.glassTop, errorContainerColor = c.glassTop,
            disabledContainerColor = c.surfaceSunken, disabledTextColor = c.textTertiary,
        ),
    )
}

/**
 * A selectable chip: selection shown by fill + check glyph, not colour alone. Selection animates (fill and text
 * cross-fade, the check grows in). Solid themes use the hero ink pill; Glass themes a tinted glass pill.
 */
@Composable
fun MyHealthChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MyHealthTheme.colors
    val spec = androidx.compose.animation.core.tween<androidx.compose.ui.graphics.Color>(if (MyHealthTheme.reduceMotion) 0 else 280, easing = MyHealthTheme.motion.emphasized)
    val selBg = if (c.isGlass) c.accentContainer else c.hero
    val selFg = if (c.isGlass) c.onAccentContainer else c.onHero
    val bg by androidx.compose.animation.animateColorAsState(if (selected) selBg else if (c.isGlass) c.glassTop else c.surfaceRaised, spec, label = CHIP)
    val fg by androidx.compose.animation.animateColorAsState(if (selected) selFg else c.textPrimary, spec, label = CHIP)
    val edge by androidx.compose.animation.animateColorAsState(if (selected) (if (c.isGlass) c.accent else c.hero) else if (c.isGlass) c.glassBorder else c.outline, spec, label = CHIP)
    Surface(
        modifier = modifier.heightIn(min = 40.dp).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = MyHealthTheme.radii.pillShape,
        color = bg,
        border = BorderStroke(if (selected) MyHealthTheme.stroke.regular else MyHealthTheme.stroke.hairline, edge),
    ) {
        Row(Modifier.padding(horizontal = MyHealthTheme.spacing.md, vertical = MyHealthTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.animation.AnimatedVisibility(selected, enter = androidx.compose.animation.expandHorizontally() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.shrinkHorizontally() + androidx.compose.animation.scaleOut()) {
                Row {
                    Icon(MyHealthIcons.Check, null, tint = fg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(MyHealthTheme.spacing.xs))
                }
            }
            Text(label, style = MyHealthTheme.typography.label, color = fg)
        }
    }
}

private const val CHIP = "chip" // animation inspector id, not UI copy

@Composable
fun <T> MyHealthChipRow(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
        options.forEach { o -> MyHealthChip(label(o), o == selected, { onSelect(o) }) }
    }
}

@Composable
fun MyHealthSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true) {
    val c = MyHealthTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = MyHealthTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MyHealthTheme.typography.subtitle, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = MyHealthTheme.typography.bodySmall, color = c.textSecondary)
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            thumbContent = if (checked) ({ Icon(MyHealthIcons.Check, null, Modifier.size(14.dp)) }) else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, checkedIconColor = c.accent,
                uncheckedThumbColor = c.outlineStrong, uncheckedTrackColor = c.surfaceSunken, uncheckedBorderColor = c.outlineStrong,
            ),
        )
    }
}

/** A field that opens a single-choice dialog (sex, blood group, frequency...). */
@Composable
fun <T> MyHealthChoiceField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String,
    placeholder: String = "",
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val c = MyHealthTheme.colors
    Surface(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MyHealthTheme.radii.mdShape,
        color = c.surface,
        border = BorderStroke(MyHealthTheme.stroke.hairline, c.outlineStrong),
    ) {
        Row(Modifier.padding(horizontal = MyHealthTheme.spacing.lg, vertical = MyHealthTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MyHealthTheme.typography.caption, color = c.textSecondary)
                Text(selected?.let { optionLabel(it) } ?: placeholder, style = MyHealthTheme.typography.body, color = c.textPrimary)
            }
            Icon(MyHealthIcons.Expand, null, tint = c.textSecondary)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label, style = MyHealthTheme.typography.title) },
            text = {
                LazyColumn {
                    items(options) { o ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = o == selected, role = Role.RadioButton) { onSelect(o); open = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = o == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.outlineStrong))
                            Spacer(Modifier.width(MyHealthTheme.spacing.md))
                            Text(optionLabel(o), style = MyHealthTheme.typography.body, color = c.textPrimary)
                        }
                    }
                }
            },
            confirmButton = { MyHealthTextButton(confirmLabel, { open = false }) },
            containerColor = c.surfaceRaised,
            shape = MyHealthTheme.radii.xlShape,
        )
    }
}

@Composable
fun MyHealthDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val c = MyHealthTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null, tint = c.accent) } },
        title = { Text(title, style = MyHealthTheme.typography.title, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.md)) {
                if (text.isNotBlank()) Text(text, style = MyHealthTheme.typography.body, color = c.textSecondary)
                content?.invoke()
            }
        },
        confirmButton = { MyHealthTextButton(confirmLabel, onConfirm) },
        dismissButton = if (dismissLabel.isBlank()) null else ({ MyHealthTextButton(dismissLabel, onDismiss) }),
        containerColor = c.surfaceRaised,
        shape = MyHealthTheme.radii.xlShape,
    )
}

@Suppress("unused")
private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

/** Checkbox row; checked state is shown by a filled box with a tick (shape), not by colour alone. */
@Composable
fun MyHealthCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, showLabel: Boolean = false) {
    val c = MyHealthTheme.colors
    Row(
        modifier.heightIn(min = 48.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { this.contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(
            checked = checked, onCheckedChange = null,
            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.outlineStrong, checkmarkColor = c.onAccent),
        )
        if (showLabel) {
            Spacer(Modifier.width(MyHealthTheme.spacing.sm))
            Text(label, style = MyHealthTheme.typography.body, color = c.textPrimary)
        }
    }
}
