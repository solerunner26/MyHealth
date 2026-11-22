package app.myhealth.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.myhealth.data.DataStack
import app.myhealth.data.db.Migrations
import app.myhealth.data.db.Schema
import app.myhealth.data.security.SecureDelete
import app.myhealth.data.vault.EncryptedVault
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupInfo(val schemaVersion: Int, val createdAtMs: Long, val members: Int, val reports: Int, val attachments: Int)

/**
 * Password-encrypted export/import of everything: every table row plus decrypted attachments, inside a ZIP,
 * inside [BackupCrypto]. Restore is two-phase: the whole backup is decrypted and validated into a staging area
 * first; current data is replaced only if that succeeds. Older backups are upgraded with the normal migrations.
 */
class BackupManager(private val context: Context, private val stack: DataStack, private val vaultDir: File) {

    /** Tables in FK-safe insert order. */
    private val tables = listOf("members", "reports", "attachments", "lab_results", "vitals", "medicines", "dose_logs", "treatments", "daily_activity", "vaccinations", "health_cards", "family_history", "visit_questions", "media", "meta")

    suspend fun export(out: OutputStream, password: CharArray, nowMs: Long, appVersion: String): BackupInfo =
        write(out, password, nowMs, appVersion, memberId = null)

    /**
     * Item 4 (shared family view, no server): ONE member's record, for another phone to read. Family-wide relatives,
     * app settings and other members are never included.
     */
    suspend fun exportMember(out: OutputStream, password: CharArray, nowMs: Long, appVersion: String, memberId: String): BackupInfo =
        write(out, password, nowMs, appVersion, memberId)

    private suspend fun write(out: OutputStream, password: CharArray, nowMs: Long, appVersion: String, memberId: String?): BackupInfo {
        val dump = stack.database.read { db ->
            if (memberId == null) tables.associateWith { t -> rows(db, t) }
            else memberTables.associateWith { t -> rows(db, t, memberFilter(t), memberId) }
        }
        val attachmentIds = dump.getValue("attachments").map { (it as JSONObject).getString("id") }
        val photoNames = if (memberId == null) stack.vault.names().filter { it.startsWith("photo-") || it.startsWith("media-") }
            else (listOf("photo-$memberId") + dump.getValue("media").map { "media-" + (it as JSONObject).getString("id") }).filter { stack.vault.exists(it) }
        val info = BackupInfo(Schema.VERSION, nowMs, dump.getValue("members").size, dump.getValue("reports").size, attachmentIds.size)
        try {
            ZipOutputStream(BackupCrypto.encrypting(out, password)).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(JSONObject().put("schema", Schema.VERSION).put("created", nowMs).put("app", appVersion).put("members", info.members)
                    .put("kind", if (memberId == null) KIND_FULL else KIND_MEMBER)
                    .put("reports", info.reports).put("attachments", info.attachments).toString().toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(JSONObject().apply { dump.forEach { (t, r) -> put(t, JSONArray(r)) } }.toString().toByteArray())
                zip.closeEntry()
                photoNames.forEach { n -> runCatching { stack.vault.get(n) }.getOrNull()?.let { zip.putNextEntry(ZipEntry("files/$n")); zip.write(it); zip.closeEntry() } }
                attachmentIds.forEach { id ->
                    val bytes = runCatching { stack.vault.get(id) }.getOrNull() ?: return@forEach
                    zip.putNextEntry(ZipEntry("files/$id")); zip.write(bytes); zip.closeEntry()
                }
            }
        } catch (e: BackupException) { throw e } catch (e: Exception) { throw BackupException(BackupFailure.Io, e) }
        return info
    }

    /** Decrypts and validates fully, then replaces all current data. Current data is untouched on any failure. */
    suspend fun restore(input: InputStream, password: CharArray): BackupInfo = read(input, password, member = false)

    /**
     * Reads a member export from another phone: that member's record is added, or replaced if the same person was
     * shared before. Every row must belong to that one member (a crafted file cannot write into anyone else's record).
     */
    suspend fun importMember(input: InputStream, password: CharArray): BackupInfo = read(input, password, member = true)

