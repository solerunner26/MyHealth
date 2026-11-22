package com.solerunner.myhealth.ui.medicines

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.ChoiceDialog
import com.solerunner.myhealth.design.components.DateField
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.MedicineForm
import com.solerunner.myhealth.ui.common.label

/** Add/edit a medicine. An empty end date means an ongoing course. */
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.meds_add else R.string.meds_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FormField(value = draft.name, onChange = { draft.name = it }, label = stringResource(R.string.meds_name), error = error)
                FormField(value = draft.molecule, onChange = { draft.molecule = it }, label = stringResource(R.string.meds_molecule))
                FormField(value = draft.strength, onChange = { draft.strength = it }, label = stringResource(R.string.meds_strength))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.meds_form), modifier = Modifier.weight(1f))
                    TextButton(onClick = { showForms = true }) { Text(draft.form.label()) }
                }
                FormField(value = draft.frequency, onChange = { draft.frequency = it }, label = stringResource(R.string.meds_frequency))
                FormField(value = draft.timesPerDay, onChange = { draft.timesPerDay = it }, label = stringResource(R.string.meds_times_per_day), numeric = true)
                FormField(value = draft.durationDays, onChange = { draft.durationDays = it }, label = stringResource(R.string.meds_duration), numeric = true)
                DateField(label = stringResource(R.string.meds_start), date = draft.startDate, onChange = { draft.startDate = it })
                DateField(label = stringResource(R.string.meds_end), date = draft.endDate, onChange = { draft.endDate = it }, allowClear = true)
                FormField(value = draft.reason, onChange = { draft.reason = it }, label = stringResource(R.string.meds_reason))
                FormField(value = draft.prescribedBy, onChange = { draft.prescribedBy = it }, label = stringResource(R.string.meds_prescribed_by))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.meds_reminder), modifier = Modifier.weight(1f))
                    Switch(checked = draft.reminderEnabled, onCheckedChange = { draft.reminderEnabled = it })
                }
                if (draft.reminderEnabled) {
                    FormField(
                        value = draft.reminderTimes,
                        onChange = { draft.reminderTimes = it },
                        label = stringResource(R.string.meds_reminder_times) + " (08:00, 21:00)",
                    )
                }
                FormField(value = draft.notes, onChange = { draft.notes = it }, label = stringResource(R.string.meds_notes), singleLine = false)
                if (error) {
                    Text(stringResource(R.string.meds_name_required), style = MaterialTheme.typography.bodySmall)
                }
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) { Text(stringResource(R.string.common_delete)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!draft.isValid()) error = true else onSave(draft.toMedicine(initial))
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )

    if (showForms) {
        ChoiceDialog(
            title = stringResource(R.string.meds_form),
            options = MedicineForm.entries,
            selected = draft.form,
            onSelect = { draft.form = it },
            onDismiss = { showForms = false },
            label = { it.label() },
        )
    }
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    error: Boolean = false,
) {
    Spacer(Modifier.height(MyHealthTokens.spacing.sm))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        isError = error,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
