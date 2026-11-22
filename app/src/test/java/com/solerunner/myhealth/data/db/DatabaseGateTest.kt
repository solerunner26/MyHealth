package com.solerunner.myhealth.data.db

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Db that records calls and pretends every statement succeeded. */
private class FakeDb : Db {
    var statements = 0
    private var version = 0

    override fun exec(sql: String, args: List<Any?>) {
        statements++
    }

    override fun <T> query(sql: String, args: List<Any?>, map: (Row) -> T): List<T> = emptyList()

    override fun insert(sql: String, args: List<Any?>): Long = 0L

    override fun update(sql: String, args: List<Any?>) {
        statements++
    }

    override fun <T> transaction(block: () -> T): T = block()

    override fun version(): Int = version

    override fun setVersion(value: Int) {
        version = value
    }

    override fun close() = Unit

    override fun path(): String = "fake"
}

/**
 * Regression guard for the on-device crash loop: observers must never query the encrypted database
 * before it is open. The first composition raced the background open and threw from a LaunchedEffect.
 *
 * Uses real dispatchers because the query flow runs on Dispatchers.IO; a virtual-time test cannot see it.
 */
class DatabaseGateTest {

    @Test
    fun `observe does not query before the database opens and emits afterwards`() = runBlocking {
        val db = MyHealthDatabase(DbFactory { FakeDb() })
        var queries = 0
        val job = launch {
            db.observe {
                queries++
                7
            }.collect { }
        }
        delay(250)
        assertEquals("no query may run before open", 0, queries)

        db.open("test-passphrase".toCharArray())
        withTimeout(5_000) {
            while (queries == 0) delay(20)
        }
        assertTrue(db.isOpen())
        assertEquals(1, queries)
        job.cancel()
    }

    @Test
    fun `closing the database stops observers from querying`() = runBlocking {
        val db = MyHealthDatabase(DbFactory { FakeDb() })
        db.open("test-passphrase".toCharArray())
        var queries = 0
        val job = launch {
            db.observe {
                queries++
                1
            }.collect { }
        }
        withTimeout(5_000) {
            while (queries == 0) delay(20)
        }
        assertEquals(1, queries)
        db.close()
        delay(250)
        assertFalse(db.isOpen())
        assertEquals("closed database must not be queried", 1, queries)
        job.cancel()
    }
}
