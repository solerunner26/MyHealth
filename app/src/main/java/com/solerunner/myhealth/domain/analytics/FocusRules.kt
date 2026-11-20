package com.solerunner.myhealth.domain.analytics

import com.solerunner.myhealth.domain.model.FocusCode
import com.solerunner.myhealth.domain.model.FocusItem
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.Severity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The non-analyte half of the focus engine: lifestyle/device signals, medicine review, treatment
 * follow-ups and data-quality nudges. Split out of [FocusEngine] purely to keep both files readable.
 */
internal object FocusRules {

    // ---- Lifestyle: blood pressure, weight, wearable metrics ---------------------------------

    fun lifestyleRules(input: FocusInput): List<FocusItem> {
        val out = mutableListOf<FocusItem>()

        val recentBp = input.measurements
            .filter { it.kind == MeasurementKind.BLOOD_PRESSURE && daysAgo(it.recordedDay, input.today) <= 14 }
        if (recentBp.size >= 2) {
            val systolic = recentBp.map { it.primaryValue }.average()
            val diastolic = recentBp.mapNotNull { it.secondaryValue }.average()
            val args = mapOf(
                "systolic" to FocusEngine.format(systolic, 0),
                "diastolic" to FocusEngine.format(diastolic, 0),
                "readings" to recentBp.size.toString(),
            )
            when {
                systolic >= 140.0 || diastolic >= 90.0 -> out += FocusItem(
                    code = FocusCode.BP_ELEVATED,
                    severity = Severity.ATTENTION,
                    priority = FocusEngine.PRIORITY_ATTENTION,
                    args = args,
                    evidence = bpEvidence(recentBp),
                )
                systolic < 90.0 || diastolic < 60.0 -> out += FocusItem(
                    code = FocusCode.BP_LOW,
                    severity = Severity.WATCH,
                    priority = FocusEngine.PRIORITY_WATCH,
                    args = args,
                    evidence = bpEvidence(recentBp),
                )
            }
        }

        val weights = input.measurements
            .filter { it.kind == MeasurementKind.WEIGHT && daysAgo(it.recordedDay, input.today) <= 120 }
            .sortedBy { it.recordedDay }
        if (weights.size >= 3) {
            val slope = slopePerMonth(weights.map { it.recordedDay to it.primaryValue })
            val first = weights.first().primaryValue
            val last = weights.last().primaryValue
            val heightCm = input.profile?.heightCm
            val latestBmi = heightCm?.takeIf { it > 0 }?.let { last / ((it / 100.0) * (it / 100.0)) }
            val args = mapOf(
                "from" to FocusEngine.format(first, 1),
                "to" to FocusEngine.format(last, 1),
                "perMonth" to FocusEngine.format(kotlin.math.abs(slope), 1),
                "bmi" to (latestBmi?.let { FocusEngine.format(it, 1) } ?: "-"),
            )
            if (slope >= 1.0) {
                out += FocusItem(
                    code = FocusCode.WEIGHT_TRENDING_UP,
                    severity = Severity.WATCH,
                    priority = FocusEngine.PRIORITY_WATCH - 10,
                    args = args,
                    evidence = weightEvidence(weights),
                )
            } else if (slope <= -1.5) {
                out += FocusItem(
                    code = FocusCode.WEIGHT_TRENDING_DOWN,
                    severity = Severity.WATCH,
                    priority = FocusEngine.PRIORITY_WATCH - 10,
                    args = args,
                    evidence = weightEvidence(weights),
                )
            }
        }

        val recent = input.dailyMetrics.filter { daysAgo(it.day, input.today) <= 7 }
        val previous = input.dailyMetrics.filter { daysAgo(it.day, input.today) in 8..14 }
        if (recent.isNotEmpty()) {
            val sleep = recent.mapNotNull { it.sleepMinutes }.takeIf { it.size >= 3 }
            if (sleep != null && sleep.average() < 390) {
                out += FocusItem(
                    code = FocusCode.LOW_SLEEP,
                    severity = Severity.WATCH,
                    priority = FocusEngine.PRIORITY_WATCH - 5,
                    args = mapOf(
                        "hours" to FocusEngine.format(sleep.average() / 60.0, 1),
                        "nights" to sleep.size.toString(),
                        "target" to "7",
                    ),
                    evidence = recent.filter { it.sleepMinutes != null }
                        .takeLast(5).map { FocusEngine.format((it.sleepMinutes ?: 0) / 60.0, 1) + " h  -  " + it.day },
                )
            }
            val steps = recent.mapNotNull { it.steps }.takeIf { it.size >= 3 }
            if (steps != null && steps.average() < 5000) {
                out += FocusItem(
                    code = FocusCode.LOW_STEPS,
                    severity = Severity.INFO,
                    priority = FocusEngine.PRIORITY_INFO,
                    args = mapOf("steps" to FocusEngine.format(steps.average(), 0), "target" to "7000"),
                    evidence = recent.filter { it.steps != null }
                        .takeLast(5).map { (it.steps ?: 0).toString() + " steps  -  " + it.day },
                )
            }
            val restingHr = recent.mapNotNull { it.restingHeartRate }
            if (restingHr.size >= 3 && restingHr.average() > 80) {
                out += FocusItem(
                    code = FocusCode.HIGH_RESTING_HEART_RATE,
                    severity = Severity.WATCH,
                    priority = FocusEngine.PRIORITY_WATCH - 10,
                    args = mapOf("value" to FocusEngine.format(restingHr.average(), 0), "range" to "50 - 75"),
                    evidence = recent.filter { it.restingHeartRate != null }
                        .takeLast(5).map { FocusEngine.format(it.restingHeartRate ?: 0.0, 0) + " bpm  -  " + it.day },
                )
            }
            val spo2 = recent.mapNotNull { it.spo2Average }
            if (spo2.isNotEmpty() && spo2.min() < 94.0) {
                out += FocusItem(
                    code = FocusCode.LOW_SPO2,
                    severity = Severity.WATCH,
                    priority = FocusEngine.PRIORITY_WATCH,
                    args = mapOf("value" to FocusEngine.format(spo2.min(), 0), "range" to "95 - 100"),
                    evidence = recent.filter { it.spo2Average != null }
                        .takeLast(5).map { FocusEngine.format(it.spo2Average ?: 0.0, 0) + " %  -  " + it.day },
                )
            }
            val hrvNow = recent.mapNotNull { it.hrvRmssd }.average().takeIf { !it.isNaN() }
            val hrvBefore = previous.mapNotNull { it.hrvRmssd }.average().takeIf { !it.isNaN() }
            if (hrvNow != null && hrvBefore != null && hrvBefore > 0 && hrvNow < hrvBefore * 0.85) {
                out += FocusItem(
                    code = FocusCode.LOW_HRV,
                    severity = Severity.INFO,
                    priority = FocusEngine.PRIORITY_INFO,
                    args = mapOf(
                        "value" to FocusEngine.format(hrvNow, 0),
                        "previous" to FocusEngine.format(hrvBefore, 0),
                    ),
                    evidence = listOf(
                        "7-day HRV " + FocusEngine.format(hrvNow, 0) + " ms",
                        "previous week " + FocusEngine.format(hrvBefore, 0) + " ms",
                    ),
                )
            }
        } else if (!input.healthConnectConnected && input.measurements.isNotEmpty()) {
            out += FocusItem(
                code = FocusCode.NO_DEVICE_DATA,
                severity = Severity.INFO,
                priority = FocusEngine.PRIORITY_INFO - 10,
                discussionPoint = false,
            )
        }
        return out
    }

