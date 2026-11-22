package com.solerunner.myhealth.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.biometric.BiometricManager
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import com.solerunner.myhealth.BuildConfig
import com.solerunner.myhealth.R
import com.solerunner.myhealth.data.prefs.AppLanguage
import com.solerunner.myhealth.data.prefs.AutoLockDelay
import com.solerunner.myhealth.data.prefs.ThemeMode
import com.solerunner.myhealth.design.rare.RareCard
import com.solerunner.myhealth.design.rare.RareChoiceDialog
import com.solerunner.myhealth.design.rare.RareConfirmDialog
import com.solerunner.myhealth.design.rare.RareDivider
import com.solerunner.myhealth.design.rare.RareKeyValue
import com.solerunner.myhealth.design.rare.RarePageHeader
import com.solerunner.myhealth.design.rare.RareScrollProgress
import com.solerunner.myhealth.design.rare.RareSectionLabel
import com.solerunner.myhealth.design.rare.RareSegmented
import com.solerunner.myhealth.design.rare.RareSnackbarHost
import com.solerunner.myhealth.design.rare.RareSwitch
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.ui.containerViewModel
import kotlinx.coroutines.launch

/**
 * Settings, grouped into hairline cards: appearance, language, security, data and about. Every toggle
 * writes through the encrypted preference store; destructive actions always pass a confirmation sheet.
 */
