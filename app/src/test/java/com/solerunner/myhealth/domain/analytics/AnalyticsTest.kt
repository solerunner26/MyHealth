package com.solerunner.myhealth.domain.analytics

import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.DirectionOfGood
import com.solerunner.myhealth.domain.model.FocusCode
import com.solerunner.myhealth.domain.model.ScoreBand
import com.solerunner.myhealth.domain.model.SeriesPoint
import com.solerunner.myhealth.domain.model.ValueStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {

    private val today = LocalDate.of(2026, 11, 20)

    private fun point(date: String, value: Double, status: ValueStatus = ValueStatus.IN_RANGE) = SeriesPoint(
        date = LocalDate.parse(date),
        value = value,
        status = status,
        source = DataSource.REPORT_IMPORT,
    )

    private fun ldl(vararg points: SeriesPoint) = AnalyteSeries(
        key = "ldl_cholesterol",
        displayName = "LDL Cholesterol",
        category = AnalyteCategory.LIPIDS,
        unit = "mg/dL",
        directionOfGood = DirectionOfGood.LOWER,
        referenceLow = null,
        referenceHigh = 100.0,
        points = points.toList(),
        decimals = 0,
    )

    @Test
    fun `series keeps the three year window and sorts by date`() {
        val series = TrendAnalyzer.series(
            "ldl_cholesterol",
            listOf(
                point("2020-01-01", 150.0),
                point("2026-06-01", 130.0, ValueStatus.HIGH),
                point("2025-01-01", 120.0, ValueStatus.HIGH),
            ),
            today,
        )
        assertNotNull(series)
        assertEquals(2, series!!.count)
        assertEquals(LocalDate.parse("2025-01-01"), series.first!!.date)
        assertEquals(LocalDate.parse("2026-06-01"), series.latest!!.date)
    }

    @Test
    fun `same date results are de-duplicated, imported values winning`() {
        val series = TrendAnalyzer.series(
            "ldl_cholesterol",
            listOf(
                point("2026-06-01", 130.0).copy(source = DataSource.MANUAL),
                point("2026-06-01", 128.0).copy(source = DataSource.REPORT_IMPORT),
            ),
            today,
        )
        assertNotNull(series)
        assertEquals(1, series!!.count)
        assertEquals(128.0, series.latest!!.value, 0.001)
    }

    @Test
    fun `summary reports delta, direction and that a rising LDL is not improving`() {
        val series = ldl(point("2026-01-01", 100.0), point("2026-06-01", 130.0, ValueStatus.HIGH))
        val summary = TrendAnalyzer.summarise(series)
        assertEquals(130.0, summary.latest!!, 0.001)
        assertEquals(30.0, summary.delta!!, 0.001)
        assertEquals(com.solerunner.myhealth.domain.model.TrendDirection.RISING, summary.direction)
        assertEquals(false, summary.improving)
    }

    @Test
    fun `focus engine flags an out of range analyte and explains why`() {
        val series = ldl(point("2026-06-01", 165.0, ValueStatus.HIGH))
        val items = FocusEngine.analyse(FocusInput(today = today, series = listOf(series)))
        val item = items.firstOrNull { it.code == FocusCode.VALUE_HIGH }
        assertNotNull(item)
        assertEquals("ldl_cholesterol", item!!.analyteKey)
        assertEquals("165", item.args["value"])
        assertTrue(item.evidence.isNotEmpty())
    }

    @Test
    fun `focus engine notices a completed medicine course`() {
        val medicine = com.solerunner.myhealth.domain.model.Medicine(
            name = "Metformin",
            endDate = today.minusDays(3),
            startDate = today.minusDays(33),
        )
        val items = FocusEngine.analyse(FocusInput(today = today, medicines = listOf(medicine)))
        assertTrue(items.any { it.code == FocusCode.MEDICINE_COURSE_ENDED })
    }

    @Test
    fun `health score is explainable and ignores domains without data`() {
        val input = ScoreInput(
            today = today,
            series = listOf(
                ldl(point("2026-06-01", 90.0)),
                AnalyteSeries(
                    key = "hba1c",
                    displayName = "HbA1c",
                    category = AnalyteCategory.DIABETES,
                    unit = "%",
                    directionOfGood = DirectionOfGood.LOWER,
                    referenceLow = 4.0,
                    referenceHigh = 5.6,
                    points = listOf(point("2026-06-01", 5.2)),
                    decimals = 1,
                ),
            ),
        )
        val score = HealthScoreCalculator.compute(input)
        assertNotNull(score.total)
        assertTrue(score.total!! in 0..100)
        assertTrue(score.domains.any { it.key == "cardio" && it.score != null })
        assertTrue(score.domains.any { it.key == "lifestyle" && it.score == null })
        assertTrue(score.completeness in 0.0..1.0)
        assertFalse(score.band == ScoreBand.INSUFFICIENT_DATA)
    }
}
