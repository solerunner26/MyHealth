package com.solerunner.myhealth.data.db

import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.Attachment
import com.solerunner.myhealth.domain.model.AuditEvent
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.DailyMetric
import com.solerunner.myhealth.domain.model.LabResult
import com.solerunner.myhealth.domain.model.Measurement
import com.solerunner.myhealth.domain.model.MeasurementKind
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.MedicineForm
import com.solerunner.myhealth.domain.model.Treatment
import com.solerunner.myhealth.domain.model.TreatmentKind
import com.solerunner.myhealth.domain.model.ValueStatus
import java.time.Instant
import java.time.LocalDate

class LabResultDao(private val db: Db) {

    fun forReport(reportId: Long): List<LabResult> =
        db.query("SELECT * FROM lab_result WHERE report_id = ? ORDER BY category, display_name", listOf(reportId)) { it.toLabResult() }

    /** The trend feed: every analyte value plus the date of the report it belongs to. */
    fun since(since: LocalDate): List<Pair<LocalDate, LabResult>> = db.query(
        "SELECT r.sample_date, l.id, l.report_id, l.analyte_key, l.display_name, l.category, l.value, l.unit, " +
            "l.canonical_value, l.canonical_unit, l.ref_low, l.ref_high, l.ref_text, l.status, l.printed_line, " +
            "l.confidence, l.user_edited FROM lab_result l JOIN report r ON r.id = l.report_id " +
            "WHERE r.sample_date >= ? ORDER BY r.sample_date ASC",
        listOf(since.toEpochDay()),
    ) { row ->
        LocalDate.ofEpochDay(row.long(0)) to row.toLabResult(offset = 1)
    }

    fun count(): Int = db.query("SELECT COUNT(*) FROM lab_result") { it.int(0) }.firstOrNull() ?: 0

    fun insertAll(reportId: Long, results: List<LabResult>) {
        results.forEach { result ->
            db.insert(
                "INSERT INTO lab_result (report_id, analyte_key, display_name, category, value, unit, canonical_value, " +
                    "canonical_unit, ref_low, ref_high, ref_text, status, printed_line, confidence, user_edited) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                listOf(
                    reportId, result.analyteKey, result.displayName, result.category.name, result.value, result.unit,
                    result.canonicalValue, result.canonicalUnit, result.refLow, result.refHigh, result.refText,
                    result.status.name, result.printedLine, result.confidence, if (result.userEdited) 1 else 0,
                ),
            )
        }
    }

    fun deleteForReport(reportId: Long) = db.update("DELETE FROM lab_result WHERE report_id = ?", listOf(reportId))

    fun update(result: LabResult) = db.update(
        "UPDATE lab_result SET value=?, unit=?, canonical_value=?, canonical_unit=?, ref_low=?, ref_high=?, ref_text=?, " +
            "status=?, user_edited=? WHERE id=?",
        listOf(
            result.value, result.unit, result.canonicalValue, result.canonicalUnit, result.refLow, result.refHigh,
            result.refText, result.status.name, if (result.userEdited) 1 else 0, result.id,
        ),
    )

    private fun Row.toLabResult(offset: Int = 0) = LabResult(
        id = long(offset + 0),
        reportId = long(offset + 1),
        analyteKey = string(offset + 2),
        displayName = string(offset + 3),
        category = enumOr(string(offset + 4), AnalyteCategory.OTHER),
        value = doubleOrNull(offset + 5),
        unit = stringOrNull(offset + 6),
        canonicalValue = doubleOrNull(offset + 7),
        canonicalUnit = stringOrNull(offset + 8),
        refLow = doubleOrNull(offset + 9),
        refHigh = doubleOrNull(offset + 10),
        refText = stringOrNull(offset + 11),
        status = enumOr(string(offset + 12), ValueStatus.UNKNOWN),
        printedLine = stringOrNull(offset + 13),
        confidence = double(offset + 14),
        userEdited = bool(offset + 15),
    )
}

class MeasurementDao(private val db: Db) {

    fun all(): List<Measurement> =
        db.query("SELECT * FROM measurement ORDER BY recorded_at DESC") { it.toMeasurement() }

    fun since(date: LocalDate): List<Measurement> =
        db.query("SELECT * FROM measurement WHERE recorded_day >= ? ORDER BY recorded_at DESC", listOf(date.toEpochDay())) { it.toMeasurement() }

    fun byKind(kind: MeasurementKind): List<Measurement> =
        db.query("SELECT * FROM measurement WHERE kind = ? ORDER BY recorded_at DESC", listOf(kind.name)) { it.toMeasurement() }

    fun latest(kind: MeasurementKind): Measurement? =
        db.query("SELECT * FROM measurement WHERE kind = ? ORDER BY recorded_at DESC LIMIT 1", listOf(kind.name)) { it.toMeasurement() }.firstOrNull()

    fun insert(measurement: Measurement): Long = db.insert(
        "INSERT INTO measurement (kind, primary_value, secondary_value, unit, recorded_at, recorded_day, source, note, " +
            "report_id, custom_label) VALUES (?,?,?,?,?,?,?,?,?,?)",
        measurementeValues(measurement),
    )

    fun update(measurement: Measurement) = db.update(
        "UPDATE measurement SET kind=?, primary_value=?, secondary_value=?, unit=?, recorded_at=?, recorded_day=?, source=?, " +
            "note=?, report_id=?, custom_label=? WHERE id=?",
        measurementeValues(measurement) + measurement.id,
    )

    fun delete(id: Long) = db.update("DELETE FROM measurement WHERE id = ?", listOf(id))

    fun deleteAll() = db.exec("DELETE FROM measurement")

    private fun measurementeValues(m: Measurement): List<Any?> = listOf(
        m.kind.name, m.primaryValue, m.secondaryValue, m.unit, m.recordedAt.epochSecond, m.recordedDay.toEpochDay(),
        m.source.name, m.note, m.reportId, m.customLabel,
    )

    private fun Row.toMeasurement() = Measurement(
        id = long(0),
        kind = enumOr(string(1), MeasurementKind.CUSTOM),
        primaryValue = double(2),
        secondaryValue = doubleOrNull(3),
        unit = stringOrNull(4),
        recordedAt = Instant.ofEpochSecond(long(5)),
        recordedDay = LocalDate.ofEpochDay(long(6)),
        source = enumOr(string(7), DataSource.MANUAL),
        note = string(8),
        reportId = longOrNull(9),
        customLabel = stringOrNull(10),
    )
}
