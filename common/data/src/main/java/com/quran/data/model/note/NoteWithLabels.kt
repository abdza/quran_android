package com.quran.data.model.note

data class NoteWithLabels(
  val note: Note,
  val labels: List<NoteLabel>,
  val childCount: Int = 0
)
