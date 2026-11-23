package app.myhealth.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.myhealth.domain.model.Attachment
import app.myhealth.domain.model.AttachmentKind
import app.myhealth.domain.model.BloodGroup
import app.myhealth.domain.model.Comparator
import app.myhealth.domain.model.DailyActivity
import app.myhealth.domain.model.DoseLog
import app.myhealth.domain.model.DoseStatus
import app.myhealth.domain.model.EmergencyContact
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Frequency
import app.myhealth.domain.model.LabResult
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.MedicineForm
import app.myhealth.domain.model.Relationship
import app.myhealth.domain.model.Report
import app.myhealth.domain.model.ReportType
import app.myhealth.domain.model.Sex
import app.myhealth.domain.model.Treatment
import app.myhealth.domain.model.TreatmentKind
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalSource
import app.myhealth.domain.model.VitalType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback

internal fun LocalDate.iso(): String = toString()
internal fun String.date(): LocalDate = LocalDate.parse(this)

/**
 * Update-then-insert. Never `INSERT OR REPLACE`: SQLite implements REPLACE as delete+insert, which would fire
 * ON DELETE CASCADE and silently erase a member's records.
 */
internal fun SupportSQLiteDatabase.upsert(table: String, cv: ContentValues, where: String, args: Array<Any?>) {
    if (update(table, SQLiteDatabase.CONFLICT_ABORT, cv, where, args) == 0) insert(table, SQLiteDatabase.CONFLICT_ABORT, cv)
}

object MemberDao {
    const val T = "members"
    fun all(db: SupportSQLiteDatabase) = db.q("SELECT * FROM members ORDER BY CASE relationship WHEN 'Self' THEN 0 ELSE 1 END, created_at").mapAll { map() }
    fun get(db: SupportSQLiteDatabase, id: String) = db.q("SELECT * FROM members WHERE id = ?", id).mapAll { map() }.firstOrNull()
    fun upsert(db: SupportSQLiteDatabase, m: FamilyMember) = db.upsert(T, ContentValues().apply {
        put("id", m.id); put("name", m.name); put("relationship", m.relationship.name); put("dob", m.dateOfBirth?.iso())
        put("sex", m.sex.name); put("height_cm", m.heightCm); put("blood_group", m.bloodGroup.name)
        put("allergies", m.allergies.joinToString("\n")); put("conditions", m.conditions.joinToString("\n"))
        put("ec_name", m.emergencyContact.name); put("ec_phone", m.emergencyContact.phone); put("ec_relation", m.emergencyContact.relation)
        put("created_at", m.createdAtEpochMs)
    }, "id = ?", arrayOf(m.id))
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
    private fun Cursor.map() = FamilyMember(
        id = str("id"), name = str("name"), relationship = enumOr(str("relationship"), Relationship.Other),
        dateOfBirth = strOrNull("dob")?.date(), sex = enumOr(str("sex"), Sex.Unspecified), heightCm = dblOrNull("height_cm"),
        bloodGroup = enumOr(str("blood_group"), BloodGroup.Unknown),
        allergies = str("allergies").split('\n').filter { it.isNotBlank() }, conditions = str("conditions").split('\n').filter { it.isNotBlank() },
        emergencyContact = EmergencyContact(str("ec_name"), str("ec_phone"), str("ec_relation")), createdAtEpochMs = lng("created_at"),
    )
}

