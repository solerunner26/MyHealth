package app.myhealth

import android.view.WindowManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.core.AppLock
import app.myhealth.core.PinResult
import app.myhealth.core.Preferences
import app.myhealth.core.LocaleSupport
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.repository.ImportBundle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MutableClock(var now: Instant) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant() = now
}

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class AppLockTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()

    @Test fun pinVerificationLockoutAndBackgroundGrace() {
        val clock = MutableClock(Instant.parse("2025-01-01T10:00:00Z"))
        val prefs = Preferences(ctx)
        val lock = AppLock(prefs, clock)
        assertFalse(lock.enabled())
        lock.setPin("2580")
        assertTrue(lock.enabled()); assertFalse(lock.locked.value)
        assertTrue(prefs.pinMaterial()!!.first.isNotBlank() && !prefs.pinMaterial()!!.first.contains("2580"))
        // background shorter than the grace period does not lock; longer does
        lock.onBackground(); clock.now = clock.now.plusSeconds(10); lock.onForeground(); assertFalse(lock.locked.value)
        lock.onBackground(); clock.now = clock.now.plusSeconds(31); lock.onForeground(); assertTrue(lock.locked.value)
        // four wrong attempts are free, the fifth starts a 30 s wait, which doubles afterwards
        repeat(4) { assertTrue(lock.tryPin("0000") is PinResult.Wrong) }
        assertEquals(PinResult.LockedOut(30), lock.tryPin("0000"))
        assertTrue(lock.tryPin("2580") is PinResult.LockedOut) // even the right PIN waits
        clock.now = clock.now.plusSeconds(31)
        assertEquals(PinResult.LockedOut(60), lock.tryPin("1111"))
        clock.now = clock.now.plusSeconds(61)
        assertEquals(PinResult.Ok, lock.tryPin("2580")); assertFalse(lock.locked.value)
        assertEquals(0, prefs.failedAttempts())
        lock.disable(); assertFalse(lock.enabled()); assertEquals(null, prefs.pinMaterial())
    }

    @Test @Config(sdk = [32]) fun languageSwitchBelowApi33AppliesStoredTag() {
        Preferences(ctx).setLanguage("hi")
        assertEquals("होम", LocaleSupport.localized(ctx).getString(R.string.nav_home))
        Preferences(ctx).setLanguage("gu")
        assertEquals("હોમ", LocaleSupport.localized(ctx).getString(R.string.nav_home))
        Preferences(ctx).setLanguage("")
        assertEquals("Home", LocaleSupport.localized(ctx).getString(R.string.nav_home))
    }

    // SummaryPdf uses android.graphics.pdf.PdfDocument, which has no Robolectric implementation (native document is
    // null on the JVM). It is covered by the instrumented DeviceTests (device/emulator; CI job).
}

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class SettingsAndInsightsFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private fun waitFor(ms: Long = 10_000, c: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (c()) return; Thread.sleep(20) }
        error("timeout")
    }
    private fun waitTag(t: String) = waitFor { rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }
    private fun seed() = runBlocking {
        val s = ctx.container.openNow()!!
        s.members.upsert(FamilyMember("m1", "Ravi", Relationship.Self))
        val d = LocalDate.now().minusDays(3)
        s.records.saveImport(ImportBundle(Report("r1", "m1", ReportType.LabReport, "Lipids", d, createdAt = Instant.EPOCH),
            listOf(LabResult("x", "m1", "r1", "ldl", d, 160.0, "mg/dL", refHigh = 100.0)), emptyList(), emptyList()), emptyList())
    }

    @Test fun lockFlowSecureFlagInsightsEvidenceAndSummary() {
        seed()
        waitTag("shell")
        // FLAG_SECURE is on by default
        rule.activityRule.scenario.onActivity { assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }

        // Insights: expand "why" → evidence row with the exact value; score breakdown present
        rule.onNodeWithText(ctx.getString(R.string.nav_more)).performClick()
        waitTag("more"); rule.onNodeWithText(ctx.getString(R.string.more_insights)).performClick()
        waitTag("insights")
        rule.onNodeWithTag("why_range:ldl").performClick()
        waitTag("evidence")
        rule.onNodeWithText("160 mg/dL").assertExists()
        rule.onNodeWithTag("insightsList").performScrollToNode(hasTestTag("scoreBreakdown"))
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // Doctor summary screen offers PDF and CSV
        waitTag("more"); rule.onNodeWithTag("openSummary").performClick()
        waitTag("sharePdf"); rule.onNodeWithTag("shareCsv").assertExists()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // Settings: screenshot blocking off clears the flag; turn on app lock with PIN 1234
        waitTag("more"); rule.onNodeWithTag("more").performScrollToNode(hasTestTag("openSettings")); rule.onNodeWithTag("openSettings").performClick()
        waitTag("settings")
        rule.onNodeWithTag("secureSwitch").performScrollTo().performClick()
        waitFor { var off = false; rule.activityRule.scenario.onActivity { off = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0 }; off }
        rule.onNodeWithTag("lockSwitch").performScrollTo().performClick()
        waitTag("pinSetup")
        repeat(2) { listOf("1", "2", "3", "4", "ok").forEach { rule.onNodeWithTag("key_$it").performScrollTo().performClick() } }
        waitFor { ctx.container.lock.enabled() }

        // Lock: wrong PIN shows a message, correct PIN unlocks
        rule.runOnUiThread { ctx.container.lock.lockNow() }
        waitTag("lockScreen")
        listOf("9", "9", "9", "9").forEach { rule.onNodeWithTag("key_$it").performClick() }; rule.onNodeWithTag("key_ok").performClick()
        waitFor { rule.onAllNodes(hasText(ctx.getString(R.string.lock_wrong))).fetchSemanticsNodes().isNotEmpty() }
        listOf("1", "2", "3", "4").forEach { rule.onNodeWithTag("key_$it").performClick() }; rule.onNodeWithTag("key_ok").performClick()
        waitTag("settings")
    }
}
