package app.myhealth.ui.backup

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.BuildInfo
import app.myhealth.R
import app.myhealth.core.AutoBackup
import app.myhealth.core.AutoBackupFrequency
import app.myhealth.core.ShareCode
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthChipRow
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthInfoBanner
import app.myhealth.core.designsystem.components.MyHealthPrimaryButton
import app.myhealth.core.designsystem.components.MyHealthSecondaryButton
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthTextButton
import app.myhealth.core.designsystem.components.MyHealthTextField
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.data.backup.BackupException
import app.myhealth.data.backup.BackupFailure
import app.myhealth.ui.LocalSession
import app.myhealth.ui.common.Share
import app.myhealth.ui.common.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** Item 3: schedule, folder and password for automatic encrypted backups. */
@Composable
fun AutoBackupSection() {
    val session = LocalSession.current
    val context = LocalContext.current
    val prefs = session.container.preferences
    var folder by rememberSaveable { mutableStateOf(prefs.autoBackupFolder()) }
    var freq by rememberSaveable { mutableStateOf(prefs.autoBackupFrequency().takeIf { it != AutoBackupFrequency.Off } ?: AutoBackupFrequency.Weekly) }
    var enabled by rememberSaveable { mutableStateOf(prefs.autoBackupFrequency() != AutoBackupFrequency.Off) }
    var pw by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    val hasSecret = prefs.autoBackupSecret() != null
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            folder = uri.toString()
        }
    }
    MyHealthSectionHeader(stringResource(R.string.auto_title))
    MyHealthCard(Modifier.testTag("autoBackup")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
            Text(stringResource(R.string.auto_body), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
            MyHealthChipRow(AutoBackupFrequency.entries, if (enabled) freq else AutoBackupFrequency.Off, {
                stringResource(when (it) { AutoBackupFrequency.Off -> R.string.auto_off; AutoBackupFrequency.Daily -> R.string.auto_daily; AutoBackupFrequency.Weekly -> R.string.auto_weekly })
            }, { if (it == AutoBackupFrequency.Off) enabled = false else { enabled = true; freq = it } }, Modifier.testTag("autoFreq"))
            folder?.let { Text(stringResource(R.string.auto_folder, AutoBackup.folderLabel(context, Uri.parse(it))), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textPrimary) }
            MyHealthSecondaryButton(stringResource(R.string.auto_choose_folder), { runCatching { pick.launch(null) } }, Modifier.fillMaxWidth(), icon = MyHealthIcons.Backup)
            MyHealthTextField(pw, { pw = it }, stringResource(R.string.auto_password), visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), supportingText = stringResource(R.string.auto_password_hint))
            prefs.autoBackupLast()?.let { (at, ok) ->
                val day = formatDate(Instant.ofEpochMilli(at).atZone(session.container.clock.zone).toLocalDate())
                Text(stringResource(if (ok) R.string.auto_last_ok else R.string.auto_last_failed, day), style = MyHealthTheme.typography.caption,
                    color = if (ok) MyHealthTheme.colors.textSecondary else MyHealthTheme.colors.danger)
            }
            message?.let { MyHealthInfoBanner(stringResource(it)) }
            MyHealthPrimaryButton(stringResource(R.string.auto_save), {
                message = if (!enabled) {
                    prefs.setAutoBackup(null, null, AutoBackupFrequency.Off); AutoBackup.schedule(context, AutoBackupFrequency.Off); R.string.auto_saved
                } else if (folder == null || (pw.length < 8 && !hasSecret)) R.string.auto_need_setup
                else {
                    val sealed = if (pw.length >= 8) runCatching { AutoBackup.seal(pw.toCharArray()) }.getOrNull() else prefs.autoBackupSecret()
                    if (sealed == null) R.string.auto_unavailable
                    else { prefs.setAutoBackup(folder, sealed, freq); AutoBackup.schedule(context, freq); pw = ""; R.string.auto_saved }
                }
            }, Modifier.fillMaxWidth().testTag("autoSave"), icon = MyHealthIcons.Check)
            if (hasSecret && folder != null) MyHealthTextButton(stringResource(R.string.auto_now), { AutoBackup.runNow(context) }, icon = MyHealthIcons.Sync)
        }
    }
}

