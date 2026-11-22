package app.myhealth.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.myhealth.core.designsystem.tokens.MyHealthThemeId

/**
 * The launcher icon follows the selected theme: one manifest <activity-alias> per theme, exactly one enabled.
 * DONT_KILL_APP keeps the running app alive; some launchers need a moment to refresh the icon.
 */
object LauncherIcons {
    fun aliasFor(t: MyHealthThemeId) = "app.myhealth.Launcher" + t.name

    fun apply(context: Context, theme: MyHealthThemeId) {
        val pm = context.packageManager
        MyHealthThemeId.entries.forEach { t ->
            val state = if (t == theme) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            runCatching { pm.setComponentEnabledSetting(ComponentName(context.packageName, aliasFor(t)), state, PackageManager.DONT_KILL_APP) }
        }
    }
}
