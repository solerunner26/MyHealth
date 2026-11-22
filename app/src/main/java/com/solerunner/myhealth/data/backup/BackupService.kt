package com.solerunner.myhealth.data.backup

import com.solerunner.myhealth.core.security.VaultFileStore
import com.solerunner.myhealth.data.db.MyHealthDatabase
import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.Attachment
import com.solerunner.myhealth.domain.model.AuthProvider
import com.solerunner.myhealth.domain.model.DailyMetric
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.MedicineForm
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.domain.model.Report
import com.solerunner.myhealth.domain.model.ReportKind
import com.solerunner.myhealth.domain.model.Sex
import com.solerunner.myhealth.domain.model.Treatment
import com.solerunner.myhealth.domain.model.TreatmentKind
import com.solerunner.myhealth.domain.model.ValueStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

/**
 * Encrypted, portable backup of the whole record. Restoring replaces health data but keeps the PIN,
 * theme and language, because those are device preferences rather than records.
 */
class BackupService(
    private val database: MyHealthDatabase,
    private val vault: VaultFileStore,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(passphrase: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val bundle = database.read { db ->
            val reports = db.reportDao.all()
            BackupBundle(
                createdAt = Instant.now().toString(),
                profile = db.profileDao.current()?.toDto(),
                reports = reports.map { it.toDto() },
                results = reports.flatMap { report ->
                    db.labResultDao.forReport(report.id).map { report.id to it }
                }.map { (reportId, result) -> result.toDto(reportId) },
                measurements = db.measurementDao.all().map { it.toDto() },
                medicines = db.medicineDao.all().map { it.toDto() },
                treatments = db.treatmentDao.all().map { it.toDto() },
                daily = db.dailyMetricDao.recent(100_000).map { it.toDto() },
                attachments = db.attachmentDao.all().map { attachment ->
                    attachment.toDto(Base64.getEncoder().encodeToString(vault.readAllBytes(attachment.storedName)))
                },
            )
        }
        BackupCodec.encrypt(json.encodeToString(BackupBundle.serializer(), bundle).toByteArray(), passphrase)
    }

    /** Decrypts and replaces the record. Throws when the passphrase or the file is wrong. */
    suspend fun restore(payload: ByteArray, passphrase: CharArray): Int = withContext(Dispatchers.IO) {
        val plain = BackupCodec.decrypt(payload, passphrase)
        val bundle = json.decodeFromString(BackupBundle.serializer(), String(plain, Charsets.UTF_8))

        vault.clear()
        val stored = bundle.attachments.map { dto ->
            dto to vault.store(Base64.getDecoder().decode(dto.bytesBase64).inputStream())
        }

        database.write { db ->
            listOf("attachment", "lab_result", "report", "measurement", "medicine", "treatment", "daily_metric", "profile")
                .forEach { table -> db.exec("DELETE FROM $table") }

            bundle.profile?.let { db.profileDao.save(it.toModel()) }

            val reportIds = mutableMapOf<Long, Long>()
            bundle.reports.forEach { dto ->
                val newId = db.reportDao.insert(dto.toModel())
                reportIds[dto.id] = newId
            }
            bundle.results.groupBy { it.reportId }.forEach { (oldReportId, rows) ->
                val newReportId = reportIds[oldReportId] ?: return@forEach
                db.labResultDao.insertAll(newReportId, rows.map { it.toModel(newReportId) })
            }
            bundle.measurements.forEach { db.measurementDao.insert(it.toModel(reportIds)) }
            bundle.medicines.forEach { dto -> db.medicineDao.insert(dto.toModel()) }
            bundle.treatments.forEach { db.treatmentDao.insert(it.toModel()) }
            bundle.daily.forEach { db.dailyMetricDao.upsert(it.toModel()) }
            stored.forEach { (dto, blob) ->
                db.attachmentDao.insert(
                    Attachment(
                        reportId = dto.reportId?.let { reportIds[it] },
                        fileName = dto.fileName,
                        mimeType = dto.mimeType,
                        sizeBytes = blob.sizeBytes,
                        sha256 = blob.sha256,
                        storedName = blob.storedName,
                        pageIndex = dto.pageIndex,
                        createdAt = Instant.now(),
                    ),
                )
            }
        }
        bundle.reports.size
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    (fallback::class.java.enumConstants as? Array<T>)?.firstOrNull { it.name == name } ?: fallback

private fun Profile.toDto() = ProfileDto(
    displayName = displayName,
    email = email,
    dateOfBirth = dateOfBirth?.toString(),
    sex = sex.name,
    heightCm = heightCm,
    bloodGroup = bloodGroup,
    allergies = allergies,
    conditions = conditions,
    emergencyContactName = emergencyContactName,
    emergencyContactPhone = emergencyContactPhone,
    targetWeightKg = targetWeightKg,
)

private fun ProfileDto.toModel() = Profile(
    displayName = displayName,
    email = email,
    dateOfBirth = dateOfBirth?.let(LocalDate::parse),
    sex = enumOrDefault(sex, Sex.UNSPECIFIED),
    heightCm = heightCm,
    bloodGroup = bloodGroup,
    allergies = allergies,
    conditions = conditions,
    emergencyContactName = emergencyContactName,
    emergencyContactPhone = emergencyContactPhone,
    targetWeightKg = targetWeightKg,
    authProvider = AuthProvider.NONE,
    createdAt = Instant.now(),
    updatedAt = Instant.now(),
)

private fun Report.toDto() = ReportDto(
    id = id,
    kind = kind.name,
    title = title,
    labName = labName,
    doctorName = doctorName,
    facilityName = facilityName,
    sampleDate = sampleDate.toString(),
    reportedDate = reportedDate?.toString(),
    importedAt = importedAt.toString(),
    fileName = fileName,
    mimeType = mimeType,
    pageCount = pageCount,
    ocrConfidence = ocrConfidence,
    source = source.name,
    notes = notes,
    fingerprint = fingerprint,
    createdAt = createdAt.toString(),
)

private fun ReportDto.toModel() = Report(
    kind = enumOrDefault(kind, ReportKind.LAB),
    title = title,
    labName = labName,
    doctorName = doctorName,
    facilityName = facilityName,
    sampleDate = LocalDate.parse(sampleDate),
    reportedDate = reportedDate?.let(LocalDate::parse),
    importedAt = Instant.parse(importedAt),
    fileName = fileName,
    mimeType = mimeType,
    pageCount = pageCount,
    ocrConfidence = ocrConfidence,
    source = enumOrDefault(source, DataSource.REPORT_IMPORT),
    notes = notes,
    fingerprint = fingerprint,
    createdAt = Instant.parse(createdAt),
)

private fun LabResult.toDto(reportId: Long) = ResultDto(
    reportId = reportId,
    analyteKey = analyteKey,
    displayName = displayName,
    category = category.name,
    value = value,
    unit = unit,
    canonicalValue = canonicalValue,
    canonicalUnit = canonicalUnit,
    refLow = refLow,
    refHigh = refHigh,
    refText = refText,
    status = status.name,
    printedLine = printedLine,
    confidence = confidence,
    userEdited = userEdited,
)

private fun ResultDto.toModel(reportId: Long) = LabResult(
    reportId = reportId,
    analyteKey = analyteKey,
    displayName = displayName,
    category = enumOrDefault(category, AnalyteCategory.OTHER),
    value = value,
    unit = unit,
    canonicalValue = canonicalValue,
    canonicalUnit = canonicalUnit,
    refLow = refLow,
    refHigh = refHigh,
    refText = refText,
    status = enumOrDefault(status, ValueStatus.UNKNOWN),
    printedLine = printedLine,
    confidence = confidence,
    userEdited = userEdited,
)

private fun Measurement.toDto() = MeasurementDto(
    kind = kind.name,
    primaryValue = primaryValue,
    secondaryValue = secondaryValue,
    unit = unit,
    recordedAt = recordedAt.toString(),
    recordedDay = recordedDay.toString(),
    source = source.name,
    note = note,
    reportId = reportId,
    customLabel = customLabel,
)

private fun MeasurementDto.toModel(reportIds: Map<Long, Long>) = Measurement(
    kind = enumOrDefault(kind, MeasurementKind.CUSTOM),
    primaryValue = primaryValue,
    secondaryValue = secondaryValue,
    unit = unit,
    recordedAt = Instant.parse(recordedAt),
    recordedDay = LocalDate.parse(recordedDay),
    source = enumOrDefault(source, DataSource.MANUAL),
    note = note,
    reportId = reportId?.let { reportIds[it] },
    customLabel = customLabel,
)

private fun Medicine.toDto() = MedicineDto(
    name = name,
    molecule = molecule,
    form = form.name,
    strength = strength,
    frequencyText = frequencyText,
    timesPerDay = timesPerDay,
    durationDays = durationDays,
    startDate = startDate?.toString(),
    endDate = endDate?.toString(),
    prescribedBy = prescribedBy,
    facility = facility,
    reason = reason,
    reminderEnabled = reminderEnabled,
    reminderTimes = reminderTimes,
    source = source.name,
    notes = notes,
    createdAt = createdAt.toString(),
)

private fun MedicineDto.toModel() = Medicine(
    name = name,
    molecule = molecule,
    form = enumOrDefault(form, MedicineForm.TABLET),
    strength = strength,
    frequencyText = frequencyText,
    timesPerDay = timesPerDay,
    durationDays = durationDays,
    startDate = startDate?.let(LocalDate::parse),
    endDate = endDate?.let(LocalDate::parse),
    prescribedBy = prescribedBy,
    facility = facility,
    reason = reason,
    reminderEnabled = reminderEnabled,
    reminderTimes = reminderTimes,
    source = enumOrDefault(source, DataSource.MANUAL),
    notes = notes,
    createdAt = Instant.parse(createdAt),
)

private fun Treatment.toDto() = TreatmentDto(
    name = name,
    kind = kind.name,
    date = date?.toString(),
    facility = facility,
    doctor = doctor,
    reason = reason,
    outcome = outcome,
    followUpDate = followUpDate?.toString(),
    notes = notes,
    createdAt = createdAt.toString(),
)

private fun TreatmentDto.toModel() = Treatment(
    name = name,
    kind = enumOrDefault(kind, TreatmentKind.PROCEDURE),
    date = date?.let(LocalDate::parse),
    facility = facility,
    doctor = doctor,
    reason = reason,
    outcome = outcome,
    followUpDate = followUpDate?.let(LocalDate::parse),
    notes = notes,
    createdAt = Instant.parse(createdAt),
)

private fun DailyMetric.toDto() = DailyDto(
    day = day.toString(),
    sleepMinutes = sleepMinutes,
    deepSleepMinutes = deepSleepMinutes,
    remSleepMinutes = remSleepMinutes,
    steps = steps,
    restingHeartRate = restingHeartRate,
    averageHeartRate = averageHeartRate,
    maxHeartRate = maxHeartRate,
    hrvRmssd = hrvRmssd,
    spo2Average = spo2Average,
    weightKg = weightKg,
    activeCalories = activeCalories,
    exerciseMinutes = exerciseMinutes,
    provider = provider,
    syncedAt = syncedAt.toString(),
)

private fun DailyDto.toModel() = DailyMetric(
    day = LocalDate.parse(day),
    sleepMinutes = sleepMinutes,
    deepSleepMinutes = deepSleepMinutes,
    remSleepMinutes = remSleepMinutes,
    steps = steps,
    restingHeartRate = restingHeartRate,
    averageHeartRate = averageHeartRate,
    maxHeartRate = maxHeartRate,
    hrvRmssd = hrvRmssd,
    spo2Average = spo2Average,
    weightKg = weightKg,
    activeCalories = activeCalories,
    exerciseMinutes = exerciseMinutes,
    provider = provider,
    syncedAt = Instant.parse(syncedAt),
)

private fun Attachment.toDto(bytesBase64: String) = AttachmentDto(
    reportId = reportId,
    medicineId = medicineId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    pageIndex = pageIndex,
    createdAt = createdAt.toString(),
    bytesBase64 = bytesBase64,
)
