package com.quran.mobile.wordbyword

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.wordbyword.data.WordByWordDatabase
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@SingleIn(AppScope::class)
class WordByWordDatabaseProvider @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranFileManager: QuranFileManager
) {
  private val databaseName = "word_by_word_en.db"
  private val databasePath = File(quranFileManager.databaseDirectory(), databaseName)
  private var cachedDatabase: WordByWordDatabase? = null

  private suspend fun ensureWordByWordDatabase(): Boolean {
    return if (databasePath.exists()) {
      true
    } else {
      withContext(Dispatchers.IO) {
        runCatching {
          quranFileManager.copyFromAssetsRelative(
            databaseName,
            databaseName,
            quranFileManager.databaseDirectory()
          )
        }.isSuccess
      }
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
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
              // Don't create tables - the database is pre-populated
            }
            override fun onUpgrade(
              db: androidx.sqlite.db.SupportSQLiteDatabase,
              oldVersion: Int,
              newVersion: Int
            ) {
              // No migrations needed for pre-populated database
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
}
