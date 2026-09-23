package com.quran.mobile.bookmark.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.quran.mobile.bookmark.importdata.MobileSyncImportBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollection
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollectionBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingSession
import java.io.File
import java.io.IOException

/**
 * mobile-sync 0.1.20 reshaped the quran.db tables without bumping the schema version or shipping
 * a migration, so a database created by 0.1.14 fails on its first query. Such a database is moved
 * aside before the library opens quran.db, and its data is imported into the new database by
 * [MobileSyncLegacySchemaUpgrader]. The moved file is kept afterwards as a backup.
 */
object MobileSyncLegacySchema {
  const val DATABASE_NAME = "quran.db"
  const val LEGACY_DATABASE_NAME = "quran-mobile-sync-0.1.14.db"
  const val IMPORTED_DATABASE_NAME = "quran-mobile-sync-0.1.14-imported.db"

  private val DATABASE_FILE_SUFFIXES = listOf("", "-journal", "-wal", "-shm")

  /**
   * Moves a 0.1.14 quran.db aside so the library creates a fresh one. Returns true if moved.
   * Every place that opens quran.db (MobileSyncDatabase and the sync graph) must call this first.
   */
  @Synchronized
  fun moveAsideIfLegacy(context: Context): Boolean {
    val database = context.getDatabasePath(DATABASE_NAME)
    if (!database.exists() || !isLegacySchema(database)) return false
    renameDatabase(database, context.getDatabasePath(LEGACY_DATABASE_NAME))
    return true
  }

  fun pendingLegacyDatabase(context: Context): File? =
    context.getDatabasePath(LEGACY_DATABASE_NAME).takeIf { it.exists() }

  fun markImported(context: Context) {
    renameDatabase(
      context.getDatabasePath(LEGACY_DATABASE_NAME),
      context.getDatabasePath(IMPORTED_DATABASE_NAME)
    )
  }

  internal fun isLegacySchema(database: File): Boolean {
    return try {
      openDatabase(database).use { db ->
        db.rawQuery("PRAGMA table_info(bookmark)", null).use { cursor ->
          val nameIndex = cursor.getColumnIndexOrThrow("name")
          generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
            .any { it == "bookmark_type" }
        }
      }
    } catch (e: Exception) {
      // Unreadable databases are left for the library to handle
      false
    }
  }

  fun readLegacyData(database: File): LegacyMobileSyncData {
    return openDatabase(database).use { db ->
      // Newest first, so de-duplication keeps the latest row for each ayah
      val bookmarkRows = db.query(
        """SELECT local_id, sura, ayah, modified_at FROM bookmark b
           WHERE b.deleted = 0 AND b.bookmark_type = 'AYAH'
             AND b.sura IS NOT NULL AND b.ayah IS NOT NULL
             AND (b.is_in_default_collection = 1 OR EXISTS (
               SELECT 1 FROM bookmark_collection bc
               WHERE bc.bookmark_local_id = b.local_id AND bc.is_active = 1))
           ORDER BY b.modified_at DESC"""
      ) { LegacyBookmarkRow(it.getLong(0), it.getInt(1), it.getInt(2), it.getLong(3)) }

      val importIdByLocalId = mutableMapOf<Long, String>()
      val bookmarks = mutableListOf<MobileSyncImportBookmark>()
      bookmarkRows.groupBy { it.sura to it.ayah }.values.forEach { rows ->
        val latest = rows.first()
        val importId = "legacy-bookmark-${latest.localId}"
        rows.forEach { importIdByLocalId[it.localId] = importId }
        bookmarks += MobileSyncImportBookmark(importId, latest.sura, latest.ayah, latest.modifiedAt)
      }

      val collections = db.query(
        "SELECT local_id, name, modified_at FROM collection WHERE deleted = 0 AND trim(name) != ''"
      ) { MobileSyncImportCollection("legacy-collection-${it.getLong(0)}", it.getString(1), it.getLong(2)) }
      val collectionIds = collections.map { it.importId }.toSet()

      val collectionBookmarks = db.query(
        """SELECT collection_local_id, bookmark_local_id, modified_at FROM bookmark_collection
           WHERE is_active = 1 ORDER BY modified_at DESC"""
      ) { cursor ->
        val bookmarkImportId = importIdByLocalId[cursor.getLong(1)]
        val collectionImportId = "legacy-collection-${cursor.getLong(0)}"
        if (bookmarkImportId != null && collectionImportId in collectionIds) {
          MobileSyncImportCollectionBookmark(collectionImportId, bookmarkImportId, cursor.getLong(2))
        } else {
          null
        }
      }.filterNotNull().distinctBy { it.collectionImportId to it.bookmarkImportId }

      val readingSessions = db.query(
        """SELECT chapter_number, verse_number, modified_at FROM reading_session
           WHERE deleted = 0 ORDER BY modified_at DESC"""
      ) { MobileSyncImportReadingSession(it.getInt(0), it.getInt(1), it.getLong(2)) }
        .distinctBy { it.sura to it.ayah }

      val readingBookmark = db.query(
        """SELECT bookmark_type, sura, ayah, page FROM bookmark
           WHERE deleted = 0 AND is_reading = 1
           ORDER BY COALESCE(reading_modified_at, modified_at) DESC LIMIT 1"""
      ) { cursor ->
        when {
          cursor.getString(0) == "PAGE" && !cursor.isNull(3) ->
            LegacyReadingBookmark.Page(cursor.getInt(3))
          !cursor.isNull(1) && !cursor.isNull(2) ->
            LegacyReadingBookmark.Ayah(cursor.getInt(1), cursor.getInt(2))
          else -> null
        }
      }.firstOrNull()

      LegacyMobileSyncData(
        importData = MobileSyncImportData(
          bookmarks = bookmarks,
          collections = collections,
          collectionBookmarks = collectionBookmarks,
          readingSessions = readingSessions
        ),
        readingBookmark = readingBookmark
      )
    }
  }

  // Read-write so SQLite can roll back a leftover journal; nothing here writes to the file
  private fun openDatabase(database: File): SQLiteDatabase =
    SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE)

  private fun <T> SQLiteDatabase.query(sql: String, mapper: (Cursor) -> T): List<T> =
    rawQuery(sql, null).use { cursor ->
      buildList { while (cursor.moveToNext()) add(mapper(cursor)) }
    }

  private fun renameDatabase(from: File, to: File) {
    DATABASE_FILE_SUFFIXES.forEach { suffix ->
      val source = File(from.path + suffix)
      if (source.exists() && !source.renameTo(File(to.path + suffix))) {
        throw IOException("Unable to move ${source.name} to ${to.name}$suffix")
      }
    }
  }

  private data class LegacyBookmarkRow(val localId: Long, val sura: Int, val ayah: Int, val modifiedAt: Long)
}

data class LegacyMobileSyncData(
  val importData: MobileSyncImportData,
  val readingBookmark: LegacyReadingBookmark?
)

sealed interface LegacyReadingBookmark {
  data class Ayah(val sura: Int, val ayah: Int) : LegacyReadingBookmark
  data class Page(val page: Int) : LegacyReadingBookmark
}
