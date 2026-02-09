package com.quran.mobile.notes.model

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quran.data.dao.NotesDao
import com.quran.data.di.AppScope
import com.quran.data.model.note.Note
import com.quran.data.model.note.NoteLabel
import com.quran.data.model.note.NoteWithLabels
import com.quran.mobile.notes.NotesDatabase
import com.quran.mobile.notes.mapper.convergeCommonlyLabeled
import com.quran.mobile.notes.mapper.NoteMappers
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
class NotesDaoImpl @Inject constructor(
  notesDatabase: NotesDatabase
) : NotesDao {
  private val noteQueries = notesDatabase.noteQueries
  private val noteLabelQueries = notesDatabase.noteLabelQueries
  private val noteLabelMapQueries = notesDatabase.noteLabelMapQueries

  private val internalChanges = MutableStateFlow<Long?>(null)
  override val changes: Flow<Long> = internalChanges.filterNotNull()

  private var predefinedLabelsSeeded = false

  override suspend fun addNote(sura: Int, ayah: Int, page: Int, text: String, parentNoteId: Long?): Long {
    return withContext(Dispatchers.IO) {
      noteQueries.transactionWithResult {
        noteQueries.addNote(sura, ayah, page, text, parentNoteId)
        noteQueries.lastInsertedId().executeAsOne()
      }.also {
        internalChanges.value = System.currentTimeMillis()
      }
    }
  }

  override suspend fun updateNote(noteId: Long, text: String) {
    withContext(Dispatchers.IO) {
      noteQueries.updateNote(text, noteId)
      internalChanges.value = System.currentTimeMillis()
    }
  }

  override suspend fun deleteNote(noteId: Long) {
    withContext(Dispatchers.IO) {
      noteQueries.transactionWithResult {
        noteLabelMapQueries.deleteLabelsForNote(noteId)
        noteQueries.deleteNote(noteId)
      }
      internalChanges.value = System.currentTimeMillis()
    }
  }

  override suspend fun getNoteById(noteId: Long): NoteWithLabels? {
    return withContext(Dispatchers.IO) {
      val notes = noteQueries.getNoteById(noteId, NoteMappers.noteWithLabelMapper)
        .executeAsList()
        .convergeCommonlyLabeled()
      notes.firstOrNull()?.let { resolveNoteWithLabels(it) }
    }
  }

  override suspend fun allNotesSortedByUpdated(): List<NoteWithLabels> {
    return withContext(Dispatchers.IO) {
      noteQueries.getAllNotesByUpdated(NoteMappers.noteWithLabelMapper)
        .executeAsList()
        .convergeCommonlyLabeled()
        .map { resolveNoteWithLabels(it) }
    }
  }

  override fun notesForSuraAyah(sura: Int, ayah: Int): Flow<List<NoteWithLabels>> {
    return noteQueries.getNotesBySuraAyah(sura, ayah, NoteMappers.noteWithLabelMapper)
      .asFlow()
      .mapToList(Dispatchers.IO)
      .map { notes ->
        notes.convergeCommonlyLabeled()
          .map { resolveNoteWithLabels(it) }
      }
  }

  override suspend fun notesBySura(sura: Int): List<NoteWithLabels> {
    return withContext(Dispatchers.IO) {
      noteQueries.getNotesBySura(sura, NoteMappers.noteWithLabelMapper)
        .executeAsList()
        .convergeCommonlyLabeled()
        .map { resolveNoteWithLabels(it) }
    }
  }

  override suspend fun notesByLabel(labelId: Long): List<NoteWithLabels> {
    return withContext(Dispatchers.IO) {
      noteQueries.getNotesByLabel(labelId, NoteMappers.noteWithLabelMapper)
        .executeAsList()
        .convergeCommonlyLabeled()
        .map { resolveNoteWithLabels(it) }
    }
  }

  override suspend fun childNotes(parentNoteId: Long): List<NoteWithLabels> {
    return withContext(Dispatchers.IO) {
      noteQueries.getChildNotes(parentNoteId, NoteMappers.noteWithLabelMapper)
        .executeAsList()
        .convergeCommonlyLabeled()
        .map { resolveNoteWithLabels(it) }
    }
  }

  override suspend fun searchNotes(query: String): List<NoteWithLabels> {
    return withContext(Dispatchers.IO) {
      noteQueries.searchNotes(query, NoteMappers.noteWithLabelMapper)
        .executeAsList()
        .convergeCommonlyLabeled()
        .map { resolveNoteWithLabels(it) }
    }
  }

  override suspend fun allLabels(): List<NoteLabel> {
    return withContext(Dispatchers.IO) {
      if (!predefinedLabelsSeeded) {
        seedPredefinedLabels()
        predefinedLabelsSeeded = true
      }
      noteLabelQueries.getAllLabels(NoteMappers.labelMapper).executeAsList()
    }
  }

  override suspend fun addLabel(name: String): Long {
    return withContext(Dispatchers.IO) {
      noteLabelQueries.transactionWithResult {
        noteLabelQueries.addLabel(name, 0)
        noteLabelQueries.lastInsertedId().executeAsOne()
      }.also {
        internalChanges.value = System.currentTimeMillis()
      }
    }
  }

  override suspend fun updateLabel(labelId: Long, name: String) {
    withContext(Dispatchers.IO) {
      noteLabelQueries.updateLabel(name, labelId)
      internalChanges.value = System.currentTimeMillis()
    }
  }

  override suspend fun deleteLabel(labelId: Long) {
    withContext(Dispatchers.IO) {
      noteLabelMapQueries.deleteByLabelId(labelId)
      noteLabelQueries.deleteLabel(labelId)
      internalChanges.value = System.currentTimeMillis()
    }
  }

  override suspend fun setLabelsForNote(noteId: Long, labelIds: List<Long>) {
    withContext(Dispatchers.IO) {
      noteLabelMapQueries.transaction {
        noteLabelMapQueries.deleteLabelsForNote(noteId)
        labelIds.forEach { labelId ->
          noteLabelMapQueries.addNoteLabel(noteId, labelId)
        }
      }
      internalChanges.value = System.currentTimeMillis()
    }
  }

  override suspend fun seedPredefinedLabels() {
    withContext(Dispatchers.IO) {
      val predefinedNames = listOf("Thought", "Question", "Reflection", "Reminder")
      predefinedNames.forEach { name ->
        noteLabelQueries.addLabelIfNotExists(name, 1)
      }
    }
  }

  private fun resolveNoteWithLabels(note: Note): NoteWithLabels {
    val labels = if (note.labels.isNotEmpty()) {
      val allLabels = noteLabelQueries.getAllLabels(NoteMappers.labelMapper).executeAsList()
      val labelMap = allLabels.associateBy { it.id }
      note.labels.mapNotNull { labelMap[it] }
    } else {
      emptyList()
    }
    val childCount = noteQueries.getChildCount(note.id).executeAsOne()
    return NoteWithLabels(note, labels, childCount.toInt())
  }
}
