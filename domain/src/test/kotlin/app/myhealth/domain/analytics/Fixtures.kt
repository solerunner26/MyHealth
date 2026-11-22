package app.myhealth.domain.analytics

import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Sex
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalType
import java.time.LocalDate
import java.time.ZoneOffset

object Fx {
    val today: LocalDate = LocalDate.of(2025, 6, 1)
    val member = FamilyMember("m", "Ravi", Relationship.Self, LocalDate.of(1980, 1, 1), Sex.Male, heightCm = 175.0)
    private var n = 0
    fun lab(analyte: String, date: LocalDate, v: Double, lo: Double? = null, hi: Double? = null) =
        LabResult("r${n++}", "m", null, analyte, date, v, "", refLow = lo, refHigh = hi)
    fun vital(type: VitalType, date: LocalDate, v: Double, v2: Double? = null) =
        Vital("v${n++}", "m", type, v, v2, measuredAt = date.atTime(9, 0).toInstant(ZoneOffset.UTC))
    fun inputs(results: List<LabResult> = emptyList(), vitals: List<Vital> = emptyList()) = FocusInputs(member, today, ZoneOffset.UTC, results, vitals)
}
