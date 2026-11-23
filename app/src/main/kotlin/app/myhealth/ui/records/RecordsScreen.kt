package app.myhealth.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthChipRow
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.components.MyHealthTextField
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.AnalyteTrend
import app.myhealth.domain.analytics.TrendEngine
import app.myhealth.domain.catalogue.Panel
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.ReportType
import app.myhealth.ui.Session
import app.myhealth.ui.common.analyteName
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
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class RecordsState(
    val loading: Boolean = true,
    val reports: List<Report> = emptyList(),
    val trends: List<AnalyteTrend> = emptyList(),
    val windowStart: LocalDate? = null,
    /** reportId → how many of its results are NOT IN RANGE (shown as a red label on the report). */
    val outOfRange: Map<String, Int> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(session: Session) : ViewModel() {
    val state: StateFlow<RecordsState> = session.activeMember.filterNotNull().flatMapLatest { m ->
        combine(session.stack.records.observeReports(m.id), session.stack.records.observeResults(m.id), session.container.preferences.state) { reports, results, prefs ->
            val today = LocalDate.now(session.container.clock)
            val start = TrendEngine.windowStart(today, prefs.historyYears)
            val age = m.ageYears(today)
            val abnormal = results.filter { r ->
                val a = app.myhealth.domain.catalogue.AnalyteCatalogue.byId(r.analyteId) ?: return@filter false
                val ref = a.rangeFor(m.sex, age)
                TrendEngine.status(a, r.value, r.refLow ?: ref?.low, r.refHigh ?: ref?.high).isAbnormal
            }.mapNotNull { it.reportId }.groupingBy { it }.eachCount()
            RecordsState(false, reports.filter { !it.reportDate.isBefore(start) }, TrendEngine.all(results, today, prefs.historyYears, m.sex, age), start, abnormal)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsState())
}

enum class RecordsTab { Reports, Results }

/** Filtering happens here, on localised text, so searching works in every UI language. */
@Composable
fun RecordsContent(state: RecordsState, onOpenReport: (String) -> Unit, onOpenTrend: (String) -> Unit, onAdd: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(RecordsTab.Reports) }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<ReportType?>(null) }
    var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
    val sp = MyHealthTheme.spacing
    Column(Modifier.fillMaxSize()) {
        MyHealthChipRow(RecordsTab.entries, tab, { if (it == RecordsTab.Reports) stringResource(R.string.records_tab_reports) else stringResource(R.string.records_tab_results) },
            { tab = it }, Modifier.padding(horizontal = sp.gutter))
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        MyHealthTextField(query, { query = it }, stringResource(R.string.records_search), Modifier.padding(horizontal = sp.gutter, vertical = sp.sm).testTag("recordsSearch"), leadingIcon = MyHealthIcons.Search)
        val q = query.trim().lowercase()
        when (tab) {
            RecordsTab.Reports -> {
                if (state.reports.isEmpty()) {
                    MyHealthEmptyState(MyHealthIcons.Records, stringResource(R.string.records_empty_title), stringResource(R.string.records_empty_body),
                        actionLabel = stringResource(R.string.home_add_document), onAction = onAdd); return@Column
                }
                val types = state.reports.map { it.type }.distinct()
                if (types.size > 1) MyHealthChipRow(listOf<ReportType?>(null) + types, type, { it?.let { t -> label(t) } ?: stringResource(R.string.records_filter_all) }, { type = it },
                    Modifier.padding(horizontal = sp.gutter))
                val typeLabels = ReportType.entries.associateWith { label(it) }
                val dates = state.reports.associate { it.id to formatDate(it.reportDate) }
                val shown = state.reports.filter { r ->
                    (type == null || r.type == type) && (q.isEmpty() || listOfNotNull(r.title, r.labName, r.doctorName, typeLabels[r.type], dates[r.id], r.reportDate.toString())
                        .any { it.lowercase().contains(q) })
                }
                ReportList(shown, state.outOfRange, onOpenReport) { query = ""; type = null }
            }
            RecordsTab.Results -> {
                if (state.trends.isEmpty()) {
                    MyHealthEmptyState(MyHealthIcons.Labs, stringResource(R.string.records_results_empty_title), stringResource(R.string.records_results_empty_body),
                        actionLabel = stringResource(R.string.home_add_document), onAction = onAdd); return@Column
                }
                val panels = state.trends.map { it.analyte.panel }.distinct()
                if (panels.size > 1) MyHealthChipRow(listOf<Panel?>(null) + panels, panel, { it?.let { p -> label(p) } ?: stringResource(R.string.records_filter_all) }, { panel = it },
                    Modifier.padding(horizontal = sp.gutter))
                val names = state.trends.associate { it.analyte.id to analyteName(it.analyte.id) }
                val shown = state.trends.filter { t ->
                    (panel == null || t.analyte.panel == panel) &&
                        (q.isEmpty() || names[t.analyte.id]!!.lowercase().contains(q) || t.analyte.name.lowercase().contains(q) || t.analyte.aliases.any { it.contains(q) })
                }
                if (shown.isEmpty()) NoMatch { query = ""; panel = null } else
                    LazyColumn(Modifier.fillMaxSize().testTag("resultsList"), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                        items(shown, key = { it.analyte.id }) { t ->
                            MyHealthCard(onClick = { onOpenTrend(t.analyte.id) }) {
                                MyHealthListRow(names[t.analyte.id]!!, subtitle = stringResource(R.string.join_dot, analyteValue(t.analyte.id, t.latest.value, t.latest.comparator), formatDate(t.latest.date)),
                                    leadingIcon = MyHealthIcons.Labs) { MyHealthStatusBadge(label(t.latest.status), t.latest.status.kind()) }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun ReportList(reports: List<Report>, outOfRange: Map<String, Int>, onOpen: (String) -> Unit, onClear: () -> Unit) {
    if (reports.isEmpty()) { NoMatch(onClear); return }
    val sp = MyHealthTheme.spacing
    // Chronology (item 4): reports sit on the date the test was done, not the day the file was added, newest first,
    // under month headings.
    val byMonth = reports.sortedWith(compareByDescending<Report> { it.reportDate }.thenByDescending { it.createdAt }).groupBy { java.time.YearMonth.from(it.reportDate) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    LazyColumn(Modifier.fillMaxSize().testTag("recordsList"), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
        byMonth.forEach { (month, list) ->
        item(key = "m$month") {
            androidx.compose.material3.Text(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", locale)), style = MyHealthTheme.typography.overline,
                color = MyHealthTheme.colors.textSecondary, modifier = Modifier.padding(top = sp.sm).testTag("month_$month"))
        }
        items(list, key = { it.id }) { r ->
            MyHealthCard(onClick = { onOpen(r.id) }, modifier = Modifier.testTag("report_${r.id}")) {
                val base = stringResource(R.string.join_dot, label(r.type), formatDate(r.reportDate))
                val bad = outOfRange[r.id] ?: 0
                MyHealthListRow(r.title, subtitle = r.labName?.let { stringResource(R.string.join_dot, base, it) } ?: base,
                    leadingIcon = if (r.type == ReportType.Prescription) MyHealthIcons.Medicines else MyHealthIcons.Document) {
                    if (bad > 0) MyHealthStatusBadge(androidx.compose.ui.res.pluralStringResource(R.plurals.records_out_of_range, bad, bad),
                        app.myhealth.core.designsystem.components.StatusKind.High, Modifier.testTag("outOfRange_${r.id}"))
                }
            }
        }
        }
    }
}

@Composable
private fun NoMatch(onClear: () -> Unit) = MyHealthEmptyState(MyHealthIcons.Search, stringResource(R.string.records_no_match_title), stringResource(R.string.records_no_match_body),
    actionLabel = stringResource(R.string.records_clear), onAction = onClear)

/** Back-compat wrapper used by earlier tests. */
@Composable
fun RecordsList(state: RecordsState, onOpen: (String) -> Unit, onAdd: () -> Unit) = RecordsContent(state, onOpen, {}, onAdd)

