package app.myhealth

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.core.designsystem.tokens.MyHealthThemeId
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class SplashAndThemeTest {
    companion object {
        @JvmStatic @BeforeClass fun on() { TestHooks.splash = true }
        @JvmStatic @AfterClass fun off() { TestHooks.splash = false }
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private fun waitTag(t: String) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty()) return; Thread.sleep(20) }
        error("timeout $t")
    }

    @Test fun splashThenEnterThenThemeSwitchKeepsTheAppRunningAndTheIconStatic() {
        runBlocking { ctx.container.openNow()!!.members.upsert(FamilyMember("m1", "Asha", Relationship.Self)) }
        // cold start: the themed splash asks the user to enter; the app is not composed behind it
        waitTag("splash")
        assertTrue(rule.onAllNodes(hasTestTag("shell")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("enterApp").performScrollTo().performClick()
        try { waitTag("shell") } catch (e: Throwable) { rule.onRoot(useUnmergedTree = true).printToLog("TREE"); println(ctx.container.splashDone); throw e }
        assertTrue(ctx.container.splashDone)

        // switching themes (every theme, both families) only recomposes: same activity, launcher components untouched
        val pm = ctx.packageManager
        val main = android.content.ComponentName(ctx, MainActivity::class.java)
        val before = pm.getComponentEnabledSetting(main)
        val activity = run { var a: MainActivity? = null; rule.activityRule.scenario.onActivity { a = it }; a!! }
        rule.onNodeWithTag("bottomBar").assertExists()
        for (t in MyHealthThemeId.entries) {
            rule.runOnUiThread { ctx.container.preferences.setColorTheme(t) }
            rule.waitForIdle()
            waitTag("shell")
        }
        rule.activityRule.scenario.onActivity { assertTrue(it === activity && !it.isFinishing) }
        assertEquals(before, pm.getComponentEnabledSetting(main))
        // older stored names still map to a theme
        assertEquals(MyHealthThemeId.Evergreen, MyHealthThemeId.parse("Botanical"))
        assertEquals(MyHealthThemeId.Crystal, MyHealthThemeId.parse("Precision"))
        assertEquals(MyHealthThemeId.Default, MyHealthThemeId.parse("Nonsense"))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class QuickSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private fun waitTag(t: String) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty()) return; Thread.sleep(20) }
        error("timeout $t")
    }

    @Test fun logoOpensQuickSettingsForThemeDarkModeAndLanguage() {
        runBlocking { ctx.container.openNow()!!.members.upsert(FamilyMember("m1", "Asha", Relationship.Self)) }
        waitTag("logoQuickSettings")
        rule.onNodeWithTag("logoQuickSettings").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitTag("qsDark")
        rule.onNodeWithTag("qsTheme_Citrus").performScrollTo().performClick()
        waitFor { ctx.container.preferences.state.value.colorTheme == MyHealthThemeId.Citrus }
        rule.onNodeWithTag("qsDark").performScrollTo().performClick()
        waitFor { ctx.container.preferences.state.value.theme == app.myhealth.core.designsystem.theme.ThemeMode.Dark }
        rule.onNodeWithTag("qsLang_hi").performScrollTo().performClick()
        waitFor { ctx.container.preferences.state.value.language == "hi" }
    }

    private fun waitFor(c: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (c()) return; Thread.sleep(20) }
        error("condition")
    }
}
