package com.quran.mobile.bookmark.migration

import android.content.Context
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports a quran.db that [MobileSyncLegacySchema] moved aside because it used the mobile-sync
 * 0.1.14 schema. Injecting [MobileSyncImporter] opens the new database, which is what moves the
 * old one aside, so the pending file is always in place by the time this runs.
 */
@SingleIn(AppScope::class)
class MobileSyncLegacySchemaUpgrader @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val importer: MobileSyncImporter,
  private val readingBookmarksDao: ReadingBookmarksDao
) {

  suspend fun importIfNeeded() {
    withContext(Dispatchers.IO) {
      val legacyDatabase = MobileSyncLegacySchema.pendingLegacyDatabase(context) ?: return@withContext
      val data = MobileSyncLegacySchema.readLegacyData(legacyDatabase)
      if (!data.importData.isEmpty()) {
        importer.importData(data.importData, deleteExisting = false)
      }
      // Mark it imported before the reading bookmark, so a failure there can't repeat the import
      MobileSyncLegacySchema.markImported(context)

      // 0.1.14 had a single reading bookmark; it becomes the first slot
      val slot = ReadingBookmarkType.entries.first()
      when (val readingBookmark = data.readingBookmark) {
        is LegacyReadingBookmark.Ayah ->
          readingBookmarksDao.setAyahReadingBookmark(slot, SuraAyah(readingBookmark.sura, readingBookmark.ayah))
        is LegacyReadingBookmark.Page ->
          readingBookmarksDao.setPageReadingBookmark(slot, readingBookmark.page)
        null -> Unit
      }
    }
  }
}
