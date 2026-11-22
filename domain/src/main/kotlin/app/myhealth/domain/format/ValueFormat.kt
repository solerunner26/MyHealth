package app.myhealth.domain.format

import app.myhealth.domain.model.Comparator
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Number formatting for medical values. Always Latin digits and '.' decimal separator in every UI language
 * (lab reports print them that way; mixing digit systems invites misreading). See DECISIONS D-010.
 */
object ValueFormat {
    fun number(v: Double, decimals: Int): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        val bd = BigDecimal(v).setScale(decimals.coerceIn(0, 4), RoundingMode.HALF_UP)
        return bd.toPlainString()
    }

    /** Shortest form up to [maxDecimals] decimals: 14.0 → "14", 1.25 → "1.25". */
    fun compact(v: Double, maxDecimals: Int = 2): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        return BigDecimal(v).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    fun withComparator(v: Double, decimals: Int, c: Comparator) = c.symbol + number(v, decimals)

    fun signed(v: Double, decimals: Int): String = (if (v > 0) "+" else if (v < 0) "−" else "±") + number(kotlin.math.abs(v), decimals)

    fun percent(v: Double, decimals: Int = 1): String = signed(v, decimals) + "%"

    fun range(low: Double?, high: Double?, decimals: Int): String = when {
        low != null && high != null -> "${number(low, decimals)}–${number(high, decimals)}"
        high != null -> "< ${number(high, decimals)}"
        low != null -> "> ${number(low, decimals)}"
        else -> ""
    }
}
