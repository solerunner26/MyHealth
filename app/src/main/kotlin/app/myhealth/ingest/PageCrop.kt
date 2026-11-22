package app.myhealth.ingest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import app.myhealth.domain.importing.DocumentDetector
import app.myhealth.domain.importing.Pt
import app.myhealth.domain.importing.Quad
import java.io.File

/** Item 5: detect the page in a camera photo and straighten it (perspective warp), all on the phone. */
object PageCrop {
    /** Upright bitmap of [file] with its long edge at most [maxEdge] (EXIF rotation applied). */
    fun load(file: File, maxEdge: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0) return null
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxEdge) s *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val rot = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
        }
        if (rot == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot) }, true).also { if (it !== raw) raw.recycle() }
    }.getOrNull()

    fun detect(preview: Bitmap): Quad? {
        val small = Bitmap.createScaledBitmap(preview, (preview.width * 320f / maxOf(preview.width, preview.height)).toInt().coerceAtLeast(16),
            (preview.height * 320f / maxOf(preview.width, preview.height)).toInt().coerceAtLeast(16), true)
        val w = small.width; val h = small.height
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== preview) small.recycle()
        val lum = IntArray(px.size) { val c = px[it]; ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000 }
        return DocumentDetector.detect(lum, w, h)
    }

    /** Warps the page inside [quad] of [file] to a flat rectangle and overwrites [file] with it (JPEG, no metadata). */
    fun cropInPlace(file: File, quad: Quad): Boolean = runCatching {
        val src = load(file, 3000) ?: return false
        val (ow, oh) = quad.outputSize(src.width, src.height)
        val m = Matrix()
        val from = quad.points.flatMap { listOf(it.x * src.width, it.y * src.height) }.toFloatArray()
        val to = floatArrayOf(0f, 0f, ow.toFloat(), 0f, ow.toFloat(), oh.toFloat(), 0f, oh.toFloat())
        if (!m.setPolyToPoly(from, 0, to, 0, 4)) return false
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        src.recycle()
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        out.recycle()
        true
    }.getOrDefault(false)

    /** Corners used when nothing was detected: an inset rectangle the user drags into place. */
    val fallback = Quad(Pt(0.08f, 0.08f), Pt(0.92f, 0.08f), Pt(0.92f, 0.92f), Pt(0.08f, 0.92f))
}
