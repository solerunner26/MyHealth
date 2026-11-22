package com.solerunner.myhealth.core.security

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream

/**
 * Encrypted file vault for the documents the user imports (lab PDFs, report photos, pharmacy bills).
 *
 * Files are stored under `filesDir/vault` with a random name, prefixed with the GCM IV, and encrypted
 * with the Keystore-backed key. Decryption is done with `doFinal`, not a stream cipher, so a tampered
 * file fails GCM authentication instead of yielding partial plaintext; a name that tries to escape the
 * vault directory is rejected.
 */
class VaultFileStore(
    context: Context,
    private val crypto: VaultCrypto,
) {
    private val directory = File(context.applicationContext.filesDir, "vault")

    init {
        if (!directory.exists()) directory.mkdirs()
    }

    /** Encrypts [input] into the vault and returns the vault file name plus its SHA-256 of the *plain* data. */
    fun store(input: InputStream): StoredBlob {
        if (!directory.exists()) directory.mkdirs()
        val storedName = UUID.randomUUID().toString().replace("-", "") + ".vault"
        val target = File(directory, storedName)
        val digest = MessageDigest.getInstance("SHA-256")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, crypto.secretKey())

        var size = 0L
        FileOutputStream(target).use { fileOut ->
            fileOut.write(cipher.iv)
            CipherOutputStream(fileOut, cipher).use { cipherOut ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    cipherOut.write(buffer, 0, read)
                    size += read
                }
                cipherOut.flush()
            }
        }
        return StoredBlob(
            storedName = storedName,
            sizeBytes = size,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
        )
    }

    /** Decrypts the whole file (GCM tag verified) and streams it back for previews or sharing. */
    fun open(storedName: String): InputStream = ByteArrayInputStream(readAllBytes(storedName))

    fun readAllBytes(storedName: String): ByteArray {
        val file = resolve(storedName)
        val payload = file.readBytes()
        require(payload.size in (IV_LENGTH + TAG_BYTES)..MAX_VAULT_BYTES) {
            "vault file has an invalid size"
        }
        // VaultCrypto.decrypt uses doFinal, so a modified file throws AEADBadTagException.
        return crypto.decrypt(payload)
    }

    fun delete(storedName: String) {
        runCatching { resolve(storedName).delete() }
    }

    fun sizeBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    /** Rejects path traversal and names outside the vault directory. */
    private fun resolve(storedName: String): File {
        require(storedName.isNotBlank()) { "empty vault name" }
        require('/' !in storedName && '\\' !in storedName && ".." !in storedName) { "invalid vault name" }
        val file = File(directory, storedName)
        require(file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) {
            "vault path escapes the vault directory"
        }
        require(file.exists()) { "vault file missing" }
        return file
    }

    data class StoredBlob(val storedName: String, val sizeBytes: Long, val sha256: String)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BYTES = 16
        const val MAX_VAULT_BYTES = 64L * 1024L * 1024L
    }
}
