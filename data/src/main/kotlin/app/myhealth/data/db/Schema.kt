package app.myhealth.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, hand-written schema. [VERSION] only moves forward; every bump adds a step to [Migrations] and a
 * snapshot file under `data/schemas/` (checked by SchemaSnapshotTest).
 */
object Schema {
    const val VERSION = 3

    /** DDL of version 1 (kept verbatim forever so migration tests can build a v1 database). */
    val V1: List<String> = listOf(
        """CREATE TABLE members (
            id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, relationship TEXT NOT NULL, dob TEXT, sex TEXT NOT NULL,
            height_cm REAL, blood_group TEXT NOT NULL, allergies TEXT NOT NULL, conditions TEXT NOT NULL,
            ec_name TEXT NOT NULL, ec_phone TEXT NOT NULL, ec_relation TEXT NOT NULL, created_at INTEGER NOT NULL)""",
        """CREATE TABLE reports (
            id TEXT PRIMARY KEY NOT NULL, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
            type TEXT NOT NULL, title TEXT NOT NULL, report_date TEXT NOT NULL, lab_name TEXT, doctor_name TEXT, notes TEXT,
            created_at INTEGER NOT NULL)""",
        "CREATE INDEX idx_reports_member_date ON reports(member_id, report_date)",
        """CREATE TABLE attachments (
            id TEXT PRIMARY KEY NOT NULL, report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
            kind TEXT NOT NULL, display_name TEXT NOT NULL, sha256 TEXT NOT NULL, size_bytes INTEGER NOT NULL,
            page_count INTEGER NOT NULL)""",
        "CREATE INDEX idx_attachments_sha ON attachments(sha256)",
        "CREATE INDEX idx_attachments_report ON attachments(report_id)",
        """CREATE TABLE lab_results (
            id TEXT PRIMARY KEY NOT NULL, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
            report_id TEXT REFERENCES reports(id) ON DELETE SET NULL, analyte_id TEXT NOT NULL, date TEXT NOT NULL,
            value REAL NOT NULL, unit TEXT NOT NULL, comparator TEXT NOT NULL, ref_low REAL, ref_high REAL,
            printed_name TEXT NOT NULL, printed_value TEXT NOT NULL, printed_unit TEXT NOT NULL, printed_range TEXT NOT NULL,
            UNIQUE(member_id, analyte_id, date))""",
        "CREATE INDEX idx_results_report ON lab_results(report_id)",
        """CREATE TABLE vitals (
            id TEXT PRIMARY KEY NOT NULL, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
            type TEXT NOT NULL, value REAL NOT NULL, value2 REAL, unit TEXT NOT NULL, measured_at INTEGER NOT NULL,
            source TEXT NOT NULL, report_id TEXT REFERENCES reports(id) ON DELETE SET NULL, custom_name TEXT, note TEXT)""",
        "CREATE INDEX idx_vitals_member_type_time ON vitals(member_id, type, measured_at)",
        """CREATE TABLE medicines (
            id TEXT PRIMARY KEY NOT NULL, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
            name TEXT NOT NULL, strength TEXT NOT NULL, form TEXT NOT NULL, frequency TEXT NOT NULL, dose TEXT NOT NULL,
            start_date TEXT NOT NULL, end_date TEXT, reminder_times TEXT NOT NULL, reminders_enabled INTEGER NOT NULL,
            instructions TEXT NOT NULL, prescribed_by TEXT NOT NULL, report_id TEXT REFERENCES reports(id) ON DELETE SET NULL,
            stopped INTEGER NOT NULL)""",
        "CREATE INDEX idx_medicines_member ON medicines(member_id)",
        """CREATE TABLE dose_logs (
            id TEXT PRIMARY KEY NOT NULL, medicine_id TEXT NOT NULL REFERENCES medicines(id) ON DELETE CASCADE,
            scheduled_at INTEGER NOT NULL, status TEXT NOT NULL, recorded_at INTEGER NOT NULL,
            UNIQUE(medicine_id, scheduled_at))""",
        """CREATE TABLE treatments (
            id TEXT PRIMARY KEY NOT NULL, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
            name TEXT NOT NULL, kind TEXT NOT NULL, date TEXT NOT NULL, provider TEXT NOT NULL, follow_up_date TEXT,
            notes TEXT NOT NULL, follow_up_done INTEGER NOT NULL)""",
        "CREATE INDEX idx_treatments_member ON treatments(member_id)",
        "CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)",
    )

