package com.solerunner.myhealth.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.solerunner.myhealth.R
import com.solerunner.myhealth.auth.GoogleIdentity
import com.solerunner.myhealth.auth.GoogleSignIn
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.AuthProvider
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.design.rare.RarePinEntry
import kotlinx.coroutines.launch
import java.time.Instant

enum class OnboardingStep { WELCOME, NAME, PIN, CONFIRM }

/**
 * First-run flow: welcome → name → PIN → confirm. Nothing is persisted until the PIN is confirmed, so
 * abandoning onboarding leaves no half-created profile behind.
 */
@Composable
fun OnboardingScreen(container: AppContainer) {
    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var identity by remember { mutableStateOf<GoogleIdentity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val google = remember { GoogleSignIn(context) }

    fun finish() {
        if (busy) return
        busy = true
        scope.launch {
            container.lock.setPin(pin)
            container.settings.setAppLockEnabled(true)
            container.profiles.save(
                Profile(
                    accountId = identity?.accountId,
                    displayName = name.trim(),
                    email = identity?.email,
                    photoUrl = identity?.photoUrl,
                    authProvider = if (identity != null) AuthProvider.GOOGLE else AuthProvider.NONE,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
            container.audit.log("profile.created", if (identity != null) "google" else "offline")
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MyHealthTokens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(
                googleConfigured = google.isConfigured,
                error = error,
                onStart = { step = OnboardingStep.NAME },
                onGoogle = {
                    scope.launch {
                        runCatching { google.signIn() }
                            .onSuccess { result ->
                                if (result == null) {
                                    error = context.getString(R.string.auth_google_failed)
                                } else {
                                    identity = result
                                    if (name.isBlank()) name = result.displayName.orEmpty()
                                    step = OnboardingStep.NAME
                                }
                            }
                            .onFailure { error = context.getString(R.string.auth_google_failed) }
                    }
                },
            )

            OnboardingStep.NAME -> NameStep(
                name = name,
                onNameChange = {
                    name = it
                    error = null
                },
                error = error,
                onContinue = {
                    if (name.trim().length < 2) {
                        error = context.getString(R.string.auth_name_required)
                    } else {
                        step = OnboardingStep.PIN
                    }
                },
                onBack = { step = OnboardingStep.WELCOME },
            )

            OnboardingStep.PIN -> RarePinEntry(
                pin = pin,
                onPinChange = { pin = it },
                title = stringResource(R.string.auth_setup_title),
                subtitle = stringResource(R.string.auth_setup_body),
                actionLabel = stringResource(R.string.common_continue),
                onAction = {
                    error = null
                    step = OnboardingStep.CONFIRM
                },
            )

            OnboardingStep.CONFIRM -> RarePinEntry(
                pin = confirm,
                onPinChange = { confirm = it },
                title = stringResource(R.string.auth_confirm_title),
                subtitle = stringResource(R.string.auth_confirm_body),
                error = error,
                actionLabel = stringResource(R.string.common_continue),
                onAction = {
                    if (confirm == pin) {
                        error = null
                        finish()
                    } else {
                        confirm = ""
                        error = context.getString(R.string.auth_pin_mismatch)
                    }
                },
            )
        }
        Spacer(Modifier.height(MyHealthTokens.spacing.xl))
        Text(
            stringResource(R.string.auth_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
