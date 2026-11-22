package app.myhealth.domain.analytics

import app.myhealth.domain.model.DailyActivity
import app.myhealth.domain.model.DoseLog
import app.myhealth.domain.model.DoseStatus
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.Treatment
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalType
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class Severity { Info, Low, Moderate, High }

/** One measurement shown as evidence under an insight. */
data class Evidence(val date: LocalDate, val value: Double, val value2: Double? = null, val unit: String, val refLow: Double? = null, val refHigh: Double? = null)

/**
 * A deterministic "what should I focus on" item. Each subtype carries everything the UI needs to render a localised
 * title, rationale and evidence; the engine never produces free text.
 */
sealed interface FocusItem {
    val key: String
    val severity: Severity
    val date: LocalDate
    val evidence: List<Evidence>

    data class OutOfRange(val analyteId: String, val status: RangeStatus, val excessPct: Double, override val severity: Severity, override val date: LocalDate, override val evidence: List<Evidence>) : FocusItem {
        override val key get() = "range:$analyteId"
    }
    data class RapidChange(val analyteId: String, val percentPerMonth: Double, val percentChange: Double, val months: Double, override val severity: Severity, override val date: LocalDate, override val evidence: List<Evidence>) : FocusItem {
        override val key get() = "rapid:$analyteId"
    }
    data class Overdue(val analyteId: String, val lastDate: LocalDate, val dueDate: LocalDate, val monthsOverdue: Int, val lastAbnormal: Boolean, override val severity: Severity, override val evidence: List<Evidence>) : FocusItem {
        override val key get() = "overdue:$analyteId"
        override val date get() = dueDate
    }
    data class VitalAlert(val type: VitalType, val band: VitalBand, override val severity: Severity, override val date: LocalDate, override val evidence: List<Evidence>) : FocusItem {
        override val key get() = "vital:${type.name}"
    }
    enum class GapKind { NoLabResults, NoRecentLabs, NoBloodPressure, NoWeight }
    data class DataGap(val kind: GapKind, val lastDate: LocalDate?, override val date: LocalDate) : FocusItem {
        override val key get() = "gap:${kind.name}"
        override val severity get() = Severity.Info
        override val evidence get() = emptyList<Evidence>()
    }
    enum class MedicineIssueKind { Overlap, MissedDoses, CourseEnded, NoReminders }
    data class MedicineIssue(val kind: MedicineIssueKind, val medicineIds: List<String>, val names: List<String>, val count: Int, override val severity: Severity, override val date: LocalDate) : FocusItem {
        override val key get() = "med:${kind.name}:${medicineIds.sorted().joinToString(",")}"
        override val evidence get() = emptyList<Evidence>()
    }
    data class FollowUp(val treatmentId: String, val name: String, val dueDate: LocalDate, val daysUntil: Long, override val severity: Severity) : FocusItem {
        override val key get() = "followup:$treatmentId"
        override val date get() = dueDate
        override val evidence get() = emptyList<Evidence>()
    }
    enum class WearableKind { StepsDrop, RestingHeartRateRise, ShortSleep }
    data class Wearable(val kind: WearableKind, val recent: Double, val baseline: Double, override val severity: Severity, override val date: LocalDate) : FocusItem {
        override val key get() = "wear:${kind.name}"
        override val evidence get() = emptyList<Evidence>()
    }
}

data class FocusInputs(
    val member: FamilyMember,
    val today: LocalDate,
    val zone: ZoneId,
    val results: List<LabResult>,
    val vitals: List<Vital> = emptyList(),
    val medicines: List<Medicine> = emptyList(),
    val doseLogs: List<DoseLog> = emptyList(),
    val treatments: List<Treatment> = emptyList(),
    val activity: List<DailyActivity> = emptyList(),
    val historyYears: Int = 5,
)

/** Pure, deterministic rule engine. Same inputs → same items in the same order. */
object FocusEngine {
    fun evaluate(i: FocusInputs): List<FocusItem> {
        val age = i.member.ageYears(i.today)
        val trends = TrendEngine.all(i.results, i.today, i.historyYears, i.member.sex, age)
        val out = mutableListOf<FocusItem>()
        trends.forEach { t -> outOfRange(t)?.let(out::add); rapidChange(t)?.let(out::add); overdue(t, i.today)?.let(out::add) }
        out += vitalAlerts(i)
        out += dataGaps(i)
        out += medicineIssues(i)
        out += followUps(i)
        out += wearable(i)
        return out.distinctBy { it.key }.sortedWith(compareByDescending<FocusItem> { it.severity.ordinal }.thenByDescending { it.date }.thenBy { it.key })
    }

    private fun ev(t: AnalyteTrend, p: TrendPoint) = Evidence(p.date, p.value, unit = t.analyte.canonicalUnit, refLow = p.refLow, refHigh = p.refHigh)

