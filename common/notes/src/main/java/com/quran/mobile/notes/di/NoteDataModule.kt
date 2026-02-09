package com.quran.mobile.notes.di

import android.content.Context
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.di.AppScope
import com.quran.mobile.notes.Notes
import com.quran.mobile.notes.NotesDatabase
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
class NoteDataModule {

  @SingleIn(AppScope::class)
  @Provides
  fun provideNotesDatabase(@ApplicationContext context: Context): NotesDatabase {
    val driver: SqlDriver = AndroidSqliteDriver(
      schema = NotesDatabase.Schema,
      context = context,
      name = "notes.db"
    )
    return NotesDatabase(
      driver,
      Notes.Adapter(IntColumnAdapter, IntColumnAdapter, IntColumnAdapter)
    )
  }
}
