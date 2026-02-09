package com.quran.mobile.feature.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.app.Activity
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
import com.quran.data.model.note.NoteWithLabels
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.NoteShareTextProvider
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.notes.di.NotesComponentInterface
import com.quran.mobile.feature.notes.presenter.NotesPresenter
import com.quran.mobile.feature.notes.ui.NoteDetailScreen
import com.quran.mobile.feature.notes.ui.NoteEditorScreen
import com.quran.mobile.feature.notes.ui.NotesListScreen
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotesListFragment : Fragment() {

  @Inject
  lateinit var notesPresenter: NotesPresenter

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var noteShareTextProvider: NoteShareTextProvider

  private var scope: CoroutineScope = MainScope()

  override fun onAttach(context: Context) {
    super.onAttach(context)
    val injector = (context.applicationContext as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? NotesComponentInterface
    injector?.notesComponentFactory()?.generate()?.inject(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    scope = MainScope()
    scope.launch {
      notesPresenter.loadLabels()
      notesPresenter.loadNotes()
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return ComposeView(requireContext()).apply {
      setContent {
        val notes by notesPresenter.notesList.collectAsState()
        val labels by notesPresenter.labels.collectAsState()
        val filterLabel by notesPresenter.filterLabel.collectAsState()
        val filterSura by notesPresenter.filterSura.collectAsState()
        val searchQuery by notesPresenter.searchQuery.collectAsState()

        var viewingDetail by remember { mutableStateOf<NoteWithLabels?>(null) }
        var childNotes by remember { mutableStateOf<List<NoteWithLabels>>(emptyList()) }
        var editingNote by remember { mutableStateOf<NoteWithLabels?>(null) }
        var showReplyEditor by remember { mutableStateOf(false) }

        QuranTheme {
          when {
            editingNote != null -> {
              NoteEditorScreen(
                initialText = editingNote!!.note.text,
                initialLabelIds = editingNote!!.labels.map { it.id },
                labels = labels,
                isEdit = true,
                onSave = { text, labelIds ->
                  scope.launch {
                    notesPresenter.updateNote(editingNote!!.note.id, text)
                    notesPresenter.setLabelsForNote(editingNote!!.note.id, labelIds)
                    editingNote = null
                    notesPresenter.loadNotes()
                  }
                },
                onCancel = { editingNote = null },
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
                    val note = viewingDetail!!.note
                    val page = quranInfo.getPageFromSuraAyah(note.sura, note.ayah)
                    val noteId = notesPresenter.addNote(
                      note.sura, note.ayah, page, text, parentId
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
                onAyahClicked = { sura, ayah ->
                  navigateToAyah(sura, ayah)
                },
                onEdit = { editingNote = viewingDetail },
                onDelete = {
                  scope.launch {
                    notesPresenter.deleteNote(viewingDetail!!.note.id)
                    viewingDetail = null
                  }
                },
                onAddReply = { showReplyEditor = true },
                onBack = { viewingDetail = null },
                onShare = {
                  scope.launch {
                    val n = viewingDetail!!.note
                    val labelNames = viewingDetail!!.labels.map { it.name }
                    val text = noteShareTextProvider.getShareText(
                      requireContext(), n.sura, n.ayah, n.text, labelNames
                    )
                    shareText(text)
                  }
                }
              )
            }
            else -> {
              NotesListScreen(
                notes = notes,
                labels = labels,
                selectedLabelId = filterLabel,
                selectedSura = filterSura,
                searchQuery = searchQuery,
                onNoteClicked = { noteWithLabels ->
                  scope.launch {
                    childNotes = notesPresenter.childNotes(noteWithLabels.note.id)
                    viewingDetail = noteWithLabels
                  }
                },
                onFilterByLabel = { labelId ->
                  notesPresenter.filterByLabel(labelId)
                  scope.launch { notesPresenter.loadNotes() }
                },
                onFilterBySura = { sura ->
                  notesPresenter.filterBySura(sura)
                  scope.launch { notesPresenter.loadNotes() }
                },
                onSearchQueryChanged = { query ->
                  notesPresenter.search(query)
                  scope.launch { notesPresenter.loadNotes() }
                },
                modifier = Modifier.fillMaxSize()
              )
            }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.share_note)))
  }

  private fun navigateToAyah(sura: Int, ayah: Int) {
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    val intent = Intent().apply {
      setClassName(requireContext(), "com.quran.labs.androidquran.ui.PagerActivity")
      putExtra("page", page)
      putExtra("highlight_sura", sura)
      putExtra("highlight_ayah", ayah)
    }
    startActivity(intent)
  }

  companion object {
    fun newInstance(): NotesListFragment = NotesListFragment()
  }
}
