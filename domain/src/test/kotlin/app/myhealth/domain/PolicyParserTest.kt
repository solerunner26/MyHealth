package app.myhealth.domain

import app.myhealth.domain.insurance.PolicyParser
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.PolicyType
import app.myhealth.domain.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PolicyParserTest {
    private val ravi = FamilyMember("r", "Ravi Shah", Relationship.Self)
    private val meera = FamilyMember("m", "Meera Shah", Relationship.Spouse)
    private val other = FamilyMember("o", "Anil Patel", Relationship.Parent)

    private val schedule = """
        Star Health and Allied Insurance Co. Ltd.
        POLICY SCHEDULE
        Product Name : Family Health Optima Insurance Plan
        Policy No. : 11240034567800  Policy Type: Family Floater
        Period of Insurance : From 01/04/2026 00:00 Hrs To 31/03/2027 Midnight
        Name of Insured Persons
        1  Mr. Ravi Shah   Self   42
        2  Mrs Meera Shah  Spouse  39
        Sum Insured (Rs.)   5,00,000
        Cumulative Bonus    Rs. 1,00,000/-
        Total Premium (incl. GST) : Rs. 24,580
        Room Rent : Single private A/C room
        Co-payment : 20% for insured aged above 60
        Initial waiting period : 30 days
        Pre-existing diseases waiting period : 36 months
        Specific diseases : 24 months
        Pre-hospitalisation : 60 days   Post-hospitalisation : 90 days
        Day care procedures : Covered
        Maternity : Not covered
        AYUSH treatment : Covered up to sum insured
        Automatic restoration of sum insured : Available
        Road ambulance : up to Rs 2,000 per hospitalisation
        Toll free : 1800-425-2255
        Agent Name : Kiran Desai  Agent Contact : 98250 12345
    """.trimIndent()

    @Test fun readsTheMainFieldsOfASchedule() {
        val r = PolicyParser.parse(listOf(schedule), listOf(ravi, meera, other))
        assertEquals("Star Health and Allied Insurance", r.insurer)
        assertEquals("11240034567800", r.policyNumber)
        assertEquals("Family Health Optima Insurance Plan", r.planName)
        assertEquals(PolicyType.FamilyFloater, r.type)
        assertEquals(500_000.0, r.sumInsured!!, 0.0)
        assertEquals(100_000.0, r.bonus!!, 0.0)
        assertEquals(24_580.0, r.premium!!, 0.0)
        assertEquals(LocalDate.of(2026, 4, 1), r.startDate)
        assertEquals(LocalDate.of(2027, 3, 31), r.endDate)
        assertEquals("18004252255", r.helpline)
        assertEquals("Kiran Desai", r.agentName)
        assertEquals("9825012345", r.agentPhone)
        assertEquals(listOf("r", "m"), r.memberIds)
        assertTrue(r.found >= 15)
    }

    @Test fun readsCoverageFeatures() {
        val f = PolicyParser.parse(listOf(schedule)).features
        assertEquals("Single private A/C room", f.roomRent)
        assertEquals(20, f.coPayPercent)
        assertEquals(30, f.initialWaitingDays)
        assertEquals(36, f.pedWaitingMonths)
        assertEquals(24, f.specificWaitingMonths)
        assertEquals(60, f.preHospitalisationDays)
        assertEquals(90, f.postHospitalisationDays)
        assertEquals(2_000.0, f.ambulanceLimit!!, 0.0)
        assertTrue(f.dayCare); assertTrue(f.ayush); assertTrue(f.restoration)
        assertFalse(f.maternity); assertFalse(f.opd)
    }

    @Test fun lakhCroreAndYearsAndOnlyAnExpiryDate() {
        val r = PolicyParser.parse(listOf("""
            HDFC ERGO Optima Secure
            Certificate No: 2856/2019/123456
            Sum Insured: 10 Lakh
            Expiry Date: 14-Aug-2027
            Pre-existing disease: 3 years
            TPA Name: Medi Assist India  Phone 080-46855555
        """.trimIndent()))
        assertEquals("HDFC ERGO General Insurance", r.insurer)
        assertEquals("2856/2019/123456", r.policyNumber)
        assertEquals(1_000_000.0, r.sumInsured!!, 0.0)
        assertEquals(LocalDate.of(2027, 8, 14), r.endDate)
        assertEquals(LocalDate.of(2026, 8, 15), r.startDate)
        assertEquals(36, r.features.pedWaitingMonths)
        assertEquals("Medi Assist", r.tpaName)
        assertEquals("08046855555", r.tpaPhone)
    }

    @Test fun unreadableTextGivesNothingRatherThanGuesses() {
        val r = PolicyParser.parse(listOf("lorem ipsum 12 dolor", "page 1 of 3"))
        assertEquals(0, r.found)
        assertEquals("", r.insurer); assertEquals(null, r.sumInsured); assertEquals(null, r.startDate)
    }

    @Test fun percentagesAndDatesAreNotAmounts() {
        assertEquals(listOf(500000.0), PolicyParser.amounts("20% of 01/04/2026 and Rs 5,00,000/-").filter { it > 100 })
    }
}
