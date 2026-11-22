package app.myhealth.domain.analytics

import app.myhealth.domain.analytics.Fx.lab
import app.myhealth.domain.analytics.Fx.today
import app.myhealth.domain.analytics.Fx.vital
import app.myhealth.domain.model.DailyActivity
import app.myhealth.domain.model.DoseLog
import app.myhealth.domain.model.DoseStatus
import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.Treatment
import app.myhealth.domain.model.VitalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class FocusEngineTest {
    private fun items(i: FocusInputs) = FocusEngine.evaluate(i)

    @Test fun outOfRangeSeverityTiers() {
        val r = listOf(
            lab("ldl", today.minusDays(10), 130.0, null, 100.0),  // 30% over → Moderate
            lab("tsh", today.minusDays(10), 4.5, 0.27, 4.2),      // 7% over → Low
            lab("k", today.minusDays(10), 6.5, 3.5, 5.1),         // critical → High
        )
        val f = items(Fx.inputs(r)).filterIsInstance<FocusItem.OutOfRange>().associateBy { it.analyteId }
        assertEquals(Severity.Moderate, f.getValue("ldl").severity); assertEquals(30.0, f.getValue("ldl").excessPct, 1e-9)
        assertEquals(Severity.Low, f.getValue("tsh").severity)
        assertEquals(Severity.High, f.getValue("k").severity); assertEquals(RangeStatus.CriticalHigh, f.getValue("k").status)
        // evidence carries the exact value, unit and range
        assertEquals(130.0, f.getValue("ldl").evidence.single().value, 0.0); assertEquals(100.0, f.getValue("ldl").evidence.single().refHigh!!, 0.0)
        // highest severity first
        assertTrue(items(Fx.inputs(r)).first().severity == Severity.High)
    }

    @Test fun rapidWorseningOnlyNotImprovement() {
        val worse = listOf(lab("creat", today.minusMonths(3), 1.0, 0.7, 1.3), lab("creat", today.minusDays(1), 1.5, 0.7, 1.3))
        val rc = items(Fx.inputs(worse)).filterIsInstance<FocusItem.RapidChange>().single()
        assertEquals("creat", rc.analyteId); assertTrue(rc.percentPerMonth > 10); assertEquals(2, rc.evidence.size)
        val better = listOf(lab("creat", today.minusMonths(3), 1.5, 0.7, 1.3), lab("creat", today.minusDays(1), 1.0, 0.7, 1.3))
        assertTrue(items(Fx.inputs(better)).none { it is FocusItem.RapidChange })
        // readings a few days apart are not a "rate"
        val close = listOf(lab("creat", today.minusDays(5), 1.0, 0.7, 1.3), lab("creat", today.minusDays(1), 1.5, 0.7, 1.3))
        assertTrue(items(Fx.inputs(close)).none { it is FocusItem.RapidChange })
    }

    @Test fun overdueMonitoringUsesAbnormalInterval() {
        val abnormal = items(Fx.inputs(listOf(lab("hba1c", today.minusMonths(5), 7.0, 4.0, 5.6)))).filterIsInstance<FocusItem.Overdue>().single()
        assertTrue(abnormal.lastAbnormal); assertEquals(Severity.Moderate, abnormal.severity); assertEquals(today.minusMonths(2), abnormal.dueDate)
        val normalRecent = items(Fx.inputs(listOf(lab("hba1c", today.minusMonths(5), 5.2, 4.0, 5.6))))
        assertTrue(normalRecent.none { it is FocusItem.Overdue }) // 12-month interval not yet passed
    }

    @Test fun vitalsAndDataGaps() {
        val v = listOf(vital(VitalType.BloodPressure, today.minusDays(2), 150.0, 95.0), vital(VitalType.Weight, today.minusDays(2), 95.0))
        val f = items(Fx.inputs(vitals = v))
        val bp = f.filterIsInstance<FocusItem.VitalAlert>().first { it.type == VitalType.BloodPressure }
        assertEquals(VitalBand.High, bp.band); assertEquals(Severity.Moderate, bp.severity)
        val bmi = f.filterIsInstance<FocusItem.VitalAlert>().first { it.type == VitalType.Bmi } // 95 kg / 1.75² = 31.0
        assertEquals(VitalBand.High, bmi.band); assertEquals(31.0, bmi.evidence.single().value, 0.05)
        assertTrue(f.any { it is FocusItem.DataGap && it.kind == FocusItem.GapKind.NoLabResults })
        assertTrue(f.none { it is FocusItem.DataGap && it.kind == FocusItem.GapKind.NoBloodPressure })
        val empty = items(Fx.inputs())
        assertEquals(setOf(FocusItem.GapKind.NoLabResults, FocusItem.GapKind.NoBloodPressure, FocusItem.GapKind.NoWeight), empty.filterIsInstance<FocusItem.DataGap>().map { it.kind }.toSet())
    }

    @Test fun medicineIssues() {
        val start = today.minusDays(20)
        val a = Medicine("a", "m", "Metformin", startDate = start, frequency = Frequency.TwiceDaily, reminderTimes = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)), remindersEnabled = true)
        val b = Medicine("b", "m", "metformin ", startDate = start.plusDays(5), remindersEnabled = true, reminderTimes = listOf(LocalTime.of(9, 0)))
        val ended = Medicine("c", "m", "Amoxicillin", startDate = today.minusDays(10), endDate = today.minusDays(3))
        val logs = (1..7).map { d -> DoseLog("l$d", "a", today.minusDays(d.toLong()).atTime(9, 0).toInstant(ZoneOffset.UTC), DoseStatus.Taken, today.atStartOfDay().toInstant(ZoneOffset.UTC)) }
        val f = items(Fx.inputs().copy(medicines = listOf(a, b, ended), doseLogs = logs)).filterIsInstance<FocusItem.MedicineIssue>()
        assertTrue(f.any { it.kind == FocusItem.MedicineIssueKind.Overlap && it.medicineIds.toSet() == setOf("a", "b") })
        val missed = f.first { it.kind == FocusItem.MedicineIssueKind.MissedDoses && it.medicineIds == listOf("a") }
        assertEquals(7, missed.count) // 14 expected, 7 taken
        assertTrue(f.any { it.kind == FocusItem.MedicineIssueKind.CourseEnded && it.medicineIds == listOf("c") })
    }

    @Test fun followUpsAndWearables() {
        val t = listOf(Treatment("t1", "m", "Knee physio", date = today.minusDays(30), followUpDate = today.minusDays(2)),
            Treatment("t2", "m", "Dental", date = today.minusDays(30), followUpDate = today.plusDays(60)))
        val acts = (1..35).map { d -> DailyActivity("m", today.minusDays(d.toLong()), steps = if (d <= 7) 3000 else 9000, restingHeartRate = if (d <= 7) 70.0 else 60.0) }
        val f = items(Fx.inputs().copy(treatments = t, activity = acts))
        val fu = f.filterIsInstance<FocusItem.FollowUp>().single()
        assertEquals("t1", fu.treatmentId); assertEquals(Severity.Moderate, fu.severity)
        val w = f.filterIsInstance<FocusItem.Wearable>().map { it.kind }.toSet()
        assertEquals(setOf(FocusItem.WearableKind.StepsDrop, FocusItem.WearableKind.RestingHeartRateRise), w)
    }

    @Test fun deterministicOrdering() {
        val r = listOf(lab("ldl", today.minusDays(3), 160.0, null, 100.0), lab("tsh", today.minusDays(3), 4.5, 0.27, 4.2))
        val a = items(Fx.inputs(r)); val b = items(Fx.inputs(r.reversed()))
        assertEquals(a.map { it.key }, b.map { it.key })
    }
}
