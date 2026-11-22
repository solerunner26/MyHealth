package app.myhealth.domain.analytics

import app.myhealth.domain.analytics.Fx.lab
import app.myhealth.domain.analytics.Fx.today
import app.myhealth.domain.analytics.Fx.vital
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.VitalType
import app.myhealth.domain.summary.DoctorSummaryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorSummaryTest {
    private val inputs = Fx.inputs(
        listOf(lab("ldl", today.minusDays(5), 160.0, null, 100.0), lab("hb", today.minusDays(5), 14.0, 13.0, 17.0), lab("mcv", today.minusYears(3), 90.0, 83.0, 101.0)),
        listOf(vital(VitalType.BloodPressure, today.minusDays(1), 150.0, 95.0)),
    ).copy(medicines = listOf(Medicine("a", "m", "Rosuvastatin", "10 mg", startDate = today.minusDays(30)), Medicine("b", "m", "Old", startDate = today.minusYears(1), endDate = today.minusMonths(6))),
        member = Fx.member.copy(allergies = listOf("Penicillin, severe")))

    @Test fun contentSelection() {
        val s = DoctorSummaryBuilder.build(inputs)
        assertEquals(listOf("Rosuvastatin"), s.activeMedicines.map { it.name })
        assertEquals("ldl", s.labs.first().analyte.id) // abnormal first
        assertTrue(s.labs.none { it.analyte.id == "mcv" }) // old, normal, non-key → omitted
        assertEquals(VitalBand.High, s.vitals.single().second)
        assertTrue(s.focus.all { it.severity != Severity.Info })
        assertTrue(s.focus.any { it is FocusItem.OutOfRange && it.analyteId == "ldl" })
    }

    @Test fun csvIsLocalisableAndEscaped() {
        val s = DoctorSummaryBuilder.build(inputs)
        val csv = DoctorSummaryBuilder.csv(s, DoctorSummaryBuilder.CsvLabels(
            listOf("Section", "Item", "Value", "Unit", "Date", "Note"), "Profile", "Medicine", "Lab", "Vital", "Treatment",
            { it.uppercase() }, { it.name }, { it.name }, { it.name }, "Allergies", "Conditions", "Blood group", "Date of birth"))
        val lines = csv.trimEnd().split("\r\n")
        assertEquals("Section,Item,Value,Unit,Date,Note", lines[0])
        assertTrue(lines.contains("Profile,Allergies,\"Penicillin, severe\",,,"))
        assertTrue(lines.any { it.startsWith("Lab,LDL,160,mg/dL,") && it.endsWith("High (< 100)") })
        assertTrue(lines.any { it.startsWith("Vital,BloodPressure,150/95,mmHg,") })
        assertTrue(lines.any { it.startsWith("Medicine,Rosuvastatin,10 mg") })
    }
}
