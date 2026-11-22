package app.myhealth.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.myhealth.core.designsystem.theme.MyHealthTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Read-only field that opens a date picker. Future dates can be disallowed (report dates, birth dates). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyHealthDateField(
    label: String,
    value: LocalDate?,
    display: @Composable (LocalDate) -> String,
    placeholder: String,
    onChange: (LocalDate) -> Unit,
    confirmLabel: String,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    maxDate: LocalDate? = null,
    errorText: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val c = MyHealthTheme.colors
    Column(modifier.fillMaxWidth()) {
        Surface(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).let { if (errorText != null) it.semantics { error(errorText) } else it },
            shape = MyHealthTheme.radii.mdShape,
            color = c.surface,
            border = BorderStroke(if (errorText != null) MyHealthTheme.stroke.strong else MyHealthTheme.stroke.hairline, if (errorText != null) c.textPrimary else c.outlineStrong),
        ) {
            Row(Modifier.padding(horizontal = MyHealthTheme.spacing.lg, vertical = MyHealthTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MyHealthTheme.typography.caption, color = c.textSecondary)
                    Text(if (value != null) display(value) else placeholder, style = MyHealthTheme.typography.body, color = c.textPrimary)
                }
                Icon(MyHealthIcons.Date, null, tint = c.textSecondary)
            }
        }
        if (errorText != null) {
            Row(Modifier.padding(start = MyHealthTheme.spacing.lg, top = MyHealthTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(MyHealthIcons.StatusError, null, tint = c.textPrimary, modifier = Modifier.padding(end = MyHealthTheme.spacing.xs))
                Text(errorText, style = MyHealthTheme.typography.caption, color = c.textPrimary)
            }
        }
    }
    if (open) {
        val maxMillis = maxDate?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = maxMillis == null || utcTimeMillis <= maxMillis
            },
        )
        val colors = DatePickerDefaults.colors(
            containerColor = c.surfaceRaised, selectedDayContainerColor = c.accent, selectedDayContentColor = c.onAccent,
            todayDateBorderColor = c.accent, todayContentColor = c.accent, selectedYearContainerColor = c.accent, selectedYearContentColor = c.onAccent,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                MyHealthTextButton(confirmLabel, {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                })
            },
            dismissButton = { MyHealthTextButton(dismissLabel, { open = false }) },
            colors = colors,
        ) { DatePicker(state = state, colors = colors) }
    }
}
