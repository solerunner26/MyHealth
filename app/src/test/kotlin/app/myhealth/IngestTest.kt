package app.myhealth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.domain.model.AttachmentKind
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import app.myhealth.ingest.DocumentSniffer
import app.myhealth.ingest.ExtractedText
import app.myhealth.ingest.ExtractionProgress
import app.myhealth.ingest.ImportPipeline
import app.myhealth.ingest.PickedDocument
import app.myhealth.ingest.TextExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class IngestTest {
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun photographedPageIsFoundCroppedAndStraightened() {
        val bmp = android.graphics.Bitmap.createBitmap(1200, 1600, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        cv.drawColor(android.graphics.Color.rgb(50, 45, 40)) // desk
        cv.save(); cv.rotate(7f, 600f, 800f)
        cv.drawRect(250f, 300f, 950f, 1300f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(235, 235, 230) }) // A4-ish page 700x1000
        cv.restore()
        val f = java.io.File.createTempFile("cam", ".jpg").apply { outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) } }
        val preview = app.myhealth.ingest.PageCrop.load(f, 1200)!!
        val q = app.myhealth.ingest.PageCrop.detect(preview)!!
        assertEquals(0.36, q.area.toDouble(), 0.06) // 700*1000 / (1200*1600)
        assertTrue(app.myhealth.ingest.PageCrop.cropInPlace(f, q))
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.path, o)
        assertEquals(0.7, o.outWidth.toDouble() / o.outHeight, 0.06) // straightened page keeps its proportions
    }

    @Test fun sharedFileNamesCannotEscapeTheShareFolder() {
        val n = app.myhealth.ui.common.Share::safeName
        assertEquals("file", n("..")); assertEquals("_", n("/"))
        assertEquals("_.._etc_passwd", n("/../etc/passwd")); assertEquals("hidden", n(".hidden"))
        assertEquals("Ravi Shah-summary.pdf", n("Ravi Shah-summary.pdf"))
    }

    @Test fun sniffsByMagicBytesNotName() {
        assertEquals(AttachmentKind.Pdf, DocumentSniffer.kind("%PDF-1.7\n...".toByteArray()))
        assertEquals(AttachmentKind.Jpeg, DocumentSniffer.kind(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals(AttachmentKind.Png, DocumentSniffer.kind(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)))
        assertNull(DocumentSniffer.kind("<html>".toByteArray()))
        assertEquals(AttachmentKind.Webp, DocumentSniffer.kind("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(AttachmentKind.Heic, DocumentSniffer.kind("\u0000\u0000\u0000\u0018ftypheic\u0000\u0000".toByteArray(Charsets.ISO_8859_1)))
        assertNull(DocumentSniffer.kind(ByteArray(0)))
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun profilePhotoIsSquareSmallAndHasNoMetadata() {
        val bmp = android.graphics.Bitmap.createBitmap(1200, 800, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(40, 160, 140)) }
        val src = java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        // add EXIF with GPS + rotation to the source
        val f = java.io.File.createTempFile("photo", ".jpg").apply { writeBytes(src) }
        androidx.exifinterface.media.ExifInterface(f.absolutePath).apply {
            setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, "23/1,1/1,0/1"); setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, "6"); saveAttributes()
        }
        val out = app.myhealth.core.Images.profilePhoto(f.readBytes())!!
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(out, 0, out.size, o)
        assertEquals(512, o.outWidth); assertEquals(512, o.outHeight)
        val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(out))
        assertNull(exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE))
        assertNull(app.myhealth.core.Images.profilePhoto("not an image".toByteArray()))
    }

    @Test fun ocrPathProducesDraftWithOcrCount() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<TestApp>()
        val stack = ctx.container.openNow()!!
        val ocrText = "Report Date 02-Feb-2024\nHAEMOGLOBIN 10.9 L g/dl 12.0-15.5\nPLATELET COUNT 1.2 lakhs/cumm 1.5-4.1"
        val fake = object : TextExtractor {
            override suspend fun extract(doc: PickedDocument, progress: ExtractionProgress) = ExtractedText(listOf(ocrText), 1, 1)
        }
        val member = FamilyMember("m9", "Asha", Relationship.Self)
        val prepared = ImportPipeline(fake, stack.records).prepare(PickedDocument(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1), "photo.jpg", AttachmentKind.Jpeg),
            member, LocalDate.of(2025, 1, 1), "", ExtractionProgress { _, _ -> })
        val d = prepared.draft
        assertEquals(1, d.source!!.ocrPages); assertEquals(AttachmentKind.Jpeg, d.source!!.kind)
        assertEquals(LocalDate.of(2024, 2, 2), d.reportDate)
        assertEquals(listOf("hb", "plt"), d.labRows.map { it.analyteId })
        assertEquals("120", d.labRows.last().valueText)
        assertNull(d.duplicateOf)
    }
}
