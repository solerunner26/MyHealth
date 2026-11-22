package app.myhealth.domain.parse

import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.MedicineForm
import app.myhealth.domain.model.VitalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalsAndMedicineParserTest {
    @Test fun vitalsFromConsultationNote() {
        val v = VitalsParser.parse(listOf(
            "BP: 138/88 mmHg   Pulse: 82 /min", "SpO2 97 %", "Wt - 72.4 kg  Ht: 170 cm  BMI: 25.1",
            "Temp 99.5 F", "RR: 18",
        )).associateBy { it.type }
        assertEquals(138.0, v.getValue(VitalType.BloodPressure).value, 0.0); assertEquals(88.0, v.getValue(VitalType.BloodPressure).value2!!, 0.0)
        assertEquals(82.0, v.getValue(VitalType.Pulse).value, 0.0)
        assertEquals(97.0, v.getValue(VitalType.SpO2).value, 0.0)
        assertEquals(72.4, v.getValue(VitalType.Weight).value, 0.0)
        assertEquals(170.0, v.getValue(VitalType.Height).value, 0.0)
        assertEquals(25.1, v.getValue(VitalType.Bmi).value, 0.0)
        assertEquals(37.5, v.getValue(VitalType.Temperature).value, 0.05)
        assertEquals(18.0, v.getValue(VitalType.RespiratoryRate).value, 0.0)
    }

    @Test fun vitalsRejectImplausibleAndConvertPounds() {
        assertTrue(VitalsParser.parse(listOf("BP 80/120", "Pulse 900", "SpO2 140")).isEmpty())
        assertEquals(68.0, VitalsParser.parse(listOf("Weight: 150 lbs")).single().value, 0.1)
        assertEquals(175.3, VitalsParser.parse(listOf("Height 69 in")).single().value, 0.1)
    }

    @Test fun prescriptionLines() {
        val a = MedicineParser.parseLine("1. Tab Metformin 500 mg 1-0-1 x 30 days")!!
        assertEquals("Metformin", a.name); assertEquals("500mg", a.strength); assertEquals(MedicineForm.Tablet, a.form)
        assertEquals(Frequency.TwiceDaily, a.frequency); assertEquals(30, a.durationDays)

        val b = MedicineParser.parseLine("Cap. Omez 20mg OD before food for 2 weeks")!!
        assertEquals("Omez", b.name); assertEquals(Frequency.OnceDaily, b.frequency); assertEquals(14, b.durationDays); assertEquals(MedicineForm.Capsule, b.form)

        val c = MedicineParser.parseLine("Syp Crocin 5 ml SOS")!!
        assertEquals(MedicineForm.Syrup, c.form); assertEquals(Frequency.AsNeeded, c.frequency); assertNull(c.durationDays)

        assertEquals(Frequency.AtBedtime, MedicineParser.parseLine("Tab Atorvastatin 10 mg 0-0-1 x 3 months")!!.frequency)
        assertEquals(Frequency.ThriceDaily, MedicineParser.parseLine("Tab Paracetamol 650mg TDS 5 days")!!.frequency)
        assertEquals(90, MedicineParser.parseLine("Tab Telma 40 1-0-0 x 3 months")!!.durationDays)
        assertNull(MedicineParser.parseLine("Tablets should be taken after food"))
    }
}