object ReportDao {
    const val T = "reports"
    const val A = "attachments"
    fun forMember(db: SupportSQLiteDatabase, memberId: String): List<Report> {
        val reports = db.q("SELECT * FROM reports WHERE member_id = ? ORDER BY report_date DESC, created_at DESC", memberId).mapAll { map() }
        val atts = db.q("SELECT a.* FROM attachments a JOIN reports r ON r.id = a.report_id WHERE r.member_id = ?", memberId).mapAll { mapAtt() }.groupBy { it.reportId }
        return reports.map { it.copy(attachments = atts[it.id].orEmpty()) }
    }
    fun get(db: SupportSQLiteDatabase, id: String): Report? = db.q("SELECT * FROM reports WHERE id = ?", id).mapAll { map() }.firstOrNull()
        ?.let { r -> r.copy(attachments = db.q("SELECT * FROM attachments WHERE report_id = ?", id).mapAll { mapAtt() }) }
    fun bySha(db: SupportSQLiteDatabase, sha: String): Attachment? = db.q("SELECT * FROM attachments WHERE sha256 = ? LIMIT 1", sha).mapAll { mapAtt() }.firstOrNull()
    fun attachment(db: SupportSQLiteDatabase, id: String): Attachment? = db.q("SELECT * FROM attachments WHERE id = ?", id).mapAll { mapAtt() }.firstOrNull()
    fun upsert(db: SupportSQLiteDatabase, r: Report) = db.upsert(T, ContentValues().apply {
        put("id", r.id); put("member_id", r.memberId); put("type", r.type.name); put("title", r.title); put("report_date", r.reportDate.iso())
        put("lab_name", r.labName); put("doctor_name", r.doctorName); put("notes", r.notes); put("created_at", r.createdAt.toEpochMilli())
    }, "id = ?", arrayOf(r.id))
    fun insertAttachment(db: SupportSQLiteDatabase, a: Attachment) {
        db.insert(A, SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("id", a.id); put("report_id", a.reportId); put("kind", a.kind.name); put("display_name", a.displayName)
            put("sha256", a.sha256); put("size_bytes", a.sizeBytes); put("page_count", a.pageCount)
        })
    }
    fun attachmentIdsForReport(db: SupportSQLiteDatabase, reportId: String) = db.q("SELECT id FROM attachments WHERE report_id = ?", reportId).mapAll { getString(0) }
    fun attachmentIdsForMember(db: SupportSQLiteDatabase, memberId: String) =
        db.q("SELECT a.id FROM attachments a JOIN reports r ON r.id = a.report_id WHERE r.member_id = ?", memberId).mapAll { getString(0) }
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
    private fun Cursor.map() = Report(
        id = str("id"), memberId = str("member_id"), type = enumOr(str("type"), ReportType.Other), title = str("title"),
        reportDate = str("report_date").date(), labName = strOrNull("lab_name"), doctorName = strOrNull("doctor_name"),
        notes = strOrNull("notes"), createdAt = Instant.ofEpochMilli(lng("created_at")),
    )
    private fun Cursor.mapAtt() = Attachment(
        id = str("id"), reportId = str("report_id"), kind = enumOr(str("kind"), AttachmentKind.Pdf), displayName = str("display_name"),
        sha256 = str("sha256"), sizeBytes = lng("size_bytes"), pageCount = lng("page_count").toInt(),
    )
}

object LabResultDao {
    const val T = "lab_results"
    fun forMember(db: SupportSQLiteDatabase, memberId: String) = db.q("SELECT * FROM lab_results WHERE member_id = ? ORDER BY date, analyte_id", memberId).mapAll { map() }
    fun forReport(db: SupportSQLiteDatabase, reportId: String) = db.q("SELECT * FROM lab_results WHERE report_id = ? ORDER BY analyte_id", reportId).mapAll { map() }
    fun byKey(db: SupportSQLiteDatabase, memberId: String, analyteId: String, date: LocalDate) =
        db.q("SELECT * FROM lab_results WHERE member_id = ? AND analyte_id = ? AND date = ?", memberId, analyteId, date.iso()).mapAll { map() }.firstOrNull()
    /** Enforces one row per (member, analyte, date): an existing row with that key is replaced. */
    fun upsert(db: SupportSQLiteDatabase, r: LabResult) {
        db.delete(T, "member_id = ? AND analyte_id = ? AND date = ? AND id != ?", arrayOf(r.memberId, r.analyteId, r.date.iso(), r.id))
        db.upsert(T, ContentValues().apply {
            put("id", r.id); put("member_id", r.memberId); put("report_id", r.reportId); put("analyte_id", r.analyteId); put("date", r.date.iso())
            put("value", r.value); put("unit", r.unit); put("comparator", r.comparator.name); put("ref_low", r.refLow); put("ref_high", r.refHigh)
            put("printed_name", r.printedName); put("printed_value", r.printedValue); put("printed_unit", r.printedUnit); put("printed_range", r.printedRange)
        }, "id = ?", arrayOf(r.id))
    }
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
    private fun Cursor.map() = LabResult(
        id = str("id"), memberId = str("member_id"), reportId = strOrNull("report_id"), analyteId = str("analyte_id"), date = str("date").date(),
        value = dbl("value"), unit = str("unit"), comparator = enumOr(str("comparator"), Comparator.Eq), refLow = dblOrNull("ref_low"), refHigh = dblOrNull("ref_high"),
        printedName = str("printed_name"), printedValue = str("printed_value"), printedUnit = str("printed_unit"), printedRange = str("printed_range"),
    )
}