    fun outOfRange(t: AnalyteTrend): FocusItem.OutOfRange? {
        val p = t.latest
        if (!p.status.isAbnormal) return null
        val excess = TrendEngine.relativeExcess(p.value, p.refLow, p.refHigh)
        val sev = when {
            p.status.isCritical -> Severity.High
            excess >= 0.25 -> Severity.Moderate
            else -> Severity.Low
        }
        return FocusItem.OutOfRange(t.analyte.id, p.status, excess * 100, sev, p.date, listOfNotNull(t.previous?.let { ev(t, it) }, ev(t, p)))
    }

    /** Worsening faster than the analyte's threshold (%/month); improvements are not flagged. */
    fun rapidChange(t: AnalyteTrend): FocusItem.RapidChange? {
        val ppm = t.percentPerMonth ?: return null
        val prev = t.previous ?: return null
        val days = t.daysBetween ?: return null
        if (days < 14 || t.direction != TrendDirection.Worsening) return null
        val threshold = t.analyte.rapidChangePctPerMonth
        if (kotlin.math.abs(ppm) < threshold) return null
        val sev = if (kotlin.math.abs(ppm) >= threshold * 2) Severity.Moderate else Severity.Low
        return FocusItem.RapidChange(t.analyte.id, ppm, t.percentChange!!, days / TrendEngine.DAYS_PER_MONTH, sev, t.latest.date, listOf(ev(t, prev), ev(t, t.latest)))
    }

    fun overdue(t: AnalyteTrend, today: LocalDate): FocusItem.Overdue? {
        val a = t.analyte
        val abnormal = t.latest.status.isAbnormal
        val months = (if (abnormal) a.monitorMonthsAbnormal else a.monitorMonthsNormal) ?: return null
        val due = t.latest.date.plusMonths(months.toLong())
        if (!today.isAfter(due)) return null
        val over = ChronoUnit.MONTHS.between(due, today).toInt()
        val sev = if (abnormal) Severity.Moderate else Severity.Low
        return FocusItem.Overdue(a.id, t.latest.date, due, over, abnormal, sev, listOf(ev(t, t.latest)))
    }

    fun vitalAlerts(i: FocusInputs): List<FocusItem> {
        val since = i.today.minusDays(90)
        return listOf(VitalType.BloodPressure, VitalType.Pulse, VitalType.SpO2, VitalType.Bmi, VitalType.Temperature, VitalType.BloodGlucose, VitalType.RespiratoryRate).mapNotNull { type ->
            val latest = latestVital(i, type) ?: return@mapNotNull null
            val day = latest.measuredAt.atZone(i.zone).toLocalDate()
            if (day.isBefore(since)) return@mapNotNull null
            val band = VitalRules.band(latest)
            val sev = when (band) {
                VitalBand.Normal, VitalBand.Unknown -> return@mapNotNull null
                VitalBand.VeryHigh -> Severity.High
                VitalBand.High -> Severity.Moderate
                VitalBand.Low -> if (type == VitalType.SpO2 || type == VitalType.BloodGlucose) Severity.Moderate else Severity.Low
                VitalBand.Elevated -> Severity.Low
            }
            FocusItem.VitalAlert(type, band, sev, day, listOf(Evidence(day, latest.value, latest.value2, latest.unit)))
        }
    }

    /** BMI is derived from the latest weight and member height when not recorded directly. */
    private fun latestVital(i: FocusInputs, type: VitalType): Vital? {
        val direct = i.vitals.filter { it.type == type }.maxByOrNull { it.measuredAt }
        if (type != VitalType.Bmi) return direct
        val w = i.vitals.filter { it.type == VitalType.Weight }.maxByOrNull { it.measuredAt }
        val h = i.member.heightCm ?: i.vitals.filter { it.type == VitalType.Height }.maxByOrNull { it.measuredAt }?.value
        val derived = if (w != null && h != null) VitalRules.bmi(w.value, h)?.let { w.copy(type = VitalType.Bmi, value = it, unit = VitalType.Bmi.canonicalUnit) } else null
        return listOfNotNull(direct, derived).maxByOrNull { it.measuredAt }
    }

    fun dataGaps(i: FocusInputs): List<FocusItem> {
        val out = mutableListOf<FocusItem>()
        val lastLab = i.results.maxOfOrNull { it.date }
        when {
            lastLab == null -> out += FocusItem.DataGap(FocusItem.GapKind.NoLabResults, null, i.today)
            lastLab.isBefore(i.today.minusMonths(12)) -> out += FocusItem.DataGap(FocusItem.GapKind.NoRecentLabs, lastLab, i.today)
        }
        val adult = (i.member.ageYears(i.today) ?: 30) >= 18
        val lastBp = i.vitals.filter { it.type == VitalType.BloodPressure }.maxOfOrNull { it.measuredAt }?.atZone(i.zone)?.toLocalDate()
        if (adult && (lastBp == null || lastBp.isBefore(i.today.minusMonths(12)))) out += FocusItem.DataGap(FocusItem.GapKind.NoBloodPressure, lastBp, i.today)
        val lastW = i.vitals.filter { it.type == VitalType.Weight }.maxOfOrNull { it.measuredAt }?.atZone(i.zone)?.toLocalDate()
            ?: i.activity.filter { it.weightKg != null }.maxOfOrNull { it.date }
        if (lastW == null || lastW.isBefore(i.today.minusMonths(6))) out += FocusItem.DataGap(FocusItem.GapKind.NoWeight, lastW, i.today)
        return out
    }

