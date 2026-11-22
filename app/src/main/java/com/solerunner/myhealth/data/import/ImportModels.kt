package com.solerunner.myhealth.data.import

import com.solerunner.myhealth.domain.lab.ParsedDocument
import com.solerunner.myhealth.domain.medicine.ParsedMedicineRow
import com.solerunner.myhealth.domain.model.ReportKind

/**
 * The reviewable output of the import pipeline. The import screen edits this draft, then hands it back
 * to [ImportService.save]; nothing is written to the encrypted database until the user confirms.
 */
data class ImportDraft(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val pageImages: List<ByteArray>,
    val pages: Int,
    val usedOcr: Boolean,
    val document: ParsedDocument,
    val medicines: List<ParsedMedicineRow> = emptyList(),
    val kind: ReportKind = ReportKind.LAB,
    val duplicateOfReportId: Long? = null,
) {
    val confidence: Double get() = document.confidence

    /** Reads the document text to decide what kind of record this is. */
    companion object {
        fun sniffKind(text: String): ReportKind {
            val lower = text.lowercase()
            val billish = listOf("invoice", "bill no", "pharmacy", "chemist", "mrp", "batch no", "gstin")
                .count { lower.contains(it) }
            val prescription = listOf("prescription", "rx", "advice", "dosage").count { lower.contains(it) }
            return when {
                billish >= 2 -> ReportKind.PHARMACY_BILL
                prescription >= 2 -> ReportKind.PRESCRIPTION
                else -> ReportKind.LAB
            }
        }
    }
}
