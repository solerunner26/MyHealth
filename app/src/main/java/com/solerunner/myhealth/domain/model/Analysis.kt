package com.solerunner.myhealth.domain.model

import java.time.LocalDate

/** One measured point of an analyte (or vital) on a date. */
data class SeriesPoint(
    val date: LocalDate,
    val value: Double,
    val status: ValueStatus,
    val source: DataSource,
    val reportId: Long? = null,
    val printedValue: Double? = null,
    val printedUnit: String? = null,
    val unit: String? = null,
)

/** Everything the app knows about one analyte over the three-year window, ordered oldest → newest. */
data class AnalyteSeries(
    val key: String,
    val displayName: String,
    val category: AnalyteCategory,
    val unit: String?,
    val directionOfGood: DirectionOfGood,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    val points: List<SeriesPoint>,
    val decimals: Int = 2,
) {
    val latest: SeriesPoint? get() = points.lastOrNull()
    val previous: SeriesPoint? get() = points.dropLast(1).lastOrNull()
    val first: SeriesPoint? get() = points.firstOrNull()
    val count: Int get() = points.size
    val isSparse: Boolean get() = points.size < 2

    /** Distinct report dates, newest first — the exact shape the longitudinal table renders. */
    val rowsNewestFirst: List<SeriesPoint> get() = points.asReversed()
}

data class TrendSummary(
    val latest: Double? = null,
    val previous: Double? = null,
    val delta: Double? = null,
    val percentChange: Double? = null,
    val perMonth: Double? = null,
    val direction: TrendDirection = TrendDirection.UNKNOWN,
    val monthsSpan: Double = 0.0,
    val status: ValueStatus = ValueStatus.UNKNOWN,
    val improving: Boolean? = null,
    val isSignificant: Boolean = false,
)

enum class ScoreBand { EXCELLENT, GOOD, FAIR, NEEDS_ATTENTION, INSUFFICIENT_DATA }

data class DomainScore(
    val key: String,
    val score: Int?,
    val weight: Double,
    val evidence: List<String> = emptyList(),
)

data class HealthScore(
    val total: Int?,
    val band: ScoreBand,
    val domains: List<DomainScore>,
    val completeness: Double,
)

/**
 * One "what should I focus on?" card. The engine is pure Kotlin and produces *structured* output:
 * the UI maps [code] to localized copy and renders [args] as placeholders. That keeps the rules
 * testable and every sentence translatable.
 */
data class FocusItem(
    val code: FocusCode,
    val severity: Severity,
    val priority: Int,
    val analyteKey: String? = null,
    val analyteName: String? = null,
    val args: Map<String, String> = emptyMap(),
    val evidence: List<String> = emptyList(),
    val relatedReportId: Long? = null,
    val discussionPoint: Boolean = true,
)

enum class FocusCode {
    VALUE_HIGH,
    VALUE_LOW,
    VALUE_CRITICAL_HIGH,
    VALUE_CRITICAL_LOW,
    VALUE_BACK_IN_RANGE,
    TREND_RISING,
    TREND_FALLING,
    RECHECK_OVERDUE,
    NO_RECENT_REPORT,
    DATA_GAP,
    BP_ELEVATED,
    BP_LOW,
    WEIGHT_TRENDING_UP,
    WEIGHT_TRENDING_DOWN,
    GLUCOSE_HIGH,
    HBA1C_HIGH,
    LOW_SLEEP,
    LOW_STEPS,
    HIGH_RESTING_HEART_RATE,
    LOW_SPO2,
    LOW_HRV,
    NO_DEVICE_DATA,
    MEDICINE_COURSE_ENDED,
    MEDICINE_DUPLICATE_MOLECULE,
    MEDICINE_MANY_ACTIVE,
    MEDICINE_NO_END_DATE,
    TREATMENT_FOLLOW_UP_DUE,
    MISSING_PROFILE_INFO,
    NO_VITALS_LOGGED,
    LOW_CONFIDENCE_EXTRACTION,
    GOOD_CONSISTENCY,
}

/** A doctor-visit cheat sheet: what changed, what is flagged, what to ask. */
data class DoctorSummary(
    val generatedAt: LocalDate,
    val windowStart: LocalDate,
    val headline: List<SummaryLine>,
    val flagged: List<SummaryLine>,
    val improved: List<SummaryLine>,
    val medicines: List<String>,
    val treatments: List<String>,
    val deviceAverages: List<SummaryLine>,
    val questions: List<SummaryLine>,
)

data class SummaryLine(
    val label: String,
    val value: String,
    val detail: String? = null,
)
