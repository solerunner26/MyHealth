package app.myhealth.core

import app.myhealth.data.security.Crypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.util.Base64

/** Salted PBKDF2-HMAC-SHA256 PIN hashing (the PIN itself is never stored). */
object PinHasher {
    const val ITERATIONS = 120_000
    fun hash(pin: String, salt: ByteArray): ByteArray = Crypto.pbkdf2(pin.toCharArray(), salt, ITERATIONS)
    fun newSalt(): ByteArray = Crypto.randomBytes(16)
    fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)
}

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val attemptsLeftBeforeWait: Int) : PinResult
    data class LockedOut(val secondsLeft: Long) : PinResult
}

/**
 * UI lock. Gates the screens only; the data itself is protected at rest by the Keystore-wrapped keys (D-012).
 * Locks on cold start and after [BACKGROUND_GRACE_MS] in the background. After [FREE_ATTEMPTS] wrong PINs each further
 * wrong attempt imposes an exponentially growing wait (30 s, 60 s, … up to 15 min), persisted across restarts.
 */
class AppLock(private val prefs: Preferences, private val clock: Clock) {
    private val _locked = MutableStateFlow(enabled())
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt: Long? = null

    fun enabled() = prefs.state.value.lockEnabled && prefs.pinMaterial() != null

    fun onBackground() { backgroundedAt = clock.millis() }

    fun onForeground() {
        val at = backgroundedAt ?: return
        backgroundedAt = null
        if (enabled() && clock.millis() - at >= BACKGROUND_GRACE_MS) _locked.value = true
    }

    fun lockNow() { if (enabled()) _locked.value = true }

    fun setPin(pin: String) {
        require(pin.length in 4..8 && pin.all(Char::isDigit))
        val salt = PinHasher.newSalt()
        val hash = PinHasher.b64(PinHasher.hash(pin, salt))
        prefs.update { putBoolean("lock", true); putString("pin_hash", hash); putString("pin_salt", PinHasher.b64(salt)); putInt("pin_fail", 0); putLong("pin_lockout_until", 0) }
        _locked.value = false
    }

    fun disable() { prefs.setLock(false); prefs.setFailedAttempts(0, 0); _locked.value = false }

    fun tryPin(pin: String): PinResult {
        val now = clock.millis()
        val until = prefs.lockoutUntil()
        if (until > now) return PinResult.LockedOut((until - now + 999) / 1000)
        val (hashB64, saltB64) = prefs.pinMaterial() ?: return PinResult.Ok.also { _locked.value = false }
        val ok = runCatching { Crypto.equalsCt(PinHasher.hash(pin, PinHasher.unb64(saltB64)), PinHasher.unb64(hashB64)) }.getOrDefault(false)
        if (ok) { prefs.setFailedAttempts(0, 0); _locked.value = false; return PinResult.Ok }
        val fails = prefs.failedAttempts() + 1
        return if (fails >= FREE_ATTEMPTS) {
            val wait = (30_000L shl (fails - FREE_ATTEMPTS).coerceAtMost(5)).coerceAtMost(15 * 60_000L)
            prefs.setFailedAttempts(fails, now + wait)
            PinResult.LockedOut(wait / 1000)
        } else {
            prefs.setFailedAttempts(fails, 0)
            PinResult.Wrong(FREE_ATTEMPTS - fails)
        }
    }

    fun unlockedByBiometric() { prefs.setFailedAttempts(0, 0); _locked.value = false }

    companion object {
        const val BACKGROUND_GRACE_MS = 30_000L
        const val FREE_ATTEMPTS = 5
    }
}
