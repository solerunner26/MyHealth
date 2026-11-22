package com.solerunner.myhealth.ui.settings

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solerunner.myhealth.BuildConfig
import com.solerunner.myhealth.R
import com.solerunner.myhealth.data.prefs.AppLanguage
import com.solerunner.myhealth.data.prefs.AutoLockDelay
import com.solerunner.myhealth.data.prefs.ThemeMode
import com.solerunner.myhealth.design.components.ChoiceDialog
import com.solerunner.myhealth.design.components.ConfirmDialog
import com.solerunner.myhealth.design.components.HealthCard
import com.solerunner.myhealth.design.components.LabeledValue
import com.solerunner.myhealth.design.components.SectionHeader
import com.solerunner.myhealth.design.components.SegmentedControl
import com.solerunner.myhealth.design.components.ThinDivider
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.ui.containerViewModel

/**
 * Settings: appearance, language, security, data and about. Every toggle writes through the encrypted
 * [com.solerunner.myhealth.core.security.SecureStore], and destructive actions are always confirmed.
 */
@Composable
fun SettingsScreen(container: AppContainer) {
    val viewModel = containerViewModel(container) { SettingsViewModel(it) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val extra by viewModel.extra.collectAsStateWithLifecycle()
    val audit by viewModel.audit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    var confirmWipe by remember { mutableStateOf(false) }
    var showAutoLock by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    val spacing = MyHealthTokens.spacing
    val snackbar = remember { SnackbarHostState() }
    val wipedText = stringResource(R.string.settings_wiped)

    LaunchedEffect(extra.shareIntent) {
        extra.shareIntent?.let { intent ->
            context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.common_share)))
            viewModel.clearShare()
        }
    }
    LaunchedEffect(extra.wiped) {
        if (extra.wiped) {
            snackbar.showSnackbar(wipedText)
            viewModel.consumeMessage()
        }
    }

    val biometricAvailable = remember {
        runCatching {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPadding, vertical = spacing.md),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(spacing.md))

            HealthCard {
                Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(spacing.sm))
                SegmentedControl(
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    onSelect = viewModel::setTheme,
                    label = {
                        when (it) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        }
                    },
                )
                Spacer(Modifier.height(spacing.lg))
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(spacing.sm))
                SegmentedControl(
                    options = AppLanguage.entries,
                    selected = settings.language,
                    onSelect = { language ->
                        viewModel.setLanguage(language)
                        activity?.recreate()
                    },
                    label = {
                        when (it) {
                            AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
                            AppLanguage.HINDI -> stringResource(R.string.settings_language_hindi)
                            AppLanguage.GUJARATI -> stringResource(R.string.settings_language_gujarati)
                        }
                    },
                )
                Text(
                    stringResource(R.string.settings_locale_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(spacing.md))
            HealthCard {
                Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleMedium)
                SwitchRow(
                    title = stringResource(R.string.settings_app_lock),
                    subtitle = stringResource(R.string.settings_app_lock_desc),
                    checked = settings.appLockEnabled && container.lock.hasPin(),
                    onChange = viewModel::setAppLock,
                )
                if (biometricAvailable && settings.appLockEnabled) {
                    SwitchRow(
                        title = stringResource(R.string.settings_biometric),
                        checked = settings.biometricUnlock,
                        onChange = viewModel::setBiometric,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.settings_auto_lock), modifier = Modifier.weight(1f))
                    TextButton(onClick = { showAutoLock = true }) { Text(autoLockLabel(settings.autoLock)) }
                }
                SwitchRow(
                    title = stringResource(R.string.settings_screenshot_block),
                    subtitle = stringResource(R.string.settings_screenshot_block_desc),
                    checked = settings.screenshotBlock,
                    onChange = viewModel::setScreenshotBlock,
                )
                TextButton(onClick = { showPin = true }) { Text(stringResource(R.string.settings_change_pin)) }
            }

            Spacer(Modifier.height(spacing.md))
            HealthCard {
                Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium)
                LabeledValue(
                    label = stringResource(R.string.settings_storage, ""),
                    value = extra.storageBytes?.let { "%.1f MB".format(it / 1_048_576.0) } ?: "—",
                )
                TextButton(onClick = viewModel::exportAll) { Text(stringResource(R.string.settings_export_all)) }
                TextButton(onClick = { confirmWipe = true }) { Text(stringResource(R.string.settings_wipe)) }
            }

            Spacer(Modifier.height(spacing.md))
            HealthCard {
                Text(stringResource(R.string.settings_section_about), style = MaterialTheme.typography.titleMedium)
                LabeledValue(stringResource(R.string.settings_version, ""), BuildConfig.VERSION_NAME)
                Text(
                    stringResource(R.string.settings_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.sm))
                Text(stringResource(R.string.settings_audit), style = MaterialTheme.typography.labelLarge)
                if (audit.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_audit_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    audit.take(8).forEach { event ->
                        Text(
                            "${event.at.toString().take(16)} · ${event.action}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = stringResource(R.string.settings_wipe_title),
            body = stringResource(R.string.settings_wipe_body),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = viewModel::wipe,
            onDismiss = { confirmWipe = false },
        )
    }

    if (showAutoLock) {
        ChoiceDialog(
            title = stringResource(R.string.settings_auto_lock),
            options = AutoLockDelay.entries,
            selected = settings.autoLock,
            onSelect = viewModel::setAutoLock,
            onDismiss = { showAutoLock = false },
            label = {
                when (it) {
                    AutoLockDelay.IMMEDIATELY -> stringResource(R.string.settings_auto_lock_immediately)
                    AutoLockDelay.ONE_MINUTE -> stringResource(R.string.settings_auto_lock_1min)
                    AutoLockDelay.FIVE_MINUTES -> stringResource(R.string.settings_auto_lock_5min)
                    AutoLockDelay.FIFTEEN_MINUTES -> stringResource(R.string.settings_auto_lock_15min)
                }
            },
        )
    }

    if (showPin) {
        ChangePinDialog(
            onDismiss = { showPin = false },
            onSubmit = { pin ->
                showPin = false
                viewModel.changePin(pin)
            },
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun autoLockLabel(delay: AutoLockDelay): String = when (delay) {
    AutoLockDelay.IMMEDIATELY -> stringResource(R.string.settings_auto_lock_immediately)
    AutoLockDelay.ONE_MINUTE -> stringResource(R.string.settings_auto_lock_1min)
    AutoLockDelay.FIVE_MINUTES -> stringResource(R.string.settings_auto_lock_5min)
    AutoLockDelay.FIFTEEN_MINUTES -> stringResource(R.string.settings_auto_lock_15min)
}
