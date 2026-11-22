package app.myhealth.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myhealth.core.designsystem.components.appear
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthDisclaimer
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthMetricTile
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthScoreRing
import app.myhealth.core.designsystem.components.MyHealthSecondaryButton
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.AnalyteTrend
import app.myhealth.domain.analytics.FocusEngine
import app.myhealth.domain.analytics.FocusItem
import app.myhealth.domain.analytics.HealthScore
import app.myhealth.domain.analytics.HealthScoreCalculator
import app.myhealth.domain.analytics.TrendEngine
import app.myhealth.domain.format.ValueFormat
import app.myhealth.domain.model.FamilyMember
import app.myhealth.ui.Session
import app.myhealth.ui.common.analyteName
import app.myhealth.ui.common.kind
import app.myhealth.ui.common.label
import app.myhealth.ui.insights.FocusCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val loading: Boolean = true,
    val member: FamilyMember? = null,
    /** Latest and previous reading per vital type, in display order. */
    val vitals: List<Triple<app.myhealth.domain.model.VitalType, app.myhealth.domain.model.Vital, app.myhealth.domain.model.Vital?>> = emptyList(),
    val allTests: List<AnalyteTrend> = emptyList(),
    val hasData: Boolean = false,
    val score: HealthScore? = null,
    val focus: List<FocusItem> = emptyList(),
    val latest: List<AnalyteTrend> = emptyList(),
)

/** Vitals that always have a tile on the dashboard (an invitation to log when there is no reading yet). */
val CORE_VITALS = listOf(
    app.myhealth.domain.model.VitalType.BloodPressure, app.myhealth.domain.model.VitalType.Pulse, app.myhealth.domain.model.VitalType.SpO2,
    app.myhealth.domain.model.VitalType.BloodGlucose, app.myhealth.domain.model.VitalType.Weight, app.myhealth.domain.model.VitalType.Temperature,
    app.myhealth.domain.model.VitalType.RespiratoryRate,
)

val VITAL_ORDER = listOf(
    app.myhealth.domain.model.VitalType.BloodPressure, app.myhealth.domain.model.VitalType.Pulse, app.myhealth.domain.model.VitalType.SpO2,
    app.myhealth.domain.model.VitalType.Weight, app.myhealth.domain.model.VitalType.Bmi, app.myhealth.domain.model.VitalType.Temperature,
    app.myhealth.domain.model.VitalType.BloodGlucose, app.myhealth.domain.model.VitalType.RespiratoryRate,
)

