package com.solerunner.myhealth.auth

import android.util.Base64
import com.solerunner.myhealth.core.security.PinHasher
import com.solerunner.myhealth.core.security.SecureStore
import com.solerunner.myhealth.data.prefs.AutoLockDelay

/**
 * Enforces the user-facing lock. The PIN is never stored: only a PBKDF2 verifier and its salt, both
 * encrypted by the vault key. The database itself is protected by a hardware-backed key, so this
 * layer is about "someone picked up my phone", not about at-rest encryption.
 */
class AppLockManager(private val store: SecureStore) {

    fun hasPin(): Boolean = !store.getString(KEY_HASH).isNullOrEmpty() && !store.getString(KEY_SALT).isNullOrEmpty()

    fun setPin(pin: String): Boolean {
        require(PinHasher.isValidFormat(pin)) { "PIN must be 4-12 digits" }
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        val saltStored = store.putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
        val hashStored = store.putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
        if (!saltStored || !hashStored) {
            // Never leave half a verifier behind: clear both so the app reports "no PIN" cleanly.
            store.remove(KEY_SALT)
            store.remove(KEY_HASH)
            return false
        }
        return hasPin()
    }

    fun verifyPin(pin: String): Boolean {
        if (!PinHasher.isValidFormat(pin)) return false
        val salt = decode(KEY_SALT) ?: return false
        val expected = decode(KEY_HASH) ?: return false
        return PinHasher.verify(pin, salt, expected)
    }

    fun clearPin() {
        store.remove(KEY_SALT)
        store.remove(KEY_HASH)
    }

    private fun decode(key: String): ByteArray? = store.getString(key)?.let {
        runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
    }

    /** Marks the moment the app went to the background. */
    fun markBackgrounded(now: Long = System.currentTimeMillis()) = store.putString(KEY_LAST_BACKGROUND, now.toString())

    /**
     * True when the lock screen must be shown. With [AutoLockDelay.IMMEDIATELY] the app locks on every
     * stop; with a delay, quick app switches (camera, picker) do not force a PIN re-entry.
     */
    fun shouldLock(now: Long, delay: AutoLockDelay, appLockEnabled: Boolean): Boolean {
        if (!appLockEnabled || !hasPin()) return false
        if (delay == AutoLockDelay.IMMEDIATELY) return true
        val backgrounded = store.getString(KEY_LAST_BACKGROUND)?.toLongOrNull() ?: return true
        return now - backgrounded >= delay.millis
    }

    fun unlockedNow() = store.putString(KEY_LAST_BACKGROUND, System.currentTimeMillis().toString())

    private companion object {
        const val KEY_SALT = "auth.pin.salt"
        const val KEY_HASH = "auth.pin.hash"
        const val KEY_LAST_BACKGROUND = "auth.last_background"
    }
}
