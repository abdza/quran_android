package com.quran.labs.androidquran.model.session

import com.quran.data.dao.BookmarksDao
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.SessionPage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SingleIn(AppScope::class)
class ReadingSessionManager @Inject constructor(
  private val bookmarksDao: BookmarksDao
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var sessionActive = false
  private var sessionStartTime: Long = 0L
  private var lastKnownPage: Int = -1

  private val _lastSessions = MutableStateFlow<List<SessionPage>>(emptyList())
  val lastSessions: StateFlow<List<SessionPage>> = _lastSessions.asStateFlow()

  init {
    refreshSessions()
  }

  fun startSession() {
    sessionActive = true
    sessionStartTime = System.currentTimeMillis() / 1000
    lastKnownPage = -1
  }

  fun updateCurrentPage(page: Int) {
    if (sessionActive && page > 0) {
      lastKnownPage = page
      // Save immediately so data persists even if app is killed
      scope.launch {
        bookmarksDao.saveSessionPage(page, sessionStartTime)
      }
    }
  }

  fun endSession() {
    if (sessionActive && lastKnownPage > 0) {
      // Final save and refresh the sessions list
      scope.launch {
        bookmarksDao.saveSessionPage(lastKnownPage, sessionStartTime)
        refreshSessions()
      }
    }
    sessionActive = false
    sessionStartTime = 0L
    lastKnownPage = -1
  }

  fun refreshSessions() {
    scope.launch {
      _lastSessions.value = bookmarksDao.lastSessions(MAX_SESSIONS)
    }
  }

  companion object {
    const val MAX_SESSIONS = 5
  }
}
