package com.quran.mobile.feature.notes

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.note.NoteWithLabels
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.startSuraAyah
import com.quran.labs.androidquran.common.audio.model.playback.currentPlaybackAyah
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.di.QuranReadingActivityComponentProvider
import com.quran.mobile.feature.notes.di.NotesFragmentInjector
import com.quran.mobile.feature.notes.presenter.NotesPresenter
import com.quran.mobile.feature.notes.ui.NoteEditorScreen
import com.quran.mobile.feature.notes.ui.NoteDetailScreen
import com.quran.mobile.feature.notes.ui.NotesForAyahScreen
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class AyahNoteFragment : Fragment() {
  private var scope: CoroutineScope = MainScope()

  @Inject
  lateinit var readingEventPresenter: ReadingEventPresenter

  @Inject
  lateinit var audioStatusRepository: AudioStatusRepository

  @Inject
  lateinit var notesPresenter: NotesPresenter

  @Inject
  lateinit var quranInfo: QuranInfo

  private var start by mutableStateOf<SuraAyah?>(null)

  override fun onAttach(context: Context) {
    super.onAttach(context)
    val injector = (activity as? QuranReadingActivityComponentProvider)
      ?.provideQuranReadingActivityComponent() as? NotesFragmentInjector
    injector?.inject(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    scope = MainScope()
    readingEventPresenter.ayahSelectionFlow
      .combine(audioStatusRepository.audioPlaybackFlow) { selectedAyah, playbackStatus ->
        val playbackAyah = playbackStatus.currentPlaybackAyah()
        val previousStart = start
        if (selectedAyah !is AyahSelection.None) {
          start = selectedAyah.startSuraAyah()
        } else if (playbackAyah != null) {
          start = playbackAyah
        }
        if (previousStart != start) {
          scope.launch { notesPresenter.loadLabels() }
        }
      }
      .launchIn(scope)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return ComposeView(requireContext()).apply {
      setContent {
        QuranTheme {
          val suraAyah = start
          if (suraAyah != null) {
            val notes by notesPresenter.notesForSuraAyah(suraAyah.sura, suraAyah.ayah)
              .collectAsState(initial = emptyList())
            val labels by notesPresenter.labels.collectAsState()

            var showEditor by remember { mutableStateOf(false) }
            var editingNote by remember { mutableStateOf<NoteWithLabels?>(null) }
            var viewingDetail by remember { mutableStateOf<NoteWithLabels?>(null) }
            var childNotes by remember { mutableStateOf<List<NoteWithLabels>>(emptyList()) }
            var showReplyEditor by remember { mutableStateOf(false) }

            when {
              showEditor || editingNote != null -> {
                NoteEditorScreen(
                  initialText = editingNote?.note?.text ?: "",
                  initialLabelIds = editingNote?.labels?.map { it.id } ?: emptyList(),
                  labels = labels,
                  isEdit = editingNote != null,
                  onSave = { text, labelIds ->
                    scope.launch {
                      if (editingNote != null) {
                        notesPresenter.updateNote(editingNote!!.note.id, text)
                        notesPresenter.setLabelsForNote(editingNote!!.note.id, labelIds)
                      } else {
                        val page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
                        val noteId = notesPresenter.addNote(
                          suraAyah.sura, suraAyah.ayah, page, text
                        )
                        if (labelIds.isNotEmpty()) {
                          notesPresenter.setLabelsForNote(noteId, labelIds)
                        }
                      }
                      editingNote = null
                      showEditor = false
                    }
                  },
                  onCancel = {
                    editingNote = null
                    showEditor = false
                  },
                  onAddLabel = { name ->
                    scope.launch { notesPresenter.addLabel(name) }
                  }
                )
              }
              showReplyEditor && viewingDetail != null -> {
                NoteEditorScreen(
                  labels = labels,
                  onSave = { text, labelIds ->
                    scope.launch {
                      val parentId = viewingDetail!!.note.id
                      val page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
                      val noteId = notesPresenter.addNote(
                        suraAyah.sura, suraAyah.ayah, page, text, parentId
                      )
                      if (labelIds.isNotEmpty()) {
                        notesPresenter.setLabelsForNote(noteId, labelIds)
                      }
                      childNotes = notesPresenter.childNotes(parentId)
                      showReplyEditor = false
                    }
                  },
                  onCancel = { showReplyEditor = false },
                  onAddLabel = { name ->
                    scope.launch { notesPresenter.addLabel(name) }
                  }
                )
              }
              viewingDetail != null -> {
                NoteDetailScreen(
                  noteWithLabels = viewingDetail!!,
                  childNotes = childNotes,
                  onAyahClicked = { _, _ -> },
                  onEdit = {
                    editingNote = viewingDetail
                    viewingDetail = null
                  },
                  onDelete = {
                    scope.launch {
                      notesPresenter.deleteNote(viewingDetail!!.note.id)
                      viewingDetail = null
                    }
                  },
                  onAddReply = { showReplyEditor = true },
                  onBack = { viewingDetail = null }
                )
              }
              else -> {
                NotesForAyahScreen(
                  notes = notes,
                  labels = labels,
                  onAddNote = { showEditor = true },
                  onEditNote = { editingNote = it },
                  onDeleteNote = { noteId ->
                    scope.launch { notesPresenter.deleteNote(noteId) }
                  },
                  onViewReplies = { noteWithLabels ->
                    scope.launch {
                      childNotes = notesPresenter.childNotes(noteWithLabels.note.id)
                      viewingDetail = noteWithLabels
                    }
                  },
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    scope.launch { notesPresenter.loadLabels() }
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  object Provider : AyahActionFragmentProvider {
    override val order = 4
    override val iconResId = R.drawable.ic_note
    override fun newAyahActionFragment(): Fragment = AyahNoteFragment()
  }
}
