package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.feature.wordbyword.presenter.WordByWordPresenter
import com.quran.labs.androidquran.feature.wordbyword.ui.WordByWordFragment as BaseWordByWordFragment
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.UthmaniSpan
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject

@HasMemberInjections
class WordByWordFragment : BaseWordByWordFragment() {

  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var _quranInfo: QuranInfo
  @Inject lateinit var _presenter: WordByWordPresenter
  @Inject lateinit var _readingEventPresenter: ReadingEventPresenter

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
