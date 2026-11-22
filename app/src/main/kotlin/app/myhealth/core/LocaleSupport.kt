package app.myhealth.core

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Per-app language (EN / HI / GU) without AppCompat. API 33+: the platform LocaleManager (system also lists the app
 * in Settings › App languages via locales_config). API 26–32: the stored tag is applied to Activity and receiver
 * contexts with createConfigurationContext.
 */
object LocaleSupport {
    val supported = listOf("en", "hi", "gu")

    fun stored(context: Context): String = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getString("language", "") ?: ""

    fun apply(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales = if (tag.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag) }
        }
    }

    /** A context whose resources use the chosen language (no-op when following the system, or on API 33+). */
    fun localized(base: Context): Context {
        val tag = stored(base)
        if (tag.isBlank() || Build.VERSION.SDK_INT >= 33) return base
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) }
        return base.createConfigurationContext(config)
    }
}
