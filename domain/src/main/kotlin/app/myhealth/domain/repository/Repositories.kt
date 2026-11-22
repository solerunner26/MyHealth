package app.myhealth.domain.repository

import app.myhealth.domain.model.Attachment
import app.myhealth.domain.model.DailyActivity
import app.myhealth.domain.model.DoseLog
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.Treatment
import app.myhealth.domain.model.Vital
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface MemberRepository {
    fun observeAll(): Flow<List<FamilyMember>>
    suspend fun all(): List<FamilyMember>
    suspend fun get(id: String): FamilyMember?
    suspend fun upsert(member: FamilyMember)
    /** Deletes the member and, by cascade, every record that belongs to them. */
    suspend fun delete(id: String)
}

/** A file to store alongside a report; bytes are encrypted by the vault before touching disk. */
class NewAttachment(val attachment: Attachment, val bytes: ByteArray)

/** Everything confirmed on the review screen, saved atomically. */
data class ImportBundle(
    val report: Report,
    val results: List<LabResult>,
    val vitals: List<Vital>,
    val medicines: List<Medicine>,
)

data class DuplicateHit(val attachment: Attachment, val report: Report)

interface RecordRepository {
    fun observeReports(memberId: String): Flow<List<Report>>
    suspend fun report(id: String): Report?
    fun observeResults(memberId: String): Flow<List<LabResult>>
    suspend fun results(memberId: String): List<LabResult>
    suspend fun resultsForReport(reportId: String): List<LabResult>
    /** Existing results that a new result on the same (member, analyte, date) would replace. */
    suspend fun conflicting(memberId: String, keys: List<Pair<String, LocalDate>>): List<LabResult>
    suspend fun findBySha256(sha256: String): DuplicateHit?
    /** Stores attachments (encrypted) and all rows in one transaction; on failure nothing is kept. */
    suspend fun saveImport(bundle: ImportBundle, files: List<NewAttachment>)
    suspend fun updateReport(report: Report)
    suspend fun upsertResult(result: LabResult)
    suspend fun deleteResult(id: String)
    suspend fun deleteReport(id: String)
    /** Decrypted bytes of an attachment, for the viewer and share sheet. */
    suspend fun attachmentBytes(attachmentId: String): ByteArray
}

interface VitalRepository {
    fun observe(memberId: String): Flow<List<Vital>>
    suspend fun all(memberId: String): List<Vital>
    suspend fun upsert(vital: Vital)
    suspend fun delete(id: String)
}

interface MedicineRepository {
    fun observe(memberId: String): Flow<List<Medicine>>
    suspend fun all(memberId: String): List<Medicine>
    suspend fun allMembersActive(day: LocalDate): List<Medicine>
    suspend fun get(id: String): Medicine?
    suspend fun upsert(medicine: Medicine)
    suspend fun delete(id: String)
    suspend fun logDose(log: DoseLog)
    fun observeDoseLogs(memberId: String, since: Instant): Flow<List<DoseLog>>
    suspend fun doseLogs(memberId: String, since: Instant): List<DoseLog>
}

interface TreatmentRepository {
    fun observe(memberId: String): Flow<List<Treatment>>
    suspend fun all(memberId: String): List<Treatment>
    suspend fun upsert(treatment: Treatment)
    suspend fun delete(id: String)
}

interface ActivityRepository {
    fun observe(memberId: String): Flow<List<DailyActivity>>
    suspend fun all(memberId: String): List<DailyActivity>
    suspend fun upsertAll(days: List<DailyActivity>)
    suspend fun syncToken(memberId: String): String?
    suspend fun setSyncToken(memberId: String, token: String?)
    suspend fun clear(memberId: String)
}
