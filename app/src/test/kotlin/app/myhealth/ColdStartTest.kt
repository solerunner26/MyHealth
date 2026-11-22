package app.myhealth

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColdStartTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun cleanInstallLaunchesShell() {
        rule.onNodeWithTag("shell").assertExists()
        rule.onNodeWithTag("bottomBar").assertExists()
    }
}
