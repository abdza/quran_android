package com.quran.mobile.feature.notes

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quran.data.core.QuranInfo
import com.quran.data.dao.NotesDao
import com.quran.data.model.note.NoteWithLabels
import com.quran.mobile.di.NoteShareTextProvider
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.notes.di.NotesComponentInterface
import com.quran.mobile.feature.notes.presenter.NotesPresenter
import com.quran.mobile.feature.notes.ui.NoteDetailScreen
import com.quran.mobile.feature.notes.ui.NoteEditorScreen
import com.quran.mobile.feature.notes.ui.NotesListScreen
import com.quran.mobile.feature.notes.util.NotesExportImport
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.quran.mobile.common.ui.core.R as UiCoreR

class NotesActivity : ComponentActivity() {

  @Inject
  lateinit var notesPresenter: NotesPresenter

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var noteShareTextProvider: NoteShareTextProvider

  @Inject
  lateinit var notesDao: NotesDao

  private val scope: CoroutineScope = MainScope()

  private val isProcessing = mutableStateOf(false)
  private val processingMessage = mutableStateOf("")

  private val exportFilePicker = registerForActivityResult(
    ActivityResultContracts.CreateDocument("text/markdown")
  ) { uri ->
    if (uri != null) {
      scope.launch {
        isProcessing.value = true
        processingMessage.value = getString(R.string.exporting_notes)
        try {
          val suraNames = resources.getStringArray(UiCoreR.array.sura_names)
          val count = withContext(Dispatchers.IO) {
            NotesExportImport.exportNotes(this@NotesActivity, uri, notesDao, suraNames)
          }
          Toast.makeText(
            this@NotesActivity,
            getString(R.string.export_success, count),
            Toast.LENGTH_LONG
          ).show()
        } catch (e: Exception) {
          Toast.makeText(
            this@NotesActivity,
            getString(R.string.export_error, e.message ?: "Unknown error"),
            Toast.LENGTH_LONG
          ).show()
        } finally {
          isProcessing.value = false
        }
      }
    }
  }

  private val importFilePicker = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        isProcessing.value = true
        processingMessage.value = getString(R.string.importing_notes)
        try {
          val result = withContext(Dispatchers.IO) {
            NotesExportImport.importNotes(this@NotesActivity, uri, notesDao, quranInfo)
          }
          notesPresenter.loadNotes()
          Toast.makeText(
            this@NotesActivity,
            getString(R.string.import_success, result.imported, result.skipped),
            Toast.LENGTH_LONG
          ).show()
        } catch (e: Exception) {
          Toast.makeText(
            this@NotesActivity,
            getString(R.string.import_error, e.message ?: "Unknown error"),
            Toast.LENGTH_LONG
          ).show()
        } finally {
          isProcessing.value = false
        }
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? NotesComponentInterface
    injector?.notesComponentFactory()?.generate()?.inject(this)

    scope.launch {
      notesPresenter.loadLabels()
      notesPresenter.loadNotes()
    }

    enableEdgeToEdge()

    setContent {
      val notes by notesPresenter.notesList.collectAsState()
      val labels by notesPresenter.labels.collectAsState()
      val filterLabel by notesPresenter.filterLabel.collectAsState()
      val filterSura by notesPresenter.filterSura.collectAsState()
      val searchQuery by notesPresenter.searchQuery.collectAsState()
      val processing by isProcessing
      val processingMsg by processingMessage

      var viewingDetail by remember { mutableStateOf<NoteWithLabels?>(null) }
      var childNotes by remember { mutableStateOf<List<NoteWithLabels>>(emptyList()) }
      var editingNote by remember { mutableStateOf<NoteWithLabels?>(null) }
      var showReplyEditor by remember { mutableStateOf(false) }
      var showMenu by remember { mutableStateOf(false) }

      QuranTheme {
        Column(
          modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .windowInsetsPadding(
              WindowInsets.systemBars
                .union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal)
            )
        ) {
          TopAppBar(
            title = { Text(stringResource(R.string.notes_title)) },
            navigationIcon = {
              IconButton(onClick = {
                when {
                  editingNote != null -> editingNote = null
                  viewingDetail != null -> viewingDetail = null
                  else -> finish()
                }
              }) {
                Icon(QuranIcons.ArrowBack, contentDescription = null)
              }
            },
            actions = {
              Box {
                IconButton(onClick = { showMenu = true }) {
                  Icon(
                    QuranIcons.MoreVert,
                    contentDescription = stringResource(R.string.more_options)
                  )
                }
                DropdownMenu(
                  expanded = showMenu,
                  onDismissRequest = { showMenu = false }
                ) {
                  DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_notes)) },
                    onClick = {
                      showMenu = false
                      exportFilePicker.launch("quran_notes.md")
                    }
                  )
                  DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_notes)) },
                    onClick = {
                      showMenu = false
                      importFilePicker.launch(arrayOf("text/*", "application/octet-stream"))
                    }
                  )
                }
              }
            }
          )

          if (processing) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                  text = processingMsg,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier
                )
              }
            }
          } else {
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
                  onEdit = {
                    editingNote = viewingDetail
                  },
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
                        this@NotesActivity, n.sura, n.ayah, n.text, labelNames
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
                  }
                )
              }
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
      setClassName(this@NotesActivity, "com.quran.labs.androidquran.ui.PagerActivity")
      putExtra("page", page)
      putExtra("highlight_sura", sura)
      putExtra("highlight_ayah", ayah)
    }
    startActivity(intent)
  }
}
