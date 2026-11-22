package app.myhealth.data

import app.myhealth.data.security.Crypto
import app.myhealth.data.security.KeyManager
import app.myhealth.data.security.SecureFailure
import app.myhealth.data.security.SecureStorageException
import app.myhealth.data.security.SoftwareKeyWrapper
import app.myhealth.data.vault.EncryptedVault
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SecurityTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun expect(failure: SecureFailure, block: () -> Unit) {
        try { block(); fail("expected $failure") } catch (e: SecureStorageException) { assertEquals(failure, e.failure) }
    }

    @Test fun gcmRoundTripAndTamperDetection() {
        val key = Crypto.aesKey(Crypto.randomBytes(32))
        val sealed = Crypto.seal(key, "hello".toByteArray(), "ctx".toByteArray())
        assertArrayEquals("hello".toByteArray(), Crypto.open(key, sealed, "ctx".toByteArray()))
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertTrue(runCatching { Crypto.open(key, tampered, "ctx".toByteArray()) }.isFailure)
        assertTrue(runCatching { Crypto.open(key, sealed, "other".toByteArray()) }.isFailure)
        assertFalse(Crypto.seal(key, "x".toByteArray()).contentEquals(Crypto.seal(key, "x".toByteArray()))) // random nonce
    }

    @Test fun pbkdf2IsDeterministicAndSha256Hex() {
        val salt = ByteArray(16) { it.toByte() }
        assertArrayEquals(Crypto.pbkdf2("pw".toCharArray(), salt, 1000), Crypto.pbkdf2("pw".toCharArray(), salt, 1000))
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", Crypto.sha256Hex("hello".toByteArray()))
    }

    @Test fun keyManagerCreatesThenReloadsSameKeys() {
        val w = SoftwareKeyWrapper(); val f = File(tmp.root, "k/keys.bin")
        val a = KeyManager(w, f).loadOrCreate(false)
        val b = KeyManager(w, f).loadOrCreate(true)
        assertArrayEquals(a.dbPassphrase, b.dbPassphrase); assertArrayEquals(a.vaultKey, b.vaultKey)
        assertFalse(a.dbPassphrase.contentEquals(a.vaultKey))
    }

    @Test fun keyManagerFailsClosed() {
        val w = SoftwareKeyWrapper(); val f = File(tmp.root, "keys.bin")
        // encrypted data without key material: never regenerate keys
        expect(SecureFailure.KeyMaterialCorrupt) { KeyManager(w, f).loadOrCreate(encryptedDataExists = true) }
        KeyManager(w, f).loadOrCreate(false)
        // keystore key lost (e.g. lock-screen reset invalidated it)
        val lost = SoftwareKeyWrapper()
        expect(SecureFailure.KeyInvalidated) { KeyManager(lost, f).loadOrCreate(true) }
        // keystore unavailable
        w.failWith = SecureFailure.KeystoreUnavailable
        expect(SecureFailure.KeystoreUnavailable) { KeyManager(w, f).loadOrCreate(true) }
        w.failWith = null
        // corrupt key file
        f.writeBytes(byteArrayOf(1, 2, 3))
        expect(SecureFailure.KeyMaterialCorrupt) { KeyManager(w, f).loadOrCreate(true) }
    }

    @Test fun destroyMakesKeysUnrecoverable() {
        val w = SoftwareKeyWrapper(); val f = File(tmp.root, "keys.bin")
        KeyManager(w, f).loadOrCreate(false)
        KeyManager(w, f).destroy()
        assertFalse(f.exists()); assertFalse(w.exists())
    }

    @Test fun vaultEncryptsBindsAndWipes() {
        val dir = File(tmp.root, "vault"); val v = EncryptedVault(dir, Crypto.randomBytes(32))
        val pdf = "%PDF-1.4 secret lab values".toByteArray()
        v.put("a1", pdf); v.put("a2", "other".toByteArray())
        assertArrayEquals(pdf, v.get("a1"))
        assertFalse(String(File(dir, "a1").readBytes(), Charsets.ISO_8859_1).contains("secret"))
        // swapping ciphertext between names is detected (AAD binding)
        File(dir, "a1").copyTo(File(dir, "a2"), overwrite = true)
        assertTrue(runCatching { v.get("a2") }.isFailure)
        // wrong key cannot read
        assertTrue(runCatching { EncryptedVault(dir, Crypto.randomBytes(32)).get("a1") }.isFailure)
        assertEquals(setOf("a1", "a2"), v.names().toSet())
        v.wipe(); assertFalse(dir.exists())
    }
}
