package com.quran.labs.androidquran.di.module.application

import com.quran.mobile.wordbyword.WordByWordDatabaseProvider
import com.quran.mobile.wordbyword.WordTranslationDataSource
import dev.zacsweers.metro.BindingContainer

@BindingContainer
interface WordByWordModule {
  // These classes use @Inject constructors and @SingleIn(AppScope),
  // so Metro will create them automatically. We just need to reference them
  // so Metro knows they exist in the dependency graph.

  val wordByWordDatabaseProvider: WordByWordDatabaseProvider
  val wordTranslationDataSource: WordTranslationDataSource
}
