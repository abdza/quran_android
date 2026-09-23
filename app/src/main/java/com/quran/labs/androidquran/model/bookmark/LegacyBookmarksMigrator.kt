package com.quran.labs.androidquran.model.bookmark

import com.quran.data.di.AppScope
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.migration.LegacyBookmarkMigrationNormalizer
import com.quran.mobile.bookmark.migration.LegacyBookmarksDataSource
import com.quran.mobile.bookmark.migration.MobileSyncLegacySchemaUpgrader
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@SingleIn(AppScope::class)
class LegacyBookmarksMigrator @Inject constructor(
  private val quranSettings: QuranSettings,
  private val legacyBookmarksDataSource: LegacyBookmarksDataSource,
  private val normalizer: LegacyBookmarkMigrationNormalizer,
  private val mobileSyncImporter: MobileSyncImporter,
  private val legacySchemaUpgrader: MobileSyncLegacySchemaUpgrader
) {
  private val migrationMutex = Mutex()

  suspend fun migrateIfNeeded(): Boolean {
    return migrationMutex.withLock {
      // Runs even after the legacy migration, since it also covers databases made by mobile-sync 0.1.14
      upgradeLegacyMobileSyncSchema()
      if (quranSettings.haveMigratedLegacyBookmarksToMobileSync()) {
        true
      } else {
        migrate()
      }
    }
  }

  private suspend fun upgradeLegacyMobileSyncSchema() {
    try {
      legacySchemaUpgrader.importIfNeeded()
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      // The old database stays in place, so the import is retried on the next launch
      Timber.e(exception, "Unable to import the mobile-sync 0.1.14 database.")
    }
  }

  private suspend fun migrate(): Boolean {
    return withContext(Dispatchers.IO) {
      try {
        val snapshot = legacyBookmarksDataSource.snapshot()
        if (!snapshot.isEmpty()) {
          val migrationData = normalizer.normalize(snapshot)
          if (!migrationData.isEmpty()) {
            mobileSyncImporter.importData(migrationData, deleteExisting = false)
          }
        }

        quranSettings.setMigratedLegacyBookmarksToMobileSync()
        true
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Timber.e(exception, "Unable to migrate legacy bookmarks to mobile-sync.")
        false
      }
    }
  }
}
