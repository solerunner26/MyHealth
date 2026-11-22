package com.solerunner.myhealth.data.import

import android.net.Uri
import com.solerunner.myhealth.core.security.VaultFileStore
import com.solerunner.myhealth.core.util.Dates
import com.solerunner.myhealth.data.db.MyHealthDatabase
import com.solerunner.myhealth.domain.lab.LabTextParser
import com.solerunner.myhealth.domain.lab.ParsedAnalyteRow
import com.solerunner.myhealth.domain.medicine.MedicineBillParser
import com.solerunner.myhealth.domain.model.Attachment
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.domain.model.Sex
import com.solerunner.myhealth.domain.repository.AuditRepository
import com.solerunner.myhealth.domain.repository.MedicineRepository
import com.solerunner.myhealth.domain.repository.ProfileRepository
import com.solerunner.myhealth.domain.repository.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/**
 * Orchestrates one import: read (text layer or OCR) → parse (lab report, vitals, medicines) → save.
 *
 * The service is stateless so the import ViewModel holds a single draft and the user reviews every
 * value before anything is persisted. Re-importing the same file is detected by the SHA-256 of the
 * original bytes and surfaced as [ImportDraft.duplicateOfReportId].
 */
class ImportService(
    private val database: MyHealthDatabase,
    private val reader: DocumentReader,
    private val vault: VaultFileStore,
    private val reports: ReportRepository,
    private val medicines: MedicineRepository,
    private val profiles: ProfileRepository,
    private val audit: AuditRepository,
) {

    suspend fun read(uri: Uri, displayName: String?, mimeType: String?): ImportDraft = withContext(Dispatchers.IO) {
        val profile = profiles.current()
        val document = reader.read(uri, displayName, mimeType)
        val parsed = LabTextParser.parse(
            text = document.text,
            fileName = document.fileName,
            sex = profile?.sex ?: Sex.UNSPECIFIED,
            ageYears = profile?.ageYears,
        )
        val sniffed = ImportDraft.sniffKind(document.text)
        val parsedMedicines = if (sniffed == ReportKind.PHARMACY_BILL || sniffed == ReportKind.PRESCRIPTION) {
            MedicineBillParser.parse(document.text)
        } else {
            emptyList()
        }
        val duplicate = database.read { it.reportDao.findByFingerprint(sha256(document.bytes))?.id }
        ImportDraft(
            fileName = document.fileName,
            mimeType = document.mimeType,
            bytes = document.bytes,
            pageImages = document.pages.mapNotNull { it.image },
            pages = document.pageCount,
            usedOcr = document.usedOcr,
            document = parsed,
            medicines = parsedMedicines,
            kind = if (parsed.header.kind != ReportKind.LAB) parsed.header.kind else sniffed,
            duplicateOfReportId = duplicate,
        )
    }

    /** Default report header, pre-filled from the parser so the review screen has something to edit. */
    fun defaultReport(draft: ImportDraft): Report {
        val header = draft.document.header
        return Report(
            kind = draft.kind,
            title = header.title ?: draft.fileName.substringBeforeLast('.').ifBlank { "Imported document" },
            labName = header.labName,
            doctorName = header.doctorName,
            facilityName = header.facilityName,
            sampleDate = header.sampleDate ?: Dates.today(),
            reportedDate = header.reportDate,
            importedAt = Instant.now(),
            fileName = draft.fileName,
            mimeType = draft.mimeType,
            pageCount = draft.pages,
            ocrConfidence = draft.confidence,
            source = DataSource.REPORT_IMPORT,
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Persists a reviewed draft. Returns the new report id; selected medicines are stored with it. */
    suspend fun save(
        draft: ImportDraft,
        report: Report,
        rows: List<ParsedAnalyteRow>,
        selectedMedicines: List<Medicine> = emptyList(),
    ): Long = withContext(Dispatchers.IO) {
        val original = vault.store(draft.bytes.inputStream())
        val attachment = Attachment(
            fileName = draft.fileName,
            mimeType = draft.mimeType,
            sizeBytes = original.sizeBytes,
            sha256 = original.sha256,
            storedName = original.storedName,
            pageIndex = 0,
        )
        val results = rows.map { row ->
            LabResult(
                reportId = 0L,
                analyteKey = row.biomarkerKey ?: row.displayName.lowercase().replace(' ', '_'),
                displayName = row.displayName,
                category = row.category,
                value = row.printedValue,
                unit = row.printedUnit,
                canonicalValue = row.canonicalValue,
                canonicalUnit = row.canonicalUnit,
                refLow = row.refLow,
                refHigh = row.refHigh,
                refText = row.refText,
                status = row.status,
                printedLine = row.printedLine,
                confidence = row.confidence,
                userEdited = false,
            )
        }
        val measurements = draft.document.vitals.map { vital ->
            Measurement(
                kind = vital.kind,
                primaryValue = vital.primary,
                secondaryValue = vital.secondary,
                unit = vital.unit,
                recordedAt = report.sampleDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                recordedDay = report.sampleDate,
                source = DataSource.REPORT_IMPORT,
            )
        }
        val id = reports.save(
            report = report.copy(
                fingerprint = sha256(draft.bytes),
                pageCount = draft.pages,
                ocrConfidence = draft.confidence,
                mimeType = draft.mimeType,
                fileName = draft.fileName,
            ),
            results = results,
            measurements = measurements,
            attachment = attachment,
        )
        selectedMedicines.forEach { medicines.save(it.copy(source = DataSource.REPORT_IMPORT)) }
        audit.log("report.import", "${draft.fileName} → $id")
        id
    }
}

