package com.quran.mobile.feature.notes

import android.content.Context
import android.content.Intent
import com.quran.mobile.di.ExtraScreenProvider

object NotesScreenProvider : ExtraScreenProvider {
  override val order = 1
  override val id = R.id.notes_menu_item
  override val titleResId = R.string.notes_title

  override fun onClick(context: Context): Boolean {
    val intent = Intent(context, NotesActivity::class.java)
    context.startActivity(intent)
    return true
  }
}
