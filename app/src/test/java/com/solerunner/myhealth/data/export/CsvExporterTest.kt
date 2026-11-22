package com.solerunner.myhealth.data.export

import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.DirectionOfGood
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.domain.model.SeriesPoint
import com.solerunner.myhealth.domain.model.ValueStatus
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvExporterTest {

    @Test
    fun `series export has a header and one line per point`() {
        val series = AnalyteSeries(
            key = "hemoglobin",
            displayName = "Haemoglobin",
            category = AnalyteCategory.HEMATOLOGY,
            unit = "g/dL",
            directionOfGood = DirectionOfGood.HIGHER,
            referenceLow = 13.0,
            referenceHigh = 17.0,
            points = listOf(
                SeriesPoint(LocalDate.of(2026, 1, 1), 12.9, ValueStatus.LOW, DataSource.REPORT_IMPORT),
                SeriesPoint(LocalDate.of(2026, 6, 1), 13.4, ValueStatus.IN_RANGE, DataSource.REPORT_IMPORT),
            ),
            decimals = 1,
        )
        val csv = CsvExporter.series(series)
        assertTrue(csv.contains(CsvExporter.SERIES_HEADER))
        assertTrue(csv.contains("2026-01-01,12.9,g/dL,13,17,LOW,REPORT_IMPORT"))
        assertTrue(csv.contains("Haemoglobin"))
    }

    @Test
    fun `result export escapes commas in titles`() {
        val report = Report(
            title = "CBC, ESR & lipids",
            sampleDate = LocalDate.of(2026, 3, 12),
            labName = "City Lab",
        )
        val result = LabResult(
            reportId = 1L,
            analyteKey = "hemoglobin",
            displayName = "Haemoglobin",
            value = 13.2,
            unit = "g/dL",
            refLow = 13.0,
            refHigh = 17.0,
            status = ValueStatus.IN_RANGE,
        )
        val csv = CsvExporter.results(listOf(report to result))
        assertTrue(csv.contains(CsvExporter.RESULTS_HEADER))
        assertTrue(csv.contains("\"CBC, ESR & lipids\""))
        assertTrue(csv.contains("13 – 17"))
    }
}
