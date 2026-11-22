package app.myhealth.domain.analytics

import app.myhealth.domain.analytics.Fx.lab
import app.myhealth.domain.analytics.Fx.today
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrendEngineTest {
    @Test fun latestPreviousDeltaAndMonthlyRate() {
        val r = listOf(lab("hba1c", LocalDate.of(2024, 12, 1), 6.0, 4.0, 5.6), lab("hba1c", LocalDate.of(2025, 3, 2), 6.6, 4.0, 5.6))
        val t = TrendEngine.trend(r, "hba1c", today, 5)!!
        assertEquals(6.6, t.latest.value, 0.0); assertEquals(6.0, t.previous!!.value, 0.0)
        assertEquals(0.6, t.absoluteChange!!, 1e-9); assertEquals(10.0, t.percentChange!!, 1e-9)
        assertEquals(91L, t.daysBetween)
        assertEquals(0.6 / (91 / 30.4375), t.ratePerMonth!!, 1e-9)
        assertEquals(TrendDirection.Worsening, t.direction)
        assertEquals(RangeStatus.High, t.latest.status)
    }

    @Test fun directionRespectsDirectionOfImprovement() {
        val down = TrendEngine.trend(listOf(lab("ldl", LocalDate.of(2024, 1, 1), 160.0, null, 100.0), lab("ldl", LocalDate.of(2025, 1, 1), 120.0, null, 100.0)), "ldl", today, 5)!!
        assertEquals(TrendDirection.Improving, down.direction)
        val hdlUp = TrendEngine.trend(listOf(lab("hdl", LocalDate.of(2024, 1, 1), 32.0, 40.0, null), lab("hdl", LocalDate.of(2025, 1, 1), 38.0, 40.0, null)), "hdl", today, 5)!!
        assertEquals(TrendDirection.Improving, hdlUp.direction)
        // in-range analyte moving out of band (TSH 2 → 6) worsens; moving back in improves
        val tsh = TrendEngine.trend(listOf(lab("tsh", LocalDate.of(2024, 1, 1), 2.0, 0.27, 4.2), lab("tsh", LocalDate.of(2025, 1, 1), 6.0, 0.27, 4.2)), "tsh", today, 5)!!
        assertEquals(TrendDirection.Worsening, tsh.direction)
        val stable = TrendEngine.trend(listOf(lab("hb", LocalDate.of(2024, 1, 1), 14.0, 13.0, 17.0), lab("hb", LocalDate.of(2025, 1, 1), 14.1, 13.0, 17.0)), "hb", today, 5)!!
        assertEquals(TrendDirection.Stable, stable.direction)
        val single = TrendEngine.trend(listOf(lab("hb", LocalDate.of(2024, 1, 1), 14.0)), "hb", today, 5)!!
        assertEquals(TrendDirection.Insufficient, single.direction); assertNull(single.previous); assertNull(single.ratePerMonth)
    }

    @Test fun historyWindowAndCatalogueBand() {
        val r = listOf(lab("hb", LocalDate.of(2018, 1, 1), 11.0), lab("hb", LocalDate.of(2024, 6, 1), 12.5))
        val t = TrendEngine.trend(r, "hb", today, 5, app.myhealth.domain.model.Sex.Male, 45)!!
        assertEquals(1, t.points.size) // 2018 is outside a 5-year window
        assertEquals(13.0, t.bandLow!!, 0.0) // male catalogue range used when none printed
        assertEquals(RangeStatus.Low, t.latest.status)
        assertEquals(2, TrendEngine.trend(r, "hb", today, 10)!!.points.size)
        assertEquals(12.0, TrendEngine.trend(r, "hb", today, 5)!!.bandLow!!, 0.0) // unknown sex → general adult range
    }

    @Test fun statusIncludesCriticalLimits() {
        val k = app.myhealth.domain.catalogue.AnalyteCatalogue.byId("k")!!
        assertEquals(RangeStatus.CriticalHigh, TrendEngine.status(k, 6.5, 3.5, 5.1))
        assertEquals(RangeStatus.High, TrendEngine.status(k, 5.5, 3.5, 5.1))
        assertEquals(RangeStatus.Unknown, TrendEngine.status(app.myhealth.domain.catalogue.AnalyteCatalogue.byId("mch")!!, 30.0, null, null))
        assertEquals(0.25, TrendEngine.relativeExcess(125.0, null, 100.0), 1e-9)
    }

    @Test fun csvEscapingAndInjectionGuard() {
        assertEquals("a,\"b,c\",\"say \"\"hi\"\"\"", Csv.row(listOf("a", "b,c", "say \"hi\"")))
        assertEquals("'=HYPERLINK(1)", Csv.escape("=HYPERLINK(1)"))
        assertEquals("-0.4", Csv.escape("-0.4"))
        val t = TrendEngine.all(listOf(lab("hb", LocalDate.of(2024, 1, 1), 12.5, 13.0, 17.0)), today, 5)
        val csv = TrendCsv.history(t, { it.name }, { it.name }, listOf("date", "test", "value", "unit", "low", "high", "status"))
        assertEquals("date,test,value,unit,low,high,status\r\n2024-01-01,Haemoglobin,12.5,g/dL,13.0,17.0,Low\r\n", csv)
        assertTrue(csv.endsWith("\r\n"))
    }
}
