package com.quran.mobile.notes.mapper

import com.quran.data.model.note.Note

fun List<Note>.convergeCommonlyLabeled(): List<Note> {
  return groupBy { it.id }
    .map {
      val firstNote = it.value.first()
      if (it.value.size == 1) {
        firstNote
      } else {
        val labelIds = it.value.fold(mutableListOf<Long>()) { acc, note ->
          acc.apply { addAll(note.labels) }
        }
        firstNote.copy(labels = labelIds)
      }
    }
}
