package com.solerunner.myhealth.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Owns the single encrypted [Db] connection, the DAOs, and the change signal that turns plain SQL reads
 * into live Compose data.
 *
 * Why a version counter instead of SQLite triggers: every write in this app goes through [write], so a
 * counter is enough to re-run observers, and it keeps the SQL boring (no trigger maintenance, no
 * per-table observers to leak).
 */
class MyHealthDatabase(private val factory: DbFactory) {

    private val version = MutableStateFlow(0L)
    private val openState = MutableStateFlow(false)

    @Volatile
    private var db: Db? = null

    val profileDao: ProfileDao get() = ProfileDao(connection())
    val reportDao: ReportDao get() = ReportDao(connection())
    val labResultDao: LabResultDao get() = LabResultDao(connection())
    val measurementDao: MeasurementDao get() = MeasurementDao(connection())
    val medicineDao: MedicineDao get() = MedicineDao(connection())
    val treatmentDao: TreatmentDao get() = TreatmentDao(connection())
    val dailyMetricDao: DailyMetricDao get() = DailyMetricDao(connection())
    val attachmentDao: AttachmentDao get() = AttachmentDao(connection())
    val auditDao: AuditDao get() = AuditDao(connection())
    val metaDao: MetaDao get() = MetaDao(connection())

    fun open(passphrase: CharArray): Unit = synchronized(this) {
        if (db == null) {
            val created = factory.create(passphrase)
            Schema.ensure(created)
            db = created
            openState.value = true
        }
    }

    fun isOpen(): Boolean = openState.value && db != null

    private fun connection(): Db = db ?: error("Database is not open yet")

    suspend fun <T> read(block: (MyHealthDatabase) -> T): T = withContext(Dispatchers.IO) { block(this@MyHealthDatabase) }

    suspend fun <T> write(block: (MyHealthDatabase) -> T): T = withContext(Dispatchers.IO) {
        val result = connection().transaction { block(this@MyHealthDatabase) }
        version.update { it + 1 }
        result
    }

    /** Escape hatch for whole-table operations that no single DAO owns (wipe, retention pruning). */
    fun exec(sql: String, args: List<Any?> = emptyList()) = connection().exec(sql, args)

    /** Re-emits whenever anything is written; repositories map it into their own queries. */
    fun changes(): Flow<Long> = version.asStateFlow()

    /**
     * Observers are only queried once the encrypted database is actually open. The first composition of
     * the app races the background open, and querying early threw from a LaunchedEffect, crashing the
     * process before onboarding could appear. Emits again as soon as the database opens.
     */
    fun <T> observe(query: suspend () -> T): Flow<T> =
        combine(version, openState) { _, opened -> opened }
            .distinctUntilChanged()
            .mapNotNull { opened -> if (opened) query() else null }
            .flowOn(Dispatchers.IO)

    fun close(): Unit = synchronized(this) {
        db?.close()
        db = null
        openState.value = false
    }
}
