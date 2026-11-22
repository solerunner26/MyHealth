package app.myhealth.domain.catalogue

import app.myhealth.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsAndCatalogueTest {
    @Test fun normalisesCommonPrintedUnits() {
        mapOf(
            "g/dL" to Units.G_DL, "gm/dl" to Units.G_DL, "GM%" to Units.G_DL, "mg/dl" to Units.MG_DL, "mg %" to Units.MG_DL,
            "mmol/L" to Units.MMOL_L, "µmol/L" to Units.UMOL_L, "umol/l" to Units.UMOL_L, "μIU/mL" to Units.UIU_ML, "mIU/L" to Units.UIU_ML,
            "IU/L" to Units.U_L, "U/L" to Units.U_L, "10^3/µL" to Units.K_UL, "x10³/uL" to Units.K_UL, "10^9/L" to Units.K_UL,
            "thou/cumm" to Units.K_UL, "103/ul" to Units.K_UL, "/cumm" to Units.PER_UL, "cells/cu.mm" to Units.PER_UL,
            "lakhs/cumm" to Units.LAKH_UL, "Lakh/cu mm" to Units.LAKH_UL, "mill/cumm" to Units.M_UL, "10^12/L" to Units.M_UL,
            "fL" to Units.FL, "pg" to Units.PG, "mm/1st hr" to Units.MM_HR, "mL/min/1.73m²" to Units.EGFR, "ng/mL" to Units.NG_ML,
            "µg/L" to Units.NG_ML, "mEq/L" to Units.MEQ_L, "%" to Units.PERCENT, "mmol/mol" to Units.MMOL_MOL,
        ).forEach { (raw, expected) -> assertEquals("unit '$raw'", expected, Units.canonical(raw)) }
        assertNull(Units.canonical("banana"))
        assertNull(Units.canonical(""))
    }

    @Test fun catalogueIdsAndAliasesAreUnique() {
        val ids = AnalyteCatalogue.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val aliases = AnalyteCatalogue.all.flatMap { a -> a.aliases.map { it to a.id } }
        val dup = aliases.groupBy { it.first }.filter { g -> g.value.map { it.second }.toSet().size > 1 }
        assertTrue("alias used by more than one analyte: ${dup.keys}", dup.isEmpty())
        AnalyteCatalogue.all.forEach { a ->
            a.aliases.forEach { assertEquals("alias must be lower case: $it", it.lowercase(), it) }
            assertTrue("${a.id} needs a range", a.ranges.isNotEmpty())
            a.ranges.forEach { r -> if (r.low != null && r.high != null) assertTrue("${a.id} range order", r.low <= r.high) }
            assertTrue("${a.id} conversions must not include its canonical unit", a.canonicalUnit !in a.conversions.keys)
        }
        assertTrue(AnalyteCatalogue.all.size >= 55)
    }

    @Test fun conversionsMatchPublishedFactors() {
        fun conv(id: String, unit: String, v: Double) = AnalyteCatalogue.byId(id)!!.conversionFor(unit)!!.toCanonical(v)
        assertEquals(90.0, conv("fbs", Units.MMOL_L, 5.0), 0.2)       // 5 mmol/L glucose ≈ 90 mg/dL
        assertEquals(193.4, conv("tc", Units.MMOL_L, 5.0), 0.1)       // cholesterol × 38.67
        assertEquals(132.9, conv("tg", Units.MMOL_L, 1.5), 0.1)       // triglycerides × 88.57
        assertEquals(1.0, conv("creat", Units.UMOL_L, 88.4), 1e-9)    // creatinine ÷ 88.4
        assertEquals(6.5, conv("hba1c", Units.MMOL_MOL, 48.0), 0.05)  // IFCC → NGSP
        assertEquals(20.0, conv("vitd", Units.NMOL_L, 49.9), 0.05)    // ÷ 2.496
        assertEquals(7.8, conv("wbc", Units.PER_UL, 7800.0), 1e-9)
        assertEquals(250.0, conv("plt", Units.LAKH_UL, 2.5), 1e-9)
        assertEquals(14.0, conv("hb", Units.G_L, 140.0), 1e-9)
        assertEquals(1.0, conv("hb", Units.G_DL, 1.0), 1e-9)
    }

    @Test fun rangeSelectionIsSexAndAgeAware() {
        val hb = AnalyteCatalogue.byId("hb")!!
        assertEquals(13.0, hb.rangeFor(Sex.Male, 40)!!.low)
        assertEquals(12.0, hb.rangeFor(Sex.Female, 40)!!.low)
        assertEquals(12.0, hb.rangeFor(Sex.Other, 40)!!.low)
        val psa = AnalyteCatalogue.byId("psa")!!
        assertNotNull(psa.rangeFor(Sex.Male, 60))
    }
}
