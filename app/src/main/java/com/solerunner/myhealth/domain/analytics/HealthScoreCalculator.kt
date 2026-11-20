package com.solerunner.myhealth.domain.analytics

import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.DailyMetric
import com.solerunner.myhealth.domain.model.DomainScore
import com.solerunner.myhealth.domain.model.HealthScore
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.ScoreBand
import com.solerunner.myhealth.domain.model.ValueStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.min

/** Input for the score. Mirrors [FocusInput] so the dashboard can compute both from one snapshot. */
data class ScoreInput(
    val today: LocalDate,
    val series: List<AnalyteSeries> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val dailyMetrics: List<DailyMetric> = emptyList(),
    val medicines: List<Medicine> = emptyList(),
)

/**
 * A single, explainable 0-100 number for "how is my health looking right now", built from five
 * domains. Missing data never punishes the user: a domain with too little information is dropped and
 * its weight is redistributed, and completeness is reported separately so the UI can say "based on
 * 6 of 9 areas". The score is a conversation starter, never a diagnosis.
 */
object HealthScoreCalculator {

    private val cardiometabolic = listOf(
        "hba1c", "fasting_glucose", "postprandial_glucose", "ldl_cholesterol", "total_cholesterol",
        "hdl_cholesterol", "triglycerides",
    )
    private val bloodNutrition = listOf(
        "hemoglobin", "ferritin", "vitamin_b12", "vitamin_d", "platelet_count", "wbc_count", "esr",
    )
    private val organ = listOf(
        "creatinine", "egfr", "sgpt", "sgot", "tsh", "uric_acid", "sodium", "potassium", "calcium",
    )

    fun compute(input: ScoreInput): HealthScore {
        val seriesByKey = input.series.associateBy { it.key }
        val domains = listOf(
            domain("cardio", 0.30, cardiometabolic, seriesByKey) to 0.30,
            domain("blood", 0.20, bloodNutrition, seriesByKey) to 0.20,
            domain("organ", 0.20, organ, seriesByKey) to 0.20,
            domain("lifestyle", 0.20, emptyList(), seriesByKey, lifestyleScore(input)) to 0.20,
            domain("monitoring", 0.10, emptyList(), seriesByKey, monitoringScore(input)) to 0.10,
        ).map { it.first }

        val available = domains.filter { it.score != null }
        val totalWeight = available.sumOf { it.weight }
        val total = if (totalWeight <= 0.0) null else (available.sumOf { (it.score ?: 0) * it.weight } / totalWeight).toInt()
        val completeness = totalWeight
        val band = when {
            total == null || completeness < 0.25 -> ScoreBand.INSUFFICIENT_DATA
            total >= 85 -> ScoreBand.EXCELLENT
            total >= 70 -> ScoreBand.GOOD
            total >= 55 -> ScoreBand.FAIR
            else -> ScoreBand.NEEDS_ATTENTION
        }
        return HealthScore(total = total, band = band, domains = domains, completeness = completeness)
    }

    private fun domain(
        key: String,
        weight: Double,
        keys: List<String>,
        series: Map<String, AnalyteSeries>,
        precomputed: Pair<Int, List<String>>? = null,
    ): DomainScore {
        if (precomputed != null) {
            return DomainScore(key = key, score = precomputed.first, weight = weight, evidence = precomputed.second)
        }
        val scores = mutableListOf<Int>()
        val evidence = mutableListOf<String>()
        for (analyteKey in keys) {
            val analyte = series[analyteKey] ?: continue
            val latest = analyte.latest ?: continue
            val score = markerScore(latest.value, analyte.referenceLow, analyte.referenceHigh, latest.status)
            scores += score
            evidence += analyte.displayName + " " + FocusEngine.format(latest.value, analyte.decimals) +
                analyte.unit?.let { " $it" }.orEmpty() + "  -  " + latest.date
        }
        val domainScore = if (scores.size < 2) null else scores.average().toInt()
        return DomainScore(key = key, score = domainScore, weight = weight, evidence = evidence.take(6))
    }

