package com.solerunner.myhealth.domain.repository

import com.solerunner.myhealth.domain.model.Attachment
import com.solerunner.myhealth.domain.model.AuditEvent
import com.solerunner.myhealth.domain.model.DailyMetric
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.domain.model.Treatment
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Repository contracts live in `domain` so the UI and the analytics never depend on SQL, SQLCipher or
 * Health Connect types. Implementations live in `data`; `AppContainer` wires them.
 *
 * Reads that the UI observes are exposed as `Flow`; one-shot reads and all writes are `suspend`.
 */

interface ProfileRepository {
    fun observe(): Flow<Profile?>
    suspend fun current(): Profile?
    suspend fun save(profile: Profile): Long
    suspend fun clear()
}

interface ReportRepository {
    fun observeAll(): Flow<List<Report>>
    suspend fun recent(limit: Int = 20): List<Report>
    suspend fun byId(id: Long): Report?
    suspend fun labResults(reportId: Long): List<LabResult>
    suspend fun attachments(reportId: Long): List<Attachment>

    /** Every (report, result) pair inside the window — the input of the trend engine. */
    suspend fun labResultsSince(since: LocalDate): List<Pair<Report, LabResult>>

    suspend fun save(
        report: Report,
        results: List<LabResult>,
        measurements: List<Measurement> = emptyList(),
        attachment: Attachment? = null,
    ): Long

    suspend fun update(report: Report, results: List<LabResult>? = null)
    suspend fun delete(id: Long)
    suspend fun count(): Int
    suspend fun latestImport(): Report?
}

interface MeasurementRepository {
    fun observeAll(): Flow<List<Measurement>>
    suspend fun since(date: LocalDate): List<Measurement>
    suspend fun add(measurement: Measurement): Long
    suspend fun update(measurement: Measurement)
    suspend fun delete(id: Long)
    suspend fun latest(kind: MeasurementKind): Measurement?
}

interface MedicineRepository {
    fun observeAll(): Flow<List<Medicine>>
    suspend fun all(): List<Medicine>
    suspend fun byId(id: Long): Medicine?
    suspend fun save(medicine: Medicine): Long
    suspend fun delete(id: Long)
}

interface TreatmentRepository {
    fun observeAll(): Flow<List<Treatment>>
    suspend fun all(): List<Treatment>
    suspend fun save(treatment: Treatment): Long
    suspend fun delete(id: Long)
}

interface DeviceRepository {
    fun observeRecent(days: Int = 30): Flow<List<DailyMetric>>
    suspend fun since(date: LocalDate): List<DailyMetric>
    suspend fun upsertAll(metrics: List<DailyMetric>)
    suspend fun lastSync(): Instant?
    suspend fun clear()
}

interface AttachmentRepository {
    suspend fun byReport(reportId: Long): List<Attachment>
    suspend fun delete(id: Long)
    suspend fun sizeInBytes(): Long
}

interface AuditRepository {
    fun observeRecent(limit: Int = 100): Flow<List<AuditEvent>>
    suspend fun log(action: String, detail: String = "")
    suspend fun clear()
}

interface MaintenanceRepository {
    /** Deletes every health record and every vault file. Used by "erase all data". */
    suspend fun wipeEverything()
    suspend fun storageFootprintBytes(): Long
}
