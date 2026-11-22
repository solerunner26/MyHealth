package app.myhealth

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.VitalType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class CareFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private fun waitFor(ms: Long = 10_000, c: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (c()) return; Thread.sleep(20) }
        error("timeout")
    }
    private fun waitTag(t: String) = waitFor { rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun logBloodPressureAndAddMedicineWithReminders() {
        org.robolectric.Shadows.shadowOf(ctx).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val stack = runBlocking { ctx.container.openNow() }!!
        runBlocking { stack.members.upsert(FamilyMember("m1", "Asha", Relationship.Self, heightCm = 160.0)) }
        waitTag("shell")
        // Trends tab → Log a vital → BP 142/92
        rule.onNodeWithText(ctx.getString(R.string.nav_trends)).performClick()
        waitTag("logVital"); rule.onNodeWithTag("logVital").performScrollTo().performClick()
        waitTag("vitalEditor")
        rule.onNodeWithTag("vitalValue").performTextInput("142")
        rule.onNodeWithTag("vitalValue2").performTextInput("92")
        rule.onNodeWithTag("saveVital").performScrollTo().performClick()
        waitTag("vital_BloodPressure")
        val bp = runBlocking { stack.vitals.all("m1") }.single()
        assertEquals(VitalType.BloodPressure, bp.type); assertEquals(92.0, bp.value2!!, 0.0)
        rule.onNodeWithText(ctx.getString(R.string.band_high)).assertExists()

        // Care tab → Add a medicine with reminders
        rule.onNodeWithText(ctx.getString(R.string.nav_care)).performClick()
        // no medicines yet → the designed empty state offers the single primary action
        waitFor { rule.onAllNodes(hasText(ctx.getString(R.string.med_empty_title))).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(ctx.getString(R.string.med_add)).performClick()
        waitTag("medicineEditor")
        rule.onNodeWithTag("medName").performTextInput("Amlodipine")
        rule.onNodeWithTag("medReminders").performScrollTo().performClick()
        rule.onNodeWithTag("saveMedicine").performScrollTo().performClick()
        waitTag("med_Amlodipine")
        val m = runBlocking { stack.medicines.all("m1") }.single()
        assertTrue(m.remindersEnabled); assertEquals(1, m.reminderTimes.size)
        val alarms = org.robolectric.Shadows.shadowOf(ctx.getSystemService(android.app.AlarmManager::class.java))
        waitFor { alarms.scheduledAlarms.isNotEmpty() }
        rule.onNodeWithText(ctx.getString(R.string.med_reminders_on)).assertExists()
    }
}
