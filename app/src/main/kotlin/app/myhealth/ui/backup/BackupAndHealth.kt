package app.myhealth.ui.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.myhealth.BuildInfo
import app.myhealth.R
import app.myhealth.core.Safe
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthOfflineState
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthProgress
import app.myhealth.core.designsystem.components.MyHealthSecondaryButton
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.components.MyHealthTextField
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.data.backup.BackupException
import app.myhealth.data.backup.BackupFailure
import app.myhealth.data.backup.BackupInfo
import app.myhealth.domain.model.Relationship
import app.myhealth.health.HcData
import app.myhealth.health.HcStatus
import app.myhealth.health.HealthConnectSource
import app.myhealth.health.HealthSource
import app.myhealth.health.HealthSync
import app.myhealth.ui.LocalSession
import app.myhealth.ui.Session
import app.myhealth.ui.common.rememberVm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface BackupPhase {
    data object Idle : BackupPhase
    data object Working : BackupPhase
    data class Exported(val info: BackupInfo) : BackupPhase
    data class Restored(val info: BackupInfo) : BackupPhase
    data class Failed(val failure: BackupFailure, val restoring: Boolean) : BackupPhase
}

class BackupViewModel(private val session: Session, private val context: android.content.Context) : ViewModel() {
    private val _phase = MutableStateFlow<BackupPhase>(BackupPhase.Idle)
    val phase: StateFlow<BackupPhase> = _phase.asStateFlow()

    fun export(uri: Uri, password: CharArray) = run(false) {
        val out = context.contentResolver.openOutputStream(uri, "w") ?: throw BackupException(BackupFailure.Io)
        BackupPhase.Exported(session.stack.backups(context).export(out, password, session.container.clock.millis(), BuildInfo.versionName(context)))
    }

    fun restore(uri: Uri, password: CharArray) = run(true) {
        val input = context.contentResolver.openInputStream(uri) ?: throw BackupException(BackupFailure.Io)
        val info = input.use { session.stack.backups(context).restore(it, password) }
        session.container.preferences.setActiveMember(null)
        runCatching { session.container.reminders.rescheduleAll(session.stack) }
        BackupPhase.Restored(info)
    }

