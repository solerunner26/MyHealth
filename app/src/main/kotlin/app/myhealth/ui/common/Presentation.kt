package app.myhealth.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import app.myhealth.R
import app.myhealth.core.designsystem.components.StatusKind
import app.myhealth.domain.analytics.FocusItem
import app.myhealth.domain.analytics.RangeStatus
import app.myhealth.domain.analytics.Severity
import app.myhealth.domain.analytics.TrendDirection
import app.myhealth.domain.analytics.VitalBand
import app.myhealth.domain.catalogue.AnalyteCatalogue
import app.myhealth.domain.catalogue.Panel
import app.myhealth.domain.format.ValueFormat
import app.myhealth.domain.model.Comparator
import app.myhealth.domain.model.VitalType

fun RangeStatus.kind(): StatusKind = when (this) {
    RangeStatus.Normal -> StatusKind.Normal
    RangeStatus.Low -> StatusKind.Low
    RangeStatus.High -> StatusKind.High
    RangeStatus.CriticalLow, RangeStatus.CriticalHigh -> StatusKind.Critical
    RangeStatus.Unknown -> StatusKind.Unknown
}

@Composable @ReadOnlyComposable
fun label(s: RangeStatus) = stringResource(when (s) {
    RangeStatus.Normal -> R.string.status_normal; RangeStatus.Low -> R.string.status_low; RangeStatus.High -> R.string.status_high
    RangeStatus.CriticalLow -> R.string.status_critical_low; RangeStatus.CriticalHigh -> R.string.status_critical_high; RangeStatus.Unknown -> R.string.status_unknown
})

fun Severity.kind(): StatusKind = when (this) { Severity.Info -> StatusKind.Info; Severity.Low -> StatusKind.SeverityLow; Severity.Moderate -> StatusKind.SeverityModerate; Severity.High -> StatusKind.SeverityHigh }

@Composable @ReadOnlyComposable
fun label(s: Severity) = stringResource(when (s) { Severity.Info -> R.string.sev_info; Severity.Low -> R.string.sev_low; Severity.Moderate -> R.string.sev_moderate; Severity.High -> R.string.sev_high })

fun TrendDirection.kind(): StatusKind = when (this) {
    TrendDirection.Improving -> StatusKind.Improving; TrendDirection.Worsening -> StatusKind.Worsening
    TrendDirection.Stable -> StatusKind.Stable; TrendDirection.Insufficient -> StatusKind.Info
}

@Composable @ReadOnlyComposable
fun label(d: TrendDirection) = stringResource(when (d) {
    TrendDirection.Improving -> R.string.dir_improving; TrendDirection.Worsening -> R.string.dir_worsening
    TrendDirection.Stable -> R.string.dir_stable; TrendDirection.Insufficient -> R.string.dir_insufficient
})

fun VitalBand.kind(): StatusKind = when (this) {
    VitalBand.Normal -> StatusKind.Normal; VitalBand.Low -> StatusKind.Low; VitalBand.Elevated -> StatusKind.Attention
    VitalBand.High -> StatusKind.High; VitalBand.VeryHigh -> StatusKind.Critical; VitalBand.Unknown -> StatusKind.Info
}

@Composable @ReadOnlyComposable
fun label(b: VitalBand) = stringResource(when (b) {
    VitalBand.Low -> R.string.band_low; VitalBand.Normal -> R.string.band_normal; VitalBand.Elevated -> R.string.band_elevated
    VitalBand.High -> R.string.band_high; VitalBand.VeryHigh -> R.string.band_very_high; VitalBand.Unknown -> R.string.band_unknown
})

@Composable @ReadOnlyComposable
fun label(p: Panel) = stringResource(when (p) {
    Panel.Blood -> R.string.panel_blood; Panel.Diabetes -> R.string.panel_diabetes; Panel.Lipids -> R.string.panel_lipids; Panel.Kidney -> R.string.panel_kidney
    Panel.Electrolytes -> R.string.panel_electrolytes; Panel.Liver -> R.string.panel_liver; Panel.Thyroid -> R.string.panel_thyroid; Panel.Vitamins -> R.string.panel_vitamins
    Panel.Iron -> R.string.panel_iron; Panel.Inflammation -> R.string.panel_inflammation; Panel.Hormones -> R.string.panel_hormones; Panel.Urine -> R.string.panel_urine
    Panel.Other -> R.string.panel_other
})

