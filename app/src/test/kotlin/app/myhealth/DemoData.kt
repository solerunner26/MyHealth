package app.myhealth

import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.model.Sex
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalType
import app.myhealth.domain.repository.ImportBundle
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A realistic family record used by screenshot and UI tests. */
object DemoData {
    fun seed(app: TestApp) = runBlocking {
        val s = app.container.openNow()!!
        s.members.upsert(FamilyMember("m1", "Ravi Shah", Relationship.Self, LocalDate.of(1980, 4, 2), Sex.Male, 174.0))
        s.members.upsert(FamilyMember("m2", "Meera Shah", Relationship.Spouse, LocalDate.of(1983, 9, 12), Sex.Female, 160.0))
        val today = LocalDate.now()
        val dates = listOf(today.minusMonths(12), today.minusMonths(8), today.minusMonths(4), today.minusDays(6))
        val series = listOf(
            Triple("hba1c", listOf(4.5, 4.8, 5.1, 5.4), 4.0 to 5.6), Triple("hb", listOf(14.2, 13.9, 13.6, 13.8), 13.0 to 17.0),
            Triple("ldl", listOf(118.0, 124.0, 131.0, 138.0), null to 100.0), Triple("vitd", listOf(18.0, 22.0, 28.0, 34.0), 30.0 to 100.0),
            Triple("b12", listOf(260.0, 310.0, 420.0, 480.0), 211.0 to 911.0), Triple("tsh", listOf(2.1, 2.4, 2.2, 2.6), 0.27 to 4.2),
            Triple("creat", listOf(0.9, 0.95, 1.0, 1.05), 0.7 to 1.3), Triple("tg", listOf(160.0, 150.0, 142.0, 135.0), null to 150.0),
        )
        dates.forEachIndexed { i, d ->
            val rid = "r$i"
            val results = series.map { (k, v, r) -> LabResult("$rid-$k", "m1", rid, k, d, v[i], "", refLow = r.first, refHigh = r.second, printedName = k, printedValue = v[i].toString()) }
            fun at(h: Int) = d.atTime(h, 0).toInstant(ZoneOffset.UTC)
            val vitals = listOf(
                Vital("$rid-bp", "m1", VitalType.BloodPressure, 118.0 + i * 3, 78.0 + i, measuredAt = at(9)), Vital("$rid-p", "m1", VitalType.Pulse, 72.0 - i, measuredAt = at(9)),
                Vital("$rid-o", "m1", VitalType.SpO2, 98.0 + (i % 2), measuredAt = at(9)), Vital("$rid-w", "m1", VitalType.Weight, 71.5 - i * 1.0, measuredAt = at(9)),
                Vital("$rid-t", "m1", VitalType.Temperature, 36.7, measuredAt = at(9)),
            )
            s.records.saveImport(ImportBundle(Report(rid, "m1", ReportType.LabReport, "Health check", d, "Sunrise Diagnostics", createdAt = Instant.EPOCH), results, vitals,
                if (i == 3) listOf(Medicine("med1", "m1", "Vitamin D3", "60000 IU", startDate = d)) else emptyList()), emptyList())
        }
        app.container.preferences.setActiveMember("m1")
    }
}