/** Item 4: share one member's record with another phone (file + code/QR), or receive one. No server. */
@Composable
fun ShareSection() {
    val session = LocalSession.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val members by session.members.collectAsStateWithLifecycle()
    val active by session.activeMember.collectAsStateWithLifecycle()
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf<String?>(null) }
    var typed by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<Int?>(null) }
    val chooser = stringResource(R.string.share_chooser)
    val memberId = chosen ?: active?.id

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val c = ShareCode.normalise(typed) ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            result = try {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { session.stack.backups(context).importMember(it, c.toCharArray()) } }
                runCatching { session.container.reminders.rescheduleAll(session.stack) }
                typed = ""; R.string.share_received
            } catch (e: BackupException) {
                when (e.failure) { BackupFailure.WrongPassword -> R.string.share_bad_code; BackupFailure.NotABackup -> R.string.share_not_member_file; else -> R.string.restore_corrupt }
            } catch (e: Exception) { R.string.share_failed }
            busy = false
        }
    }
    val scan = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { app.myhealth.core.Images.readLimited(it, 25 * 1024 * 1024) } ?: return@runCatching null
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    var s = 1; while (maxOf(o.outWidth, o.outHeight) / s > 2000) s *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })?.let { ShareCode.scan(it) }
                }.getOrNull()
            }
            if (found != null) { typed = found; result = null } else result = R.string.share_scan_failed
        }
    }

    MyHealthSectionHeader(stringResource(R.string.share_title))
    MyHealthCard(Modifier.testTag("shareSection")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
            Text(stringResource(R.string.share_body), style = MyHealthTheme.typography.bodySmall, color = MyHealthTheme.colors.textSecondary)
            val list = members.orEmpty()
            if (list.size > 1) MyHealthChipRow(list.map { it.id }, memberId, { id -> list.first { it.id == id }.name }, { chosen = it })
            MyHealthPrimaryButton(stringResource(R.string.share_create), {
                val m = list.firstOrNull { it.id == memberId } ?: return@MyHealthPrimaryButton
                busy = true
                scope.launch {
                    val newCode = ShareCode.generate()
                    val ok = runCatching {
                        val bytes = withContext(Dispatchers.IO) {
                            java.io.ByteArrayOutputStream().also { session.stack.backups(context).exportMember(it, newCode.toCharArray(), session.container.clock.millis(), BuildInfo.versionName(context), m.id) }.toByteArray()
                        }
                        Share.launch(context, withContext(Dispatchers.IO) { Share.write(context, "MyHealth-${m.name}.myhealth", bytes) }, "application/octet-stream", chooser)
                    }.isSuccess
                    if (ok) code = newCode else result = R.string.share_failed
                    busy = false
                }
            }, Modifier.fillMaxWidth().testTag("createShare"), icon = MyHealthIcons.Share, enabled = !busy && memberId != null)
        }
    }
    MyHealthSectionHeader(stringResource(R.string.share_receive_title))
    MyHealthCard(Modifier.testTag("receiveSection")) {
        Column(verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
            MyHealthTextField(typed, { typed = it.take(40) }, stringResource(R.string.share_code_field), Modifier.testTag("shareCodeField"))
            MyHealthTextButton(stringResource(R.string.share_scan), { runCatching { scan.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }, icon = MyHealthIcons.Camera)
            result?.let { MyHealthInfoBanner(stringResource(it), icon = if (it == R.string.share_received) MyHealthIcons.StatusNormal else MyHealthIcons.StatusWarning) }
            MyHealthSecondaryButton(stringResource(R.string.share_pick_file), { runCatching { openFile.launch(arrayOf("*/*")) } }, Modifier.fillMaxWidth().testTag("receiveShare"),
                icon = MyHealthIcons.Restore, enabled = !busy && ShareCode.normalise(typed) != null)
        }
    }
    code?.let { c ->
        val qr = remember(c) { ShareCode.qr(c).asImageBitmap() }
        MyHealthDialog(stringResource(R.string.share_code_title), stringResource(R.string.share_code_body), stringResource(R.string.action_close), { code = null },
            stringResource(R.string.action_close), { code = null }, icon = MyHealthIcons.Lock) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().testTag("shareCodeDialog")) {
                Box(Modifier.clip(MyHealthTheme.radii.mdShape).background(Color.White).padding(8.dp)) {
                    Image(qr, stringResource(R.string.share_qr_cd), Modifier.size(220.dp))
                }
                Text(c, style = MyHealthTheme.typography.title, color = MyHealthTheme.colors.textPrimary, modifier = Modifier.padding(top = 12.dp).testTag("shareCodeText"))
            }
        }
    }
}
