package app.myhealth.domain.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise/sunset for the automatic day/night theme. No location permission: the position is approximated from the
 * time zone (longitude from the zone's UTC offset or a known city, latitude from the region). Accuracy of ~15–30 min
 * is plenty for switching colours.
 */
object SunClock {
    data class Day(val sunrise: LocalTime, val sunset: LocalTime)

    /** Approximate (latitude, longitude) for a time zone. */
    fun approxPosition(zone: ZoneId, date: LocalDate): Pair<Double, Double> {
        val id = zone.id
        KNOWN[id]?.let { return it }
        val offsetH = zone.rules.getStandardOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / 3600.0
        val lat = when {
            id.startsWith("Asia/") -> 25.0; id.startsWith("Europe/") -> 50.0; id.startsWith("America/") -> 38.0
            id.startsWith("Australia/") -> -30.0; id.startsWith("Africa/") -> 5.0; id.startsWith("Pacific/") -> -15.0
            else -> 20.0
        }
        return lat to offsetH * 15.0
    }

    private val KNOWN = mapOf(
        "Asia/Kolkata" to (22.3 to 78.0), "Asia/Calcutta" to (22.3 to 78.0), "Asia/Dubai" to (25.2 to 55.3),
        "Europe/London" to (51.5 to -0.1), "America/New_York" to (40.7 to -74.0), "America/Los_Angeles" to (34.0 to -118.2),
        "Asia/Singapore" to (1.35 to 103.8), "Australia/Sydney" to (-33.9 to 151.2), "America/Toronto" to (43.7 to -79.4),
    )

    /** NOAA approximation. Polar day/night falls back to 06:00/18:00. */
    fun day(date: LocalDate, zone: ZoneId): Day {
        val (lat, lon) = approxPosition(zone, date)
        val n = date.dayOfYear
        val gamma = 2 * Math.PI / 365 * (n - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val latR = Math.toRadians(lat)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        if (cosHa < -1 || cosHa > 1) return Day(LocalTime.of(6, 0), LocalTime.of(18, 0))
        val ha = Math.toDegrees(acos(cosHa))
        val offsetMin = zone.rules.getOffset(date.atTime(12, 0).atZone(zone).toInstant()).totalSeconds / 60.0
        fun at(minutesUtc: Double): LocalTime {
            val local = ((minutesUtc + offsetMin) % 1440 + 1440) % 1440
            return LocalTime.of((local / 60).toInt(), (local % 60).toInt())
        }
        return Day(at(720 - 4 * (lon + ha) - eqTime), at(720 - 4 * (lon - ha) - eqTime))
    }

    fun isNight(now: ZonedDateTime): Boolean {
        val d = day(now.toLocalDate(), now.zone)
        val t = now.toLocalTime()
        return t.isBefore(d.sunrise) || !t.isBefore(d.sunset)
    }

    /** When the theme should next be re-evaluated. */
    fun nextChange(now: ZonedDateTime): ZonedDateTime {
        val d = day(now.toLocalDate(), now.zone)
        val t = now.toLocalTime()
        return when {
            t.isBefore(d.sunrise) -> now.with(d.sunrise)
            t.isBefore(d.sunset) -> now.with(d.sunset)
            else -> day(now.toLocalDate().plusDays(1), now.zone).sunrise.atDate(now.toLocalDate().plusDays(1)).atZone(now.zone)
        }
    }

}
