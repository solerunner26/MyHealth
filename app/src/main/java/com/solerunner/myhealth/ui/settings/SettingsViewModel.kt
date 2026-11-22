package com.solerunner.myhealth.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solerunner.myhealth.data.prefs.AppLanguage
import com.solerunner.myhealth.data.prefs.AutoLockDelay
import com.solerunner.myhealth.data.prefs.ThemeMode
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.AuditEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsExtraState(
    val storageBytes: Long? = null,
    val shareIntent: Intent? = null,
    val wiped: Boolean = false,
    val message: String? = null,
    val hasCrashLog: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings.settings

    val audit: StateFlow<List<AuditEvent>> = container.audit.observeRecent(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _extra = MutableStateFlow(SettingsExtraState())
    val extra: StateFlow<SettingsExtraState> = _extra.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { container.maintenance.storageFootprintBytes() }
                .onSuccess { bytes -> _extra.update { it.copy(storageBytes = bytes) } }
            _extra.update { it.copy(hasCrashLog = container.crashLogFile.exists()) }
        }
    }

    /** Shares the last fatal exception written by the uncaught-exception handler. */
    fun shareCrashLog() {
        val file = container.crashLogFile
        if (!file.exists()) return
        runCatching { container.exporter.shareDocument(file, "text/plain") }
            .onSuccess { intent -> _extra.update { it.copy(shareIntent = intent) } }
            .onFailure { error -> _extra.update { it.copy(message = error.message) } }
    }

    fun setTheme(mode: ThemeMode) = container.settings.setThemeMode(mode)

    fun setLanguage(language: AppLanguage) {
        container.settings.setLanguage(language)
        viewModelScope.launch { container.audit.log("settings.language", language.tag) }
    }

    fun setAppLock(enabled: Boolean) {
        container.settings.setAppLockEnabled(enabled)
        viewModelScope.launch { container.audit.log("settings.app_lock", enabled.toString()) }
    }

    fun verifyCurrentPin(pin: String): Boolean = container.lock.verifyPin(pin)

    fun setBiometric(enabled: Boolean) = container.settings.setBiometricUnlock(enabled)

    fun setAutoLock(delay: AutoLockDelay) = container.settings.setAutoLock(delay)

    fun setScreenshotBlock(block: Boolean) = container.settings.setScreenshotBlock(block)

    fun setHealthConnectEnabled(enabled: Boolean) = container.settings.setHealthConnectEnabled(enabled)

    fun healthConnectAvailable(): Boolean = container.healthConnect.isAvailable()

    /** Reads wearable data now; the last-sync timestamp lands in settings and updates the UI. */
    fun syncHealthConnect() {
        viewModelScope.launch {
            runCatching { container.syncHealthConnect() }
                .onSuccess { result ->
                    val note = when {
                        result.unavailable -> "unavailable"
                        result.skippedForPermission -> "permission"
                        else -> "synced ${result.days}"
                    }
                    _extra.update { it.copy(message = note) }
                }
                .onFailure { error -> _extra.update { it.copy(message = error.message) } }
        }
    }

    fun changePin(pin: String): Boolean {
        if (!com.solerunner.myhealth.core.security.PinHasher.isValidFormat(pin)) return false
        val stored = container.lock.setPin(pin)
        if (!stored) return false
        container.settings.setAppLockEnabled(true)
        viewModelScope.launch { container.audit.log("settings.pin_changed") }
        return true
    }

    fun exportAll() {
        viewModelScope.launch {
            runCatching {
                val rows = container.reports.labResultsSince(com.solerunner.myhealth.core.util.Dates.historyWindowStart())
                container.exporter.exportResults(rows)
            }.onSuccess { file ->
                _extra.update { it.copy(shareIntent = container.exporter.shareIntent(file)) }
            }.onFailure { error -> _extra.update { it.copy(message = error.message) } }
        }
    }

    fun clearShare() = _extra.update { it.copy(shareIntent = null) }

    fun wipe() {
        viewModelScope.launch {
            runCatching { container.wipeEverything() }
                .onSuccess { _extra.update { it.copy(wiped = true) } }
                .onFailure { error -> _extra.update { it.copy(message = error.message) } }
        }
    }

    fun consumeMessage() = _extra.update { it.copy(message = null, wiped = false) }
}
