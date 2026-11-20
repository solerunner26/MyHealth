package com.solerunner.myhealth.data.db

import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.Attachment
import com.solerunner.myhealth.domain.model.AuthProvider
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.domain.model.Sex
import com.solerunner.myhealth.domain.model.ValueStatus
import java.time.Instant
import java.time.LocalDate

/** Shared enum/list helpers: names go in and come out, unknown values fall back instead of throwing. */
internal inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
    if (value.isNullOrBlank()) fallback else runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

internal fun List<String>.joinList(): String = joinToString("|") { it.trim() }

internal fun String?.splitList(): List<String> =
    this?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

class ProfileDao(private val db: Db) {

    fun current(): Profile? = db.query("SELECT * FROM profile ORDER BY id LIMIT 1") { it.toProfile() }.firstOrNull()

    fun byAccount(accountId: String): Profile? =
        db.query("SELECT * FROM profile WHERE account_id = ? LIMIT 1", listOf(accountId)) { it.toProfile() }.firstOrNull()

    fun save(profile: Profile): Long {
        val values = listOf<Any?>(
            profile.accountId, profile.displayName, profile.email, profile.photoUrl, profile.authProvider.name,
            profile.dateOfBirth?.toEpochDay(), profile.sex.name, profile.heightCm, profile.bloodGroup,
            profile.allergies.joinList(), profile.conditions.joinList(), profile.emergencyContactName,
            profile.emergencyContactPhone, profile.targetWeightKg, profile.createdAt.epochSecond,
            Instant.now().epochSecond,
        )
        return if (profile.id == 0L) {
            db.insert(
                "INSERT INTO profile (account_id, display_name, email, photo_url, auth_provider, dob_epoch_day, sex, height_cm, " +
                    "blood_group, allergies, conditions, emergency_contact_name, emergency_contact_phone, target_weight_kg, " +
                    "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                values,
            )
        } else {
            db.update(
                "UPDATE profile SET account_id=?, display_name=?, email=?, photo_url=?, auth_provider=?, dob_epoch_day=?, " +
                    "sex=?, height_cm=?, blood_group=?, allergies=?, conditions=?, emergency_contact_name=?, " +
                    "emergency_contact_phone=?, target_weight_kg=?, created_at=?, updated_at=? WHERE id=?",
                values + profile.id,
            )
            profile.id
        }
    }

    fun clear() = db.exec("DELETE FROM profile")

    private fun Row.toProfile() = Profile(
        id = long(0),
        accountId = stringOrNull(1),
        displayName = string(2),
        email = stringOrNull(3),
        photoUrl = stringOrNull(4),
        authProvider = enumOr(string(5), AuthProvider.NONE),
        dateOfBirth = longOrNull(6)?.let { LocalDate.ofEpochDay(it) },
        sex = enumOr(string(7), Sex.UNSPECIFIED),
        heightCm = doubleOrNull(8),
        bloodGroup = stringOrNull(9),
        allergies = string(10).splitList(),
        conditions = string(11).splitList(),
        emergencyContactName = stringOrNull(12),
        emergencyContactPhone = stringOrNull(13),
        targetWeightKg = doubleOrNull(14),
        createdAt = Instant.ofEpochSecond(long(15)),
        updatedAt = Instant.ofEpochSecond(long(16)),
    )
}

class ReportDao(private val db: Db) {

    fun all(): List<Report> = db.query("SELECT * FROM report ORDER BY sample_date DESC, id DESC") { it.toReport() }

    fun recent(limit: Int): List<Report> =
        db.query("SELECT * FROM report ORDER BY sample_date DESC, id DESC LIMIT ?", listOf(limit)) { it.toReport() }

    fun byId(id: Long): Report? =
        db.query("SELECT * FROM report WHERE id = ?", listOf(id)) { it.toReport() }.firstOrNull()

    fun latest(): Report? =
        db.query("SELECT * FROM report ORDER BY sample_date DESC, id DESC LIMIT 1") { it.toReport() }.firstOrNull()

    fun count(): Int = db.query("SELECT COUNT(*) FROM report") { it.int(0) }.firstOrNull() ?: 0

    fun findByFingerprint(fingerprint: String): Report? =
        db.query("SELECT * FROM report WHERE fingerprint = ? LIMIT 1", listOf(fingerprint)) { it.toReport() }.firstOrNull()

    fun rawText(reportId: Long): String? =
        db.query("SELECT raw_text FROM report WHERE id = ?", listOf(reportId)) { it.stringOrNull(0) }.firstOrNull()

    fun insert(report: Report): Long = db.insert(
        "INSERT INTO report (kind, title, lab_name, doctor_name, facility_name, sample_date, reported_date, imported_at, " +
            "file_name, mime_type, page_count, ocr_confidence, source, notes, fingerprint, raw_text, created_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        report.toValues(),
    )

    fun update(report: Report) = db.update(
        "UPDATE report SET kind=?, title=?, lab_name=?, doctor_name=?, facility_name=?, sample_date=?, reported_date=?, " +
            "imported_at=?, file_name=?, mime_type=?, page_count=?, ocr_confidence=?, source=?, notes=?, fingerprint=?, " +
            "raw_text=?, created_at=? WHERE id=?",
        report.toValues() + report.id,
    )

    fun saveRawText(reportId: Long, rawText: String) =
        db.update("UPDATE report SET raw_text = ? WHERE id = ?", listOf(rawText, reportId))

    fun delete(id: Long) = db.update("DELETE FROM report WHERE id = ?", listOf(id))

    fun deleteAll() = db.exec("DELETE FROM report")

    private fun Report.toValues(): List<Any?> = listOf(
        kind.name, title, labName, doctorName, facilityName, sampleDate.toEpochDay(),
        reportedDate?.toEpochDay(), importedAt.epochSecond, fileName, mimeType, pageCount, ocrConfidence,
        source.name, notes, fingerprint, null as String?, createdAt.epochSecond,
    )

    private fun Row.toReport() = Report(
        id = long(0),
        kind = enumOr(string(1), ReportKind.LAB),
        title = string(2),
        labName = stringOrNull(3),
        doctorName = stringOrNull(4),
        facilityName = stringOrNull(5),
        sampleDate = LocalDate.ofEpochDay(long(6)),
        reportedDate = longOrNull(7)?.let { LocalDate.ofEpochDay(it) },
        importedAt = Instant.ofEpochSecond(long(8)),
        fileName = stringOrNull(9),
        mimeType = stringOrNull(10),
        pageCount = int(11),
        ocrConfidence = doubleOrNull(12),
        source = enumOr(string(13), DataSource.REPORT_IMPORT),
        notes = string(14),
        fingerprint = stringOrNull(15),
        createdAt = Instant.ofEpochSecond(long(17)),
    )
}
