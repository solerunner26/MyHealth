package app.myhealth.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object Images {
    /** Reads at most [max] bytes; null if the stream is larger (prevents a huge "image" exhausting memory). */
    fun readLimited(input: java.io.InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream(); val buf = ByteArray(64 * 1024); var total = 0
        while (true) { val n = input.read(buf); if (n < 0) break; total += n; if (total > max) return null; out.write(buf, 0, n) }
        return out.toByteArray()
    }

    /**
     * Square, upright, 512 px JPEG for profile photos. Re-encoding drops ALL metadata (GPS location, device, time),
     * so nothing but pixels is stored.
     */
    fun profilePhoto(bytes: ByteArray, size: Int = 512): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        // Extreme aspect ratios (e.g. 400 × 60000) would still decode huge: also bound the long edge.
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
            }
        }.getOrDefault(0f)
        val side = minOf(src.width, src.height)
        val m = Matrix().apply { postRotate(rotation); val s = size.toFloat() / side; postScale(s, s) }
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side, m, true)
        ByteArrayOutputStream().also { square.compress(Bitmap.CompressFormat.JPEG, 86, it); if (square !== src) square.recycle(); src.recycle() }.toByteArray()
    }.getOrNull()

    private const val MAX_EDGE = 4096
}
