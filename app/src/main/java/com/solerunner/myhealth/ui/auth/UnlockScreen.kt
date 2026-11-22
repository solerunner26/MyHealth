package com.solerunner.myhealth.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.solerunner.myhealth.R
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.design.theme.MyHealthTokens

/**
 * The lock screen. Biometric unlock is offered only when the user enabled it *and* the device reports a
 * usable sensor; otherwise the PIN is the only path — there is no "forgot PIN" that bypasses the gate,
 * because the data key lives in hardware and cannot be re-derived from an email address.
 */
@Composable
fun UnlockScreen(container: AppContainer, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAllowed = remember {
        container.settings.settings.value.biometricUnlock &&
            runCatching {
                BiometricManager.from(context)
                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
            }.getOrDefault(false)
    }

    val prompt = remember(activity, biometricAllowed) {
        if (activity == null || !biometricAllowed) {
            null
        } else {
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        container.lock.unlockedNow()
                        onUnlocked()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        error = errString.toString()
                    }
                },
            )
        }
    }

    fun askBiometric() {
        prompt?.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.auth_biometric_prompt))
                .setNegativeButtonText(context.getString(R.string.auth_skip_pin))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }

    LaunchedEffect(prompt) {
        if (prompt != null) askBiometric()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MyHealthTokens.spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MyHealthTokens.spacing.xl))
        PinPad(
            pin = pin,
            onPinChange = {
                pin = it
                error = null
            },
            title = stringResource(R.string.auth_unlock_title),
            subtitle = stringResource(R.string.auth_unlock_subtitle),
            error = error,
            actionLabel = stringResource(R.string.common_continue),
            onAction = {
                if (container.lock.verifyPin(pin)) {
                    container.lock.unlockedNow()
                    onUnlocked()
                } else {
                    error = context.getString(R.string.auth_pin_wrong)
                    pin = ""
                }
            },
            onBiometric = if (prompt != null) ({ askBiometric() }) else null,
        )
        Spacer(Modifier.height(MyHealthTokens.spacing.xl))
        Text(
            stringResource(R.string.auth_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