object VitalDao {
    const val T = "vitals"
    fun forMember(db: SupportSQLiteDatabase, memberId: String) = db.q("SELECT * FROM vitals WHERE member_id = ? ORDER BY measured_at", memberId).mapAll { map() }
    fun upsert(db: SupportSQLiteDatabase, v: Vital) = db.upsert(T, ContentValues().apply {
        put("id", v.id); put("member_id", v.memberId); put("type", v.type.name); put("value", v.value); put("value2", v.value2); put("unit", v.unit)
        put("measured_at", v.measuredAt.toEpochMilli()); put("source", v.source.name); put("report_id", v.reportId); put("custom_name", v.customName); put("note", v.note)
    }, "id = ?", arrayOf(v.id))
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
    private fun Cursor.map() = Vital(
        id = str("id"), memberId = str("member_id"), type = enumOr(str("type"), VitalType.Custom), value = dbl("value"), value2 = dblOrNull("value2"),
        unit = str("unit"), measuredAt = Instant.ofEpochMilli(lng("measured_at")), source = enumOr(str("source"), VitalSource.Manual),
        reportId = strOrNull("report_id"), customName = strOrNull("custom_name"), note = strOrNull("note"),
    )
}

object MedicineDao {
    const val T = "medicines"
    const val D = "dose_logs"
    fun forMember(db: SupportSQLiteDatabase, memberId: String) = db.q("SELECT * FROM medicines WHERE member_id = ? ORDER BY stopped, start_date DESC", memberId).mapAll { map() }
    fun all(db: SupportSQLiteDatabase) = db.q("SELECT * FROM medicines").mapAll { map() }
    fun get(db: SupportSQLiteDatabase, id: String) = db.q("SELECT * FROM medicines WHERE id = ?", id).mapAll { map() }.firstOrNull()
    fun upsert(db: SupportSQLiteDatabase, m: Medicine) = db.upsert(T, ContentValues().apply {
        put("id", m.id); put("member_id", m.memberId); put("name", m.name); put("strength", m.strength); put("form", m.form.name)
        put("frequency", m.frequency.name); put("dose", m.dose); put("start_date", m.startDate.iso()); put("end_date", m.endDate?.iso())
        put("reminder_times", m.reminderTimes.joinToString(",")); put("reminders_enabled", if (m.remindersEnabled) 1 else 0)
        put("instructions", m.instructions); put("prescribed_by", m.prescribedBy); put("report_id", m.reportId); put("stopped", if (m.stopped) 1 else 0)
    }, "id = ?", arrayOf(m.id))
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
    fun logDose(db: SupportSQLiteDatabase, l: DoseLog) = db.upsert(D, ContentValues().apply {
        put("id", l.id); put("medicine_id", l.medicineId); put("scheduled_at", l.scheduledAt.toEpochMilli()); put("status", l.status.name); put("recorded_at", l.recordedAt.toEpochMilli())
    }, "medicine_id = ? AND scheduled_at = ?", arrayOf(l.medicineId, l.scheduledAt.toEpochMilli()))
    fun doseLogs(db: SupportSQLiteDatabase, memberId: String, since: Instant) = db.q(
        "SELECT d.* FROM dose_logs d JOIN medicines m ON m.id = d.medicine_id WHERE m.member_id = ? AND d.scheduled_at >= ? ORDER BY d.scheduled_at",
        memberId, since.toEpochMilli(),
    ).mapAll { DoseLog(str("id"), str("medicine_id"), Instant.ofEpochMilli(lng("scheduled_at")), enumOr(str("status"), DoseStatus.Taken), Instant.ofEpochMilli(lng("recorded_at"))) }
    private fun Cursor.map() = Medicine(
        id = str("id"), memberId = str("member_id"), name = str("name"), strength = str("strength"), form = enumOr(str("form"), MedicineForm.Other),
        frequency = enumOr(str("frequency"), Frequency.OnceDaily), dose = str("dose"), startDate = str("start_date").date(), endDate = strOrNull("end_date")?.date(),
        reminderTimes = str("reminder_times").split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() },
        remindersEnabled = bool("reminders_enabled"), instructions = str("instructions"), prescribedBy = str("prescribed_by"),
        reportId = strOrNull("report_id"), stopped = bool("stopped"),
    )
}

