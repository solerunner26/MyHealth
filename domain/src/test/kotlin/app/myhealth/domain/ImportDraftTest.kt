package app.myhealth.domain

import app.myhealth.domain.importing.DraftError
import app.myhealth.domain.importing.ImportDrafts
import app.myhealth.domain.importing.SourceFile
import app.myhealth.domain.model.AttachmentKind
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Sex
import app.myhealth.domain.parse.ReportParser
import app.myhealth.domain.util.IdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ImportDraftTest {
    private val member = FamilyMember("m1", "Ravi", Relationship.Self, LocalDate.of(1980, 1, 1), Sex.Male)
    private val today = LocalDate.of(2025, 1, 1)
    private fun ids(): IdGenerator { var i = 0; return IdGenerator { "id${i++}" } }
    private val src = SourceFile("r.pdf", AttachmentKind.Pdf, "sha", 100, 1, 0)

    private fun draft(text: String) = ImportDrafts.fromParsed(ReportParser(today).parse(listOf(text), Sex.Male, 45), member, src, null, "Lab report")

    @Test fun parsedRowsBecomeCanonicalDraftsAndBundle() {
        val d = draft("Collected: 12/03/2024\nTotal Leucocyte Count 7,800 /cumm 4000-11000\nGlucose Fasting 5.6 mmol/L 3.9-5.5\nBP 130/85")
        val wbc = d.labRows.first { it.analyteId == "wbc" }
        assertEquals("7.8", wbc.valueText); assertEquals("10³/µL", wbc.unit); assertEquals("4", wbc.lowText); assertEquals("7,800", wbc.printedValue)
        assertTrue(ImportDrafts.validate(d, today).isEmpty())
        val (bundle, att) = ImportDrafts.toBundle(d, ids(), Instant.EPOCH, ZoneOffset.UTC, today)
        assertEquals(LocalDate.of(2024, 3, 12), bundle.report.reportDate)
        assertEquals("sha", att.single().sha256); assertEquals(bundle.report.id, att.single().reportId)
        assertEquals(2, bundle.results.size)
        assertEquals(100.9, bundle.results.first { it.analyteId == "fbs" }.value, 0.1)
        assertEquals("7,800", bundle.results.first { it.analyteId == "wbc" }.printedValue)
        assertEquals(1, bundle.vitals.size); assertEquals(85.0, bundle.vitals.single().value2!!, 0.0)
        assertTrue(bundle.results.all { it.reportId == bundle.report.id && it.date == bundle.report.reportDate })
    }

    @Test fun userEditsAreValidatedAndConverted() {
        var d = draft("Date: 01/02/2024\nHaemoglobin 14 g/dL 13-17")
        val row = d.labRows.single()
        // user switches unit to g/L and types the value as printed in g/L
        d = d.copy(labRows = listOf(row.copy(unit = "g/L", valueText = "140", lowText = "130", highText = "170")))
        val (b, _) = ImportDrafts.toBundle(d, ids(), Instant.EPOCH, ZoneOffset.UTC, today)
        assertEquals(14.0, b.results.single().value, 1e-9); assertEquals(17.0, b.results.single().refHigh!!, 1e-9)

        val bad = d.copy(labRows = listOf(row.copy(valueText = "abc", lowText = "20", highText = "10", unit = "mmol/mol")))
        val errors = ImportDrafts.validate(bad, today).map { it.error }.toSet()
        assertEquals(setOf(DraftError.InvalidValue, DraftError.RangeInverted, DraftError.UnitNotConvertible), errors)
    }

    @Test fun draftLevelValidation() {
        val d = draft("Haemoglobin 14 g/dL 13-17")
        assertEquals(listOf(DraftError.MissingDate), ImportDrafts.validate(d, today).map { it.error })
        assertEquals(listOf(DraftError.FutureDate), ImportDrafts.validate(d.copy(reportDate = today.plusDays(3)), today).map { it.error })
        val (titled, _) = ImportDrafts.toBundle(d.copy(reportDate = today, title = " "), ids(), Instant.EPOCH, ZoneOffset.UTC, today, fallbackTitle = "Lab report")
        assertEquals("Lab report", titled.report.title)
        val dup = d.copy(reportDate = today, labRows = d.labRows + d.labRows.first().copy(key = "x"))
        assertTrue(DraftError.DuplicateAnalyte in ImportDrafts.validate(dup, today).map { it.error })
    }

    @Test fun excludedAndLowConfidenceRowsAreNotSaved() {
        val d = draft("Date: 01/02/2024\nHaemoglobin 1420 g/dL 13-17\nTSH 2.1 µIU/mL 0.27-4.2")
        assertFalse(d.labRows.first { it.analyteId == "hb" }.include) // implausible → unticked by default
        val (b, _) = ImportDrafts.toBundle(d, ids(), Instant.EPOCH, ZoneOffset.UTC, today)
        assertEquals(listOf("tsh"), b.results.map { it.analyteId })
    }

    @Test fun prescriptionBecomesMedicineCourse() {
        val d = draft("Date: 05/01/2024\nRx\nTab Metformin 500 mg 1-0-1 x 30 days")
        val (b, _) = ImportDrafts.toBundle(d, ids(), Instant.EPOCH, ZoneOffset.UTC, today)
        val m = b.medicines.single()
        assertEquals(LocalDate.of(2024, 1, 5), m.startDate); assertEquals(LocalDate.of(2024, 2, 3), m.endDate)
        assertEquals(2, m.reminderTimes.size); assertFalse(m.remindersEnabled)
    }

    @Test fun morePagesMergeIntoOneRecordWithoutDuplicates() {
        val first = draft("Collected: 12/03/2024\nHaemoglobin 13.1 g/dL 13-17")
        val edited = first.copy(labRows = first.labRows.map { it.copy(valueText = "13.4") })
        val page2 = ReportParser(today).parse(listOf("Haemoglobin 99 g/dL 13-17\nTSH 2.0 µIU/mL 0.27-4.2\nBP 120/80"), Sex.Male, 45)
        val merged = ImportDrafts.merge(edited, page2, SourceFile("p2.jpg", AttachmentKind.Jpeg, "sha2", 50, 1, 1))
        assertEquals(listOf("hb", "tsh"), merged.labRows.map { it.analyteId })
        assertEquals("13.4", merged.labRows.first().valueText) // user edit kept, page-2 duplicate ignored
        assertEquals(1, merged.vitals.size); assertEquals(2, merged.sources.size)
        assertEquals(merged.labRows.size, merged.labRows.map { it.key }.toSet().size) // keys stay unique
        val (b, att) = ImportDrafts.toBundle(merged, ids(), Instant.EPOCH, ZoneOffset.UTC, today)
        assertEquals(listOf("sha", "sha2"), att.map { it.sha256 }); assertEquals(2, b.report.attachments.size)
    }

    @Test fun manualDraftAndNewRow() {
        val d = ImportDrafts.emptyManual(member, today, "Manual entry")
        val withRow = d.copy(labRows = listOf(ImportDrafts.newLabRow("k", "hba1c").copy(valueText = "6.2")))
        val (b, att) = ImportDrafts.toBundle(withRow, ids(), Instant.EPOCH, ZoneOffset.UTC, today)
        assertTrue(att.isEmpty()); assertEquals(6.2, b.results.single().value, 0.0); assertEquals("HbA1c", b.results.single().printedName)
    }

    @Test fun filesPickedTogetherSplitByTestDateAndMergeOnTheSameDate() {
        fun d(text: String, sha: String) = ImportDrafts.fromParsed(ReportParser(today).parse(listOf(text), Sex.Male, 45), member,
            SourceFile("$sha.pdf", AttachmentKind.Pdf, sha, 10, 1, 0), null, "Lab report")
        val nov = d("Patient Name: Ravi Shah\nCollected: 16/11/2024\nLDL Cholesterol 165 mg/dL", "a")
        val jul = d("Patient Name: Ravi Shah\nCollected: 22/07/2024\nHaemoglobin 13.1 g/dL 13-17", "b")
        val nov2 = d("Patient Name: Ravi Shah\nCollected: 16/11/2024\nHbA1c 5.4 %\nLDL Cholesterol 999 mg/dL", "c")
        val undated = d("Discharge summary", "d")
        val otherPerson = d("Patient Name: Meera Shah\nCollected: 16/11/2024\nTSH 2.0 µIU/mL", "e")
        val groups = ImportDrafts.groupByDate(listOf(nov to "a", jul to "b", nov2 to "c", undated to "d", otherPerson to "e"))
        assertEquals(listOf(listOf("a", "c"), listOf("e"), listOf("b"), listOf("d")), groups.map { it.second })
        val merged = groups.first().first
        assertEquals(LocalDate.of(2024, 11, 16), merged.reportDate)
        assertEquals(listOf("ldl", "hba1c"), merged.labRows.map { it.analyteId }) // first file's LDL kept
        assertEquals("165", merged.labRows.first().valueText)
        assertEquals(listOf("a", "c"), merged.sources.map { it.sha256 })
        assertEquals(merged.labRows.size, merged.labRows.map { it.key }.toSet().size)
        assertEquals(null, groups.last().first.reportDate)
    }
}
