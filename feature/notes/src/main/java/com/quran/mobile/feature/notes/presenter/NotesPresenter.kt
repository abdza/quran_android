package com.quran.mobile.feature.notes.presenter

import com.quran.data.dao.NotesDao
import com.quran.data.model.note.NoteLabel
import com.quran.data.model.note.NoteWithLabels
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotesPresenter @Inject constructor(
  private val notesDao: NotesDao
) {
  private val _notesList = MutableStateFlow<List<NoteWithLabels>>(emptyList())
  val notesList: StateFlow<List<NoteWithLabels>> = _notesList.asStateFlow()

  private val _labels = MutableStateFlow<List<NoteLabel>>(emptyList())
  val labels: StateFlow<List<NoteLabel>> = _labels.asStateFlow()

  private val _filterLabel = MutableStateFlow<Long?>(null)
  val filterLabel: StateFlow<Long?> = _filterLabel.asStateFlow()

  private val _filterSura = MutableStateFlow<Int?>(null)
  val filterSura: StateFlow<Int?> = _filterSura.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  fun notesForSuraAyah(sura: Int, ayah: Int): Flow<List<NoteWithLabels>> {
    return notesDao.notesForSuraAyah(sura, ayah)
  }

  suspend fun loadNotes() {
    val labelFilter = _filterLabel.value
    val suraFilter = _filterSura.value
    val query = _searchQuery.value
    _notesList.value = when {
      query.isNotBlank() -> notesDao.searchNotes(query)
      labelFilter != null -> notesDao.notesByLabel(labelFilter)
      suraFilter != null -> notesDao.notesBySura(suraFilter)
      else -> notesDao.allNotesSortedByUpdated()
    }
  }

  suspend fun loadLabels() {
    _labels.value = notesDao.allLabels()
  }

  suspend fun addNote(sura: Int, ayah: Int, page: Int, text: String, parentNoteId: Long? = null): Long {
    val noteId = notesDao.addNote(sura, ayah, page, text, parentNoteId)
    loadNotes()
    return noteId
  }

  suspend fun updateNote(noteId: Long, text: String) {
    notesDao.updateNote(noteId, text)
    loadNotes()
  }

  suspend fun deleteNote(noteId: Long) {
    notesDao.deleteNote(noteId)
    loadNotes()
  }

  suspend fun addLabel(name: String): Long {
    val labelId = notesDao.addLabel(name)
    loadLabels()
    return labelId
  }

  suspend fun setLabelsForNote(noteId: Long, labelIds: List<Long>) {
    notesDao.setLabelsForNote(noteId, labelIds)
    loadNotes()
  }

  suspend fun childNotes(parentNoteId: Long): List<NoteWithLabels> {
    return notesDao.childNotes(parentNoteId)
  }

  suspend fun getNoteById(noteId: Long): NoteWithLabels? {
    return notesDao.getNoteById(noteId)
  }

  fun filterByLabel(labelId: Long?) {
    _filterLabel.value = labelId
    _filterSura.value = null
  }

  fun filterBySura(sura: Int?) {
    _filterSura.value = sura
    _filterLabel.value = null
  }

  fun search(query: String) {
    _searchQuery.value = query
    if (query.isNotBlank()) {
      _filterLabel.value = null
      _filterSura.value = null
    }
  }

  fun clearFilters() {
    _filterLabel.value = null
    _filterSura.value = null
    _searchQuery.value = ""
  }
}
