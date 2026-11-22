package app.myhealth.data.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM and PBKDF2 helpers. Pure javax.crypto: identical on device and in JVM tests. */
object Crypto {
    const val NONCE_BYTES = 12
    const val TAG_BITS = 128
    private val random = SecureRandom()

    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    fun aesKey(raw: ByteArray): SecretKey = SecretKeySpec(raw, "AES")

    /** Output: nonce(12) || ciphertext+tag. [aad] binds the ciphertext to its context (e.g. file id). */
    fun seal(key: SecretKey, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        if (aad != null) c.updateAAD(aad)
        val ct = c.doFinal(plain)
        return ByteBuffer.allocate(c.iv.size + ct.size).put(c.iv).put(ct).array()
    }

    fun open(key: SecretKey, sealed: ByteArray, aad: ByteArray? = null): ByteArray {
        require(sealed.size > NONCE_BYTES) { "ciphertext too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
        if (aad != null) c.updateAAD(aad)
        return c.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
    }

    fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, bytes: Int = 32): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, bytes * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Constant-time comparison. */
    fun equalsCt(a: ByteArray, b: ByteArray) = java.security.MessageDigest.isEqual(a, b)
}
