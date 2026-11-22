package app.myhealth.domain.catalogue

import app.myhealth.domain.model.Sex

enum class Panel { Blood, Diabetes, Lipids, Kidney, Electrolytes, Liver, Thyroid, Vitamins, Iron, Inflammation, Hormones, Urine, Other }

/** Which way is "better" for trend wording and focus rules. */
enum class Direction { LowerIsBetter, HigherIsBetter, InRange }

/** Converts a printed value to the canonical unit. Linear for nearly all analytes; HbA1c mmol/mol is affine. */
sealed interface Conversion {
    fun toCanonical(v: Double): Double
    data class Factor(val factor: Double) : Conversion { override fun toCanonical(v: Double) = v * factor }
    data class Affine(val factor: Double, val offset: Double) : Conversion { override fun toCanonical(v: Double) = v * factor + offset }
}

data class RefRange(
    val low: Double?,
    val high: Double?,
    val sex: Sex? = null,
    val minAgeYears: Int = 18,
    val maxAgeYears: Int = 200,
)

data class Analyte(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val panel: Panel,
    val canonicalUnit: String,
    /** printed-unit canonical key → conversion into [canonicalUnit]. The canonical unit itself is implicit (factor 1). */
    val conversions: Map<String, Conversion> = emptyMap(),
    val ranges: List<RefRange> = emptyList(),
    val direction: Direction = Direction.InRange,
    val criticalLow: Double? = null,
    val criticalHigh: Double? = null,
    /** Re-test interval when last value was in range / out of range (months). Null = no monitoring rule. */
    val monitorMonthsNormal: Int? = null,
    val monitorMonthsAbnormal: Int? = null,
    /** Percent-per-month change considered "rapid" by the focus engine. */
    val rapidChangePctPerMonth: Double = 10.0,
    val decimals: Int = 1,
    /** Analytes the health score treats as key markers. */
    val keyMarker: Boolean = false,
) {
    fun conversionFor(unitKey: String): Conversion? =
        if (unitKey == canonicalUnit) Conversion.Factor(1.0) else conversions[unitKey]

    /** Best catalogue range for a person: sex- and age-specific first, then general adult. */
    fun rangeFor(sex: Sex?, ageYears: Int?): RefRange? {
        val age = ageYears ?: 30
        val candidates = ranges.filter { age >= it.minAgeYears && age <= it.maxAgeYears }
        return candidates.firstOrNull { it.sex != null && it.sex == sex } ?: candidates.firstOrNull { it.sex == null }
            ?: candidates.firstOrNull()
    }
}
