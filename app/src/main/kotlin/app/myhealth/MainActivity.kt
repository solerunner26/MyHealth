package app.myhealth

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.core.LocaleSupport
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.ui.LocalSession
import app.myhealth.ui.Session
import app.myhealth.ui.common.SecureErase
import app.myhealth.ui.lock.LockScreen
import app.myhealth.ui.nav.AppRoot
import app.myhealth.ui.startup.StorageGate

/** Single activity. FragmentActivity (a ComponentActivity) only because BiometricPrompt requires it. */
class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // API 26–32: apply the in-app language; API 33+ uses the platform per-app locale.
        super.attachBaseContext(LocaleSupport.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        applySecureFlag(c.preferences.state.value.blockScreenshots)
        setContent {
            val prefs by c.preferences.state.collectAsStateWithLifecycle()
            val storage by c.storage.collectAsStateWithLifecycle()
            val locked by c.lock.locked.collectAsStateWithLifecycle()
            LaunchedEffect(prefs.blockScreenshots) { applySecureFlag(prefs.blockScreenshots) }
            MyHealthTheme(prefs.theme, prefs.colorTheme) {
                app.myhealth.core.designsystem.components.MyHealthAmbientBackground {
                // The lock is an opaque overlay: the app underneath keeps its navigation and unsaved input, but is
                // hidden from the screen and from accessibility services while locked.
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)) {
                        StorageGate(storage, onRetry = c::retry, onErase = { SecureErase.run(this@MainActivity) }) { stack ->
                            val session = remember(stack) { Session(c, stack) }
                            CompositionLocalProvider(LocalSession provides session) { AppRoot() }
                        }
                    }
                    if (locked) LockScreen(c.lock, prefs.biometricEnabled, onForgot = { SecureErase.run(this@MainActivity) })
                }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); runCatching { container.lock.onForeground() } }

    override fun onStop() { super.onStop(); runCatching { container.lock.onBackground() } }

    private fun applySecureFlag(on: Boolean) = runCatching {
        if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
