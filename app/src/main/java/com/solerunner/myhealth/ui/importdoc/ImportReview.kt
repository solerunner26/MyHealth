package com.solerunner.myhealth.ui.importdoc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.ChoiceDialog
import com.solerunner.myhealth.design.components.DateField
import com.solerunner.myhealth.design.components.PillTone
import com.solerunner.myhealth.design.components.StatusPill
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.ui.common.label

/**
 * Review screen. Parsed rows are editable text fields rather than read-only labels: OCR is good but not
 * perfect, and the user is the final authority on their own record. `Save` is the only write path.
 */
@Composable
fun ImportReview(state: ImportUiState.Review, viewModel: ImportViewModel, onBack: () -> Unit) {
    var showKinds by remember { mutableStateOf(false) }
    val spacing = MyHealthTokens.spacing

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.import_cancel)) }
            Spacer(Modifier.weight(1f))
            StatusPill(
                text = stringResource(R.string.import_confidence, (state.draft.confidence * 100).toInt()),
                tone = if (state.draft.confidence >= 0.62) PillTone.GOOD else PillTone.WARN,
            )
        }
        Text(stringResource(R.string.import_review_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.import_review_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.md))
        OutlinedTextField(
            value = state.report.title,
            onValueChange = { title -> viewModel.updateReport { it.copy(title = title) } },
            label = { Text(stringResource(R.string.import_title_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(
            label = stringResource(R.string.import_report_date),
            date = state.report.sampleDate,
            onChange = { date -> date?.let { picked -> viewModel.updateReport { it.copy(sampleDate = picked) } } },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.import_kind),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showKinds = true }) { Text(state.report.kind.label()) }
        }
        Spacer(Modifier.height(spacing.sm))
        Text(stringResource(R.string.import_results, state.rows.size), style = MaterialTheme.typography.titleMedium)
        state.rows.forEachIndexed { index, row ->
            RowRow(row = row, onValueChange = { viewModel.editValue(index, it) }, onRemove = { viewModel.removeRow(index) })
        }
        if (state.rows.isEmpty()) {
            Text(
                stringResource(R.string.import_no_rows),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExtrasSection(state = state, viewModel = viewModel)
        if (state.error != null) {
            Text(state.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = viewModel::save,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md),
        ) {
            Text(if (state.saving) stringResource(R.string.import_saving) else stringResource(R.string.import_save))
        }
        Spacer(Modifier.height(spacing.xl))
    }

    if (showKinds) {
        ChoiceDialog(
            title = stringResource(R.string.import_kind),
            options = ReportKind.entries,
            selected = state.report.kind,
            onSelect = { kind -> viewModel.updateReport { it.copy(kind = kind) } },
            onDismiss = { showKinds = false },
            label = { it.label() },
        )
    }
}
