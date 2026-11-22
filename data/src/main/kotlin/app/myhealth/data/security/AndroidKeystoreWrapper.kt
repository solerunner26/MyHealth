package app.myhealth.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** AES-256-GCM key in the AndroidKeyStore (StrongBox when available). Not user-auth bound so reminders work after reboot. */
class AndroidKeystoreWrapper(private val alias: String = "myhealth.master.v1") : KeyWrapper {

    private fun keyStore(): KeyStore = try {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    } catch (e: Exception) {
        throw SecureStorageException(SecureFailure.KeystoreUnavailable, e)
    }

    private fun key(): SecretKey {
        val ks = keyStore()
        return try {
            (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: throw SecureStorageException(SecureFailure.KeyInvalidated)
        } catch (e: UnrecoverableKeyException) {
            throw SecureStorageException(SecureFailure.KeyInvalidated, e)
        } catch (e: SecureStorageException) {
            throw e
        } catch (e: Exception) {
            throw SecureStorageException(SecureFailure.KeystoreUnavailable, e)
        }
    }

    override fun exists(): Boolean = runCatching { keyStore().containsAlias(alias) }.getOrElse { throw SecureStorageException(SecureFailure.KeystoreUnavailable, it) }

    override fun create() {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }
            .build()
        try {
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            try {
                gen.init(spec(strongBox = true)); gen.generateKey()
            } catch (e: Exception) {
                // StrongBox is optional hardware; fall back to the TEE-backed keystore (still non-exportable).
                gen.init(spec(strongBox = false)); gen.generateKey()
            }
        } catch (e: Exception) {
            throw SecureStorageException(SecureFailure.KeystoreUnavailable, e)
        }
    }

    override fun wrap(plain: ByteArray): ByteArray = guarded { Crypto.seal(key(), plain) }

    override fun unwrap(wrapped: ByteArray): ByteArray = guarded { Crypto.open(key(), wrapped) }

    override fun destroy() { runCatching { keyStore().deleteEntry(alias) } }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: SecureStorageException) {
        throw e
    } catch (e: KeyPermanentlyInvalidatedException) {
        throw SecureStorageException(SecureFailure.KeyInvalidated, e)
    } catch (e: AEADBadTagException) {
        throw SecureStorageException(SecureFailure.KeyMaterialCorrupt, e)
    } catch (e: Exception) {
        throw SecureStorageException(SecureFailure.KeystoreUnavailable, e)
    }
}
