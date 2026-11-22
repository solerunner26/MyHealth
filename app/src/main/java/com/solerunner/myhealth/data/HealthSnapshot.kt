package com.solerunner.myhealth.data

import com.solerunner.myhealth.core.util.Dates
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.analytics.HealthScoreCalculator
import com.solerunner.myhealth.domain.analytics.ScoreInput
import com.solerunner.myhealth.domain.analytics.TrendAnalyzer
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.DailyMetric
import com.solerunner.myhealth.domain.model.HealthScore
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.domain.model.SeriesPoint
import com.solerunner.myhealth.domain.model.Treatment
import java.time.LocalDate

/** One immutable read of everything the analytics screens need, taken inside the three-year window. */
data class HealthSnapshot(
    val today: LocalDate,
    val profile: Profile?,
    val series: List<AnalyteSeries>,
    val reports: List<Report>,
    val measurements: List<Measurement>,
    val medicines: List<Medicine>,
    val treatments: List<Treatment>,
    val dailyMetrics: List<DailyMetric>,
) {
    val activeMedicines: List<Medicine> get() = medicines.filter { it.isActive }
    val score: HealthScore by lazy {
        HealthScoreCalculator.compute(
            ScoreInput(today = today, series = series, measurements = measurements, dailyMetrics = dailyMetrics, medicines = medicines),
        )
    }
}

/**
 * Loads the snapshot the dashboard and insight screens share. One read means the score and the focus
 * list can never disagree about, say, the latest HbA1c.
 */
suspend fun AppContainer.loadSnapshot(today: LocalDate = Dates.today()): HealthSnapshot {
    val windowStart = TrendAnalyzer.windowStart(today)
    val profile = profiles.current()
    val pairs = reports.labResultsSince(windowStart)
    val grouped = pairs.groupBy { it.second.analyteKey }
    val series = grouped.mapNotNull { (key, rows) ->
        val points = rows.mapNotNull { (report, result) ->
            val value = result.canonicalValue ?: result.value ?: return@mapNotNull null
            SeriesPoint(
                date = report.sampleDate,
                value = value,
                status = result.status,
                source = report.source,
                reportId = report.id,
                printedValue = result.value,
                printedUnit = result.unit,
                unit = result.canonicalUnit,
            )
        }
        TrendAnalyzer.series(key, points, today)
    }
    return HealthSnapshot(
        today = today,
        profile = profile,
        series = TrendAnalyzer.dashboardOrder(series),
        reports = reports.recent(limit = 60),
        measurements = measurements.since(windowStart),
        medicines = medicines.all(),
        treatments = treatments.all(),
        dailyMetrics = device.since(windowStart),
    )
}
