package app.myhealth.health

import app.myhealth.domain.model.DailyActivity
import app.myhealth.domain.repository.ActivityRepository
import java.time.Clock
import java.time.LocalDate

enum class HcStatus { Available, NotInstalled, UpdateRequired, Unsupported }
enum class HcData { Steps, HeartRate, RestingHeartRate, Sleep, Weight, BloodPressure, OxygenSaturation, BloodGlucose, BodyTemperature, RespiratoryRate }

/** One measurement from a fitness device / app via Health Connect, with its stable Health Connect record id. */
data class HcReading(val id: String, val type: app.myhealth.domain.model.VitalType, val value: Double, val value2: Double?, val unit: String, val time: java.time.Instant)

data class DayAggregate(val date: LocalDate, val steps: Long?, val avgHr: Double?, val restingHr: Double?, val sleepMinutes: Long?, val weightKg: Double?)
data class HcChanges(val days: Set<LocalDate>, val fullResync: Boolean, val nextToken: String)

/** Abstraction over Health Connect so the sync logic is testable without the provider app. */
interface HealthSource {
    fun status(): HcStatus
    suspend fun granted(): Set<HcData>
    suspend fun dailyAggregates(from: LocalDate, toExclusive: LocalDate): List<DayAggregate>
    suspend fun changesToken(): String
    suspend fun changes(token: String): HcChanges
    suspend fun revokeAll()
    /** Individual vital readings (BP, SpO2, glucose, temperature, respiratory rate, weight) in [from, to). */
    suspend fun readings(from: java.time.Instant, to: java.time.Instant): List<HcReading> = emptyList()
}

data class SyncResult(val daysWritten: Int, val full: Boolean)

/**
 * Incremental, local-only sync. First run (or an expired token / any deletion upstream): aggregate the last
 * [WINDOW_DAYS] days. Later runs: re-aggregate only days that changed, plus yesterday and today (partial days).
 * Health Connect data is attributed to the device owner's own profile.
 */
class HealthSync(
    private val source: HealthSource,
    private val activity: ActivityRepository,
    private val clock: Clock,
    private val vitals: app.myhealth.domain.repository.VitalRepository? = null,
) {
    suspend fun sync(memberId: String): SyncResult {
        if (source.status() != HcStatus.Available || source.granted().isEmpty()) return SyncResult(0, false)
        val today = LocalDate.now(clock)
        val token = activity.syncToken(memberId)
        var full = token == null
        var days: Set<LocalDate> = emptySet()
        var next: String? = null
        if (token != null) {
            val ch = runCatching { source.changes(token) }.getOrNull()
            if (ch == null || ch.fullResync) full = true else { days = ch.days; next = ch.nextToken }
        }
        val from = if (full) today.minusDays(WINDOW_DAYS - 1L) else (days + today.minusDays(1)).filter { !it.isBefore(today.minusDays(WINDOW_DAYS - 1L)) }.minOrNull() ?: today.minusDays(1)
        val aggregates = source.dailyAggregates(from, today.plusDays(1))
        val wanted = if (full) null else days + today + today.minusDays(1)
        val rows = aggregates.filter { wanted == null || it.date in wanted }
            .filter { it.steps != null || it.avgHr != null || it.restingHr != null || it.sleepMinutes != null || it.weightKg != null }
            .map { DailyActivity(memberId, it.date, it.steps, it.restingHr, it.avgHr, it.sleepMinutes, it.weightKg) }
        activity.upsertAll(rows)
        activity.setSyncToken(memberId, next ?: source.changesToken())
        syncVitals(memberId, rows)
        return SyncResult(rows.size, full)
    }

    /**
     * Item 1: readings measured by smart devices become vitals automatically. Individual readings keep their Health
     * Connect id ("hc-<id>"), daily pulse and weight one per day ("hc-pulse-<date>"), so re-syncing never duplicates.
     */
    private suspend fun syncVitals(memberId: String, days: List<DailyActivity>) {
        val repo = vitals ?: return
        val now = clock.instant()
        val last = activity.syncToken(VITALS_KEY + memberId)?.toLongOrNull()?.let(java.time.Instant::ofEpochMilli)
        val from = (last?.minus(java.time.Duration.ofDays(1)) ?: now.minus(java.time.Duration.ofDays(WINDOW_DAYS.toLong()))).coerceAtLeast(now.minus(java.time.Duration.ofDays(WINDOW_DAYS.toLong())))
        val src = app.myhealth.domain.model.VitalSource.HealthConnect
        runCatching { source.readings(from, now) }.getOrDefault(emptyList()).take(MAX_READINGS).forEach { r ->
            repo.upsert(app.myhealth.domain.model.Vital("hc-" + r.id.take(60), memberId, r.type, r.value, r.value2, r.unit, r.time, src))
        }
        val noon = { d: LocalDate -> d.atTime(12, 0).atZone(clock.zone).toInstant() }
        days.forEach { d ->
            (d.restingHeartRate ?: d.avgHeartRate)?.let { repo.upsert(app.myhealth.domain.model.Vital("hc-pulse-${d.date}", memberId, app.myhealth.domain.model.VitalType.Pulse, Math.round(it).toDouble(), measuredAt = noon(d.date), source = src)) }
            d.weightKg?.let { repo.upsert(app.myhealth.domain.model.Vital("hc-weight-${d.date}", memberId, app.myhealth.domain.model.VitalType.Weight, Math.round(it * 10) / 10.0, measuredAt = noon(d.date), source = src)) }
        }
        activity.setSyncToken(VITALS_KEY + memberId, now.toEpochMilli().toString())
    }

    /** Disconnect: revoke permissions and remove everything imported, as the user asked. */
    suspend fun disconnect(memberId: String) {
        runCatching { source.revokeAll() }
        activity.clear(memberId)
    }

    companion object {
        const val WINDOW_DAYS = 30
        const val MAX_READINGS = 2000
        private const val VITALS_KEY = "vitals."
    }
}
