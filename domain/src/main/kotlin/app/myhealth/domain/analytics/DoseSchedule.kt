package app.myhealth.domain.analytics

import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.Medicine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure dose-time arithmetic used by reminders and adherence. DST-safe: times are local wall-clock times. */
object DoseSchedule {
    fun isDoseDay(m: Medicine, day: LocalDate): Boolean {
        if (day.isBefore(m.startDate) || (m.endDate != null && day.isAfter(m.endDate)) || m.stopped) return false
        val n = m.frequency.everyNDays.coerceAtLeast(1)
        return ChronoUnit.DAYS.between(m.startDate, day) % n == 0L
    }

    /** All scheduled dose instants in [from, to). */
    fun occurrences(m: Medicine, from: Instant, to: Instant, zone: ZoneId): List<Instant> {
        if (m.frequency == Frequency.AsNeeded || m.reminderTimes.isEmpty()) return emptyList()
        val out = mutableListOf<Instant>()
        var day = from.atZone(zone).toLocalDate()
        val last = to.atZone(zone).toLocalDate()
        while (!day.isAfter(last)) {
            if (isDoseDay(m, day)) m.reminderTimes.sorted().forEach { t ->
                val at = day.atTime(t).atZone(zone).toInstant()
                if (!at.isBefore(from) && at.isBefore(to)) out += at
            }
            day = day.plusDays(1)
        }
        return out
    }

    /** Next dose strictly after [now], searching up to 400 days ahead; null when the course has no future dose. */
    fun next(m: Medicine, now: Instant, zone: ZoneId): Instant? {
        if (!m.remindersEnabled) return null
        val horizon = now.plus(400, ChronoUnit.DAYS)
        val end = m.endDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.let { minOf(it, horizon) } ?: horizon
        var from = now.plusMillis(1)
        while (from.isBefore(end)) {
            val chunkEnd = minOf(from.plus(31, ChronoUnit.DAYS), end)
            occurrences(m, from, chunkEnd, zone).firstOrNull()?.let { return it }
            from = chunkEnd
        }
        return null
    }
}
