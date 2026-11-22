package app.myhealth.domain.parse

import app.myhealth.domain.catalogue.Units
import app.myhealth.domain.model.Comparator
import app.myhealth.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabLineParserTest {
    private val p = LabLineParser(Sex.Male, 40)

    private fun row(line: String, next: String? = null) = p.parse(TextNormalizer.cleanLine(line), next)!!

    @Test fun valueUnitRange() {
        val r = row("Hemoglobin 14.2 g/dL 13.0 - 17.0")
        assertEquals("hb", r.analyteId); assertEquals(14.2, r.canonicalValue!!, 1e-9); assertEquals(Units.G_DL, r.canonicalUnit)
        assertEquals(13.0, r.refLow!!, 1e-9); assertEquals(17.0, r.refHigh!!, 1e-9)
        assertEquals("Hemoglobin", r.printedName); assertEquals("14.2", r.printedValue); assertEquals("13.0 - 17.0", r.printedRange)
        assertEquals(Confidence.High, r.confidence)
    }

    @Test fun flagBeforeRangeAndUnitAfterRange() {
        val r = row("HAEMOGLOBIN (Hb)   11.2   L   13.0-17.0   g/dL")
        assertEquals("hb", r.analyteId); assertEquals(11.2, r.value!!, 1e-9); assertEquals("L", r.flag); assertEquals(Units.G_DL, r.canonicalUnit)
    }

    @Test fun bracketedRangeAndColon() {
        val r = row("Fasting Blood Sugar : 126 mg/dl (70 - 99)")
        assertEquals("fbs", r.analyteId); assertEquals(126.0, r.value!!, 1e-9); assertEquals(99.0, r.refHigh!!, 1e-9)
    }

    @Test fun indianGroupingAndPerCummConversion() {
        val r = row("Total Leucocyte Count (TLC) 7,800 /cumm 4000 - 11000")
        assertEquals("wbc", r.analyteId); assertEquals(7800.0, r.value!!, 1e-9)
        assertEquals(7.8, r.canonicalValue!!, 1e-9); assertEquals(4.0, r.refLow!!, 1e-9); assertEquals(11.0, r.refHigh!!, 1e-9)
    }

    @Test fun plateletsInLakhs() {
        val r = row("Platelet Count 1.5 lakhs/cumm 1.5 - 4.1")
        assertEquals("plt", r.analyteId); assertEquals(150.0, r.canonicalValue!!, 1e-9); assertEquals(410.0, r.refHigh!!, 1e-6)
    }

    @Test fun upperAndLowerBoundRanges() {
        val tc = row("Cholesterol, Total 182 mg/dL < 200")
        assertEquals("tc", tc.analyteId); assertNull(tc.refLow); assertEquals(200.0, tc.refHigh!!, 1e-9)
        val hdl = row("HDL Cholesterol 38 mg/dL > 40")
        assertEquals("hdl", hdl.analyteId); assertEquals(40.0, hdl.refLow!!, 1e-9); assertNull(hdl.refHigh)
    }

    @Test fun longestAliasWins() {
        assertEquals("nonhdl", row("Non-HDL Cholesterol 150 mg/dL <130").analyteId)
        assertEquals("vldl", row("VLDL Cholesterol 24 mg/dL 2-30").analyteId)
        assertEquals("tchdl", row("Total Cholesterol/HDL Ratio 4.2 <5.0").analyteId)
        assertEquals("mchc", row("Mean Corpuscular Hemoglobin Concentration 33 g/dL 31.5-34.5").analyteId)
        assertEquals("eag", row("Estimated Average Glucose (eAG) 154 mg/dL").analyteId)
        assertEquals("ft4", row("Free T4 1.2 ng/dL 0.93-1.7").analyteId)
        assertEquals("tibc", row("Total Iron Binding Capacity 320 µg/dL 250-450").analyteId)
    }

    @Test fun digitsInNameDoNotBecomeTheValue() {
        val r = row("Vitamin D (25-OH) 18.5 ng/mL 30 - 100")
        assertEquals("vitd", r.analyteId); assertEquals(18.5, r.value!!, 1e-9); assertEquals(30.0, r.refLow!!, 1e-9)
    }

    @Test fun microUnitsAndComparatorValues() {
        val tsh = row("TSH 3.2 µIU/mL 0.27-4.2")
        assertEquals(Units.UIU_ML, tsh.canonicalUnit)
        val crp = row("CRP <0.5 mg/L 0-5")
        assertEquals(Comparator.Lt, crp.comparator); assertEquals(0.5, crp.value!!, 1e-9)
        assertTrue(ParseWarning.ComparatorValue in crp.warnings)
    }

    @Test fun siUnitsConvert() {
        val g = row("Glucose Fasting 5.6 mmol/L 3.9-5.5")
        assertEquals(100.9, g.canonicalValue!!, 0.1); assertEquals(99.1, g.refHigh!!, 0.1)
        val a1c = row("HbA1c 48 mmol/mol")
        assertEquals(6.54, a1c.canonicalValue!!, 0.02)
        assertTrue(ParseWarning.RangeFromCatalogue in a1c.warnings)
    }

    @Test fun missingUnitIsInferredAndFlagged() {
        val r = row("WBC Count 7800 4000-11000")
        assertEquals(7.8, r.canonicalValue!!, 1e-9)
        assertTrue(ParseWarning.UnitInferred in r.warnings); assertEquals(Confidence.Medium, r.confidence)
    }

    @Test fun valueOnTheNextLine() {
        val r = row("Serum Creatinine", "1.1 mg/dL 0.7 - 1.3")
        assertEquals("creat", r.analyteId); assertEquals(1.1, r.value!!, 1e-9); assertTrue(ParseWarning.ValueOnNextLine in r.warnings)
    }

    @Test fun eGfrMultiTokenUnit() {
        val r = row("eGFR 85 mL/min/1.73 m² >60")
        assertEquals("egfr", r.analyteId); assertEquals(Units.EGFR, r.canonicalUnit); assertEquals(60.0, r.refLow!!, 1e-9)
    }

    @Test fun rejectsNonResultLines() {
        assertNull(p.parse("Haemoglobin is a protein in red blood cells"))
        assertNull(p.parse("Free PSA 0.4 ng/mL"))
        assertNull(p.parse("Reference range 13 - 17"))
        assertNull(p.parse("Page 1 of 3"))
    }

    @Test fun catalogueRangeWhenNoneIsPrinted() {
        val r = LabLineParser(Sex.Female, 30).parse("Haemoglobin 11.0 g/dL")!!
        assertEquals(12.0, r.refLow!!, 1e-9); assertTrue(ParseWarning.RangeFromCatalogue in r.warnings)
    }

    @Test fun implausibleValueIsLowConfidence() {
        val r = row("Haemoglobin 1420 g/dL 13-17")
        assertEquals(Confidence.Low, r.confidence); assertTrue(ParseWarning.ImplausibleValue in r.warnings)
    }
}
