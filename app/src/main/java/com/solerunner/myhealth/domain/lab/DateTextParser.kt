package com.solerunner.myhealth.domain.lab

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Finds and interprets dates inside messy document text.
 *
 * Lab reports in the wild use `20.02.2026`, `20/02/2026`, `20-02-2026`, `2026-02-20`,
 * `20 Feb 2026`, `Feb 20, 2026` and `20/2/26`. When a value like `05/03/2026` is ambiguous we prefer
 * day-first, because Indian pathology reports are day-first; the caller can pass [dayFirst] = false
 * to override that.
 */
object DateTextParser {

    private val months = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10,
        "october" to 10, "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )

    /** All date-like tokens in [text], in order of appearance. */
    fun findAll(text: String, dayFirst: Boolean = true, referenceYear: Int = LocalDate.now().year): List<LocalDate> =
        candidateTokens(text).mapNotNull { parseToken(it, dayFirst, referenceYear) }

    /** Best-effort single date: the first parseable token, or null. */
    fun findFirst(text: String, dayFirst: Boolean = true, referenceYear: Int = LocalDate.now().year): LocalDate? =
        findAll(text, dayFirst, referenceYear).firstOrNull()

    /**
     * Picks the date that most likely is the *sample* date: reports usually print it next to a label
     * containing "sample"/"collection", otherwise we take the earliest plausible date, because
     * reporting happens after collection.
     */
    fun findSampleDate(text: String, dayFirst: Boolean = true): LocalDate? {
        val labelled = text.lines().firstOrNull { line ->
            val l = line.lowercase(Locale.ROOT)
            listOf("sample date", "collection date", "collected on", "date of collection", "sampling date")
                .any { it in l }
        }
        labelled?.let { line -> findFirst(line, dayFirst) }?.let { return it }
        return findAll(text, dayFirst).minOrNull()
    }

    /** Reporting/printing date, used as a fallback when the sample date is missing. */
    fun findReportDate(text: String, dayFirst: Boolean = true): LocalDate? {
        val labelled = text.lines().firstOrNull { line ->
            val l = line.lowercase(Locale.ROOT)
            listOf("report date", "reported on", "reporting date", "printed on", "date of report", "verified on")
                .any { it in l }
        }
        labelled?.let { line -> findFirst(line, dayFirst) }?.let { return it }
        return findAll(text, dayFirst).maxOrNull()
    }

    // ---------------------------------------------------------------------------------------

    private val numericDatePattern: Regex = Regex(
        "\\b(\\d{1,4})[-/. ](\\d{1,2}|[A-Za-z]{3,9})[-/. ,]{0,3}(\\d{1,4})?\\b",
    )

    private val namedMonthPattern: Regex = Regex(
        "\\b([A-Za-z]{3,9})[-/. ]{1,3}(\\d{1,2}),?[-/. ]{1,3}(\\d{2,4})\\b",
    )

    private val dayMonthNamePattern: Regex = Regex(
        "\\b(\\d{1,2})[-/. ]{1,3}([A-Za-z]{3,9})[-/. ,]{0,3}(\\d{2,4})?\\b",
    )

    private fun candidateTokens(text: String): List<String> {
        val tokens = mutableListOf<String>()
        namedMonthPattern.findAll(text).forEach { tokens += it.value }
        dayMonthNamePattern.findAll(text).forEach { tokens += it.value }
        numericDatePattern.findAll(text).forEach { match ->
            val monthPart = match.groupValues[2]
            val yearPart = match.groupValues[3]
            if (yearPart.isBlank() && monthPart.toIntOrNull() != null) return@forEach
            tokens += match.value
        }
        return tokens
    }

    fun parseToken(token: String, dayFirst: Boolean = true, referenceYear: Int = LocalDate.now().year): LocalDate? {
        val cleaned = token.trim().replace(",", " ").replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(' ', '-', '/', '.', ',').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null

        val monthIndex = parts.indexOfFirst { months.containsKey(it.lowercase(Locale.ROOT)) }
        if (monthIndex >= 0) {
            val month = months.getValue(parts[monthIndex].lowercase(Locale.ROOT))
            val dayPart = parts.getOrNull(monthIndex - 1)?.toIntOrNull()?.takeIf { it in 1..31 }
                ?: parts.getOrNull(monthIndex + 1)?.toIntOrNull()?.takeIf { it in 1..31 }
            val yearPart = parts.mapNotNull { it.toIntOrNull() }.firstOrNull { it > 31 }
            return build(dayPart, month, yearPart, referenceYear)
        }

        val numbers = parts.mapNotNull { it.toIntOrNull() }
        if (numbers.size < 3) return null
        val first = numbers[0]
        val second = numbers[1]
        val third = numbers[2]
        return if (first > 31) {
            build(
                day = if (dayFirst) second else third,
                month = if (dayFirst) third else second,
                year = first,
                referenceYear = referenceYear,
            )
        } else {
            if (dayFirst) build(first, second, third, referenceYear)
            else build(second, first, third, referenceYear)
        }
    }

    private fun build(day: Int?, month: Int, year: Int?, referenceYear: Int): LocalDate? {
        if (day == null || month !in 1..12 || day < 1 || day > 31) return null
        if (year == null) return null
        val resolvedYear = when {
            year > 1000 -> year
            year >= 100 -> 1900 + year
            else -> 2000 + year
        }
        return try {
            val date = LocalDate.of(resolvedYear, month, day)
            when {
                // Reject OCR nonsense such as 31/02 that would silently roll over.
                date.monthValue != month || date.dayOfMonth != day -> null
                date.year !in (referenceYear - 60)..(referenceYear + 2) -> null
                date.isAfter(LocalDate.now().plusDays(1)) -> null
                else -> date
            }
        } catch (_: DateTimeParseException) {
            null
        } catch (_: java.time.DateTimeException) {
            null
        }
    }
}

