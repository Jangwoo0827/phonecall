package com.example.superdialer.browser.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
)

@Entity(tableName = "history", indices = [Index(value = ["visitedAt"])])
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
)

/** One tile of the start page (the "speed dial"). */
@Entity(tableName = "speed_dials")
data class SpeedDial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val position: Int,
    /** 0 = top level, otherwise the id of the folder this tile sits in. */
    val parentId: Long = 0,
    /** A folder tile (no url) that holds other tiles. */
    val isFolder: Boolean = false,
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getAll(): List<Bookmark>

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryItem>>

    @Insert
    suspend fun insert(item: HistoryItem)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dials ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<SpeedDial>>

    @Insert
    suspend fun insert(item: SpeedDial): Long

    @Update
    suspend fun update(item: SpeedDial)

    @Query("DELETE FROM speed_dials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM speed_dials WHERE parentId = :parentId")
    suspend fun nextPosition(parentId: Long): Int

    @Query("DELETE FROM speed_dials WHERE parentId = :parentId")
    suspend fun deleteChildren(parentId: Long)

    @Query("UPDATE speed_dials SET position = :position, parentId = :parentId WHERE id = :id")
    suspend fun place(id: Long, parentId: Long, position: Int)

    @Query("SELECT * FROM speed_dials ORDER BY position ASC, id ASC")
    suspend fun getAll(): List<SpeedDial>

    @Query("DELETE FROM speed_dials")
    suspend fun deleteAll()
}

@Database(
    entities = [Bookmark::class, HistoryItem::class, SpeedDial::class],
    version = 4,
    exportSchema = false,
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun speedDialDao(): SpeedDialDao

    companion object {
        @Volatile private var instance: BrowserDatabase? = null

        /** The checklist web app (extension/Summarizer, GitHub Pages) is the first tile; see ChecklistLink. */
        const val CHECKLIST_TITLE = "체크리스트"
        const val CHECKLIST_URL = "https://jangwoo0827.github.io/checklist_summarizer/"

        /** Insert used on a fresh install; must name every NOT NULL column of [SpeedDial] (see BrowserDatabaseSeedTest). */
        internal const val SEED_INSERT_SQL =
            "INSERT INTO speed_dials (title, url, position, parentId, isFolder) VALUES (?, ?, ?, 0, 0)"

        private val DEFAULT_TILES = listOf(
            CHECKLIST_TITLE to CHECKLIST_URL,
            "네이버" to "https://m.naver.com",
            "구글" to "https://www.google.com",
            "유튜브" to "https://m.youtube.com",
            "다음" to "https://m.daum.net",
            "쿠팡" to "https://m.coupang.com",
            "위키백과" to "https://ko.m.wikipedia.org",
        )

        /**
         * The default tiles. A fresh install is created at the newest schema, so the folder columns must be filled
         * (they are NOT NULL); the 1 -> 2 migration runs when those columns do not exist yet, hence [withFolderColumns].
         */
        private fun seed(db: SupportSQLiteDatabase, withFolderColumns: Boolean = true) {
            DEFAULT_TILES.forEachIndexed { index, (title, url) ->
                if (withFolderColumns) {
                    db.execSQL(SEED_INSERT_SQL, arrayOf<Any>(title, url, index))
                } else {
                    db.execSQL(
                        "INSERT INTO speed_dials (title, url, position) VALUES (?, ?, ?)",
                        arrayOf<Any>(title, url, index),
                    )
                }
            }
        }

        // v1 (bookmarks + history) -> v2 adds the speed dial table with the default tiles.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `speed_dials` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `position` INTEGER NOT NULL)"
                )
                seed(db, withFolderColumns = false)
            }
        }

        // v2 -> v3: the checklist tile goes first on phones that already have a start page.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val exists = db.query("SELECT 1 FROM speed_dials WHERE url = ?", arrayOf<Any>(CHECKLIST_URL)).use { it.moveToFirst() }
                if (exists) return
                db.execSQL("UPDATE speed_dials SET position = position + 1")
                db.execSQL(
                    "INSERT INTO speed_dials (title, url, position) VALUES (?, ?, 0)",
                    arrayOf<Any>(CHECKLIST_TITLE, CHECKLIST_URL),
                )
            }
        }

        // v3 -> v4: folders on the start page.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE speed_dials ADD COLUMN parentId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE speed_dials ADD COLUMN isFolder INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): BrowserDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, BrowserDatabase::class.java, "browser.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : Callback() {
                    // Fresh installs only; upgraded databases are seeded by the migration.
                    override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
                })
                .build()
                .also { instance = it }
        }
    }
}
