package com.solerunner.myhealth.domain.medicine

import com.solerunner.myhealth.domain.model.MedicineForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineBillParserTest {

    private val bill = """
        SUNRISE PHARMACY
        Invoice No: 88231        Date: 04/04/2026
        Patient Name: Test Patient
        TAB. METFORMIN 500 MG   1-0-1   15 DAYS   MRP 45.00
        TAB. ATORVASTATIN 10 MG 0-0-1  30 DAYS   MRP 80.00
        CAP. OMEPRAZOLE 20 MG   1-0-0   10 DAYS   MRP 35.00
        Batch No: A1234  Exp: 09/2027  HSN 3004  GST 5%
        Total Amount: 160.00
    """.trimIndent()

    @Test
    fun `reads medicine rows and ignores billing noise`() {
        val rows = MedicineBillParser.parse(bill)
        assertTrue("expected at least three medicines, got ${rows.size}", rows.size >= 3)
        assertTrue(rows.none { it.name.uppercase().contains("INVOICE") })
        assertTrue(rows.none { it.name.uppercase().contains("BATCH") })
        assertTrue(rows.none { it.name.uppercase().contains("TOTAL") })
    }

    @Test
    fun `extracts strength, frequency and duration`() {
        val rows = MedicineBillParser.parse(bill)
        val metformin = rows.firstOrNull { it.name.contains("METFORMIN", ignoreCase = true) }
        assertNotNull(metformin)
        assertEquals("500 mg", metformin!!.strength?.lowercase())
        assertEquals(2, metformin.timesPerDay)
        assertEquals(15, metformin.durationDays)
        assertEquals(MedicineForm.TABLET, metformin.form)
    }

    @Test
    fun `maps common brand names to molecules`() {
        val rows = MedicineBillParser.parse("Tab Glycomet 500mg 1-0-1 10 days")
        val row = rows.firstOrNull()
        assertNotNull(row)
        assertTrue(row!!.molecule?.contains("metformin", ignoreCase = true) == true)
    }

    @Test
    fun `blank text returns nothing`() {
        assertTrue(MedicineBillParser.parse("  ").isEmpty())
    }
}
