package com.solerunner.myhealth.ui.trends

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.StatusPill
import com.solerunner.myhealth.design.components.TableContainer
import com.solerunner.myhealth.design.components.ThinDivider
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.common.tone

/** The longitudinal table: one row per report date, newest first, exactly as AGENTS.md specifies. */
@Composable
fun TrendHistoryTable(series: AnalyteSeries) {
    val spacing = MyHealthTokens.spacing
    TableContainer {
        series.rowsNewestFirst.forEachIndexed { index, point ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(point.date.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.2f))
                Text(
                    "${formatNumber(point.value)} ${series.unit.orEmpty()}".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    sourceLabel(point.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.9f),
                )
                StatusPill(text = point.status.label(), tone = point.status.tone())
            }
            if (index != series.rowsNewestFirst.lastIndex) ThinDivider()
        }
    }
}

@Composable
internal fun sourceLabel(source: DataSource): String = when (source) {
    DataSource.REPORT_IMPORT -> stringResource(R.string.trends_source_report)
    DataSource.MANUAL -> stringResource(R.string.trends_source_manual)
    DataSource.DEVICE_SYNC -> stringResource(R.string.trends_source_device)
}
