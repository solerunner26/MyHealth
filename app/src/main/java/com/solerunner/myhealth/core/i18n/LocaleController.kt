package com.solerunner.myhealth.core.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.solerunner.myhealth.data.prefs.AppLanguage
import java.util.Locale

/**
 * App-language switching without AppCompat.
 *
 * The activity builds a configuration-overridden base context in `attachBaseContext`, and a language
 * change re-creates the activity from [MainActivity]. Resources, `LocalConfiguration` and every
 * `stringResource` call therefore resolve in the chosen language on API 26 through 37.
 *
 * Locale.setDefault is deliberately not called globally: it would leak into date formatting done by
 * background workers, which must stay consistent with the stored record.
 */
object LocaleController {

    fun localeFor(language: AppLanguage): Locale = Locale.forLanguageTag(language.tag)

    fun localizedContext(base: Context, language: AppLanguage): Context {
        val locale = localeFor(language)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }

    /** Resolves an [AppLanguage] for a system locale, used on first launch. */
    fun matchSystem(system: Locale): AppLanguage = when (system.language) {
        "hi" -> AppLanguage.HINDI
        "gu" -> AppLanguage.GUJARATI
        else -> AppLanguage.ENGLISH
    }
}
