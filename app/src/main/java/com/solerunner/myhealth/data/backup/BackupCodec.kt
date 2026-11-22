package com.solerunner.myhealth.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable encryption for backup files.
 *
 * A backup must be restorable on a different phone, so it cannot use the hardware-bound vault key. The
 * user's passphrase is stretched with PBKDF2-HMAC-SHA256 (210k iterations) and the payload is sealed with
 * AES-256-GCM. The file starts with a magic header and carries its own salt and IV:
 *
 * `MHBAK1 | salt(16) | iv(12) | ciphertext+tag`
 */
object BackupCodec {

    private val magic = byteArrayOf('M'.code.toByte(), 'H'.code.toByte(), 'B'.code.toByte(), 'A'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun encrypt(json: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt))
        val cipherText = cipher.doFinal(json)
        return magic + salt + cipher.iv + cipherText
    }

    fun decrypt(payload: ByteArray, passphrase: CharArray): ByteArray {
        require(payload.size > magic.size + SALT_LENGTH + IV_LENGTH + 16) { "backup is truncated" }
        require(payload.copyOfRange(0, magic.size).contentEquals(magic)) { "not a My Health backup" }
        val salt = payload.copyOfRange(magic.size, magic.size + SALT_LENGTH)
        val ivStart = magic.size + SALT_LENGTH
        val iv = payload.copyOfRange(ivStart, ivStart + IV_LENGTH)
        val cipherText = payload.copyOfRange(ivStart + IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
