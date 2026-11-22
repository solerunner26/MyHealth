package app.myhealth

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myhealth.data.security.SecureFailure
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ColdStartTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = ApplicationProvider.getApplicationContext<TestApp>()

    // The rule launches the app before @Before runs, so only clear injected failures afterwards.
    @After fun clearFailure() { TestHooks.wrapper.failWith = null }

    private fun awaitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun cleanInstallReachesOnboarding() {
        awaitTag("welcome")
        rule.onNodeWithTag("onbStart").assertExists()
    }

    @Test fun keystoreFailureShowsRecoverableErrorAndRetryRecovers() {
        awaitTag("welcome")
        // simulate the keystore going away and a fresh process start
        TestHooks.wrapper.failWith = SecureFailure.KeystoreUnavailable
        rule.runOnUiThread { ctx.container.eraseStateForTest() }
        rule.runOnIdle { }
        kotlinx.coroutines.runBlocking { ctx.container.openNow() }
        awaitTag("storageError")
        rule.onNodeWithText(ctx.getString(R.string.storage_keystore_title)).assertExists()
        // the keystore comes back: Retry opens the same data
        TestHooks.wrapper.failWith = null
        rule.onNodeWithText(ctx.getString(R.string.action_retry)).performClick()
        awaitTag("welcome")
    }

    @Test fun eraseFromErrorScreenStartsFresh() {
        awaitTag("welcome")
        TestHooks.wrapper.destroy() // key invalidated: data cannot be decrypted
        rule.runOnUiThread { ctx.container.eraseStateForTest() }
        kotlinx.coroutines.runBlocking { ctx.container.openNow() }
        awaitTag("storageError")
        rule.onNodeWithText(ctx.getString(R.string.storage_invalidated_title)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.storage_action_erase)).performClick()
        rule.onNodeWithText(ctx.getString(R.string.storage_erase_confirm)).performClick()
        awaitTag("welcome")
    }
}
