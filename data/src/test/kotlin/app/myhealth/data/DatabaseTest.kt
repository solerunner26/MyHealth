package app.myhealth.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.myhealth.data.db.MyHealthDatabase
import app.myhealth.data.db.Schema
import app.myhealth.data.security.SecureFailure
import app.myhealth.data.security.SecureStorageException
import app.myhealth.data.security.SoftwareKeyWrapper
import app.myhealth.domain.model.DoseLog
import app.myhealth.domain.model.DoseStatus
import app.myhealth.domain.model.Medicine
import app.myhealth.domain.model.Vital
import app.myhealth.domain.model.VitalType
import app.myhealth.domain.repository.ImportBundle
import app.myhealth.domain.repository.NewAttachment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var root: File
    private lateinit var wrapper: SoftwareKeyWrapper
    private lateinit var stack: DataStack

    private fun opener() = StorageOpener(context, StoragePaths(root), wrapper) { FrameworkSQLiteOpenHelperFactory() }

    @Before fun setUp() {
        root = File(context.noBackupFilesDir, "t-${System.nanoTime()}")
        wrapper = SoftwareKeyWrapper()
        stack = opener().open()
    }

    @After fun tearDown() { stack.close(); root.deleteRecursively() }

    @Test fun freshDatabaseIsAtCurrentVersionWithForeignKeys() {
        val db = stack.database.open()
        assertEquals(Schema.VERSION, db.version)
        db.query("PRAGMA foreign_keys").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test fun memberRoundTripAndCascadeDelete() = runBlocking {
        stack.members.upsert(TestData.member())
        assertEquals(listOf("Penicillin"), stack.members.get("m1")!!.allergies)
        stack.records.saveImport(ImportBundle(TestData.report(), listOf(TestData.result("x1")), emptyList(), emptyList()),
            listOf(NewAttachment(TestData.attachment(), "pdf-bytes".toByteArray())))
        // upserting the member again must NOT cascade-delete their records
        stack.members.upsert(TestData.member(name = "Asha P"))
        assertEquals(1, stack.records.results("m1").size)
        assertEquals("Asha P", stack.members.get("m1")!!.name)
        stack.members.delete("m1")
        assertTrue(stack.records.results("m1").isEmpty())
        assertNull(stack.records.report("r1"))
        assertTrue(stack.vault.names().isEmpty())
    }

    @Test fun oneRowPerMemberAnalyteDate() = runBlocking {
        stack.members.upsert(TestData.member())
        stack.records.saveImport(ImportBundle(TestData.report(), listOf(TestData.result("x1", v = 11.0)), emptyList(), emptyList()), emptyList())
        val conflicts = stack.records.conflicting("m1", listOf("hb" to LocalDate.of(2024, 3, 12)))
        assertEquals(listOf("x1"), conflicts.map { it.id })
        stack.records.upsertResult(TestData.result("x2", v = 13.0))
        val all = stack.records.results("m1")
        assertEquals(1, all.size); assertEquals(13.0, all.single().value, 0.0); assertEquals("x2", all.single().id)
    }

    @Test fun importIsAtomicAndCleansVaultOnFailure() = runBlocking {
        stack.members.upsert(TestData.member())
        val bad = ImportBundle(TestData.report(member = "missing-member"), emptyList(), emptyList(), emptyList())
        try {
            stack.records.saveImport(bad, listOf(NewAttachment(TestData.attachment(), "bytes".toByteArray())))
            fail("foreign key must reject")
        } catch (_: Exception) { }
        assertTrue("vault file removed after failed transaction", stack.vault.names().isEmpty())
        assertNull(stack.records.report("r1"))
    }

    @Test fun duplicateDetectionAndAttachmentDecrypt() = runBlocking {
        stack.members.upsert(TestData.member())
        stack.records.saveImport(ImportBundle(TestData.report(), emptyList(), emptyList(), emptyList()),
            listOf(NewAttachment(TestData.attachment(sha = "deadbeef"), "hello".toByteArray())))
        val hit = stack.records.findBySha256("deadbeef")
        assertNotNull(hit); assertEquals("r1", hit!!.report.id)
        assertNull(stack.records.findBySha256("nope"))
        assertArrayEquals("hello".toByteArray(), stack.records.attachmentBytes("a1"))
        stack.records.deleteReport("r1")
        assertTrue(stack.vault.names().isEmpty())
    }

    @Test fun observeEmitsOnChange() = runBlocking {
        val flow = stack.members.observeAll()
        val collected = async { flow.take(2).toList() }
        yield(); Thread.sleep(200)
        stack.members.upsert(TestData.member())
        val lists = collected.await()
        assertEquals(0, lists[0].size); assertEquals(1, lists[1].size)
    }

    @Test fun medicinesVitalsDoseLogs() = runBlocking {
        stack.members.upsert(TestData.member())
        val med = Medicine("med1", "m1", "Metformin", "500mg", startDate = LocalDate.of(2024, 1, 1), reminderTimes = listOf(java.time.LocalTime.of(9, 0)), remindersEnabled = true)
        stack.medicines.upsert(med)
        assertEquals(listOf(java.time.LocalTime.of(9, 0)), stack.medicines.get("med1")!!.reminderTimes)
        val at = Instant.parse("2024-02-01T03:30:00Z")
        stack.medicines.logDose(DoseLog("d1", "med1", at, DoseStatus.Snoozed, at))
        stack.medicines.logDose(DoseLog("d2", "med1", at, DoseStatus.Taken, at)) // same slot → updated, not duplicated
        assertEquals(listOf(DoseStatus.Taken), stack.medicines.doseLogs("m1", Instant.EPOCH).map { it.status })
        stack.vitals.upsert(Vital("v1", "m1", VitalType.BloodPressure, 130.0, 85.0, measuredAt = at))
        assertEquals(85.0, stack.vitals.all("m1").single().value2!!, 0.0)
        assertEquals(1, stack.medicines.allMembersActive(LocalDate.of(2024, 6, 1)).size)
    }

    @Test fun reopenWithSameKeysKeepsData() = runBlocking {
        stack.members.upsert(TestData.member())
        stack.close()
        stack = opener().open()
        assertEquals(1, stack.members.all().size)
    }

    @Test fun lostKeystoreKeyFailsClosedWithoutTouchingData() {
        runBlocking { stack.members.upsert(TestData.member()) }
        stack.close()
        val db = File(root, "myhealth.db"); val sizeBefore = db.length()
        wrapper.destroy()
        try { opener().open(); fail() } catch (e: SecureStorageException) { assertEquals(SecureFailure.KeyInvalidated, e.failure) }
        assertEquals(sizeBefore, db.length())
        stack = StorageOpener(context, StoragePaths(File(root, "other")), SoftwareKeyWrapper()) { FrameworkSQLiteOpenHelperFactory() }.open()
    }

    @Test fun eraseEverythingRemovesFilesAndKeys() {
        runBlocking { stack.members.upsert(TestData.member()) }
        val oldKeys = File(root, "keys/keys.bin").readBytes()
        stack.close()
        opener().eraseEverything()
        // Every byte of storage is gone and the wrapping key is destroyed: the old ciphertext is unrecoverable.
        assertTrue("no files may survive erase", !root.exists() || root.walkTopDown().none { it.isFile })
        assertTrue(!wrapper.exists())
        // A clean start afterwards works, uses brand-new key material and contains no old rows.
        stack = opener().open()
        assertTrue(!File(root, "keys/keys.bin").readBytes().contentEquals(oldKeys))
        assertTrue(runBlocking { stack.members.all() }.isEmpty())
        // the database file really lives where erase looks for it
        assertTrue(File(root, "myhealth.db").exists())
    }
}
