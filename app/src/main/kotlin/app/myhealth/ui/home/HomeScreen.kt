package app.myhealth.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    val hasData: Boolean = false,
    val score: HealthScore? = null,
    val focus: List<FocusItem> = emptyList(),
    val latest: List<AnalyteTrend> = emptyList(),
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
) {
    val sp = MyHealthTheme.spacing
    if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return }
    val name = state.member?.name.orEmpty()
    if (!state.hasData) {
        MyHealthEmptyState(MyHealthIcons.Document, stringResource(R.string.home_empty_title, name), stringResource(R.string.home_empty_body),
            Modifier.testTag("homeEmpty"), actionLabel = stringResource(R.string.home_add_document), onAction = onAddDocument)
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(horizontal = sp.gutter, vertical = sp.sm), verticalArrangement = Arrangement.spacedBy(sp.md)) {
        item { ScoreCard(state.score, onOpenInsights) }
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
        items(top, key = { it.key }) { f -> FocusCard(f, onClick = onOpenInsights) }
        item { MyHealthPrimaryButton(stringResource(R.string.home_add_document), onAddDocument, Modifier.fillMaxWidth().testTag("homeAdd"), icon = MyHealthIcons.Import) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                MyHealthSecondaryButton(stringResource(R.string.home_quick_vital), onLogVital, Modifier.weight(1f), icon = MyHealthIcons.Vitals)
                MyHealthSecondaryButton(stringResource(R.string.home_quick_medicine), onAddMedicine, Modifier.weight(1f), icon = MyHealthIcons.Medicines)
            }
        }
        if (state.latest.isNotEmpty()) {
            item { MyHealthSectionHeader(stringResource(R.string.home_latest)) }
            items(state.latest.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                    pair.forEach { t ->
                        MyHealthMetricTile(
                            label = analyteName(t.analyte.id), value = ValueFormat.withComparator(t.latest.value, t.analyte.decimals, t.latest.comparator),
                            unit = t.analyte.canonicalUnit.takeIf { it != "ratio" }, status = label(t.latest.status) to t.latest.status.kind(),
                            caption = app.myhealth.ui.common.formatDate(t.latest.date), onClick = { onOpenTrend(t.analyte.id) },
                            modifier = Modifier.weight(1f).testTag("tile_${t.analyte.id}"),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item { MyHealthDisclaimer(stringResource(R.string.disclaimer_short)) }
    }
}

@Composable
private fun ScoreCard(score: HealthScore?, onHow: () -> Unit) {
    val s = score ?: return
    val value = s.score
    val coverage = (s.coverage * 100).toInt()
    val caption = if (value == null) stringResource(R.string.home_score_none) else stringResource(R.string.home_score_caption)
    val coverageText = stringResource(R.string.home_score_coverage, coverage)
    MyHealthCard(Modifier.testTag("scoreCard")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MyHealthScoreRing(value, value?.toString() ?: stringResource(R.string.home_score_none_short), caption,
                stringResource(R.string.home_score_cd, value?.toString() ?: caption, coverageText))
            Spacer(Modifier.width(MyHealthTheme.spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_score_title), style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary)
                Text(coverageText, style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
                MyHealthTextButton(stringResource(R.string.home_score_how), onHow, icon = MyHealthIcons.Info)
            }
        }
    }
}

