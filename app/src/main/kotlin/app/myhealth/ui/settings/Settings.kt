package app.myhealth.ui.settings

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.R
import app.myhealth.core.LocaleSupport
import app.myhealth.core.designsystem.components.MyHealthCard
import app.myhealth.core.designsystem.components.MyHealthChipRow
import app.myhealth.core.designsystem.components.MyHealthDialog
import app.myhealth.core.designsystem.components.MyHealthIcons
import app.myhealth.core.designsystem.components.MyHealthListRow
import app.myhealth.core.designsystem.components.MyHealthSectionHeader
import app.myhealth.core.designsystem.components.MyHealthSwitchRow
import app.myhealth.core.designsystem.components.MyHealthTopBar
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.core.designsystem.theme.ThemeMode
import app.myhealth.ui.LocalSession
import app.myhealth.ui.common.Share
import app.myhealth.ui.lock.Biometrics
import app.myhealth.ui.lock.PinSetupDialog

@Composable
fun SettingsScreen(onBack: () -> Unit, onErased: () -> Unit) {
    val session = LocalSession.current
    val c = session.container
    val prefs by c.preferences.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sp = MyHealthTheme.spacing
    var pinSetup by rememberSaveable { mutableStateOf(false) }
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    val chooser = stringResource(R.string.share_chooser)
    Column(Modifier.fillMaxSize().background(MyHealthTheme.colors.background).testTag("settings")) {
        MyHealthTopBar(stringResource(R.string.more_settings), onBack = onBack, backLabel = stringResource(R.string.action_back))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            MyHealthSectionHeader(stringResource(R.string.settings_appearance))
            Text(stringResource(R.string.settings_theme), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
            MyHealthChipRow(ThemeMode.entries, prefs.theme, { stringResource(when (it) { ThemeMode.System -> R.string.theme_system; ThemeMode.Light -> R.string.theme_light; ThemeMode.Dark -> R.string.theme_dark }) },
                { c.preferences.setTheme(it) }, Modifier.testTag("themeChips"))
            Text(stringResource(R.string.settings_language), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
            MyHealthChipRow(listOf("") + LocaleSupport.supported, prefs.language, { tag ->
                stringResource(when (tag) { "en" -> R.string.lang_en; "hi" -> R.string.lang_hi; "gu" -> R.string.lang_gu; else -> R.string.lang_system })
            }, { tag ->
                // State lives in ViewModels / saved state, so recreating the activity keeps everything the user typed.
                c.preferences.setLanguage(tag)
                LocaleSupport.apply(context, tag)
                if (Build.VERSION.SDK_INT < 33) (context as? Activity)?.recreate()
            }, Modifier.testTag("languageChips"))

            MyHealthSectionHeader(stringResource(R.string.settings_privacy))
            MyHealthSwitchRow(stringResource(R.string.settings_lock), prefs.lockEnabled && c.preferences.pinMaterial() != null, { on -> if (on) pinSetup = true else c.lock.disable() },
                subtitle = stringResource(R.string.settings_lock_sub), modifier = Modifier.testTag("lockSwitch"))
            if (prefs.lockEnabled) {
                val bio = Biometrics.available(context)
                MyHealthSwitchRow(stringResource(R.string.settings_biometric), prefs.biometricEnabled && bio, { c.preferences.setBiometric(it) }, enabled = bio,
                    subtitle = if (!bio) stringResource(R.string.settings_biometric_unavailable) else null)
                MyHealthListRow(stringResource(R.string.settings_change_pin), leadingIcon = MyHealthIcons.Lock, onClick = { pinSetup = true })
            }
            MyHealthSwitchRow(stringResource(R.string.settings_screenshots), prefs.blockScreenshots, { c.preferences.setBlockScreenshots(it) },
                subtitle = stringResource(R.string.settings_screenshots_sub), modifier = Modifier.testTag("secureSwitch"))

            MyHealthSectionHeader(stringResource(R.string.settings_data))
            Text(stringResource(R.string.settings_history), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
            MyHealthChipRow(listOf(1, 2, 3, 5, 10, 20), prefs.historyYears, { stringResource(R.string.settings_years, it) }, { c.preferences.setHistoryYears(it) })
            Text(stringResource(R.string.settings_history_sub, prefs.historyYears), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
            if (c.crashLog.hasReport()) MyHealthCard {
                MyHealthListRow(stringResource(R.string.settings_error_report), subtitle = stringResource(R.string.crash_body), leadingIcon = MyHealthIcons.Bug, onClick = {
                    runCatching { Share.launch(context, Share.write(context, "myhealth-error.txt", c.crashLog.file.readBytes()), "text/plain", chooser) }
                })
            }
            MyHealthCard {
                MyHealthListRow(stringResource(R.string.settings_erase), subtitle = stringResource(R.string.settings_erase_sub), leadingIcon = MyHealthIcons.Erase,
                    onClick = { confirmErase = true }, modifier = Modifier.testTag("eraseAll"))
            }
            Spacer(Modifier.height(sp.xl))
        }
    }
    if (pinSetup) PinSetupDialog(onDone = { pin -> c.lock.setPin(pin); pinSetup = false }, onCancel = { pinSetup = false })
    if (confirmErase) MyHealthDialog(stringResource(R.string.storage_erase_title), stringResource(R.string.storage_erase_body), stringResource(R.string.storage_erase_confirm),
        { confirmErase = false; app.myhealth.ui.common.SecureErase.run(context, onErased) }, stringResource(R.string.action_cancel), { confirmErase = false }, icon = MyHealthIcons.Erase)
}
