package app.myhealth.domain.parse

import app.myhealth.domain.model.ReportType
import app.myhealth.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportParserTest {
    private val parser = ReportParser(today = LocalDate.of(2025, 1, 1))

    @Test fun fullLabReportTwoPages() {
        val page1 = """
            SUNRISE DIAGNOSTIC LABORATORY
            Patient Name : Mr. Ravi Shah      Age: 45 Y   Sex: Male
            Sample Collected On : 12/03/2024 08:10      Reported On: 13/03/2024
            Test Name                Result    Unit        Biological Ref. Interval
            COMPLETE BLOOD COUNT
            Haemoglobin              13.1      g/dL        13.0 - 17.0
            Total Leucocyte Count    11,600 H  /cumm       4000 - 11000
            Platelet Count           2.1       lakhs/cumm  1.5 - 4.1
            Haemoglobin              13.1      g/dL        13.0 - 17.0
        """.trimIndent()
        val page2 = """
            LIPID PROFILE
            Cholesterol, Total       212       mg/dL       < 200
            Triglycerides            180       mg/dL       < 150
            HDL Cholesterol          38   L    mg/dL       > 40
            LDL Cholesterol          138       mg/dL       < 100
            HbA1c                    6.1       %           4.0 - 5.6
            Page 2 of 2
        """.trimIndent()
        val doc = parser.parse(listOf(page1, page2), Sex.Male, 45)
        assertEquals(ReportType.LabReport, doc.type)
        assertEquals(LocalDate.of(2024, 3, 12), doc.reportDate)
        assertEquals("SUNRISE DIAGNOSTIC LABORATORY", doc.labName)
        assertEquals("Ravi Shah", doc.patientName)
        assertEquals(2, doc.pageCount)
        assertEquals(listOf("hb", "wbc", "plt", "tc", "tg", "hdl", "ldl", "hba1c"), doc.labRows.map { it.analyteId })
        val wbc = doc.labRows.first { it.analyteId == "wbc" }
        assertEquals(11.6, wbc.canonicalValue!!, 1e-9); assertEquals("H", wbc.flag)
        assertTrue(doc.medicines.isEmpty())
    }

    @Test fun prescriptionWithVitals() {
        val text = """
            Dr. Mehta's Clinic
            Date: 05-Jan-2024
            BP 142/90  Pulse 88  Wt 80 kg
            Rx
            1. Tab Amlodipine 5 mg 1-0-0 x 30 days
            2. Tab Rosuvastatin 10 mg 0-0-1 x 3 months
            Review after 1 month
        """.trimIndent()
        val doc = parser.parse(listOf(text))
        assertEquals(ReportType.Prescription, doc.type)
        assertEquals(LocalDate.of(2024, 1, 5), doc.reportDate)
        assertEquals(listOf("Amlodipine", "Rosuvastatin"), doc.medicines.map { it.name })
        assertEquals(3, doc.vitals.size)
    }

    @Test fun ocrStyleSplitLinesAndNoise() {
        val text = "Serum Creatinine\n1.4 mg/dL 0.7 - 1.3\nRemarks: correlate clinically\nTSH\n6.8 µIU/mL 0.27 - 4.2"
        val doc = parser.parse(listOf(text), Sex.Female, 50)
        assertEquals(listOf("creat", "tsh"), doc.labRows.map { it.analyteId })
    }

    @Test fun emptyOrGarbageText() {
        val doc = parser.parse(listOf("", "@@@ ### lorem ipsum"))
        assertTrue(doc.isEmpty); assertEquals(ReportType.Other, doc.type)
    }

    @Test fun isDeterministic() {
        val t = listOf("Haemoglobin 12 g/dL 12-15\nTSH 2 µIU/mL 0.27-4.2")
        assertEquals(parser.parse(t), parser.parse(t))
    }

    @Test fun classifiesImagingAndBills() {
        assertEquals(ReportType.Imaging, DocumentClassifier.classify(listOf("USG ABDOMEN", "IMPRESSION: normal study"), 0, 0))
        assertEquals(ReportType.PharmacyBill, DocumentClassifier.classify(listOf("Invoice No 123", "GSTIN 24ABC", "Batch Qty MRP Amount"), 0, 1))
    }
}
