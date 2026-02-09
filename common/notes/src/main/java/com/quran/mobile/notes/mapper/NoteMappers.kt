package com.quran.mobile.notes.mapper

import com.quran.data.model.note.Note
import com.quran.data.model.note.NoteLabel

object NoteMappers {
  val noteWithLabelMapper: ((
    id: Long,
    sura: Int,
    ayah: Int,
    page: Int,
    text: String,
    parentNoteId: Long?,
    createdDate: Long,
    updatedDate: Long,
    labelId: Long?
  ) -> Note) = { id, sura, ayah, page, text, parentNoteId, createdDate, updatedDate, labelId ->
    val labels = if (labelId == null) emptyList() else listOf(labelId)
    Note(id, sura, ayah, page, text, parentNoteId, createdDate, updatedDate, labels)
  }

  val labelMapper: ((id: Long, name: String, isPredefined: Long, addedDate: Long) -> NoteLabel) =
    { id, name, isPredefined, _ -> NoteLabel(id, name, isPredefined != 0L) }
}