    private suspend fun read(input: InputStream, password: CharArray, member: Boolean): BackupInfo {
        val staging = File(vaultDir.parentFile, "restore-staging").apply { if (exists()) SecureDelete.overwriteAndDelete(this); mkdirs() }
        val stagingVault = EncryptedVault(staging, vaultKeyBytes())
        var manifest: JSONObject? = null; var data: JSONObject? = null
        val files = mutableListOf<String>()
        try {
            val dec = BackupCrypto.decrypting(input, password)
            ZipInputStream(dec).use { zip ->
                var entries = 0
                while (true) {
                    val e = zip.nextEntry ?: break
                    // A backup is password-authenticated, but the password may be an attacker's ("restore my file"):
                    // bound everything so a crafted archive cannot exhaust memory or storage (zip bomb).
                    require(++entries <= MAX_ENTRIES) { "too many entries" }
                    when {
                        e.name == "manifest.json" -> manifest = JSONObject(String(readBounded(zip, MAX_MANIFEST)))
                        e.name == "data.json" -> data = JSONObject(String(readBounded(zip, MAX_DATA)))
                        e.name.startsWith("files/") -> {
                            val id = e.name.removePrefix("files/")
                            require(id.matches(ENTRY_ID)) { "bad entry" }
                            require(id !in files) { "duplicate entry" }
                            stagingVault.put(id, readBounded(zip, EncryptedVault.MAX_BYTES)); files += id
                        }
                    }
                }
                // Read through to the authenticated final chunk: a backup cut at an entry boundary must not restore partially.
                val sink = ByteArray(64 * 1024); while (dec.read(sink) != -1) { /* drain */ }
            }
            val m = manifest ?: throw BackupException(BackupFailure.Corrupt)
            val d = data ?: throw BackupException(BackupFailure.Corrupt)
            if (m.optInt("attachments", files.size) != files.count { !it.startsWith("photo-") && !it.startsWith("media-") }) throw BackupException(BackupFailure.Corrupt)
            val version = m.getInt("schema")
            if (version > Schema.VERSION) throw BackupException(BackupFailure.NewerVersion)
            if ((m.optString("kind", KIND_FULL) == KIND_MEMBER) != member) throw BackupException(BackupFailure.NotABackup)
            val rows = upgrade(d, version)
            if (member) {
                val memberId = checkMemberOnly(rows, files)
                val old = stack.database.read { db -> oldFilesOf(db, memberId) }
                stack.database.write(tables.toSet()) { db ->
                    db.delete("members", "id = ?", arrayOf(memberId)) // cascades to every table of that member
                    memberTables.forEach { t -> rows[t]?.forEach { cv -> db.insert(t, SQLiteDatabase.CONFLICT_ABORT, cv) } }
                }
                old.forEach { runCatching { stack.vault.delete(it) } }
                files.forEach { id -> File(staging, id).renameTo(File(vaultDir, id)) }
                return BackupInfo(version, m.optLong("created"), 1, m.optInt("reports"), files.count { !it.startsWith("photo-") && !it.startsWith("media-") })
            }
            stack.database.write(tables.toSet()) { db ->
                tables.reversed().forEach { db.delete(it, null, null) }
                tables.forEach { t -> rows[t]?.forEach { cv -> db.insert(t, SQLiteDatabase.CONFLICT_ABORT, cv) } }
            }
            // swap attachments: drop current vault files, move staged ones in (same key, same AAD name)
            stack.vault.names().forEach { runCatching { stack.vault.delete(it) } }
            files.forEach { id -> File(staging, id).renameTo(File(vaultDir, id)) }
            return BackupInfo(version, m.optLong("created"), m.optInt("members"), m.optInt("reports"), files.count { !it.startsWith("photo-") && !it.startsWith("media-") })
        } catch (e: BackupException) { throw e
        } catch (e: java.util.zip.ZipException) { throw BackupException(BackupFailure.Corrupt, e)
        } catch (e: org.json.JSONException) { throw BackupException(BackupFailure.Corrupt, e)
        } catch (e: IllegalArgumentException) { throw BackupException(BackupFailure.Corrupt, e)
        } catch (e: Exception) { throw BackupException(BackupFailure.Io, e)
        } finally { runCatching { SecureDelete.overwriteAndDelete(staging) } }
    }

