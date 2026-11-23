package app.myhealth.domain

import app.myhealth.domain.insurance.HospitalListParser
import app.myhealth.domain.insurance.Mediclaims
import app.myhealth.domain.model.ClaimStatus
import app.myhealth.domain.model.MediclaimClaim
import app.myhealth.domain.model.MediclaimPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MediclaimTest {
    private val today = LocalDate.of(2026, 11, 23)
    private val p = MediclaimPolicy("p", "m", "Star Health", sumInsured = 500_000.0, bonus = 100_000.0,
        startDate = LocalDate.of(2026, 4, 1), endDate = LocalDate.of(2027, 3, 31))

    @Test fun balanceCountsOnlyApprovedClaimsOfThisPolicyPeriod() {
        val claims = listOf(
            MediclaimClaim("a", "p", "m", admittedOn = LocalDate.of(2026, 6, 1), claimedAmount = 90_000.0, approvedAmount = 80_000.0, status = ClaimStatus.Settled),
            MediclaimClaim("b", "p", "m", admittedOn = LocalDate.of(2026, 8, 1), claimedAmount = 50_000.0, status = ClaimStatus.Rejected),
            MediclaimClaim("c", "p", "m", admittedOn = LocalDate.of(2025, 8, 1), claimedAmount = 40_000.0, approvedAmount = 40_000.0, status = ClaimStatus.Settled), // last year
            MediclaimClaim("d", "other", "m", admittedOn = LocalDate.of(2026, 8, 1), claimedAmount = 10.0, approvedAmount = 10.0, status = ClaimStatus.Settled),
        )
        assertEquals(80_000.0, Mediclaims.used(p, claims), 0.0)
        assertEquals(520_000.0, Mediclaims.available(p, claims), 0.0)
    }

    @Test fun renewalRemindersAndExpiryState() {
        assertEquals(listOf(30, 7, 1, 0), Mediclaims.reminderDates(p, today).map { it.first })
        assertEquals(Mediclaims.Expiry.Ok, Mediclaims.expiry(p, today))
        assertEquals(Mediclaims.Expiry.Soon, Mediclaims.expiry(p, LocalDate.of(2027, 3, 10)))
        assertEquals(Mediclaims.Expiry.Expired, Mediclaims.expiry(p, LocalDate.of(2027, 4, 2)))
        assertEquals(listOf(1, 0), Mediclaims.reminderDates(p, LocalDate.of(2027, 3, 30)).map { it.first })
    }

    @Test fun networkHospitalsAreReadFromTableTextAndNextLineAddresses() {
        val text = """
            NETWORK HOSPITAL LIST - GUJARAT
            Sr No   Hospital Name                 Address                               City        Pincode   Phone
            1       Sterling Hospital             Off Gurukul Road, Memnagar            Ahmedabad   380052    079-40011111
            2       Apollo Hospitals International   Plot 1A, GIDC Bhat                  Gandhinagar 382428    9876543210
            3.  Shree Krishna Nursing Home
                Near Bus Stand, Station Road, Anand 388001, Ph: 02692 253456
            Terms and conditions apply to all cashless treatment.
            1       Sterling Hospital             Off Gurukul Road, Memnagar            Ahmedabad   380052    079-40011111
        """.trimIndent().lines()
        val h = HospitalListParser.parse(text)
        assertEquals(3, h.size) // duplicate row ignored, header and prose skipped
        assertEquals("Sterling Hospital", h[0].name); assertEquals("380052", h[0].pincode); assertEquals("07940011111", h[0].phone)
        assertTrue(h[0].address.contains("Memnagar"))
        assertEquals("9876543210", h[1].phone)
        assertEquals("Shree Krishna Nursing Home", h[2].name); assertEquals("388001", h[2].pincode); assertTrue(h[2].address.contains("Station Road"))
        assertEquals("02692253456", h[2].phone)
    }
}
