package app.myhealth.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.R
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthDisclaimer
import app.myhealth.core.designsystem.components.MyHealthDivider
import app.myhealth.core.designsystem.components.MyHealthEmptyState
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthLoadingState
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthStatusBadge
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.components.StatusKind
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.analytics.Evidence
import app.myhealth.domain.analytics.FocusItem
import app.myhealth.domain.analytics.HealthScore
import app.myhealth.domain.analytics.MissingReason
import app.myhealth.domain.analytics.ScoreComponentKind
import app.myhealth.domain.format.ValueFormat
import app.myhealth.domain.model.VitalType
import app.myhealth.ui.LocalSession
import app.myhealth.ui.common.analyteName
import app.myhealth.ui.common.analyteRange
import app.myhealth.ui.common.analyteValue
import app.myhealth.ui.common.formatDate
import app.myhealth.ui.common.label
import app.myhealth.ui.common.rememberVm
import app.myhealth.ui.common.vitalValue
import app.myhealth.ui.home.HomeViewModel

@Composable
fun InsightsRoute(onBack: () -> Unit, onOpenTrend: (String) -> Unit) {
    val session = LocalSession.current
    val vm = rememberVm { HomeViewModel(session) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sp = MyHealthTheme.spacing
    Column(Modifier.fillMaxSize().testTag("insights")) {
        MyHealthTopBar(stringResource(R.string.insights_title), onBack = onBack, backLabel = stringResource(R.string.action_back))
        if (state.loading) { MyHealthLoadingState(stringResource(R.string.state_loading)); return@Column }
        LazyColumn(Modifier.fillMaxSize().testTag("insightsList"), contentPadding = PaddingValues(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            item { MyHealthInfoBanner(stringResource(R.string.insights_intro), icon = MyHealthIcons.Help) }
            if (state.focus.isEmpty()) item {
                MyHealthEmptyState(MyHealthIcons.StatusNormal, stringResource(R.string.insights_none_title), stringResource(R.string.insights_none_body))
            }
            items(state.focus, key = { it.key }) { f -> InsightCard(f, onOpenTrend) }
            state.score?.let { s -> item { MyHealthSectionHeader(stringResource(R.string.score_breakdown)) }; item { ScoreBreakdown(s) } }
            item { MyHealthDisclaimer(stringResource(R.string.disclaimer_short)) }
        }
    }
}

private fun FocusItem.analyteId(): String? = when (this) { is FocusItem.OutOfRange -> analyteId; is FocusItem.RapidChange -> analyteId; is FocusItem.Overdue -> analyteId; is FocusItem.SteadyTrend -> analyteId; else -> null }

@Composable
private fun InsightCard(f: FocusItem, onOpenTrend: (String) -> Unit) {
    var open by rememberSaveable(f.key) { mutableStateOf(false) }
    val c = MyHealthTheme.colors
    FocusCard(f, onClick = null, modifier = Modifier.testTag("insight_${f.key}")) {
        MyHealthTextButton(stringResource(if (open) R.string.insights_hide else R.string.insights_why), { open = !open }, icon = MyHealthIcons.Expand, modifier = Modifier.testTag("why_${f.key}"))
        if (open) {
            Text(stringResource(ruleText(f)), style = MyHealthTheme.typography.caption, color = c.textSecondary)
            if (f.evidence.isNotEmpty()) {
                Text(stringResource(R.string.insights_evidence), style = MyHealthTheme.typography.label, color = c.textPrimary)
                f.evidence.forEach { e -> EvidenceRow(f, e) }
            }
            f.analyteId()?.let { a -> MyHealthTextButton(stringResource(R.string.insights_open_trend), { onOpenTrend(a) }, icon = MyHealthIcons.Trends) }
            Text(stringResource(R.string.disclaimer_short), style = MyHealthTheme.typography.caption, color = c.textTertiary)
        }
    }
}

private fun ruleText(f: FocusItem) = when (f) {
    is FocusItem.OutOfRange -> R.string.rule_range; is FocusItem.RapidChange -> R.string.rule_rapid; is FocusItem.Overdue -> R.string.rule_overdue
    is FocusItem.VitalAlert -> R.string.rule_vital; is FocusItem.DataGap -> R.string.rule_gap; is FocusItem.MedicineIssue -> R.string.rule_medicine
    is FocusItem.FollowUp -> R.string.rule_followup; is FocusItem.Wearable -> R.string.rule_wearable; is FocusItem.SteadyTrend -> R.string.rule_steady
}

@Composable
private fun EvidenceRow(f: FocusItem, e: Evidence) {
    val c = MyHealthTheme.colors
    val a = f.analyteId()
    val value = when {
        a != null -> analyteValue(a, e.value)
        f is FocusItem.VitalAlert -> vitalValue(f.type, e.value, e.value2, e.unit)
        f is FocusItem.SteadyTrend && f.vitalType != null -> vitalValue(f.vitalType!!, e.value, e.value2, e.unit)
        else -> ValueFormat.compact(e.value) + " " + e.unit
    }
    val range = if (a != null) analyteRange(a, e.refLow, e.refHigh) else ""
    Row(Modifier.testTag("evidence"), horizontalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
        Text(formatDate(e.date), style = MyHealthTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MyHealthTheme.typography.bodySmall, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(if (range.isNotBlank()) stringResource(R.string.trend_range, range) else "", style = MyHealthTheme.typography.caption, color = c.textTertiary, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ScoreBreakdown(s: HealthScore) {
    val c = MyHealthTheme.colors
    MyHealthCard(Modifier.testTag("scoreBreakdown")) {
        Text(stringResource(R.string.score_explain), style = MyHealthTheme.typography.bodySmall, color = c.textSecondary)
        s.components.forEach { comp ->
            MyHealthDivider(Modifier.background(c.surface))
            Row(horizontalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
                Text(stringResource(when (comp.kind) {
                    ScoreComponentKind.LabMarkers -> R.string.score_part_labs; ScoreComponentKind.Vitals -> R.string.score_part_vitals
                    ScoreComponentKind.Monitoring -> R.string.score_part_monitoring; ScoreComponentKind.Activity -> R.string.score_part_activity
                }), style = MyHealthTheme.typography.subtitle, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.score_weight, comp.kind.weight), style = MyHealthTheme.typography.caption, color = c.textSecondary)
                if (comp.score != null) MyHealthStatusBadge(stringResource(R.string.score_points, comp.score!!), StatusKind.Info)
                else MyHealthStatusBadge(stringResource(R.string.score_missing), StatusKind.Unknown)
            }
            comp.missing?.let { m ->
                Text(stringResource(when (m) {
                    MissingReason.NoKeyMarkers -> R.string.score_missing_labs; MissingReason.NoRecentVitals -> R.string.score_missing_vitals
                    MissingReason.NothingToMonitor -> R.string.score_missing_monitoring; MissingReason.NoActivityData -> R.string.score_missing_activity
                }), style = MyHealthTheme.typography.caption, color = c.textTertiary)
            }
            comp.inputs.forEach { inp ->
                val name: String; val v: String
                when (comp.kind) {
                    ScoreComponentKind.LabMarkers -> { name = analyteName(inp.subject); v = analyteValue(inp.subject, inp.value) }
                    ScoreComponentKind.Monitoring -> { name = analyteName(inp.subject); v = androidx.compose.ui.res.pluralStringResource(R.plurals.score_days_since, inp.value.toInt(), inp.value.toInt()) }
                    ScoreComponentKind.Vitals -> { val t = VitalType.valueOf(inp.subject); name = label(t); v = vitalValue(t, inp.value, inp.value2) }
                    ScoreComponentKind.Activity -> { name = stringResource(R.string.score_steps); v = ValueFormat.number(inp.value, 0) }
                }
                Text(stringResource(R.string.score_input, name, v, inp.points), style = MyHealthTheme.typography.caption, color = c.textSecondary)
            }
        }
    }
}
