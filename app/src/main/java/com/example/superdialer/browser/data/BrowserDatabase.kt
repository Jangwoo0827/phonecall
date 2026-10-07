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
    suspend fun insert(item: SpeedDial)

    @Update
    suspend fun update(item: SpeedDial)

    @Query("DELETE FROM speed_dials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM speed_dials")
    suspend fun nextPosition(): Int
}

@Database(
    entities = [Bookmark::class, HistoryItem::class, SpeedDial::class],
    version = 2,
    exportSchema = false,
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun speedDialDao(): SpeedDialDao

    companion object {
        @Volatile private var instance: BrowserDatabase? = null

        private val DEFAULT_TILES = listOf(
            "네이버" to "https://m.naver.com",
            "구글" to "https://www.google.com",
            "유튜브" to "https://m.youtube.com",
            "다음" to "https://m.daum.net",
            "쿠팡" to "https://m.coupang.com",
            "위키백과" to "https://ko.m.wikipedia.org",
        )

        private fun seed(db: SupportSQLiteDatabase) {
            DEFAULT_TILES.forEachIndexed { index, (title, url) ->
                db.execSQL(
                    "INSERT INTO speed_dials (title, url, position) VALUES (?, ?, ?)",
                    arrayOf<Any>(title, url, index),
                )
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
                seed(db)
            }
        }

        fun get(context: Context): BrowserDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, BrowserDatabase::class.java, "browser.db"
            )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    // Fresh installs only; upgraded databases are seeded by the migration.
                    override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
                })
                .build()
                .also { instance = it }
        }
    }
}
