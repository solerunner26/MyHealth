package com.solerunner.myhealth.data.import

/** One page of a document: recognized text plus a JPEG of the rendered page (null for text-layer PDFs). */
data class ReadPage(
    val index: Int,
    val text: String,
    val image: ByteArray? = null,
)

/** Everything the import pipeline extracted from a picked file. */
data class ReadDocument(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val pages: List<ReadPage>,
    val usedOcr: Boolean,
) {
    val text: String get() = pages.joinToString("\n") { it.text }
    val pageCount: Int get() = pages.size
    val hasText: Boolean get() = text.isNotBlank()
}