    fun create(db: SupportSQLiteDatabase) {
        V1.forEach(db::execSQL)
        Migrations.steps.filter { it.from >= 1 && it.to <= VERSION }.sortedBy { it.from }.forEach { it.apply(db) }
    }

    /** Normalised DDL of the live database, used for snapshot comparison. */
    fun dump(db: SupportSQLiteDatabase): String {
        val out = StringBuilder()
        db.query("SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY type, name").use { c ->
            while (c.moveToNext()) out.append(c.getString(2).replace(Regex("\\s+"), " ").trim()).append(";\n")
        }
        return out.toString()
    }
}

/** One forward-only migration step. Downgrades are refused (never destructive). */
class Migration(val from: Int, val to: Int, private val statements: List<String>) {
    init { require(to == from + 1) }
    fun apply(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
}

object Migrations {
    val steps: List<Migration> = listOf(
        // v2: local daily aggregates from Health Connect (Phase 9).
        Migration(1, 2, listOf(
            """CREATE TABLE daily_activity (
                member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE, date TEXT NOT NULL,
                steps INTEGER, resting_hr REAL, avg_hr REAL, sleep_minutes INTEGER, weight_kg REAL,
                PRIMARY KEY(member_id, date))""",
        )),
        // v3: vaccinations, health cards, family history of relatives, visit questions, encrypted images.
        Migration(2, 3, listOf(
            """CREATE TABLE vaccinations (
                id TEXT PRIMARY KEY NOT NULL, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
                name TEXT NOT NULL, dose TEXT NOT NULL, given_date TEXT, due_date TEXT, provider TEXT NOT NULL, notes TEXT NOT NULL,
                created_at INTEGER NOT NULL)""",
            "CREATE INDEX idx_vaccinations_member ON vaccinations(member_id)",
            """CREATE TABLE health_cards (
                id TEXT PRIMARY KEY NOT NULL, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
                kind TEXT NOT NULL, title TEXT NOT NULL, number TEXT NOT NULL, holder_name TEXT NOT NULL, issuer TEXT NOT NULL,
                valid_until TEXT, notes TEXT NOT NULL, created_at INTEGER NOT NULL)""",
            "CREATE INDEX idx_health_cards_member ON health_cards(member_id)",
            """CREATE TABLE family_history (
                id TEXT PRIMARY KEY NOT NULL, relation TEXT NOT NULL, condition TEXT NOT NULL, onset_age INTEGER,
                notes TEXT NOT NULL, created_at INTEGER NOT NULL)""",
            """CREATE TABLE visit_questions (
                id TEXT PRIMARY KEY NOT NULL, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
                text TEXT NOT NULL, done INTEGER NOT NULL, created_at INTEGER NOT NULL)""",
            """CREATE TABLE media (
                id TEXT PRIMARY KEY NOT NULL, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
                owner_id TEXT NOT NULL, kind TEXT NOT NULL, created_at INTEGER NOT NULL)""",
            "CREATE INDEX idx_media_owner ON media(owner_id)",
        )),
    )

    fun migrate(db: SupportSQLiteDatabase, from: Int, to: Int) {
        var v = from
        while (v < to) {
            val step = steps.firstOrNull { it.from == v } ?: error("no migration from $v")
            step.apply(db)
            v = step.to
        }
    }
}
