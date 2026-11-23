package app.myhealth.ui.startup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.myhealth.R
import app.myhealth.container
import app.myhealth.core.UpdateState
import app.myhealth.core.Updater
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.theme.MyHealthTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Update states shown while the app is in use (item 4: never blocks start-up). */
sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val a: Updater.Available) : UpdateUi
    data class Downloading(val version: String, val progress: Float) : UpdateUi
    data class NeedsPermission(val a: Updater.Available) : UpdateUi
    data object Installing : UpdateUi
    data object Failed : UpdateUi
}

/**
 * Process-wide update controller. The app opens straight away; a quiet background check runs ~5 s after start and
 * again when the app returns to the foreground after [RECHECK_MS]. Only when a newer build exists is the user asked.
 */
object UpdateController {
    val state = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    private var lastCheck = 0L
    private var dismissedCode = -1L
    const val RECHECK_MS = 6 * 60 * 60 * 1000L

    fun enabled(context: Context): Boolean =
        (context.applicationContext as app.myhealth.MyHealthApplication).updatesEnabled && Updater.enabledFor(context) && context.container.preferences.autoUpdate()

    /** Background check; [manual] also reports "up to date" / failures. */
    suspend fun check(context: Context, manual: Boolean = false) {
        if (!manual && (!enabled(context) || System.currentTimeMillis() - lastCheck < RECHECK_MS)) return
        if (!(context.applicationContext as app.myhealth.MyHealthApplication).updatesEnabled) return
        if (state.value is UpdateUi.Downloading || state.value == UpdateUi.Installing) return
        lastCheck = System.currentTimeMillis()
        if (manual) state.value = UpdateUi.Checking
        val found = withContext(Dispatchers.IO) { runCatching { Updater.check(context) } }
        state.value = when {
            found.isFailure -> if (manual) UpdateUi.Failed else UpdateUi.Idle
            found.getOrNull() == null -> if (manual) UpdateUi.UpToDate else UpdateUi.Idle
            found.getOrNull()!!.versionCode == dismissedCode && !manual -> UpdateUi.Idle
            else -> UpdateUi.Available(found.getOrNull()!!)
        }
    }

    fun later() { (state.value as? UpdateUi.Available)?.let { dismissedCode = it.a.versionCode }; state.value = UpdateUi.Idle }

    suspend fun install(context: Context, a: Updater.Available) {
        if (!Updater.canInstall(context)) { state.value = UpdateUi.NeedsPermission(a); return }
        state.value = UpdateUi.Downloading(a.versionName, 0f)
        val apk = File(File(context.cacheDir, "update").apply { mkdirs() }, "update.apk")
        val ok = withContext(Dispatchers.IO) {
            runCatching { Updater.download(a, apk) { p -> state.value = UpdateUi.Downloading(a.versionName, p) } && Updater.verify(context, apk) }.getOrDefault(false)
        }
        if (!ok) { apk.delete(); state.value = UpdateUi.Failed; return }
        state.value = UpdateUi.Installing
        if (withContext(Dispatchers.IO) { runCatching { Updater.install(context, apk) }.isFailure }) state.value = UpdateUi.Failed
    }

    fun openInstallSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Runs the background check and shows the update prompt/progress over the app when there is something to say. */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ui by UpdateController.state.collectAsStateWithLifecycle()
    val failedInstall by UpdateState.failedFlow.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(5_000)
            UpdateController.check(context)
            // back from "install unknown apps" settings: continue the install the user started
            (UpdateController.state.value as? UpdateUi.NeedsPermission)?.let { if (Updater.canInstall(context)) UpdateController.install(context, it.a) }
        }
    }
    LaunchedEffect(failedInstall, ui) { if (failedInstall && ui == UpdateUi.Installing) UpdateController.state.value = UpdateUi.Failed }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    when (val u = ui) {
        is UpdateUi.Available -> MyHealthDialog(stringResource(R.string.upd_available_title), stringResource(R.string.upd_available_body, u.a.versionName, u.a.versionCode.toInt()),
            stringResource(R.string.upd_now), { scope.launch { UpdateController.install(context, u.a) } }, stringResource(R.string.upd_later), { UpdateController.later() }, icon = MyHealthIcons.Sync)
        is UpdateUi.NeedsPermission -> MyHealthDialog(stringResource(R.string.upd_allow_title), stringResource(R.string.upd_allow_body), stringResource(R.string.upd_allow),
            { UpdateController.openInstallSettings(context) }, stringResource(R.string.upd_later), { UpdateController.later() }, icon = MyHealthIcons.Lock)
        is UpdateUi.Downloading -> MyHealthDialog(stringResource(R.string.upd_found, u.version), "", stringResource(R.string.action_close), {}, "", {}, icon = MyHealthIcons.Sync) {
            LinearProgressIndicator({ u.progress }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("updProgress"), color = MyHealthTheme.colors.accent, trackColor = MyHealthTheme.colors.accentSoft)
        }
        UpdateUi.Installing -> MyHealthDialog(stringResource(R.string.upd_installing), "", stringResource(R.string.action_close), { UpdateController.state.value = UpdateUi.Idle },
            "", {}, icon = MyHealthIcons.Sync)
        UpdateUi.Failed -> MyHealthDialog(stringResource(R.string.upd_failed), "", stringResource(R.string.action_ok), { UpdateController.state.value = UpdateUi.Idle }, "", {})
        UpdateUi.UpToDate -> MyHealthDialog(stringResource(R.string.upd_up_to_date), "", stringResource(R.string.action_ok), { UpdateController.state.value = UpdateUi.Idle }, "", {})
        UpdateUi.Checking -> Column(Modifier.testTag("updChecking")) { Text("") }
        UpdateUi.Idle -> Unit
    }
}
