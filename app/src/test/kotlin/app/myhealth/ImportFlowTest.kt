package app.myhealth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.domain.model.AttachmentKind
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Sex
import app.myhealth.domain.analytics.RangeStatus
import app.myhealth.domain.analytics.TrendEngine
import app.myhealth.ingest.AndroidTextExtractor
import app.myhealth.ingest.DocumentReader
import app.myhealth.ingest.ImportPipeline
import app.myhealth.ingest.OcrEngine
import app.myhealth.ingest.PickedDocument
import app.myhealth.ui.LocalSession
import app.myhealth.ui.Session
import app.myhealth.ui.importer.ImportEvent
import app.myhealth.ui.importer.ImportPhase
import kotlinx.coroutines.flow.first
import app.myhealth.ui.importer.ImportScreen
import app.myhealth.ui.importer.ImportViewModel
import app.myhealth.ui.records.RecordsList
import app.myhealth.ui.records.RecordsViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * End-to-end: real PDF text layer → parser → review UI edit → save → visible in records (and as a time series for trends).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ImportFlowTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()

    private fun pdf(lines: List<String>): ByteArray {
        PDFBoxResourceLoader.init(ctx)
        PDDocument().use { doc ->
            val page = PDPage(); doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 11f); cs.setLeading(16f); cs.newLineAtOffset(40f, 740f)
                lines.forEach { cs.showText(it); cs.newLine() }
                cs.endText()
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
    }

    /** Polls while idling Robolectric's paused main looper, so viewModelScope continuations run. */
    private fun waitFor(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            rule.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        error("condition not met within $timeoutMs ms")
    }

    private fun session(): Session {
        val stack = runBlocking { ctx.container.openNow() }!!
        return Session(ctx.container, stack)
    }

    private val failingOcr = object : OcrEngine { override suspend fun recognize(bitmap: android.graphics.Bitmap) = error("text layer expected") }

    @Test fun importReviewEditSaveVisibleInRecordsAndSeries() {
        val s = session()
        val member = FamilyMember("m1", "Ravi", Relationship.Self, LocalDate.of(1980, 1, 1), Sex.Male)
        runBlocking { s.stack.members.upsert(member) }
        val vm = ImportViewModel(s, DocumentReader(ctx.contentResolver), ImportPipeline(AndroidTextExtractor(ctx, failingOcr), s.stack.records), ctx.container.clock)

        rule.setContent {
            MyHealthTheme { CompositionLocalProvider(LocalSession provides s) {
                val st by vm.state.collectAsStateWithLifecycle()
                ImportScreen(st, vm::onEvent, onBack = {}, onPickFile = {}, onTakePhoto = {})
            } }
        }
        waitFor(5_000) { vm.state.value.member != null }
        val bytes = pdf(listOf(
            "SUNRISE DIAGNOSTIC LABORATORY", "Patient Name : Mr. Ravi Shah   Age: 44",
            "Sample Collected On : 12/03/2024", "Haemoglobin 13.1 g/dL 13.0 - 17.0", "HbA1c 6.4 % 4.0 - 5.6", "TSH 3.1 uIU/mL 0.27 - 4.2",
        ))
        vm.onDocument(PickedDocument(bytes, "cbc.pdf", AttachmentKind.Pdf))
        rule.waitUntil(15_000) { rule.onAllNodes(hasTestTag("reviewList")).fetchSemanticsNodes().isNotEmpty() }
        val draft = vm.state.value.draft!!
        assertEquals(LocalDate.of(2024, 3, 12), draft.reportDate)
        assertEquals(listOf("hb", "hba1c", "tsh"), draft.labRows.map { it.analyteId })
        assertEquals(0, draft.source!!.ocrPages)

        // user corrects the HbA1c value that was "read wrongly"
        // compact review: one line per value; tapping the line opens its editor
        rule.onNodeWithTag("reviewList").performScrollToNode(hasTestTag("labRow_hba1c"))
        rule.onNodeWithTag("labRow_hba1c").performClick()
        rule.onNodeWithTag("reviewList").performScrollToNode(hasTestTag("value_hba1c"))
        rule.onNodeWithTag("value_hba1c").performTextReplacement("6.2")
        rule.onNodeWithTag("saveImport").performClick()
        waitFor(10_000) { vm.state.value.phase is ImportPhase.Saved }

        val results = runBlocking { s.stack.records.results("m1") }
        assertEquals(6.2, results.first { it.analyteId == "hba1c" }.value, 1e-9)
        assertEquals("13.0 - 17.0", results.first { it.analyteId == "hb" }.printedRange)
        // visible in trends: the saved value is the latest point of the HbA1c series, with the printed band
        val trend = TrendEngine.trend(results, "hba1c", LocalDate.now(), 5)!!
        assertEquals(6.2, trend.latest.value, 1e-9); assertEquals(5.6, trend.bandHigh!!, 1e-9)
        assertEquals(RangeStatus.High, trend.latest.status)
        val report = runBlocking { s.stack.records.observeReports("m1").first() }.single()
        assertEquals("SUNRISE DIAGNOSTIC LABORATORY", report.labName)
        // the original PDF is stored encrypted and decrypts byte-for-byte
        assertTrue(runBlocking { s.stack.records.attachmentBytes(report.attachments.single().id) }.contentEquals(bytes))

        // a second import of the same file is flagged as a duplicate
        vm.onEvent(ImportEvent.Reset)
        vm.onDocument(PickedDocument(bytes, "cbc.pdf", AttachmentKind.Pdf))
        waitFor(15_000) { vm.state.value.phase == ImportPhase.Review }
        assertEquals(report.id, vm.state.value.draft!!.duplicateOf!!.report.id)
    }

    @Test fun reportForUnknownPersonOffersToAddThemAndKnownPersonIsAutoMatched() {
        val s = session()
        runBlocking {
            s.stack.members.upsert(FamilyMember("m1", "Ravi Shah", Relationship.Self))
            s.stack.members.upsert(FamilyMember("m2", "Meera Shah", Relationship.Spouse))
            ctx.container.preferences.setActiveMember("m1")
        }
        val vm = ImportViewModel(s, DocumentReader(ctx.contentResolver), ImportPipeline(AndroidTextExtractor(ctx, failingOcr), s.stack.records), ctx.container.clock)
        waitFor(5_000) { vm.state.value.member != null && vm.state.value.members.size == 2 }
        // 1) printed name belongs to nobody → ask; "add as new member" creates the profile and files the report there
        vm.onDocument(PickedDocument(pdf(listOf("City Pathology Laboratory", "Patient Name : Mr. SURESH MEHTA   Age: 61", "Date: 05/01/2024", "TSH 5.9 uIU/mL 0.27 - 4.2")), "a.pdf", AttachmentKind.Pdf))
        waitFor(15_000) { vm.state.value.phase is ImportPhase.ChooseMember }
        vm.onEvent(ImportEvent.AddMemberFromReport((vm.state.value.phase as ImportPhase.ChooseMember).printedName))
        waitFor(5_000) { vm.state.value.phase == ImportPhase.Review }
        val suresh = runBlocking { s.stack.members.all() }.single { it.name == "Suresh Mehta" }
        assertEquals(suresh.id, vm.state.value.draft!!.memberId)
        // 2) printed name matches another member → filed under them automatically, with a visible notice
        vm.onEvent(ImportEvent.Reset)
        vm.onEvent(ImportEvent.ChooseMember("m1"))
        vm.onDocument(PickedDocument(pdf(listOf("City Pathology Laboratory", "Patient Name : Mrs. Meera Shah   Age: 41", "Date: 06/01/2024", "TSH 2.1 uIU/mL 0.27 - 4.2")), "b.pdf", AttachmentKind.Pdf))
        waitFor(15_000) { vm.state.value.phase == ImportPhase.Review }
        assertEquals("m2", vm.state.value.draft!!.memberId)
        assertEquals("Meera Shah", vm.state.value.autoMatchedName)
    }

    @Test fun severalPagesBecomeOneRecordWithEveryFileEncrypted() {
        val s = session()
        runBlocking { s.stack.members.upsert(FamilyMember("m1", "Ravi", Relationship.Self)); ctx.container.preferences.setActiveMember("m1") }
        val vm = ImportViewModel(s, DocumentReader(ctx.contentResolver), ImportPipeline(AndroidTextExtractor(ctx, failingOcr), s.stack.records), ctx.container.clock)
        waitFor(5_000) { vm.state.value.member != null }
        val p1 = pdf(listOf("Sunrise Hospital Discharge Summary", "Date: 03/02/2024", "Haemoglobin 11.2 g/dL 13.0 - 17.0"))
        val p2 = pdf(listOf("Sunrise Hospital laboratory page two", "Creatinine 1.4 mg/dL 0.7 - 1.3", "Haemoglobin 99 g/dL 13 - 17"))
        val f2 = java.io.File(ctx.cacheDir, "page2.pdf").apply { writeBytes(p2) }
        vm.onDocument(PickedDocument(p1, "page1.pdf", AttachmentKind.Pdf))
        waitFor(15_000) { vm.state.value.phase == ImportPhase.Review }
        vm.onEvent(ImportEvent.AddPage(android.net.Uri.fromFile(f2), "page2.pdf"))
        waitFor(15_000) { vm.state.value.draft!!.sources.size == 2 && vm.state.value.phase == ImportPhase.Review }
        assertEquals(listOf("hb", "creat"), vm.state.value.draft!!.labRows.map { it.analyteId })
        vm.onEvent(ImportEvent.Save("Hospital file"))
        waitFor(10_000) { vm.state.value.phase is ImportPhase.Saved }
        val report = runBlocking { s.stack.records.observeReports("m1").first() }.single()
        assertEquals(2, report.attachments.size)
        assertTrue(runBlocking { s.stack.records.attachmentBytes(report.attachments[1].id) }.contentEquals(p2))
        assertEquals(11.2, runBlocking { s.stack.records.results("m1") }.first { it.analyteId == "hb" }.value, 1e-9)
    }

    @Test fun savedReportIsVisibleInRecordsList() {
        val s = session()
        runBlocking {
            s.stack.members.upsert(FamilyMember("m1", "Ravi", Relationship.Self))
            ctx.container.preferences.setActiveMember("m1")
        }
        val vm = ImportViewModel(s, DocumentReader(ctx.contentResolver), ImportPipeline(AndroidTextExtractor(ctx, failingOcr), s.stack.records), ctx.container.clock)
        waitFor(5_000) { vm.state.value.member != null }
        vm.onDocument(PickedDocument(pdf(listOf("City Pathology Laboratory", "Date: 05/01/2024", "Thyroid Function Test", "TSH 5.9 uIU/mL 0.27 - 4.2")), "thyroid.pdf", AttachmentKind.Pdf))
        waitFor(15_000) { vm.state.value.phase == ImportPhase.Review }
        vm.onEvent(ImportEvent.SetTitle("Thyroid check"))
        vm.onEvent(ImportEvent.Save("Lab report"))
        waitFor(10_000) { vm.state.value.phase is ImportPhase.Saved }

        val records = RecordsViewModel(s)
        rule.setContent { MyHealthTheme { val st by records.state.collectAsStateWithLifecycle(); RecordsList(st, {}, {}) } }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("recordsList")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Thyroid check").assertExists()
    }
}
