package app.myhealth.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.myhealth.core.designsystem.theme.MyHealthTheme
import java.time.LocalTime

/** Tappable time chip that opens a time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyHealthTimeField(
    label: String,
    value: LocalTime,
    display: @Composable (LocalTime) -> String,
    onChange: (LocalTime) -> Unit,
    confirmLabel: String,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val c = MyHealthTheme.colors
    Surface(onClick = { open = true }, modifier = modifier.heightIn(min = 56.dp), shape = MyHealthTheme.radii.mdShape, color = c.surface,
        border = BorderStroke(MyHealthTheme.stroke.hairline, c.outlineStrong)) {
        Row(Modifier.padding(horizontal = MyHealthTheme.spacing.md, vertical = MyHealthTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(label, style = MyHealthTheme.typography.caption, color = c.textSecondary)
                Text(display(value), style = MyHealthTheme.typography.body, color = c.textPrimary)
            }
            Icon(MyHealthIcons.Schedule, null, tint = c.textSecondary, modifier = Modifier.padding(start = MyHealthTheme.spacing.sm))
        }
    }
    if (open) {
        val state = rememberTimePickerState(value.hour, value.minute, is24Hour)
        AlertDialog(
            onDismissRequest = { open = false },
            text = {
                TimePicker(state, colors = TimePickerDefaults.colors(
                    clockDialSelectedContentColor = c.onAccent, selectorColor = c.accent, timeSelectorSelectedContainerColor = c.accentContainer,
                    timeSelectorSelectedContentColor = c.onAccentContainer, periodSelectorSelectedContainerColor = c.accentContainer,
                    periodSelectorSelectedContentColor = c.onAccentContainer, clockDialColor = c.surfaceSunken,
                ))
            },
            confirmButton = { MyHealthTextButton(confirmLabel, { onChange(LocalTime.of(state.hour, state.minute)); open = false }) },
            dismissButton = { MyHealthTextButton(dismissLabel, { open = false }) },
            containerColor = c.surfaceRaised, shape = MyHealthTheme.radii.xlShape,
        )
    }
}
