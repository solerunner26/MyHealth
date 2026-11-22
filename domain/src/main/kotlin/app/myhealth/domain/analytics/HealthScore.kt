package app.myhealth.domain.analytics

import app.myhealth.domain.model.VitalType
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class ScoreComponentKind(val weight: Int) { LabMarkers(40), Vitals(30), Monitoring(15), Activity(15) }

enum class MissingReason { NoKeyMarkers, NoRecentVitals, NothingToMonitor, NoActivityData }

/** One input to a component, e.g. "LDL 138 mg/dL → 52/100". [subject] is an analyte id or a VitalType name. */
data class ScoreInput(val subject: String, val value: Double, val value2: Double? = null, val points: Int)

data class ScoreComponent(val kind: ScoreComponentKind, val score: Int?, val inputs: List<ScoreInput>, val missing: MissingReason?) {
    val available get() = score != null
}

/**
 * @param score 0–100, or null when coverage is below [HealthScoreCalculator.MIN_COVERAGE] ("not enough data").
 * @param coverage share of total weight that had data (0–1). Missing components are excluded and the remaining
 *                 weights renormalised, so missing data never counts as good or bad.
 */
data class HealthScore(val score: Int?, val coverage: Double, val components: List<ScoreComponent>) {
    /** Contribution of each available component to the final score, in points (sums to [score]). */
    fun contributions(): Map<ScoreComponentKind, Double> {
        val avail = components.filter { it.available }
        val total = avail.sumOf { it.kind.weight }.toDouble()
        return if (total == 0.0) emptyMap() else avail.associate { it.kind to it.score!! * it.kind.weight / total }
    }
}

object HealthScoreCalculator {
    const val MIN_COVERAGE = 0.4
    private const val LAB_RECENCY_MONTHS = 18L
    private const val VITAL_RECENCY_MONTHS = 12L

    fun calculate(i: FocusInputs): HealthScore {
        val trends = TrendEngine.all(i.results, i.today, i.historyYears, i.member.sex, i.member.ageYears(i.today))
        val comps = listOf(labs(trends, i), vitals(i), monitoring(trends, i), activity(i))
        val avail = comps.filter { it.available }
        val coverage = avail.sumOf { it.kind.weight } / 100.0
        val score = if (coverage < MIN_COVERAGE || avail.isEmpty()) null
        else (avail.sumOf { it.score!! * it.kind.weight }.toDouble() / avail.sumOf { it.kind.weight }).roundToInt()
        return HealthScore(score, coverage, comps)
    }

    /** In range → 100; outside → linearly down to 0 at 40 % beyond the bound; critical → 0. */
    fun markerPoints(status: RangeStatus, excess: Double): Int = when {
        status.isCritical -> 0
        status == RangeStatus.Normal || status == RangeStatus.Unknown -> 100
        else -> (100 - excess * 250).coerceIn(0.0, 100.0).roundToInt()
    }

    private fun labs(trends: List<AnalyteTrend>, i: FocusInputs): ScoreComponent {
        val cutoff = i.today.minusMonths(LAB_RECENCY_MONTHS)
        val inputs = trends.filter { it.analyte.keyMarker && !it.latest.date.isBefore(cutoff) && it.latest.status != RangeStatus.Unknown }.map { t ->
            ScoreInput(t.analyte.id, t.latest.value, points = markerPoints(t.latest.status, TrendEngine.relativeExcess(t.latest.value, t.latest.refLow, t.latest.refHigh)))
        }
        return if (inputs.isEmpty()) ScoreComponent(ScoreComponentKind.LabMarkers, null, emptyList(), MissingReason.NoKeyMarkers)
        else ScoreComponent(ScoreComponentKind.LabMarkers, inputs.map { it.points }.average().roundToInt(), inputs, null)
    }

