package app.myhealth.data.security

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Holds the two data keys (SQLCipher passphrase, attachment-vault key). Both are random 256-bit values, stored only
 * in wrapped form (encrypted by the Keystore key) in app-private no-backup storage.
 *
 * Fail-closed rules:
 *  - wrapped keys present but unwrap fails → [SecureFailure.KeyInvalidated]/[SecureFailure.KeyMaterialCorrupt]; never regenerate.
 *  - encrypted data present but key file missing → [SecureFailure.KeyMaterialCorrupt]; never regenerate.
 */
class KeyManager(private val wrapper: KeyWrapper, private val keyFile: File) {

    class Keys(val dbPassphrase: ByteArray, val vaultKey: ByteArray)

    fun loadOrCreate(encryptedDataExists: Boolean): Keys {
        if (keyFile.exists()) return load()
        if (encryptedDataExists) throw SecureStorageException(SecureFailure.KeyMaterialCorrupt)
        if (!wrapper.exists()) wrapper.create()
        val keys = Keys(Crypto.randomBytes(32), Crypto.randomBytes(32))
        val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
        keyFile.parentFile?.mkdirs()
        DataOutputStream(tmp.outputStream()).use { out ->
            out.writeInt(MAGIC)
            listOf(wrapper.wrap(keys.dbPassphrase), wrapper.wrap(keys.vaultKey)).forEach { out.writeInt(it.size); out.write(it) }
        }
        if (!tmp.renameTo(keyFile)) throw SecureStorageException(SecureFailure.KeystoreUnavailable)
        return keys
    }

    private fun load(): Keys {
        if (!wrapper.exists()) throw SecureStorageException(SecureFailure.KeyInvalidated)
        val blobs = try {
            DataInputStream(keyFile.inputStream()).use { input ->
                require(input.readInt() == MAGIC)
                List(2) { val n = input.readInt(); require(n in 1..4096); ByteArray(n).also { input.readFully(it) } }
            }
        } catch (e: Exception) {
            throw SecureStorageException(SecureFailure.KeyMaterialCorrupt, e)
        }
        return Keys(wrapper.unwrap(blobs[0]), wrapper.unwrap(blobs[1]))
    }

    /** Secure erase: overwrite the wrapped blob, delete it, and destroy the Keystore key. Data becomes unrecoverable. */
    fun destroy() {
        runCatching { if (keyFile.exists()) SecureDelete.overwriteAndDelete(keyFile) }
        runCatching { wrapper.destroy() }
    }

    companion object { private const val MAGIC = 0x4D484B31 /* "MHK1" */ }
}

object SecureDelete {
    /** Best-effort overwrite with random bytes before unlinking (flash wear-levelling limits guarantees; key destruction is the real erase). */
    fun overwriteAndDelete(file: File) {
        if (file.isDirectory) { file.listFiles()?.forEach { overwriteAndDelete(it) }; file.delete(); return }
        runCatching {
            val len = file.length()
            java.io.RandomAccessFile(file, "rws").use { raf ->
                val buf = ByteArray(64 * 1024)
                var written = 0L
                while (written < len) {
                    Crypto.randomBytes(buf.size).copyInto(buf)
                    val n = minOf(buf.size.toLong(), len - written).toInt()
                    raf.write(buf, 0, n); written += n
                }
            }
        }
        file.delete()
    }
}
