package app.myhealth.ui.settings

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.myhealth.core.designsystem.tokens.toColors
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
    Column(Modifier.fillMaxSize().testTag("settings")) {
        MyHealthTopBar(stringResource(R.string.more_settings), onBack = onBack, backLabel = stringResource(R.string.action_back))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = sp.gutter), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
            MyHealthSectionHeader(stringResource(R.string.settings_appearance))
            Text(stringResource(R.string.settings_color_theme), style = MyHealthTheme.typography.label, color = MyHealthTheme.colors.textSecondary)
            app.myhealth.core.designsystem.tokens.ThemeFamily.entries.forEach { fam ->
                Text(stringResource(if (fam == app.myhealth.core.designsystem.tokens.ThemeFamily.Glass) R.string.theme_family_glass else R.string.theme_family_solid).uppercase(),
                    style = MyHealthTheme.typography.overline, color = MyHealthTheme.colors.textSecondary, modifier = Modifier.padding(top = sp.sm))
                app.myhealth.core.designsystem.tokens.MyHealthThemeId.entries.filter { it.family == fam }.forEach { t ->
                    ThemeCard(t, selected = t == prefs.colorTheme, dark = MyHealthTheme.colors.isDark) { c.preferences.setColorTheme(t) }
                }
            }
            Text(stringResource(R.string.settings_color_theme_note), style = MyHealthTheme.typography.caption, color = MyHealthTheme.colors.textSecondary)
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

            var autoUpdate by rememberSaveable { mutableStateOf(c.preferences.autoUpdate()) }
            MyHealthSwitchRow(stringResource(R.string.settings_auto_update), autoUpdate, { autoUpdate = it; c.preferences.setAutoUpdate(it) },
                subtitle = stringResource(R.string.settings_auto_update_sub), modifier = Modifier.testTag("autoUpdateSwitch"))

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

/** Live preview of a theme: its own colours, logo, name and mood line. Selection = border weight + check + text. */
@Composable
private fun ThemeCard(t: app.myhealth.core.designsystem.tokens.MyHealthThemeId, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val p = app.myhealth.core.designsystem.tokens.MyHealthColorRoles.roles(t, dark).toColors(dark, t)
    val shape = MyHealthTheme.radii.lgShape
    val selectedCd = stringResource(R.string.theme_selected, stringResource(app.myhealth.ui.common.Branding.name(t)))
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (p.isGlass) androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.background, p.accentSoft, p.backgroundAlt))
                else androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.background, p.background, p.accentSoft)))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) p.accent else p.outline, shape)
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .then(if (selected) Modifier.semantics { contentDescription = selectedCd } else Modifier)
            .padding(MyHealthTheme.spacing.md).testTag("theme_${t.name}"),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        app.myhealth.ui.common.MyHealthLogo(52.dp, colors = p, alive = selected)
        androidx.compose.foundation.layout.Spacer(Modifier.width(MyHealthTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(stringResource(app.myhealth.ui.common.Branding.name(t)), style = MyHealthTheme.typography.subtitle, color = p.textPrimary)
            Text(stringResource(app.myhealth.ui.common.Branding.description(t)), style = MyHealthTheme.typography.caption, color = p.textSecondary)
            androidx.compose.foundation.layout.Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(p.hero, p.accent, p.heroAccent, p.accentSoft, p.surfaceRaised).forEach { sw ->
                    androidx.compose.foundation.layout.Box(Modifier.size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(sw).border(1.dp, p.outline, androidx.compose.foundation.shape.CircleShape))
                }
            }
        }
        if (selected) androidx.compose.material3.Icon(MyHealthIcons.StatusNormal, null, tint = p.accent, modifier = Modifier.size(28.dp))
    }
}
