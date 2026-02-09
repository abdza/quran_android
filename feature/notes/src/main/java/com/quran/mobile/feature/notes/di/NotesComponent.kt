package com.quran.mobile.feature.notes.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.notes.NotesActivity
import com.quran.mobile.feature.notes.NotesListFragment
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface NotesComponent {
  fun inject(activity: NotesActivity)
  fun inject(fragment: NotesListFragment)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): NotesComponent
  }
}