object TreatmentDao {
    const val T = "treatments"
    fun forMember(db: SupportSQLiteDatabase, memberId: String) = db.q("SELECT * FROM treatments WHERE member_id = ? ORDER BY date DESC", memberId).mapAll { map() }
    fun upsert(db: SupportSQLiteDatabase, t: Treatment) = db.upsert(T, ContentValues().apply {
        put("id", t.id); put("member_id", t.memberId); put("name", t.name); put("kind", t.kind.name); put("date", t.date.iso()); put("provider", t.provider)
        put("follow_up_date", t.followUpDate?.iso()); put("notes", t.notes); put("follow_up_done", if (t.followUpDone) 1 else 0)
    }, "id = ?", arrayOf(t.id))
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
    private fun Cursor.map() = Treatment(
        id = str("id"), memberId = str("member_id"), name = str("name"), kind = enumOr(str("kind"), TreatmentKind.Other), date = str("date").date(),
        provider = str("provider"), followUpDate = strOrNull("follow_up_date")?.date(), notes = str("notes"), followUpDone = bool("follow_up_done"),
    )
}

object ActivityDao {
    const val T = "daily_activity"
    fun forMember(db: SupportSQLiteDatabase, memberId: String) = db.q("SELECT * FROM daily_activity WHERE member_id = ? ORDER BY date", memberId).mapAll {
        DailyActivity(str("member_id"), str("date").date(), lngOrNull("steps"), dblOrNull("resting_hr"), dblOrNull("avg_hr"), lngOrNull("sleep_minutes"), dblOrNull("weight_kg"))
    }
    fun upsert(db: SupportSQLiteDatabase, d: DailyActivity) = db.upsert(T, ContentValues().apply {
        put("member_id", d.memberId); put("date", d.date.iso()); put("steps", d.steps); put("resting_hr", d.restingHeartRate); put("avg_hr", d.avgHeartRate)
        put("sleep_minutes", d.sleepMinutes); put("weight_kg", d.weightKg)
    }, "member_id = ? AND date = ?", arrayOf(d.memberId, d.date.iso()))
    fun clear(db: SupportSQLiteDatabase, memberId: String) = db.delete(T, "member_id = ?", arrayOf(memberId))
}

object MetaDao {
    const val T = "meta"
    fun get(db: SupportSQLiteDatabase, key: String): String? = db.q("SELECT value FROM meta WHERE key = ?", key).mapAll { getString(0) }.firstOrNull()
    fun set(db: SupportSQLiteDatabase, key: String, value: String?) {
        if (value == null) db.delete(T, "key = ?", arrayOf(key))
        else db.upsert(T, ContentValues().apply { put("key", key); put("value", value) }, "key = ?", arrayOf(key))
    }
}

