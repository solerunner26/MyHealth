package com.solerunner.myhealth.domain.lab

import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.domain.model.ValueStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LabTextParserTest {

    private val report = """
        CITY DIAGNOSTICS LABORATORY
        Patient Name: Test Patient
        Age/Sex: 38/M
        Sample Date: 12/03/2026
        Report Date: 13/03/2026
        Ref By: Dr A. Sharma

        COMPLETE BLOOD COUNT
        Haemoglobin          13.2   g/dL      13.0 - 17.0
        Platelet Count       245    /uL       150 - 410

        BIOCHEMISTRY
        Fasting Blood Sugar  118    mg/dL     70 - 100
        HbA1c                6.8    %         4.0 - 5.6
        Total Cholesterol    205    mg/dL     < 200
        Serum Creatinine     0.9    mg/dL     0.7 - 1.3

        Blood Pressure: 128/84 mmHg
        Pulse: 76 bpm
        Weight: 72 kg
    """.trimIndent()

    @Test
    fun `reads the header dates and lab`() {
        val parsed = LabTextParser.parse(report, fileName = "cbc.pdf")
        assertEquals(LocalDate.of(2026, 3, 12), parsed.header.sampleDate)
        assertEquals(LocalDate.of(2026, 3, 13), parsed.header.reportDate)
        assertEquals("CITY DIAGNOSTICS LABORATORY", parsed.header.labName?.uppercase())
        assertEquals(ReportKind.LAB, parsed.header.kind)
    }

    @Test
    fun `reads analytes with printed units and ranges`() {
        val parsed = LabTextParser.parse(report, fileName = "cbc.pdf")
        val hemoglobin = parsed.analytes.firstOrNull { it.biomarkerKey == "hemoglobin" }
        assertNotNull(hemoglobin)
        assertEquals(13.2, hemoglobin!!.printedValue!!, 0.001)
        assertEquals("g/dL", hemoglobin.printedUnit)
        assertEquals(ValueStatus.IN_RANGE, hemoglobin.status)

        val glucose = parsed.analytes.firstOrNull { it.biomarkerKey == "fasting_glucose" }
        assertNotNull(glucose)
        assertEquals(118.0, glucose!!.printedValue!!, 0.001)
        assertEquals(ValueStatus.HIGH, glucose.status)

        val cholesterol = parsed.analytes.firstOrNull { it.biomarkerKey == "total_cholesterol" }
        assertNotNull(cholesterol)
        assertEquals(205.0, cholesterol!!.printedValue!!, 0.001)
        assertEquals(ValueStatus.HIGH, cholesterol.status)
    }

    @Test
    fun `keeps the canonical value when the unit differs`() {
        val mmolReport = "HbA1c 48 mmol/mol 20 - 42"
        val parsed = LabTextParser.parse(mmolReport)
        val hba1c = parsed.analytes.firstOrNull { it.biomarkerKey == "hba1c" }
        assertNotNull(hba1c)
        assertEquals(48.0, hba1c!!.printedValue!!, 0.001)
        assertNotNull(hba1c.canonicalValue)
        assertTrue(hba1c.canonicalValue!! in 4.0..5.0)
    }

    @Test
    fun `extracts vitals printed below the table`() {
        val parsed = LabTextParser.parse(report)
        val bp = parsed.vitals.firstOrNull { it.kind.key == "blood_pressure" }
        assertNotNull(bp)
        assertEquals(128.0, bp!!.primary, 0.001)
        assertEquals(84.0, bp.secondary!!, 0.001)
        assertTrue(parsed.vitals.any { it.kind.key == "pulse" })
    }

    @Test
    fun `empty text yields a warning, not a crash`() {
        val parsed = LabTextParser.parse("   ")
        assertTrue(parsed.isEmpty)
        assertTrue(parsed.warnings.contains("EMPTY_TEXT"))
    }
}
