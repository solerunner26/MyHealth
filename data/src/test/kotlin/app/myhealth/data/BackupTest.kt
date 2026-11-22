package app.myhealth.data

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.myhealth.data.backup.BackupCrypto
import app.myhealth.data.backup.BackupException
import app.myhealth.data.backup.BackupFailure
import app.myhealth.data.security.SoftwareKeyWrapper
import app.myhealth.domain.repository.ImportBundle
import app.myhealth.domain.repository.NewAttachment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun newStack(name: String) = StorageOpener(context, StoragePaths(File(context.noBackupFilesDir, "$name-${System.nanoTime()}")), SoftwareKeyWrapper()) { FrameworkSQLiteOpenHelperFactory() }.open()
    private val pw = "correct horse battery".toCharArray()

    private fun expect(f: BackupFailure, block: () -> Unit) { try { block(); fail("expected $f") } catch (e: BackupException) { assertEquals(f, e.failure) } }

    private fun cryptoRoundTrip(size: Int) {
        val data = ByteArray(size) { (it * 31).toByte() }
        val out = ByteArrayOutputStream(); BackupCrypto.encrypting(out, pw, iterations = 10_000).use { it.write(data) }
        assertArrayEquals(data, BackupCrypto.decrypting(ByteArrayInputStream(out.toByteArray()), pw).readBytes())
    }

    @Test fun chunkedCryptoRoundTripsAtBoundaries() {
        listOf(0, 1, BackupCrypto.CHUNK - 1, BackupCrypto.CHUNK, BackupCrypto.CHUNK + 1, 3 * BackupCrypto.CHUNK + 7).forEach { cryptoRoundTrip(it) }
    }

    @Test fun detectsWrongPasswordTamperTruncationAndForeignFiles() {
        val out = ByteArrayOutputStream(); BackupCrypto.encrypting(out, pw, iterations = 10_000).use { it.write(ByteArray(2 * BackupCrypto.CHUNK + 5) { 1 }) }
        val bytes = out.toByteArray()
        expect(BackupFailure.WrongPassword) { BackupCrypto.decrypting(ByteArrayInputStream(bytes), "nope".toCharArray()).readBytes() }
        val tampered = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        expect(BackupFailure.Corrupt) { BackupCrypto.decrypting(ByteArrayInputStream(tampered), pw).readBytes() }
        // drop the final chunk entirely: must be detected as truncation, not accepted as a shorter backup
        val firstTwoChunks = 4 + 1 + 4 + 16 + 8 + 16 + 2 * (4 + BackupCrypto.CHUNK + 16)
        expect(BackupFailure.Truncated) { BackupCrypto.decrypting(ByteArrayInputStream(bytes.copyOf(firstTwoChunks)), pw).readBytes() }
        val firstChunkTamper = bytes.copyOf().also { it[60] = (it[60].toInt() xor 1).toByte() }
        expect(BackupFailure.Corrupt) { BackupCrypto.decrypting(ByteArrayInputStream(firstChunkTamper), pw).readBytes() }
        expect(BackupFailure.NotABackup) { BackupCrypto.decrypting(ByteArrayInputStream("%PDF-1.4".toByteArray()), pw).readBytes() }
    }

    @Test fun exportRestoreRoundTripReplacesDataAndAttachments() = runBlocking {
        val a = newStack("a")
        a.members.upsert(TestData.member())
        a.records.saveImport(ImportBundle(TestData.report(), listOf(TestData.result("x1", v = 13.2)), emptyList(), emptyList()),
            listOf(NewAttachment(TestData.attachment(), "the original pdf".toByteArray())))
        val out = ByteArrayOutputStream()
        val info = a.backups(context).export(out, pw, 1_700_000_000_000, "test")
        assertEquals(1, info.members); assertEquals(1, info.attachments)
        assertFalse(String(out.toByteArray(), Charsets.ISO_8859_1).contains("Haemoglobin"))

        val b = newStack("b")
        b.members.upsert(TestData.member(id = "other", name = "Someone else"))
        val restored = b.backups(context).restore(ByteArrayInputStream(out.toByteArray()), pw)
        assertEquals(1, restored.attachments)
        assertEquals(listOf("m1"), b.members.all().map { it.id })
        assertEquals(13.2, b.records.results("m1").single().value, 0.0)
        assertArrayEquals("the original pdf".toByteArray(), b.records.attachmentBytes("a1"))

        // a wrong password leaves the existing data untouched
        val c = newStack("c"); c.members.upsert(TestData.member(id = "keep", name = "Keep me"))
        try { c.backups(context).restore(ByteArrayInputStream(out.toByteArray()), "wrong".toCharArray()); fail() } catch (e: BackupException) { assertEquals(BackupFailure.WrongPassword, e.failure) }
        assertEquals(listOf("keep"), c.members.all().map { it.id })
    }

    /** A validly encrypted archive (the attacker knows its password) with arbitrary entries. */
    private fun crafted(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(BackupCrypto.encrypting(out, pw, iterations = 10_000)).use { z ->
            entries.forEach { (n, b) -> z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(b); z.closeEntry() }
        }
        return out.toByteArray()
    }

    @Test fun craftedBackupsAreRejectedWithoutTouchingCurrentData() = runBlocking {
        val s = newStack("crafted")
        s.members.upsert(TestData.member())
        val manifest = """{"schema":1,"attachments":0}""".toByteArray()
        // column names are SQL identifiers: an injected key is refused
        val evil = """{"members":[{"id":"x","name":"y","relationship":"Self","created_at":0,"updated_at":0,"name) VALUES('a','b');--":1}]}"""
        expect(BackupFailure.Corrupt) { runBlocking { s.backups(context).restore(ByteArrayInputStream(crafted("manifest.json" to manifest, "data.json" to evil.toByteArray())), pw) } }
        // path-like attachment names are refused (no writes outside the staging folder)
        expect(BackupFailure.Corrupt) { runBlocking { s.backups(context).restore(ByteArrayInputStream(crafted("manifest.json" to manifest, "files/../../x" to ByteArray(1))), pw) } }
        // an oversized manifest (decompression bomb shape) is refused before it is parsed
        expect(BackupFailure.Corrupt) { runBlocking { s.backups(context).restore(ByteArrayInputStream(crafted("manifest.json" to ByteArray(app.myhealth.data.backup.BackupManager.MAX_MANIFEST + 1) { ' '.code.toByte() })), pw) } }
        assertEquals(1, s.members.all().size)
    }
}