private enum class VerifyPurpose { CHANGE_PIN, DISABLE_LOCK }

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
    var backupRequest by remember { mutableStateOf<BackupRequest?>(null) }
    var restoreCandidate by remember { mutableStateOf<Uri?>(null) }
    var verifyPurpose by remember { mutableStateOf<VerifyPurpose?>(null) }
    val spacing = MyHealthTokens.spacing
    val shareLabel = stringResource(R.string.common_share)
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val wipedText = stringResource(R.string.settings_wiped)
    val backupDoneText = stringResource(R.string.settings_backup_done)
    val backupRestoredText = stringResource(R.string.settings_backup_restored)
    val backupFailedText = stringResource(R.string.settings_backup_failed)
    val scroll = rememberScrollState()

    LaunchedEffect(extra.shareIntent) {
        extra.shareIntent?.let { intent ->
            context.startActivity(Intent.createChooser(intent, shareLabel))
            viewModel.clearShare()
        }
    }
    LaunchedEffect(extra.wiped) {
        if (extra.wiped) {
            scope.launch { snackbar.showSnackbar(wipedText) }
            viewModel.consumeMessage()
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) backupRequest = BackupRequest(uri, exporting = true) }
    val openBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreCandidate = uri
    }

    val healthConnectPermissions = remember { container.healthConnect.readPermissions }
    val healthConnectLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (granted.containsAll(healthConnectPermissions)) {
            viewModel.setHealthConnectEnabled(true)
            viewModel.syncHealthConnect()
        }
    }
    val healthConnectAvailable = remember { viewModel.healthConnectAvailable() }

    val biometricAvailable = remember {
        runCatching {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            RareScrollProgress(scroll)
            RarePageHeader(
                title = stringResource(R.string.settings_title),
                modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = spacing.md),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = spacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                RareCard {
                    RareSectionLabel(text = stringResource(R.string.settings_appearance))
                    com.solerunner.myhealth.design.rare.RareChoiceTiles(
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
                    Spacer(Modifier.height(spacing.md))
                    RareSectionLabel(text = stringResource(R.string.settings_language))
                    RareSegmented(
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
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_locale_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                RareCard {
                    RareSectionLabel(text = stringResource(R.string.settings_security))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_app_lock),
                        subtitle = stringResource(R.string.settings_app_lock_desc),
                        checked = settings.appLockEnabled && container.lock.hasPin(),
                        onChange = { enabled ->
                            when {
                                enabled && !container.lock.hasPin() -> showPin = true
                                !enabled && container.lock.hasPin() -> verifyPurpose = VerifyPurpose.DISABLE_LOCK
                                else -> viewModel.setAppLock(enabled)
                            }
                        },
                    )
                    if (biometricAvailable && settings.appLockEnabled) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_biometric),
                            checked = settings.biometricUnlock,
                            onChange = viewModel::setBiometric,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(stringResource(R.string.settings_auto_lock), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showAutoLock = true }) { Text(autoLockLabel(settings.autoLock)) }
                    }
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_screenshot_block),
                        subtitle = stringResource(R.string.settings_screenshot_block_desc),
                        checked = settings.screenshotBlock,
                        onChange = viewModel::setScreenshotBlock,
                    )
                    RareDivider()
                    TextButton(onClick = {
                        if (container.lock.hasPin()) verifyPurpose = VerifyPurpose.CHANGE_PIN else showPin = true
                    }) { Text(stringResource(R.string.settings_change_pin)) }
                }

                RareCard {
                    RareSectionLabel(text = stringResource(R.string.settings_health_connect))
                    Text(
                        stringResource(R.string.settings_health_connect_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!healthConnectAvailable) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_health_connect_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        com.solerunner.myhealth.design.rare.RareButton(
                            text = stringResource(
                                if (settings.healthConnectEnabled) R.string.settings_health_connect_sync
                                else R.string.settings_health_connect_connect,
                            ),
                            onClick = {
                                if (settings.healthConnectEnabled) viewModel.syncHealthConnect()
                                else healthConnectLauncher.launch(healthConnectPermissions)
                            },
                        )
                        if (settings.lastHealthConnectSync > 0L) {
                            Spacer(Modifier.height(6.dp))
                            val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm") }
                            Text(
                                stringResource(
                                    R.string.settings_health_connect_last,
                                    Instant.ofEpochMilli(settings.lastHealthConnectSync)
                                        .atZone(ZoneId.systemDefault())
                                        .format(formatter),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                RareCard {
                    RareSectionLabel(text = stringResource(R.string.settings_data))
                    RareKeyValue(
                        label = stringResource(R.string.settings_documents),
                        value = extra.storageBytes?.let { "%.1f MB".format(it / 1_048_576.0) } ?: "—",
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.settings_backup_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        com.solerunner.myhealth.design.rare.RareButton(
                            text = stringResource(R.string.settings_backup_create),
                            onClick = { createBackupLauncher.launch("myhealth-backup.mhb") },
                            modifier = Modifier.weight(1f),
                        )
                        com.solerunner.myhealth.design.rare.RareButton(
                            text = stringResource(R.string.settings_backup_restore),
                            onClick = { openBackupLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                            style = com.solerunner.myhealth.design.rare.RareButtonStyle.SECONDARY,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        com.solerunner.myhealth.design.rare.RareButton(
                            text = stringResource(R.string.settings_export_all),
                            onClick = viewModel::exportAll,
                            style = com.solerunner.myhealth.design.rare.RareButtonStyle.SECONDARY,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    com.solerunner.myhealth.design.rare.RareHoldToDelete(
                        label = stringResource(R.string.settings_wipe),
                        onConfirm = { confirmWipe = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                RareCard {
                    RareSectionLabel(text = stringResource(R.string.settings_section_about))
                    RareKeyValue(label = stringResource(R.string.settings_version, ""), value = BuildConfig.VERSION_NAME)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.settings_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    com.solerunner.myhealth.design.rare.RareFaqItem(
                        question = stringResource(R.string.settings_faq_encryption_q),
                        answer = stringResource(R.string.settings_faq_encryption_a),
                    )
                    RareDivider()
                    com.solerunner.myhealth.design.rare.RareFaqItem(
                        question = stringResource(R.string.settings_faq_ocr_q),
                        answer = stringResource(R.string.settings_faq_ocr_a),
                    )
                    RareDivider()
                    com.solerunner.myhealth.design.rare.RareFaqItem(
                        question = stringResource(R.string.settings_faq_ranges_q),
                        answer = stringResource(R.string.settings_faq_ranges_a),
                    )
                    RareDivider()
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.settings_design_credit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rareui.com")))
                            }
                        }) { Text("rareui.com") }
                    }
                    Spacer(Modifier.height(6.dp))
                    RareSectionLabel(text = stringResource(R.string.settings_audit))
                    if (audit.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_audit_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        audit.take(6).forEach { event ->
                            Text(
                                "${event.at.toString().take(16)} · ${event.action}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (extra.hasCrashLog) {
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = viewModel::shareCrashLog) {
                            Text(stringResource(R.string.settings_crash_log))
                        }
                    }
                }
                Spacer(Modifier.height(spacing.xxxl))
            }
        }
        RareSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (confirmWipe) {
        RareConfirmDialog(
            title = stringResource(R.string.settings_wipe_title),
            body = stringResource(R.string.settings_wipe_body),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = viewModel::wipe,
            onDismiss = { confirmWipe = false },
        )
    }

    if (showAutoLock) {
        RareChoiceDialog(
            title = stringResource(R.string.settings_auto_lock),
            options = AutoLockDelay.entries,
            selected = settings.autoLock,
            onSelect = viewModel::setAutoLock,
            onDismiss = { showAutoLock = false },
            label = { autoLockLabel(it) },
        )
    }

    restoreCandidate?.let { uri ->
        RareConfirmDialog(
            title = stringResource(R.string.settings_backup_overwrite_title),
            body = stringResource(R.string.settings_backup_overwrite_body),
            confirmLabel = stringResource(R.string.settings_backup_restore),
            onConfirm = {
                restoreCandidate = null
                backupRequest = BackupRequest(uri, exporting = false)
            },
            onDismiss = { restoreCandidate = null },
        )
    }

    backupRequest?.let { request ->
        BackupPassphraseSheet(
            exporting = request.exporting,
            onDismiss = { backupRequest = null },
            onSubmit = { passphrase ->
                backupRequest = null
                scope.launch {
                    val outcome = runCatching {
                        if (request.exporting) {
                            val bytes = container.backup.export(passphrase)
                            context.contentResolver.openOutputStream(request.uri)?.use { it.write(bytes) }
                                ?: error("cannot write backup")
                        } else {
                            val bytes = context.contentResolver.openInputStream(request.uri)?.use { it.readBytes() }
                                ?: error("cannot read backup")
                            container.backup.restore(bytes, passphrase)
                        }
                    }
                    passphrase.fill('\u0000')
                    snackbar.showSnackbar(
                        when {
                            !outcome.isSuccess -> backupFailedText
                            request.exporting -> backupDoneText
                            else -> backupRestoredText
                        },
                    )
                }
            },
        )
    }

    verifyPurpose?.let { purpose ->
        VerifyPinSheet(
            title = when (purpose) {
                VerifyPurpose.CHANGE_PIN -> stringResource(R.string.settings_change_pin)
                VerifyPurpose.DISABLE_LOCK -> stringResource(R.string.settings_app_lock)
            },
            onDismiss = { verifyPurpose = null },
            verify = viewModel::verifyCurrentPin,
            onVerified = {
                verifyPurpose = null
                when (purpose) {
                    VerifyPurpose.CHANGE_PIN -> showPin = true
                    VerifyPurpose.DISABLE_LOCK -> viewModel.setAppLock(false)
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
private fun SettingsSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
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
        Spacer(Modifier.width(12.dp))
        RareSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun autoLockLabel(delay: AutoLockDelay): String = when (delay) {
    AutoLockDelay.IMMEDIATELY -> stringResource(R.string.settings_auto_lock_immediately)
    AutoLockDelay.ONE_MINUTE -> stringResource(R.string.settings_auto_lock_1min)
    AutoLockDelay.FIVE_MINUTES -> stringResource(R.string.settings_auto_lock_5min)
    AutoLockDelay.FIFTEEN_MINUTES -> stringResource(R.string.settings_auto_lock_15min)
}


private data class BackupRequest(val uri: Uri, val exporting: Boolean)
