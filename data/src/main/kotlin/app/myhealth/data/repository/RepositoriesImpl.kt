package app.myhealth.data.repository

import app.myhealth.data.db.ActivityDao
import app.myhealth.data.db.LabResultDao
import app.myhealth.data.db.MedicineDao
import app.myhealth.data.db.MemberDao
import app.myhealth.data.db.MetaDao
import app.myhealth.data.db.MyHealthDatabase
import app.myhealth.data.db.ReportDao
import app.myhealth.data.db.TreatmentDao
import app.myhealth.data.db.VitalDao
import app.myhealth.data.vault.EncryptedVault
import app.myhealth.domain.model.DailyActivity
import app.myhealth.domain.model.DoseLog
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.Treatment
import app.myhealth.domain.model.Vital
import app.myhealth.domain.repository.ActivityRepository
import app.myhealth.domain.repository.DuplicateHit
import app.myhealth.domain.repository.ImportBundle
import app.myhealth.domain.repository.MedicineRepository
import app.myhealth.domain.repository.MemberRepository
import app.myhealth.domain.repository.NewAttachment
import app.myhealth.domain.repository.RecordRepository
import app.myhealth.domain.repository.TreatmentRepository
import app.myhealth.domain.repository.VitalRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

private val RECORD_TABLES = setOf(ReportDao.T, ReportDao.A, LabResultDao.T, VitalDao.T, MedicineDao.T)

class MemberRepositoryImpl(private val db: MyHealthDatabase, private val vault: EncryptedVault) : MemberRepository {
    override fun observeAll(): Flow<List<FamilyMember>> = db.observe(setOf(MemberDao.T)) { MemberDao.all(it) }
    override suspend fun all() = db.read { MemberDao.all(it) }
    override suspend fun get(id: String) = db.read { MemberDao.get(it, id) }
    override suspend fun upsert(member: FamilyMember) = db.write(setOf(MemberDao.T)) { MemberDao.upsert(it, member) }
    override suspend fun delete(id: String) {
        val files = db.read { ReportDao.attachmentIdsForMember(it, id) }
        db.write(RECORD_TABLES + setOf(MemberDao.T, TreatmentDao.T, ActivityDao.T, MedicineDao.D)) { MemberDao.delete(it, id) }
        files.forEach { runCatching { vault.delete(it) } }
    }
}

class RecordRepositoryImpl(private val db: MyHealthDatabase, private val vault: EncryptedVault) : RecordRepository {
    override fun observeReports(memberId: String) = db.observe(setOf(ReportDao.T, ReportDao.A)) { ReportDao.forMember(it, memberId) }
    override suspend fun report(id: String) = db.read { ReportDao.get(it, id) }
    override fun observeResults(memberId: String) = db.observe(setOf(LabResultDao.T)) { LabResultDao.forMember(it, memberId) }
    override suspend fun results(memberId: String) = db.read { LabResultDao.forMember(it, memberId) }
    override suspend fun resultsForReport(reportId: String) = db.read { LabResultDao.forReport(it, reportId) }
    override suspend fun conflicting(memberId: String, keys: List<Pair<String, LocalDate>>) =
        db.read { d -> keys.mapNotNull { (a, date) -> LabResultDao.byKey(d, memberId, a, date) } }

    override suspend fun findBySha256(sha256: String): DuplicateHit? = db.read { d ->
        ReportDao.bySha(d, sha256)?.let { a -> ReportDao.get(d, a.reportId)?.let { DuplicateHit(a, it) } }
    }

    override suspend fun saveImport(bundle: ImportBundle, files: List<NewAttachment>) {
        // 1) encrypted files first; 2) all rows in one transaction; 3) on DB failure remove the files again.
        val written = mutableListOf<String>()
        try {
            files.forEach { vault.put(it.attachment.id, it.bytes); written += it.attachment.id }
            db.write(RECORD_TABLES) { d ->
                ReportDao.upsert(d, bundle.report)
                files.forEach { ReportDao.insertAttachment(d, it.attachment) }
                bundle.results.forEach { LabResultDao.upsert(d, it) }
                bundle.vitals.forEach { VitalDao.upsert(d, it) }
                bundle.medicines.forEach { MedicineDao.upsert(d, it) }
            }
        } catch (e: Exception) {
            written.forEach { runCatching { vault.delete(it) } }
            throw e
        }
    }

