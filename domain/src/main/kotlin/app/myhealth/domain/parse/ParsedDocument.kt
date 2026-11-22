package app.myhealth.domain.parse

import app.myhealth.domain.model.Comparator
import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.MedicineForm
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.model.VitalType
import java.time.LocalDate

enum class Confidence { High, Medium, Low }

/** Reasons the review screen shows next to a row so the user knows what to double-check. */
enum class ParseWarning { UnitInferred, UnitUnknown, RangeFromCatalogue, NoRange, UnknownAnalyte, ImplausibleValue, ValueOnNextLine, ComparatorValue }

data class ParsedLabRow(
    val analyteId: String?,
    val printedName: String,
    val rawLine: String,
    val printedValue: String,
    val value: Double?,
    val comparator: Comparator,
    val printedUnit: String,
    val printedRange: String,
    /** Canonical-unit value and range (null when unit unknown). */
    val canonicalValue: Double?,
    val canonicalUnit: String?,
    val refLow: Double?,
    val refHigh: Double?,
    val flag: String?,
    val confidence: Confidence,
    val warnings: Set<ParseWarning>,
)

data class ParsedVital(val type: VitalType, val value: Double, val value2: Double? = null, val unit: String = type.canonicalUnit, val rawLine: String)

data class ParsedMedicine(
    val name: String,
    val strength: String,
    val form: MedicineForm,
    val frequency: Frequency?,
    val durationDays: Int?,
    val rawLine: String,
)

data class ParsedDocument(
    val type: ReportType,
    val reportDate: LocalDate?,
    val labName: String?,
    val patientName: String?,
    val labRows: List<ParsedLabRow>,
    val vitals: List<ParsedVital>,
    val medicines: List<ParsedMedicine>,
    val pageCount: Int,
    val lineCount: Int,
) {
    val isEmpty get() = labRows.isEmpty() && vitals.isEmpty() && medicines.isEmpty()
}
