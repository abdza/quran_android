package com.quran.mobile.bookmark

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.quran.labs.androidquran.BookmarksDatabase

/**
 * Creates an in-memory [BookmarksDatabase]. Robolectric tests should pass an
 * `AndroidSqliteDriver` instead of relying on the JDBC default, since registering
 * the JDBC driver from a Robolectric sandbox hides it from plain JUnit tests.
 */
fun inMemoryBookmarksDatabase(
  driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
    BookmarksDatabase.Schema.create(it)
  }
): BookmarksDatabase {
  return BookmarksDatabase(
    driver,
    Bookmarks.Adapter(IntColumnAdapter, IntColumnAdapter, IntColumnAdapter),
    Last_pages.Adapter(IntColumnAdapter),
    Session_pages.Adapter(IntColumnAdapter)
  )
}
