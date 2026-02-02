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
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.UthmaniSpan
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
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

  override val quranInfo: QuranInfo get() = _quranInfo
  override val presenter: WordByWordPresenter get() = _presenter
  override val readingEventPresenter: ReadingEventPresenter get() = _readingEventPresenter

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
    val arabicTextSize = quranSettings.wbwArabicTextSize.toFloat()
    val translationTextSize = quranSettings.wbwTranslationTextSize.toFloat()
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
    // Translation spinner is now handled by PagerActivity's ActionBar
    // (same as normal translation mode)
    translationSpinnerContainer.visibility = View.GONE
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
            val text = texts.first().text
            // Parse footnotes from translation text (same regex as TranslationUtil)
            val footnotes = FOOTNOTE_REGEX.findAll(text).map { it.range }.toList()
            result.add(
              WordByWordDisplayRow.TranslationText(
                translatorName = translation.name,
                text = text,
                footnotes = footnotes
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
    private val FOOTNOTE_REGEX = """\[\[[\s\S]*?]]""".toRegex()

    fun newInstance(page: Int): WordByWordFragment {
      val fragment = WordByWordFragment()
      val args = Bundle()
      args.putInt(PAGE_NUMBER_EXTRA, page)
      fragment.arguments = args
      return fragment
    }
  }
}
