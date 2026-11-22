package app.myhealth.ui.startup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.myhealth.container
import app.myhealth.core.UpdateState
import app.myhealth.core.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** What the splash shows about updates. */
sealed interface UpdateUi {
    data object Checking : UpdateUi
    data object Done : UpdateUi
    data class Downloading(val version: String, val progress: Float) : UpdateUi
    data class NeedsPermission(val version: String) : UpdateUi
    data object Installing : UpdateUi
    data object Failed : UpdateUi
}

/**
 * Item 6: on every cold start (release build, setting on) the splash first asks GitHub for a newer release. If one
 * exists it is downloaded, checked and installed before the user enters; any failure falls back to "continue".
 */
@Composable
fun rememberUpdateFlow(): Pair<UpdateUi, () -> Unit> {
    val context = LocalContext.current
    val c = context.container
    val enabled = remember {
        (context.applicationContext as app.myhealth.MyHealthApplication).updatesEnabled && Updater.enabledFor(context) && c.preferences.autoUpdate() && !c.updateChecked
    }
    var ui by remember { mutableStateOf<UpdateUi>(if (enabled) UpdateUi.Checking else UpdateUi.Done) }
    var found by remember { mutableStateOf<Updater.Available?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        c.updateChecked = true
        found = withTimeoutOrNull(10_000) { withContext(Dispatchers.IO) { runCatching { Updater.check(context) }.getOrNull() } }
        if (found == null) ui = UpdateUi.Done else attempt++
    }
    // download → verify → install; re-run when the user returns from granting "install unknown apps"
    LaunchedEffect(attempt) {
        val a = found ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (ui == UpdateUi.Installing || ui == UpdateUi.Failed) return@repeatOnLifecycle
            if (!Updater.canInstall(context)) { ui = UpdateUi.NeedsPermission(a.versionName); return@repeatOnLifecycle }
            ui = UpdateUi.Downloading(a.versionName, 0f)
            val apk = File(File(context.cacheDir, "update").apply { mkdirs() }, "update.apk")
            val ok = withContext(Dispatchers.IO) {
                runCatching { Updater.download(a, apk) { p -> ui = UpdateUi.Downloading(a.versionName, p) } && Updater.verify(context, apk) }.getOrDefault(false)
            }
            if (!ok) { apk.delete(); ui = UpdateUi.Failed; return@repeatOnLifecycle }
            ui = UpdateUi.Installing
            val started = withContext(Dispatchers.IO) { runCatching { Updater.install(context, apk) }.isSuccess }
            if (!started) ui = UpdateUi.Failed
        }
    }
    val failed by UpdateState.failedFlow.collectAsStateWithLifecycle()
    if (failed && ui == UpdateUi.Installing) ui = UpdateUi.Failed
    val openSettings = {
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Unit
    }
    return ui to openSettings
}
