package app.myhealth

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.core.designsystem.theme.ThemeMode
import app.myhealth.core.designsystem.tokens.MyHealthThemeId
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders real screens to PNG for design review. Opt-in (slow): ./gradlew :app:testDebugUnitTest -Pscreenshots
 * --tests '*DesignPreview*'. Output: app/build/screenshots/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class DesignPreviewTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun waitFor(c: () -> Boolean) { val end = System.currentTimeMillis() + 15_000; while (System.currentTimeMillis() < end) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); rule.waitForIdle(); if (c()) return; Thread.sleep(20) }; error("timeout") }
    private fun waitTag(t: String) = waitFor { rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }
    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(2_000) // let entrance and gauge animations settle
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun render() {
        assumeTrue(System.getProperty("screenshots") != null)
        val themes = (System.getProperty("themes") ?: "Aurora").split(",").map { MyHealthThemeId.valueOf(it) }
        val modes = (System.getProperty("modes") ?: "Dark").split(",").map { ThemeMode.valueOf(it) }
        DemoData.seed(ctx)
        for (t in themes) for (m in modes) {
            ctx.container.preferences.setColorTheme(t); ctx.container.preferences.setTheme(m)
            waitTag("shell")
            rule.onNodeWithText(ctx.getString(R.string.nav_home)).performClick(); waitTag("home"); shot("${t}_${m}_home")
            rule.onNodeWithTag("home").performScrollToNode(hasTestTag("tile_vitd")); shot("${t}_${m}_home_tests")
            rule.onNodeWithText(ctx.getString(R.string.nav_more)).performClick(); waitTag("more")
            rule.onNodeWithTag("more").performScrollToNode(hasTestTag("openSettings")); rule.onNodeWithTag("openSettings").performClick(); waitTag("settings"); shot("${t}_${m}_settings")
            rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
    }
}
