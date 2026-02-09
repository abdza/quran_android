package com.quran.mobile.feature.notes.di

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface NotesComponentInterface {
  fun notesComponentFactory(): NotesComponent.Factory
}
