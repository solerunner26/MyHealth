package com.solerunner.myhealth.ui.records

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareChip
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareEmptyState
import com.solerunner.myhealth.design.rare.RareIconButton
import com.solerunner.myhealth.design.rare.RareListRow
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RareSearchField
import com.solerunner.myhealth.design.rare.RareSectionLabel
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.ui.common.label
import com.solerunner.myhealth.ui.containerViewModel

/**
 * Records timeline. Search and filters are local and instant; the list is grouped by year inside one
 * hairline card so the rare "one record, one row" rhythm survives dense data.
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
    var collapsedYears by remember { mutableStateOf(setOf<Int>()) }
    val kinds = listOf(null, ReportKind.LAB, ReportKind.PRESCRIPTION, ReportKind.PHARMACY_BILL, ReportKind.IMAGING)

    Column(Modifier.fillMaxSize()) {
        RarePageHeader(
            title = stringResource(R.string.records_title),
            eyebrow = if (state.all.isEmpty()) null else stringResource(R.string.records_count, state.all.size),
            modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.md),
            actions = {
                RareIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.import_pick_file),
                    onClick = onImport,
                )
            },
        )
        Column(Modifier.padding(horizontal = spacing.screenPadding)) {
            RareSearchField(
                query = state.filter.query,
                onQueryChange = viewModel::setQuery,
                placeholder = stringResource(R.string.records_search_hint),
            )
            Spacer(Modifier.height(spacing.sm))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                RareChip(
                    text = stringResource(R.string.common_all),
                    selected = state.filter.year == null && state.filter.kind == null,
                    onClick = {
                        viewModel.setYear(null)
                        viewModel.setKind(null)
                    },
                )
                state.years.forEach { year ->
                    RareChip(
                        text = year.toString(),
                        selected = state.filter.year == year,
                        onClick = { viewModel.setYear(if (state.filter.year == year) null else year) },
                    )
                }
            }
            Spacer(Modifier.height(spacing.sm))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                kinds.forEach { kind ->
                    RareChip(
                        text = kind?.label() ?: stringResource(R.string.common_all),
                        selected = state.filter.kind == kind,
                        onClick = { viewModel.setKind(kind) },
                    )
                }
            }
            Spacer(Modifier.height(spacing.md))
        }

        when {
            !state.loading && state.all.isEmpty() -> RareEmptyState(
                title = stringResource(R.string.records_empty_title),
                body = stringResource(R.string.records_empty_body),
                actionLabel = stringResource(R.string.import_pick_file),
                onAction = onImport,
            )

            !state.loading && state.visible.isEmpty() -> RareEmptyState(
                title = stringResource(R.string.records_no_results),
                body = stringResource(R.string.records_search_hint),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(start = spacing.screenPadding, end = spacing.screenPadding, bottom = spacing.xxxl),
            ) {
                state.visible.groupBy { it.sampleDate.year }.forEach { (year, reports) ->
                    item(key = "year-$year") {
                        com.solerunner.myhealth.design.rare.RareFolderGroup(
                            title = year.toString(),
                            count = reports.size,
                            expanded = year !in collapsedYears,
                            onToggle = {
                                collapsedYears = if (year in collapsedYears) collapsedYears - year else collapsedYears + year
                            },
                        ) {
                            RareCard(contentPadding = 0.dp) {
                                reports.forEachIndexed { index, report ->
                                    Column(Modifier.padding(horizontal = 14.dp)) {
                                        RareListRow(
                                            title = report.title,
                                            subtitle = "${report.sampleDate} · ${report.kind.label()}",
                                            trailing = report.labName,
                                            onClick = { onOpenReport(report.id) },
                                        )
                                    }
                                    if (index != reports.lastIndex) RareDivider(Modifier.padding(horizontal = 14.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(spacing.sm))
                    }
                }
            }
        }
    }
}

/** Display-only metadata block used by the report detail screen. */
@Composable
fun ReportMeta(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    com.solerunner.myhealth.design.rare.RareKeyValue(label = label, value = value)
}
