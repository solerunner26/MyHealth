package com.solerunner.myhealth.ui.trends

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.LineChart
import com.solerunner.myhealth.design.components.MetricTile
import com.solerunner.myhealth.design.components.StatusPill
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.common.tone
import com.solerunner.myhealth.ui.containerViewModel

@Composable
fun TrendDetailScreen(container: AppContainer, analyteKey: String, onBack: () -> Unit) {
    val viewModel = containerViewModel(container, key = "trend-$analyteKey") { TrendDetailViewModel(it, analyteKey) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = MyHealthTokens.spacing
    val shareLabel = stringResource(R.string.common_share)

    LaunchedEffect(state.shareIntent) {
        state.shareIntent?.let { intent ->
            context.startActivity(android.content.Intent.createChooser(intent, shareLabel))
            viewModel.clearShare()
        }
    }

    val series = state.series
    Column(Modifier.fillMaxSize()) {
        com.solerunner.myhealth.design.rare.RarePageHeader(
            title = series?.displayName ?: "",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.md),
            actions = {
                com.solerunner.myhealth.design.rare.RareIconButton(
                    icon = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.trends_export_csv),
                    onClick = viewModel::exportCsv,
                )
            },
        )

        if (series == null) {
            if (!state.loading) {
                Text(stringResource(R.string.trends_empty_body), modifier = Modifier.padding(spacing.screenPadding))
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = spacing.screenPadding, end = spacing.screenPadding, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                LineChart(
                    points = series.points,
                    referenceLow = series.referenceLow,
                    referenceHigh = series.referenceHigh,
                    unit = series.unit,
                    decimals = series.decimals,
                    contentDescription = stringResource(R.string.trends_chart_desc, series.displayName),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    MetricTile(
                        label = stringResource(R.string.trends_latest),
                        value = formatNumber(state.summary.latest ?: 0.0),
                        unit = series.unit,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = stringResource(R.string.trends_change),
                        value = state.summary.delta?.let { (if (it > 0) "+" else "") + formatNumber(it) } ?: "—",
                        unit = series.unit,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = stringResource(R.string.trends_per_month),
                        value = state.summary.perMonth?.let { formatNumber(it) } ?: "—",
                        unit = series.unit,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    series.latest?.let { latest -> StatusPill(text = latest.status.label(), tone = latest.status.tone()) }
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        when (state.summary.improving) {
                            true -> stringResource(R.string.trends_improving)
                            false -> stringResource(R.string.trends_worsening)
                            null -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (series.referenceLow != null || series.referenceHigh != null) {
                    Text(
                        stringResource(R.string.trends_range) + ": " +
                            listOfNotNull(series.referenceLow?.let(::formatNumber), series.referenceHigh?.let(::formatNumber))
                                .joinToString(" – "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(stringResource(R.string.trends_history_title), style = MaterialTheme.typography.titleMedium)
            }
            item { TrendHistoryTable(series) }
        }
    }
}
