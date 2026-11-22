package com.solerunner.myhealth.data.backup

import kotlinx.serialization.Serializable

/** Portable snapshot of the user's record. Every field is a primitive so the JSON stays stable. */
@Serializable
data class BackupBundle(
    val version: Int = 1,
    val createdAt: String,
    val profile: ProfileDto? = null,
    val reports: List<ReportDto> = emptyList(),
    val results: List<ResultDto> = emptyList(),
    val measurements: List<MeasurementDto> = emptyList(),
    val medicines: List<MedicineDto> = emptyList(),
    val treatments: List<TreatmentDto> = emptyList(),
    val daily: List<DailyDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
data class ProfileDto(
    val displayName: String,
    val email: String? = null,
    val dateOfBirth: String? = null,
    val sex: String = "UNSPECIFIED",
    val heightCm: Double? = null,
    val bloodGroup: String? = null,
    val allergies: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val targetWeightKg: Double? = null,
)

@Serializable
data class ReportDto(
    val id: Long,
    val kind: String,
    val title: String,
    val labName: String? = null,
    val doctorName: String? = null,
    val facilityName: String? = null,
    val sampleDate: String,
    val reportedDate: String? = null,
    val importedAt: String,
    val fileName: String? = null,
    val mimeType: String? = null,
    val pageCount: Int = 1,
    val ocrConfidence: Double? = null,
    val source: String = "REPORT_IMPORT",
    val notes: String = "",
    val fingerprint: String? = null,
    val createdAt: String,
)

@Serializable
data class ResultDto(
    val reportId: Long,
    val analyteKey: String,
    val displayName: String,
    val category: String,
    val value: Double? = null,
    val unit: String? = null,
    val canonicalValue: Double? = null,
    val canonicalUnit: String? = null,
    val refLow: Double? = null,
    val refHigh: Double? = null,
    val refText: String? = null,
    val status: String,
    val printedLine: String? = null,
    val confidence: Double = 1.0,
    val userEdited: Boolean = false,
)

@Serializable
data class MeasurementDto(
    val kind: String,
    val primaryValue: Double,
    val secondaryValue: Double? = null,
    val unit: String? = null,
    val recordedAt: String,
    val recordedDay: String,
    val source: String = "MANUAL",
    val note: String = "",
    val reportId: Long? = null,
    val customLabel: String? = null,
)

@Serializable
data class MedicineDto(
    val name: String,
    val molecule: String? = null,
    val form: String = "TABLET",
    val strength: String? = null,
    val frequencyText: String? = null,
    val timesPerDay: Int? = null,
    val durationDays: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val prescribedBy: String? = null,
    val facility: String? = null,
    val reason: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderTimes: List<String> = emptyList(),
    val source: String = "MANUAL",
    val notes: String = "",
    val createdAt: String,
)

@Serializable
data class TreatmentDto(
    val name: String,
    val kind: String = "PROCEDURE",
    val date: String? = null,
    val facility: String? = null,
    val doctor: String? = null,
    val reason: String? = null,
    val outcome: String? = null,
    val followUpDate: String? = null,
    val notes: String = "",
    val createdAt: String,
)

@Serializable
data class DailyDto(
    val day: String,
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
    val syncedAt: String,
)

@Serializable
data class AttachmentDto(
    val reportId: Long? = null,
    val medicineId: Long? = null,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val pageIndex: Int = 0,
    val createdAt: String,
    val bytesBase64: String,
)
