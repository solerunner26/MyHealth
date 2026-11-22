package com.solerunner.myhealth.ui.medicines

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.DateField
import com.solerunner.myhealth.design.rare.RareBadge
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareChoiceDialog
import com.solerunner.myhealth.design.rare.RareChip
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareSheet
import com.solerunner.myhealth.design.rare.RareSwitch
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.MedicineForm
import com.solerunner.myhealth.ui.common.label

/** Add or edit a medicine in a Rare sheet; an empty end date means an ongoing course. */
@Composable
fun MedicineEditorDialog(
    initial: Medicine?,
    onDismiss: () -> Unit,
    onSave: (Medicine) -> Unit,
    onDelete: (Medicine) -> Unit,
) {
    val draft = remember(initial) { MedicineDraft(initial) }
    var showForms by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val spacing = MyHealthTokens.spacing

    RareSheet(onDismiss = onDismiss) {
        Text(
            stringResource(if (initial == null) R.string.meds_add else R.string.meds_edit),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(spacing.md))
        RareTextField(
            value = draft.name,
            onValueChange = { draft.name = it },
            label = stringResource(R.string.meds_name),
            isError = error,
        )
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = draft.molecule, onValueChange = { draft.molecule = it }, label = stringResource(R.string.meds_molecule))
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = draft.strength, onValueChange = { draft.strength = it }, label = stringResource(R.string.meds_strength))
        Spacer(Modifier.height(spacing.sm))
        Text(
            stringResource(R.string.meds_form),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row { RareChip(text = draft.form.label(), selected = true, onClick = { showForms = true }) }
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = draft.frequency, onValueChange = { draft.frequency = it }, label = stringResource(R.string.meds_frequency))
        Spacer(Modifier.height(spacing.sm))
        RareTextField(
            value = draft.timesPerDay,
            onValueChange = { draft.timesPerDay = it },
            label = stringResource(R.string.meds_times_per_day),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(spacing.sm))
        RareTextField(
            value = draft.durationDays,
            onValueChange = { draft.durationDays = it },
            label = stringResource(R.string.meds_duration),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(spacing.sm))
        DateField(label = stringResource(R.string.meds_start), date = draft.startDate, onChange = { draft.startDate = it })
        Spacer(Modifier.height(spacing.sm))
        DateField(label = stringResource(R.string.meds_end), date = draft.endDate, onChange = { draft.endDate = it }, allowClear = true)
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = draft.reason, onValueChange = { draft.reason = it }, label = stringResource(R.string.meds_reason))
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = draft.prescribedBy, onValueChange = { draft.prescribedBy = it }, label = stringResource(R.string.meds_prescribed_by))
        Spacer(Modifier.height(spacing.md))
        RareDivider()
        Spacer(Modifier.height(spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.meds_reminder), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.meds_reminders_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RareSwitch(checked = draft.reminderEnabled, onCheckedChange = { draft.reminderEnabled = it })
        }
        if (draft.reminderEnabled) {
            Spacer(Modifier.height(spacing.sm))
            RareTextField(
                value = draft.reminderTimes,
                onValueChange = { draft.reminderTimes = it },
                label = stringResource(R.string.meds_reminder_times) + " (08:00, 21:00)",
            )
        }
        Spacer(Modifier.height(spacing.sm))
        RareTextField(
            value = draft.notes,
            onValueChange = { draft.notes = it },
            label = stringResource(R.string.meds_notes),
            singleLine = false,
            minHeight = 64,
        )
        if (error) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.meds_name_required), style = MaterialTheme.typography.bodySmall)
        }
        if (initial != null) {
            Spacer(Modifier.height(spacing.sm))
            TextButton(onClick = { onDelete(initial) }) { Text(stringResource(R.string.common_delete)) }
        }
        Spacer(Modifier.height(spacing.md))
        RareButton(
            text = stringResource(R.string.common_save),
            onClick = {
                if (!draft.isValid()) error = true else onSave(draft.toMedicine(initial))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showForms) {
        RareChoiceDialog(
            title = stringResource(R.string.meds_form),
            options = MedicineForm.entries,
            selected = draft.form,
            onSelect = { draft.form = it },
            onDismiss = { showForms = false },
            label = { it.label() },
        )
    }
}
