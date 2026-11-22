package com.solerunner.myhealth.ui.importdoc

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.lab.ParsedAnalyteRow
import com.solerunner.myhealth.ui.common.displayWithUnit
import com.solerunner.myhealth.ui.common.label

@Composable
internal fun RowRow(row: ParsedAnalyteRow, onValueChange: (String) -> Unit, onRemove: () -> Unit) {
    var text by remember(row) { mutableStateOf(row.printedValue?.toString() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            label = { Text(row.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            suffix = { if (row.printedUnit != null) Text(row.printedUnit) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.import_remove_row))
        }
    }
}

/** Vitals read from the same document plus the medicines that belong to it (only for bills). */
@Composable
internal fun ExtrasSection(state: ImportUiState.Review, viewModel: ImportViewModel) {
    val vitals = state.draft.document.vitals
    if (vitals.isNotEmpty()) {
        Text(stringResource(R.string.import_vitals, vitals.size), style = MaterialTheme.typography.titleMedium)
        vitals.forEach { vital ->
            Text(
                "${vital.kind.label()}: ${vital.primary}${vital.secondary?.let { "/$it" } ?: ""} ${vital.unit.orEmpty()}".trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (state.medicines.isEmpty()) return
    Spacer(Modifier.height(MyHealthTokens.spacing.md))
    Text(stringResource(R.string.import_medicines, state.medicines.size), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.import_medicines_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.medicines.forEachIndexed { index, medicine ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.selectedMedicines.contains(index),
                onCheckedChange = { viewModel.toggleMedicine(index) },
            )
            Column(Modifier.weight(1f)) {
                Text(medicine.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(medicine.strength, medicine.frequencyText, medicine.durationDays?.let { "$it d" })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Unused import guard for the shared formatter: keeps vitals rendering identical to the vitals screen. */
@Suppress("unused")
private fun previewUnit(primary: Double, unit: String?) = com.solerunner.myhealth.domain.model.Measurement(
    kind = com.solerunner.myhealth.domain.model.MeasurementKind.CUSTOM,
    primaryValue = primary,
    unit = unit,
    recordedAt = java.time.Instant.EPOCH,
    recordedDay = java.time.LocalDate.now(),
).displayWithUnit()