    /** Loads rows into an in-memory database at [version], migrates it to the current schema, and reads them back. */
    private fun upgrade(d: JSONObject, version: Int): Map<String, List<ContentValues>> {
        val helper: SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) { Schema.V1.forEach(db::execSQL); Migrations.migrate(db, 1, version) }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            val present = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c -> while (c.moveToNext()) present += c.getString(0) }
            tables.filter { it in present && d.has(it) }.forEach { t ->
                val arr = d.getJSONArray(t)
                for (i in 0 until arr.length()) db.insert(t, SQLiteDatabase.CONFLICT_ABORT, toValues(arr.getJSONObject(i)))
            }
            Migrations.migrate(db, version, Schema.VERSION)
            return tables.associateWith { t -> db.query("SELECT * FROM $t").use { c -> buildList { while (c.moveToNext()) add(cursorValues(c)) } } }
        } finally { helper.close() }
    }

    private fun vaultKeyBytes(): ByteArray = stack.vault.keyBytesForStaging()

    /** Tables that hold one member's record (FK-safe order). */
    private val memberTables = listOf("members", "reports", "attachments", "lab_results", "vitals", "medicines", "dose_logs", "treatments", "daily_activity",
        "vaccinations", "health_cards", "visit_questions", "media")

    private fun memberFilter(t: String) = when (t) {
        "members" -> "id = ?"
        "attachments" -> "report_id IN (SELECT id FROM reports WHERE member_id = ?)"
        "dose_logs" -> "medicine_id IN (SELECT id FROM medicines WHERE member_id = ?)"
        else -> "member_id = ?"
    }

    /** Every row of a member file must belong to its single member; links must stay inside that member's rows. */
    private fun checkMemberOnly(rows: Map<String, List<ContentValues>>, files: List<String>): String {
        val members = rows["members"].orEmpty()
        require(members.size == 1) { "member file must hold exactly one member" }
        val id = members.single().getAsString("id")
        require(id != null && id.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "bad member id" }
        memberTables.filter { it !in setOf("members", "attachments", "dose_logs") }.forEach { t ->
            rows[t].orEmpty().forEach { require(it.getAsString("member_id") == id) { "row of another member" } }
        }
        val reports = rows["reports"].orEmpty().map { it.getAsString("id") }.toSet()
        val meds = rows["medicines"].orEmpty().map { it.getAsString("id") }.toSet()
        rows["attachments"].orEmpty().forEach { require(it.getAsString("report_id") in reports) { "attachment outside the record" } }
        rows["dose_logs"].orEmpty().forEach { require(it.getAsString("medicine_id") in meds) { "dose log outside the record" } }
        val allowedFiles = rows["attachments"].orEmpty().map { it.getAsString("id") }.toSet() + "photo-$id" +
            rows["media"].orEmpty().map { "media-" + it.getAsString("id") }
        require(files.all { it in allowedFiles }) { "file outside the record" }
        return id
    }

    /** Vault files of a member already on this phone, removed when a newer share of the same member replaces it. */
    private fun oldFilesOf(db: SupportSQLiteDatabase, memberId: String): List<String> =
        rows(db, "attachments", memberFilter("attachments"), memberId).map { it.getString("id") } + "photo-$memberId" +
            rows(db, "media", memberFilter("media"), memberId).map { "media-" + it.getString("id") }

    private fun rows(db: SupportSQLiteDatabase, table: String, where: String, arg: String): List<JSONObject> = db.query("SELECT * FROM $table WHERE $where", arrayOf(arg)).use { c ->
        buildList { while (c.moveToNext()) add(JSONObject().apply { for (i in 0 until c.columnCount) put(c.getColumnName(i), value(c, i)) }) }
    }

    private fun rows(db: SupportSQLiteDatabase, table: String): List<JSONObject> = db.query("SELECT * FROM $table").use { c ->
        buildList { while (c.moveToNext()) add(JSONObject().apply { for (i in 0 until c.columnCount) put(c.getColumnName(i), value(c, i)) }) }
    }

    private fun value(c: Cursor, i: Int): Any = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        else -> c.getString(i)
    }

    private fun toValues(o: JSONObject) = ContentValues().apply {
        o.keys().forEach { k ->
            // Keys become column names in an INSERT statement: accept plain identifiers only.
            require(k.matches(COLUMN)) { "bad column" }
            when (val v = o.get(k)) {
                JSONObject.NULL -> putNull(k)
                is Int -> put(k, v.toLong()); is Long -> put(k, v); is Double -> put(k, v); is Number -> put(k, v.toDouble())
                else -> put(k, v.toString())
            }
        }
    }

    private fun cursorValues(c: Cursor) = ContentValues().apply {
        for (i in 0 until c.columnCount) {
            val k = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> putNull(k); Cursor.FIELD_TYPE_INTEGER -> put(k, c.getLong(i)); Cursor.FIELD_TYPE_FLOAT -> put(k, c.getDouble(i))
                else -> put(k, c.getString(i))
            }
        }
    }

    companion object {
        const val KIND_FULL = "full"
        const val KIND_MEMBER = "member"
        const val MAX_ENTRIES = 20_000
        const val MAX_MANIFEST = 64 * 1024
        const val MAX_DATA = 96 * 1024 * 1024
        private val ENTRY_ID = Regex("[A-Za-z0-9-]{1,64}")
        private val COLUMN = Regex("[a-z_][a-z0-9_]{0,63}")

        /** Reads the current zip entry, failing once it exceeds [max] bytes (sizes in the zip header are not trusted). */
        internal fun readBounded(input: java.io.InputStream, max: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(64 * 1024); var total = 0L
            while (true) {
                val n = input.read(buf); if (n < 0) break
                total += n
                require(total <= max) { "entry too large" }
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