    // ---- Medicines ---------------------------------------------------------------------------

    fun medicineRules(input: FocusInput): List<FocusItem> {
        val out = mutableListOf<FocusItem>()
        val today = input.today
        val active = input.medicines.filter { it.isActive }

        active.mapNotNull { it.molecule?.trim()?.lowercase()?.takeIf { m -> m.isNotEmpty() } }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= 2 }
            .forEach { (molecule, count) ->
                out += FocusItem(
                    code = FocusCode.MEDICINE_DUPLICATE_MOLECULE,
                    severity = Severity.ATTENTION,
                    priority = FocusEngine.PRIORITY_ATTENTION - 5,
                    args = mapOf("molecule" to molecule, "count" to count.toString()),
                    evidence = active.filter { it.molecule?.lowercase() == molecule }.map { it.name },
                )
            }

        if (active.size >= 5) {
            out += FocusItem(
                code = FocusCode.MEDICINE_MANY_ACTIVE,
                severity = Severity.WATCH,
                priority = FocusEngine.PRIORITY_WATCH - 15,
                args = mapOf("count" to active.size.toString()),
                evidence = active.map { it.name }.take(6),
            )
        }

        active.filter { it.endDate != null && daysAgo(it.endDate!!, today) in 0..60 }.forEach { medicine ->
            out += FocusItem(
                code = FocusCode.MEDICINE_COURSE_ENDED,
                severity = Severity.INFO,
                priority = FocusEngine.PRIORITY_INFO,
                args = mapOf(
                    "name" to medicine.name,
                    "date" to medicine.endDate.toString(),
                    "days" to daysAgo(medicine.endDate!!, today).toString(),
                ),
                discussionPoint = true,
            )
        }

