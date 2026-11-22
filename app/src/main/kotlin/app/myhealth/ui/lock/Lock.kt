package app.myhealth.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.myhealth.R
import app.myhealth.core.AppLock
import app.myhealth.core.PinResult
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthSecondaryButton
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.theme.MyHealthTheme

object Biometrics {
    fun available(context: android.content.Context) = runCatching { BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS }.getOrDefault(false)

    fun prompt(activity: FragmentActivity, title: String, negative: String, onSuccess: () -> Unit) {
        runCatching {
            val p = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            })
            p.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText(negative).setAllowedAuthenticators(BIOMETRIC_STRONG).build())
        }
    }
}

/** Numeric keypad; digits are announced to TalkBack, the entered PIN is only announced as a count. */
@Composable
fun PinPad(pin: String, maxLength: Int, onChange: (String) -> Unit, keySize: androidx.compose.ui.unit.Dp = 72.dp, onSubmit: (String) -> Unit) {
    val c = MyHealthTheme.colors
    val entered = stringResource(R.string.lock_entered, pin.length)
    Row(Modifier.semantics { contentDescription = entered; liveRegion = LiveRegionMode.Polite }.testTag("pinDots"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxOf(4, pin.length)) { i ->
            Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) c.accent else c.surface).border(MyHealthTheme.stroke.regular, c.outlineStrong, CircleShape))
        }
    }
    Spacer(Modifier.height(MyHealthTheme.spacing.lg))
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("del", "0", "ok"))
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.lg)) {
            row.forEach { k ->
                val cd = when (k) { "del" -> stringResource(R.string.lock_delete_digit); "ok" -> stringResource(R.string.action_ok); else -> stringResource(R.string.lock_digit, k) }
                Surface(
                    onClick = {
                        when (k) {
                            "del" -> onChange(pin.dropLast(1))
                            "ok" -> if (pin.length >= 4) onSubmit(pin)
                            else -> if (pin.length < maxLength) { val n = pin + k; onChange(n); if (n.length == maxLength) onSubmit(n) }
                        }
                    },
                    modifier = Modifier.size(keySize).semantics { contentDescription = cd }.testTag("key_$k"),
                    shape = CircleShape, color = if (k == "ok") c.accent else c.surfaceSunken,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when (k) {
                            "del" -> Icon(MyHealthIcons.Back, null, tint = c.textPrimary)
                            "ok" -> Icon(MyHealthIcons.Check, null, tint = c.onAccent)
                            else -> Text(k, style = MyHealthTheme.typography.headline, color = c.textPrimary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(MyHealthTheme.spacing.md))
    }
}

@Composable
fun LockScreen(lock: AppLock, biometricEnabled: Boolean, onForgot: () -> Unit) {
    val c = MyHealthTheme.colors
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var forgot by rememberSaveable { mutableStateOf(false) }
    val wrong = stringResource(R.string.lock_wrong)
    val res = androidx.compose.ui.platform.LocalResources.current
    val waitFmt: (Long) -> String = { s -> res.getQuantityString(R.plurals.lock_wait, s.toInt(), s.toInt()) }
    val bioTitle = stringResource(R.string.lock_biometric_title); val usePin = stringResource(R.string.lock_use_pin)
    val canBio = biometricEnabled && Biometrics.available(context)
    val showBio = { (context as? FragmentActivity)?.let { Biometrics.prompt(it, bioTitle, usePin) { lock.unlockedByBiometric() } } }
    LaunchedEffect(Unit) { if (canBio) showBio() }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.background, c.backgroundAlt))).pointerInput(Unit) { }.safeDrawingPadding().testTag("lockScreen"), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(MyHealthTheme.spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(MyHealthIcons.Lock, null, tint = c.accent, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(MyHealthTheme.spacing.md))
            Text(stringResource(R.string.lock_title), style = MyHealthTheme.typography.headline, color = c.textPrimary, textAlign = TextAlign.Center)
            Text(message ?: stringResource(R.string.lock_body), style = MyHealthTheme.typography.body, color = c.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }.testTag("lockMessage"))
            Spacer(Modifier.height(MyHealthTheme.spacing.xl))
            PinPad(pin, 8, { pin = it }) { entered ->
                when (val r = lock.tryPin(entered)) {
                    PinResult.Ok -> message = null
                    is PinResult.Wrong -> message = wrong
                    is PinResult.LockedOut -> message = waitFmt(r.secondsLeft)
                }
                pin = ""
            }
            if (canBio) MyHealthSecondaryButton(stringResource(R.string.lock_biometric), { showBio() }, icon = MyHealthIcons.Biometric)
            MyHealthTextButton(stringResource(R.string.lock_forgot), { forgot = true })
        }
    }
    if (forgot) MyHealthDialog(stringResource(R.string.lock_forgot), stringResource(R.string.lock_forgot_body), stringResource(R.string.storage_erase_confirm),
        { forgot = false; onForgot() }, stringResource(R.string.action_cancel), { forgot = false }, icon = MyHealthIcons.Erase)
}

/** Two-step PIN entry (choose, confirm). */
@Composable
fun PinSetupDialog(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    MyHealthDialog(
        title = stringResource(if (first == null) R.string.pin_set_title else R.string.pin_confirm_title),
        text = error?.let { stringResource(it) } ?: stringResource(R.string.pin_set_body),
        confirmLabel = stringResource(R.string.action_ok),
        onConfirm = {
            when {
                pin.length < 4 -> error = R.string.pin_too_short
                first == null -> { first = pin; pin = ""; error = null }
                first == pin -> onDone(pin)
                else -> { first = null; pin = ""; error = R.string.pin_mismatch }
            }
        },
        dismissLabel = stringResource(R.string.action_cancel), onDismiss = onCancel, icon = MyHealthIcons.Lock,
    ) {
        // Scrollable so the keypad stays reachable on small screens and with large font sizes.
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.verticalScroll(rememberScrollState()).testTag("pinSetup")) {
            PinPad(pin, 8, { pin = it }, keySize = 56.dp, onSubmit = { entered ->
                when {
                    first == null -> { first = entered; pin = ""; error = null }
                    first == entered -> onDone(entered)
                    else -> { first = null; pin = ""; error = R.string.pin_mismatch }
                }
            })
        }
    }
}
