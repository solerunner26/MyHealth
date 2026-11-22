package app.myhealth.core

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom

/**
 * Item 4: the one-time code that unlocks a shared member file. 25 symbols from a 32-letter alphabet without look-alike
 * characters (≈125 bits), shown as text and as a QR code; the file and the code travel separately.
 */
object ShareCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val PREFIX = "MYHEALTH-SHARE:"

    fun generate(random: SecureRandom = SecureRandom()): String =
        (1..25).map { ALPHABET[random.nextInt(ALPHABET.length)] }.chunked(5).joinToString("-") { it.joinToString("") }

    /** Accepts what a person types: case, spaces and dashes do not matter. Null if it cannot be a code. */
    fun normalise(input: String): String? {
        val raw = input.trim().removePrefix(PREFIX).uppercase().filter { it.isLetterOrDigit() }
        if (raw.length != 25 || raw.any { it !in ALPHABET }) return null
        return raw.chunked(5).joinToString("-")
    }

    fun qr(code: String, size: Int = 640): Bitmap {
        val m = QRCodeWriter().encode(PREFIX + code, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Reads a share code from a photo of the QR (offline, ZXing). */
    fun scan(bitmap: Bitmap): String? = runCatching {
        val w = bitmap.width; val h = bitmap.height
        val px = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        val text = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, px))),
            mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)).text
        if (text.startsWith(PREFIX)) normalise(text) else null
    }.getOrNull()
}
