package com.solerunner.myhealth.data.repository

import com.solerunner.myhealth.core.security.VaultFileStore
import com.solerunner.myhealth.data.db.MyHealthDatabase
import com.solerunner.myhealth.domain.model.Attachment
import com.solerunner.myhealth.domain.model.AuditEvent
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.domain.repository.AttachmentRepository
import com.solerunner.myhealth.domain.repository.AuditRepository
import com.solerunner.myhealth.domain.repository.MaintenanceRepository
import com.solerunner.myhealth.domain.repository.MeasurementRepository
import com.solerunner.myhealth.domain.repository.ProfileRepository
import com.solerunner.myhealth.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

class ProfileRepositoryImpl(private val database: MyHealthDatabase) : ProfileRepository {

    override fun observe(): Flow<Profile?> = database.observe { database.read { it.profileDao.current() } }

    override suspend fun current(): Profile? = database.read { it.profileDao.current() }

    override suspend fun save(profile: Profile): Long = database.write { db ->
        val existing = db.profileDao.current()
        val toSave = if (profile.id == 0L && existing != null) profile.copy(id = existing.id) else profile
        db.profileDao.save(toSave)
        db.profileDao.current()?.id ?: 0L
    }

    override suspend fun clear() = database.write { it.profileDao.clear() }
}

class ReportRepositoryImpl(
    private val database: MyHealthDatabase,
    private val vault: VaultFileStore,
) : ReportRepository {

    override fun observeAll(): Flow<List<Report>> = database.observe { database.read { it.reportDao.all() } }

    override suspend fun recent(limit: Int): List<Report> = database.read { it.reportDao.recent(limit) }

    override suspend fun byId(id: Long): Report? = database.read { it.reportDao.byId(id) }

    override suspend fun labResults(reportId: Long): List<LabResult> = database.read { it.labResultDao.forReport(reportId) }

    override suspend fun attachments(reportId: Long): List<Attachment> = database.read { it.attachmentDao.forReport(reportId) }

    override suspend fun labResultsSince(since: LocalDate): List<Pair<Report, LabResult>> =
        database.read { db ->
            val reports = db.reportDao.all().associateBy { it.id }
            db.labResultDao.since(since).mapNotNull { (_, result) -> reports[result.reportId]?.let { it to result } }
        }

    override suspend fun save(
        report: Report,
        results: List<LabResult>,
        measurements: List<Measurement>,
        attachment: Attachment?,
    ): Long = database.write { db ->
        val id = db.reportDao.insert(report)
        db.labResultDao.insertAll(id, results)
        measurements.forEach { measurement ->
            db.measurementDao.insert(measurement.copy(reportId = id))
        }
        attachment?.let { db.attachmentDao.insert(it.copy(reportId = id)) }
        id
    }

    override suspend fun update(report: Report, results: List<LabResult>?) = database.write { db ->
        db.reportDao.update(report)
        if (results != null) {
            db.labResultDao.deleteForReport(report.id)
            db.labResultDao.insertAll(report.id, results)
        }
    }

    override suspend fun delete(id: Long) = database.write { db ->
        db.attachmentDao.forReport(id).forEach { attachment -> vault.delete(attachment.storedName) }
        db.reportDao.delete(id)
    }

    override suspend fun count(): Int = database.read { it.reportDao.count() }

    override suspend fun latestImport(): Report? = database.read { it.reportDao.latest() }
}

class MeasurementRepositoryImpl(private val database: MyHealthDatabase) : MeasurementRepository {

    override fun observeAll(): Flow<List<Measurement>> = database.observe { database.read { it.measurementDao.all() } }

    override suspend fun since(date: LocalDate): List<Measurement> = database.read { it.measurementDao.since(date) }

    override suspend fun add(measurement: Measurement): Long = database.write { it.measurementDao.insert(measurement) }

    override suspend fun update(measurement: Measurement) = database.write { it.measurementDao.update(measurement) }

    override suspend fun delete(id: Long) = database.write { it.measurementDao.delete(id) }

    override suspend fun latest(kind: MeasurementKind): Measurement? = database.read { it.measurementDao.latest(kind) }
}

class AttachmentRepositoryImpl(
    private val database: MyHealthDatabase,
    private val vault: VaultFileStore,
) : AttachmentRepository {

    override suspend fun byReport(reportId: Long): List<Attachment> = database.read { it.attachmentDao.forReport(reportId) }

    override suspend fun delete(id: Long) = database.write { db ->
        db.attachmentDao.byId(id)?.let { vault.delete(it.storedName) }
        db.attachmentDao.delete(id)
    }

    override suspend fun sizeInBytes(): Long = database.read { it.attachmentDao.totalSize() }
}

class AuditRepositoryImpl(private val database: MyHealthDatabase) : AuditRepository {

    override fun observeRecent(limit: Int): Flow<List<AuditEvent>> =
        database.observe { database.read { it.auditDao.recent(limit) } }

    override suspend fun log(action: String, detail: String) = database.write { db ->
        db.auditDao.insert(AuditEvent(at = Instant.now(), action = action, detail = detail))
        // Keep the log useful but bounded: a security log is not a health record.
        db.exec(
            "DELETE FROM audit_event WHERE id NOT IN (SELECT id FROM audit_event ORDER BY at DESC LIMIT 500)",
        )
    }

    override suspend fun clear() = database.write { it.auditDao.deleteAll() }
}

/** "Erase everything": health data, vault files and the audit log. Irreversible by design. */
class MaintenanceRepositoryImpl(
    private val database: MyHealthDatabase,
    private val vault: VaultFileStore,
) : MaintenanceRepository {

    override suspend fun wipeEverything() = database.write { db ->
        db.attachmentDao.deleteAll()
        db.labResultDao.deleteForReport(-1L)
        db.reportDao.deleteAll()
        db.measurementDao.deleteAll()
        db.medicineDao.deleteAll()
        db.treatmentDao.deleteAll()
        db.dailyMetricDao.deleteAll()
        db.auditDao.deleteAll()
        db.profileDao.clear()
        db.exec("DELETE FROM lab_result")
        vault.clear()
    }

    override suspend fun storageFootprintBytes(): Long =
        database.read { it.attachmentDao.totalSize() } + vault.sizeBytes()
}
