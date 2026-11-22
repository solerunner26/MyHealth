package com.solerunner.myhealth.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.design.components.StatusPill
import com.solerunner.myhealth.design.theme.NumericTextStyle
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.common.tone
import java.util.Locale

/** One analyte line of a report: name, printed value + unit, reference text, status pill. */
@Composable
fun ResultRow(result: LabResult, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(result.displayName, style = MaterialTheme.typography.bodyLarge)
            val range = result.refText ?: when {
                result.refLow != null && result.refHigh != null -> "${formatValue(result.refLow)} – ${formatValue(result.refHigh)}"
                result.refHigh != null -> "< ${formatValue(result.refHigh)}"
                result.refLow != null -> "> ${formatValue(result.refLow)}"
                else -> null
            }
            if (range != null) {
                Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "${result.value?.let { formatValue(it) } ?: "—"} ${result.unit.orEmpty()}".trim(),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = NumericTextStyle.fontFamily),
            textAlign = TextAlign.End,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        StatusPill(text = result.status.label(), tone = result.status.tone())
    }
}

private fun formatValue(value: Double): String {
    val text = String.format(Locale.getDefault(), "%.2f", value)
    return text.trimEnd('0').trimEnd('.')
}
