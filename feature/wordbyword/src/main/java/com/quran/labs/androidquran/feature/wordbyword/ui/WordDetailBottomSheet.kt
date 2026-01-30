package com.quran.labs.androidquran.feature.wordbyword.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.quran.data.model.WordTranslation
import com.quran.labs.androidquran.feature.wordbyword.R
import com.quran.mobile.wordbyword.RootOccurrence
import com.quran.mobile.wordbyword.WordTranslationDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WordDetailBottomSheet : BottomSheetDialogFragment() {

  private var word: WordTranslation? = null
  private var arabicTypeface: Typeface? = null
  private var isNightMode: Boolean = false
  private var suraName: String? = null
  private var dataSource: WordTranslationDataSource? = null

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.word_detail_bottom_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val currentWord = word ?: return

    // Arabic word
    val arabicWordView = view.findViewById<TextView>(R.id.arabic_word)
    arabicWordView.text = currentWord.arabicText
    arabicTypeface?.let { arabicWordView.typeface = it }

    // Transliteration
    val transliterationView = view.findViewById<TextView>(R.id.transliteration)
    if (currentWord.transliteration != null) {
      transliterationView.text = currentWord.transliteration
      transliterationView.visibility = View.VISIBLE
    } else {
      transliterationView.visibility = View.GONE
    }

    // Translation
    val translationView = view.findViewById<TextView>(R.id.translation)
    translationView.text = currentWord.translation

    // Etymology section
    val etymologySection = view.findViewById<LinearLayout>(R.id.etymology_section)
    val etymologyView = view.findViewById<TextView>(R.id.etymology)
    val etymology = currentWord.etymology
    if (etymology != null) {
      etymologyView.text = etymology
      etymologySection.visibility = View.VISIBLE

      // Load root meaning and related words
      loadRootDetails(view, etymology)
    } else {
      etymologySection.visibility = View.GONE
    }

    // Location
    val locationView = view.findViewById<TextView>(R.id.location)
    val locationText = if (suraName != null) {
      "$suraName, ${getString(R.string.verse_number_format, currentWord.sura, currentWord.ayah)}, Word ${currentWord.wordPosition}"
    } else {
      getString(R.string.word_detail_location_format, currentWord.sura, currentWord.ayah, currentWord.wordPosition)
    }
    locationView.text = locationText

    // Apply night mode colors
    if (isNightMode) {
      applyNightModeColors(view)
    }
  }

  private fun loadRootDetails(view: View, etymology: String) {
    val ds = dataSource ?: return
    val currentWord = word ?: return

    scope.launch {
      // Load root meaning
      val rootMeaning = withContext(Dispatchers.IO) {
        ds.getRootMeaning(etymology)
      }

      // Load related words
      val relatedWords = withContext(Dispatchers.IO) {
        ds.getRelatedWordsByRoot(etymology)
      }
      val totalCount = withContext(Dispatchers.IO) {
        ds.getRelatedWordsCount(etymology)
      }

      if (!isAdded) return@launch

      // Display root meaning if available
      if (rootMeaning != null) {
        displayRootMeaning(view, rootMeaning)
      }

      // Display related words
      displayRelatedWords(view, etymology, relatedWords, totalCount, currentWord)
    }
  }

  private fun displayRootMeaning(view: View, rootMeaning: com.quran.mobile.wordbyword.RootMeaning) {
    val context = requireContext()

    // Primary meaning
    val primaryMeaningLabel = view.findViewById<TextView>(R.id.primary_meaning_label)
    val primaryMeaningView = view.findViewById<TextView>(R.id.primary_meaning)
    primaryMeaningLabel.visibility = View.VISIBLE
    primaryMeaningView.text = rootMeaning.primaryMeaning
    primaryMeaningView.visibility = View.VISIBLE

    // Quran usage
    if (rootMeaning.quranUsage != null) {
      val quranUsageLabel = view.findViewById<TextView>(R.id.quran_usage_label)
      val quranUsageView = view.findViewById<TextView>(R.id.quran_usage)
      quranUsageLabel.visibility = View.VISIBLE
      quranUsageView.text = rootMeaning.quranUsage
      quranUsageView.visibility = View.VISIBLE
    }

    // Extended meaning / notes
    val extendedText = listOfNotNull(rootMeaning.extendedMeaning, rootMeaning.notes)
      .joinToString("\n\n")
    if (extendedText.isNotEmpty()) {
      val extendedMeaningView = view.findViewById<TextView>(R.id.extended_meaning)
      extendedMeaningView.text = extendedText
      extendedMeaningView.visibility = View.VISIBLE
    }

    // Apply night mode colors to new views
    if (isNightMode) {
      val nightTextColor = ContextCompat.getColor(context, R.color.word_card_translation_text_night)
      val nightLabelColor = ContextCompat.getColor(context, R.color.word_card_transliteration_text_night)

      primaryMeaningLabel.setTextColor(nightLabelColor)
      primaryMeaningView.setTextColor(nightTextColor)
      view.findViewById<TextView>(R.id.quran_usage_label).setTextColor(nightLabelColor)
      view.findViewById<TextView>(R.id.quran_usage).setTextColor(nightTextColor)
      view.findViewById<TextView>(R.id.extended_meaning).setTextColor(nightLabelColor)
    }
  }

  private fun displayRelatedWords(
    view: View,
    etymology: String,
    relatedWords: List<com.quran.mobile.wordbyword.RootOccurrence>,
    totalCount: Int,
    currentWord: WordTranslation
  ) {
    val relatedSection = view.findViewById<LinearLayout>(R.id.related_words_section)
    val relatedHeader = view.findViewById<TextView>(R.id.related_words_header)
    val relatedContainer = view.findViewById<LinearLayout>(R.id.related_words_container)

    // Filter out the current word and get unique forms
    val uniqueRelated = relatedWords
      .filter { it.arabicText != currentWord.arabicText || it.sura != currentWord.sura || it.ayah != currentWord.ayah }
      .distinctBy { it.arabicText }
      .take(10)

    if (uniqueRelated.isNotEmpty()) {
      relatedHeader.text = getString(R.string.word_detail_related_words, etymology, totalCount)
      relatedSection.visibility = View.VISIBLE

      relatedContainer.removeAllViews()
      val inflater = LayoutInflater.from(requireContext())

      for (related in uniqueRelated) {
        val itemView = inflater.inflate(R.layout.word_detail_related_item, relatedContainer, false)

        val arabicView = itemView.findViewById<TextView>(R.id.related_arabic)
        val translationItemView = itemView.findViewById<TextView>(R.id.related_translation)
        val locationItemView = itemView.findViewById<TextView>(R.id.related_location)

        arabicView.text = related.arabicText
        arabicTypeface?.let { arabicView.typeface = it }
        translationItemView.text = related.translation
        locationItemView.text = getString(R.string.word_detail_related_word_location, related.sura, related.ayah)

        if (isNightMode) {
          arabicView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_arabic_text_night))
          translationItemView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_translation_text_night))
          locationItemView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_transliteration_text_night))
        }

        relatedContainer.addView(itemView)
      }
    }
  }

  override fun onDestroyView() {
    scope.cancel()
    super.onDestroyView()
  }

  private fun applyNightModeColors(view: View) {
    val context = requireContext()

    // Apply to the root ScrollView
    (view as? androidx.core.widget.NestedScrollView)?.setBackgroundColor(
      ContextCompat.getColor(context, R.color.word_by_word_background_night)
    )

    view.findViewById<TextView>(R.id.arabic_word)
      .setTextColor(ContextCompat.getColor(context, R.color.word_card_arabic_text_night))

    view.findViewById<TextView>(R.id.transliteration)
      .setTextColor(ContextCompat.getColor(context, R.color.word_card_transliteration_text_night))

    view.findViewById<TextView>(R.id.translation)
      .setTextColor(ContextCompat.getColor(context, R.color.word_card_translation_text_night))

    view.findViewById<TextView>(R.id.etymology)
      .setTextColor(ContextCompat.getColor(context, R.color.word_card_etymology_text_night))

    view.findViewById<TextView>(R.id.location)
      .setTextColor(ContextCompat.getColor(context, R.color.word_card_translation_text_night))

    view.findViewById<TextView>(R.id.related_words_header)
      .setTextColor(ContextCompat.getColor(context, R.color.word_card_transliteration_text_night))
  }

  fun setWord(wordTranslation: WordTranslation) {
    this.word = wordTranslation
  }

  fun setArabicTypeface(typeface: Typeface?) {
    this.arabicTypeface = typeface
  }

  fun setNightMode(nightMode: Boolean) {
    this.isNightMode = nightMode
  }

  fun setSuraName(name: String?) {
    this.suraName = name
  }

  fun setDataSource(source: WordTranslationDataSource?) {
    this.dataSource = source
  }

  companion object {
    const val TAG = "WordDetailBottomSheet"

    fun newInstance(
      word: WordTranslation,
      arabicTypeface: Typeface? = null,
      isNightMode: Boolean = false,
      suraName: String? = null,
      dataSource: WordTranslationDataSource? = null
    ): WordDetailBottomSheet {
      return WordDetailBottomSheet().apply {
        setWord(word)
        setArabicTypeface(arabicTypeface)
        setNightMode(isNightMode)
        setSuraName(suraName)
        setDataSource(dataSource)
      }
    }
  }
}
