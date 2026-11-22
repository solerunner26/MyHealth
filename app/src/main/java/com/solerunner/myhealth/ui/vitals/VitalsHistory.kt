package com.solerunner.myhealth.ui.vitals

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.ui.common.displayWithUnit
import com.solerunner.myhealth.ui.common.label

@Composable
fun VitalsHistory(measurements: List<Measurement>, onDelete: (Measurement) -> Unit) {
    measurements.forEachIndexed { index, measurement ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    measurement.customLabel ?: measurement.kind.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(measurement.displayWithUnit(), style = MaterialTheme.typography.bodyLarge)
                if (measurement.note.isNotBlank()) {
                    Text(
                        measurement.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(measurement.recordedDay.toString(), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { onDelete(measurement) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete))
            }
        }
        if (index != measurements.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
