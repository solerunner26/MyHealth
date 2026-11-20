package com.solerunner.myhealth.core.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date helpers. Everything is stored in ISO form (epoch days / epoch seconds) and rendered in the
 *  user's locale, so the storage layer never depends on a formatting choice. */
object Dates {

    /** Reference date used by rule engines; injectable so tests are deterministic. */
    fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())

    fun fromEpochDay(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    fun toEpochDay(value: LocalDate): Long = value.toEpochDay()

    /** Whole months between two dates, as a fraction (used for "rate per month" trends). */
    fun monthsBetween(from: LocalDate, to: LocalDate): Double {
        val days = java.time.temporal.ChronoUnit.DAYS.between(from, to).toDouble()
        return days / 30.4375
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(from, to)

    /** The record window: everything inside the last three years is "current history". */
    fun historyWindowStart(reference: LocalDate = today()): LocalDate = reference.minusYears(3)

    fun isWithinHistoryWindow(date: LocalDate, reference: LocalDate = today()): Boolean =
        !date.isBefore(historyWindowStart(reference)) && !date.isAfter(reference.plusDays(1))

    fun format(date: LocalDate, locale: Locale, pattern: String = "dd MMM yyyy"): String =
        date.format(DateTimeFormatter.ofPattern(pattern, locale))

    fun formatShort(date: LocalDate, locale: Locale): String = format(date, locale, "MMM yy")

    fun formatMonthYear(date: LocalDate, locale: Locale): String = format(date, locale, "MMM yyyy")
}
