package com.solerunner.myhealth.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.solerunner.myhealth.domain.model.AnalyteSeries
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes exports into the app cache and hands back a shareable `content://` URI.
 *
 * Exports are deliberately short-lived: they live in cache (never in the vault, never in shared
 * storage) and are removed by the system when space is needed. The user chooses where they go through
 * the standard share sheet.
 */
class ExportService(private val context: Context) {

    suspend fun exportSeries(series: AnalyteSeries): File = withContext(Dispatchers.IO) {
        val safeName = series.key.ifBlank { "analyte" }
        write("${safeName}-trend.csv", CsvExporter.series(series))
    }

    suspend fun exportResults(rows: List<Pair<Report, LabResult>>): File = withContext(Dispatchers.IO) {
        write("myhealth-results.csv", CsvExporter.results(rows))
    }

    fun shareIntent(file: File, mime: String = "text/csv"): Intent {
        val uri = uriFor(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun write(name: String, content: String): File {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, name)
        file.writeText(content)
        return file
    }
}
