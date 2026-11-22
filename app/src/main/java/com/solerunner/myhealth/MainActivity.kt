package com.solerunner.myhealth

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.solerunner.myhealth.core.i18n.LocaleController
import com.solerunner.myhealth.data.prefs.AppLanguage
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.ui.MyHealthApp

/**
 * Single activity. Two things happen before `super.onCreate`:
 * 1. the base context is replaced with one configured for the user's language, so every resource and
 *    every `LocalConfiguration` resolves in Hindi, Gujarati or English;
 * 2. the splash screen is installed, which keeps the system splash visible until the first frame.
 *
 * `FragmentActivity` (not plain `ComponentActivity`) is required by [androidx.biometric.BiometricPrompt].
 */
class MainActivity : FragmentActivity() {

    private lateinit var container: AppContainer

    override fun attachBaseContext(newBase: Context) {
        val language = runCatching {
            (newBase.applicationContext as MyHealthApplication).container.settings.settings.value.language
        }.getOrDefault(AppLanguage.ENGLISH)
        super.attachBaseContext(LocaleController.localizedContext(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = (application as MyHealthApplication).container
        setContent {
            MyHealthApp(container)
        }
    }
}
