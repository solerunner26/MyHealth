package com.solerunner.myhealth.ui.records

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.components.EmptyState
import com.solerunner.myhealth.design.components.LabeledValue
import com.solerunner.myhealth.design.components.ListRow
import com.solerunner.myhealth.design.components.SearchField
import com.solerunner.myhealth.design.components.SectionHeader
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.containerViewModel

/**
 * Records timeline. Filters are local and instantaneous: search matches title, lab and doctor; the kind
 * chips cover the four kinds people actually import; years come from the record itself, never a fixed list.
 */
@Composable
fun RecordsScreen(
    container: AppContainer,
    onOpenReport: (Long) -> Unit,
    onImport: () -> Unit,
) {
    val viewModel = containerViewModel(container) { RecordsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = MyHealthTokens.spacing
    val kinds = listOf(null, ReportKind.LAB, ReportKind.PRESCRIPTION, ReportKind.PHARMACY_BILL, ReportKind.IMAGING)

    Column(Modifier.fillMaxSize().padding(horizontal = spacing.screenPadding)) {
        Spacer(Modifier.height(spacing.md))
        Text(stringResource(R.string.records_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(spacing.md))
        SearchField(
            query = state.filter.query,
            onQueryChange = viewModel::setQuery,
            placeholder = stringResource(R.string.records_search_hint),
        )
        Spacer(Modifier.height(spacing.sm))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            FilterChip(
                selected = state.filter.year == null,
                onClick = { viewModel.setYear(null) },
                label = { Text(stringResource(R.string.common_all)) },
            )
            state.years.forEach { year ->
                FilterChip(
                    selected = state.filter.year == year,
                    onClick = { viewModel.setYear(year) },
                    label = { Text(year.toString()) },
                )
            }
        }
        Spacer(Modifier.height(spacing.xs))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            kinds.forEach { kind ->
                FilterChip(
                    selected = state.filter.kind == kind,
                    onClick = { viewModel.setKind(kind) },
                    label = { Text(kind?.label() ?: stringResource(R.string.common_all)) },
                )
            }
        }
        Spacer(Modifier.height(spacing.sm))

        if (!state.loading && state.all.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.records_empty_title),
                body = stringResource(R.string.records_empty_body),
                actionLabel = stringResource(R.string.import_pick_file),
                onAction = onImport,
            )
        } else if (!state.loading && state.visible.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.records_no_results),
                body = stringResource(R.string.records_search_hint),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = spacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                state.visible.groupBy { it.sampleDate.year }.forEach { (year, reports) ->
                    item(key = "year-$year") {
                        SectionHeader(
                            title = year.toString(),
                            subtitle = stringResource(R.string.records_count, reports.size),
                        )
                    }
                    items(reports, key = { it.id }) { report ->
                        ListRow(
                            title = report.title,
                            subtitle = "${report.sampleDate} · ${report.kind.label()}",
                            trailing = report.labName,
                            onClick = { onOpenReport(report.id) },
                        )
                    }
                }
            }
        }
        if (!state.loading) {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(bottom = spacing.md)) {
                Text(stringResource(R.string.import_pick_file))
            }
        }
    }
}

/** Display-only metadata block reused by the report detail screen. */
@Composable
fun ReportMeta(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    LabeledValue(label = label, value = value)
}
