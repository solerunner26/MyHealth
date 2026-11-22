package com.solerunner.myhealth.ui.trends

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.EmptyState
import com.solerunner.myhealth.design.components.ListRow
import com.solerunner.myhealth.design.components.SearchField
import com.solerunner.myhealth.design.components.Sparkline
import com.solerunner.myhealth.design.components.StatusPill
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.design.theme.NumericTextStyle
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.common.tone
import com.solerunner.myhealth.ui.containerViewModel
import java.util.Locale

@Composable
fun TrendsScreen(container: AppContainer, onOpenTrend: (String) -> Unit) {
    val viewModel = containerViewModel(container) { TrendsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = MyHealthTokens.spacing
    val categories = listOf<AnalyteCategory?>(null) + state.all.map { it.category }.distinct().sortedBy { it.ordinal }

    Column(Modifier.fillMaxSize().padding(horizontal = spacing.screenPadding)) {
        Spacer(Modifier.height(spacing.md))
        Text(stringResource(R.string.trends_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(spacing.md))
        SearchField(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            placeholder = stringResource(R.string.trends_search_hint),
        )
        Spacer(Modifier.height(spacing.sm))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            categories.forEach { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { viewModel.setCategory(category) },
                    label = { Text(category?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.common_all)) },
                )
            }
        }

        if (!state.loading && state.all.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.trends_empty_title),
                body = stringResource(R.string.trends_empty_body),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = spacing.xxxl)) {
                items(state.visible, key = { it.key }) { series ->
                    TrendListRow(series = series, onClick = { onOpenTrend(series.key) })
                }
            }
        }
    }
}

@Composable
private fun TrendListRow(series: AnalyteSeries, onClick: () -> Unit) {
    val latest = series.latest
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            ListRow(
                title = series.displayName,
                subtitle = stringResource(R.string.trends_points, series.count),
                onClick = onClick,
            )
        }
        series.points.takeIf { it.size >= 2 }?.let {
            Sparkline(values = it.map { point -> point.value }, modifier = Modifier.width(64.dp).padding(horizontal = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 4.dp)) {
            Text(
                latest?.let { formatNumber(it.value) } ?: "—",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = NumericTextStyle.fontFamily,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (latest != null) StatusPill(text = latest.status.label(), tone = latest.status.tone())
        }
    }
}

internal fun formatNumber(value: Double): String {
    val text = String.format(Locale.getDefault(), "%.2f", value)
    return text.trimEnd('0').trimEnd('.')
}
