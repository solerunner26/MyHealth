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
    private val tables = listOf("members", "reports", "attachments", "lab_results", "vitals", "medicines", "dose_logs", "treatments", "daily_activity", "meta")

    suspend fun export(out: OutputStream, password: CharArray, nowMs: Long, appVersion: String): BackupInfo {
        val dump = stack.database.read { db -> tables.associateWith { t -> rows(db, t) } }
        val attachmentIds = dump.getValue("attachments").map { (it as JSONObject).getString("id") }
        val info = BackupInfo(Schema.VERSION, nowMs, dump.getValue("members").size, dump.getValue("reports").size, attachmentIds.size)
        try {
            ZipOutputStream(BackupCrypto.encrypting(out, password)).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(JSONObject().put("schema", Schema.VERSION).put("created", nowMs).put("app", appVersion).put("members", info.members)
                    .put("reports", info.reports).put("attachments", info.attachments).toString().toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(JSONObject().apply { dump.forEach { (t, r) -> put(t, JSONArray(r)) } }.toString().toByteArray())
                zip.closeEntry()
                attachmentIds.forEach { id ->
                    val bytes = runCatching { stack.vault.get(id) }.getOrNull() ?: return@forEach
                    zip.putNextEntry(ZipEntry("files/$id")); zip.write(bytes); zip.closeEntry()
                }
            }
        } catch (e: BackupException) { throw e } catch (e: Exception) { throw BackupException(BackupFailure.Io, e) }
        return info
    }

    /** Decrypts and validates fully, then replaces all current data. Current data is untouched on any failure. */
    suspend fun restore(input: InputStream, password: CharArray): BackupInfo {
        val staging = File(vaultDir.parentFile, "restore-staging").apply { if (exists()) SecureDelete.overwriteAndDelete(this); mkdirs() }
        val stagingVault = EncryptedVault(staging, vaultKeyBytes())
        var manifest: JSONObject? = null; var data: JSONObject? = null
        val files = mutableListOf<String>()
        try {
            val dec = BackupCrypto.decrypting(input, password)
            ZipInputStream(dec).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == "manifest.json" -> manifest = JSONObject(String(zip.readBytes()))
                        e.name == "data.json" -> data = JSONObject(String(zip.readBytes()))
                        e.name.startsWith("files/") -> {
                            val id = e.name.removePrefix("files/")
                            require(id.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "bad entry" }
                            stagingVault.put(id, zip.readBytes()); files += id
                        }
                    }
                }
                // Read through to the authenticated final chunk: a backup cut at an entry boundary must not restore partially.
                val sink = ByteArray(64 * 1024); while (dec.read(sink) != -1) { /* drain */ }
            }
            val m = manifest ?: throw BackupException(BackupFailure.Corrupt)
            val d = data ?: throw BackupException(BackupFailure.Corrupt)
            if (m.optInt("attachments", files.size) != files.size) throw BackupException(BackupFailure.Corrupt)
            val version = m.getInt("schema")
            if (version > Schema.VERSION) throw BackupException(BackupFailure.NewerVersion)
            val rows = upgrade(d, version)
            stack.database.write(tables.toSet()) { db ->
                tables.reversed().forEach { db.delete(it, null, null) }
                tables.forEach { t -> rows[t]?.forEach { cv -> db.insert(t, SQLiteDatabase.CONFLICT_ABORT, cv) } }
            }
            // swap attachments: drop current vault files, move staged ones in (same key, same AAD name)
            stack.vault.names().forEach { runCatching { stack.vault.delete(it) } }
            files.forEach { id -> File(staging, id).renameTo(File(vaultDir, id)) }
            return BackupInfo(version, m.optLong("created"), m.optInt("members"), m.optInt("reports"), files.size)
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
}
