package com.solerunner.myhealth.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.Period

/** The single local health identity. A Google sign-in only fills in [accountId]/[email]/[photoUrl];
 *  everything else is entered by the user and never leaves the device. */
data class Profile(
    val id: Long = 0L,
    val accountId: String? = null,
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val authProvider: AuthProvider = AuthProvider.NONE,
    val dateOfBirth: LocalDate? = null,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCm: Double? = null,
    val bloodGroup: String? = null,
    val allergies: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val targetWeightKg: Double? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    val ageYears: Int?
        get() = dateOfBirth?.let { Period.between(it, LocalDate.now()).years }

    val initials: String
        get() = displayName.trim().split(' ').filter { it.isNotBlank() }.take(2)
            .map { it.first().uppercaseChar() }.joinToString("").ifBlank { "ME" }
}

/** One uploaded document (lab report, bill, scan…). Metadata only: the bytes live in the vault. */
data class Report(
    val id: Long = 0L,
    val kind: ReportKind = ReportKind.LAB,
    val title: String = "",
    val labName: String? = null,
    val doctorName: String? = null,
    val facilityName: String? = null,
    val sampleDate: LocalDate,
    val reportedDate: LocalDate? = null,
    val importedAt: Instant = Instant.now(),
    val fileName: String? = null,
    val mimeType: String? = null,
    val pageCount: Int = 1,
    val ocrConfidence: Double? = null,
    val source: DataSource = DataSource.REPORT_IMPORT,
    val notes: String = "",
    val fingerprint: String? = null,
    val createdAt: Instant = Instant.now(),
) {
    val effectiveDate: LocalDate get() = sampleDate
}

/** A single analyte line of a report, kept in both printed and canonical units for traceability. */
data class LabResult(
    val id: Long = 0L,
    val reportId: Long,
    val analyteKey: String,
    val displayName: String,
    val category: AnalyteCategory = AnalyteCategory.OTHER,
    val value: Double? = null,
    val unit: String? = null,
    val canonicalValue: Double? = null,
    val canonicalUnit: String? = null,
    val refLow: Double? = null,
    val refHigh: Double? = null,
    val refText: String? = null,
    val status: ValueStatus = ValueStatus.UNKNOWN,
    val printedLine: String? = null,
    val confidence: Double = 1.0,
    val userEdited: Boolean = false,
)

/** A vital the user logged by hand, read from a report, or synced from a device. */
data class Measurement(
    val id: Long = 0L,
    val kind: MeasurementKind,
    val primaryValue: Double,
    val secondaryValue: Double? = null,
    val unit: String? = null,
    val recordedAt: Instant,
    val recordedDay: LocalDate,
    val source: DataSource = DataSource.MANUAL,
    val note: String = "",
    val reportId: Long? = null,
    val customLabel: String? = null,
)

data class Medicine(
    val id: Long = 0L,
    val name: String,
    val molecule: String? = null,
    val form: MedicineForm = MedicineForm.TABLET,
    val strength: String? = null,
    val frequencyText: String? = null,
    val timesPerDay: Int? = null,
    val durationDays: Int? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val prescribedBy: String? = null,
    val facility: String? = null,
    val reason: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderTimes: List<String> = emptyList(),
    val source: DataSource = DataSource.MANUAL,
    val notes: String = "",
    val createdAt: Instant = Instant.now(),
) {
    /** A course is active until its end date passes; courses without an end date stay active. */
    val isActive: Boolean
        get() = endDate == null || !endDate.isBefore(LocalDate.now())
}

/** Surgeries, procedures, vaccinations, therapies — anything that is not a pill. */
data class Treatment(
    val id: Long = 0L,
    val name: String,
    val kind: TreatmentKind = TreatmentKind.PROCEDURE,
    val date: LocalDate? = null,
    val facility: String? = null,
    val doctor: String? = null,
    val reason: String? = null,
    val outcome: String? = null,
    val followUpDate: LocalDate? = null,
    val notes: String = "",
    val createdAt: Instant = Instant.now(),
)

/** One day of wearable data mirrored from Health Connect (Fitbit / Google Health / phone sensors). */
data class DailyMetric(
    val day: LocalDate,
    val sleepMinutes: Int? = null,
    val deepSleepMinutes: Int? = null,
    val remSleepMinutes: Int? = null,
    val steps: Int? = null,
    val restingHeartRate: Double? = null,
    val averageHeartRate: Double? = null,
    val maxHeartRate: Double? = null,
    val hrvRmssd: Double? = null,
    val spo2Average: Double? = null,
    val weightKg: Double? = null,
    val activeCalories: Double? = null,
    val exerciseMinutes: Int? = null,
    val provider: String? = null,
    val syncedAt: Instant = Instant.now(),
) {
    val hasSleep: Boolean get() = sleepMinutes != null
    val hasActivity: Boolean get() = steps != null || exerciseMinutes != null
}

/** A file that belongs to a report/medicine, stored encrypted in the app vault. */
data class Attachment(
    val id: Long = 0L,
    val reportId: Long? = null,
    val medicineId: Long? = null,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val storedName: String,
    val pageIndex: Int = 0,
    val createdAt: Instant = Instant.now(),
)

/** Tamper-evident local log of sensitive actions (unlock, export, wipe…). */
data class AuditEvent(
    val id: Long = 0L,
    val at: Instant,
    val action: String,
    val detail: String = "",
)
