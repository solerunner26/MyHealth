package com.solerunner.myhealth.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The cryptographic root of the app.
 *
 * Everything sensitive is protected by one AES-256-GCM key that lives in the Android Keystore and can
 * never be exported from the device:
 *
 * * the **database passphrase** — SQLCipher needs a passphrase; the app stores 32 random bytes wrapped
 *   by the Keystore key, so a stolen `.db` file without the device key is unreadable;
 * * **vault files** — scanned reports/bills are stored encrypted, not merely in private storage;
 * * **secure preferences** — PIN hash/salt, security flags, Health Connect consent.
 *
 * The key is StrongBox-backed when the device offers it. `setUserAuthenticationRequired` is left off
 * on purpose: the wrapped passphrase must also be usable by the background Health Connect sync, and
 * the *user-facing* lock is enforced separately by [com.solerunner.myhealth.auth.AppLockManager].
 */
class VaultCrypto(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    @Volatile
    private var cachedKey: SecretKey? = null

    fun secretKey(): SecretKey = cachedKey ?: synchronized(this) {
        cachedKey ?: (loadKey() ?: createKey()).also { cachedKey = it }
    }

    private fun loadKey(): SecretKey? = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    } catch (_: Exception) {
        null
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        ) {
            try {
                generator.init(builder.setIsStrongBoxBacked(true).build())
                return generator.generateKey()
            } catch (_: Exception) {
                // Fall through to a normal TEE-backed key on devices with partial StrongBox support.
            }
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    /** Returns `iv || ciphertext`, which is what gets written to disk. */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        return iv + cipherText
    }

    fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > IV_LENGTH) { "ciphertext too short" }
        val iv = payload.copyOfRange(0, IV_LENGTH)
        val cipherText = payload.copyOfRange(IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    fun encryptString(value: String): String =
        Base64.encodeToString(encrypt(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

    fun decryptString(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        return try {
            String(decrypt(Base64.decode(value, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    /**
     * SQLCipher passphrase: 32 random bytes, generated once and kept wrapped by the Keystore key.
     * Returned as a char array because that is what SQLCipher's API expects.
     */
    fun databasePassphrase(): CharArray {
        val stored = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (stored != null) {
            val decoded = decryptString(stored)
            if (decoded != null) return decoded.toCharArray()
        }
        val fresh = randomBytes(32)
        prefs.edit()
            .putString(KEY_DB_PASSPHRASE, encryptString(Base64.encodeToString(fresh, Base64.NO_WRAP)))
            .apply()
        return Base64.encodeToString(fresh, Base64.NO_WRAP).toCharArray()
    }

    /** Confirms that the Keystore key still works — used to detect a restored-but-undecryptable vault. */
    fun selfCheck(): Boolean {
        val probe = encrypt("myhealth".toByteArray())
        return String(decrypt(probe)) == "myhealth"
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "myhealth.vault.key.v1"
        const val PREFS_NAME = "myhealth_vault"
        const val KEY_DB_PASSPHRASE = "db_passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
