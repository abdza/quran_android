package com.quran.mobile.wordbyword

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.wordbyword.data.WordByWordDatabase
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.CRC32

@SingleIn(AppScope::class)
class WordByWordDatabaseProvider @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranFileManager: QuranFileManager
) {
  private val databaseName = "word_by_word_en.db"
  private val databasePath = File(quranFileManager.databaseDirectory(), databaseName)
  private var cachedDatabase: WordByWordDatabase? = null
  private val rootMeaningsAsset = "root_meanings.json"
  private val checksumKey = "root_meanings_checksum"

  private suspend fun ensureWordByWordDatabase(): Boolean {
    return withContext(Dispatchers.IO) {
      // Check if database exists and has the required tables
      if (databasePath.exists()) {
        if (isDatabaseValid()) {
          return@withContext true
        } else {
          // Database is corrupted or missing tables, delete and re-copy
          databasePath.delete()
        }
      }

      // Copy from assets
      runCatching {
        quranFileManager.copyFromAssetsRelative(
          databaseName,
          databaseName,
          quranFileManager.databaseDirectory()
        )
      }.isSuccess
    }
  }

  private fun isDatabaseValid(): Boolean {
    return try {
      val db = SQLiteDatabase.openDatabase(
        databasePath.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY
      )
      db.use { database ->
        val cursor = database.rawQuery(
          "SELECT name FROM sqlite_master WHERE type='table' AND name='word_translations'",
          null
        )
        cursor.use { it.count > 0 }
      }
    } catch (e: Exception) {
      false
    }
  }

  suspend fun provideDatabase(): WordByWordDatabase? {
    val cached = cachedDatabase
    return cached
      ?: if (ensureWordByWordDatabase()) {
        val filePath = databasePath.absolutePath
        // Use a custom callback that doesn't try to create tables since the database
        // is pre-populated and copied from assets
        val driver = AndroidSqliteDriver(
          schema = WordByWordDatabase.Schema,
          context = appContext,
          name = filePath,
          callback = object : AndroidSqliteDriver.Callback(WordByWordDatabase.Schema) {
            override fun onCreate(db: SupportSQLiteDatabase) {
              // Don't create tables - the database is pre-populated
            }
            override fun onUpgrade(
              db: SupportSQLiteDatabase,
              oldVersion: Int,
              newVersion: Int
            ) {
              // No migrations needed for pre-populated database
            }
            override fun onOpen(db: SupportSQLiteDatabase) {
              super.onOpen(db)
              // Load root meanings from the bundled asset when it has changed
              ensureRootMeaningsTable(db)
            }
          }
        )
        val database = WordByWordDatabase(driver)
        cachedDatabase = database
        database
      } else {
        null
      }
  }

  fun isDatabaseAvailable(): Boolean = databasePath.exists()

  private fun ensureRootMeaningsTable(db: SupportSQLiteDatabase) {
    try {
      db.execSQL(
        """
          CREATE TABLE IF NOT EXISTS root_meanings(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            root TEXT NOT NULL UNIQUE,
            primary_meaning TEXT NOT NULL,
            extended_meaning TEXT,
            quran_usage TEXT,
            notes TEXT
          )
        """.trimIndent()
      )
      db.execSQL("CREATE TABLE IF NOT EXISTS root_meanings_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")

      // Reseed only when the bundled asset changes, so edits ship without a manual version bump
      val assetBytes = appContext.assets.open(rootMeaningsAsset).use { it.readBytes() }
      val checksum = CRC32().apply { update(assetBytes) }.value.toString()
      val storedChecksum = db.query(
        "SELECT value FROM root_meanings_meta WHERE key = ?",
        arrayOf(checksumKey)
      ).use { if (it.moveToFirst()) it.getString(0) else null }

      if (storedChecksum != checksum) {
        seedRootMeanings(db, String(assetBytes, Charsets.UTF_8), checksum)
      }
    } catch (e: Exception) {
      // Ignore errors - root meanings are optional
    }
  }

  private fun seedRootMeanings(db: SupportSQLiteDatabase, json: String, checksum: String) {
    val entries = JSONArray(json)
    db.beginTransaction()
    try {
      db.execSQL("DELETE FROM root_meanings")
      for (i in 0 until entries.length()) {
        val entry = entries.getJSONObject(i)
        db.execSQL(
          """INSERT OR REPLACE INTO root_meanings (root, primary_meaning, extended_meaning, quran_usage, notes)
             VALUES (?, ?, ?, ?, ?)""",
          arrayOf(
            entry.getString("root"),
            entry.getString("primaryMeaning"),
            entry.optStringOrNull("extendedMeaning"),
            entry.optStringOrNull("quranUsage"),
            entry.optStringOrNull("notes")
          )
        )
      }
      db.execSQL(
        "INSERT OR REPLACE INTO root_meanings_meta(key, value) VALUES (?, ?)",
        arrayOf(checksumKey, checksum)
      )
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
}