    override suspend fun updateReport(report: Report) = db.write(setOf(ReportDao.T)) { ReportDao.upsert(it, report) }
    override suspend fun upsertResult(result: LabResult) = db.write(setOf(LabResultDao.T)) { LabResultDao.upsert(it, result) }
    override suspend fun deleteResult(id: String) { db.write(setOf(LabResultDao.T)) { LabResultDao.delete(it, id) } }

    override suspend fun deleteReport(id: String) {
        val files = db.read { ReportDao.attachmentIdsForReport(it, id) }
        // Results extracted from the report are deleted with it (they would otherwise lose their evidence).
        db.write(RECORD_TABLES) { d ->
            d.delete(LabResultDao.T, "report_id = ?", arrayOf(id))
            d.delete(VitalDao.T, "report_id = ?", arrayOf(id))
            ReportDao.delete(d, id)
        }
        files.forEach { runCatching { vault.delete(it) } }
    }

    override suspend fun attachmentBytes(attachmentId: String): ByteArray = vault.get(attachmentId)
}

class VitalRepositoryImpl(private val db: MyHealthDatabase) : VitalRepository {
    override fun observe(memberId: String) = db.observe(setOf(VitalDao.T)) { VitalDao.forMember(it, memberId) }
    override suspend fun all(memberId: String) = db.read { VitalDao.forMember(it, memberId) }
    override suspend fun upsert(vital: Vital) = db.write(setOf(VitalDao.T)) { VitalDao.upsert(it, vital) }
    override suspend fun delete(id: String) { db.write(setOf(VitalDao.T)) { VitalDao.delete(it, id) } }
}

class MedicineRepositoryImpl(private val db: MyHealthDatabase) : MedicineRepository {
    override fun observe(memberId: String) = db.observe(setOf(MedicineDao.T)) { MedicineDao.forMember(it, memberId) }
    override suspend fun all(memberId: String) = db.read { MedicineDao.forMember(it, memberId) }
    override suspend fun allMembersActive(day: LocalDate) = db.read { MedicineDao.all(it) }.filter { it.isActiveOn(day) }
    override suspend fun get(id: String) = db.read { MedicineDao.get(it, id) }
    override suspend fun upsert(medicine: Medicine) = db.write(setOf(MedicineDao.T)) { MedicineDao.upsert(it, medicine) }
    override suspend fun delete(id: String) { db.write(setOf(MedicineDao.T, MedicineDao.D)) { MedicineDao.delete(it, id) } }
    override suspend fun logDose(log: DoseLog) = db.write(setOf(MedicineDao.D)) { MedicineDao.logDose(it, log) }
    override fun observeDoseLogs(memberId: String, since: Instant) = db.observe(setOf(MedicineDao.D)) { MedicineDao.doseLogs(it, memberId, since) }
    override suspend fun doseLogs(memberId: String, since: Instant) = db.read { MedicineDao.doseLogs(it, memberId, since) }
}

class TreatmentRepositoryImpl(private val db: MyHealthDatabase) : TreatmentRepository {
    override fun observe(memberId: String) = db.observe(setOf(TreatmentDao.T)) { TreatmentDao.forMember(it, memberId) }
    override suspend fun all(memberId: String) = db.read { TreatmentDao.forMember(it, memberId) }
    override suspend fun upsert(treatment: Treatment) = db.write(setOf(TreatmentDao.T)) { TreatmentDao.upsert(it, treatment) }
    override suspend fun delete(id: String) { db.write(setOf(TreatmentDao.T)) { TreatmentDao.delete(it, id) } }
}

class ActivityRepositoryImpl(private val db: MyHealthDatabase) : ActivityRepository {
    override fun observe(memberId: String) = db.observe(setOf(ActivityDao.T)) { ActivityDao.forMember(it, memberId) }
    override suspend fun all(memberId: String) = db.read { ActivityDao.forMember(it, memberId) }
    override suspend fun upsertAll(days: List<DailyActivity>) = db.write(setOf(ActivityDao.T)) { d -> days.forEach { ActivityDao.upsert(d, it) } }
    override suspend fun syncToken(memberId: String) = db.read { MetaDao.get(it, "hc.token.$memberId") }
    override suspend fun setSyncToken(memberId: String, token: String?) = db.write(setOf(MetaDao.T)) { MetaDao.set(it, "hc.token.$memberId", token) }
    override suspend fun clear(memberId: String) = db.write(setOf(ActivityDao.T, MetaDao.T)) { ActivityDao.clear(it, memberId); MetaDao.set(it, "hc.token.$memberId", null) }
}
