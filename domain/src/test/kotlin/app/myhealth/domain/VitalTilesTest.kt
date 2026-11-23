package app.myhealth.domain

import app.myhealth.domain.analytics.VitalStatus
import app.myhealth.domain.analytics.VitalTiles
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class VitalTilesTest {
    @Test fun statusAndClampedPositionFollowTheReferenceInterval() {
        assertEquals(VitalStatus.HIGH, VitalTiles.status(165.0, null, 100.0))
        assertEquals(VitalStatus.LOW, VitalTiles.status(11.0, 13.0, 17.0))
        assertEquals(VitalStatus.NORMAL, VitalTiles.status(13.0, 13.0, 17.0)) // bounds are inside
        assertEquals(0.25f, VitalTiles.position(13.0, 13.0, 17.0), 1e-4f)
        assertEquals(0.75f, VitalTiles.position(17.0, 13.0, 17.0), 1e-4f)
        assertEquals(0.95f, VitalTiles.position(400.0, 13.0, 17.0), 1e-4f) // pinned right
        assertEquals(0.05f, VitalTiles.position(-5.0, 13.0, 17.0), 1e-4f)  // pinned left
        assertEquals(0.5f, VitalTiles.position(3.0, null, null), 1e-4f)
    }

    @Test fun bloodPressureUsesTheWorseNumberAndTriageCounts() {
        val bp = VitalTiles.fromVital(Vital("1", "m", VitalType.BloodPressure, 118.0, 92.0, measuredAt = Instant.EPOCH))
        assertEquals(VitalStatus.HIGH, bp.status) // diastolic 92 > 79
        val ok = VitalTiles.fromVital(Vital("2", "m", VitalType.SpO2, 98.0, measuredAt = Instant.EPOCH))
        val low = VitalTiles.fromVital(Vital("3", "m", VitalType.SpO2, 90.0, measuredAt = Instant.EPOCH))
        assertEquals(2 to 1, VitalTiles.triage(listOf(ok, bp, low)))
        assertEquals(listOf(bp.key, low.key, ok.key), VitalTiles.doctorOrder(listOf(ok, bp, low)).map { it.key })
    }
}