    fun medicineIssues(i: FocusInputs): List<FocusItem> {
        val out = mutableListOf<FocusItem>()
        val active = i.medicines.filter { it.isActiveOn(i.today) }
        // the same medicine twice at once (e.g. imported from two prescriptions)
        active.groupBy { it.name.trim().lowercase() }.values.filter { it.size > 1 }.forEach { dup ->
            out += FocusItem.MedicineIssue(FocusItem.MedicineIssueKind.Overlap, dup.map { it.id }, dup.map { it.name }, dup.size, Severity.Moderate, i.today)
        }
        // missed (skipped or never confirmed) scheduled doses in the last 7 days for reminder-enabled medicines
        val weekAgo = i.today.minusDays(7)
        active.filter { it.remindersEnabled && it.frequency != Frequency.AsNeeded && it.reminderTimes.isNotEmpty() }.forEach { m ->
            val logs = i.doseLogs.filter { it.medicineId == m.id }
            val start = maxOf(weekAgo, m.startDate)
            var expected = 0
            var d = start
            while (d.isBefore(i.today)) { // completed days only
                if (ChronoUnit.DAYS.between(m.startDate, d) % m.frequency.everyNDays == 0L) expected += m.reminderTimes.size
                d = d.plusDays(1)
            }
            val taken = logs.count { it.status == DoseStatus.Taken && !it.scheduledAt.atZone(i.zone).toLocalDate().isBefore(start) && it.scheduledAt.atZone(i.zone).toLocalDate().isBefore(i.today) }
            val missed = expected - taken
            if (expected > 0 && missed >= 3) out += FocusItem.MedicineIssue(FocusItem.MedicineIssueKind.MissedDoses, listOf(m.id), listOf(m.name), missed,
                if (missed >= expected / 2) Severity.Moderate else Severity.Low, i.today)
        }
        // course end date passed but never marked finished: ask the user to confirm
        i.medicines.filter { !it.stopped && it.endDate != null && it.endDate.isBefore(i.today) && !it.endDate.isBefore(i.today.minusDays(30)) }.forEach { m ->
            out += FocusItem.MedicineIssue(FocusItem.MedicineIssueKind.CourseEnded, listOf(m.id), listOf(m.name), 0, Severity.Low, m.endDate!!)
        }
        val noRem = active.filter { !it.remindersEnabled && it.frequency != Frequency.AsNeeded }
        if (noRem.isNotEmpty()) out += FocusItem.MedicineIssue(FocusItem.MedicineIssueKind.NoReminders, noRem.map { it.id }, noRem.map { it.name }, noRem.size, Severity.Info, i.today)
        return out
    }

    fun followUps(i: FocusInputs): List<FocusItem> = i.treatments.filter { it.followUpDate != null && !it.followUpDone }.mapNotNull { t ->
        val days = ChronoUnit.DAYS.between(i.today, t.followUpDate)
        when {
            days < 0 -> FocusItem.FollowUp(t.id, t.name, t.followUpDate!!, days, Severity.Moderate)
            days <= 14 -> FocusItem.FollowUp(t.id, t.name, t.followUpDate!!, days, Severity.Low)
            else -> null
        }
    }

    fun wearable(i: FocusInputs): List<FocusItem> {
        val out = mutableListOf<FocusItem>()
        val recent = i.activity.filter { !it.date.isBefore(i.today.minusDays(7)) && it.date.isBefore(i.today) }
        val base = i.activity.filter { !it.date.isBefore(i.today.minusDays(35)) && it.date.isBefore(i.today.minusDays(7)) }
        fun avg(xs: List<Double>) = if (xs.size >= 4) xs.average() else null
        val rs = avg(recent.mapNotNull { it.steps?.toDouble() }); val bs = avg(base.mapNotNull { it.steps?.toDouble() })
        if (rs != null && bs != null && bs > 1000 && rs < bs * 0.7) out += FocusItem.Wearable(FocusItem.WearableKind.StepsDrop, rs, bs, Severity.Low, i.today)
        val rh = avg(recent.mapNotNull { it.restingHeartRate }); val bh = avg(base.mapNotNull { it.restingHeartRate })
        if (rh != null && bh != null && rh - bh >= 7) out += FocusItem.Wearable(FocusItem.WearableKind.RestingHeartRateRise, rh, bh, Severity.Low, i.today)
        val sl = avg(recent.mapNotNull { it.sleepMinutes?.toDouble() })
        if (sl != null && sl < 360) out += FocusItem.Wearable(FocusItem.WearableKind.ShortSleep, sl, 420.0, Severity.Info, i.today)
        return out
    }
}
