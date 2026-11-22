package app.myhealth.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myhealth.R
import app.myhealth.core.designsystem.components.ChartPoint
import app.myhealth.core.designsystem.components.ChartSeries
import app.myhealth.core.designsystem.components.MyHealthBandChart
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthChartLegend
import app.myhealth.core.designsystem.components.MyHealthDisclaimer
import app.myhealth.core.designsystem.components.MyHealthDivider
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIconButton
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthMetricTile
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.AnalyteTrend
import app.myhealth.domain.analytics.TrendEngine
import app.myhealth.domain.catalogue.Direction
import app.myhealth.domain.format.ValueFormat
import app.myhealth.domain.model.Report
import app.myhealth.ui.Session
import app.myhealth.ui.common.analyteName
import app.myhealth.ui.common.analyteRange
import app.myhealth.ui.common.analyteValue
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.kind
import app.myhealth.ui.common.label
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class TrendsState(val loading: Boolean = true, val trends: List<AnalyteTrend> = emptyList(), val historyYears: Int = 5)

class TrendsViewModel(session: Session) : ViewModel() {
    val state: StateFlow<TrendsState> = session.snapshot.map { s ->
        val today = LocalDate.now(session.container.clock)
        TrendsState(false, TrendEngine.all(s.results, today, s.historyYears, s.member.sex, s.member.ageYears(today)), s.historyYears)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsState())
}

/** Lab trends grouped by panel. [vitalsSection] lets Phase 7 append vitals without coupling. */
@Composable
fun TrendsContent(state: TrendsState, onOpen: (String) -> Unit, onAdd: () -> Unit, vitalsSection: (@Composable () -> Unit)? = null) {
    val sp = MyHealthTheme.spacing
    when {
        state.loading -> MyHealthLoadingState(stringResource(R.string.state_loading))
        state.trends.isEmpty() && vitalsSection == null -> MyHealthEmptyState(MyHealthIcons.Trends, stringResource(R.string.trends_empty_title), stringResource(R.string.trends_empty_body),
            actionLabel = stringResource(R.string.home_add_document), onAction = onAdd)
        else -> LazyColumn(Modifier.fillMaxSize().testTag("trendsList"), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            item { MyHealthInfoBanner(stringResource(R.string.trends_window, state.historyYears), icon = MyHealthIcons.Date) }
            vitalsSection?.let { item { it() } }
            if (state.trends.isEmpty()) item {
                MyHealthEmptyState(MyHealthIcons.Labs, stringResource(R.string.trends_empty_title), stringResource(R.string.trends_empty_body),
                    actionLabel = stringResource(R.string.home_add_document), onAction = onAdd)
            }
            state.trends.groupBy { it.analyte.panel }.forEach { (panel, list) ->
                item(key = "h_$panel") { MyHealthSectionHeader(label(panel)) }
                items(list, key = { it.analyte.id }) { t ->
                    MyHealthCard(onClick = { onOpen(t.analyte.id) }, modifier = Modifier.testTag("trend_${t.analyte.id}")) {
                        MyHealthListRow(analyteName(t.analyte.id), subtitle = stringResource(R.string.join_dot, analyteValue(t.analyte.id, t.latest.value, t.latest.comparator), label(t.direction))) {
                            MyHealthStatusBadge(label(t.latest.status), t.latest.status.kind())
                        }
                    }
                }
            }
        }
    }
}

data class TrendDetailState(val loading: Boolean = true, val trend: AnalyteTrend? = null, val reports: Map<String, Report> = emptyMap())

