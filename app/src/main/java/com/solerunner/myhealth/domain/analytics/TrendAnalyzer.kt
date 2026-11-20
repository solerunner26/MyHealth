package com.solerunner.myhealth.domain.analytics

import com.solerunner.myhealth.core.util.Dates
import com.solerunner.myhealth.domain.lab.BiomarkerCatalog
import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.DirectionOfGood
import com.solerunner.myhealth.domain.model.SeriesPoint
import com.solerunner.myhealth.domain.model.Sex
import com.solerunner.myhealth.domain.model.TrendDirection
import com.solerunner.myhealth.domain.model.TrendSummary
import java.time.LocalDate
import kotlin.math.abs

/**
 * Turns raw measurement points into what the UI shows: latest value, change since the previous
 * report, change per month, whether that direction is good news, and how far the series spans.
 *
 * Everything is windowed to the last three years — the product promise is "all records, up to the
 * last 3 years". Older results stay in the database (nothing is deleted) but are excluded from trend
 * claims, so the app never mixes a 2019 result into today's picture.
 */
object TrendAnalyzer {

    const val HISTORY_YEARS = 3

    /** A change smaller than this relative amount is reported as "stable". */
    private const val STABLE_RELATIVE_SPAN = 0.03

    fun windowStart(today: LocalDate): LocalDate = today.minusYears(HISTORY_YEARS.toLong())

    /** Filters, sorts and de-duplicates points for one analyte inside the history window. */
    fun series(
        key: String,
        points: List<SeriesPoint>,
        today: LocalDate = Dates.today(),
    ): AnalyteSeries? {
        val biomarker = BiomarkerCatalog.byKey(key)
        val start = windowStart(today)
        val filtered = points
            .filter { !it.date.isBefore(start) && !it.date.isAfter(today.plusDays(1)) }
            .sortedBy { it.date }
        if (filtered.isEmpty()) return null
        val range = biomarker?.rangeFor(Sex.UNSPECIFIED, null)
        return AnalyteSeries(
            key = key,
            displayName = biomarker?.displayName ?: key,
            category = biomarker?.category ?: AnalyteCategory.OTHER,
            unit = biomarker?.canonicalUnit ?: filtered.last().unit,
            directionOfGood = biomarker?.directionOfGood ?: DirectionOfGood.UNKNOWN,
            referenceLow = range?.low,
            referenceHigh = range?.high,
            points = dedupe(filtered),
            decimals = biomarker?.decimals ?: 2,
        )
    }

    /**
     * Merges same-date results: patients often have two reports drawn on one day, and the timeline must
     * show exactly one row per date. Imported report values win over manual entries.
     */
    private fun dedupe(points: List<SeriesPoint>): List<SeriesPoint> {
        val byDate = linkedMapOf<LocalDate, SeriesPoint>()
        for (point in points) {
            val existing = byDate[point.date]
            byDate[point.date] = when {
                existing == null -> point
                point.source == DataSource.REPORT_IMPORT && existing.source != DataSource.REPORT_IMPORT -> point
                existing.source == DataSource.REPORT_IMPORT && point.source != DataSource.REPORT_IMPORT -> existing
                else -> point
            }
        }
        return byDate.values.toList()
    }


    fun summarise(series: AnalyteSeries): TrendSummary {
        val points = series.points
        val latest = points.lastOrNull() ?: return TrendSummary()
        val previous = points.dropLast(1).lastOrNull()
            ?: return TrendSummary(latest = latest.value, status = latest.status)

        val delta = latest.value - previous.value
        val percent = if (previous.value != 0.0) delta / previous.value * 100.0 else null
        val months = Dates.monthsBetween(previous.date, latest.date).coerceAtLeast(0.05)
        val span = Dates.monthsBetween(points.first().date, latest.date)
        val slope = linearSlope(points)
        val relativeSlope = if (latest.value != 0.0) slope / abs(latest.value) else 0.0
        val direction = when {
            abs(relativeSlope) * span < STABLE_RELATIVE_SPAN -> TrendDirection.STABLE
            slope > 0 -> TrendDirection.RISING
            slope < 0 -> TrendDirection.FALLING
            else -> TrendDirection.STABLE
        }

        val improving = when (series.directionOfGood) {
            DirectionOfGood.LOWER -> latest.value < previous.value
            DirectionOfGood.HIGHER -> latest.value > previous.value
            DirectionOfGood.BALANCED ->
                distanceFromBandMiddle(series, latest.value) < distanceFromBandMiddle(series, previous.value)
            DirectionOfGood.UNKNOWN -> null
        }

        val statusChanged = latest.status != previous.status
        return TrendSummary(
            latest = latest.value,
            previous = previous.value,
            delta = delta,
            percentChange = percent,
            perMonth = delta / months,
            direction = direction,
            monthsSpan = span,
            status = latest.status,
            improving = improving,
            isSignificant = statusChanged || (percent != null && abs(percent) >= 15.0),
        )
    }

    private fun distanceFromBandMiddle(series: AnalyteSeries, value: Double): Double {
        val low = series.referenceLow ?: return 0.0
        val high = series.referenceHigh ?: return 0.0
        return abs(value - (low + high) / 2.0)
    }

    /** Least-squares slope in value-per-month: steadier than first/last when the data is noisy. */
    fun linearSlope(points: List<SeriesPoint>): Double {
        if (points.size < 2) return 0.0
        val origin = points.first().date
        val xs = points.map { Dates.monthsBetween(origin, it.date) }
        val ys = points.map { it.value }
        val meanX = xs.average()
        val meanY = ys.average()
        var covariance = 0.0
        var variance = 0.0
        for (index in xs.indices) {
            covariance += (xs[index] - meanX) * (ys[index] - meanY)
            variance += (xs[index] - meanX) * (xs[index] - meanX)
        }
        return if (variance == 0.0) 0.0 else covariance / variance
    }

    /** Clinical order used by the dashboard and the report detail screen. */
    private val dashboardPriority = listOf(
        "hba1c", "fasting_glucose", "postprandial_glucose", "ldl_cholesterol", "total_cholesterol",
        "hdl_cholesterol", "triglycerides", "hemoglobin", "creatinine", "egfr", "tsh", "vitamin_d",
        "vitamin_b12", "ferritin", "uric_acid", "sgpt", "sgot", "esr", "crp", "platelet_count",
        "wbc_count", "sodium", "potassium", "calcium",
    )

    private val priorityRank: Map<String, Int> = dashboardPriority.withIndex().associate { it.value to it.index }

    fun dashboardOrder(series: List<AnalyteSeries>): List<AnalyteSeries> =
        series.sortedWith(compareBy({ priorityRank[it.key] ?: Int.MAX_VALUE }, { it.displayName }))

    /** Series needing attention first (very high / very low / high / low), then the rest. */
    fun attentionFirst(series: List<AnalyteSeries>): List<AnalyteSeries> = series.sortedWith(
        compareByDescending<AnalyteSeries> { it.latest?.status?.ordinal ?: 0 }
            .thenBy { priorityRank[it.key] ?: Int.MAX_VALUE },
    )
}
