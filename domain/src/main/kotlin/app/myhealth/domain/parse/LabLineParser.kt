package app.myhealth.domain.parse

import app.myhealth.domain.catalogue.Analyte
import app.myhealth.domain.catalogue.AnalyteCatalogue
import app.myhealth.domain.catalogue.IgnoredTerms
import app.myhealth.domain.catalogue.Units
import app.myhealth.domain.model.Comparator
import app.myhealth.domain.model.Sex

/** Range printed on a report, in the printed unit. */
data class PrintedRange(val low: Double?, val high: Double?, val text: String)

object RangeParser {
    private const val NUM = """(\d{1,3}(?:,\d{2,3})+(?:\.\d+)?|\d+(?:\.\d+)?|\.\d+)"""
    private val between = Regex("""$NUM\s*(?:-|to|–)\s*$NUM""", RegexOption.IGNORE_CASE)
    private val upper = Regex("""(?:<=|≤|<|up\s*to|upto|less\s*than|below)\s*$NUM""", RegexOption.IGNORE_CASE)
    private val lower = Regex("""(?:>=|≥|>|more\s*than|greater\s*than|above)\s*$NUM""", RegexOption.IGNORE_CASE)

    /** Finds the first range in [text]; returns the range and its index span. */
    fun find(text: String): Pair<PrintedRange, IntRange>? {
        val cands = listOfNotNull(
            between.find(text)?.let { m -> PrintedRange(Numbers.parse(m.groupValues[1]), Numbers.parse(m.groupValues[2]), m.value.trim()) to m.range },
            upper.find(text)?.let { m -> PrintedRange(null, Numbers.parse(m.groupValues[1]), m.value.trim()) to m.range },
            lower.find(text)?.let { m -> PrintedRange(Numbers.parse(m.groupValues[1]), null, m.value.trim()) to m.range },
        )
        return cands.minByOrNull { it.second.first }?.takeIf { (r, _) -> r.low == null || r.high == null || r.low <= r.high }
    }
}

/** Result of matching a catalogue alias in a line. */
data class AliasMatch(val analyte: Analyte?, val start: Int, val end: Int, val alias: String)

object AliasMatcher {
    private val index: List<Pair<String, Analyte?>> =
        (AnalyteCatalogue.aliasIndex.map { it.first to it.second } + IgnoredTerms.terms.map { it to null }).sortedByDescending { it.first.length }

    private val compiled = index.map { (alias, a) -> Regex("(?<![a-z0-9])" + Regex.escape(alias) + "(?![a-z0-9])") to (alias to a) }

    /** Longest alias wins; ties broken by earliest position. Null analyte = an ignored (different) test. */
    fun match(normalized: String, before: Int = normalized.length): AliasMatch? {
        var best: AliasMatch? = null
        for ((re, pair) in compiled) {
            if (best != null && pair.first.length < best.alias.length) break
            val m = re.find(normalized) ?: continue
            if (m.range.first >= before) continue
            val cand = AliasMatch(pair.second, m.range.first, m.range.last + 1, pair.first)
            if (best == null || cand.alias.length > best.alias.length || (cand.alias.length == best.alias.length && cand.start < best.start)) best = cand
        }
        return best
    }
}

/**
 * Parses one lab-result line such as "Haemoglobin (Hb) 11.2 L g/dL 13.0 - 17.0".
 * Order of value/flag/unit/range is tolerated; only the analyte name must come first.
 */
class LabLineParser(private val sex: Sex? = null, private val ageYears: Int? = null) {
    private val flagRe = Regex("""^(h|l|high|low|hh|ll|\*+|↑|↓|critical|abnormal|\(h\)|\(l\)|\[h\]|\[l\])$""", RegexOption.IGNORE_CASE)
    private val valueRe = Regex("""^(<=|>=|<|>|≤|≥)?\s*(\d{1,3}(?:,\d{2,3})+(?:\.\d+)?|\d+(?:\.\d+)?|\.\d+)$""")

