package com.solerunner.myhealth.data.prefs

import com.solerunner.myhealth.core.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Theme choice. `SYSTEM` is resolved once, in the theme entry point. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The three languages the product ships with, in the order the settings screen shows them. */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    HINDI("hi"),
    GUJARATI("gu"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

/** How long the app may stay in the background before it asks for the PIN again. */
enum class AutoLockDelay(val millis: Long) {
    IMMEDIATELY(0L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(300_000L),
    FIFTEEN_MINUTES(900_000L),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val appLockEnabled: Boolean = false,
    val biometricUnlock: Boolean = false,
    val autoLock: AutoLockDelay = AutoLockDelay.IMMEDIATELY,
    val screenshotBlock: Boolean = true,
    val healthConnectEnabled: Boolean = false,
    val lastHealthConnectSync: Long = 0L,
)

/**
 * Persisted preferences. The store is encrypted with the Keystore-backed vault key
 * ([SecureStore]), so flags such as "app lock enabled" cannot be flipped by editing a plain XML file
 * on a rooted device.
 */
interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun setThemeMode(mode: ThemeMode)
    fun setLanguage(language: AppLanguage)
    fun setAppLockEnabled(enabled: Boolean)
    fun setBiometricUnlock(enabled: Boolean)
    fun setAutoLock(delay: AutoLockDelay)
    fun setScreenshotBlock(block: Boolean)
    fun setHealthConnectEnabled(enabled: Boolean)
    fun setLastHealthConnectSync(epochMillis: Long)
}

class SecureSettingsRepository(private val store: SecureStore) : SettingsRepository {

    private val state = MutableStateFlow(read())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    private fun read(): AppSettings = AppSettings(
        themeMode = enumValue(store.getString(KEY_THEME), ThemeMode.SYSTEM),
        language = AppLanguage.fromTag(store.getString(KEY_LANGUAGE)),
        appLockEnabled = store.getBoolean(KEY_APP_LOCK, false),
        biometricUnlock = store.getBoolean(KEY_BIOMETRIC, false),
        autoLock = enumValue(store.getString(KEY_AUTO_LOCK), AutoLockDelay.IMMEDIATELY),
        screenshotBlock = store.getBoolean(KEY_SCREENSHOT_BLOCK, true),
        healthConnectEnabled = store.getBoolean(KEY_HEALTH_CONNECT, false),
        lastHealthConnectSync = store.getString(KEY_HEALTH_CONNECT_SYNC)?.toLongOrNull() ?: 0L,
    )

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        raw?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

    private fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }

    override fun setThemeMode(mode: ThemeMode) {
        store.putString(KEY_THEME, mode.name)
        update { it.copy(themeMode = mode) }
    }

    override fun setLanguage(language: AppLanguage) {
        store.putString(KEY_LANGUAGE, language.tag)
        update { it.copy(language = language) }
    }

    override fun setAppLockEnabled(enabled: Boolean) {
        store.putBoolean(KEY_APP_LOCK, enabled)
        update { it.copy(appLockEnabled = enabled) }
    }

    override fun setBiometricUnlock(enabled: Boolean) {
        store.putBoolean(KEY_BIOMETRIC, enabled)
        update { it.copy(biometricUnlock = enabled) }
    }

    override fun setAutoLock(delay: AutoLockDelay) {
        store.putString(KEY_AUTO_LOCK, delay.name)
        update { it.copy(autoLock = delay) }
    }

    override fun setScreenshotBlock(block: Boolean) {
        store.putBoolean(KEY_SCREENSHOT_BLOCK, block)
        update { it.copy(screenshotBlock = block) }
    }

    override fun setHealthConnectEnabled(enabled: Boolean) {
        store.putBoolean(KEY_HEALTH_CONNECT, enabled)
        update { it.copy(healthConnectEnabled = enabled) }
    }

    override fun setLastHealthConnectSync(epochMillis: Long) {
        store.putString(KEY_HEALTH_CONNECT_SYNC, epochMillis.toString())
        update { it.copy(lastHealthConnectSync = epochMillis) }
    }

    private companion object {
        const val KEY_THEME = "settings.theme"
        const val KEY_LANGUAGE = "settings.language"
        const val KEY_APP_LOCK = "settings.app_lock"
        const val KEY_BIOMETRIC = "settings.biometric"
        const val KEY_AUTO_LOCK = "settings.auto_lock"
        const val KEY_SCREENSHOT_BLOCK = "settings.screenshot_block"
        const val KEY_HEALTH_CONNECT = "settings.health_connect"
        const val KEY_HEALTH_CONNECT_SYNC = "settings.health_connect_sync"
    }
}
