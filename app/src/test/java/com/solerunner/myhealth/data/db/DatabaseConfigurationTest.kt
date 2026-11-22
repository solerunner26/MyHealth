package com.solerunner.myhealth.data.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the first on-device database failure: `PRAGMA journal_mode = WAL` returns a row,
 * and running it through `execSQL` throws
 * "unknown error (code 0): Queries can be performed using SQLiteDatabase query or rawQuery methods only."
 */
class DatabaseConfigurationTest {

    @Test
    fun `journal mode is queried, never executed`() {
        val executed = mutableListOf<String>()
        val queried = mutableListOf<String>()
        configureDatabase(
            object : DbConfigurator {
                override fun exec(sql: String) {
                    executed += sql
                }

                override fun query(sql: String) {
                    queried += sql
                }
            },
        )

        assertTrue("foreign keys must be enabled", executed.any { it.contains("foreign_keys") })
        assertFalse("journal_mode returns a row and must not be executed", executed.any { it.contains("journal_mode") })
        assertTrue("journal_mode must be queried", queried.any { it.contains("journal_mode") })
    }
}
