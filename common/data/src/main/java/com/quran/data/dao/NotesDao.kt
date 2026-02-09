package com.quran.data.dao

import com.quran.data.model.note.Note
import com.quran.data.model.note.NoteLabel
import com.quran.data.model.note.NoteWithLabels
import kotlinx.coroutines.flow.Flow

interface NotesDao {
  val changes: Flow<Long>

  // notes CRUD
  suspend fun addNote(sura: Int, ayah: Int, page: Int, text: String, parentNoteId: Long? = null): Long
  suspend fun updateNote(noteId: Long, text: String)
  suspend fun deleteNote(noteId: Long)
  suspend fun getNoteById(noteId: Long): NoteWithLabels?

  // notes listing
  suspend fun allNotesSortedByUpdated(): List<NoteWithLabels>
  fun notesForSuraAyah(sura: Int, ayah: Int): Flow<List<NoteWithLabels>>
  suspend fun notesBySura(sura: Int): List<NoteWithLabels>
  suspend fun notesByLabel(labelId: Long): List<NoteWithLabels>
  suspend fun childNotes(parentNoteId: Long): List<NoteWithLabels>
  suspend fun searchNotes(query: String): List<NoteWithLabels>

  // labels
  suspend fun allLabels(): List<NoteLabel>
  suspend fun addLabel(name: String): Long
  suspend fun updateLabel(labelId: Long, name: String)
  suspend fun deleteLabel(labelId: Long)
  suspend fun setLabelsForNote(noteId: Long, labelIds: List<Long>)

  // seed predefined labels
  suspend fun seedPredefinedLabels()
}
