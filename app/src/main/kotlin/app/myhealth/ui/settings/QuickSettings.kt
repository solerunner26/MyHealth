package app.myhealth.ui.settings

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myhealth.R
import app.myhealth.container
import app.myhealth.core.LocaleSupport
import app.myhealth.core.designsystem.components.MyHealthSwitchRow
import app.myhealth.core.designsystem.theme.MyHealthTheme
import app.myhealth.core.designsystem.theme.ThemeMode
import app.myhealth.core.designsystem.tokens.MyHealthColorRoles
import app.myhealth.core.designsystem.tokens.MyHealthThemeId
import app.myhealth.core.designsystem.tokens.toColors
import app.myhealth.ui.common.Branding

/**
 * Item 5: opened from the animated logo on Home. Dark mode (toggle), language (three radios) and theme (one radio per
 * theme, drawn in that theme's own colour so the choice is visible before tapping).
 */
@Composable
fun QuickSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.container.preferences
    val state by prefs.state.collectAsStateWithLifecycle()
    val c = MyHealthTheme.colors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
      Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.clip(MyHealthTheme.radii.xlShape).background(c.surfaceRaised).testTag("quickSettings")
            .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MyHealthTheme.spacing.sm)) {
            Text(stringResource(R.string.qs_title), style = MyHealthTheme.typography.title, color = c.textPrimary)
            // dark / light: a hand choice turns the sunrise/sunset switching off
            MyHealthSwitchRow(stringResource(R.string.qs_dark), c.isDark, { on -> prefs.setTheme(if (on) ThemeMode.Dark else ThemeMode.Light) },
                modifier = Modifier.testTag("qsDark"))
            MyHealthSwitchRow(stringResource(R.string.theme_auto), state.theme == ThemeMode.Auto, { on -> prefs.setTheme(if (on) ThemeMode.Auto else if (c.isDark) ThemeMode.Dark else ThemeMode.Light) })

            Header(stringResource(R.string.settings_language))
            Column(Modifier.selectableGroup()) {
                listOf("en" to "English", "hi" to "हिन्दी", "gu" to "ગુજરાતી").forEach { (tag, name) ->
                    val selected = state.language == tag || (state.language.isBlank() && tag == "en")
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton) {
                        prefs.setLanguage(tag); LocaleSupport.apply(context, tag)
                        if (Build.VERSION.SDK_INT < 33) (context as? Activity)?.recreate()
                    }.testTag("qsLang_$tag"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, null, colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.outlineStrong))
                        Spacer(Modifier.width(12.dp))
                        Text(name, style = MyHealthTheme.typography.body, color = c.textPrimary)
                    }
                }
            }

            Header(stringResource(R.string.settings_color_theme))
            Column(Modifier.selectableGroup()) {
                MyHealthThemeId.entries.forEach { t ->
                    val p = MyHealthColorRoles.roles(t, c.isDark).toColors(c.isDark, t)
                    val tint = if (c.isDark) p.accentBright else p.accent
                    val selected = state.colorTheme == t
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected, role = Role.RadioButton) { prefs.setColorTheme(t) }.testTag("qsTheme_${t.name}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        // the radio itself wears the theme's colour
                        RadioButton(selected, null, colors = RadioButtonDefaults.colors(selectedColor = tint, unselectedColor = tint.copy(alpha = 0.7f)))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.hero, tint, p.accentSoft))).border(1.dp, p.outline, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Branding.name(t)), style = MyHealthTheme.typography.subtitle, color = c.textPrimary)
                            Text(stringResource(Branding.description(t)), style = MyHealthTheme.typography.caption, color = c.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun Header(text: String) =
    Text(text.uppercase(), style = MyHealthTheme.typography.overline, color = MyHealthTheme.colors.textSecondary, modifier = Modifier.padding(top = 12.dp))
