package com.solerunner.myhealth.ui.vitals

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.LineChart
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareChip
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareStat
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.SeriesPoint
import com.solerunner.myhealth.domain.model.ValueStatus
import com.solerunner.myhealth.ui.common.defaultUnit
import com.solerunner.myhealth.ui.common.displayWithUnit
import com.solerunner.myhealth.ui.common.formatNumber
import com.solerunner.myhealth.ui.common.label

/** Kind filter, latest and 7-day stats, and a trend line for the selected vital. */
@Composable
fun VitalsOverview(state: VitalsUiState, onSelect: (MeasurementKind?) -> Unit) {
    val spacing = MyHealthTokens.spacing
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        RareChip(
            text = stringResource(R.string.common_all),
            selected = state.selectedKind == null,
            onClick = { onSelect(null) },
        )
        state.kinds.forEach { kind ->
            RareChip(
                text = kind.label(),
                selected = state.selectedKind == kind,
                onClick = { onSelect(kind) },
            )
        }
    }
    Spacer(Modifier.height(spacing.md))
    RareCard {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RareStat(
                label = stringResource(R.string.vitals_latest),
                value = state.latest?.displayWithUnit() ?: "—",
                modifier = Modifier.weight(1f),
            )
            RareStat(
                label = stringResource(R.string.vitals_avg_7),
                value = state.average7?.let { formatNumber(it) } ?: "—",
                unit = state.selectedKind?.defaultUnit(),
                modifier = Modifier.weight(1f),
            )
        }
        val chartValues = state.visible.filter { it.kind != MeasurementKind.BLOOD_PRESSURE }
        if (chartValues.size >= 2) {
            Spacer(Modifier.height(spacing.md))
            RareDivider()
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
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.chart_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