object VaccinationDao {
    const val T = "vaccinations"
    fun forMember(db: SupportSQLiteDatabase, memberId: String) =
        db.q("SELECT * FROM vaccinations WHERE member_id = ? ORDER BY COALESCE(given_date, due_date) DESC, name", memberId).mapAll { map() }
    fun pending(db: SupportSQLiteDatabase) = db.q("SELECT * FROM vaccinations WHERE given_date IS NULL AND due_date IS NOT NULL").mapAll { map() }
    fun upsert(db: SupportSQLiteDatabase, v: app.myhealth.domain.model.Vaccination) = db.upsert(T, ContentValues().apply {
        put("id", v.id); put("member_id", v.memberId); put("name", v.name); put("dose", v.dose); put("given_date", v.givenDate?.iso()); put("due_date", v.dueDate?.iso())
        put("provider", v.provider); put("notes", v.notes); put("created_at", v.createdAtEpochMs)
    }, "id = ?", arrayOf(v.id))
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
    private fun Cursor.map() = app.myhealth.domain.model.Vaccination(
        str("id"), str("member_id"), str("name"), str("dose"), strOrNull("given_date")?.date(), strOrNull("due_date")?.date(), str("provider"), str("notes"), lng("created_at"),
    )
}

object HealthCardDao {
    const val T = "health_cards"
    fun forMember(db: SupportSQLiteDatabase, memberId: String) = db.q("SELECT * FROM health_cards WHERE member_id = ? ORDER BY created_at", memberId).mapAll {
        app.myhealth.domain.model.HealthCard(str("id"), str("member_id"), enumOr(str("kind"), app.myhealth.domain.model.HealthCardKind.Other), str("title"), str("number"),
            str("holder_name"), str("issuer"), strOrNull("valid_until")?.date(), str("notes"), lng("created_at"))
    }
    fun upsert(db: SupportSQLiteDatabase, c: app.myhealth.domain.model.HealthCard) = db.upsert(T, ContentValues().apply {
        put("id", c.id); put("member_id", c.memberId); put("kind", c.kind.name); put("title", c.title); put("number", c.number); put("holder_name", c.holderName)
        put("issuer", c.issuer); put("valid_until", c.validUntil?.iso()); put("notes", c.notes); put("created_at", c.createdAtEpochMs)
    }, "id = ?", arrayOf(c.id))
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
}

object FamilyHistoryDao {
    const val T = "family_history"
    fun all(db: SupportSQLiteDatabase) = db.q("SELECT * FROM family_history ORDER BY created_at").mapAll {
        app.myhealth.domain.model.RelativeCondition(str("id"), str("relation"), str("condition"), lngOrNull("onset_age")?.toInt(), str("notes"), lng("created_at"))
    }
    fun upsert(db: SupportSQLiteDatabase, r: app.myhealth.domain.model.RelativeCondition) = db.upsert(T, ContentValues().apply {
        put("id", r.id); put("relation", r.relation); put("condition", r.condition); put("onset_age", r.onsetAge); put("notes", r.notes); put("created_at", r.createdAtEpochMs)
    }, "id = ?", arrayOf(r.id))
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
}

object VisitQuestionDao {
    const val T = "visit_questions"
    fun forMember(db: SupportSQLiteDatabase, memberId: String) = db.q("SELECT * FROM visit_questions WHERE member_id = ? ORDER BY created_at", memberId).mapAll {
        app.myhealth.domain.model.VisitQuestion(str("id"), str("member_id"), str("text"), bool("done"), lng("created_at"))
    }
    fun upsert(db: SupportSQLiteDatabase, q: app.myhealth.domain.model.VisitQuestion) = db.upsert(T, ContentValues().apply {
        put("id", q.id); put("member_id", q.memberId); put("text", q.text); put("done", if (q.done) 1 else 0); put("created_at", q.createdAtEpochMs)
    }, "id = ?", arrayOf(q.id))
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
}

object MediaDao {
    const val T = "media"
    private fun Cursor.map() = app.myhealth.domain.model.Media(str("id"), str("member_id"), str("owner_id"), enumOr(str("kind"), app.myhealth.domain.model.MediaKind.CardFront), lng("created_at"))
    fun forOwner(db: SupportSQLiteDatabase, ownerId: String) = db.q("SELECT * FROM media WHERE owner_id = ? ORDER BY created_at", ownerId).mapAll { map() }
    fun forMember(db: SupportSQLiteDatabase, memberId: String) = db.q("SELECT * FROM media WHERE member_id = ?", memberId).mapAll { map() }
    fun insert(db: SupportSQLiteDatabase, m: app.myhealth.domain.model.Media) = db.upsert(T, ContentValues().apply {
        put("id", m.id); put("member_id", m.memberId); put("owner_id", m.ownerId); put("kind", m.kind.name); put("created_at", m.createdAtEpochMs)
    }, "id = ?", arrayOf(m.id))
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(T, "id = ?", arrayOf(id))
    fun deleteForOwner(db: SupportSQLiteDatabase, ownerId: String) = db.delete(T, "owner_id = ?", arrayOf(ownerId))
}

