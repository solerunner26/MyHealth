package app.myhealth.domain

import app.myhealth.domain.util.SunClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SunClockTest {
    private val india = ZoneId.of("Asia/Kolkata")

    @Test fun indiaSunriseAndSunsetAreRealistic() {
        val winter = SunClock.day(LocalDate.of(2026, 12, 21), india)
        val summer = SunClock.day(LocalDate.of(2026, 6, 21), india)
        assertTrue("winter sunrise ${winter.sunrise}", winter.sunrise in LocalTime.of(6, 20)..LocalTime.of(7, 20))
        assertTrue("winter sunset ${winter.sunset}", winter.sunset in LocalTime.of(17, 10)..LocalTime.of(18, 10))
        assertTrue("summer sunrise ${summer.sunrise}", summer.sunrise in LocalTime.of(5, 0)..LocalTime.of(6, 0))
        assertTrue("summer sunset ${summer.sunset}", summer.sunset in LocalTime.of(18, 30)..LocalTime.of(19, 30))
        assertTrue(summer.sunset.isAfter(winter.sunset))
    }

    @Test fun nightAndNextChange() {
        val noon = ZonedDateTime.of(2026, 11, 23, 12, 0, 0, 0, india)
        val night = ZonedDateTime.of(2026, 11, 23, 22, 0, 0, 0, india)
        assertFalse(SunClock.isNight(noon)); assertTrue(SunClock.isNight(night))
        assertTrue(SunClock.nextChange(noon).toLocalTime().hour in 17..18)
        assertTrue(SunClock.nextChange(night).toLocalDate() == LocalDate.of(2026, 11, 24))
        // other zones work without a table entry
        SunClock.day(LocalDate.of(2026, 3, 1), ZoneId.of("Europe/Berlin"))
        SunClock.day(LocalDate.of(2026, 6, 21), ZoneId.of("Arctic/Longyearbyen"))
    }
}