@OptIn(ExperimentalCoroutinesApi::class)
class TrendDetailViewModel(session: Session, analyteId: String) : ViewModel() {
    val state: StateFlow<TrendDetailState> = session.activeMember.filterNotNull().flatMapLatest { m ->
        combine(session.stack.records.observeResults(m.id), session.stack.records.observeReports(m.id), session.container.preferences.state) { results, reports, prefs ->
            val today = LocalDate.now(session.container.clock)
            TrendDetailState(false, TrendEngine.trend(results, analyteId, today, prefs.historyYears, m.sex, m.ageYears(today)), reports.associateBy { it.id })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendDetailState())
}

@Composable
fun TrendDetailScreen(state: TrendDetailState, analyteId: String, onBack: () -> Unit, onExport: (AnalyteTrend) -> Unit, onOpenReport: (String) -> Unit) {
    val sp = MyHealthTheme.spacing; val c = MyHealthTheme.colors
    val name = analyteName(analyteId)
    Column(Modifier.fillMaxSize().background(c.background).testTag("trendDetail")) {
        MyHealthTopBar(name, onBack = onBack, backLabel = stringResource(R.string.action_back)) {
            state.trend?.let { t -> MyHealthIconButton(MyHealthIcons.Csv, stringResource(R.string.trend_export), { onExport(t) }, Modifier.testTag("exportCsv")) }
        }
        val t = state.trend
        when {
            state.loading -> MyHealthLoadingState(stringResource(R.string.state_loading))
            t == null -> MyHealthEmptyState(MyHealthIcons.Trends, name, stringResource(R.string.trend_not_found))
            else -> LazyColumn(Modifier.fillMaxSize().testTag("trendDetailList"), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MyHealthStatusBadge(label(t.latest.status), t.latest.status.kind()); Spacer(Modifier.width(sp.sm))
                        MyHealthStatusBadge(label(t.direction), t.direction.kind())
                    }
                }
                item {
                    MyHealthCard {
                        val d = t.analyte.decimals
                        val first = t.points.first(); val last = t.latest
                        val cd = stringResource(R.string.trend_chart_cd, name, t.points.size, formatDate(first.date), formatDate(last.date),
                            analyteValue(t.analyte.id, last.value, last.comparator), label(last.status))
                        MyHealthBandChart(
                            series = listOf(ChartSeries(t.points.map { ChartPoint(it.date.toEpochDay().toDouble(), it.value, it.status.isAbnormal) })),
                            contentDescription = cd, bandLow = t.bandLow, bandHigh = t.bandHigh,
                            yLabelTop = t.analyte.canonicalUnit, xLabelStart = formatDate(first.date), xLabelEnd = formatDate(last.date),
                            modifier = Modifier.testTag("chart"),
                        )
                        Spacer(Modifier.padding(top = sp.sm))
                        MyHealthChartLegend(stringResource(R.string.legend_in_range), stringResource(R.string.legend_out_of_range),
                            if (t.bandLow != null || t.bandHigh != null) stringResource(R.string.legend_band) else null)
                        if (t.bandLow != null || t.bandHigh != null) Text(stringResource(R.string.trend_range, ValueFormat.range(t.bandLow, t.bandHigh, d)),
                            style = MyHealthTheme.typography.caption, color = c.textSecondary)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                        MyHealthMetricTile(stringResource(R.string.trend_latest), ValueFormat.withComparator(t.latest.value, t.analyte.decimals, t.latest.comparator),
                            Modifier.weight(1f), unit = t.analyte.canonicalUnit.takeIf { it != "ratio" }, caption = formatDate(t.latest.date))
                        MyHealthMetricTile(stringResource(R.string.trend_previous), t.previous?.let { ValueFormat.withComparator(it.value, t.analyte.decimals, it.comparator) } ?: "—",
                            Modifier.weight(1f), unit = t.analyte.canonicalUnit.takeIf { t.previous != null && it != "ratio" }, caption = t.previous?.let { formatDate(it.date) })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                        MyHealthMetricTile(stringResource(R.string.trend_change), t.absoluteChange?.let { ValueFormat.signed(it, t.analyte.decimals) } ?: "—", Modifier.weight(1f),
                            caption = t.percentChange?.let { ValueFormat.percent(it) })
                        MyHealthMetricTile(stringResource(R.string.trend_rate), t.ratePerMonth?.let { ValueFormat.signed(it, t.analyte.decimals + 1) } ?: "—", Modifier.weight(1f),
                            caption = t.percentPerMonth?.let { ValueFormat.percent(it) })
                    }
                }
                item {
                    MyHealthInfoBanner(stringResource(when (t.analyte.direction) {
                        Direction.LowerIsBetter -> R.string.trend_direction_lower; Direction.HigherIsBetter -> R.string.trend_direction_higher; Direction.InRange -> R.string.trend_direction_range
                    }), icon = MyHealthIcons.Info)
                }
                item { MyHealthSectionHeader(stringResource(R.string.trend_history), Modifier.testTag("historyHeader")) }
                items(t.points.reversed(), key = { it.date.toString() }) { p ->
                    val report = p.reportId?.let { state.reports[it] }
                    MyHealthCard(onClick = report?.let { r -> { onOpenReport(r.id) } }) {
                        MyHealthListRow(analyteValue(t.analyte.id, p.value, p.comparator),
                            subtitle = listOfNotNull(formatDate(p.date), analyteRange(t.analyte.id, p.refLow, p.refHigh).takeIf { it.isNotBlank() }?.let { stringResource(R.string.trend_range, it) },
                                report?.let { stringResource(R.string.trend_from_report, it.title) } ?: stringResource(R.string.trend_manual)).joinToString("\n")) {
                            MyHealthStatusBadge(label(p.status), p.status.kind())
                        }
                    }
                }
                item { MyHealthDivider() }
                item { MyHealthDisclaimer(stringResource(R.string.disclaimer_short)) }
            }
        }
    }
}