/** "14.2 g/dL" with analyte-specific decimals and comparator. */
fun analyteValue(analyteId: String, v: Double, c: Comparator = Comparator.Eq): String {
    val a = AnalyteCatalogue.byId(analyteId)
    val num = ValueFormat.withComparator(v, a?.decimals ?: 1, c)
    return if (a == null || a.canonicalUnit.isBlank() || a.canonicalUnit == "ratio") num else "$num ${a.canonicalUnit}"
}

fun analyteRange(analyteId: String, low: Double?, high: Double?): String = ValueFormat.range(low, high, AnalyteCatalogue.byId(analyteId)?.decimals ?: 1)

fun vitalValue(type: VitalType, v: Double, v2: Double?, unit: String = type.canonicalUnit): String {
    val d = when (type) { VitalType.BloodPressure, VitalType.Pulse, VitalType.SpO2, VitalType.RespiratoryRate, VitalType.BloodGlucose -> 0; else -> 1 }
    val num = if (type.paired && v2 != null) "${ValueFormat.number(v, 0)}/${ValueFormat.number(v2, 0)}" else ValueFormat.number(v, d)
    return if (unit.isBlank()) num else "$num $unit"
}

/** Localised title and rationale for a focus item; numbers come straight from the evidence. */
data class FocusText(val title: String, val body: String)

