package com.solerunner.myhealth.data.db

import android.content.Context
import android.database.Cursor
import com.solerunner.myhealth.core.security.VaultCrypto
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * A very small SQL surface.
 *
 * The app deliberately avoids Room/KSP: an annotation processor in the build would add a whole class of
 * failure (and a KSP version to keep in lock-step with AGP's built-in Kotlin). What the app needs is a
 * handful of typed tables, so the query layer is written by hand and unit-tested through a fake
 * [Db] implementation — the same SQL strings run against SQLCipher on the device and against the
 * framework SQLite in JVM tests.
 */
interface Db {
    fun exec(sql: String, args: List<Any?> = emptyList())
    fun <T> query(sql: String, args: List<Any?> = emptyList(), map: (Row) -> T): List<T>
    /** Runs an INSERT and returns the new row id. */
    fun insert(sql: String, args: List<Any?> = emptyList()): Long
    fun update(sql: String, args: List<Any?> = emptyList())
    fun <T> transaction(block: () -> T): T
    fun version(): Int
    fun setVersion(value: Int)
    fun close()
    fun path(): String
}

/** Column accessor with null-safe helpers, so mappers stay one-liners. */
class Row(private val cursor: Cursor) {
    fun long(index: Int): Long = cursor.getLong(index)
    fun longOrNull(index: Int): Long? = if (cursor.isNull(index)) null else cursor.getLong(index)
    fun int(index: Int): Int = cursor.getInt(index)
    fun intOrNull(index: Int): Int? = if (cursor.isNull(index)) null else cursor.getInt(index)
    fun double(index: Int): Double = cursor.getDouble(index)
    fun doubleOrNull(index: Int): Double? = if (cursor.isNull(index)) null else cursor.getDouble(index)
    fun string(index: Int): String = if (cursor.isNull(index)) "" else cursor.getString(index)
    fun stringOrNull(index: Int): String? = if (cursor.isNull(index)) null else cursor.getString(index)
    fun bool(index: Int): Boolean = !cursor.isNull(index) && cursor.getInt(index) != 0
}

/** Creates the database for a given passphrase. Swapped out in tests. */
fun interface DbFactory {
    fun create(passphrase: CharArray): Db
}


/**
 * Splits database configuration into statements that return rows and statements that do not.
 *
 * Android's `execSQL` throws `unknown error (code 0): Queries can be performed using SQLiteDatabase
 * query or rawQuery methods only.` for row-returning PRAGMAs such as `journal_mode`, which is what broke
 * the first on-device run. This helper exists so the split is unit-tested instead of discovered on a
 * phone.
 */
internal interface DbConfigurator {
    fun exec(sql: String)
    fun query(sql: String)
}

internal fun configureDatabase(configurator: DbConfigurator) {
    configurator.exec("PRAGMA foreign_keys = ON")
    configurator.query("PRAGMA journal_mode = WAL")
}

/**
 * SQLCipher-backed database: AES-256 page encryption keyed by the Keystore-wrapped passphrase.
 * `System.loadLibrary` is called defensively because a couple of OEM builds preload it.
 */
class SqlcipherDbFactory(
    private val context: Context,
    private val crypto: VaultCrypto,
    private val fileName: String = "myhealth.db",
) : DbFactory {

    override fun create(passphrase: CharArray): Db {
        if (!nativeLoaded) {
            System.loadLibrary("sqlcipher")
            nativeLoaded = true
        }
        val file = File(context.applicationContext.getDatabasePath(fileName).parentFile, fileName)
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, String(passphrase), null, null)
        configureDatabase(
            object : DbConfigurator {
                override fun exec(sql: String) = database.execSQL(sql)

                override fun query(sql: String) {
                    // rawQuery returns the resulting mode ("wal"); execSQL rejects row-returning SQL.
                    // A device that refuses WAL is slower but still fully usable, so this is not fatal.
                    runCatching { database.rawQuery(sql, null)?.use { cursor -> cursor.moveToFirst() } }
                        .onFailure { error ->
                            android.util.Log.w("MyHealthDb", "Could not run $sql; continuing without it", error)
                        }
                }
            },
        )
        return SqlcipherDb(database)
    }

    private companion object {
        @Volatile
        var nativeLoaded = false
    }
}

class SqlcipherDb(private val database: SQLiteDatabase) : Db {

    override fun exec(sql: String, args: List<Any?>) {
        if (args.isEmpty()) database.execSQL(sql) else database.execSQL(sql, args.toTypedArray())
    }

    override fun <T> query(sql: String, args: List<Any?>, map: (Row) -> T): List<T> {
        val cursor = if (args.isEmpty()) database.rawQuery(sql, emptyArray<String>()) else database.rawQuery(sql, args.toTypedArray())
        return cursor.use { c ->
            val results = ArrayList<T>(c.count.coerceAtLeast(1))
            while (c.moveToNext()) results += map(Row(c))
            results
        }
    }

    override fun insert(sql: String, args: List<Any?>): Long {
        exec(sql, args)
        return query("SELECT last_insert_rowid() AS id") { it.long(0) }.firstOrNull() ?: -1L
    }

    override fun update(sql: String, args: List<Any?>) = exec(sql, args)

    override fun <T> transaction(block: () -> T): T {
        database.beginTransaction()
        return try {
            val value = block()
            database.setTransactionSuccessful()
            value
        } finally {
            database.endTransaction()
        }
    }

    override fun version(): Int = database.version

    override fun setVersion(value: Int) {
        database.version = value
    }

    override fun close() = database.close()

    override fun path(): String = database.path ?: ""
}
