package app.myhealth.domain.format

import app.myhealth.domain.model.Comparator
import org.junit.Assert.assertEquals
import org.junit.Test

class ValueFormatTest {
    @Test fun numbers() {
        assertEquals("14.2", ValueFormat.number(14.2, 1))
        assertEquals("0.30", ValueFormat.number(0.3, 2))
        assertEquals("150", ValueFormat.number(149.6, 0))
        assertEquals("2.5", ValueFormat.number(2.45, 1)) // half-up, not banker's
        assertEquals("—", ValueFormat.number(Double.NaN, 1))
        assertEquals("14", ValueFormat.compact(14.0)); assertEquals("1.25", ValueFormat.compact(1.25))
    }

    @Test fun signsRangesComparators() {
        assertEquals("+1.5", ValueFormat.signed(1.5, 1)); assertEquals("−0.4", ValueFormat.signed(-0.4, 1)); assertEquals("±0", ValueFormat.signed(0.0, 0))
        assertEquals("+12.5%", ValueFormat.percent(12.5))
        assertEquals("13.0–17.0", ValueFormat.range(13.0, 17.0, 1)); assertEquals("< 200", ValueFormat.range(null, 200.0, 0))
        assertEquals("> 40", ValueFormat.range(40.0, null, 0)); assertEquals("", ValueFormat.range(null, null, 0))
        assertEquals("<0.5", ValueFormat.withComparator(0.5, 1, Comparator.Lt))
    }
}
