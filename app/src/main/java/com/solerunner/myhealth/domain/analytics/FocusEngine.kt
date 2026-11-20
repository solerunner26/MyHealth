package com.solerunner.myhealth.domain.analytics

import com.solerunner.myhealth.domain.lab.BiomarkerCatalog
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.DailyMetric
import com.solerunner.myhealth.domain.model.DirectionOfGood
import com.solerunner.myhealth.domain.model.FocusCode
import com.solerunner.myhealth.domain.model.FocusItem
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.domain.model.Severity
import com.solerunner.myhealth.domain.model.Treatment
import com.solerunner.myhealth.domain.model.TrendDirection
import com.solerunner.myhealth.domain.model.ValueStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Everything the focus engine may look at; passing it explicitly keeps the engine pure and testable. */
data class FocusInput(
    val today: LocalDate,
    val profile: Profile? = null,
    val series: List<AnalyteSeries> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val medicines: List<Medicine> = emptyList(),
    val treatments: List<Treatment> = emptyList(),
    val dailyMetrics: List<DailyMetric> = emptyList(),
    val healthConnectConnected: Boolean = false,
    val latestImportConfidence: Double? = null,
)

/**
 * The "what should I focus on?" engine.
 *
 * * **Explainable** — every item carries the numbers that triggered it, so the app can show *why*.
 * * **Never diagnostic** — wording points at a conversation with a doctor, never a diagnosis.
 * * **Deterministic** — a pure function of its input, so every rule is unit-testable.
 * * **Balanced** — it also reports good news; an app that only warns is an app people stop opening.
 */
object FocusEngine {

    internal const val PRIORITY_CRITICAL = 100
    internal const val PRIORITY_ATTENTION = 85
    internal const val PRIORITY_WATCH = 60
    internal const val PRIORITY_INFO = 30

    fun analyse(input: FocusInput): List<FocusItem> {
        val items = buildList {
            addAll(analyteRules(input))
            addAll(trendRules(input))
            addAll(FocusRules.lifestyleRules(input))
            addAll(FocusRules.medicineRules(input))
            addAll(FocusRules.treatmentRules(input))
            addAll(FocusRules.dataQualityRules(input))
        }
        return items
            .sortedWith(compareByDescending<FocusItem> { it.priority }.thenBy { it.code.ordinal })
            .distinctBy { it.code to it.analyteKey }
    }

    private fun analyteRules(input: FocusInput): List<FocusItem> {
        val out = mutableListOf<FocusItem>()
        for (series in input.series) {
            val latest = series.latest ?: continue
            val previous = series.previous
            val name = series.displayName
            val valueText = format(latest.value, series.decimals)
            val unit = series.unit.orEmpty()
            val range = rangeText(series)

            val statusCode = when (latest.status) {
                ValueStatus.VERY_HIGH -> FocusCode.VALUE_CRITICAL_HIGH
                ValueStatus.VERY_LOW -> FocusCode.VALUE_CRITICAL_LOW
                ValueStatus.HIGH -> FocusCode.VALUE_HIGH
                ValueStatus.LOW -> FocusCode.VALUE_LOW
                else -> null
            }
            if (statusCode != null) {
                val critical = latest.status == ValueStatus.VERY_HIGH || latest.status == ValueStatus.VERY_LOW
                val isHigh = latest.status == ValueStatus.HIGH || latest.status == ValueStatus.VERY_HIGH
                out += FocusItem(
                    code = statusCode,
                    severity = if (critical) Severity.ATTENTION else Severity.WATCH,
                    priority = if (critical) PRIORITY_CRITICAL else PRIORITY_WATCH,
                    analyteKey = series.key,
                    analyteName = name,
                    args = mapOf(
                        "analyte" to name,
                        "value" to valueText,
                        "unit" to unit,
                        "range" to range,
                        "reason" to if (isHigh) "high" else "low",
                    ),
                    evidence = evidenceLines(series),
                    relatedReportId = latest.reportId,
                )
            } else if (previous != null && previous.status != ValueStatus.IN_RANGE) {
                out += FocusItem(
                    code = FocusCode.VALUE_BACK_IN_RANGE,
                    severity = Severity.INFO,
                    priority = PRIORITY_INFO,
                    analyteKey = series.key,
                    analyteName = name,
                    args = mapOf("analyte" to name, "value" to valueText, "unit" to unit, "range" to range),
                    evidence = evidenceLines(series),
                    discussionPoint = false,
                )
            }
        }
        return out
    }

    private fun trendRules(input: FocusInput): List<FocusItem> {
        val out = mutableListOf<FocusItem>()
        for (series in input.series) {
            val latest = series.latest ?: continue
            val previous = series.previous ?: continue
            val trend = TrendAnalyzer.summarise(series)
            val name = series.displayName
            val badDirection = when (series.directionOfGood) {
                DirectionOfGood.LOWER -> trend.direction == TrendDirection.RISING
                DirectionOfGood.HIGHER -> trend.direction == TrendDirection.FALLING
                DirectionOfGood.BALANCED ->
                    latest.status != ValueStatus.IN_RANGE && latest.status.ordinal > previous.status.ordinal
                DirectionOfGood.UNKNOWN -> false
            }
            if (trend.isSignificant && trend.direction != TrendDirection.STABLE && trend.percentChange != null && badDirection) {
                out += FocusItem(
                    code = if (trend.direction == TrendDirection.RISING) FocusCode.TREND_RISING else FocusCode.TREND_FALLING,
                    severity = Severity.WATCH,
                    priority = PRIORITY_WATCH - 5,
                    analyteKey = series.key,
                    analyteName = name,
                    args = mapOf(
                        "analyte" to name,
                        "from" to format(trend.previous ?: 0.0, series.decimals),
                        "to" to format(latest.value, series.decimals),
                        "unit" to series.unit.orEmpty(),
                        "percent" to format(abs(trend.percentChange), 0),
                        "months" to format(trend.monthsSpan, 0),
                    ),
                    evidence = evidenceLines(series),
                    relatedReportId = latest.reportId,
                )
            }

            val recheck = BiomarkerCatalog.byKey(series.key)?.recheckMonths
            if (recheck != null) {
                val monthsSince = ChronoUnit.DAYS.between(latest.date, input.today) / 30.0
                if (monthsSince >= recheck + 1) {
                    out += FocusItem(
                        code = FocusCode.RECHECK_OVERDUE,
                        severity = Severity.INFO,
                        priority = PRIORITY_INFO + 5,
                        analyteKey = series.key,
                        analyteName = name,
                        args = mapOf(
                            "analyte" to name,
                            "months" to format(monthsSince, 0),
                            "due" to recheck.toString(),
                        ),
                        evidence = evidenceLines(series),
                    )
                }
            }
        }
        return out
    }

    internal fun rangeText(series: AnalyteSeries): String {
        val low = series.referenceLow
        val high = series.referenceHigh
        return when {
            low != null && high != null -> format(low, series.decimals) + " – " + format(high, series.decimals)
            high != null -> "< " + format(high, series.decimals)
            low != null -> "> " + format(low, series.decimals)
            else -> ""
        }
    }

    internal fun evidenceLines(series: AnalyteSeries): List<String> =
        series.rowsNewestFirst.take(4).map { format(it.value, series.decimals) + "  ·  " + it.date }

    internal fun format(value: Double, decimals: Int): String {
        val factor = Math.pow(10.0, decimals.toDouble())
        val rounded = Math.round(value * factor) / factor
        val text = if (decimals <= 0) rounded.toLong().toString() else rounded.toString()
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }
}

