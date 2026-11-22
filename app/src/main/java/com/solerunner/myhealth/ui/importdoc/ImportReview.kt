package com.solerunner.myhealth.ui.importdoc

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
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.DateField
import com.solerunner.myhealth.design.rare.RareBadge
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareChoiceDialog
import com.solerunner.myhealth.design.rare.RareChip
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.rare.RareTone
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.ui.common.label

/**
 * Review screen. Parsed rows are editable fields, not labels: OCR is good but not perfect and the user
 * is the final authority on their own record. Save is the only write path.
 */
@Composable
fun ImportReview(state: ImportUiState.Review, viewModel: ImportViewModel, onBack: () -> Unit) {
    var showKinds by remember { mutableStateOf(false) }
    val spacing = MyHealthTokens.spacing

    Column(Modifier.fillMaxSize()) {
        RarePageHeader(
            title = stringResource(R.string.import_review_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.sm),
            actions = {
                RareBadge(
                    text = stringResource(R.string.import_confidence, (state.draft.confidence * 100).toInt()),
                    tone = if (state.draft.confidence >= 0.62) RareTone.ACCENT else RareTone.NEUTRAL,
                )
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPadding),
        ) {
            Text(
                stringResource(R.string.import_review_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.md))
            RareCard {
                RareTextField(
                    value = state.report.title,
                    onValueChange = { title -> viewModel.updateReport { it.copy(title = title) } },
                    label = stringResource(R.string.import_title_field),
                )
                Spacer(Modifier.height(spacing.sm))
                DateField(
                    label = stringResource(R.string.import_report_date),
                    date = state.report.sampleDate,
                    onChange = { date -> date?.let { picked -> viewModel.updateReport { it.copy(sampleDate = picked) } } },
                )
                Spacer(Modifier.height(spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.import_kind),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    RareChip(text = state.report.kind.label(), selected = false, onClick = { showKinds = true })
                }
            }

            Spacer(Modifier.height(spacing.lg))
            Text(
                stringResource(R.string.import_results, state.rows.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (state.rows.isEmpty()) {
                RareCard(inset = true) {
                    Text(
                        stringResource(R.string.import_no_rows),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                state.rows.forEachIndexed { index, row ->
                    RowRow(row = row, onValueChange = { viewModel.editValue(index, it) }, onRemove = { viewModel.removeRow(index) })
                    Spacer(Modifier.height(spacing.xs))
                }
            }

            ExtrasSection(state = state, viewModel = viewModel)

            if (state.error != null) {
                Spacer(Modifier.height(spacing.sm))
                Text(state.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(spacing.md))
            RareButton(
                text = if (state.saving) stringResource(R.string.import_saving) else stringResource(R.string.import_save),
                onClick = viewModel::save,
                enabled = !state.saving,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_cancel))
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }

    if (showKinds) {
        RareChoiceDialog(
            title = stringResource(R.string.import_kind),
            options = ReportKind.entries,
            selected = state.report.kind,
            onSelect = { kind -> viewModel.updateReport { it.copy(kind = kind) } },
            onDismiss = { showKinds = false },
            label = { it.label() },
        )
    }
}
