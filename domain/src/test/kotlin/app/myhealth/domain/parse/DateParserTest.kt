package app.myhealth.domain.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateParserTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun parsesCommonFormats() {
        assertEquals(d(2024, 3, 12), DateParser.parseFirst("12/03/2024"))
        assertEquals(d(2024, 3, 12), DateParser.parseFirst("12-03-2024"))
        assertEquals(d(2024, 3, 12), DateParser.parseFirst("12.03.24"))
        assertEquals(d(2024, 3, 12), DateParser.parseFirst("2024-03-12"))
        assertEquals(d(2024, 3, 12), DateParser.parseFirst("12-Mar-2024"))
        assertEquals(d(2024, 3, 12), DateParser.parseFirst("12 March 2024"))
        assertEquals(d(2024, 3, 12), DateParser.parseFirst("12th Mar, 2024"))
        assertEquals(d(2024, 3, 12), DateParser.parseFirst("Mar 12, 2024"))
        assertEquals(d(2024, 9, 5), DateParser.parseFirst("05 Sept 2024"))
    }

    @Test fun dayFirstUnlessImpossible() {
        assertEquals(d(2024, 4, 3), DateParser.parseFirst("03/04/2024"))   // 3 April (Indian convention)
        assertEquals(d(2024, 12, 25), DateParser.parseFirst("12/25/2024")) // month-first only when day > 12
        assertNull(DateParser.parseFirst("32/13/2024"))
    }

    @Test fun rejectsFutureAndAncientDates() {
        val today = d(2025, 1, 1)
        assertNull(DateParser.parseFirst("12/03/2030", today))
        assertNull(DateParser.parseFirst("12/03/1901", today))
    }

    @Test fun reportDatePrefersCollectionOverReportAndIgnoresDob() {
        val lines = listOf(
            "Patient: Asha Patel   DOB: 01/01/1980",
            "Registered on: 10/03/2024",
            "Reported: 13/03/2024 10:22",
            "Sample Collected On: 12/03/2024 08:05",
        )
        assertEquals(d(2024, 3, 12), DateParser.reportDate(lines))
        assertEquals(d(2024, 3, 13), DateParser.reportDate(lines.take(3)))
        assertEquals(d(2024, 3, 10), DateParser.reportDate(lines.take(2)))
        assertNull(DateParser.reportDate(lines.take(1)))
    }
}
