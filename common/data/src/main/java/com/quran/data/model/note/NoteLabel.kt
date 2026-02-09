package com.quran.data.model.note

data class NoteLabel(
  val id: Long,
  val name: String,
  val isPredefined: Boolean = false
)