    fun bpPoints(sys: Double, dia: Double): Int = when {
        sys >= 180 || dia >= 120 -> 0
        sys >= 160 || dia >= 100 -> 15
        sys >= 140 || dia >= 90 -> 40
        sys >= 130 || dia >= 80 -> 65
        sys >= 120 -> 85
        sys < 90 || dia < 60 -> 70
        else -> 100
    }

    fun bmiPoints(bmi: Double): Int = when { bmi < 18.5 -> 60; bmi < 25 -> 100; bmi < 30 -> 70; bmi < 35 -> 45; else -> 25 }

    private fun vitals(i: FocusInputs): ScoreComponent {
        val cutoff = i.today.minusMonths(VITAL_RECENCY_MONTHS)
        fun latest(t: VitalType) = i.vitals.filter { it.type == t && !it.measuredAt.atZone(i.zone).toLocalDate().isBefore(cutoff) }.maxByOrNull { it.measuredAt }
        val inputs = mutableListOf<ScoreInput>()
        latest(VitalType.BloodPressure)?.let { inputs += ScoreInput(VitalType.BloodPressure.name, it.value, it.value2, bpPoints(it.value, it.value2 ?: 0.0)) }
        val w = latest(VitalType.Weight); val h = i.member.heightCm ?: latest(VitalType.Height)?.value
        val bmi = latest(VitalType.Bmi)?.value ?: if (w != null && h != null) VitalRules.bmi(w.value, h) else null
        bmi?.let { inputs += ScoreInput(VitalType.Bmi.name, it, points = bmiPoints(it)) }
        latest(VitalType.Pulse)?.let { inputs += ScoreInput(VitalType.Pulse.name, it.value, points = if (it.value in 50.0..90.0) 100 else if (it.value in 40.0..110.0) 70 else 40) }
        latest(VitalType.SpO2)?.let { inputs += ScoreInput(VitalType.SpO2.name, it.value, points = if (it.value >= 95) 100 else if (it.value >= 90) 60 else 20) }
        return if (inputs.isEmpty()) ScoreComponent(ScoreComponentKind.Vitals, null, emptyList(), MissingReason.NoRecentVitals)
        else ScoreComponent(ScoreComponentKind.Vitals, inputs.map { it.points }.average().roundToInt(), inputs, null)
    }

    private fun monitoring(trends: List<AnalyteTrend>, i: FocusInputs): ScoreComponent {
        val monitored = trends.filter { it.analyte.keyMarker && it.analyte.monitorMonthsNormal != null }
        if (monitored.isEmpty()) return ScoreComponent(ScoreComponentKind.Monitoring, null, emptyList(), MissingReason.NothingToMonitor)
        val inputs = monitored.map { t ->
            val months = if (t.latest.status.isAbnormal) t.analyte.monitorMonthsAbnormal!! else t.analyte.monitorMonthsNormal!!
            val due = t.latest.date.plusMonths(months.toLong())
            val pts = if (!i.today.isAfter(due)) 100 else (100 - ChronoUnit.MONTHS.between(due, i.today) * 20).coerceAtLeast(0).toInt()
            ScoreInput(t.analyte.id, ChronoUnit.DAYS.between(t.latest.date, i.today).toDouble(), points = pts)
        }
        return ScoreComponent(ScoreComponentKind.Monitoring, inputs.map { it.points }.average().roundToInt(), inputs, null)
    }

    private fun activity(i: FocusInputs): ScoreComponent {
        val days = i.activity.filter { it.steps != null && !it.date.isBefore(i.today.minusDays(7)) && it.date.isBefore(i.today) }
        if (days.size < 3) return ScoreComponent(ScoreComponentKind.Activity, null, emptyList(), MissingReason.NoActivityData)
        val avg = days.mapNotNull { it.steps }.average()
        val pts = (avg / 80.0).coerceIn(0.0, 100.0).roundToInt() // 8 000 steps/day = 100
        return ScoreComponent(ScoreComponentKind.Activity, pts, listOf(ScoreInput("steps", avg, points = pts)), null)
    }

}