object MediclaimDao {
    const val P = "mediclaim_policies"; const val C = "mediclaim_claims"; const val H = "network_hospitals"

    private fun featuresJson(f: app.myhealth.domain.model.PolicyFeatures) = org.json.JSONObject().apply {
        put("roomRent", f.roomRent); put("coPay", f.coPayPercent); put("deductible", f.deductible); put("initialWait", f.initialWaitingDays)
        put("pedWait", f.pedWaitingMonths); put("specificWait", f.specificWaitingMonths); put("pre", f.preHospitalisationDays); put("post", f.postHospitalisationDays)
        put("ncb", f.noClaimBonusPercent); put("ambulance", f.ambulanceLimit); put("dayCare", f.dayCare); put("restoration", f.restoration); put("ayush", f.ayush)
        put("maternity", f.maternity); put("opd", f.opd); put("checkup", f.healthCheckup); put("domiciliary", f.domiciliary); put("organDonor", f.organDonor); put("other", f.other)
    }.toString()

    private fun features(s: String) = runCatching {
        val o = org.json.JSONObject(s)
        fun i(k: String) = if (o.isNull(k) || !o.has(k)) null else o.optInt(k)
        fun d(k: String) = if (o.isNull(k) || !o.has(k)) null else o.optDouble(k)
        app.myhealth.domain.model.PolicyFeatures(o.optString("roomRent"), i("coPay"), d("deductible"), i("initialWait"), i("pedWait"), i("specificWait"), i("pre"), i("post"),
            i("ncb"), d("ambulance"), o.optBoolean("dayCare"), o.optBoolean("restoration"), o.optBoolean("ayush"), o.optBoolean("maternity"), o.optBoolean("opd"),
            o.optBoolean("checkup"), o.optBoolean("domiciliary"), o.optBoolean("organDonor"), o.optString("other"))
    }.getOrDefault(app.myhealth.domain.model.PolicyFeatures())

    private fun Cursor.policy() = app.myhealth.domain.model.MediclaimPolicy(
        str("id"), str("member_id"), str("insurer"), str("plan_name"), str("policy_number"), enumOr(str("type"), app.myhealth.domain.model.PolicyType.Individual),
        dblOrNull("sum_insured") ?: 0.0, dblOrNull("bonus") ?: 0.0, dblOrNull("premium"), strOrNull("start_date")?.date(), strOrNull("end_date")?.date(),
        str("covered").split(',').filter { it.isNotBlank() }, str("tpa_name"), str("tpa_phone"), str("helpline"), str("agent_name"), str("agent_phone"),
        features(str("features")), str("notes"), lng("created_at"),
    )

