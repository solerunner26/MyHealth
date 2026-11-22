package app.myhealth.data

import app.myhealth.domain.model.Attachment
import app.myhealth.domain.model.AttachmentKind
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.model.Sex
import java.time.Instant
import java.time.LocalDate

object TestData {
    fun member(id: String = "m1", name: String = "Asha", rel: Relationship = Relationship.Self) =
        FamilyMember(id, name, rel, LocalDate.of(1985, 5, 1), Sex.Female, 160.0, allergies = listOf("Penicillin"), conditions = listOf("Hypothyroidism"), createdAtEpochMs = 1)
    fun report(id: String = "r1", member: String = "m1", date: LocalDate = LocalDate.of(2024, 3, 12)) =
        Report(id, member, ReportType.LabReport, "CBC", date, "Sunrise Lab", createdAt = Instant.ofEpochMilli(5))
    fun result(id: String, member: String = "m1", report: String? = "r1", analyte: String = "hb", date: LocalDate = LocalDate.of(2024, 3, 12), v: Double = 12.5) =
        LabResult(id, member, report, analyte, date, v, "g/dL", refLow = 12.0, refHigh = 15.5, printedName = "Haemoglobin", printedValue = v.toString(), printedUnit = "g/dL", printedRange = "12.0 - 15.5")
    fun attachment(id: String = "a1", report: String = "r1", sha: String = "abc") = Attachment(id, report, AttachmentKind.Pdf, "cbc.pdf", sha, 10, 1)
}
