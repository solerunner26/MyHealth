package com.solerunner.myhealth.data.db

import com.solerunner.myhealth.domain.model.Attachment
import com.solerunner.myhealth.domain.model.AuditEvent
import java.time.Instant

class AttachmentDao(private val db: Db) {

    fun forReport(reportId: Long): List<Attachment> =
        db.query("SELECT * FROM attachment WHERE report_id = ? ORDER BY page_index", listOf(reportId)) { it.toAttachment() }

    fun byId(id: Long): Attachment? = db.query("SELECT * FROM attachment WHERE id = ?", listOf(id)) { it.toAttachment() }.firstOrNull()

    fun all(): List<Attachment> = db.query("SELECT * FROM attachment") { it.toAttachment() }

    fun insert(attachment: Attachment): Long = db.insert(
        "INSERT INTO attachment (report_id, medicine_id, file_name, mime_type, size_bytes, sha256, stored_name, page_index, " +
            "created_at) VALUES (?,?,?,?,?,?,?,?,?)",
        listOf(
            attachment.reportId, attachment.medicineId, attachment.fileName, attachment.mimeType,
            attachment.sizeBytes, attachment.sha256, attachment.storedName, attachment.pageIndex,
            attachment.createdAt.epochSecond,
        ),
    )

    fun delete(id: Long) = db.update("DELETE FROM attachment WHERE id = ?", listOf(id))

    fun totalSize(): Long = db.query("SELECT COALESCE(SUM(size_bytes), 0) FROM attachment") { it.long(0) }.firstOrNull() ?: 0L

    fun deleteAll() = db.exec("DELETE FROM attachment")

    private fun Row.toAttachment() = Attachment(
        id = long(0),
        reportId = longOrNull(1),
        medicineId = longOrNull(2),
        fileName = string(3),
        mimeType = string(4),
        sizeBytes = long(5),
        sha256 = string(6),
        storedName = string(7),
        pageIndex = int(8),
        createdAt = Instant.ofEpochSecond(long(9)),
    )
}

class AuditDao(private val db: Db) {

    fun recent(limit: Int): List<AuditEvent> =
        db.query("SELECT * FROM audit_event ORDER BY at DESC LIMIT ?", listOf(limit)) { row ->
            AuditEvent(id = row.long(0), at = Instant.ofEpochSecond(row.long(1)), action = row.string(2), detail = row.string(3))
        }

    fun insert(event: AuditEvent) = db.insert(
        "INSERT INTO audit_event (at, action, detail) VALUES (?,?,?)",
        listOf(event.at.epochSecond, event.action, event.detail),
    )

    fun deleteAll() = db.exec("DELETE FROM audit_event")
}

class MetaDao(private val db: Db) {

    fun get(key: String): String? =
        db.query("SELECT value FROM meta WHERE key = ?", listOf(key)) { it.string(0) }.firstOrNull()

    fun put(key: String, value: String) = db.exec(
        "INSERT INTO meta (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        listOf(key, value),
    )

    fun deleteAll() = db.exec("DELETE FROM meta")
}
