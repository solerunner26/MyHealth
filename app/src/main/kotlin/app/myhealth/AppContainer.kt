package app.myhealth

import android.content.Context
import android.util.Log
import app.myhealth.core.CrashLog
import app.myhealth.core.Preferences
import app.myhealth.core.Safe
import app.myhealth.data.DataStack
import app.myhealth.data.StorageOpener
import app.myhealth.data.security.SecureFailure
import app.myhealth.data.security.SecureStorageException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

sealed interface StorageState {
    data object Opening : StorageState
    data class Ready(val stack: DataStack) : StorageState
    data class Failed(val failure: SecureFailure) : StorageState
}

/** Manual dependency graph. Constructed once in [MyHealthApplication]; no reflection, no codegen. */
class AppContainer(
    val context: Context,
    val crashLog: CrashLog,
    private val openerFactory: () -> StorageOpener,
    val clock: Clock = Clock.systemDefaultZone(),
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + Safe.handler)
    val preferences = Preferences(context)
    /** True once the user tapped "Enter" on this app start's splash (process lifetime, not persisted). */
    var splashDone = false
    /** The update check runs once per app start. */
    var updateChecked = false
    val lock = app.myhealth.core.AppLock(preferences, app.myhealth.core.AndroidMonotonic(context))
    val reminders by lazy { app.myhealth.reminders.ReminderScheduler(context, clock) }

    private val _storage = MutableStateFlow<StorageState>(StorageState.Opening)
    val storage: StateFlow<StorageState> = _storage.asStateFlow()
    private val openLock = Mutex()

    /** The open stack, or null while opening/failed. Background components (reminders) must handle null. */
    val stackOrNull: DataStack? get() = (storage.value as? StorageState.Ready)?.stack

    fun start() {
        if (_storage.value is StorageState.Ready) return
        appScope.launch(Dispatchers.IO) { app.myhealth.ui.common.Share.wipe(context) }
        appScope.launch(Dispatchers.IO) { openNow() }
    }

    /** Opens storage synchronously on the caller's thread (used by receivers/workers after reboot). */
    suspend fun openNow(): DataStack? = openLock.withLock {
        (_storage.value as? StorageState.Ready)?.let { return@withLock it.stack }
        _storage.value = StorageState.Opening
        try {
            val stack = openerFactory().open()
            _storage.value = StorageState.Ready(stack)
            // Keep alarms in sync with the stored schedule on every unlock (idempotent).
            appScope.launch(Dispatchers.IO) { runCatching { reminders.ensureChannel(); reminders.rescheduleAll(stack) } }
            // Opted-in Health Connect: incremental sync on every unlock, plus a periodic background sync (item 1).
            if (preferences.state.value.healthConnectEnabled) app.myhealth.health.HealthSyncWorker.schedule(context)
            if (preferences.state.value.healthConnectEnabled) appScope.launch(Dispatchers.IO) {
                runCatching {
                    val self = stack.members.all().firstOrNull { it.relationship == app.myhealth.domain.model.Relationship.Self } ?: return@runCatching
                    app.myhealth.health.HealthSync(app.myhealth.health.HealthConnectSource(context, clock.zone), stack.activity, clock, stack.vitals).sync(self.id)
                }
            }
            stack
        } catch (e: SecureStorageException) {
            Log.w("MyHealth", "storage open failed: ${e.failure}")
            _storage.value = StorageState.Failed(e.failure)
            null
        } catch (e: Exception) {
            Log.w("MyHealth", "storage open failed", e)
            _storage.value = StorageState.Failed(SecureFailure.DatabaseUnavailable)
            null
        }
    }

    fun retry() = start()

    internal fun resetForTest() { _storage.value = StorageState.Opening }

    /** Destroys all data and keys, then starts fresh. Only reachable after an explicit, confirmed user action. */
    fun eraseEverything(onDone: () -> Unit = {}) {
        appScope.launch(Dispatchers.IO) {
            openLock.withLock {
                (_storage.value as? StorageState.Ready)?.stack?.close()
                _storage.value = StorageState.Opening
                runCatching { openerFactory().eraseEverything() }
                preferences.clearAll()
                crashLog.clear()
            }
            openNow()
            launch(Dispatchers.Main) { onDone() }
        }
    }
}

/** Test-only: forget the open stack as if the process had restarted. */
internal fun AppContainer.eraseStateForTest() {
    stackOrNull?.close()
    resetForTest()
}
