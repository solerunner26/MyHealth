package com.solerunner.myhealth.data.db

import com.solerunner.myhealth.domain.model.Attachment
import com.solerunner.myhealth.domain.model.AuditEvent
import com.solerunner.myhealth.domain.model.DailyMetric
import com.solerunner.myhealth.domain.model.DataSource
import com.solerunner.myhealth.domain.model.Medicine
import com.solerunner.myhealth.domain.model.MedicineForm
import com.solerunner.myhealth.domain.model.Treatment
import com.solerunner.myhealth.domain.model.TreatmentKind
import java.time.Instant
import java.time.LocalDate

class MedicineDao(private val db: Db) {

    fun all(): List<Medicine> = db.query("SELECT * FROM medicine ORDER BY name COLLATE NOCASE") { it.toMedicine() }

    fun byId(id: Long): Medicine? = db.query("SELECT * FROM medicine WHERE id = ?", listOf(id)) { it.toMedicine() }.firstOrNull()

    fun active(on: LocalDate): List<Medicine> = db.query(
        "SELECT * FROM medicine WHERE end_date IS NULL OR end_date >= ? ORDER BY name COLLATE NOCASE",
        listOf(on.toEpochDay()),
    ) { it.toMedicine() }

    fun withReminders(): List<Medicine> =
        db.query("SELECT * FROM medicine WHERE reminder_enabled = 1") { it.toMedicine() }

    fun insert(medicine: Medicine): Long = db.insert(
        "INSERT INTO medicine (name, molecule, form, strength, frequency_text, times_per_day, duration_days, start_date, " +
            "end_date, prescribed_by, facility, reason, reminder_enabled, reminder_times, source, notes, created_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        medicine.toValues(),
    )

    fun update(medicine: Medicine) = db.update(
        "UPDATE medicine SET name=?, molecule=?, form=?, strength=?, frequency_text=?, times_per_day=?, duration_days=?, " +
            "start_date=?, end_date=?, prescribed_by=?, facility=?, reason=?, reminder_enabled=?, reminder_times=?, source=?, " +
            "notes=?, created_at=? WHERE id=?",
        medicine.toValues() + medicine.id,
    )

    fun delete(id: Long) = db.update("DELETE FROM medicine WHERE id = ?", listOf(id))

    fun deleteAll() = db.exec("DELETE FROM medicine")

    private fun Medicine.toValues(): List<Any?> = listOf(
        name, molecule, form.name, strength, frequencyText, timesPerDay, durationDays, startDate?.toEpochDay(),
        endDate?.toEpochDay(), prescribedBy, facility, reason, if (reminderEnabled) 1 else 0,
        reminderTimes.joinToString(","), source.name, notes, createdAt.epochSecond,
    )

    private fun Row.toMedicine() = Medicine(
        id = long(0),
        name = string(1),
        molecule = stringOrNull(2),
        form = enumOr(string(3), MedicineForm.TABLET),
        strength = stringOrNull(4),
        frequencyText = stringOrNull(5),
        timesPerDay = intOrNull(6),
        durationDays = intOrNull(7),
        startDate = longOrNull(8)?.let { LocalDate.ofEpochDay(it) },
        endDate = longOrNull(9)?.let { LocalDate.ofEpochDay(it) },
        prescribedBy = stringOrNull(10),
        facility = stringOrNull(11),
        reason = stringOrNull(12),
        reminderEnabled = bool(13),
        reminderTimes = string(14).split(',').map { it.trim() }.filter { it.isNotEmpty() },
        source = enumOr(string(15), DataSource.MANUAL),
        notes = string(16),
        createdAt = Instant.ofEpochSecond(long(17)),
    )
}

class TreatmentDao(private val db: Db) {

    fun all(): List<Treatment> = db.query("SELECT * FROM treatment ORDER BY COALESCE(date, 0) DESC, id DESC") { it.toTreatment() }

    fun byId(id: Long): Treatment? = db.query("SELECT * FROM treatment WHERE id = ?", listOf(id)) { it.toTreatment() }.firstOrNull()

    fun insert(treatment: Treatment): Long = db.insert(
        "INSERT INTO treatment (name, kind, date, facility, doctor, reason, outcome, follow_up_date, notes, created_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?)",
        treatment.toValues(),
    )

    fun update(treatment: Treatment) = db.update(
        "UPDATE treatment SET name=?, kind=?, date=?, facility=?, doctor=?, reason=?, outcome=?, follow_up_date=?, notes=?, " +
            "created_at=? WHERE id=?",
        treatment.toValues() + treatment.id,
    )

    fun delete(id: Long) = db.update("DELETE FROM treatment WHERE id = ?", listOf(id))

    fun deleteAll() = db.exec("DELETE FROM treatment")

