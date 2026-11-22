package com.solerunner.myhealth.ui.importdoc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareCheckbox
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.lab.ParsedAnalyteRow
import com.solerunner.myhealth.ui.common.label

@Composable
internal fun RowRow(row: ParsedAnalyteRow, onValueChange: (String) -> Unit, onRemove: () -> Unit) {
    var text by remember(row) { mutableStateOf(row.printedValue?.toString() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RareTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            label = row.displayName,
            placeholder = if (row.printedUnit != null) row.printedUnit else null,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Clear,
                contentDescription = stringResource(R.string.import_remove_row),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Vitals read from the same document plus the medicines that belong to it (bills only). */
@Composable
internal fun ExtrasSection(state: ImportUiState.Review, viewModel: ImportViewModel) {
    val spacing = MyHealthTokens.spacing
    val vitals = state.draft.document.vitals
    if (vitals.isNotEmpty()) {
        Spacer(Modifier.height(spacing.md))
        Text(
            stringResource(R.string.import_vitals, vitals.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        RareCard(inset = true) {
            vitals.forEach { vital ->
                Text(
                    "${vital.kind.label()}: ${vital.primary}${vital.secondary?.let { "/$it" } ?: ""} ${vital.unit.orEmpty()}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (state.medicines.isEmpty()) return
    Spacer(Modifier.height(spacing.md))
    Text(
        stringResource(R.string.import_medicines, state.medicines.size),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.import_medicines_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    state.medicines.forEachIndexed { index, medicine ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RareCheckbox(
                checked = state.selectedMedicines.contains(index),
                onCheckedChange = { viewModel.toggleMedicine(index) },
            )
            Spacer(Modifier.width(12.dp))
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
