package com.example.superdialer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.superdialer.browser.data.BrowserDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A brand-new install creates the browser database from scratch. v0.5.2 crashed on every start because that first
 * creation failed (a NOT NULL column missing from the seed insert), which no upgrade-path test can see.
 */
@RunWith(AndroidJUnit4::class)
class FreshInstallTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "fresh-install-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun freshDatabaseCreatesAndSeedsTheDefaultTiles() = runBlocking {
        context.deleteDatabase(name)
        val db = BrowserDatabase.build(context, name)
        try {
            val tiles = db.speedDialDao().getAll()
            assertEquals(8, tiles.size)
            assertEquals("체크리스트", tiles.first().title)
            // everyone who opens the drawing board from this app joins the same fixed room
            assertTrue(tiles.first { it.title == "같이 그리기" }.url.endsWith("?room=superdialer"))
            assertTrue(tiles.none { it.isFolder })
            assertTrue(tiles.all { it.parentId == 0L })
            // the other tables work on a fresh database too
            assertTrue(db.bookmarkDao().getAll().isEmpty())
        } finally {
            db.close()
        }
    }
}
