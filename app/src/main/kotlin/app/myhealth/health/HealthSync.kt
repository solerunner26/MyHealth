package app.myhealth.health

import app.myhealth.domain.model.DailyActivity
import app.myhealth.domain.repository.ActivityRepository
import java.time.Clock
import java.time.LocalDate

enum class HcStatus { Available, NotInstalled, UpdateRequired, Unsupported }
enum class HcData { Steps, HeartRate, RestingHeartRate, Sleep, Weight }

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
}

data class SyncResult(val daysWritten: Int, val full: Boolean)

/**
 * Incremental, local-only sync. First run (or an expired token / any deletion upstream): aggregate the last
 * [WINDOW_DAYS] days. Later runs: re-aggregate only days that changed, plus yesterday and today (partial days).
 * Health Connect data is attributed to the device owner's own profile.
 */
class HealthSync(private val source: HealthSource, private val activity: ActivityRepository, private val clock: Clock) {
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
        return SyncResult(rows.size, full)
    }

    /** Disconnect: revoke permissions and remove everything imported, as the user asked. */
    suspend fun disconnect(memberId: String) {
        runCatching { source.revokeAll() }
        activity.clear(memberId)
    }

    companion object { const val WINDOW_DAYS = 30 }
}
