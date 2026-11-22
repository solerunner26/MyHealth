package app.myhealth.data.vault

import app.myhealth.data.security.Crypto
import app.myhealth.data.security.SecureDelete
import java.io.File
import java.io.IOException

/**
 * Attachment store. Each file is AES-256-GCM sealed with the vault key and bound (AAD) to its own name,
 * so files cannot be swapped between records. Names are random ids; original names live only in the encrypted DB.
 */
class EncryptedVault(private val dir: File, private val vaultKey: ByteArray) {
    private val key = Crypto.aesKey(vaultKey)

    /** Restore stages files with the same key in a sibling directory, then renames them in. */
    internal fun keyBytesForStaging(): ByteArray = vaultKey.copyOf()

    init { dir.mkdirs() }

    fun put(name: String, plain: ByteArray) {
        require(plain.size <= MAX_BYTES) { "attachment too large" }
        val sealed = Crypto.seal(key, plain, aad(name))
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(MAGIC + sealed)
        if (!tmp.renameTo(File(dir, name))) { tmp.delete(); throw IOException("vault write failed") }
    }

    fun get(name: String): ByteArray {
        val bytes = File(dir, name).readBytes()
        require(bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "not a vault file" }
        return Crypto.open(key, bytes.copyOfRange(MAGIC.size, bytes.size), aad(name))
    }

    fun exists(name: String) = File(dir, name).exists()

    fun delete(name: String) = SecureDelete.overwriteAndDelete(File(dir, name))

    fun names(): List<String> = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.map { it.name } ?: emptyList()

    fun wipe() = SecureDelete.overwriteAndDelete(dir)

    private fun aad(name: String) = "myhealth.vault.v1:$name".toByteArray()

    companion object {
        const val MAX_BYTES = 30 * 1024 * 1024
        private val MAGIC = "MHV1".toByteArray()
    }
}
