package com.solerunner.myhealth.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Thrown when the Keystore cannot provide or use the vault key. Never swallowed silently. */
class VaultUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

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
 * StrongBox is preferred but never required: if the device advertises it and generation fails (some
 * vendor implementations do), the key is regenerated with a fresh TEE-backed spec. Encryption failures
 * are reported to the caller instead of crashing a lifecycle callback or a button press.
 */
class VaultCrypto(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    @Volatile
    private var cachedKey: SecretKey? = null

    fun secretKey(): SecretKey = cachedKey ?: synchronized(this) {
        cachedKey ?: runCatching { loadKey() ?: createKey() }
            .getOrElse { error ->
                Log.e(TAG, "Vault key unavailable", error)
                throw VaultUnavailableException("The device keystore could not provide the vault key", error)
            }
            .also { cachedKey = it }
    }

    private fun loadKey(): SecretKey? = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    } catch (error: Exception) {
        Log.w(TAG, "Could not load the vault key", error)
        null
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val strongBoxAdvertised = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (strongBoxAdvertised) {
            val strongBox = runCatching {
                generator.init(newKeySpec(strongBox = true).build())
                generator.generateKey()
            }
            strongBox.onSuccess { return it }
            strongBox.onFailure { error ->
                // A fresh spec is essential: reusing the StrongBox builder would keep requesting it.
                Log.w(TAG, "StrongBox key generation failed; using the TEE-backed key", error)
            }
        }

        generator.init(newKeySpec(strongBox = false).build())
        return generator.generateKey()
    }

    private fun newKeySpec(strongBox: Boolean): KeyGenParameterSpec.Builder {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        if (strongBox) builder.setIsStrongBoxBacked(true)
        return builder
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

    /** Returns null when the Keystore is unavailable; callers must fail closed, never crash. */
    fun encryptString(value: String): String? = runCatching {
        Base64.encodeToString(encrypt(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }.onFailure { error -> Log.e(TAG, "Could not encrypt a secure value", error) }.getOrNull()

    fun decryptString(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        return try {
            String(decrypt(Base64.decode(value, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (error: Exception) {
            Log.w(TAG, "Could not decrypt a secure value", error)
            null
        }
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    /**
     * SQLCipher passphrase: 32 random bytes, generated once and kept wrapped by the Keystore key.
     *
     * If a wrapped passphrase exists but cannot be unwrapped, this throws. Overwriting it would make
     * every existing database permanently unreadable, so a transient Keystore failure must never be
     * treated as "first run".
     */
    fun databasePassphrase(): CharArray {
        val stored = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (stored != null) {
            val decoded = decryptString(stored)
                ?: throw VaultUnavailableException("The stored database passphrase cannot be unwrapped")
            return decoded.toCharArray()
        }
        val fresh = Base64.encodeToString(randomBytes(32), Base64.NO_WRAP)
        val wrapped = encryptString(fresh)
            ?: throw VaultUnavailableException("The device keystore is unavailable")
        prefs.edit().putString(KEY_DB_PASSPHRASE, wrapped).apply()
        return fresh.toCharArray()
    }

    /** Confirms that the Keystore key still works — used to detect a restored-but-undecryptable vault. */
    fun selfCheck(): Boolean = runCatching {
        val probe = encrypt("myhealth".toByteArray())
        String(decrypt(probe)) == "myhealth"
    }.getOrDefault(false)

    private companion object {
        const val TAG = "MyHealthVault"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "myhealth.vault.key.v1"
        const val PREFS_NAME = "myhealth_vault"
        const val KEY_DB_PASSPHRASE = "db_passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
