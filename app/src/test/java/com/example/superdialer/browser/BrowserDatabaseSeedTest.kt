package com.example.superdialer.browser

import com.example.superdialer.browser.data.BrowserDatabase
import com.example.superdialer.browser.data.SpeedDial
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * A fresh install creates the database at the newest version and fills the default tiles with raw SQL.
 * v0.5.2 shipped crashing on every start because a column added later (`parentId`, NOT NULL) was missing from that
 * insert. This guards against it: every column of the entity except the auto id must be named in the insert.
 */
class BrowserDatabaseSeedTest {
    @Test fun freshInstallInsertNamesEveryColumn() {
        val columns = SpeedDial::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) && it.name != "id" }
            .map { it.name }
        val sql = BrowserDatabase.SEED_INSERT_SQL
        columns.forEach { assertTrue("insert is missing column $it", sql.contains(it)) }
    }
}
