package app.myhealth.domain.summary

import app.myhealth.domain.analytics.AnalyteTrend
import app.myhealth.domain.analytics.Csv
import app.myhealth.domain.analytics.FocusEngine
import app.myhealth.domain.analytics.FocusInputs
import app.myhealth.domain.analytics.FocusItem
import app.myhealth.domain.analytics.Severity
import app.myhealth.domain.analytics.TrendEngine
import app.myhealth.domain.analytics.VitalBand
import app.myhealth.domain.analytics.VitalRules
import app.myhealth.domain.format.ValueFormat
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.Treatment
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalType
import java.time.LocalDate

/** Content of a doctor-visit summary. Pure data; the app renders it to PDF/CSV with localised labels. */
data class DoctorSummary(
    val member: FamilyMember,
    val generatedOn: LocalDate,
    val ageYears: Int?,
    val activeMedicines: List<Medicine>,
    /** Key and abnormal lab trends, abnormal first. */
    val labs: List<AnalyteTrend>,
    val vitals: List<Pair<Vital, VitalBand>>,
    val focus: List<FocusItem>,
    val treatments: List<Treatment>,
    val windowYears: Int,
)

object DoctorSummaryBuilder {
    const val MAX_LABS = 24
    const val MAX_FOCUS = 8

    fun build(i: FocusInputs): DoctorSummary {
        val age = i.member.ageYears(i.today)
        val trends = TrendEngine.all(i.results, i.today, i.historyYears, i.member.sex, age)
        val labs = trends.filter { it.latest.status.isAbnormal || it.analyte.keyMarker || !it.latest.date.isBefore(i.today.minusMonths(12)) }
            .sortedWith(compareByDescending<AnalyteTrend> { it.latest.status.isAbnormal }.thenBy { it.analyte.panel.ordinal }.thenByDescending { it.latest.date })
            .take(MAX_LABS)
        val vitals = VitalType.entries.mapNotNull { t -> i.vitals.filter { it.type == t }.maxByOrNull { it.measuredAt } }.map { it to VitalRules.band(it) }
        return DoctorSummary(
            member = i.member, generatedOn = i.today, ageYears = age,
            activeMedicines = i.medicines.filter { it.isActiveOn(i.today) }.sortedBy { it.name.lowercase() },
            labs = labs, vitals = vitals,
            focus = FocusEngine.evaluate(i).filter { it.severity != Severity.Info }.take(MAX_FOCUS),
            treatments = i.treatments.filter { !it.date.isBefore(i.today.minusYears(2)) || (it.followUpDate != null && !it.followUpDone) }.sortedByDescending { it.date },
            windowYears = i.historyYears,
        )
    }

    /** Localised labels for the CSV (column titles and section names). */
    data class CsvLabels(
        val header: List<String>, val profile: String, val medicine: String, val lab: String, val vital: String, val treatment: String,
        val analyteName: (String) -> String, val vitalName: (VitalType) -> String, val status: (app.myhealth.domain.analytics.RangeStatus) -> String,
        val band: (VitalBand) -> String, val allergies: String, val conditions: String, val bloodGroup: String, val dob: String,
    )

    fun csv(s: DoctorSummary, l: CsvLabels): String {
        val rows = mutableListOf<List<String>>()
        val m = s.member
        rows += listOf(l.profile, m.name, "", "", "", "")
        m.dateOfBirth?.let { rows += listOf(l.profile, l.dob, it.toString(), "", "", "") }
        if (m.bloodGroup != app.myhealth.domain.model.BloodGroup.Unknown) rows += listOf(l.profile, l.bloodGroup, m.bloodGroup.label, "", "", "")
        if (m.allergies.isNotEmpty()) rows += listOf(l.profile, l.allergies, m.allergies.joinToString("; "), "", "", "")
        if (m.conditions.isNotEmpty()) rows += listOf(l.profile, l.conditions, m.conditions.joinToString("; "), "", "", "")
        s.activeMedicines.forEach { med -> rows += listOf(l.medicine, med.name, med.strength, med.dose, med.startDate.toString(), med.frequency.name) }
        s.labs.forEach { t ->
            rows += listOf(l.lab, l.analyteName(t.analyte.id), ValueFormat.withComparator(t.latest.value, t.analyte.decimals, t.latest.comparator), t.analyte.canonicalUnit,
                t.latest.date.toString(), l.status(t.latest.status) + (ValueFormat.range(t.latest.refLow, t.latest.refHigh, t.analyte.decimals).takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""))
        }
        s.vitals.forEach { (v, b) ->
            val value = if (v.type.paired && v.value2 != null) "${ValueFormat.number(v.value, 0)}/${ValueFormat.number(v.value2, 0)}" else ValueFormat.compact(v.value, 1)
            rows += listOf(l.vital, l.vitalName(v.type), value, v.unit, v.measuredAt.toString().take(10), l.band(b))
        }
        s.treatments.forEach { t -> rows += listOf(l.treatment, t.name, t.provider, "", t.date.toString(), t.followUpDate?.toString() ?: "") }
        return Csv.document(l.header, rows)
    }
}