    private fun run(restoring: Boolean, block: suspend () -> BackupPhase) {
        if (_phase.value == BackupPhase.Working) return
        _phase.value = BackupPhase.Working
        viewModelScope.launch(Safe.handler) {
            _phase.value = try { withContext(Dispatchers.IO) { block() } }
            catch (e: BackupException) { BackupPhase.Failed(e.failure, restoring) }
            catch (e: Exception) { BackupPhase.Failed(BackupFailure.Io, restoring) }
        }
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val session = LocalSession.current
    val context = LocalContext.current
    val vm = rememberVm { BackupViewModel(session, context.applicationContext) }
    val phase by vm.phase.collectAsStateWithLifecycle()
    var pw by rememberSaveable { mutableStateOf("") }
    var pw2 by rememberSaveable { mutableStateOf("") }
    var restorePw by rememberSaveable { mutableStateOf("") }
    var errors by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val sp = MyHealthTheme.spacing
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) vm.export(uri, pw.toCharArray()) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.restore(uri, restorePw.toCharArray()) }
    LaunchedEffect(phase) { if (phase is BackupPhase.Exported || phase is BackupPhase.Restored) { pw = ""; pw2 = ""; restorePw = "" } }
    Column(Modifier.fillMaxSize().background(MyHealthTheme.colors.background).testTag("backup")) {
        MyHealthTopBar(stringResource(R.string.backup_title), onBack = onBack, backLabel = stringResource(R.string.action_back))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
            MyHealthInfoBanner(stringResource(R.string.backup_intro), icon = MyHealthIcons.Lock)
            when (val p = phase) {
                BackupPhase.Working -> MyHealthProgress(stringResource(R.string.backup_working), null)
                is BackupPhase.Exported -> MyHealthInfoBanner(stringResource(R.string.backup_done, p.info.members, p.info.attachments), icon = MyHealthIcons.StatusNormal)
                is BackupPhase.Restored -> MyHealthInfoBanner(stringResource(R.string.restore_done, p.info.members, p.info.attachments), icon = MyHealthIcons.StatusNormal)
                is BackupPhase.Failed -> MyHealthInfoBanner(stringResource(if (!p.restoring) R.string.backup_failed else when (p.failure) {
                    BackupFailure.WrongPassword -> R.string.restore_wrong_password; BackupFailure.Corrupt, BackupFailure.Truncated -> R.string.restore_corrupt
                    BackupFailure.NotABackup -> R.string.restore_not_backup; BackupFailure.NewerVersion -> R.string.restore_newer; BackupFailure.Io -> R.string.restore_io
                }), icon = MyHealthIcons.StatusError)
                BackupPhase.Idle -> Unit
            }
            MyHealthSectionHeader(stringResource(R.string.backup_create))
            MyHealthTextField(pw, { pw = it; errors = null }, stringResource(R.string.backup_password), visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), supportingText = stringResource(R.string.backup_password_hint),
                errorText = if (errors == R.string.backup_err_short) stringResource(R.string.backup_err_short) else null)
            MyHealthTextField(pw2, { pw2 = it; errors = null }, stringResource(R.string.backup_password_confirm), visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), errorText = if (errors == R.string.backup_err_mismatch) stringResource(R.string.backup_err_mismatch) else null)
            MyHealthPrimaryButton(stringResource(R.string.backup_choose_location), {
                errors = when { pw.length < 8 -> R.string.backup_err_short; pw != pw2 -> R.string.backup_err_mismatch; else -> null }
                if (errors == null) runCatching { create.launch("MyHealth-${LocalDate.now()}.myhealth") }
            }, Modifier.fillMaxWidth().testTag("createBackup"), icon = MyHealthIcons.Backup, enabled = phase != BackupPhase.Working)
            MyHealthSectionHeader(stringResource(R.string.backup_restore))
            MyHealthTextField(restorePw, { restorePw = it }, stringResource(R.string.restore_password), visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            MyHealthSecondaryButton(stringResource(R.string.restore_choose), { confirmRestore = true }, Modifier.fillMaxWidth(), icon = MyHealthIcons.Restore,
                enabled = restorePw.isNotEmpty() && phase != BackupPhase.Working)
            Spacer(Modifier.height(sp.xl))
        }
    }
    if (confirmRestore) MyHealthDialog(stringResource(R.string.restore_warning_title), stringResource(R.string.restore_warning_body), stringResource(R.string.action_continue),
        { confirmRestore = false; runCatching { open.launch(arrayOf("*/*")) } }, stringResource(R.string.action_cancel), { confirmRestore = false }, icon = MyHealthIcons.Restore)
}

// ---------------- Health Connect
data class HcState(val status: HcStatus = HcStatus.Unsupported, val granted: Set<HcData> = emptySet(), val busy: Boolean = false, val lastDays: Int? = null,
                   val storedDays: Int = 0, val failed: Boolean = false, val denied: Boolean = false, val ownerName: String = "")

class HealthConnectViewModel(private val session: Session, private val source: HealthSource) : ViewModel() {
    private val _state = MutableStateFlow(HcState())
    val state: StateFlow<HcState> = _state.asStateFlow()
    private val sync = HealthSync(source, session.stack.activity, session.container.clock)

    private suspend fun owner() = session.stack.members.all().firstOrNull { it.relationship == Relationship.Self }

    fun refresh() = viewModelScope.launch(Safe.handler) {
        val o = owner()
        val st = source.status()
        val g = if (st == HcStatus.Available) runCatching { source.granted() }.getOrDefault(emptySet()) else emptySet()
        _state.value = _state.value.copy(status = st, granted = g, ownerName = o?.name.orEmpty(),
            storedDays = o?.let { runCatching { session.stack.activity.all(it.id).size }.getOrDefault(0) } ?: 0)
    }

    fun onPermissionsResult(granted: Set<String>) {
        _state.value = _state.value.copy(denied = granted.isEmpty())
        if (granted.isNotEmpty()) { session.container.preferences.setHealthConnect(true); syncNow() } else refresh()
    }

    fun syncNow() = viewModelScope.launch(Safe.handler) {
        val o = owner() ?: return@launch
        _state.value = _state.value.copy(busy = true, failed = false)
        val r = runCatching { withContext(Dispatchers.IO) { sync.sync(o.id) } }
        _state.value = _state.value.copy(busy = false, failed = r.isFailure, lastDays = r.getOrNull()?.daysWritten)
        refresh()
    }

