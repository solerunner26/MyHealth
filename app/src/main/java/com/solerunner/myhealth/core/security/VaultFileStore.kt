package com.solerunner.myhealth.core.security

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted file vault for the documents the user imports (lab PDFs, report photos, pharmacy bills).
 *
 * Files are stored under `filesDir/vault` with a random name, prefixed with the GCM IV, and encrypted
 * with the Keystore-backed key. Consequences that matter:
 * * a rooted device or a file-copy of app storage still cannot read the scans;
 * * the original file name and type are kept in the database, so the UI can show
 *   "Dr Lal PathLabs — CBC — 20 Feb 2026.pdf" while the bytes on disk are `a3f9…​.bin`;
 * * nothing here is ever world-readable or backed up (`allowBackup=false`).
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

    /** Streams the decrypted bytes back, for previews, thumbnails and sharing with a doctor. */
    fun open(storedName: String): InputStream {
        val file = File(directory, storedName)
        require(file.exists()) { "vault file missing: $storedName" }
        val fileInput = FileInputStream(file)
        val iv = ByteArray(IV_LENGTH).also { buffer ->
            var offset = 0
            while (offset < IV_LENGTH) {
                val read = fileInput.read(buffer, offset, IV_LENGTH - offset)
                if (read <= 0) break
                offset += read
            }
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, crypto.secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return CipherInputStream(fileInput, cipher)
    }

    fun readAllBytes(storedName: String): ByteArray = open(storedName).use { it.readBytes() }

    fun delete(storedName: String) {
        File(directory, storedName).delete()
    }

    fun sizeBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    data class StoredBlob(val storedName: String, val sizeBytes: Long, val sha256: String)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
