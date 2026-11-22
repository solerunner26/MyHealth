package com.solerunner.myhealth.data.import

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The document layer: SAF URI → PDF text layer → raster → on-device ML Kit OCR fallback.
 *
 * Why this order: a text-layer PDF is exact, instant and costs no battery. OCR runs only for scans and
 * photos, and only for pages whose text layer is empty. Nothing leaves the device, and the ML Kit model
 * ships inside the APK (no first-use download).
 */
class DocumentReader(private val context: Context) {

    @Volatile
    private var ocrEngine: OcrEngine? = null

    private fun ocr(): OcrEngine = ocrEngine ?: OcrEngine(context).also { ocrEngine = it }
    private val ocrThreshold = 40

    suspend fun read(uri: Uri, displayName: String?, mimeType: String?): ReadDocument =
        withContext(Dispatchers.IO) {
            val resolvedMime = mimeType ?: context.contentResolver.getType(uri) ?: guessMime(displayName)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot open the selected document")
            val name = displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document"
            when {
                resolvedMime == "application/pdf" || name.endsWith(".pdf", true) -> readPdf(bytes, name, resolvedMime)
                resolvedMime.startsWith("image/") -> readImage(bytes, name, resolvedMime)
                else -> error("Unsupported document type: $resolvedMime")
            }
        }

    fun close() {
        ocrEngine?.close()
        ocrEngine = null
    }

    private suspend fun readPdf(bytes: ByteArray, name: String, mime: String): ReadDocument {
        PDFBoxResourceLoader.init(context)
        val tempFile = File(context.cacheDir, "import-${System.currentTimeMillis()}.pdf")
        tempFile.outputStream().use { it.write(bytes) }
        val pages = mutableListOf<ReadPage>()
        var usedOcr = false
        try {
            val textByPage = runCatching { extractPdfText(tempFile) }.getOrElse { emptyList() }
            val pageCount = runCatching { pdfPageCount(tempFile) }.getOrDefault(textByPage.size)
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                if (pageCount == 0) return@use
                PdfRenderer(descriptor).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        val textLayer = textByPage.getOrNull(index).orEmpty().trim()
                        if (textLayer.length >= ocrThreshold) {
                            pages += ReadPage(index, textLayer)
                        } else {
                            usedOcr = true
                            val bitmap = renderPage(renderer, index)
                            val ocrText = runCatching { ocr().recognize(bitmap) }.getOrDefault("")
                            pages += ReadPage(index, ocrText.ifBlank { textLayer }, ocr().encodeJpeg(bitmap))
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            tempFile.delete()
        }
        return ReadDocument(name, mime, bytes, pages, usedOcr)
    }

    private suspend fun readImage(bytes: ByteArray, name: String, mime: String): ReadDocument {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("The image could not be decoded")
        val text = runCatching { ocr().recognize(bitmap) }.getOrDefault("")
        val image = ocr().encodeJpeg(bitmap)
        bitmap.recycle()
        return ReadDocument(name, mime, bytes, listOf(ReadPage(0, text, image)), usedOcr = true)
    }

    private fun extractPdfText(file: File): List<String> = PDDocument.load(file).use { document ->
        val stripper = PDFTextStripper()
        (1..document.numberOfPages).map { page ->
            stripper.startPage = page
            stripper.endPage = page
            stripper.getText(document)
        }
    }

    private fun pdfPageCount(file: File): Int = PDDocument.load(file).use { it.numberOfPages }

    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val scale = 2
            val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    private fun guessMime(name: String?): String = when {
        name == null -> "application/octet-stream"
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        else -> "application/octet-stream"
    }
}
