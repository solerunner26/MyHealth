package app.myhealth.data.db

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.myhealth.data.security.SecureFailure
import app.myhealth.data.security.SecureStorageException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thin, codegen-free database wrapper: serialised write transactions plus a table-level invalidation stream so
 * DAOs can expose Flows that re-query when a table they read changes.
 */
class MyHealthDatabase(
    private val helper: SupportSQLiteOpenHelper,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val writeLock = Mutex()
    private val changes = MutableSharedFlow<Set<String>>(extraBufferCapacity = 64)

    /** Forces the open (and key check / migration) now, surfacing failures as [SecureStorageException]. */
    fun open(): SupportSQLiteDatabase = try {
        helper.writableDatabase
    } catch (e: SecureStorageException) {
        throw e
    } catch (e: Exception) {
        val newer = generateSequence<Throwable>(e) { it.cause }.any { it is SecureStorageException && it.failure == SecureFailure.NewerSchema }
        throw SecureStorageException(if (newer) SecureFailure.NewerSchema else SecureFailure.DatabaseUnavailable, e)
    }

    private val db: SupportSQLiteDatabase get() = open()

    suspend fun <T> read(block: (SupportSQLiteDatabase) -> T): T = withContext(io) { block(db) }

    suspend fun <T> write(tables: Set<String>, block: (SupportSQLiteDatabase) -> T): T {
        val result = withContext(io) {
            writeLock.withLock {
                val d = db
                d.beginTransaction()
                try {
                    block(d).also { d.setTransactionSuccessful() }
                } finally {
                    d.endTransaction()
                }
            }
        }
        changes.emit(tables)
        return result
    }

    fun <T> observe(tables: Set<String>, query: (SupportSQLiteDatabase) -> T): Flow<T> =
        changes.onSubscription { emit(tables) }
            .filter { changed -> changed.any { it in tables } }
            .conflate()
            .map { query(db) }
            .distinctUntilChanged()
            .flowOn(io)

    fun close() = runCatching { helper.close() }

    companion object {
        fun callback() = object : SupportSQLiteOpenHelper.Callback(Schema.VERSION) {
            override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
            override fun onCreate(db: SupportSQLiteDatabase) = Schema.create(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Migrations.migrate(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // Forward-only: a database written by a newer app version is never touched.
                throw SecureStorageException(SecureFailure.NewerSchema)
            }
            // The default implementation DELETES the database file. We never destroy user data implicitly.
            override fun onCorruption(db: SupportSQLiteDatabase) = Unit
        }
    }
}

// ---- cursor helpers
internal fun Cursor.str(c: String): String = getString(getColumnIndexOrThrow(c))
internal fun Cursor.strOrNull(c: String): String? = getColumnIndexOrThrow(c).let { if (isNull(it)) null else getString(it) }
internal fun Cursor.dbl(c: String): Double = getDouble(getColumnIndexOrThrow(c))
internal fun Cursor.dblOrNull(c: String): Double? = getColumnIndexOrThrow(c).let { if (isNull(it)) null else getDouble(it) }
internal fun Cursor.lng(c: String): Long = getLong(getColumnIndexOrThrow(c))
internal fun Cursor.lngOrNull(c: String): Long? = getColumnIndexOrThrow(c).let { if (isNull(it)) null else getLong(it) }
internal fun Cursor.bool(c: String): Boolean = getInt(getColumnIndexOrThrow(c)) != 0
internal inline fun <T> Cursor.mapAll(map: Cursor.() -> T): List<T> = use { val out = ArrayList<T>(count); while (moveToNext()) out += map(); out }

internal fun SupportSQLiteDatabase.q(sql: String, vararg args: Any?): Cursor = query(sql, args)
