package com.solerunner.myhealth.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `accepts four to twelve digits only`() {
        assertTrue(PinHasher.isValidFormat("1234"))
        assertTrue(PinHasher.isValidFormat("123456789012"))
        assertFalse(PinHasher.isValidFormat("123"))
        assertFalse(PinHasher.isValidFormat("1234567890123"))
        assertFalse(PinHasher.isValidFormat("12a4"))
        assertFalse(PinHasher.isValidFormat(""))
    }

    @Test
    fun `salt is random and the right size`() {
        val first = PinHasher.newSalt()
        val second = PinHasher.newSalt()
        assertEquals(16, first.size)
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun `hash is deterministic for the same pin and salt`() {
        val salt = PinHasher.newSalt()
        val first = PinHasher.hash("2468", salt)
        val second = PinHasher.hash("2468", salt)
        assertEquals(32, first.size)
        assertEquals(first.toList(), second.toList())
    }

    @Test
    fun `different salts produce different verifiers`() {
        val first = PinHasher.hash("2468", PinHasher.newSalt())
        val second = PinHasher.hash("2468", PinHasher.newSalt())
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun `verifies the correct pin and rejects the wrong one`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("2468", salt)
        assertTrue(PinHasher.verify("2468", salt, hash))
        assertFalse(PinHasher.verify("1357", salt, hash))
        assertFalse(PinHasher.verify("2468", ByteArray(0), hash))
        assertFalse(PinHasher.verify("2468", salt, ByteArray(0)))
        assertFalse(PinHasher.verify("24", salt, hash))
    }
}
