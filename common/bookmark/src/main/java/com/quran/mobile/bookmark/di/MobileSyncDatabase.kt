package com.quran.mobile.bookmark.di

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.migration.MobileSyncLegacySchema
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.shared.persistence.DriverFactory
import com.quran.shared.persistence.QuranDatabase
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class MobileSyncDatabase @Inject constructor(@ApplicationContext context: Context) {
  internal val database: QuranDatabase

  init {
    // A 0.1.14 database would crash on first query; MobileSyncLegacySchemaUpgrader imports it later
    MobileSyncLegacySchema.moveAsideIfLegacy(context)
    database = QuranDatabase(DriverFactory(context).makeDriver())
  }
}
