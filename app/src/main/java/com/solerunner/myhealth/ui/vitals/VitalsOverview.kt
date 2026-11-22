package com.solerunner.myhealth.ui.vitals

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.LineChart
import com.solerunner.myhealth.design.components.MetricTile
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.SeriesPoint
import com.solerunner.myhealth.domain.model.ValueStatus
import com.solerunner.myhealth.ui.common.defaultUnit
import com.solerunner.myhealth.ui.common.displayWithUnit
import com.solerunner.myhealth.ui.common.formatNumber
import com.solerunner.myhealth.ui.common.label

/** Kind filter, latest/7-day tiles and a trend line for the selected vital. */
@Composable
fun VitalsOverview(state: VitalsUiState, onSelect: (MeasurementKind?) -> Unit) {
    val spacing = MyHealthTokens.spacing
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        FilterChip(
            selected = state.selectedKind == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.common_all)) },
        )
        state.kinds.forEach { kind ->
            FilterChip(
                selected = state.selectedKind == kind,
                onClick = { onSelect(kind) },
                label = { Text(kind.label()) },
            )
        }
    }
    Spacer(Modifier.height(spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        MetricTile(
            label = stringResource(R.string.vitals_latest),
            value = state.latest?.displayWithUnit() ?: "—",
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            label = stringResource(R.string.vitals_avg_7),
            value = state.average7?.let { formatNumber(it) } ?: "—",
            unit = state.selectedKind?.defaultUnit(),
            modifier = Modifier.weight(1f),
        )
    }
    val chartValues = state.visible.filter { it.kind != MeasurementKind.BLOOD_PRESSURE }
    if (chartValues.size >= 2) {
        Spacer(Modifier.height(spacing.md))
        LineChart(
            points = chartValues.sortedBy { it.recordedDay }.map { measurement ->
                SeriesPoint(
                    date = measurement.recordedDay,
                    value = measurement.primaryValue,
                    status = ValueStatus.UNKNOWN,
                    source = measurement.source,
                )
            },
            referenceLow = null,
            referenceHigh = null,
            unit = state.selectedKind?.defaultUnit(),
            contentDescription = stringResource(R.string.vitals_title),
        )
        Text(
            stringResource(R.string.chart_latest, state.latest?.let { formatNumber(it.primaryValue) } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
