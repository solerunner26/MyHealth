package com.solerunner.myhealth.core.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * SharedPreferences whose values are encrypted with the Keystore-backed vault key. Used for the PIN
 * verifier, security toggles and Health Connect consent — never for health data itself (that lives in
 * the encrypted database).
 */
class SecureStore(
    context: Context,
    private val crypto: VaultCrypto,
    name: String = "myhealth_secure",
) {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    /**
     * Writes an encrypted value. Returns false when the Keystore cannot encrypt right now; in that case
     * nothing is written, so a transient key failure can never destroy a value that is already stored.
     */
    fun putString(key: String, value: String?): Boolean {
        val editor = prefs.edit()
        if (value == null) {
            editor.remove(key)
            return true
        }
        val encrypted = crypto.encryptString(value) ?: return false
        editor.putString(key, encrypted)
        return true
    }

    fun getString(key: String): String? = crypto.decryptString(prefs.getString(key, null))

    fun putBoolean(key: String, value: Boolean) = putString(key, if (value) "1" else "0")

    fun getBoolean(key: String, default: Boolean = false): Boolean = getString(key)?.let { it == "1" } ?: default

    fun putInt(key: String, value: Int) = putString(key, value.toString())

    fun getInt(key: String, default: Int = 0): Int = getString(key)?.toIntOrNull() ?: default

    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun contains(key: String): Boolean = prefs.contains(key)
}

/**
 * PIN verifier.
 *
 * PBKDF2-HMAC-SHA256 with a per-install random salt and a deliberately slow iteration count. The app
 * never stores the PIN, only a verifier, and comparisons are constant-time
 * ([MessageDigest.isEqual]) so a timing side channel cannot be used to recover the PIN digit by digit.
 * A short PIN is a usability decision the user makes knowingly: the *data* is protected by the
 * hardware-backed database key, the PIN only gates the UI.
 */
object PinHasher {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16

    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: ByteArray, expected: ByteArray): Boolean {
        if (salt.isEmpty() || expected.isEmpty()) return false
        return MessageDigest.isEqual(hash(pin, salt), expected)
    }

    fun isValidFormat(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }
}
