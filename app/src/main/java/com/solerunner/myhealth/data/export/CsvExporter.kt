package com.solerunner.myhealth.data.export

import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Report
import java.util.Locale

/**
 * CSV formatting lives in a pure object so it can be unit-tested without Android and reused by both
 * export paths (one analyte over time, or every result in the record).
 *
 * Rules that matter for spreadsheet compatibility:
 * * the first line is a header, always in the same order;
 * * values are written with a dot decimal separator (the canonical unit is next to them);
 * * any field containing a comma, quote or newline is quoted, and quotes are doubled.
 */
object CsvExporter {

    const val SERIES_HEADER = "date,value,unit,reference_low,reference_high,status,source"
    const val RESULTS_HEADER = "report_date,reported_date,report_title,lab,analyte,value,unit,canonical_value,canonical_unit,reference,status"

    fun series(series: AnalyteSeries): String = buildString {
        appendLine("analyte,${csv(series.displayName)}")
        appendLine("category,${series.category.name}")
        appendLine("unit,${csv(series.unit ?: "")}")
        appendLine(SERIES_HEADER)
        series.points.forEach { point ->
            appendLine(
                listOf(
                    point.date.toString(),
                    decimal(point.value),
                    csv(point.unit ?: series.unit ?: ""),
                    series.referenceLow?.let(::decimal) ?: "",
                    series.referenceHigh?.let(::decimal) ?: "",
                    point.status.name,
                    point.source.name,
                ).joinToString(","),
            )
        }
        if (series.points.isEmpty()) {
            appendLine("# no results in the last three years")
        }
    }

    fun results(rows: List<Pair<Report, LabResult>>): String = buildString {
        appendLine(RESULTS_HEADER)
        rows.sortedByDescending { it.first.sampleDate }.forEach { (report, result) ->
            appendLine(
                listOf(
                    report.sampleDate.toString(),
                    report.reportedDate?.toString() ?: "",
                    csv(report.title),
                    csv(report.labName ?: ""),
                    csv(result.displayName),
                    result.value?.let(::decimal) ?: "",
                    csv(result.unit ?: ""),
                    result.canonicalValue?.let(::decimal) ?: "",
                    csv(result.canonicalUnit ?: ""),
                    csv(result.refText ?: referenceRange(result)),
                    result.status.name,
                ).joinToString(","),
            )
        }
    }

    /** Substring used both for the "value / unit" summary and for the trend table. */
    fun referenceRange(result: LabResult): String = when {
        result.refText != null -> result.refText
        result.refLow != null && result.refHigh != null -> "${decimal(result.refLow)} – ${decimal(result.refHigh)}"
        result.refHigh != null -> "up to ${decimal(result.refHigh)}"
        result.refLow != null -> "from ${decimal(result.refLow)}"
        else -> ""
    }

    private fun decimal(value: Double): String {
        val text = String.format(Locale.US, "%.4f", value)
        return text.trimEnd('0').trimEnd('.')
    }

    private fun csv(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