@Composable
fun focusText(item: FocusItem): FocusText = when (item) {
    is FocusItem.OutOfRange -> {
        val n = analyteName(item.analyteId); val e = item.evidence.last()
        FocusText(
            stringResource(when { item.status.isCritical -> R.string.focus_critical_title; item.status == RangeStatus.Low -> R.string.focus_low_title; else -> R.string.focus_high_title }, n),
            stringResource(R.string.focus_range_body, analyteValue(item.analyteId, e.value), formatDate(e.date), analyteRange(item.analyteId, e.refLow, e.refHigh), ValueFormat.number(item.excessPct, 0)),
        )
    }
    is FocusItem.RapidChange -> FocusText(
        stringResource(R.string.focus_rapid_title, analyteName(item.analyteId)),
        stringResource(R.string.focus_rapid_body, analyteValue(item.analyteId, item.evidence.first().value), analyteValue(item.analyteId, item.evidence.last().value),
            ValueFormat.number(item.months, 1), ValueFormat.number(kotlin.math.abs(item.percentPerMonth), 1)),
    )
    is FocusItem.Overdue -> {
        val a = AnalyteCatalogue.byId(item.analyteId)
        val months = (if (item.lastAbnormal) a?.monitorMonthsAbnormal else a?.monitorMonthsNormal) ?: 12
        FocusText(stringResource(R.string.focus_overdue_title, analyteName(item.analyteId)),
            stringResource(if (item.lastAbnormal) R.string.focus_overdue_abnormal_body else R.string.focus_overdue_body, formatDate(item.lastDate), months))
    }
    is FocusItem.VitalAlert -> {
        val e = item.evidence.single()
        FocusText(stringResource(R.string.focus_vital_title, label(item.type), label(item.band)), stringResource(R.string.focus_vital_body, vitalValue(item.type, e.value, e.value2, e.unit), formatDate(e.date)))
    }
    is FocusItem.DataGap -> when (item.kind) {
        FocusItem.GapKind.NoLabResults -> FocusText(stringResource(R.string.gap_no_labs_title), stringResource(R.string.gap_no_labs_body))
        FocusItem.GapKind.NoRecentLabs -> FocusText(stringResource(R.string.gap_no_recent_labs_title), stringResource(R.string.gap_no_recent_labs_body, item.lastDate?.let { formatDate(it) } ?: "—"))
        FocusItem.GapKind.NoBloodPressure -> FocusText(stringResource(R.string.gap_no_bp_title), stringResource(R.string.gap_no_bp_body))
        FocusItem.GapKind.NoWeight -> FocusText(stringResource(R.string.gap_no_weight_title), stringResource(R.string.gap_no_weight_body))
    }
    is FocusItem.MedicineIssue -> when (item.kind) {
        FocusItem.MedicineIssueKind.Overlap -> FocusText(stringResource(R.string.med_overlap_title, item.names.first()), stringResource(R.string.med_overlap_body))
        FocusItem.MedicineIssueKind.MissedDoses -> FocusText(stringResource(R.string.med_missed_title, item.names.first()), stringResource(R.string.med_missed_body, item.count))
        FocusItem.MedicineIssueKind.CourseEnded -> FocusText(stringResource(R.string.med_ended_title, item.names.first()), stringResource(R.string.med_ended_body, formatDate(item.date)))
        FocusItem.MedicineIssueKind.NoReminders -> FocusText(stringResource(R.string.med_noreminders_title), stringResource(R.string.med_noreminders_body, item.names.joinToString(", ")))
    }
    is FocusItem.FollowUp -> FocusText(stringResource(R.string.followup_title, item.name),
        stringResource(if (item.daysUntil < 0) R.string.followup_overdue_body else R.string.followup_soon_body, formatDate(item.dueDate)))
    is FocusItem.SteadyTrend -> {
        val name = item.analyteId?.let { analyteName(it) } ?: label(item.vitalType!!)
        val seq = item.evidence.joinToString(" → ") { e -> item.analyteId?.let { ValueFormat.withComparator(e.value, AnalyteCatalogue.byId(it)?.decimals ?: 1, Comparator.Eq) }
            ?: vitalValue(item.vitalType!!, e.value, e.value2, "") }
        val unit = item.analyteId?.let { AnalyteCatalogue.byId(it)?.canonicalUnit?.takeIf { u -> u != "ratio" } } ?: item.vitalType?.canonicalUnit.orEmpty()
        val change = ValueFormat.signed(item.totalChange, item.analyteId?.let { AnalyteCatalogue.byId(it)?.decimals } ?: 1) + (if (unit.isBlank()) "" else " $unit")
        val guide = stringResource(guidance(item.analyteId, item.vitalType))
        FocusText(
            stringResource(if (item.rising) R.string.focus_steady_up_title else R.string.focus_steady_down_title, name, item.steps + 1),
            stringResource(R.string.focus_steady_body, seq, change, guide) + if (item.inRange) " " + stringResource(R.string.focus_steady_in_range) else "",
        )
    }
    is FocusItem.Wearable -> when (item.kind) {
        FocusItem.WearableKind.StepsDrop -> FocusText(stringResource(R.string.wear_steps_title), stringResource(R.string.wear_steps_body, ValueFormat.number(item.recent, 0), ValueFormat.number(item.baseline, 0)))
        FocusItem.WearableKind.RestingHeartRateRise -> FocusText(stringResource(R.string.wear_rhr_title), stringResource(R.string.wear_rhr_body, ValueFormat.number(item.recent, 0), ValueFormat.number(item.baseline, 0)))
        FocusItem.WearableKind.ShortSleep -> FocusText(stringResource(R.string.wear_sleep_title), stringResource(R.string.wear_sleep_body, ValueFormat.number(item.recent / 60, 1)))
    }
}

/** Gentle, non-diagnostic next-step wording by topic. */
fun guidance(analyteId: String?, vital: VitalType?): Int = when {
    vital == VitalType.BloodPressure -> R.string.guide_bp
    vital == VitalType.Weight || vital == VitalType.Bmi -> R.string.guide_weight
    vital == VitalType.BloodGlucose -> R.string.guide_sugar
    vital == VitalType.SpO2 -> R.string.guide_spo2
    analyteId != null -> when (AnalyteCatalogue.byId(analyteId)?.panel) {
        Panel.Diabetes -> R.string.guide_sugar; Panel.Lipids -> R.string.guide_lipids; Panel.Kidney, Panel.Electrolytes -> R.string.guide_kidney
        Panel.Liver -> R.string.guide_liver; Panel.Thyroid -> R.string.guide_thyroid; Panel.Blood, Panel.Iron -> R.string.guide_blood
        Panel.Vitamins -> R.string.guide_vitamins; else -> R.string.guide_general
    }
    else -> R.string.guide_general
}
