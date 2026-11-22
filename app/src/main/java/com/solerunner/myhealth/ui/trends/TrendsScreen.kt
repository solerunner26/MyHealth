package com.solerunner.myhealth.ui.trends

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareBadge
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareChip
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareEmptyState
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RareSearchField
import com.solerunner.myhealth.design.rare.RareTone
import com.solerunner.myhealth.design.components.Sparkline
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.design.theme.NumericTextStyle
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.ValueStatus
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.containerViewModel
import java.util.Locale

@Composable
fun TrendsScreen(container: AppContainer, onOpenTrend: (String) -> Unit) {
    val viewModel = containerViewModel(container) { TrendsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = MyHealthTokens.spacing
    val categories = listOf<AnalyteCategory?>(null) + state.all.map { it.category }.distinct().sortedBy { it.ordinal }

    Column(Modifier.fillMaxSize()) {
        RarePageHeader(
            title = stringResource(R.string.trends_title),
            eyebrow = if (state.all.isEmpty()) null else stringResource(R.string.trends_points, state.all.size),
            modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.md),
        )
        Column(Modifier.padding(horizontal = spacing.screenPadding)) {
            RareSearchField(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                placeholder = stringResource(R.string.trends_search_hint),
            )
            Spacer(Modifier.height(spacing.sm))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                categories.forEach { category ->
                    RareChip(
                        text = category?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                            ?: stringResource(R.string.common_all),
                        selected = state.category == category,
                        onClick = { viewModel.setCategory(category) },
                    )
                }
            }
            Spacer(Modifier.height(spacing.md))
        }

        if (!state.loading && state.all.isEmpty()) {
            RareEmptyState(
                title = stringResource(R.string.trends_empty_title),
                body = stringResource(R.string.trends_empty_body),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(start = spacing.screenPadding, end = spacing.screenPadding, bottom = spacing.xxxl)) {
                item {
                    RareCard(contentPadding = 0.dp) {
                        state.visible.forEachIndexed { index, series ->
                            TrendListRow(series = series, onClick = { onOpenTrend(series.key) })
                            if (index != state.visible.lastIndex) RareDivider(Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendListRow(series: AnalyteSeries, onClick: () -> Unit) {
    val latest = series.latest
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            com.solerunner.myhealth.design.rare.RareListRow(
                title = series.displayName,
                subtitle = stringResource(R.string.trends_points, series.count),
                onClick = onClick,
            )
        }
        series.points.takeIf { it.size >= 2 }?.let {
            Sparkline(values = it.map { point -> point.value }, modifier = Modifier.width(56.dp).padding(horizontal = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 6.dp)) {
            Text(
                latest?.let { formatNumber(it.value) } ?: "—",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = NumericTextStyle.fontFamily,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (latest != null) {
                RareBadge(
                    text = latest.status.label(),
                    tone = when (latest.status) {
                        ValueStatus.IN_RANGE -> RareTone.ACCENT
                        ValueStatus.UNKNOWN -> RareTone.OUTLINE
                        ValueStatus.VERY_HIGH, ValueStatus.VERY_LOW -> RareTone.SOLID
                        else -> RareTone.NEUTRAL
                    },
                )
            }
        }
    }
}

internal fun formatNumber(value: Double): String {
    val text = String.format(Locale.getDefault(), "%.2f", value)
    return text.trimEnd('0').trimEnd('.')
}