    fun parse(line: String, continuation: String? = null): ParsedLabRow? {
        val norm = TextNormalizer.forMatching(line)
        val firstDigit = norm.indexOfFirst { it.isDigit() }.let { if (it < 0) norm.length else it }
        // Alias must start before the first number (names like "25-OH vitamin D" contain digits, so allow a margin)
        val match = AliasMatcher.match(norm, before = maxOf(firstDigit, 1) + 12) ?: return null
        val analyte = match.analyte ?: return null
        val afterAlias = stripLeadingQualifiers(norm.substring(match.end))
        val printedName = line.trim().let { orig -> orig.take(nameLengthIn(orig, norm, match.end)).trim().trimEnd(':', '-', ' ') }

        var tail = afterAlias
        var warnings = mutableSetOf<ParseWarning>()
        if (!tail.any { it.isDigit() } && continuation != null) {
            tail = TextNormalizer.forMatching(continuation)
            warnings += ParseWarning.ValueOnNextLine
        }
        if (!tail.any { it.isDigit() }) return null

        // Remove range first so its numbers are not mistaken for the value.
        val rangeHit = findRangeAfterValue(tail)
        val rangeText = rangeHit?.first
        val withoutRange = if (rangeHit != null) tail.removeRange(rangeHit.second).replace(Regex("\\s+"), " ") else tail

        val tokens = withoutRange.split(' ').filter { it.isNotBlank() }
        var value: Double? = null; var printedValue = ""; var comparator = Comparator.Eq
        var unit = ""; var flag: String? = null
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (value == null) {
                val joined = if ((t == "<" || t == ">" || t == "<=" || t == ">=") && i + 1 < tokens.size) t + tokens[i + 1] else t
                val m = valueRe.find(joined)
                if (m != null) {
                    value = Numbers.parse(m.groupValues[2]); printedValue = joined
                    comparator = when (m.groupValues[1]) { "<" -> Comparator.Lt; "<=", "≤" -> Comparator.Le; ">" -> Comparator.Gt; ">=", "≥" -> Comparator.Ge; else -> Comparator.Eq }
                    if (joined != t) i++
                    i++; continue
                }
            } else {
                if (flag == null && flagRe.matches(t)) { flag = t.uppercase().trim('(', ')', '[', ']'); i++; continue }
                if (unit.isEmpty()) {
                    // units can span 2 tokens ("mg / dl", "10^3 /ul", "ml/min/1.73 m2")
                    val two = if (i + 1 < tokens.size) t + tokens[i + 1] else null
                    val three = if (i + 2 < tokens.size) t + tokens[i + 1] + tokens[i + 2] else null
                    when {
                        three != null && Units.canonical(three) != null -> { unit = three; i += 3; continue }
                        two != null && Units.canonical(two) != null -> { unit = two; i += 2; continue }
                        Units.canonical(t) != null -> { unit = t; i++; continue }
                    }
                }
            }
            i++
        }
        if (value == null) return null
        if (comparator != Comparator.Eq) warnings += ParseWarning.ComparatorValue

        // also accept a unit printed *after* the range (common layout: "11.2 13.0-17.0 g/dL")
        if (unit.isEmpty() && rangeHit != null) {
            val rest = tail.substring(rangeHit.second.last + 1).trim().split(' ').filter { it.isNotBlank() }
            unit = listOfNotNull(rest.take(3).joinToString("").takeIf { rest.size >= 3 }, rest.take(2).joinToString("").takeIf { rest.size >= 2 }, rest.firstOrNull())
                .firstOrNull { Units.canonical(it) != null } ?: ""
        }

        val printedUnitCanon = Units.canonical(unit)
        var conversion = printedUnitCanon?.let { analyte.conversionFor(it) }
        var unitKeyUsed = printedUnitCanon
        if (unit.isEmpty() || conversion == null) {
            // Infer the unit by plausibility against the catalogue range (e.g. WBC "7800" → /µL).
            val inferred = inferUnit(analyte, value)
            if (inferred != null) {
                conversion = analyte.conversionFor(inferred); unitKeyUsed = inferred
                warnings += if (unit.isEmpty()) ParseWarning.UnitInferred else ParseWarning.UnitUnknown
            } else {
                warnings += ParseWarning.UnitUnknown
            }
        }
        val canonicalValue = conversion?.toCanonical(value)
        val printedRange = rangeText?.let { RangeParser.find(it)?.first }
        var low = printedRange?.low?.let { conversion?.toCanonical(it) }
        var high = printedRange?.high?.let { conversion?.toCanonical(it) }
        if (printedRange == null) {
            val cat = analyte.rangeFor(sex, ageYears)
            if (cat != null) { low = cat.low; high = cat.high; warnings += ParseWarning.RangeFromCatalogue } else warnings += ParseWarning.NoRange
        }
        if (canonicalValue != null && !plausible(analyte, canonicalValue)) warnings += ParseWarning.ImplausibleValue

