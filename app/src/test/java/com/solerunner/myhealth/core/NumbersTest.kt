package com.solerunner.myhealth.core

import com.solerunner.myhealth.core.util.Comparator
import com.solerunner.myhealth.core.util.Numbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumbersTest {

    @Test
    fun `parses plain and decimal values`() {
        assertEquals(13.2, Numbers.parse("13.2")!!.value, 0.0001)
        assertEquals(118.0, Numbers.parse("118")!!.value, 0.0001)
        assertEquals(0.85, Numbers.parse("0.85")!!.value, 0.0001)
    }

    @Test
    fun `normalises thousands separators and comma decimals`() {
        assertEquals(1234.5, Numbers.parse("1,234.5")!!.value, 0.0001)
        assertEquals(1234.5, Numbers.parse("1.234,5")!!.value, 0.0001)
        assertEquals(123456.0, Numbers.parse("1,23,456")!!.value, 0.0001)
        assertEquals(12.5, Numbers.parse("12,5")!!.value, 0.0001)
    }

    @Test
    fun `keeps comparator for less-than and greater-than values`() {
        val less = Numbers.parse("<0.5")!!
        assertEquals(0.5, less.value, 0.0001)
        assertEquals(Comparator.LESS_THAN, less.comparator)
        val greater = Numbers.parse(">1000")!!
        assertEquals(1000.0, greater.value, 0.0001)
        assertEquals(Comparator.GREATER_THAN, greater.comparator)
    }

    @Test
    fun `returns null for text without a number`() {
        assertNull(Numbers.parse("haemoglobin"))
        assertNull(Numbers.parse(""))
        assertNull(Numbers.parse(null))
    }
}
