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
import androidx.compose.runtime.setValue
import androidx.compose.animation.togetherWith
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
            // With a PIN set, the recents switcher shows a blank card even if screenshots are allowed.
            LaunchedEffect(prefs.lockEnabled) { if (android.os.Build.VERSION.SDK_INT >= 33) runCatching { setRecentsScreenshotEnabled(!prefs.lockEnabled) } }
            // Tapjacking: while the PIN pad is up, ignore touches that pass through another app's overlay.
            LaunchedEffect(locked) { window.decorView.filterTouchesWhenObscured = locked }
            val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            var entered by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(c.splashDone || !(application as MyHealthApplication).showSplash) }
            MyHealthTheme(prefs.theme, prefs.colorTheme) {
                app.myhealth.core.designsystem.components.MyHealthAmbientBackground {
                // While locked the app's screens are not composed at all: nothing is drawn, nothing reaches
                // accessibility services, and no dialog or popup window (which would float above an overlay) can
                // stay open. Navigation and unsaved input come back on unlock from the saveable state holder.
                androidx.compose.animation.AnimatedContent(entered, transitionSpec = {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500)) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)) + androidx.compose.animation.scaleOut(targetScale = 1.06f)
                }, label = ENTRY) { inApp ->
                if (!inApp) app.myhealth.ui.startup.SplashScreen { c.splashDone = true; entered = true }
                else Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)) {
                        StorageGate(storage, onRetry = c::retry, onErase = { SecureErase.run(this@MainActivity) }) { stack ->
                            val session = remember(stack) { Session(c, stack) }
                            if (!locked) saved.SaveableStateProvider(APP_STATE) {
                                CompositionLocalProvider(LocalSession provides session) { AppRoot() }
                            }
                        }
                    }
                    if (locked) LockScreen(c.lock, prefs.biometricEnabled, onForgot = { SecureErase.run(this@MainActivity) })
                }
                }
                }
            }
        }
    }

    private companion object { const val APP_STATE = "app"; const val ENTRY = "entry" // animation inspector id, not UI copy
    }

    override fun onStart() { super.onStart(); runCatching { container.lock.onForeground() } }

    override fun onStop() { super.onStop(); runCatching { container.lock.onBackground() } }

    private fun applySecureFlag(on: Boolean) = runCatching {
        if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
