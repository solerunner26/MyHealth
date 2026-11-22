package app.myhealth.domain.summary

import app.myhealth.domain.analytics.FocusItem
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.VisitQuestion
import app.myhealth.domain.model.Vital
import java.time.LocalDate
import java.time.ZoneId

/**
 * One page to take to the doctor, built only when the user asks: what changed since the last visit, the trend
 * alerts, medicine changes and the user's own questions. Pure data; the app renders it.
 */
data class VisitPrep(
    val since: LocalDate,
    /** Tests measured after [since], with the value before it for comparison (null if first time). */
    val changedTests: List<Pair<LabResult, LabResult?>>,
    val newVitals: List<Vital>,
    val alerts: List<FocusItem>,
    val started: List<Medicine>,
    val stopped: List<Medicine>,
    val questions: List<VisitQuestion>,
) {
    val isEmpty get() = changedTests.isEmpty() && newVitals.isEmpty() && alerts.isEmpty() && started.isEmpty() && stopped.isEmpty() && questions.isEmpty()

    companion object {
        fun build(
            since: LocalDate, results: List<LabResult>, vitals: List<Vital>, focus: List<FocusItem>, medicines: List<Medicine>,
            questions: List<VisitQuestion>, zone: ZoneId,
        ): VisitPrep {
            val changed = results.filter { it.date.isAfter(since) }.groupBy { it.analyteId }.map { (_, rs) ->
                val latest = rs.maxBy { it.date }
                val before = results.filter { it.analyteId == latest.analyteId && it.date.isBefore(latest.date) }.maxByOrNull { it.date }
                latest to before
            }.sortedByDescending { it.first.date }
            val newVitals = vitals.filter { it.measuredAt.atZone(zone).toLocalDate().isAfter(since) }
                .groupBy { it.type }.map { (_, vs) -> vs.maxBy { it.measuredAt } }
            return VisitPrep(
                since, changed, newVitals, focus,
                started = medicines.filter { it.startDate.isAfter(since) },
                stopped = medicines.filter { m -> m.stopped || (m.endDate != null && m.endDate.isAfter(since) && !m.endDate.isAfter(LocalDate.now(zone))) }.filter { it.endDate?.isAfter(since) ?: true },
                questions = questions.filterNot { it.done },
            )
        }

        /** Default start: the last consultation's date, else 6 months ago. */
        fun defaultSince(lastVisit: LocalDate?, today: LocalDate): LocalDate = lastVisit ?: today.minusMonths(6)

    }
}