        val confidence = when {
            canonicalValue == null || ParseWarning.ImplausibleValue in warnings -> Confidence.Low
            warnings.any { it == ParseWarning.UnitInferred || it == ParseWarning.UnitUnknown || it == ParseWarning.ValueOnNextLine } -> Confidence.Medium
            else -> Confidence.High
        }
        return ParsedLabRow(
            analyteId = analyte.id, printedName = printedName.ifBlank { analyte.name }, rawLine = listOfNotNull(line, continuation.takeIf { ParseWarning.ValueOnNextLine in warnings }).joinToString(" ⏎ "),
            printedValue = printedValue, value = value, comparator = comparator, printedUnit = unit,
            printedRange = printedRange?.text ?: "", canonicalValue = canonicalValue, canonicalUnit = conversion?.let { analyte.canonicalUnit },
            refLow = low, refHigh = high, flag = flag, confidence = confidence, warnings = warnings,
        )
    }

    /** Range must come after the value: search after the first number token. */
    private fun findRangeAfterValue(tail: String): Pair<String, IntRange>? {
        val first = Numbers.token.find(tail) ?: return null
        // if the line starts with "<0.5" the comparator belongs to the value; skip past it
        val from = first.range.last + 1
        val sub = tail.substring(from)
        val hit = RangeParser.find(sub) ?: return null
        val range = (hit.second.first + from)..(hit.second.last + from)
        return hit.first.text to range
    }

    private fun stripLeadingQualifiers(s: String): String {
        var t = s.trim()
        // drop qualifiers such as "25 oh", "serum", "method: ..." words that sit between name and value
        t = t.replace(Regex("^(serum|plasma|blood|whole blood|s|edta|calculated|calc|direct|by|method|photometry|hplc|immunoassay|clia|eclia|cmia|enzymatic|ise|automated|derived|\\s)+\\b"), "").trim()
        return t
    }

    private fun nameLengthIn(orig: String, norm: String, normEnd: Int): Int {
        // map normalised end position back to the original by counting letters
        val lettersInName = norm.take(normEnd).count { it.isLetterOrDigit() }
        var seen = 0
        for ((idx, ch) in orig.withIndex()) {
            if (ch.isLetterOrDigit()) seen++
            if (seen >= lettersInName) return idx + 1
        }
        return orig.length
    }

    companion object {
        /** A canonical value is plausible if within [low/20, high*20] of any catalogue range (very loose: catches unit slips). */
        fun plausible(a: Analyte, v: Double): Boolean {
            if (v < 0) return false
            val lows = a.ranges.mapNotNull { it.low }.filter { it > 0 }
            val highs = a.ranges.mapNotNull { it.high }.filter { it > 0 }
            val lo = lows.minOrNull()?.div(20) ?: 0.0
            val hi = highs.maxOrNull()?.times(20) ?: (lows.maxOrNull()?.times(50) ?: Double.MAX_VALUE)
            return v in lo..hi
        }

        /** Choose the unit (canonical or a known alternative) that puts [v] closest to the catalogue range. */
        fun inferUnit(a: Analyte, v: Double): String? {
            val range = a.ranges.firstOrNull { it.sex == null } ?: a.ranges.firstOrNull() ?: return a.canonicalUnit
            val lo = range.low ?: (range.high!! / 10); val hi = range.high ?: (range.low!! * 3)
            val mid = (lo + hi) / 2
            val options = listOf(a.canonicalUnit) + a.conversions.keys
            return options.map { u -> u to a.conversionFor(u)!!.toCanonical(v) }
                .filter { (_, c) -> c >= lo / 4 && c <= hi * 4 }
                .minByOrNull { (_, c) -> kotlin.math.abs(kotlin.math.ln(c.coerceAtLeast(1e-9) / mid)) }?.first
        }
    }
}