        active.filter { it.endDate == null && it.startDate != null && daysAgo(it.startDate!!, today) >= 120 }
            .forEach { medicine ->
                out += FocusItem(
                    code = FocusCode.MEDICINE_NO_END_DATE,
                    severity = Severity.INFO,
                    priority = FocusEngine.PRIORITY_INFO - 5,
                    args = mapOf(
                        "name" to medicine.name,
                        "months" to (daysAgo(medicine.startDate!!, today) / 30).toString(),
                    ),
                )
            }
        return out
    }

    // ---- Treatments --------------------------------------------------------------------------

    fun treatmentRules(input: FocusInput): List<FocusItem> {
        val out = mutableListOf<FocusItem>()
        input.treatments.forEach { treatment ->
            val followUp = treatment.followUpDate ?: return@forEach
            val days = ChronoUnit.DAYS.between(input.today, followUp)
            if (days in -30..21) {
                out += FocusItem(
                    code = FocusCode.TREATMENT_FOLLOW_UP_DUE,
                    severity = Severity.WATCH,
                    priority = FocusEngine.PRIORITY_WATCH + 5,
                    args = mapOf(
                        "name" to treatment.name,
                        "date" to followUp.toString(),
                        "days" to kotlin.math.abs(days).toString(),
                    ),
                )
            }
        }
        return out
    }

    // ---- Data quality / daily habits ----------------------------------------------------------

    fun dataQualityRules(input: FocusInput): List<FocusItem> {
        val out = mutableListOf<FocusItem>()
        val today = input.today

        val profile = input.profile
        if (profile == null || profile.dateOfBirth == null || profile.sex == com.solerunner.myhealth.domain.model.Sex.UNSPECIFIED) {
            out += FocusItem(
                code = FocusCode.MISSING_PROFILE_INFO,
                severity = Severity.INFO,
                priority = FocusEngine.PRIORITY_INFO - 10,
                discussionPoint = false,
            )
        }

        val reportDates = input.series.flatMap { it.points.map { point -> point.date } }.distinct().sorted()
        val lastReport = reportDates.lastOrNull()
        if (lastReport == null || daysAgo(lastReport, today) > 180) {
            out += FocusItem(
                code = FocusCode.NO_RECENT_REPORT,
                severity = Severity.INFO,
                priority = FocusEngine.PRIORITY_INFO + 5,
                args = mapOf(
                    "months" to (lastReport?.let { daysAgo(it, today) / 30 }?.toString() ?: "-"),
                ),
            )
        }

        if (reportDates.size >= 2) {
            val maxGap = reportDates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.max()
            if (maxGap > 548) {
                out += FocusItem(
                    code = FocusCode.DATA_GAP,
                    severity = Severity.INFO,
                    priority = FocusEngine.PRIORITY_INFO - 5,
                    args = mapOf("months" to (maxGap / 30).toString()),
                )
            }
        }

        val recentReports = reportDates.count { daysAgo(it, today) <= 365 }
        if (recentReports >= 3) {
            out += FocusItem(
                code = FocusCode.GOOD_CONSISTENCY,
                severity = Severity.INFO,
                priority = FocusEngine.PRIORITY_INFO - 15,
                args = mapOf("count" to recentReports.toString()),
                discussionPoint = false,
            )
        }

        val latestMeasurement = input.measurements.maxByOrNull { it.recordedDay }
        if (latestMeasurement == null || daysAgo(latestMeasurement.recordedDay, today) > 60) {
            out += FocusItem(
                code = FocusCode.NO_VITALS_LOGGED,
                severity = Severity.INFO,
                priority = FocusEngine.PRIORITY_INFO - 10,
                discussionPoint = false,
            )
        }

        val confidence = input.latestImportConfidence
        if (confidence != null && confidence < 0.5) {
            out += FocusItem(
                code = FocusCode.LOW_CONFIDENCE_EXTRACTION,
                severity = Severity.INFO,
                priority = FocusEngine.PRIORITY_INFO,
                args = mapOf("percent" to FocusEngine.format(confidence * 100, 0)),
            )
        }
        return out
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun daysAgo(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(date, today)

    private fun bpEvidence(measurements: List<Measurement>): List<String> =
        measurements.sortedByDescending { it.recordedAt }.take(4).map {
            FocusEngine.format(it.primaryValue, 0) + "/" + FocusEngine.format(it.secondaryValue ?: 0.0, 0) +
                "  -  " + it.recordedDay
        }

    private fun weightEvidence(weights: List<Measurement>): List<String> =
        weights.takeLast(4).map { FocusEngine.format(it.primaryValue, 1) + " kg  -  " + it.recordedDay }

    /** Least-squares slope expressed in units per month (x in days, scaled to 30.4 days). */
    internal fun slopePerMonth(points: List<Pair<LocalDate, Double>>): Double {
        if (points.size < 2) return 0.0
        val origin = points.first().first
        val xs = points.map { ChronoUnit.DAYS.between(origin, it.first).toDouble() / 30.4375 }
        val ys = points.map { it.second }
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
}
