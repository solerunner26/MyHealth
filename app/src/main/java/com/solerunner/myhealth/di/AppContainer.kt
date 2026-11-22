package com.solerunner.myhealth.di

import android.content.Context
import com.solerunner.myhealth.auth.AppLockManager
import com.solerunner.myhealth.core.security.SecureStore
import com.solerunner.myhealth.core.security.VaultCrypto
import com.solerunner.myhealth.core.security.VaultFileStore
import com.solerunner.myhealth.data.backup.BackupService
import com.solerunner.myhealth.data.db.MyHealthDatabase
import com.solerunner.myhealth.data.db.SqlcipherDbFactory
import com.solerunner.myhealth.data.export.DoctorSummaryPdf
import com.solerunner.myhealth.data.export.ExportService
import com.solerunner.myhealth.data.healthconnect.HealthConnectResult
import com.solerunner.myhealth.data.healthconnect.HealthConnectSync
import com.solerunner.myhealth.data.import.DocumentReader
import com.solerunner.myhealth.data.import.ImportService
import com.solerunner.myhealth.data.prefs.SecureSettingsRepository
import com.solerunner.myhealth.data.prefs.SettingsRepository
import com.solerunner.myhealth.reminders.MedicineReminders
import com.solerunner.myhealth.data.repository.AttachmentRepositoryImpl
import com.solerunner.myhealth.data.repository.AuditRepositoryImpl
import com.solerunner.myhealth.data.repository.DeviceRepositoryImpl
import com.solerunner.myhealth.data.repository.MaintenanceRepositoryImpl
import com.solerunner.myhealth.data.repository.MeasurementRepositoryImpl
import com.solerunner.myhealth.data.repository.MedicineRepositoryImpl
import com.solerunner.myhealth.data.repository.ProfileRepositoryImpl
import com.solerunner.myhealth.data.repository.ReportRepositoryImpl
import com.solerunner.myhealth.data.repository.TreatmentRepositoryImpl
import com.solerunner.myhealth.domain.repository.AuditRepository
import com.solerunner.myhealth.domain.repository.DeviceRepository
import com.solerunner.myhealth.domain.repository.MaintenanceRepository
import com.solerunner.myhealth.domain.repository.MeasurementRepository
import com.solerunner.myhealth.domain.repository.MedicineRepository
import com.solerunner.myhealth.domain.repository.ProfileRepository
import com.solerunner.myhealth.domain.repository.ReportRepository
import com.solerunner.myhealth.domain.repository.TreatmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manual dependency container. One instance per process, created by [com.solerunner.myhealth.MyHealthApplication].
 *
 * The encrypted database is opened on a background dispatcher because SQLCipher derives its key from a
 * hardware-wrapped passphrase, which is not instant. Screens gate on [ready] until that finishes.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val crypto = VaultCrypto(appContext)
    val vault = VaultFileStore(appContext, crypto)
    val secureStore = SecureStore(appContext, crypto)
    val settings: SettingsRepository = SecureSettingsRepository(secureStore)
    val lock = AppLockManager(secureStore)

    val database = MyHealthDatabase(SqlcipherDbFactory(appContext, crypto))

    val profiles: ProfileRepository = ProfileRepositoryImpl(database)
    val reports: ReportRepository = ReportRepositoryImpl(database, vault)
    val measurements: MeasurementRepository = MeasurementRepositoryImpl(database)
    val medicines: MedicineRepository = MedicineRepositoryImpl(database)
    val treatments: TreatmentRepository = TreatmentRepositoryImpl(database)
    val device: DeviceRepository = DeviceRepositoryImpl(database)
    val attachments = AttachmentRepositoryImpl(database, vault)
    val audit: AuditRepository = AuditRepositoryImpl(database)
    val maintenance: MaintenanceRepository = MaintenanceRepositoryImpl(database, vault)

    val documentReader = DocumentReader(appContext)
    val importer = ImportService(database, documentReader, vault, reports, medicines, profiles, audit)
    val exporter = ExportService(appContext)
    val backup = BackupService(database, vault)
    val doctorSummaryPdf = DoctorSummaryPdf(appContext)
    val reminders = MedicineReminders(appContext)
    val healthConnect = HealthConnectSync(appContext, device, measurements)

    /** Written by the uncaught-exception handler; shared only when the user asks. */
    val crashLogFile: java.io.File get() = java.io.File(appContext.filesDir, "diagnostics/last-crash.txt")

    private val readyState = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = readyState.asStateFlow()

    private val failureState = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = failureState.asStateFlow()

    private val databaseState = appContext.getSharedPreferences("myhealth_db_state", Context.MODE_PRIVATE)

    fun start() {
        if (readyState.value) return
        failureState.value = null
        scope.launch {
            val passphrase = runCatching { crypto.databasePassphrase() }
                .getOrElse { error ->
                    fail(error)
                    return@launch
                }
            var result = runCatching { database.open(passphrase) }
            if (result.isFailure && !databaseState.getBoolean(KEY_DB_INITIALIZED, false)) {
                // The database was never opened successfully (for example the first build failed while
                // configuring journal mode). Keep the file for inspection and start clean.
                runCatching { quarantineDatabaseFiles() }
                result = runCatching { database.open(passphrase) }
            }
            result.onSuccess {
                databaseState.edit().putBoolean(KEY_DB_INITIALIZED, true).apply()
                readyState.value = true
                if (settings.settings.value.healthConnectEnabled) {
                    scope.launch { runCatching { syncHealthConnect() } }
                }
            }.onFailure { error -> fail(error) }
        }
    }

    private fun fail(error: Throwable) {
        android.util.Log.e("MyHealth", "Encrypted database could not be opened", error)
        failureState.value = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
    }

    /** Renames the database and its sidecars; only used when no successful open ever happened. */
    private fun quarantineDatabaseFiles() {
        val base = appContext.getDatabasePath("myhealth.db")
        val stamp = System.currentTimeMillis()
        listOf(base, java.io.File(base.path + "-wal"), java.io.File(base.path + "-shm")).forEach { file ->
            if (file.exists()) file.renameTo(java.io.File(file.path + ".unusable-$stamp"))
        }
    }

    private companion object {
        const val KEY_DB_INITIALIZED = "db_initialized"
    }

    fun close() {
        database.close()
        documentReader.close()
    }

    /** Reads wearable data now and records the time of a successful sync. */
    suspend fun syncHealthConnect(): HealthConnectResult {
        val result = healthConnect.sync()
        if (!result.unavailable && !result.skippedForPermission) {
            settings.setLastHealthConnectSync(System.currentTimeMillis())
        }
        return result
    }

    /**
     * Erases health data, vault files, audit log, PIN, security flags and armed reminders in one place.
     * Kept on the container so no screen can perform a partial wipe.
     */
    suspend fun wipeEverything() {
        maintenance.wipeEverything()
        reminders.cancelAll()
        secureStore.clearAll()
        settings.setAppLockEnabled(false)
        settings.setBiometricUnlock(false)
    }

    val scopeForWork: CoroutineScope get() = scope
}
