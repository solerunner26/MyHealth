package app.myhealth

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myhealth.data.StorageOpener
import app.myhealth.data.StoragePaths
import app.myhealth.data.security.AndroidKeystoreWrapper
import app.myhealth.ui.summary.SummaryPdf
import app.myhealth.ui.summary.SummaryText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Checks that need real Android: PdfDocument, AndroidKeyStore, SQLCipher native code, a real cold start. */
@RunWith(AndroidJUnit4::class)
class DeviceTests {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun coldStartReachesOnboardingOrShell() {
        rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("welcome")).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodes(hasTestTag("shell")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun summaryPdfRendersHindiAndGujarati() {
        val t = SummaryText("डॉक्टर विज़िट सारांश", "બનાવ્યું", "रवि · 45", listOf("એલર્જી: નથી"), listOf("चर्चा की बातें" to List(60) { "LDL कोलेस्ट्रॉल सामान्य रेंज से ज़्यादा है $it" }), "निदान नहीं", { "पेज $it" })
        val pdf = SummaryPdf.render(t, 0xFF1D7C72.toInt())
        assertTrue(String(pdf.copyOfRange(0, 5)) == "%PDF-")
        assertTrue(pdf.size > 5_000)
    }

    @Test fun realKeystoreAndSqlCipherRoundTrip() {
        val root = File(ctx.noBackupFilesDir, "devtest-${System.nanoTime()}")
        val opener = StorageOpener(ctx, StoragePaths(root), AndroidKeystoreWrapper("myhealth.devtest"))
        val stack = opener.open()
        kotlinx.coroutines.runBlocking { stack.members.upsert(app.myhealth.domain.model.FamilyMember("m", "Test", app.myhealth.domain.model.Relationship.Self)) }
        stack.close()
        val header = File(root, "myhealth.db").readBytes().copyOfRange(0, 16)
        assertTrue("database must not be plaintext SQLite", !String(header).startsWith("SQLite format 3"))
        val again = opener.open()
        assertTrue(kotlinx.coroutines.runBlocking { again.members.all() }.size == 1)
        again.close(); opener.eraseEverything()
    }
}
