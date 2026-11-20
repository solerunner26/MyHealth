package com.solerunner.myhealth.data.db

/**
 * Schema v1 of the local health record.
 *
 * Conventions
 * * dates are stored as **epoch days** (`INTEGER`), timestamps as **epoch seconds** (`INTEGER`), so
 *   ordering and 3-year windowing are plain integer arithmetic and timezone-free;
 * * booleans are `INTEGER` 0/1;
 * * list-like fields (allergies, reminder times) are stored as `|`-separated text: the app never needs
 *   to query inside them, and a normalised side table would add joins without value;
 * * every table that belongs to a report cascades on delete, so "delete this report" cannot leave
 *   orphan analytes behind.
 */
object Schema {

    const val VERSION = 1

    private val tables = listOf(
        """
        CREATE TABLE IF NOT EXISTS profile (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            account_id TEXT,
            display_name TEXT NOT NULL DEFAULT '',
            email TEXT,
            photo_url TEXT,
            auth_provider TEXT NOT NULL DEFAULT 'NONE',
            dob_epoch_day INTEGER,
            sex TEXT NOT NULL DEFAULT 'UNSPECIFIED',
            height_cm REAL,
            blood_group TEXT,
            allergies TEXT NOT NULL DEFAULT '',
            conditions TEXT NOT NULL DEFAULT '',
            emergency_contact_name TEXT,
            emergency_contact_phone TEXT,
            target_weight_kg REAL,
            created_at INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS report (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            kind TEXT NOT NULL DEFAULT 'LAB',
            title TEXT NOT NULL DEFAULT '',
            lab_name TEXT,
            doctor_name TEXT,
            facility_name TEXT,
            sample_date INTEGER NOT NULL,
            reported_date INTEGER,
            imported_at INTEGER NOT NULL DEFAULT 0,
            file_name TEXT,
            mime_type TEXT,
            page_count INTEGER NOT NULL DEFAULT 1,
            ocr_confidence REAL,
            source TEXT NOT NULL DEFAULT 'REPORT_IMPORT',
            notes TEXT NOT NULL DEFAULT '',
            fingerprint TEXT,
            raw_text TEXT,
            created_at INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS lab_result (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            report_id INTEGER NOT NULL REFERENCES report(id) ON DELETE CASCADE,
            analyte_key TEXT NOT NULL,
            display_name TEXT NOT NULL DEFAULT '',
            category TEXT NOT NULL DEFAULT 'OTHER',
            value REAL,
            unit TEXT,
            canonical_value REAL,
            canonical_unit TEXT,
            ref_low REAL,
            ref_high REAL,
            ref_text TEXT,
            status TEXT NOT NULL DEFAULT 'UNKNOWN',
            printed_line TEXT,
            confidence REAL NOT NULL DEFAULT 1.0,
            user_edited INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS measurement (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            kind TEXT NOT NULL,
            primary_value REAL NOT NULL,
            secondary_value REAL,
            unit TEXT,
            recorded_at INTEGER NOT NULL,
            recorded_day INTEGER NOT NULL,
            source TEXT NOT NULL DEFAULT 'MANUAL',
            note TEXT NOT NULL DEFAULT '',
            report_id INTEGER REFERENCES report(id) ON DELETE SET NULL,
            custom_label TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS medicine (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            molecule TEXT,
            form TEXT NOT NULL DEFAULT 'TABLET',
            strength TEXT,
            frequency_text TEXT,
            times_per_day INTEGER,
            duration_days INTEGER,
            start_date INTEGER,
            end_date INTEGER,
            prescribed_by TEXT,
            facility TEXT,
            reason TEXT,
            reminder_enabled INTEGER NOT NULL DEFAULT 0,
            reminder_times TEXT NOT NULL DEFAULT '',
            source TEXT NOT NULL DEFAULT 'MANUAL',
            notes TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS treatment (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            kind TEXT NOT NULL DEFAULT 'PROCEDURE',
            date INTEGER,
            facility TEXT,
            doctor TEXT,
            reason TEXT,
            outcome TEXT,
            follow_up_date INTEGER,
            notes TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS daily_metric (
            day INTEGER PRIMARY KEY,
            sleep_minutes INTEGER,
            deep_sleep_minutes INTEGER,
            rem_sleep_minutes INTEGER,
            steps INTEGER,
            resting_heart_rate REAL,
            average_heart_rate REAL,
            max_heart_rate REAL,
            hrv_rmssd REAL,
            spo2_average REAL,
            weight_kg REAL,
            active_calories REAL,
            exercise_minutes INTEGER,
            provider TEXT,
            synced_at INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS attachment (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            report_id INTEGER REFERENCES report(id) ON DELETE CASCADE,
            medicine_id INTEGER REFERENCES medicine(id) ON DELETE CASCADE,
            file_name TEXT NOT NULL DEFAULT '',
            mime_type TEXT NOT NULL DEFAULT 'application/octet-stream',
            size_bytes INTEGER NOT NULL DEFAULT 0,
            sha256 TEXT NOT NULL DEFAULT '',
            stored_name TEXT NOT NULL,
            page_index INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS audit_event (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            at INTEGER NOT NULL,
            action TEXT NOT NULL,
            detail TEXT NOT NULL DEFAULT ''
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL DEFAULT ''
        )
        """,
    )

    private val indices = listOf(
        "CREATE INDEX IF NOT EXISTS idx_report_sample_date ON report(sample_date DESC)",
        "CREATE INDEX IF NOT EXISTS idx_lab_result_report ON lab_result(report_id)",
        "CREATE INDEX IF NOT EXISTS idx_lab_result_key ON lab_result(analyte_key)",
        "CREATE INDEX IF NOT EXISTS idx_measurement_kind_day ON measurement(kind, recorded_day DESC)",
        "CREATE INDEX IF NOT EXISTS idx_medicine_name ON medicine(name)",
        "CREATE INDEX IF NOT EXISTS idx_medicine_active ON medicine(end_date)",
        "CREATE INDEX IF NOT EXISTS idx_daily_metric_day ON daily_metric(day DESC)",
        "CREATE INDEX IF NOT EXISTS idx_attachment_report ON attachment(report_id)",
        "CREATE INDEX IF NOT EXISTS idx_audit_at ON audit_event(at DESC)",
    )

    fun ensure(db: Db) {
        db.transaction {
            tables.forEach { db.exec(it) }
            indices.forEach { db.exec(it) }
        }
        val current = db.version()
        if (current == 0) {
            db.setVersion(VERSION)
        } else if (current < VERSION) {
            migrate(db, current)
            db.setVersion(VERSION)
        }
    }

    /**
     * Forward-only migrations. When the schema changes, raise [VERSION] and add a step here — never edit
     * the statements above, because existing installs never re-run them.
     *
     * Example for the next version:
     * ```
     * 1 -> db.exec("ALTER TABLE report ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
     * ```
     */
    private fun migrate(db: Db, from: Int) {
        if (from < 1) {
            // Nothing to do: v1 is the first released schema.
        }
    }
}
