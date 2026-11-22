package com.solerunner.myhealth.domain.analytics

import com.solerunner.myhealth.core.util.Dates
import com.solerunner.myhealth.domain.model.DoctorSummary
import com.solerunner.myhealth.domain.model.FocusItem
import com.solerunner.myhealth.domain.model.Severity
import com.solerunner.myhealth.domain.model.SummaryLine
import com.solerunner.myhealth.domain.model.TrendDirection
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * Builds the doctor-visit cheat sheet from the same snapshot the dashboard uses.
 *
 * The point is preparation, not diagnosis: it lists what changed, what is flagged, current medicines and
 * the questions worth asking. Pure Kotlin, so the wording rules are unit-testable.
 */
object DoctorSummaryBuilder {

    fun build(
        today: LocalDate,
        since: LocalDate,
        series: List<com.solerunner.myhealth.domain.model.AnalyteSeries>,
        focus: List<FocusItem>,
        medicines: List<String>,
        treatments: List<String>,
        deviceLines: List<SummaryLine>,
    ): DoctorSummary {
        val changed = series.mapNotNull { analyte ->
            val latest = analyte.latest ?: return@mapNotNull null
            val previous = analyte.previous ?: return@mapNotNull null
            val delta = latest.value - previous.value
            if (abs(delta) < analyte.decimals.toDouble()) return@mapNotNull null
            val direction = if (delta > 0) TrendDirection.RISING else TrendDirection.FALLING
            SummaryLine(
                label = analyte.displayName,
                value = format(latest.value, analyte.decimals) + " " + (analyte.unit ?: ""),
                detail = (if (direction == TrendDirection.RISING) "up from " else "down from ") +
                    format(previous.value, analyte.decimals) + " on " + previous.date,
            )
        }.sortedByDescending { it.label }

        val flagged = focus.filter { it.severity != Severity.INFO }.map { item ->
            SummaryLine(
                label = item.analyteName ?: item.code.name.lowercase().replace('_', ' '),
                value = item.code.name.lowercase().replace('_', ' '),
                detail = item.evidence.firstOrNull(),
            )
        }
        val improved = focus.filter { it.severity == Severity.INFO }.map { item ->
            SummaryLine(
                label = item.titleTextFallback(),
                value = "",
                detail = item.evidence.firstOrNull(),
            )
        }
        val questions = focus.filter { it.discussionPoint }.map { item ->
            SummaryLine(label = item.titleTextFallback(), value = "", detail = null)
        }

        return DoctorSummary(
            generatedAt = today,
            windowStart = since,
            headline = changed.take(12),
            flagged = flagged,
            improved = improved,
            medicines = medicines,
            treatments = treatments,
            deviceAverages = deviceLines,
            questions = questions,
        )
    }

    /** English fallback used only for the PDF; the app UI uses localized focus copy. */
    private fun FocusItem.titleTextFallback(): String = when {
        analyteName != null -> "$analyteName: " + code.name.lowercase().replace('_', ' ')
        else -> code.name.lowercase().replace('_', ' ')
    }

    fun windowStart(today: LocalDate): LocalDate = Dates.historyWindowStart(today)

    private fun format(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals.coerceIn(0, 2)}f", value)
}
