package app.myhealth.domain.catalogue

/**
 * Canonical unit keys. Printed units are normalised (case, micro sign, superscripts, OCR spellings, Indian lab
 * conventions such as "lakhs/cumm") to one of these keys before conversion.
 */
object Units {
    const val G_DL = "g/dL"
    const val G_L = "g/L"
    const val MG_DL = "mg/dL"
    const val MG_L = "mg/L"
    const val MMOL_L = "mmol/L"
    const val UMOL_L = "µmol/L"
    const val U_L = "U/L"
    const val UIU_ML = "µIU/mL"
    const val NG_ML = "ng/mL"
    const val NG_DL = "ng/dL"
    const val PG_ML = "pg/mL"
    const val UG_DL = "µg/dL"
    const val NMOL_L = "nmol/L"
    const val PMOL_L = "pmol/L"
    const val PERCENT = "%"
    const val MMOL_MOL = "mmol/mol"
    const val FL = "fL"
    const val PG = "pg"
    const val K_UL = "10³/µL"
    const val PER_UL = "/µL"
    const val M_UL = "10⁶/µL"
    const val LAKH_UL = "lakh/µL"
    const val MM_HR = "mm/hr"
    const val EGFR = "mL/min/1.73m²"
    const val MEQ_L = "mEq/L"
    const val RATIO = "ratio"
    const val MG_G = "mg/g"
    const val NONE = ""

    private val synonyms: Map<String, String> = buildMap {
        fun alias(canonical: String, vararg keys: String) { keys.forEach { put(it, canonical) } }
        alias(G_DL, "g/dl", "gm/dl", "g%", "gm%", "gms/dl", "gram/dl", "grams/dl", "g/100ml")
        alias(G_L, "g/l", "gm/l")
        alias(MG_DL, "mg/dl", "mg%", "mgs/dl", "mg/100ml")
        alias(MG_L, "mg/l", "mg/lt")
        alias(MMOL_L, "mmol/l", "mmol/lt", "mmol/litre", "mmol/liter")
        alias(MEQ_L, "meq/l", "meq/lt", "meq/litre")
        alias(UMOL_L, "umol/l", "micromol/l", "µmol/l")
        alias(U_L, "u/l", "iu/l", "units/l", "unit/l", "u/lt", "iu/lt")
        alias(UIU_ML, "uiu/ml", "miu/l", "uu/ml", "micro-iu/ml", "microiu/ml", "mu/l", "miu/lt")
        alias(NG_ML, "ng/ml", "ug/l", "mcg/l")
        alias(NG_DL, "ng/dl")
        alias(PG_ML, "pg/ml", "ng/l")
        alias(UG_DL, "ug/dl", "mcg/dl", "µg/dl")
        alias(NMOL_L, "nmol/l", "nmol/lt")
        alias(PMOL_L, "pmol/l", "pmol/lt")
        alias(PERCENT, "%", "percent", "pct")
        alias(MMOL_MOL, "mmol/mol")
        alias(FL, "fl", "femtolitre", "femtoliter", "cu.micron", "cumicron", "cubicmicron", "u^3", "um^3")
        alias(PG, "pg", "picogram", "picograms", "pg/cell")
        alias(K_UL, "10^3/ul", "10^9/l", "/nl", "k/ul", "thou/ul", "thousand/ul", "1000/ul", "x1000/ul")
        alias(PER_UL, "/ul", "cells/ul", "cell/ul")
        alias(M_UL, "10^6/ul", "10^12/l", "mill/ul", "million/ul", "m/ul", "millions/ul", "mil/ul")
        alias(LAKH_UL, "10^5/ul", "lakh/ul", "lakhs/ul", "lac/ul", "lacs/ul")
        alias(MM_HR, "mm/hr", "mm/h", "mm/1sthr", "mm/1sthour", "mm/hour", "mm/1hr", "mmin1sthr", "mm/1st")
        alias(EGFR, "ml/min/1.73m^2", "ml/min/1.73m2", "ml/min/1.73", "ml/min", "ml/min/1.73sqm", "ml/min/1.73sq.m")
        alias(RATIO, "ratio")
        alias(MG_G, "mg/g", "mg/gm", "ug/mg", "mcg/mg")
    }

    /** Lower-cased, whitespace-free, micro sign → u, superscripts → ^n, cubic millimetre → µL. */
    fun normalizeKey(raw: String): String {
        var s = raw.trim().lowercase()
        s = s.replace('μ', 'u').replace('µ', 'u').replace("mcl", "ul")
        s = s.replace("³", "^3").replace("⁶", "^6").replace("⁹", "^9").replace("¹²", "^12").replace("²", "^2").replace("⁵", "^5")
        s = s.replace(Regex("\\s+"), "")
        s = s.replace("×", "x").replace("*10", "10").replace(Regex("^x(?=10)"), "").replace("10e", "10^").replace("10*", "10^")
        // OCR frequently drops the caret: "103/ul", "109/l", "106/ul", "1012/l"
        s = s.replace(Regex("^10(3|5|6|9|12)/"), "10^$1/")
        s = s.replace(Regex("(cumm|cu\\.mm|c\\.mm|cmm|mm\\^3|mm3)$"), "ul").replace("/ul.", "/ul")
        s = s.replace("lakhs", "lakh").replace("lacs", "lac")
        s = s.replace(Regex("\\.$"), "")
        return s
    }

    /** Canonical key for a printed unit, or null when unknown. */
    fun canonical(raw: String): String? {
        if (raw.isBlank()) return null
        val k = normalizeKey(raw)
        synonyms[k]?.let { return it }
        // "/cumm" etc. normalise to "/ul"; "thou/cumm" → "thou/ul"; "mill/cumm" → "mill/ul"
        return synonyms[k.removeSuffix(".")]
    }

    /** True if the token looks like a unit (used by the parser to separate units from other words). */
    fun looksLikeUnit(token: String): Boolean = canonical(token) != null
}