    fun disconnect() = viewModelScope.launch(Safe.handler) {
        val o = owner() ?: return@launch
        runCatching { sync.disconnect(o.id) }
        session.container.preferences.setHealthConnect(false)
        _state.value = _state.value.copy(lastDays = null)
        refresh()
    }
}

@Composable
fun HealthConnectScreen(onBack: () -> Unit, sourceOverride: HealthSource? = null) {
    val session = LocalSession.current
    val context = LocalContext.current
    val vm = rememberVm { HealthConnectViewModel(session, sourceOverride ?: HealthConnectSource(context.applicationContext, session.container.clock.zone)) }
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val sp = MyHealthTheme.spacing
    val request = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { vm.onPermissionsResult(it) }
    val names = mapOf(HcData.Steps to R.string.hc_data_steps, HcData.HeartRate to R.string.hc_data_hr, HcData.RestingHeartRate to R.string.hc_data_rhr,
        HcData.Sleep to R.string.hc_data_sleep, HcData.Weight to R.string.hc_data_weight).mapValues { stringResource(it.value) }
    Column(Modifier.fillMaxSize().background(MyHealthTheme.colors.background).testTag("healthConnect")) {
        MyHealthTopBar(stringResource(R.string.hc_title), onBack = onBack, backLabel = stringResource(R.string.action_back))
        when (s.status) {
            HcStatus.NotInstalled, HcStatus.UpdateRequired, HcStatus.Unsupported -> {
                val (t, b) = when (s.status) { HcStatus.NotInstalled -> R.string.hc_not_installed_title to R.string.hc_not_installed_body
                    HcStatus.UpdateRequired -> R.string.hc_update_title to R.string.hc_update_body; else -> R.string.hc_unsupported_title to R.string.hc_unsupported_body }
                Column(Modifier.padding(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
                    MyHealthInfoBanner(stringResource(R.string.hc_intro), icon = MyHealthIcons.Activity)
                    MyHealthOfflineState(stringResource(t), stringResource(b), Modifier.height(260.dp()))
                    if (s.status != HcStatus.Unsupported) MyHealthSecondaryButton(stringResource(R.string.hc_open_store), {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }, Modifier.fillMaxWidth())
                }
            }
            HcStatus.Available -> Column(Modifier.verticalScroll(rememberScrollState()).padding(sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.md)) {
                MyHealthInfoBanner(stringResource(R.string.hc_intro), icon = MyHealthIcons.Activity)
                if (s.ownerName.isNotBlank()) MyHealthInfoBanner(stringResource(R.string.hc_only_self, s.ownerName), icon = MyHealthIcons.Profile)
                if (s.denied) MyHealthInfoBanner(stringResource(R.string.hc_denied), icon = MyHealthIcons.StatusWarning)
                if (s.failed) MyHealthInfoBanner(stringResource(R.string.hc_failed), icon = MyHealthIcons.StatusError)
                if (s.busy) MyHealthProgress(stringResource(R.string.state_loading), null)
                if (s.granted.isEmpty()) {
                    MyHealthPrimaryButton(stringResource(R.string.hc_connect), { runCatching { request.launch(HealthConnectSource.PERMISSIONS.values.toSet()) } }, Modifier.fillMaxWidth().testTag("hcConnect"), icon = MyHealthIcons.Sync)
                } else {
                    MyHealthCard {
                        Text(stringResource(R.string.hc_connected, s.granted.map { names.getValue(it) }.joinToString(", ")), style = MyHealthTheme.typography.body, color = MyHealthTheme.colors.textPrimary)
                        s.lastDays?.let { Text(stringResource(R.string.hc_last_sync, it), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary) }
                        Text(stringResource(R.string.hc_days_stored, s.storedDays), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
                    }
                    MyHealthPrimaryButton(stringResource(R.string.hc_sync), { vm.syncNow() }, Modifier.fillMaxWidth().testTag("hcSync"), icon = MyHealthIcons.Sync, enabled = !s.busy)
                    MyHealthTextButton(stringResource(R.string.hc_disconnect), { vm.disconnect() }, Modifier.fillMaxWidth(), icon = MyHealthIcons.Delete)
                }
            }
        }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
