package app.myhealth.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.myhealth.data.db.MyHealthDatabase
import app.myhealth.data.db.Schema
import app.myhealth.data.security.SecureFailure
import app.myhealth.data.security.SecureStorageException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val schemasDir = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }.let { File(it, "data/schemas") }

    private fun helper(name: String, version: Int, create: (SupportSQLiteDatabase) -> Unit): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = create(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())

    private fun current(name: String) = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(MyHealthDatabase.callback()).build())

    @Test fun v1DatabaseMigratesToCurrentAndKeepsData() {
        val name = "mig-${System.nanoTime()}.db"
        helper(name, 1) { db -> Schema.V1.forEach(db::execSQL) }.writableDatabase.apply {
            execSQL("INSERT INTO members VALUES('m1','Asha','Self',NULL,'Female',NULL,'Unknown','','','','','',1)")
            execSQL("INSERT INTO reports VALUES('r1','m1','LabReport','CBC','2024-03-12',NULL,NULL,NULL,1)")
            execSQL("INSERT INTO lab_results VALUES('x1','m1','r1','hb','2024-03-12',12.5,'g/dL','Eq',12,15.5,'Hb','12.5','g/dL','12-15.5')")
            close()
        }
        val migrated = current(name).writableDatabase
        assertEquals(Schema.VERSION, migrated.version)
        migrated.query("SELECT value FROM lab_results WHERE id='x1'").use { it.moveToFirst(); assertEquals(12.5, it.getDouble(0), 0.0) }
        migrated.execSQL("INSERT INTO daily_activity(member_id, date, steps) VALUES('m1','2024-03-12',5000)")
        // migrated schema must equal a freshly created one
        val fresh = current("fresh-${System.nanoTime()}.db").writableDatabase
        assertEquals(Schema.dump(fresh), Schema.dump(migrated))
    }

    @Test fun newerDatabaseIsRefusedNotDeleted() {
        val name = "newer-${System.nanoTime()}.db"
        helper(name, Schema.VERSION + 1) { db -> Schema.V1.forEach(db::execSQL) }.writableDatabase.apply {
            execSQL("INSERT INTO meta VALUES('k','v')"); close()
        }
        try { MyHealthDatabase(current(name)).open(); fail() } catch (e: SecureStorageException) { assertEquals(SecureFailure.NewerSchema, e.failure) }
        val file = context.getDatabasePath(name)
        assertTrue(file.exists())
        helper(name, Schema.VERSION + 1) { }.writableDatabase.query("SELECT value FROM meta").use { it.moveToFirst(); assertEquals("v", it.getString(0)) }
    }

    /** Exported schema snapshots: every version's DDL is committed under data/schemas/<version>.sql. */
    @Test fun schemaSnapshotsMatch() {
        val v1 = helper("snap1-${System.nanoTime()}.db", 1) { db -> Schema.V1.forEach(db::execSQL) }.writableDatabase
        val cur = current("snap-${System.nanoTime()}.db").writableDatabase
        for ((version, dump) in listOf(1 to Schema.dump(v1), Schema.VERSION to Schema.dump(cur))) {
            val f = File(schemasDir, "$version.sql")
            if (!f.exists()) { f.parentFile.mkdirs(); f.writeText(dump); fail("wrote missing snapshot ${f.path}; review and commit it") }
            assertEquals("schema $version drifted from snapshot; add a migration instead of editing history", f.readText(), dump)
        }
    }
}
