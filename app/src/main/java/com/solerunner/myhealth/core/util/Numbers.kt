package com.solerunner.myhealth.core.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

/** Comparison operator that a lab report may print next to a value (`<0.5`, `>1000`). */
enum class Comparator { EQUAL, LESS_THAN, GREATER_THAN, LESS_OR_EQUAL, GREATER_OR_EQUAL }

data class ParsedNumber(
    val value: Double,
    val comparator: Comparator = Comparator.EQUAL,
    val raw: String,
)

/**
 * Locale-tolerant number parsing for text that arrives from OCR or from PDF text layers.
 * OCR frequently mangles decimal separators, lakhs separators and stray spaces, so every entry
 * point here is deliberately forgiving and returns `null` instead of throwing.
 */
object Numbers {

    private val numericPattern = Regex("^[<>]?\\s*-?\\d{1,3}(?:[ ,.]\\d{3})*(?:[.,]\\d+)?%?$")

    fun parseNumberOrNull(text: String?): Double? = parse(text)?.value

    fun parse(text: String?): ParsedNumber? {
        if (text.isNullOrBlank()) return null
        val cleaned = text.trim()
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace("O", "0")
            .replace("o", "0")
            .replace("l", "1")
            .replace("I", "1")
            .removeSuffix("%")
        if (cleaned.isEmpty()) return null

        val comparator = when {
            cleaned.startsWith("<=") -> Comparator.LESS_OR_EQUAL
            cleaned.startsWith(">=") -> Comparator.GREATER_OR_EQUAL
            else -> when (cleaned.first()) {
                '<' -> Comparator.LESS_THAN
                '>' -> Comparator.GREATER_THAN
                else -> Comparator.EQUAL
            }
        }
        val digitsPart = cleaned.trimStart('<', '>', '=')
        if (!numericPattern.matches(digitsPart)) {
            // Last resort: keep a plain unsigned/integer run (e.g. "12mg" arrived glued to a unit).
            val loose = Regex("-?\\d+(?:[.,]\\d+)?").find(digitsPart)?.value ?: return null
            return parse(loose)
        }
        val normalized = normalizeSeparators(digitsPart)
        val value = normalized.toDoubleOrNull() ?: return null
        return ParsedNumber(value = value, comparator = comparator, raw = text.trim())
    }

    /**
     * "1,234.5" -> 1234.5   "1.234,5" -> 1234.5   "12,5" -> 12.5   "1,23,456" -> 123456
     * The rule: the *last* separator wins as the decimal point when it is followed by 1-2 digits and
     * the digits before it are not a pure 3-digit grouping.
     */
    internal fun normalizeSeparators(input: String): String {
        var s = input.replace(" ", "").replace("\u00A0", "")
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val lastSeparator = maxOf(lastComma, lastDot)
        if (lastSeparator < 0) return s
        val decimals = s.length - lastSeparator - 1
        val decimalIsComma = lastComma == lastSeparator
        val otherCount = if (decimalIsComma) s.count { it == '.' } else s.count { it == ',' }
        val treatAsDecimal = decimals in 1..2 && (otherCount > 0 || lastSeparator != s.length - 4 || decimals != 3)
        if (treatAsDecimal) {
            val intPart = s.substring(0, lastSeparator).replace(",", "").replace(".", "")
            val decPart = s.substring(lastSeparator + 1).replace(",", "").replace(".", "")
            s = "$intPart.$decPart"
        } else {
            s = if (decimalIsComma) s.replace(",", "") else s.replace(",", "")
        }
        return s
    }
}