    /**
     * 100 when inside the reference range, then degrading with the relative distance outside it,
     * capped so a single catastrophic value cannot drag the whole score to zero.
     */
    internal fun markerScore(value: Double, low: Double?, high: Double?, status: ValueStatus): Int {
        if (low == null && high == null) return 85
        if (status == ValueStatus.IN_RANGE) return 100
        if (low != null && value < low) {
            val deficit = if (low != 0.0) (low - value) / low else 1.0
            return (100 - min(75.0, deficit * 220.0)).toInt()
        }
        if (high != null && value > high) {
            val excess = if (high != 0.0) (value - high) / high else 1.0
            return (100 - min(75.0, excess * 220.0)).toInt()
        }
        return 85
    }

    private fun lifestyleScore(input: ScoreInput): Pair<Int, List<String>>? {
        val recent = input.dailyMetrics.filter { ChronoUnit.DAYS.between(it.day, input.today) <= 14 }
        val parts = mutableListOf<Int>()
        val evidence = mutableListOf<String>()

        recent.mapNotNull { it.sleepMinutes }.takeIf { it.size >= 3 }?.let { sleep ->
            val average = sleep.average()
            parts += when {
                average >= 420 -> 100
                average >= 390 -> 80
                average >= 360 -> 60
                else -> 40
            }
            evidence += "Sleep " + FocusEngine.format(average / 60.0, 1) + " h avg - " + sleep.size + " nights"
        }
        recent.mapNotNull { it.steps }.takeIf { it.size >= 3 }?.let { steps ->
            val average = steps.average()
            parts += when {
                average >= 10000 -> 100
                average >= 7000 -> 85
                average >= 5000 -> 65
                else -> 45
            }
            evidence += "Steps " + FocusEngine.format(average, 0) + " avg - " + steps.size + " days"
        }
        recent.mapNotNull { it.restingHeartRate }.takeIf { it.size >= 3 }?.let { rest ->
            val average = rest.average()
            parts += when {
                average < 60 -> 100
                average <= 70 -> 90
                average <= 80 -> 70
                else -> 50
            }
            evidence += "Resting HR " + FocusEngine.format(average, 0) + " bpm"
        }
        val weights = input.measurements.filter { it.kind == MeasurementKind.WEIGHT }
            .filter { ChronoUnit.DAYS.between(it.recordedDay, input.today) <= 120 }
            .sortedBy { it.recordedDay }
        if (weights.size >= 3) {
            val slope = FocusRules.slopePerMonth(weights.map { it.recordedDay to it.primaryValue })
            parts += if (abs(slope) <= 1.0) 100 else if (abs(slope) <= 2.0) 75 else 55
            evidence += "Weight stable within " + FocusEngine.format(abs(slope), 1) + " kg/month"
        }
        return if (parts.size < 2) null else parts.average().toInt() to evidence
    }

    private fun monitoringScore(input: ScoreInput): Pair<Int, List<String>> {
        val evidence = mutableListOf<String>()
        var score = 60
        val reportDates = input.series.flatMap { s -> s.points.map { it.date } }.distinct().sorted()
        val lastReport = reportDates.lastOrNull()
        val daysSinceReport = lastReport?.let { ChronoUnit.DAYS.between(it, input.today) }
        score += when {
            daysSinceReport == null -> -20
            daysSinceReport <= 180 -> 25
            daysSinceReport <= 365 -> 10
            else -> -10
        }
        evidence += "Reports in last 3 years: " + reportDates.size
        evidence += if (lastReport != null) "Last report " + lastReport else "No report yet"

        val lastVital = input.measurements.maxByOrNull { it.recordedDay }?.recordedDay
        score += when {
            lastVital == null -> -10
            ChronoUnit.DAYS.between(lastVital, input.today) <= 30 -> 15
            ChronoUnit.DAYS.between(lastVital, input.today) <= 90 -> 5
            else -> -5
        }
        val duplicates = input.medicines.filter { it.isActive }
            .mapNotNull { it.molecule?.lowercase() }
            .groupingBy { it }.eachCount().count { it.value >= 2 }
        if (duplicates > 0) score -= 10 * duplicates
        if (input.medicines.any { it.isActive }) evidence += "Active medicines: " + input.medicines.count { it.isActive }
        return score.coerceIn(0, 100) to evidence
    }
}