    /** Policies held by this member or covering them. */
    fun forMember(db: SupportSQLiteDatabase, memberId: String) =
        db.q("SELECT * FROM mediclaim_policies WHERE member_id = ? OR (',' || covered || ',') LIKE ? ORDER BY end_date DESC", memberId, "%,$memberId,%").mapAll { policy() }
    /**
     * Before a member is deleted: every policy they hold that still covers another (existing) member moves to that
     * member, with its documents; the member is removed from all covered lists. Policies covering only them cascade.
     */
    fun releaseMember(db: SupportSQLiteDatabase, memberId: String) {
        val existing = db.q("SELECT id FROM members").mapAll { str("id") }.toSet()
        forMember(db, memberId).forEach { p ->
            val covered = p.coveredMemberIds.filter { it != memberId && it in existing }
            val holder = if (p.memberId == memberId) covered.firstOrNull() else p.memberId
            if (holder == null) return@forEach // only this member: the policy goes with them
            db.update(P, android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, ContentValues().apply { put("member_id", holder); put("covered", covered.joinToString(",")) }, "id = ?", arrayOf(p.id))
            if (holder != p.memberId) db.update(MediaDao.T, android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, ContentValues().apply { put("member_id", holder) }, "owner_id = ?", arrayOf(p.id))
        }
    }
    fun all(db: SupportSQLiteDatabase) = db.q("SELECT * FROM mediclaim_policies").mapAll { policy() }
    fun get(db: SupportSQLiteDatabase, id: String) = db.q("SELECT * FROM mediclaim_policies WHERE id = ?", id).mapAll { policy() }.firstOrNull()
    fun upsert(db: SupportSQLiteDatabase, p: app.myhealth.domain.model.MediclaimPolicy) = db.upsert(P, ContentValues().apply {
        put("id", p.id); put("member_id", p.memberId); put("insurer", p.insurer); put("plan_name", p.planName); put("policy_number", p.policyNumber); put("type", p.type.name)
        put("sum_insured", p.sumInsured); put("bonus", p.bonus); put("premium", p.premium); put("start_date", p.startDate?.iso()); put("end_date", p.endDate?.iso())
        put("covered", p.coveredMemberIds.joinToString(",")); put("tpa_name", p.tpaName); put("tpa_phone", p.tpaPhone); put("helpline", p.helpline)
        put("agent_name", p.agentName); put("agent_phone", p.agentPhone); put("features", featuresJson(p.features)); put("notes", p.notes); put("created_at", p.createdAtEpochMs)
    }, "id = ?", arrayOf(p.id))
    fun delete(db: SupportSQLiteDatabase, id: String) = db.delete(P, "id = ?", arrayOf(id))

    fun claims(db: SupportSQLiteDatabase, policyId: String) = db.q("SELECT * FROM mediclaim_claims WHERE policy_id = ? ORDER BY admitted_on DESC", policyId).mapAll {
        app.myhealth.domain.model.MediclaimClaim(str("id"), str("policy_id"), str("member_id"), str("hospital"), strOrNull("admitted_on")?.date(), strOrNull("discharged_on")?.date(),
            enumOr(str("kind"), app.myhealth.domain.model.ClaimKind.Cashless), dblOrNull("claimed") ?: 0.0, dblOrNull("approved"),
            enumOr(str("status"), app.myhealth.domain.model.ClaimStatus.Intimated), str("claim_number"), str("notes"), lng("created_at"))
    }
    fun upsertClaim(db: SupportSQLiteDatabase, c: app.myhealth.domain.model.MediclaimClaim) = db.upsert(C, ContentValues().apply {
        put("id", c.id); put("policy_id", c.policyId); put("member_id", c.memberId); put("hospital", c.hospital); put("admitted_on", c.admittedOn?.iso()); put("discharged_on", c.dischargedOn?.iso())
        put("kind", c.kind.name); put("claimed", c.claimedAmount); put("approved", c.approvedAmount); put("status", c.status.name); put("claim_number", c.claimNumber); put("notes", c.notes)
        put("created_at", c.createdAtEpochMs)
    }, "id = ?", arrayOf(c.id))
    fun deleteClaim(db: SupportSQLiteDatabase, id: String) = db.delete(C, "id = ?", arrayOf(id))

    fun hospitals(db: SupportSQLiteDatabase, policyId: String) = db.q("SELECT * FROM network_hospitals WHERE policy_id = ? ORDER BY name", policyId).mapAll {
        app.myhealth.domain.model.NetworkHospital(str("id"), str("policy_id"), str("name"), str("address"), str("city"), str("pincode"), str("phone"))
    }
    fun insertHospital(db: SupportSQLiteDatabase, h: app.myhealth.domain.model.NetworkHospital) = db.upsert(H, ContentValues().apply {
        put("id", h.id); put("policy_id", h.policyId); put("name", h.name); put("address", h.address); put("city", h.city); put("pincode", h.pincode); put("phone", h.phone)
    }, "id = ?", arrayOf(h.id))
    fun deleteHospital(db: SupportSQLiteDatabase, id: String) = db.delete(H, "id = ?", arrayOf(id))
}
