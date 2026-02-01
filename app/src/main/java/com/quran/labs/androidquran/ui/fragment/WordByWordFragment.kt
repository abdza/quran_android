package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import com.quran.data.core.QuranInfo
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.feature.wordbyword.model.MemorizationConfig
import com.quran.labs.androidquran.feature.wordbyword.model.WordByWordDisplayRow
import com.quran.labs.androidquran.feature.wordbyword.presenter.WordByWordPresenter
import com.quran.labs.androidquran.feature.wordbyword.ui.WordByWordFragment as BaseWordByWordFragment
import com.quran.labs.androidquran.model.translation.TranslationModel
import com.quran.labs.androidquran.presenter.translationlist.TranslationListPresenter
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.UthmaniSpan
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.view.QuranSpinner
import com.quran.mobile.translation.model.LocalTranslation
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

@HasMemberInjections
class WordByWordFragment : BaseWordByWordFragment() {

  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var _quranInfo: QuranInfo
  @Inject lateinit var _presenter: WordByWordPresenter
  @Inject lateinit var _readingEventPresenter: ReadingEventPresenter
  @Inject lateinit var translationModel: TranslationModel
  @Inject lateinit var translationsDBAdapter: TranslationsDBAdapter
  @Inject lateinit var translationListPresenter: TranslationListPresenter

  override val quranInfo: QuranInfo get() = _quranInfo
  override val presenter: WordByWordPresenter get() = _presenter
  override val readingEventPresenter: ReadingEventPresenter get() = _readingEventPresenter

  private var translationSpinner: QuranSpinner? = null
  private var translationAdapter: TranslationsSpinnerAdapter? = null
  private var translationJob: Job? = null

  override fun onAttach(context: Context) {
    super.onAttach(context)
    val pageNumber = arguments?.getInt(PAGE_NUMBER_EXTRA) ?: -1
    val pages = intArrayOf(pageNumber)
    val pagerActivity = activity as? PagerActivity
    if (pagerActivity != null) {
      pagerActivity.pagerActivityComponent
        .quranPageComponentFactory()
        .generate(pages)
        .inject(this)
    }
  }

  override fun getSuraName(sura: Int): String {
    return quranDisplayData.getSuraName(requireContext(), sura, true)
  }

  override fun onPageClicked() {
    val activity: Activity? = activity
    (activity as? PagerActivity)?.toggleActionBar()
  }

  override fun getQuranSettings(): WordByWordSettings {
    val context = requireContext()
    val isNightMode = quranSettings.isNightMode
    val showTransliteration = quranSettings.showTransliteration()
    val arabicTextSize = quranSettings.translationTextSize.toFloat() * 1.5f
    val translationTextSize = quranSettings.translationTextSize.toFloat()
    val arabicTypeface = TypefaceManager.getUthmaniTypeface(context)

    val spanApplier: (SpannableString) -> Unit = { spannableString ->
      spannableString.setSpan(
        UthmaniSpan(context),
        0,
        spannableString.length,
        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
      )
    }

    return object : WordByWordSettings {
      override val isNightMode: Boolean = isNightMode
      override val showTransliteration: Boolean = showTransliteration
      override val arabicTextSize: Float = arabicTextSize
      override val translationTextSize: Float = translationTextSize
      override val arabicTypeface: Typeface = arabicTypeface
      override val uthmaniSpanApplier: ((SpannableString) -> Unit) = spanApplier
    }
  }

  override fun getMemorizationConfig(): MemorizationConfig {
    return MemorizationConfig(
      enabled = quranSettings.isMemorizationModeEnabled,
      hideArabic = quranSettings.shouldHideArabicInMemorization(),
      hideTranslation = quranSettings.shouldHideTranslationInMemorization(),
      delaySeconds = quranSettings.memorizationDelaySeconds
    )
  }

