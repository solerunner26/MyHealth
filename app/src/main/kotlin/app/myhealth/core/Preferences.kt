package app.myhealth.core

import android.content.Context
import android.content.SharedPreferences
import app.myhealth.core.designsystem.theme.ThemeMode
import app.myhealth.core.designsystem.tokens.MyHealthThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non-health UI preferences that must be readable BEFORE the encrypted database opens (theme, language, lock).
 * Stored in app-private prefs, excluded from backup. Contains no health data.
 */
class Preferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    data class Snapshot(
        val theme: ThemeMode = ThemeMode.System,
        val colorTheme: MyHealthThemeId = MyHealthThemeId.Aurora,
        val language: String = "", // "" = follow system
        val lockEnabled: Boolean = false,
        val biometricEnabled: Boolean = false,
        val blockScreenshots: Boolean = true,
        val activeMemberId: String? = null,
        val historyYears: Int = 5,
        val onboarded: Boolean = false,
        val healthConnectEnabled: Boolean = false,
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private fun read() = Snapshot(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.System.name)!!) }.getOrDefault(ThemeMode.System),
        colorTheme = runCatching { MyHealthThemeId.valueOf(prefs.getString("color_theme", MyHealthThemeId.Aurora.name)!!) }.getOrDefault(MyHealthThemeId.Aurora),
        language = prefs.getString("language", "") ?: "",
        lockEnabled = prefs.getBoolean("lock", false),
        biometricEnabled = prefs.getBoolean("biometric", false),
        blockScreenshots = prefs.getBoolean("secure_flag", true),
        activeMemberId = prefs.getString("active_member", null),
        historyYears = prefs.getInt("history_years", 5),
        onboarded = prefs.getBoolean("onboarded", false),
        healthConnectEnabled = prefs.getBoolean("hc_enabled", false),
    )

    /** commit(), not apply(): lock state and PIN attempt counters must survive an immediate process death. */
    @android.annotation.SuppressLint("ApplySharedPref", "UseKtx")
    fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).commit()
        _state.value = read()
    }

    fun setTheme(t: ThemeMode) = update { putString("theme", t.name) }
    fun setColorTheme(t: MyHealthThemeId) = update { putString("color_theme", t.name) }
    fun setLanguage(tag: String) = update { putString("language", tag) }
    fun setLock(enabled: Boolean) = update { putBoolean("lock", enabled); if (!enabled) { putBoolean("biometric", false); remove("pin_hash"); remove("pin_salt") } }
    fun setOnboardedMember(id: String) = update { putBoolean("onboarded", true); putString("active_member", id) }
    fun setBiometric(enabled: Boolean) = update { putBoolean("biometric", enabled) }
    fun setBlockScreenshots(enabled: Boolean) = update { putBoolean("secure_flag", enabled) }
    fun setActiveMember(id: String?) = update { putString("active_member", id) }
    fun setHistoryYears(y: Int) = update { putInt("history_years", y.coerceIn(1, 30)) }
    fun setOnboarded(v: Boolean) = update { putBoolean("onboarded", v) }
    fun setHealthConnect(v: Boolean) = update { putBoolean("hc_enabled", v) }

    // PIN material (salted PBKDF2 hash) lives here too: it gates the UI, the data keys are Keystore-wrapped.
    fun pinMaterial(): Pair<String, String>? {
        val h = prefs.getString("pin_hash", null) ?: return null
        val s = prefs.getString("pin_salt", null) ?: return null
        return h to s
    }
    fun setPinMaterial(hashB64: String, saltB64: String) = update { putString("pin_hash", hashB64); putString("pin_salt", saltB64) }
    fun failedAttempts(): Int = prefs.getInt("pin_fail", 0)
    /** Failed-attempt counter plus the running lockout, measured on the monotonic clock of one boot (see [AppLock]). */
    fun setFailedAttempts(n: Int, lockout: Lockout = Lockout.NONE) = update {
        putInt("pin_fail", n); putLong("pin_lock_boot", lockout.boot); putLong("pin_lock_start", lockout.startElapsed); putLong("pin_lock_ms", lockout.durationMs)
        remove("pin_lockout_until") // legacy wall-clock field
    }
    fun lockout(): Lockout = Lockout(prefs.getLong("pin_lock_boot", 0), prefs.getLong("pin_lock_start", 0), prefs.getLong("pin_lock_ms", 0))
    fun setLockout(l: Lockout) = update { putLong("pin_lock_boot", l.boot); putLong("pin_lock_start", l.startElapsed); putLong("pin_lock_ms", l.durationMs) }

    data class Lockout(val boot: Long, val startElapsed: Long, val durationMs: Long) { companion object { val NONE = Lockout(0, 0, 0) } }

    @android.annotation.SuppressLint("ApplySharedPref", "UseKtx")
    fun clearAll() { prefs.edit().clear().commit(); _state.value = read() }
}