class HomeViewModel(session: Session) : ViewModel() {
    val state: StateFlow<HomeState> = session.snapshot.map { s ->
        val inputs = session.focusInputs(s)
        val trends = TrendEngine.all(s.results, inputs.today, s.historyYears, s.member.sex, s.member.ageYears(inputs.today))
        HomeState(
            loading = false, member = s.member,
            hasData = s.results.isNotEmpty() || s.vitals.isNotEmpty() || s.medicines.isNotEmpty() || s.treatments.isNotEmpty() || s.activity.isNotEmpty(),
            score = HealthScoreCalculator.calculate(inputs),
            focus = FocusEngine.evaluate(inputs),
            // abnormal first, then key markers, then most recent
            // not in range first, then slightly off, then in range (same order as tests)
            vitals = VITAL_ORDER.mapNotNull { t ->
                val list = s.vitals.filter { it.type == t }.sortedByDescending { it.measuredAt }
                list.firstOrNull()?.let { Triple(t, it, list.getOrNull(1)) }
            }.sortedBy { (_, v, _) -> app.myhealth.domain.analytics.VitalRules.band(v).let { b -> if (b == app.myhealth.domain.analytics.VitalBand.Unknown) null else b.kind() }.urgency() },
            allTests = trends.sortedWith(compareBy<AnalyteTrend> { it.latest.status.kind().urgency() }.thenByDescending { it.analyte.keyMarker }.thenByDescending { it.latest.date }),
            latest = trends.sortedWith(compareByDescending<AnalyteTrend> { it.latest.status.isAbnormal }.thenByDescending { it.analyte.keyMarker }.thenByDescending { it.latest.date }).take(6),
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

@Composable
fun HomeContent(
    state: HomeState,
    onAddDocument: () -> Unit,
    onLogVital: () -> Unit,
    onAddMedicine: () -> Unit,
    onOpenTrend: (String) -> Unit,
    onOpenInsights: () -> Unit,
    onOpenVital: (app.myhealth.domain.model.VitalType) -> Unit = {},
    onAllTests: () -> Unit = {},
    onVisitPrep: () -> Unit = {},
    onLogVitalType: (app.myhealth.domain.model.VitalType) -> Unit = { onLogVital() },
    header: @Composable () -> Unit = {},
) {
    val sp = MyHealthTheme.spacing
    if (state.loading) { Column { header(); MyHealthLoadingState(stringResource(R.string.state_loading)) }; return }
    val name = state.member?.name.orEmpty()
    if (!state.hasData) {
        Column {
            header()
            MyHealthEmptyState(MyHealthIcons.Document, stringResource(R.string.home_empty_title, name), stringResource(R.string.home_empty_body),
                Modifier.testTag("homeEmpty"), actionLabel = stringResource(R.string.home_add_document), onAction = onAddDocument)
        }
        return
    }
    val zone = java.time.ZoneId.systemDefault()
    var index = 0
    Column(Modifier.fillMaxSize()) {
    header()
    LazyColumn(Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(start = sp.gutter, end = sp.gutter, bottom = sp.xl), verticalArrangement = Arrangement.spacedBy(sp.md)) {
        item { ScoreCard(state.score, onOpenInsights) }
        item {
            Row(Modifier.fillMaxWidth().appear(1)) {
                app.myhealth.core.designsystem.components.MyHealthQuickAction(MyHealthIcons.Import, stringResource(R.string.home_action_report), onAddDocument, Modifier.weight(1f).testTag("homeAdd"))
                app.myhealth.core.designsystem.components.MyHealthQuickAction(MyHealthIcons.Vitals, stringResource(R.string.home_quick_vital), onLogVital, Modifier.weight(1f))
                app.myhealth.core.designsystem.components.MyHealthQuickAction(MyHealthIcons.Medicines, stringResource(R.string.home_quick_medicine), onAddMedicine, Modifier.weight(1f))
                app.myhealth.core.designsystem.components.MyHealthQuickAction(MyHealthIcons.Document, stringResource(R.string.home_action_visit), onVisitPrep, Modifier.weight(1f).testTag("homeVisitPrep"))
            }
        }
        run {
            // every core vital gets a tile: readings first (not in range on top), then invitations to log the rest
            val have = state.vitals.map { it.first }.toSet()
            val missing = CORE_VITALS.filter { it !in have && !(it == app.myhealth.domain.model.VitalType.Weight && app.myhealth.domain.model.VitalType.Bmi in have) }
            val cells: List<Any> = state.vitals + missing
            item { MyHealthSectionHeader(stringResource(R.string.dash_vitals)) { MyHealthTextButton(stringResource(R.string.home_quick_vital), onLogVital, icon = MyHealthIcons.Add) } }
            items(cells.chunked(TILE_COLUMNS)) { row ->
                TileRow { m ->
                    row.forEach { cell ->
                        @Suppress("UNCHECKED_CAST")
                        if (cell is Triple<*, *, *>) {
                            val (t, latest, prev) = cell as Triple<app.myhealth.domain.model.VitalType, app.myhealth.domain.model.Vital, app.myhealth.domain.model.Vital?>
                            VitalTile(t, latest, prev, state.member?.heightCm, zone, { onOpenVital(t) }, index++, m)
                        } else { val t = cell as app.myhealth.domain.model.VitalType; EmptyVitalTile(t, { onLogVitalType(t) }, index++, m) }
                    }
                    repeat(TILE_COLUMNS - row.size) { Spacer(m) }
                }
            }
        }
        if (state.allTests.isNotEmpty()) {
            item { MyHealthSectionHeader(stringResource(R.string.dash_tests)) { if (state.allTests.size > 9) MyHealthTextButton(stringResource(R.string.home_focus_all), onAllTests) } }
            items(state.allTests.take(9).chunked(TILE_COLUMNS)) { row ->
                TileRow { m ->
                    row.forEach { t -> TestTile(t, { onOpenTrend(t.analyte.id) }, index++, m) }
                    repeat(TILE_COLUMNS - row.size) { Spacer(m) }
                }
            }
        }
        item {
            MyHealthSectionHeader(stringResource(R.string.home_focus_title)) {
                if (state.focus.isNotEmpty()) MyHealthTextButton(stringResource(R.string.home_focus_all), onOpenInsights)
            }
        }
        val top = state.focus.take(3)
        if (top.isEmpty()) item {
            MyHealthCard {
                Text(stringResource(R.string.home_focus_none_title), style = MyHealthTheme.typography.subtitle, color = MyHealthTheme.colors.textPrimary)
                Text(stringResource(R.string.home_focus_none_body), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
            }
        }
        items(top, key = { it.key }) { f -> FocusCard(f, onClick = onOpenInsights, modifier = Modifier.appear(3)) }
        item { MyHealthDisclaimer(stringResource(R.string.disclaimer_short)) }
    }
    }
}

@Composable
private fun ScoreCard(score: HealthScore?, onHow: () -> Unit) {
    val s = score ?: return
    val value = s.score
    val coverage = (s.coverage * 100).toInt()
    val caption = if (value == null) stringResource(R.string.home_score_none) else stringResource(R.string.home_score_caption)
    val coverageText = stringResource(R.string.home_score_coverage, coverage)
    MyHealthCard(Modifier.testTag("scoreCard").appear(0), onClick = onHow, emphasis = app.myhealth.core.designsystem.components.Emphasis.Strong) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                app.myhealth.core.designsystem.components.RingGauge((value ?: 0) / 100f, diameter = 112.dp)
                // Only the number sits inside the ring; the caption lives beside it, so nothing can collide with the stroke.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value?.toString() ?: stringResource(R.string.home_score_none_short), style = MyHealthTheme.typography.metric.copy(fontSize = 26.sp), color = MyHealthTheme.colors.textPrimary)
                    if (value != null) Text("/100", style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Spacer(Modifier.width(MyHealthTheme.spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_score_title), style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary)
                if (value == null) Text(caption, style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textPrimary)
                Text(coverageText, style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
                Row(Modifier.padding(top = MyHealthTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(MyHealthIcons.Info, null, tint = MyHealthTheme.colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_score_how), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.accent)
                }
            }
        }
    }
}
