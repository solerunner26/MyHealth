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

class SteadyTrendTest {
    private val today = Fx.today

    @Test fun hba1cRisingEveryTestIsFlaggedEvenInRange() {
        val r = listOf(Fx.lab("hba1c", today.minusMonths(8), 4.5, 4.0, 5.6), Fx.lab("hba1c", today.minusMonths(4), 4.8, 4.0, 5.6), Fx.lab("hba1c", today.minusDays(3), 5.1, 4.0, 5.6))
        val s = FocusEngine.evaluate(Fx.inputs(r)).filterIsInstance<FocusItem.SteadyTrend>().single()
        assertEquals("hba1c", s.analyteId); assertTrue(s.rising); assertEquals(2, s.steps); assertTrue(s.inRange)
        assertEquals(Severity.Low, s.severity); assertEquals(0.6, s.totalChange, 1e-9)
        assertEquals(listOf(4.5, 4.8, 5.1), s.evidence.map { it.value })
    }

    @Test fun fourStepsOrOutOfRangeIsModerate() {
        val v = listOf(4.5, 4.8, 5.1, 5.4)
        val r = v.mapIndexed { k, x -> Fx.lab("hba1c", today.minusMonths((3 - k) * 3L), x, 4.0, 5.6) }
        assertEquals(Severity.Moderate, FocusEngine.evaluate(Fx.inputs(r)).filterIsInstance<FocusItem.SteadyTrend>().single().severity)
    }

    @Test fun improvementsFlatAndBrokenRunsAreNotFlagged() {
        fun run(vals: List<Double>, a: String = "hba1c", lo: Double? = 4.0, hi: Double? = 5.6) = FocusEngine.evaluate(Fx.inputs(vals.mapIndexed { k, x -> Fx.lab(a, today.minusMonths((vals.size - k) * 3L), x, lo, hi) })).filterIsInstance<FocusItem.SteadyTrend>()
        assertTrue(run(listOf(5.4, 5.1, 4.8)).isEmpty())          // falling HbA1c = improving
        assertTrue(run(listOf(4.8, 4.81, 4.82)).isEmpty())        // < 1 % moves are noise
        assertTrue(run(listOf(4.5, 5.0, 4.9, 5.2)).isEmpty())     // run broken
        assertEquals(1, run(listOf(40.0, 36.0, 33.0), "hdl", 40.0, null).size) // higher-is-better falling
        assertEquals(1, run(listOf(2.0, 2.8, 3.6), "tsh", 0.27, 4.2).size)     // in-range analyte drifting to the upper limit
    }

    @Test fun weeklyBloodPressureRise() {
        val v = listOf(122.0, 128.0, 133.0).mapIndexed { k, x -> Fx.vital(app.myhealth.domain.model.VitalType.BloodPressure, today.minusWeeks((2 - k) * 2L), x, 80.0) }
        val s = FocusEngine.evaluate(Fx.inputs(vitals = v)).filterIsInstance<FocusItem.SteadyTrend>().single()
        assertEquals(app.myhealth.domain.model.VitalType.BloodPressure, s.vitalType); assertEquals(11.0, s.totalChange, 1e-9)
        // two readings the same week count once
        val sameWeek = listOf(Fx.vital(app.myhealth.domain.model.VitalType.BloodPressure, today.minusDays(2), 120.0, 80.0), Fx.vital(app.myhealth.domain.model.VitalType.BloodPressure, today.minusDays(1), 125.0, 80.0), Fx.vital(app.myhealth.domain.model.VitalType.BloodPressure, today, 130.0, 80.0))
        assertTrue(FocusEngine.evaluate(Fx.inputs(vitals = sameWeek)).none { it is FocusItem.SteadyTrend })
    }
}
