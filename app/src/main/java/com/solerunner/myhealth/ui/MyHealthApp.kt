package com.solerunner.myhealth.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.solerunner.myhealth.R
import com.solerunner.myhealth.data.prefs.ThemeMode
import com.solerunner.myhealth.design.components.LoadingState
import com.solerunner.myhealth.design.theme.MyHealthTheme
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.ui.auth.UnlockScreen
import com.solerunner.myhealth.ui.error.ErrorScreen
import com.solerunner.myhealth.ui.onboarding.OnboardingScreen

/**
 * The process-level UI: theme, privacy flag, database readiness, onboarding and the app lock gate.
 *
 * Order matters. The lock is checked on every `ON_START`, so returning from the camera or the file
 * picker re-locks when the configured delay has elapsed, but does not interrupt an import in progress.
 */
@Composable
fun MyHealthApp(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MyHealthTheme(darkTheme = darkTheme) {
        KeepScreenPrivate(settings.screenshotBlock)

        val ready by container.ready.collectAsStateWithLifecycle()
        val failure by container.failure.collectAsStateWithLifecycle()
        var profile by remember { mutableStateOf<Profile?>(null) }
        var profileLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(container, ready) {
            if (!ready) return@LaunchedEffect
            container.profiles.observe().collect {
                profile = it
                profileLoaded = true
            }
        }

        var locked by remember { mutableStateOf(true) }
        LockLifecycleObserver(
            hasPin = { container.lock.hasPin() },
            onStarted = { locked = it },
            onStopped = { container.lock.markBackgrounded() },
            shouldLock = {
                container.lock.shouldLock(System.currentTimeMillis(), settings.autoLock, settings.appLockEnabled)
            },
        )

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                failure != null -> ErrorScreen(
                    message = stringResource(R.string.error_db),
                    detail = failure,
                    onRetry = { container.start() },
                )
                !ready || !profileLoaded -> LoadingState(
                    message = stringResource(R.string.common_loading),
                    modifier = Modifier.fillMaxSize(),
                )
                profile == null -> OnboardingScreen(container)
                locked -> UnlockScreen(
                    container = container,
                    onUnlocked = {
                        container.lock.unlockedNow()
                        locked = false
                    },
                )
                else -> MainScaffold(container = container, profile = profile!!)
            }
        }
    }
}

/** Applies FLAG_SECURE while the user wants their record hidden from the app switcher. */
@Composable
private fun KeepScreenPrivate(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            if (enabled) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        onDispose { }
    }
}

@Composable
private fun LockLifecycleObserver(
    hasPin: () -> Boolean,
    shouldLock: () -> Boolean,
    onStarted: (Boolean) -> Unit,
    onStopped: () -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, hasPin, shouldLock) {
        val observer = LifecycleEventObserver { _, event ->
            // A lifecycle callback must never throw: a keystore hiccup here used to kill the process.
            when (event) {
                Lifecycle.Event.ON_STOP -> runCatching { onStopped() }
                Lifecycle.Event.ON_START -> runCatching {
                    if (hasPin()) onStarted(shouldLock())
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
