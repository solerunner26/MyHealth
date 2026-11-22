package app.myhealth.domain.analytics

import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.VitalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CareRulesTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun med(f: Frequency, times: List<LocalTime>, start: LocalDate = LocalDate.of(2025, 1, 1), end: LocalDate? = null) =
        Medicine("m", "x", "Metformin", frequency = f, startDate = start, endDate = end, reminderTimes = times, remindersEnabled = true)

    @Test fun vitalEntryValidatesAndConverts() {
        assertEquals(72.6, VitalEntry.parse(VitalType.Weight, "160", "", EntryUnit.Pound).value, 0.05)
        assertEquals(37.0, VitalEntry.parse(VitalType.Temperature, "98.6", "", EntryUnit.Fahrenheit).value, 0.05)
        assertEquals(126.0, VitalEntry.parse(VitalType.BloodGlucose, "7.0", "", EntryUnit.MmolL).value, 0.0)
        val bp = VitalEntry.parse(VitalType.BloodPressure, "128", "84", EntryUnit.Canonical)
        assertTrue(bp.ok); assertEquals(84.0, bp.value2!!, 0.0)
        assertEquals(VitalEntryError.DiastolicNotBelowSystolic, VitalEntry.parse(VitalType.BloodPressure, "80", "120", EntryUnit.Canonical).errors["value2"])
        assertEquals(VitalEntryError.OutOfBounds, VitalEntry.parse(VitalType.SpO2, "140", "", EntryUnit.Canonical).errors["value"])
        assertEquals(VitalEntryError.NotANumber, VitalEntry.parse(VitalType.Pulse, "abc", "", EntryUnit.Canonical).errors["value"])
        assertEquals(VitalEntryError.Missing, VitalEntry.parse(VitalType.Pulse, " ", "", EntryUnit.Canonical).errors["value"])
        assertEquals(VitalEntryError.CustomNameMissing, VitalEntry.parse(VitalType.Custom, "3", "", EntryUnit.Canonical).errors["name"])
        assertEquals(listOf(EntryUnit.Canonical, EntryUnit.Pound), VitalEntry.unitsFor(VitalType.Weight))
        assertEquals(60.0 to 79.0, VitalRules.normalRange2(VitalType.BloodPressure))
    }

    @Test fun nextDoseSameDayAndNextDay() {
        val m = med(Frequency.TwiceDaily, listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)))
        val morning = LocalDate.of(2025, 3, 1).atTime(8, 0).atZone(ist).toInstant()
        assertEquals(LocalDate.of(2025, 3, 1).atTime(9, 0).atZone(ist).toInstant(), DoseSchedule.next(m, morning, ist))
        val late = LocalDate.of(2025, 3, 1).atTime(22, 0).atZone(ist).toInstant()
        assertEquals(LocalDate.of(2025, 3, 2).atTime(9, 0).atZone(ist).toInstant(), DoseSchedule.next(m, late, ist))
        // exactly at a dose time → the following one
        assertEquals(LocalDate.of(2025, 3, 1).atTime(21, 0).atZone(ist).toInstant(), DoseSchedule.next(m, LocalDate.of(2025, 3, 1).atTime(9, 0).atZone(ist).toInstant(), ist))
    }

    @Test fun everyOtherDayWeeklyEndDateStoppedAndAsNeeded() {
        val alt = med(Frequency.EveryOtherDay, listOf(LocalTime.of(9, 0)), start = LocalDate.of(2025, 3, 1))
        assertEquals(LocalDate.of(2025, 3, 3), DoseSchedule.next(alt, LocalDate.of(2025, 3, 1).atTime(10, 0).atZone(ist).toInstant(), ist)!!.atZone(ist).toLocalDate())
        val weekly = med(Frequency.Weekly, listOf(LocalTime.of(9, 0)), start = LocalDate.of(2025, 3, 3))
        assertEquals(LocalDate.of(2025, 3, 10), DoseSchedule.next(weekly, LocalDate.of(2025, 3, 3).atTime(10, 0).atZone(ist).toInstant(), ist)!!.atZone(ist).toLocalDate())
        val ended = med(Frequency.OnceDaily, listOf(LocalTime.of(9, 0)), end = LocalDate.of(2025, 3, 1))
        assertNull(DoseSchedule.next(ended, LocalDate.of(2025, 3, 1).atTime(10, 0).atZone(ist).toInstant(), ist))
        assertNull(DoseSchedule.next(ended.copy(stopped = true, endDate = null), Instant.parse("2025-03-01T00:00:00Z"), ist))
        assertNull(DoseSchedule.next(med(Frequency.AsNeeded, listOf(LocalTime.of(9, 0))), Instant.parse("2025-03-01T00:00:00Z"), ist))
        assertNull(DoseSchedule.next(ended.copy(remindersEnabled = false, endDate = null), Instant.parse("2025-03-01T00:00:00Z"), ist))
    }

    @Test fun futureStartAndOccurrenceWindow() {
        val m = med(Frequency.ThriceDaily, listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0)), start = LocalDate.of(2025, 4, 1))
        assertEquals(LocalDate.of(2025, 4, 1).atTime(8, 0).atZone(ist).toInstant(), DoseSchedule.next(m, Instant.parse("2025-03-01T00:00:00Z"), ist))
        val occ = DoseSchedule.occurrences(m, LocalDate.of(2025, 4, 1).atStartOfDay(ist).toInstant(), LocalDate.of(2025, 4, 3).atStartOfDay(ist).toInstant(), ist)
        assertEquals(6, occ.size)
    }

    @Test fun dstTransitionKeepsWallClockTime() {
        val ny = ZoneId.of("America/New_York")
        val m = med(Frequency.OnceDaily, listOf(LocalTime.of(9, 0)), start = LocalDate.of(2025, 3, 1))
        val next = DoseSchedule.next(m, LocalDate.of(2025, 3, 8).atTime(10, 0).atZone(ny).toInstant(), ny)!! // DST starts 9 Mar
        assertEquals(LocalTime.of(9, 0), next.atZone(ny).toLocalTime())
    }
}
