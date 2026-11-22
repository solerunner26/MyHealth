package com.solerunner.myhealth.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solerunner.myhealth.core.security.SecureKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private class FakeStore : SecureKeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun putString(key: String, value: String?): Boolean {
        if (value == null) values.remove(key) else values[key] = value
        return true
    }

    override fun getString(key: String): String? = values[key]

    override fun remove(key: String) {
        values.remove(key)
    }
}

/** Brute-force protection: five failures start a cooldown; the right PIN is refused until it expires. */
@RunWith(AndroidJUnit4::class)
class AppLockManagerTest {

    @Test
    fun `five wrong pins lock the app even against the correct pin`() {
        val lock = AppLockManager(FakeStore())
        assertTrue(lock.setPin("2468"))
        val now = 1_000L
        repeat(4) { assertFalse(lock.verifyPin("0000", now)) }
        assertFalse(lock.isLockedOut(now))
        assertFalse(lock.verifyPin("0000", now))
        assertTrue(lock.isLockedOut(now))
        assertTrue(lock.lockoutRemainingMillis(now) >= 30_000L)
        assertFalse("correct PIN must wait out the cooldown", lock.verifyPin("2468", now))

        assertTrue(lock.verifyPin("2468", now + 30_001L))
        assertEquals(0, lock.failedAttempts())
        assertFalse(lock.isLockedOut(now + 30_001L))
    }

    @Test
    fun `cooldown doubles and is capped`() {
        val lock = AppLockManager(FakeStore())
        assertEquals(0L, lock.cooldownFor(4))
        assertEquals(30_000L, lock.cooldownFor(5))
        assertEquals(60_000L, lock.cooldownFor(6))
        assertEquals(120_000L, lock.cooldownFor(7))
        assertEquals(15 * 60_000L, lock.cooldownFor(20))
    }

    @Test
    fun `changing the pin clears failures`() {
        val lock = AppLockManager(FakeStore())
        lock.setPin("2468")
        repeat(3) { lock.verifyPin("0000", 0L) }
        assertEquals(3, lock.failedAttempts())
        assertTrue(lock.setPin("1357"))
        assertEquals(0, lock.failedAttempts())
        assertTrue(lock.verifyPin("1357", 0L))
    }
}
