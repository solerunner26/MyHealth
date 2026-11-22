package com.solerunner.myhealth.ui.vitals

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.solerunner.myhealth.R
import com.solerunner.myhealth.core.util.Dates
import com.solerunner.myhealth.design.components.ChoiceDialog
import com.solerunner.myhealth.design.components.DateField
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.ui.common.defaultUnit
import com.solerunner.myhealth.ui.common.label
import java.time.LocalDate

/**
 * Manual vital entry. Range checks are intentionally loose (the app is a record, not a monitor) but
 * nonsense values are rejected so a typo cannot poison the trend chart.
 */
@Composable
fun AddVitalDialog(
    onDismiss: () -> Unit,
    onSave: (MeasurementKind, Double, Double?, LocalDate, String, String?) -> Unit,
) {
    var kind by remember { mutableStateOf(MeasurementKind.BLOOD_PRESSURE) }
    var primary by remember { mutableStateOf("") }
    var secondary by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var customLabel by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(Dates.today()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showKinds by remember { mutableStateOf(false) }
    val invalidText = stringResource(R.string.vitals_invalid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vitals_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.vitals_kind), modifier = Modifier.weight(1f))
                    TextButton(onClick = { showKinds = true }) { Text(kind.label()) }
                }
                OutlinedTextField(
                    value = primary,
                    onValueChange = { primary = it },
                    label = { Text(stringResource(R.string.vitals_value)) },
                    suffix = { kind.defaultUnit()?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind.isPaired) {
                    Spacer(Modifier.height(MyHealthTokens.spacing.sm))
                    OutlinedTextField(
                        value = secondary,
                        onValueChange = { secondary = it },
                        label = { Text(stringResource(R.string.vitals_value_second)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind == MeasurementKind.CUSTOM) {
                    Spacer(Modifier.height(MyHealthTokens.spacing.sm))
                    OutlinedTextField(
                        value = customLabel,
                        onValueChange = { customLabel = it },
                        label = { Text(stringResource(R.string.vitals_custom_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DateField(label = stringResource(R.string.vitals_date), date = day, onChange = { day = it ?: day })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.vitals_note)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val first = primary.trim().replace(',', '.').toDoubleOrNull()
                    val second = secondary.trim().replace(',', '.').toDoubleOrNull()
                    when {
                        first == null -> error = invalidText
                        kind.isPaired && second == null -> error = invalidText
                        first < 0 || first > 1000 || (kind.isPaired && (second ?: 0.0) > 500) -> error = invalidText
                        else -> onSave(kind, first, if (kind.isPaired) second else null, day, note.trim(), customLabel.trim())
                    }
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )

    if (showKinds) {
        ChoiceDialog(
            title = stringResource(R.string.vitals_kind),
            options = MeasurementKind.entries,
            selected = kind,
            onSelect = { kind = it },
            onDismiss = { showKinds = false },
            label = { it.label() },
        )
    }
}
