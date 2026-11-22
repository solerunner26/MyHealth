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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class IngestTest {
    @Test fun sniffsByMagicBytesNotName() {
        assertEquals(AttachmentKind.Pdf, DocumentSniffer.kind("%PDF-1.7\n...".toByteArray()))
        assertEquals(AttachmentKind.Jpeg, DocumentSniffer.kind(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals(AttachmentKind.Png, DocumentSniffer.kind(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)))
        assertNull(DocumentSniffer.kind("<html>".toByteArray()))
        assertNull(DocumentSniffer.kind(ByteArray(0)))
    }

    @Test fun ocrPathProducesDraftWithOcrCount() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<TestApp>()
        val stack = app.container.openNow()!!
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