  override fun setupTranslationSpinner() {
    // Only show spinner if ayah translation is enabled
    if (!quranSettings.showAyahTranslationInWordByWord()) {
      translationSpinnerContainer.visibility = View.GONE
      return
    }

    // Create the spinner
    translationSpinner = QuranSpinner(requireContext(), null).apply {
      layoutParams = android.widget.FrameLayout.LayoutParams(
        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
      )
      val padding = resources.getDimensionPixelSize(R.dimen.translation_left_right_margin)
      setPadding(padding, padding / 2, padding, padding / 2)
    }
    translationSpinnerContainer.addView(translationSpinner)
    translationSpinnerContainer.visibility = View.VISIBLE

    // Register for translation updates
    translationJob = translationListPresenter.registerForTranslations { _, translations ->
      onTranslationsUpdated(translations)
    }
  }

  private fun onTranslationsUpdated(translations: List<LocalTranslation>) {
    if (translations.isEmpty()) {
      translationSpinnerContainer.visibility = View.GONE
      return
    }

    val activeTranslationsFilesNames = quranSettings.activeTranslations.ifEmpty {
      // If no explicit selection, select all as default
      translations.map { it.filename }.toSet()
    }

    val adapter = translationAdapter
    if (adapter == null) {
      translationAdapter = TranslationsSpinnerAdapter(
        activity,
        R.layout.translation_ab_spinner_item,
        translations.map { it.resolveTranslatorName() }.toTypedArray(),
        translations,
        activeTranslationsFilesNames
      ) { selectedItems: Set<String?>? ->
        quranSettings.activeTranslations = selectedItems
        // Refresh the view when translation selection changes
        refresh()
      }
      translationSpinner?.adapter = translationAdapter
    } else {
      adapter.updateItems(
        translations.map { it.resolveTranslatorName() }.toTypedArray(),
        translations,
        activeTranslationsFilesNames
      )
    }
  }

  override fun onDestroyView() {
    translationJob?.cancel()
    translationJob = null
    translationSpinner = null
    translationAdapter = null
    super.onDestroyView()
  }

  override fun showAyahTranslation(): Boolean {
    val show = quranSettings.showAyahTranslationInWordByWord()
    Timber.d("WordByWord: showAyahTranslation = $show")
    return show
  }

  override suspend fun getAyahTranslations(sura: Int, ayah: Int): List<WordByWordDisplayRow.TranslationText> {
    return withContext(Dispatchers.IO) {
      val verseRange = VerseRange(sura, ayah, sura, ayah, 1)
      val translations = translationsDBAdapter.getTranslations().first()

      if (translations.isEmpty()) {
        Timber.d("WordByWord: No translations available")
        return@withContext emptyList()
      }

      // Get explicitly selected translations, or fall back to all available translations
      val activeTranslations = quranSettings.activeTranslations
      val activeTranslationList = if (activeTranslations.isEmpty()) {
        // Fallback: use all available translations (same behavior as normal translation mode)
        Timber.d("WordByWord: No explicit selection, using all ${translations.size} available translations")
        translations.sortedBy { it.displayOrder }
      } else {
        Timber.d("WordByWord: Using ${activeTranslations.size} explicitly selected translations")
        translations.filter { activeTranslations.contains(it.filename) }
          .sortedBy { it.displayOrder }
      }

      val result = mutableListOf<WordByWordDisplayRow.TranslationText>()
      for (translation in activeTranslationList) {
        try {
          val texts = translationModel.getTranslationFromDatabase(verseRange, translation.filename)
          Timber.d("WordByWord: Got ${texts.size} texts for ${translation.filename}")
          if (texts.isNotEmpty()) {
            result.add(
              WordByWordDisplayRow.TranslationText(
                translatorName = translation.name,
                text = texts.first().text
              )
            )
          }
        } catch (e: Exception) {
          Timber.e(e, "WordByWord: Error fetching translation ${translation.filename}")
        }
      }
      Timber.d("WordByWord: Returning ${result.size} translations for $sura:$ayah")
      result
    }
  }

  companion object {
    private const val PAGE_NUMBER_EXTRA = "pageNumber"

    fun newInstance(page: Int): WordByWordFragment {
      val fragment = WordByWordFragment()
      val args = Bundle()
      args.putInt(PAGE_NUMBER_EXTRA, page)
      fragment.arguments = args
      return fragment
    }
  }
}