    private fun Treatment.toValues(): List<Any?> = listOf(
        name, kind.name, date?.toEpochDay(), facility, doctor, reason, outcome, followUpDate?.toEpochDay(), notes,
        createdAt.epochSecond,
    )

    private fun Row.toTreatment() = Treatment(
        id = long(0),
        name = string(1),
        kind = enumOr(string(2), TreatmentKind.PROCEDURE),
        date = longOrNull(3)?.let { LocalDate.ofEpochDay(it) },
        facility = stringOrNull(4),
        doctor = stringOrNull(5),
        reason = stringOrNull(6),
        outcome = stringOrNull(7),
        followUpDate = longOrNull(8)?.let { LocalDate.ofEpochDay(it) },
        notes = string(9),
        createdAt = Instant.ofEpochSecond(long(10)),
    )
}

class DailyMetricDao(private val db: Db) {

    fun recent(limit: Int): List<DailyMetric> =
        db.query("SELECT * FROM daily_metric ORDER BY day DESC LIMIT ?", listOf(limit)) { it.toMetric() }

    fun since(date: LocalDate): List<DailyMetric> =
        db.query("SELECT * FROM daily_metric WHERE day >= ? ORDER BY day ASC", listOf(date.toEpochDay())) { it.toMetric() }

    /** Upsert: Health Connect is the authoritative source for a given day, so a re-sync overwrites only
     *  the fields it actually brought back (nulls keep whatever the user already had). */
    fun upsert(metric: DailyMetric) {
        db.exec(
            "INSERT INTO daily_metric (day, sleep_minutes, deep_sleep_minutes, rem_sleep_minutes, steps, resting_heart_rate, " +
                "average_heart_rate, max_heart_rate, hrv_rmssd, spo2_average, weight_kg, active_calories, exercise_minutes, " +
                "provider, synced_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(day) DO UPDATE SET " +
                "sleep_minutes = COALESCE(excluded.sleep_minutes, daily_metric.sleep_minutes), " +
                "deep_sleep_minutes = COALESCE(excluded.deep_sleep_minutes, daily_metric.deep_sleep_minutes), " +
                "rem_sleep_minutes = COALESCE(excluded.rem_sleep_minutes, daily_metric.rem_sleep_minutes), " +
                "steps = COALESCE(excluded.steps, daily_metric.steps), " +
                "resting_heart_rate = COALESCE(excluded.resting_heart_rate, daily_metric.resting_heart_rate), " +
                "average_heart_rate = COALESCE(excluded.average_heart_rate, daily_metric.average_heart_rate), " +
                "max_heart_rate = COALESCE(excluded.max_heart_rate, daily_metric.max_heart_rate), " +
                "hrv_rmssd = COALESCE(excluded.hrv_rmssd, daily_metric.hrv_rmssd), " +
                "spo2_average = COALESCE(excluded.spo2_average, daily_metric.spo2_average), " +
                "weight_kg = COALESCE(excluded.weight_kg, daily_metric.weight_kg), " +
                "active_calories = COALESCE(excluded.active_calories, daily_metric.active_calories), " +
                "exercise_minutes = COALESCE(excluded.exercise_minutes, daily_metric.exercise_minutes), " +
                "provider = COALESCE(excluded.provider, daily_metric.provider), " +
                "synced_at = excluded.synced_at",
            listOf(
                metric.day.toEpochDay(), metric.sleepMinutes, metric.deepSleepMinutes, metric.remSleepMinutes,
                metric.steps, metric.restingHeartRate, metric.averageHeartRate, metric.maxHeartRate,
                metric.hrvRmssd, metric.spo2Average, metric.weightKg, metric.activeCalories,
                metric.exerciseMinutes, metric.provider, metric.syncedAt.epochSecond,
            ),
        )
    }

    fun lastSync(): Instant? =
        db.query("SELECT MAX(synced_at) FROM daily_metric") { it.longOrNull(0) }.firstOrNull()?.let { Instant.ofEpochSecond(it) }

    fun deleteAll() = db.exec("DELETE FROM daily_metric")

    private fun Row.toMetric() = DailyMetric(
        day = LocalDate.ofEpochDay(long(0)),
        sleepMinutes = intOrNull(1),
        deepSleepMinutes = intOrNull(2),
        remSleepMinutes = intOrNull(3),
        steps = intOrNull(4),
        restingHeartRate = doubleOrNull(5),
        averageHeartRate = doubleOrNull(6),
        maxHeartRate = doubleOrNull(7),
        hrvRmssd = doubleOrNull(8),
        spo2Average = doubleOrNull(9),
        weightKg = doubleOrNull(10),
        activeCalories = doubleOrNull(11),
        exerciseMinutes = intOrNull(12),
        provider = stringOrNull(13),
        syncedAt = Instant.ofEpochSecond(long(14)),
    )
}
