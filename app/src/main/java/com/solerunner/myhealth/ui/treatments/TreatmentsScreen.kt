package com.solerunner.myhealth.ui.treatments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.ChoiceDialog
import com.solerunner.myhealth.design.components.ConfirmDialog
import com.solerunner.myhealth.design.components.DateField
import com.solerunner.myhealth.design.components.EmptyState
import com.solerunner.myhealth.design.components.ListRow
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Treatment
import com.solerunner.myhealth.domain.model.TreatmentKind
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.containerViewModel
import java.time.Instant
import java.time.LocalDate

/** Treatments timeline: surgeries, procedures, vaccinations, therapies and their follow-ups. */
@Composable
fun TreatmentsScreen(container: AppContainer) {
    val viewModel = containerViewModel(container) { TreatmentsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Treatment?>(null) }
    val spacing = MyHealthTokens.spacing

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenPadding, vertical = spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.treatments_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = viewModel::create) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.treatments_add))
            }
        }
        Spacer(Modifier.height(spacing.sm))
        if (state.treatments.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.treatments_empty_title),
                body = stringResource(R.string.treatments_empty_body),
                actionLabel = stringResource(R.string.treatments_add),
                onAction = viewModel::create,
            )
        } else {
            state.treatments.forEach { treatment ->
                ListRow(
                    title = treatment.name,
                    subtitle = listOfNotNull(treatment.date?.toString(), treatment.kind.label(), treatment.facility)
                        .joinToString(" · "),
                    trailing = treatment.followUpDate?.let { stringResource(R.string.treatments_follow_up_due, it.toString()) },
                    onClick = { viewModel.edit(treatment) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(spacing.xxxl))
    }

    if (state.creating || state.editing != null) {
        TreatmentEditorDialog(
            initial = state.editing,
            onDismiss = viewModel::dismiss,
            onSave = viewModel::save,
            onDelete = { pendingDelete = it },
        )
    }

    pendingDelete?.let { treatment ->
        ConfirmDialog(
            title = stringResource(R.string.treatments_delete_title),
            body = treatment.name,
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { viewModel.delete(treatment) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun TreatmentEditorDialog(
    initial: Treatment?,
    onDismiss: () -> Unit,
    onSave: (Treatment) -> Unit,
    onDelete: (Treatment) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: TreatmentKind.PROCEDURE) }
    var date by remember { mutableStateOf<LocalDate?>(initial?.date) }
    var followUp by remember { mutableStateOf<LocalDate?>(initial?.followUpDate) }
    var facility by remember { mutableStateOf(initial?.facility.orEmpty()) }
    var doctor by remember { mutableStateOf(initial?.doctor.orEmpty()) }
    var reason by remember { mutableStateOf(initial?.reason.orEmpty()) }
    var outcome by remember { mutableStateOf(initial?.outcome.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var showKinds by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.treatments_add else R.string.common_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TreatmentField(value = name, onChange = { name = it }, label = stringResource(R.string.treatments_name), error = error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.treatments_kind), modifier = Modifier.weight(1f))
                    TextButton(onClick = { showKinds = true }) { Text(kind.label()) }
                }
                DateField(label = stringResource(R.string.treatments_date), date = date, onChange = { date = it })
                DateField(label = stringResource(R.string.treatments_follow_up), date = followUp, onChange = { followUp = it }, allowClear = true)
                TreatmentField(value = facility, onChange = { facility = it }, label = stringResource(R.string.treatments_facility))
                TreatmentField(value = doctor, onChange = { doctor = it }, label = stringResource(R.string.treatments_doctor))
                TreatmentField(value = reason, onChange = { reason = it }, label = stringResource(R.string.meds_reason))
                TreatmentField(value = outcome, onChange = { outcome = it }, label = stringResource(R.string.treatments_outcome))
                TreatmentField(value = notes, onChange = { notes = it }, label = stringResource(R.string.treatments_notes))
                if (error) {
                    Text(stringResource(R.string.meds_name_required), style = MaterialTheme.typography.bodySmall)
                }
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) { Text(stringResource(R.string.common_delete)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().length < 2) {
                    error = true
                } else {
                    onSave(
                        Treatment(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            kind = kind,
                            date = date,
                            facility = facility.trim().takeIf { it.isNotEmpty() },
                            doctor = doctor.trim().takeIf { it.isNotEmpty() },
                            reason = reason.trim().takeIf { it.isNotEmpty() },
                            outcome = outcome.trim().takeIf { it.isNotEmpty() },
                            followUpDate = followUp,
                            notes = notes.trim(),
                            createdAt = initial?.createdAt ?: Instant.now(),
                        ),
                    )
                }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )

    if (showKinds) {
        ChoiceDialog(
            title = stringResource(R.string.treatments_kind),
            options = TreatmentKind.entries,
            selected = kind,
            onSelect = { kind = it },
            onDismiss = { showKinds = false },
            label = { it.label() },
        )
    }
}

@Composable
private fun TreatmentField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: Boolean = false,
) {
    Spacer(Modifier.height(MyHealthTokens.spacing.sm))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error,
        modifier = Modifier.fillMaxWidth(),
    )
}
