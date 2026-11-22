package com.solerunner.myhealth.auth

import android.util.Base64
import com.solerunner.myhealth.core.security.PinHasher
import com.solerunner.myhealth.core.security.SecureKeyValueStore
import com.solerunner.myhealth.data.prefs.AutoLockDelay
import kotlin.math.min

/**
 * Enforces the user-facing lock. The PIN is never stored: only a PBKDF2 verifier and its salt, both
 * encrypted by the vault key.
 *
 * Brute-force protection: five wrong PINs start a cooldown that doubles for every further failure, up
 * to fifteen minutes. The counter survives process restarts because it lives in the encrypted store,
 * so killing the app does not reset the clock.
 */
class AppLockManager(private val store: SecureKeyValueStore) {

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
        resetFailures()
        return hasPin()
    }

    /** Verifies a PIN, applying the failed-attempt cooldown. Locked-out attempts never reach PBKDF2. */
    fun verifyPin(pin: String, now: Long = System.currentTimeMillis()): Boolean {
        if (lockoutRemainingMillis(now) > 0) return false
        if (!PinHasher.isValidFormat(pin)) {
            registerFailure(now)
            return false
        }
        val salt = decode(KEY_SALT) ?: return false
        val expected = decode(KEY_HASH) ?: return false
        val matches = PinHasher.verify(pin, salt, expected)
        if (matches) resetFailures() else registerFailure(now)
        return matches
    }

    fun clearPin() {
        store.remove(KEY_SALT)
        store.remove(KEY_HASH)
        resetFailures()
    }

    fun failedAttempts(): Int = store.getString(KEY_FAILED_ATTEMPTS)?.toIntOrNull() ?: 0

    fun lockoutRemainingMillis(now: Long = System.currentTimeMillis()): Long {
        val until = store.getString(KEY_LOCKOUT_UNTIL)?.toLongOrNull() ?: 0L
        return (until - now).coerceAtLeast(0L)
    }

    fun isLockedOut(now: Long = System.currentTimeMillis()): Boolean = lockoutRemainingMillis(now) > 0

    private fun registerFailure(now: Long) {
        val attempts = failedAttempts() + 1
        store.putString(KEY_FAILED_ATTEMPTS, attempts.toString())
        val delay = cooldownFor(attempts)
        if (delay > 0L) store.putString(KEY_LOCKOUT_UNTIL, (now + delay).toString())
    }

    private fun resetFailures() {
        store.remove(KEY_FAILED_ATTEMPTS)
        store.remove(KEY_LOCKOUT_UNTIL)
    }

    /** 30s after the fifth failure, doubling per further failure, capped at 15 minutes. */
    internal fun cooldownFor(attempts: Int): Long {
        if (attempts < FREE_ATTEMPTS) return 0L
        val extra = (attempts - FREE_ATTEMPTS).coerceAtMost(5)
        return min(BASE_COOLDOWN_MILLIS shl extra, MAX_COOLDOWN_MILLIS)
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
        const val KEY_FAILED_ATTEMPTS = "auth.pin.failed_attempts"
        const val KEY_LOCKOUT_UNTIL = "auth.pin.lockout_until"
        const val FREE_ATTEMPTS = 5
        const val BASE_COOLDOWN_MILLIS = 30_000L
        const val MAX_COOLDOWN_MILLIS = 15 * 60_000L
    }
}
