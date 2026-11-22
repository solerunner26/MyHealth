package app.myhealth.domain

import app.myhealth.domain.family.FamilyHistory
import app.myhealth.domain.family.VaccineSchedule
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.RelativeCondition
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Sex
import app.myhealth.domain.model.Vaccination
import app.myhealth.domain.model.VisitQuestion
import app.myhealth.domain.summary.VisitPrep
import app.myhealth.domain.util.IdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FamilyFeaturesTest {
    private fun ids(): IdGenerator { var i = 0; return IdGenerator { "id${i++}" } }
    private val today = LocalDate.of(2026, 11, 22)

    @Test fun childScheduleFromBirthSkipsRecordedDosesAndAdultsGetBoosters() {
        val baby = FamilyMember("b", "Kiara", Relationship.Child, LocalDate.of(2026, 9, 1), Sex.Female)
        val recorded = listOf(Vaccination("x", "b", "BCG", "Birth", givenDate = LocalDate.of(2026, 9, 1)))
        val s = VaccineSchedule.suggest(baby, recorded, ids(), today, 0)
        assertFalse(s.any { it.name == "BCG" })
        assertEquals(LocalDate.of(2026, 10, 13), s.first { it.name == "Pentavalent" && it.dose == "1" }.dueDate) // 6 weeks
        assertTrue(s.any { it.name == "HPV" }) // female-only entry kept for a girl
        assertTrue(s.all { it.givenDate == null })
        val boy = baby.copy(id = "c", sex = Sex.Male)
        assertFalse(VaccineSchedule.suggest(boy, emptyList(), ids(), today, 0).any { it.name == "HPV" })
        val grandpa = FamilyMember("g", "Dada", Relationship.Parent, LocalDate.of(1955, 1, 1), Sex.Male)
        assertEquals(setOf("Td", "Influenza", "Pneumococcal"), VaccineSchedule.suggest(grandpa, emptyList(), ids(), today, 0).map { it.name }.toSet())
        assertTrue(VaccineSchedule.suggest(grandpa.copy(dateOfBirth = null), emptyList(), ids(), today, 0).isEmpty())
    }

    @Test fun familyMapGroupsSpellingVariantsAndFlagsWhatRunsInTheFamily() {
        val members = listOf(
            FamilyMember("m1", "Ravi", Relationship.Self, conditions = listOf("Type 2 Diabetes", "Hypertension")),
            FamilyMember("m2", "Meera", Relationship.Spouse, conditions = listOf("Thyroid (hypo)")),
        )
        val relatives = listOf(RelativeCondition("r1", "Father", "diabetes"), RelativeCondition("r2", "Grandmother", "High blood pressure"), RelativeCondition("r3", "Uncle", "Asthma"))
        val rows = FamilyHistory.map(members, relatives) { it.relationship.name }
        val diabetes = rows.first { FamilyHistory.normalise(it.condition) == "diabetes" }
        assertEquals(2, diabetes.carriers.size); assertTrue(diabetes.runsInFamily)
        assertTrue(rows.first { FamilyHistory.normalise(it.condition) == "hypertension" }.runsInFamily)
        assertFalse(rows.first { it.condition == "Asthma" }.runsInFamily)
        assertTrue(rows.indexOf(diabetes) < rows.indexOfFirst { it.condition == "Asthma" }) // shared conditions first
    }

    @Test fun visitPrepShowsChangesSinceTheLastVisitWithPreviousValues() {
        fun r(a: String, d: LocalDate, v: Double) = LabResult("$a$d", "m1", null, a, d, v, "")
        val since = LocalDate.of(2026, 6, 1)
        val results = listOf(r("ldl", LocalDate.of(2026, 1, 5), 120.0), r("ldl", LocalDate.of(2026, 10, 1), 165.0), r("hb", LocalDate.of(2026, 2, 1), 13.0))
        val meds = listOf(Medicine("a", "m1", "Atorvastatin", startDate = LocalDate.of(2026, 10, 2)), Medicine("b", "m1", "Vitamin D", startDate = LocalDate.of(2025, 1, 1)))
        val prep = VisitPrep.build(since, results, emptyList(), emptyList(), meds, listOf(VisitQuestion("q", "m1", "Repeat lipids?"), VisitQuestion("d", "m1", "old", done = true)), ZoneOffset.UTC)
        assertEquals(1, prep.changedTests.size)
        assertEquals(165.0, prep.changedTests.single().first.value, 0.0); assertEquals(120.0, prep.changedTests.single().second!!.value, 0.0)
        assertEquals(listOf("Atorvastatin"), prep.started.map { it.name })
        assertEquals(listOf("Repeat lipids?"), prep.questions.map { it.text })
        assertEquals(LocalDate.of(2026, 5, 22), VisitPrep.defaultSince(null, today))
    }
}
