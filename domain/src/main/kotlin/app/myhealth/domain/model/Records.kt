package app.myhealth.domain.model

import java.time.Instant
import java.time.LocalDate

enum class ReportType { LabReport, Prescription, PharmacyBill, Imaging, Discharge, Other }

enum class AttachmentKind { Pdf, Jpeg, Png }

/** Encrypted file stored in the vault; [sha256] is of the original plaintext bytes (duplicate detection). */
data class Attachment(
    val id: String,
    val reportId: String,
    val kind: AttachmentKind,
    val displayName: String,
    val sha256: String,
    val sizeBytes: Long,
    val pageCount: Int,
)

/** A medical document and the date it describes. */
data class Report(
    val id: String,
    val memberId: String,
    val type: ReportType,
    val title: String,
    val reportDate: LocalDate,
    val labName: String? = null,
    val doctorName: String? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val attachments: List<Attachment> = emptyList(),
)

enum class Comparator(val symbol: String) { Eq(""), Lt("<"), Le("≤"), Gt(">"), Ge("≥") }

/**
 * One analyte measurement. Canonical fields drive analytics; printed fields are kept verbatim for traceability.
 * Invariant (enforced by the store): one row per (member, analyte, date).
 */
data class LabResult(
    val id: String,
    val memberId: String,
    val reportId: String?,
    val analyteId: String,
    val date: LocalDate,
    val value: Double,
    val unit: String,
    val comparator: Comparator = Comparator.Eq,
    val refLow: Double? = null,
    val refHigh: Double? = null,
    val printedName: String = "",
    val printedValue: String = "",
    val printedUnit: String = "",
    val printedRange: String = "",
)

enum class VitalType(val canonicalUnit: String, val paired: Boolean = false) {
    BloodPressure("mmHg", paired = true),
    Pulse("bpm"),
    SpO2("%"),
    Weight("kg"),
    Height("cm"),
    Bmi("kg/m²"),
    Temperature("°C"),
    RespiratoryRate("/min"),
    BloodGlucose("mg/dL"),
    Custom(""),
}

enum class VitalSource { Manual, Report, HealthConnect }

/** A vital sign at a point in time. Blood pressure uses [value] = systolic, [value2] = diastolic. */
data class Vital(
    val id: String,
    val memberId: String,
    val type: VitalType,
    val value: Double,
    val value2: Double? = null,
    val unit: String = type.canonicalUnit,
    val measuredAt: Instant,
    val source: VitalSource = VitalSource.Manual,
    val reportId: String? = null,
    val customName: String? = null,
    val note: String? = null,
)

/** Daily aggregate imported from Health Connect (stored locally only). */
data class DailyActivity(
    val memberId: String,
    val date: LocalDate,
    val steps: Long? = null,
    val restingHeartRate: Double? = null,
    val avgHeartRate: Double? = null,
    val sleepMinutes: Long? = null,
    val weightKg: Double? = null,
)
