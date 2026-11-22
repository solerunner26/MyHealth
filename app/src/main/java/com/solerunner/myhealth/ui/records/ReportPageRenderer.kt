package com.solerunner.myhealth.ui.records

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders a stored (encrypted) page for the report viewer. PDFs are rasterised through PdfRenderer; images
 * are decoded with a sample size so a phone photo does not allocate its full resolution.
 */
object ReportPageRenderer {

    suspend fun render(container: AppContainer, attachment: Attachment, maxDimension: Int = 1600): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = container.vault.readAllBytes(attachment.storedName)
                if (attachment.mimeType == "application/pdf") renderPdf(bytes, attachment.pageIndex, maxDimension)
                else decodeImage(bytes, maxDimension)
            }.getOrNull()
        }

    private fun decodeImage(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun renderPdf(bytes: ByteArray, pageIndex: Int, maxDimension: Int): Bitmap? {
        val temp = File.createTempFile("report-page", ".pdf")
        return try {
            temp.writeBytes(bytes)
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    val index = pageIndex.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(index).use { page ->
                        val longest = maxOf(page.width, page.height).coerceAtLeast(1)
                        val scale = (maxDimension.toFloat() / longest).coerceIn(0.5f, 2f)
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            temp.delete()
        }
    }
}
