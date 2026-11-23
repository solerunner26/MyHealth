package app.myhealth.ingest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.myhealth.domain.model.AttachmentKind
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Per-page text plus how many pages needed OCR. */
data class ExtractedText(val pages: List<String>, val pageCount: Int, val ocrPages: Int)

fun interface ExtractionProgress { fun onPage(done: Int, total: Int) }

interface TextExtractor {
    suspend fun extract(doc: PickedDocument, progress: ExtractionProgress = ExtractionProgress { _, _ -> }): ExtractedText
}

interface OcrEngine { suspend fun recognize(bitmap: Bitmap): String }

/** ML Kit bundled on-device Latin recogniser. The model ships in the APK; no download, no network (INTERNET is stripped). */
class MlKitOcr : OcrEngine {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    override suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        try {
            client.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text ->
                    // keep ML Kit's line structure; blocks are already in reading order
                    cont.resume(text.textBlocks.joinToString("\n") { b -> b.lines.joinToString("\n") { it.text } })
                }
                .addOnFailureListener { cont.resumeWithException(IngestException(IngestError.OcrUnavailable, it)) }
        } catch (e: Exception) {
            cont.resumeWithException(IngestException(IngestError.OcrUnavailable, e))
        }
    }
}

/**
 * PDF: text layer first (PdfBox, sorted by position so table rows stay on one line); pages with too little text are
 * rendered and OCR'd. Images: OCR. Everything happens in memory: no plaintext temp files.
 */
class AndroidTextExtractor(private val context: Context, private val ocr: OcrEngine = MlKitOcr(), private val maxPages: Int = MAX_PAGES) : TextExtractor {
    private val initialised by lazy { PDFBoxResourceLoader.init(context.applicationContext); true }

    override suspend fun extract(doc: PickedDocument, progress: ExtractionProgress): ExtractedText = when (doc.kind) {
        AttachmentKind.Pdf -> extractPdf(doc.bytes, progress)
        AttachmentKind.Jpeg, AttachmentKind.Png, AttachmentKind.Webp, AttachmentKind.Heic -> {
            val bmp = decodeScaled(doc.bytes) ?: throw IngestException(IngestError.Unreadable)
            try {
                progress.onPage(0, 1)
                ExtractedText(listOf(ocr.recognize(bmp)), 1, 1).also { progress.onPage(1, 1) }
            } finally { bmp.recycle() }
        }
    }

    private suspend fun extractPdf(bytes: ByteArray, progress: ExtractionProgress): ExtractedText {
        check(initialised)
        val document = try {
            PDDocument.load(bytes)
        } catch (e: InvalidPasswordException) {
            throw IngestException(IngestError.PasswordProtected, e)
        } catch (e: Exception) {
            throw IngestException(IngestError.Unreadable, e)
        }
        document.use { pdf ->
            if (pdf.isEncrypted && !pdf.currentAccessPermission.canExtractContent()) throw IngestException(IngestError.PasswordProtected)
            val total = pdf.numberOfPages.coerceAtMost(maxPages)
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val renderer by lazy { PDFRenderer(pdf) }
            var ocrPages = 0
            val pages = (1..total).map { pageNo ->
                progress.onPage(pageNo - 1, total)
                stripper.startPage = pageNo; stripper.endPage = pageNo
                val text = runCatching { stripper.getText(pdf) }.getOrDefault("")
                if (text.count { it.isLetterOrDigit() } >= MIN_TEXT_CHARS) text
                else {
                    // Scanned page: OCR it, but never let a failed or weaker OCR result discard a usable text layer.
                    // A crafted page can declare an enormous media box: cap the rendered size, not just the DPI.
                    val box = runCatching { pdf.getPage(pageNo - 1).mediaBox }.getOrNull()
                    val longest = box?.let { maxOf(it.width, it.height) }?.takeIf { it > 0f } ?: 612f
                    val dpi = minOf(OCR_DPI, MAX_IMAGE_EDGE * 72f / longest).coerceAtLeast(1f)
                    val bmp = runCatching { renderer.renderImageWithDPI(pageNo - 1, dpi, com.tom_roush.pdfbox.rendering.ImageType.RGB) }.getOrNull()
                        ?: return@map text
                    val ocrText = try { runCatching { ocr.recognize(bmp) }.getOrNull() } finally { bmp.recycle() }
                    if (ocrText != null && ocrText.count { it.isLetterOrDigit() } > text.count { it.isLetterOrDigit() }) { ocrPages++; ocrText } else text
                }
            }
            progress.onPage(total, total)
            return ExtractedText(pages, pdf.numberOfPages, ocrPages)
        }
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_EDGE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        const val MAX_PAGES = 40
        const val MIN_TEXT_CHARS = 40
        const val OCR_DPI = 200f
        const val MAX_IMAGE_EDGE = 3000
    }
}
