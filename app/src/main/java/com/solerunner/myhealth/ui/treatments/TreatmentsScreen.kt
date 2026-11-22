package com.solerunner.myhealth.ui.treatments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.DateField
import com.solerunner.myhealth.design.rare.RareBadge
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareChoiceDialog
import com.solerunner.myhealth.design.rare.RareChip
import com.solerunner.myhealth.design.rare.RareConfirmDialog
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareEmptyState
import com.solerunner.myhealth.design.rare.RareIconButton
import com.solerunner.myhealth.design.rare.RareListRow
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RareScrollProgress
import com.solerunner.myhealth.design.rare.RareSheet
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.rare.RareTone
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
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        RareScrollProgress(scroll)
        RarePageHeader(
            title = stringResource(R.string.treatments_title),
            eyebrow = if (state.treatments.isEmpty()) null else state.treatments.size.toString(),
            modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.md),
            actions = {
                RareIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.treatments_add),
                    onClick = viewModel::create,
                    filled = true,
                )
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = spacing.screenPadding),
        ) {
            if (state.treatments.isEmpty()) {
                RareEmptyState(
                    title = stringResource(R.string.treatments_empty_title),
                    body = stringResource(R.string.treatments_empty_body),
                    actionLabel = stringResource(R.string.treatments_add),
                    onAction = viewModel::create,
                )
            } else {
                RareCard(contentPadding = 0.dp) {
                    state.treatments.forEachIndexed { index, treatment ->
                        Column(Modifier.padding(horizontal = 14.dp)) {
                            RareListRow(
                                title = treatment.name,
                                subtitle = listOfNotNull(
                                    treatment.date?.toString(),
                                    treatment.kind.label(),
                                    treatment.facility,
                                ).joinToString(" · "),
                                trailing = treatment.followUpDate?.toString(),
                                onClick = { viewModel.edit(treatment) },
                            )
                        }
                        if (index != state.treatments.lastIndex) RareDivider(Modifier.padding(horizontal = 14.dp))
                    }
                }
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }

    if (state.creating || state.editing != null) {
        TreatmentEditorSheet(
            initial = state.editing,
            onDismiss = viewModel::dismiss,
            onSave = viewModel::save,
            onDelete = { pendingDelete = it },
        )
    }

    pendingDelete?.let { treatment ->
        RareConfirmDialog(
            title = stringResource(R.string.treatments_delete_title),
            body = treatment.name,
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { viewModel.delete(treatment) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun TreatmentEditorSheet(
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
    val spacing = MyHealthTokens.spacing

    RareSheet(onDismiss = onDismiss) {
        Text(
            stringResource(if (initial == null) R.string.treatments_add else R.string.common_edit),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(spacing.md))
        RareTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.treatments_name),
            isError = error,
        )
        Spacer(Modifier.height(spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.treatments_kind),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            RareChip(text = kind.label(), selected = false, onClick = { showKinds = true })
        }
        Spacer(Modifier.height(spacing.sm))
        DateField(label = stringResource(R.string.treatments_date), date = date, onChange = { date = it })
        Spacer(Modifier.height(spacing.sm))
        DateField(label = stringResource(R.string.treatments_follow_up), date = followUp, onChange = { followUp = it }, allowClear = true)
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = facility, onValueChange = { facility = it }, label = stringResource(R.string.treatments_facility))
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = doctor, onValueChange = { doctor = it }, label = stringResource(R.string.treatments_doctor))
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = reason, onValueChange = { reason = it }, label = stringResource(R.string.meds_reason))
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = outcome, onValueChange = { outcome = it }, label = stringResource(R.string.treatments_outcome))
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = notes, onValueChange = { notes = it }, label = stringResource(R.string.treatments_notes), singleLine = false, minHeight = 64)
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
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showKinds) {
        RareChoiceDialog(
            title = stringResource(R.string.treatments_kind),
            options = TreatmentKind.entries,
            selected = kind,
            onSelect = { kind = it },
            onDismiss = { showKinds = false },
            label = { it.label() },
        )
    }
}
