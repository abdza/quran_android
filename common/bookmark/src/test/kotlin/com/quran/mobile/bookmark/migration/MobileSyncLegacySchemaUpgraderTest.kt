package com.quran.mobile.bookmark.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.importdata.MobileSyncImporterImpl
import com.quran.mobile.bookmark.inMemoryBookmarksDatabase
import com.quran.mobile.bookmark.model.BookmarksDaoImpl
import com.quran.mobile.bookmark.model.RepositoryBackedTestBookmarkCollectionsState
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepositoryImpl
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepositoryImpl
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MobileSyncLegacySchemaUpgraderTest {

  private val context = RuntimeEnvironment.getApplication().applicationContext as Context
  private lateinit var appCoroutineScope: AppCoroutineScope

  @Before
  fun setUp() {
    listOf(
      MobileSyncLegacySchema.DATABASE_NAME,
      MobileSyncLegacySchema.LEGACY_DATABASE_NAME,
      MobileSyncLegacySchema.IMPORTED_DATABASE_NAME
    ).forEach { context.deleteDatabase(it) }
    appCoroutineScope = AppCoroutineScope()
  }

  @After
  fun tearDown() {
    appCoroutineScope.cancel()
  }

  @Test
  fun `a database with the current schema is left in place`() {
    MobileSyncDatabase(context)

    assertThat(MobileSyncLegacySchema.moveAsideIfLegacy(context)).isFalse()
    assertThat(MobileSyncLegacySchema.pendingLegacyDatabase(context)).isNull()
  }

  @Test
  fun `legacy data keeps saved ayah bookmarks, active links and the reading bookmark`() {
    createLegacyDatabase()
    MobileSyncLegacySchema.moveAsideIfLegacy(context)

    val data = MobileSyncLegacySchema.readLegacyData(MobileSyncLegacySchema.pendingLegacyDatabase(context)!!)

    // 3:1 is deleted and 36:1 is only the reading bookmark; the older 2:255 row is a duplicate
    assertThat(data.importData.bookmarks.map { it.sura to it.ayah }).containsExactly(2 to 255, 18 to 10)
    assertThat(data.importData.bookmarks.first { it.sura == 2 }.importId).isEqualTo("legacy-bookmark-1")
    assertThat(data.importData.collections.map { it.name }).containsExactly("Kahf")
    assertThat(data.importData.collectionBookmarks.map { it.collectionImportId to it.bookmarkImportId })
      .containsExactly("legacy-collection-1" to "legacy-bookmark-2")
    assertThat(data.importData.readingSessions.map { it.sura to it.ayah }).containsExactly(3 to 16, 1 to 1)
    assertThat(data.readingBookmark).isEqualTo(LegacyReadingBookmark.Ayah(36, 1))
  }

  @Test
  fun `legacy database is moved aside, imported and kept as a backup`() = runTest {
    createLegacyDatabase()

    // Opening the database moves the legacy file aside instead of crashing on the old tables
    val mobileSyncDatabase = MobileSyncDatabase(context)
    val readingBookmarksDao = RecordingReadingBookmarksDao()
    MobileSyncLegacySchemaUpgrader(
      context,
      MobileSyncImporterImpl(mobileSyncDatabase, context),
      readingBookmarksDao
    ).importIfNeeded()

    val bookmarksDao = bookmarksDao(mobileSyncDatabase)
    assertThat(bookmarksDao.bookmarks().map { it.sura to it.ayah }).containsExactly(2 to 255, 18 to 10)
    // The importer puts every bookmark in the default collection too, as the legacy migration does
    val tags = bookmarksDao.tags()
    val kahf = tags.single { it.name == "Kahf" }
    val default = tags.single { it.isDefault }
    assertThat(bookmarksDao.bookmarks().single { it.sura == 18 }.tags).containsExactly(default.id, kahf.id)
    assertThat(
      ReadingSessionsRepositoryImpl(mobileSyncDatabase.database).getReadingSessions()
        .map { it.sura to it.ayah }
    ).containsExactly(3 to 16, 1 to 1)
    assertThat(readingBookmarksDao.ayahBookmarks).containsExactly(ReadingBookmarkType.CORAL to SuraAyah(36, 1))

    assertThat(MobileSyncLegacySchema.pendingLegacyDatabase(context)).isNull()
    assertThat(context.getDatabasePath(MobileSyncLegacySchema.IMPORTED_DATABASE_NAME).exists()).isTrue()
  }

  private fun createLegacyDatabase() {
    val schema = javaClass.classLoader!!.getResource("mobile_sync_0_1_14_schema.sql")!!.readText()
    val file = context.getDatabasePath(MobileSyncLegacySchema.DATABASE_NAME).apply { parentFile!!.mkdirs() }
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
      schema.split(";\n").map { it.lines().filterNot { line -> line.startsWith("--") }.joinToString("\n").trim() }
        .filter { it.isNotEmpty() }
        .forEach { db.execSQL(it) }
      db.execSQL(
        """INSERT INTO bookmark (local_id, bookmark_type, sura, ayah, ayah_id, mushaf_id, is_reading,
             is_in_default_collection, deleted, modified_at) VALUES
           (1, 'AYAH', 2, 255, 262, 1, 0, 1, 0, 5000),
           (2, 'AYAH', 18, 10, 2150, 1, 0, 0, 0, 4000),
           (3, 'AYAH', 3, 1, 294, 1, 0, 1, 1, 3000),
           (4, 'AYAH', 36, 1, 3706, 1, 1, 0, 0, 2000),
           (5, 'AYAH', 2, 255, 262, 2, 0, 1, 0, 1000)"""
      )
      db.execSQL(
        """INSERT INTO collection (local_id, name, deleted, modified_at) VALUES
           (1, 'Kahf', 0, 4000), (2, 'Removed', 1, 4000)"""
      )
      db.execSQL(
        """INSERT INTO bookmark_collection (bookmark_local_id, collection_local_id, is_active, modified_at) VALUES
           (2, 1, 1, 4000), (1, 2, 1, 4000), (1, 1, 0, 4000)"""
      )
      db.execSQL(
        """INSERT INTO reading_session (chapter_number, verse_number, deleted, modified_at) VALUES
           (3, 16, 0, 3000), (1, 1, 0, 2000), (1, 1, 1, 1000), (4, 1, 1, 500)"""
      )
    }
  }

  private fun bookmarksDao(mobileSyncDatabase: MobileSyncDatabase): BookmarksDaoImpl {
    val collectionsRepository = CollectionsRepositoryImpl(mobileSyncDatabase.database)
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(mobileSyncDatabase.database)
    return BookmarksDaoImpl(
      quranInfoProvider = { QuranInfo(MadaniDataSource()) },
      bookmarksRepository = BookmarksRepositoryImpl(mobileSyncDatabase.database),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = RepositoryBackedTestBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
      timestampProvider = FakeMobileSyncTimestampProvider(),
      bookmarksDatabase = inMemoryBookmarksDatabase(
        AndroidSqliteDriver(BookmarksDatabase.Schema, context, null)
      ),
      appCoroutineScope = appCoroutineScope
    )
  }

  private class RecordingReadingBookmarksDao : ReadingBookmarksDao {
    val ayahBookmarks = mutableListOf<Pair<ReadingBookmarkType, SuraAyah>>()

    override suspend fun setAyahReadingBookmark(slot: ReadingBookmarkType, suraAyah: SuraAyah): Boolean {
      ayahBookmarks += slot to suraAyah
      return true
    }

    override fun readingBookmarksFlow(): Flow<List<ReadingBookmark>> = throw UnsupportedOperationException()
    override suspend fun readingBookmarks(): List<ReadingBookmark> = throw UnsupportedOperationException()
    override suspend fun setPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean =
      throw UnsupportedOperationException()
    override suspend fun clearReadingBookmark(slot: ReadingBookmarkType): ReadingBookmark =
      throw UnsupportedOperationException()
    override suspend fun renameReadingBookmark(slot: ReadingBookmarkType, name: String?) =
      throw UnsupportedOperationException()
    override suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean =
      throw UnsupportedOperationException()
    override suspend fun togglePageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean =
      throw UnsupportedOperationException()
    override suspend fun updateReadingBookmarks(
      ayah: SuraAyah,
      added: List<ReadingBookmark>,
      removed: List<ReadingBookmark>
    ): Boolean = throw UnsupportedOperationException()
  }
}
