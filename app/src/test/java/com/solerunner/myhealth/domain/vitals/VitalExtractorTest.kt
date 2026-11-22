package com.solerunner.myhealth.domain.vitals

import com.solerunner.myhealth.domain.model.MeasurementKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalExtractorTest {

    private fun row(text: String, kind: MeasurementKind) =
        VitalExtractor.extract(text).firstOrNull { it.kind == kind }

    @Test
    fun `reads blood pressure only from a labelled line`() {
        val bp = row("Blood Pressure: 128/84 mmHg", MeasurementKind.BLOOD_PRESSURE)
        assertNotNull(bp)
        assertEquals(128.0, bp!!.primary, 0.01)
        assertEquals(84.0, bp.secondary!!, 0.01)
        assertEquals("mmHg", bp.unit)
    }

    @Test
    fun `does not mistake a date or a ratio for blood pressure`() {
        assertTrue(VitalExtractor.extract("Sample Date: 12/03/2026").isEmpty())
        assertTrue(VitalExtractor.extract("Neutrophil 70/30").isEmpty())
    }

    @Test
    fun `reads pulse and oxygen saturation`() {
        val text = "Pulse: 76 bpm\nSpO2: 97%"
        assertEquals(76.0, row(text, MeasurementKind.PULSE)!!.primary, 0.01)
        assertEquals(97.0, row(text, MeasurementKind.SPO2)!!.primary, 0.01)
    }

    @Test
    fun `reads weight and converts pounds`() {
        assertEquals(72.0, row("Weight: 72 kg", MeasurementKind.WEIGHT)!!.primary, 0.01)
        val pounds = row("Weight: 158 lb", MeasurementKind.WEIGHT)!!.primary
        assertEquals(71.7, pounds, 0.2)
    }

    @Test
    fun `reads height in centimetres and metres`() {
        assertEquals(172.0, row("Height: 172 cm", MeasurementKind.HEIGHT)!!.primary, 0.01)
        assertEquals(165.0, row("Height: 1.65 m", MeasurementKind.HEIGHT)!!.primary, 0.5)
    }

    @Test
    fun `converts Fahrenheit temperature to Celsius`() {
        assertEquals(37.0, row("Temperature: 98.6 F", MeasurementKind.TEMPERATURE)!!.primary, 0.1)
        assertEquals(37.2, row("Temperature: 37.2 C", MeasurementKind.TEMPERATURE)!!.primary, 0.01)
    }

    @Test
    fun `reads BMI and respiratory rate`() {
        assertEquals(24.3, row("BMI: 24.3", MeasurementKind.BMI)!!.primary, 0.01)
        assertEquals(16.0, row("Respiratory Rate: 16 /min", MeasurementKind.RESPIRATORY_RATE)!!.primary, 0.01)
    }

    @Test
    fun `keeps one row per kind and ignores blank text`() {
        val text = "Pulse: 76 bpm\nPulse: 80 bpm\nWeight: 72 kg"
        val rows = VitalExtractor.extract(text)
        assertEquals(rows.size, rows.map { it.kind }.distinct().size)
        assertTrue(VitalExtractor.extract("   ").isEmpty())
    }
}
