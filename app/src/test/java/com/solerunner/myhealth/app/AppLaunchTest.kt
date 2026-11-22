package com.solerunner.myhealth.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solerunner.myhealth.MainActivity
import com.solerunner.myhealth.MyHealthApplication
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Launches the real activity with the real Application, exactly as Android would.
 *
 * This exists because the first on-device run crashed before onboarding: the profile flow queried the
 * encrypted database before it finished opening. The test drains the main looper so a crash in a
 * LaunchedEffect fails the test instead of passing silently.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = MyHealthApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppLaunchTest {

    @Test
    fun `activity survives the database open race`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ShadowLooper.idleMainLooper()
            scenario.onActivity { activity ->
                assertFalse("activity finished during startup", activity.isFinishing)
                assertFalse("activity was destroyed during startup", activity.isDestroyed)
            }
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }
}
