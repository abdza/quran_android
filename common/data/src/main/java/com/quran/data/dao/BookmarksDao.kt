package com.quran.data.dao

import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.SessionPage
import kotlinx.coroutines.flow.Flow

interface BookmarksDao {
  val changes: Flow<Long>

  suspend fun bookmarks(): List<Bookmark>
  fun bookmarksForPage(page: Int): Flow<List<Bookmark>>
  fun pageBookmarksWithoutTags(): Flow<List<Bookmark>>
  suspend fun replaceBookmarks(bookmarks: List<Bookmark>)
  suspend fun removeBookmarksForPage(page: Int)
  suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean
  suspend fun togglePageBookmark(page: Int): Boolean
  suspend fun toggleAyahBookmark(suraAyah: SuraAyah, page: Int): Boolean

  // recent pages
  suspend fun recentPages(): List<RecentPage>
  suspend fun removeRecentPages()
  suspend fun replaceRecentPages(pages: List<RecentPage>)
  suspend fun removeRecentsForPage(page: Int)

  // session pages
  suspend fun lastSessions(limit: Int = 5): List<SessionPage>
  suspend fun saveSessionPage(page: Int, sessionStart: Long)
}
