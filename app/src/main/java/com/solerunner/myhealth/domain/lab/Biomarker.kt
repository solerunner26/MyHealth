package com.solerunner.myhealth.domain.lab

import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.DirectionOfGood
import com.solerunner.myhealth.domain.model.Sex
import com.solerunner.myhealth.domain.model.ValueStatus

/** A reference interval, optionally restricted to a sex and/or an age range. */
data class ReferenceRange(
    val low: Double?,
    val high: Double?,
    val sex: Sex? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val note: String? = null,
) {
    fun matches(sex: Sex, age: Int?): Boolean {
        if (this.sex != null && this.sex != Sex.UNSPECIFIED && this.sex != sex) return false
        if (minAge != null && age != null && age < minAge) return false
        if (maxAge != null && age != null && age > maxAge) return false
        return true
    }
}

/**
 * The knowledge the app has about one measurable biomarker: how to recognise it in OCR text, what
 * its canonical unit is, how to convert printed units, and what "normal" looks like.
 *
 * Keeping this table in code (not a downloaded file) is what makes the app work with no network.
 */
data class Biomarker(
    val key: String,
    val displayName: String,
    val category: AnalyteCategory,
    val canonicalUnit: String?,
    val aliases: List<String>,
    val ranges: List<ReferenceRange>,
    val directionOfGood: DirectionOfGood = DirectionOfGood.UNKNOWN,
    /** multiplier that converts a printed unit into [canonicalUnit]. */
    val unitConversions: Map<String, Double> = emptyMap(),
    val decimals: Int = 2,
    /** months after which a repeat test is commonly advised (drives "overdue" nudges). */
    val recheckMonths: Int? = null,
    /** values beyond these are escalated to "critical" severity in the focus engine. */
    val criticalLow: Double? = null,
    val criticalHigh: Double? = null,
    /** true when the healthy interpretation is "inside the band", not "as low/high as possible". */
    val bandTarget: Boolean = false,
) {

    fun rangeFor(sex: Sex, age: Int?): ReferenceRange? =
        ranges.firstOrNull { it.matches(sex, age) } ?: ranges.firstOrNull()

    /** Convert a value printed in [fromUnit] into the canonical unit (identity when unknown). */
    fun toCanonical(value: Double, fromUnit: String?): Double {
        val unit = normalizeUnitToken(fromUnit) ?: return value
        val canonical = normalizeUnitToken(canonicalUnit)
        if (canonical != null && unit == canonical) return value
        val factor = unitConversions.entries
            .firstOrNull { normalizeUnitToken(it.key) == unit }?.value
        return if (factor != null) value * factor else value
    }

    fun classify(canonicalValue: Double, sex: Sex = Sex.UNSPECIFIED, age: Int? = null): ValueStatus {
        val range = rangeFor(sex, age) ?: return ValueStatus.UNKNOWN
        val low = range.low
        val high = range.high
        if (low != null && canonicalValue < low) {
            val critical = criticalLow != null && canonicalValue <= criticalLow
            return if (critical) ValueStatus.VERY_LOW else ValueStatus.LOW
        }
        if (high != null && canonicalValue > high) {
            val critical = criticalHigh != null && canonicalValue >= criticalHigh
            return if (critical) ValueStatus.VERY_HIGH else ValueStatus.HIGH
        }
        return ValueStatus.IN_RANGE
    }

    /** Is a move from [from] to [to] good news? Used for the "improving/needs attention" wording. */
    fun isImproving(from: Double, to: Double): Boolean? = when (directionOfGood) {
        DirectionOfGood.LOWER -> to < from
        DirectionOfGood.HIGHER -> to > from
        DirectionOfGood.BALANCED -> {
            val range = rangeFor(Sex.UNSPECIFIED, null)
            val mid = range?.let { r ->
                val l = r.low ?: return@let null
                val h = r.high ?: return@let null
                (l + h) / 2.0
            }
            if (mid == null) null else kotlin.math.abs(to - mid) < kotlin.math.abs(from - mid)
        }
        DirectionOfGood.UNKNOWN -> null
    }
}

/**
 * Normalises a printed unit so that `µg/dL`, `μg/dl`, `UG/DL` and `ug / dl` all compare equal.
 * Both the catalog's conversion table and the text coming out of a report run through this, which is
 * what keeps the longitudinal table consistent when a lab changes how it prints a unit.
 */
internal fun normalizeUnitToken(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim()
        .lowercase(java.util.Locale.ROOT)
        .replace('\u00b5', 'u')
        .replace('\u03bc', 'u')
        .replace("per", "/")
        .replace(" ", "")
        .replace("x10", "10")
        .trimEnd('.', ',')
    return cleaned.ifBlank { null }
}
