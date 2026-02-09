package com.quran.mobile.di

import android.content.Context

interface NoteShareTextProvider {
  suspend fun getShareText(
    context: Context,
    sura: Int,
    ayah: Int,
    noteText: String,
    labels: List<String>
  ): String
}
