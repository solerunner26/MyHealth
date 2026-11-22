package com.solerunner.myhealth.ui.records

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.solerunner.myhealth.design.rare.RareButton
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareSheet
import com.solerunner.myhealth.design.rare.RareTextField
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.LabResult

/** Corrects a parsed result after import; the value is reclassified against the catalogue on save. */
@Composable
fun EditResultSheet(
    result: LabResult,
    onDismiss: () -> Unit,
    onSave: (LabResult) -> Unit,
) {
    var value by remember(result) { mutableStateOf(result.value?.toString().orEmpty()) }
    var unit by remember(result) { mutableStateOf(result.unit.orEmpty()) }
    var low by remember(result) { mutableStateOf(result.refLow?.toString().orEmpty()) }
    var high by remember(result) { mutableStateOf(result.refHigh?.toString().orEmpty()) }
    val spacing = MyHealthTokens.spacing

    RareSheet(onDismiss = onDismiss) {
        Text(result.displayName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(spacing.md))
        RareTextField(
            value = value,
            onValueChange = { value = it },
            label = stringResource(R.string.vitals_value),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.height(spacing.sm))
        RareTextField(value = unit, onValueChange = { unit = it }, label = stringResource(R.string.import_unit))
        Spacer(Modifier.height(spacing.sm))
        Row {
            Column(Modifier.weight(1f)) {
                RareTextField(
                    value = low,
                    onValueChange = { low = it },
                    label = stringResource(R.string.records_ref_low),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Spacer(Modifier.width(spacing.sm))
            Column(Modifier.weight(1f)) {
                RareTextField(
                    value = high,
                    onValueChange = { high = it },
                    label = stringResource(R.string.records_ref_high),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
        Spacer(Modifier.height(spacing.md))
        RareDivider()
        Spacer(Modifier.height(spacing.md))
        RareButton(
            text = stringResource(R.string.common_save),
            onClick = {
                onSave(
                    result.copy(
                        value = value.trim().replace(',', '.').toDoubleOrNull(),
                        unit = unit.trim().takeIf { it.isNotEmpty() },
                        refLow = low.trim().replace(',', '.').toDoubleOrNull(),
                        refHigh = high.trim().replace(',', '.').toDoubleOrNull(),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
