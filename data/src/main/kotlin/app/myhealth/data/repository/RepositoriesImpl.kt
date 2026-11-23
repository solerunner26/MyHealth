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
        val media = db.read { app.myhealth.data.db.MediaDao.forMember(it, id) }.map { MediaRepositoryImpl.vaultName(it.id) }
        db.write(RECORD_TABLES + setOf(MemberDao.T, TreatmentDao.T, ActivityDao.T, MedicineDao.D) + V3_TABLES) { MemberDao.delete(it, id) }
        (files + media).forEach { runCatching { vault.delete(it) } }
        runCatching { app.myhealth.data.PhotoStore(vault).delete(id) }
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

private val V3_TABLES = setOf(app.myhealth.data.db.VaccinationDao.T, app.myhealth.data.db.HealthCardDao.T, app.myhealth.data.db.VisitQuestionDao.T, app.myhealth.data.db.MediaDao.T,
    app.myhealth.data.db.MediclaimDao.P, app.myhealth.data.db.MediclaimDao.C, app.myhealth.data.db.MediclaimDao.H)

class VaccinationRepositoryImpl(private val db: MyHealthDatabase) : app.myhealth.domain.repository.VaccinationRepository {
    private val t = setOf(app.myhealth.data.db.VaccinationDao.T)
    override fun observe(memberId: String) = db.observe(t) { app.myhealth.data.db.VaccinationDao.forMember(it, memberId) }
    override suspend fun all(memberId: String) = db.read { app.myhealth.data.db.VaccinationDao.forMember(it, memberId) }
    override suspend fun allMembersPending() = db.read { app.myhealth.data.db.VaccinationDao.pending(it) }
    override suspend fun upsert(v: app.myhealth.domain.model.Vaccination) = db.write(t) { app.myhealth.data.db.VaccinationDao.upsert(it, v) }
    override suspend fun upsertAll(list: List<app.myhealth.domain.model.Vaccination>) = db.write(t) { d -> list.forEach { app.myhealth.data.db.VaccinationDao.upsert(d, it) } }
    override suspend fun delete(id: String) { db.write(t) { app.myhealth.data.db.VaccinationDao.delete(it, id) } }
}

class HealthCardRepositoryImpl(private val db: MyHealthDatabase) : app.myhealth.domain.repository.HealthCardRepository {
    private val t = setOf(app.myhealth.data.db.HealthCardDao.T)
    override fun observe(memberId: String) = db.observe(t) { app.myhealth.data.db.HealthCardDao.forMember(it, memberId) }
    override suspend fun upsert(card: app.myhealth.domain.model.HealthCard) = db.write(t) { app.myhealth.data.db.HealthCardDao.upsert(it, card) }
    override suspend fun delete(id: String) { db.write(t) { app.myhealth.data.db.HealthCardDao.delete(it, id) } }
}

class FamilyHistoryRepositoryImpl(private val db: MyHealthDatabase) : app.myhealth.domain.repository.FamilyHistoryRepository {
    private val t = setOf(app.myhealth.data.db.FamilyHistoryDao.T)
    override fun observe() = db.observe(t) { app.myhealth.data.db.FamilyHistoryDao.all(it) }
    override suspend fun upsert(r: app.myhealth.domain.model.RelativeCondition) = db.write(t) { app.myhealth.data.db.FamilyHistoryDao.upsert(it, r) }
    override suspend fun delete(id: String) { db.write(t) { app.myhealth.data.db.FamilyHistoryDao.delete(it, id) } }
}

class VisitQuestionRepositoryImpl(private val db: MyHealthDatabase) : app.myhealth.domain.repository.VisitQuestionRepository {
    private val t = setOf(app.myhealth.data.db.VisitQuestionDao.T)
    override fun observe(memberId: String) = db.observe(t) { app.myhealth.data.db.VisitQuestionDao.forMember(it, memberId) }
    override suspend fun upsert(q: app.myhealth.domain.model.VisitQuestion) = db.write(t) { app.myhealth.data.db.VisitQuestionDao.upsert(it, q) }
    override suspend fun delete(id: String) { db.write(t) { app.myhealth.data.db.VisitQuestionDao.delete(it, id) } }
}

/** Images for vaccinations and health cards: row in `media`, bytes AES-GCM encrypted in the vault as "media-<id>". */
class MediaRepositoryImpl(private val db: MyHealthDatabase, private val vault: EncryptedVault) : app.myhealth.domain.repository.MediaRepository {
    private val t = setOf(app.myhealth.data.db.MediaDao.T)
    override fun observe(ownerId: String) = db.observe(t) { app.myhealth.data.db.MediaDao.forOwner(it, ownerId) }
    override suspend fun add(media: app.myhealth.domain.model.Media, bytes: ByteArray) {
        require(bytes.size <= MAX_BYTES) { "image too large" }
        vault.put(vaultName(media.id), bytes)
        try { db.write(t) { app.myhealth.data.db.MediaDao.insert(it, media) } } catch (e: Exception) { runCatching { vault.delete(vaultName(media.id)) }; throw e }
    }
    override suspend fun bytes(id: String): ByteArray? = runCatching { if (vault.exists(vaultName(id))) vault.get(vaultName(id)) else null }.getOrNull()
    override suspend fun delete(id: String) { db.write(t) { app.myhealth.data.db.MediaDao.delete(it, id) }; runCatching { vault.delete(vaultName(id)) } }
    override suspend fun deleteForOwner(ownerId: String) {
        val ids = db.read { app.myhealth.data.db.MediaDao.forOwner(it, ownerId) }.map { it.id }
        db.write(t) { app.myhealth.data.db.MediaDao.deleteForOwner(it, ownerId) }
        ids.forEach { runCatching { vault.delete(vaultName(it)) } }
    }
    companion object {
        const val MAX_BYTES = 25 * 1024 * 1024
        fun vaultName(id: String): String { require(id.matches(Regex("[A-Za-z0-9-]{1,64}"))); return "media-$id" }
    }
}

class MediclaimRepositoryImpl(private val db: MyHealthDatabase) : app.myhealth.domain.repository.MediclaimRepository {
    private val d = app.myhealth.data.db.MediclaimDao
    private val all = setOf(d.P, d.C, d.H)
    override fun observe(memberId: String) = db.observe(setOf(d.P)) { d.forMember(it, memberId) }
    override fun observePolicy(id: String) = db.observe(setOf(d.P)) { d.get(it, id) }
    override suspend fun all() = db.read { d.all(it) }
    override suspend fun upsert(p: app.myhealth.domain.model.MediclaimPolicy) = db.write(setOf(d.P)) { d.upsert(it, p) }
    override suspend fun delete(id: String) { db.write(all) { d.delete(it, id) } }
    override fun observeClaims(policyId: String) = db.observe(setOf(d.C)) { d.claims(it, policyId) }
    override suspend fun upsertClaim(c: app.myhealth.domain.model.MediclaimClaim) = db.write(setOf(d.C)) { d.upsertClaim(it, c) }
    override suspend fun deleteClaim(id: String) { db.write(setOf(d.C)) { d.deleteClaim(it, id) } }
    override fun observeHospitals(policyId: String) = db.observe(setOf(d.H)) { d.hospitals(it, policyId) }
    override suspend fun addHospitals(list: List<app.myhealth.domain.model.NetworkHospital>) = db.write(setOf(d.H)) { w -> list.forEach { d.insertHospital(w, it) } }
    override suspend fun deleteHospital(id: String) { db.write(setOf(d.H)) { d.deleteHospital(it, id) } }
}
