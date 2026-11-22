package com.solerunner.myhealth.ui.vitals

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.core.util.Dates
import com.solerunner.myhealth.design.components.DateField
import com.solerunner.myhealth.design.rare.RareBanner
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareChip
import com.solerunner.myhealth.design.rare.RareSheet
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.ui.common.defaultUnit
import com.solerunner.myhealth.ui.common.label
import java.time.LocalDate

/** Manual vital entry in a Rare sheet: kind chips, value fields, date and note. */
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
    val invalidText = stringResource(R.string.vitals_invalid)
    val spacing = MyHealthTokens.spacing

    RareSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.vitals_add), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(spacing.md))
        Text(
            stringResource(R.string.vitals_kind),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MeasurementKind.entries.forEach { option ->
                RareChip(
                    text = option.label(),
                    selected = kind == option,
                    onClick = { kind = option },
                )
            }
        }
        Spacer(Modifier.height(spacing.md))
        RareTextField(
            value = primary,
            onValueChange = { primary = it },
            label = stringResource(R.string.vitals_value),
            placeholder = "0",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailing = kind.defaultUnit()?.let { unit ->
                { Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        )
        if (kind.isPaired) {
            Spacer(Modifier.height(spacing.sm))
            RareTextField(
                value = secondary,
                onValueChange = { secondary = it },
                label = stringResource(R.string.vitals_value_second),
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        if (kind == MeasurementKind.CUSTOM) {
            Spacer(Modifier.height(spacing.sm))
            RareTextField(
                value = customLabel,
                onValueChange = { customLabel = it },
                label = stringResource(R.string.vitals_custom_label),
            )
        }
        Spacer(Modifier.height(spacing.sm))
        DateField(label = stringResource(R.string.vitals_date), date = day, onChange = { day = it ?: day })
        Spacer(Modifier.height(spacing.sm))
        RareTextField(
            value = note,
            onValueChange = { note = it },
            label = stringResource(R.string.vitals_note),
            singleLine = false,
            minHeight = 64,
        )
        if (error != null) {
            Spacer(Modifier.height(spacing.sm))
            RareBanner(text = error!!)
        }
        Spacer(Modifier.height(spacing.md))
        RareButton(
            text = stringResource(R.string.common_save),
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
