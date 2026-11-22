package com.solerunner.myhealth.core.util

/** Comparison operator that a lab report may print next to a value (`<0.5`, `>1000`). */
enum class Comparator { EQUAL, LESS_THAN, GREATER_THAN, LESS_OR_EQUAL, GREATER_OR_EQUAL }

data class ParsedNumber(
    val value: Double,
    val comparator: Comparator = Comparator.EQUAL,
    val raw: String,
)

/**
 * Locale-tolerant number parsing for text that arrives from OCR or a PDF text layer.
 *
 * OCR mangles separators, digits and stray spaces, so parsing is forgiving — but it never guesses:
 * a token must consist of digits (optionally with separators and a trailing unit) or the result is
 * `null`. That matters medically: inventing a value from a word like "haemoglobin" would be worse
 * than showing nothing.
 */
object Numbers {

    /** Digits with grouping/decimal separators, optionally prefixed by a comparison sign. */
    private val plainPattern = Regex("^[<>]?\\s*-?\\d[\\d.,]*$")

    /** Same, but with a unit glued on ("12mg", "5.4%", "120/80" is rejected elsewhere). */
    private val withUnitPattern = Regex("^([<>]?\\s*-?\\d[\\d.,]*)\\s*[A-Za-z%µμ/\\s·^²°-]*$")

    private val ocrDigitFixes = mapOf(
        'O' to '0', 'o' to '0', 'l' to '1', 'I' to '1', '|' to '1',
    )

    fun parseNumberOrNull(text: String?): Double? = parse(text)?.value

    fun parse(text: String?): ParsedNumber? {
        if (text.isNullOrBlank()) return null
        parseOnce(text)?.let { return it }
        // OCR confusion only makes sense for short, mostly-numeric tokens; never for prose.
        if (text.trim().length > 14) return null
        val fixed = text.trim().map { ocrDigitFixes[it] ?: it }.joinToString("")
        if (fixed == text.trim()) return null
        return parseOnce(fixed)
    }

    private fun parseOnce(raw: String): ParsedNumber? {
        val cleaned = raw.trim()
            .removeSuffix("%")
            .replace("\u00A0", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return null

        val comparator = when {
            cleaned.startsWith("<=") -> Comparator.LESS_OR_EQUAL
            cleaned.startsWith(">=") -> Comparator.GREATER_OR_EQUAL
            cleaned.startsWith("<") -> Comparator.LESS_THAN
            cleaned.startsWith(">") -> Comparator.GREATER_THAN
            else -> Comparator.EQUAL
        }

        val digits = when {
            plainPattern.matches(cleaned) -> cleaned
            else -> withUnitPattern.find(cleaned)?.groupValues?.get(1) ?: return null
        }.trimStart('<', '>', '=').trim()

        if (digits.isEmpty()) return null
        val normalized = normalizeSeparators(digits)
        val value = normalized.toDoubleOrNull() ?: return null
        return ParsedNumber(value = value, comparator = comparator, raw = raw.trim())
    }

    /**
     * Turns a digits-and-separators token into a canonical decimal string.
     *
     * Rules, in order:
     * * a final group of exactly three digits preceded by separator groups of 2–3 digits is Indian or
     *   Western grouping (`1,23,456`, `1,234`) → drop every separator;
     * * otherwise the last separator is a decimal point (`1,234.5`, `1.234,5`, `12,5`, `0.853`);
     * * anything else falls back to dropping separators.
     */
    internal fun normalizeSeparators(input: String): String {
        val s = input.replace(" ", "").replace("\u00A0", "")
        val lastSeparator = maxOf(s.lastIndexOf(','), s.lastIndexOf('.'))
        if (lastSeparator < 0) return s

        val head = s.substring(0, lastSeparator)
        val tail = s.substring(lastSeparator + 1)
        val headGroups = head.split(',', '.')
        val westernGrouping = headGroups.size > 1 && headGroups.drop(1).all { it.length == 3 }
        val indianGrouping = headGroups.size > 1 && headGroups.drop(1).all { it.length in 2..3 }

        return when {
            tail.length == 3 && (westernGrouping || indianGrouping) -> headGroups.joinToString("") + tail
            tail.length in 1..2 -> headGroups.joinToString("") + "." + tail
            headGroups.size == 1 && tail.length == 3 && head == "0" -> "0." + tail
            else -> s.replace(",", "").replace(".", "")
        }
    }
}
