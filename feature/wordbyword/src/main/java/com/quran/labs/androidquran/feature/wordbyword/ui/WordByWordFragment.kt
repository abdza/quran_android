package com.quran.labs.androidquran.feature.wordbyword.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.WordTranslation
import com.quran.data.model.selection.AyahSelection
import com.quran.labs.androidquran.feature.wordbyword.R
import com.quran.labs.androidquran.feature.wordbyword.model.MemorizationConfig
import com.quran.labs.androidquran.feature.wordbyword.model.WordByWordDisplayRow
import com.quran.labs.androidquran.feature.wordbyword.presenter.WordByWordPresenter
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class WordByWordFragment : Fragment(),
  WordByWordPresenter.WordByWordScreen,
  WordByWordAdapter.OnVerseSelectedListener,
  WordByWordAdapter.OnWordClickListener {

  private var pageNumber = 0
  private var scrollPosition = 0

  private lateinit var recyclerView: RecyclerView
  private lateinit var adapter: WordByWordAdapter
  private lateinit var layoutManager: LinearLayoutManager

  abstract val quranInfo: QuranInfo
  abstract val presenter: WordByWordPresenter
  abstract val readingEventPresenter: ReadingEventPresenter

  private val scope = MainScope()

  private var isNightMode: Boolean = false
  private var showTransliteration: Boolean = true
  private var arabicTextSize: Float = 22f
  private var translationTextSize: Float = 14f
  private var arabicTypeface: Typeface? = null
  private var uthmaniSpanApplier: ((SpannableString) -> Unit)? = null
  private var memorizationJob: Job? = null

  protected abstract fun getSuraName(sura: Int): String
  protected abstract fun onPageClicked()
  protected abstract fun getQuranSettings(): WordByWordSettings
  protected abstract fun getMemorizationConfig(): MemorizationConfig

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) {
      scrollPosition = savedInstanceState.getInt(SI_SCROLL_POSITION)
    }
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.word_by_word_fragment, container, false)
    recyclerView = view.findViewById(R.id.recycler_view)
    layoutManager = LinearLayoutManager(context)
    recyclerView.layoutManager = layoutManager
    adapter = WordByWordAdapter(
      requireContext(),
      recyclerView,
      View.OnClickListener { onPageClicked() },
      this,
      this
    )
    recyclerView.adapter = adapter
    return view
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    pageNumber = arguments?.getInt(PAGE_NUMBER_EXTRA) ?: -1
  }

  override fun onDetach() {
    scope.cancel()
    super.onDetach()
  }

  override fun onResume() {
    super.onResume()
    presenter.bind(this)
    updateView()
  }

  override fun onPause() {
    memorizationJob?.cancel()
    presenter.unbind(this)
    super.onPause()
  }

  fun updateView() {
    if (isAdded) {
      val settings = getQuranSettings()
      isNightMode = settings.isNightMode
      showTransliteration = settings.showTransliteration
      arabicTextSize = settings.arabicTextSize
      translationTextSize = settings.translationTextSize
      arabicTypeface = settings.arabicTypeface
      uthmaniSpanApplier = settings.uthmaniSpanApplier
      val memorizationConfig = getMemorizationConfig()

      // Update background color based on night mode
      val backgroundColor = if (isNightMode) {
        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.word_by_word_background_night)
      } else {
        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.word_by_word_background)
      }
      view?.setBackgroundColor(backgroundColor)
      recyclerView.setBackgroundColor(backgroundColor)

      adapter.refresh(
        arabicTextSize,
        translationTextSize,
        isNightMode,
        showTransliteration,
        arabicTypeface,
        uthmaniSpanApplier,
        memorizationConfig
      )
      refresh()
    }
  }

  fun refresh() {
    scope.launch {
      presenter.refresh(::getSuraName)
    }
  }

  override fun setWords(page: Int, rows: List<WordByWordDisplayRow>) {
    adapter.resetMemorization()
    adapter.setData(rows)
    adapter.notifyDataSetChanged()
    updateScrollPosition()

    // Start memorization timer
    val config = getMemorizationConfig()
    if (config.hasContentToHide) {
      memorizationJob?.cancel()
      memorizationJob = scope.launch {
        delay(config.delaySeconds * 1000L)
        if (isAdded) {
          adapter.activateMemorization()
        }
      }
    }
  }

  override fun updateScrollPosition() {
    layoutManager.scrollToPosition(scrollPosition)
  }

  override fun onVerseSelected(suraAyah: SuraAyah) {
    if (isVisible) {
      val toolbarPosition = getToolbarPosition(suraAyah.sura, suraAyah.ayah)
      readingEventPresenter.onAyahSelection(AyahSelection.Ayah(suraAyah, toolbarPosition))
    }
  }

  private fun getToolbarPosition(sura: Int, ayah: Int): com.quran.data.model.selection.SelectionIndicator {
    // For now, return a simple selection indicator
    // This could be enhanced to calculate the actual position for the toolbar
    return com.quran.data.model.selection.SelectionIndicator.None
  }

  override fun onSaveInstanceState(outState: Bundle) {
    scrollPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
    outState.putInt(SI_SCROLL_POSITION, scrollPosition)
    super.onSaveInstanceState(outState)
  }

  fun highlightAyah(sura: Int, ayah: Int) {
    val ayahId = quranInfo.getAyahId(sura, ayah)
    adapter.setHighlightedAyah(ayahId)
  }

  fun unhighlight() {
    adapter.unhighlight()
  }

  override fun onWordClicked(word: WordTranslation) {
    showWordDetail(word)
  }

  private fun showWordDetail(word: WordTranslation) {
    val bottomSheet = WordDetailBottomSheet.newInstance(
      word = word,
      arabicTypeface = arabicTypeface,
      isNightMode = isNightMode,
      suraName = getSuraName(word.sura),
      dataSource = presenter.getDataSource()
    )
    bottomSheet.show(childFragmentManager, WordDetailBottomSheet.TAG)
  }

  interface WordByWordSettings {
    val isNightMode: Boolean
    val showTransliteration: Boolean
    val arabicTextSize: Float
    val translationTextSize: Float
    val arabicTypeface: Typeface?
    val uthmaniSpanApplier: ((SpannableString) -> Unit)?
  }

  companion object {
    private const val PAGE_NUMBER_EXTRA = "pageNumber"
    private const val SI_SCROLL_POSITION = "SI_SCROLL_POSITION"

    fun newInstanceArgs(page: Int): Bundle {
      return Bundle().apply {
        putInt(PAGE_NUMBER_EXTRA, page)
      }
    }
  }
}
