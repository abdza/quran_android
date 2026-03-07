package com.quran.labs.androidquran.feature.wordbyword.ui

import android.graphics.Typeface
import android.os.Bundle
import android.net.Uri
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
import java.net.URLEncoder

class WordDetailBottomSheet : BottomSheetDialogFragment() {

  private var word: WordTranslation? = null
  private var arabicTypeface: Typeface? = null
  private var isNightMode: Boolean = false
  private var suraName: String? = null
  private var dataSource: WordTranslationDataSource? = null
  private var arabicTextSize: Float = 0f
  private var translationTextSize: Float = 0f
  private var showRootSearchAction: Boolean = true

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

    // Apply font sizes from WBW settings
    if (arabicTextSize > 0f) {
      arabicWordView.textSize = arabicTextSize * 1.5f
      transliterationView.textSize = translationTextSize
    }
    if (translationTextSize > 0f) {
      translationView.textSize = translationTextSize
      view.findViewById<TextView>(R.id.etymology).textSize = translationTextSize
      view.findViewById<TextView>(R.id.location).textSize = translationTextSize * 0.85f
    }

    // Apply night mode colors
    if (isNightMode) {
      applyNightModeColors(view)
    }
  }

  private fun loadRootDetails(view: View, etymology: String) {
    val ds = dataSource ?: return
    val currentWord = word ?: return

    scope.launch {
      // Load root meaning and related words (DS methods switch to IO internally)
      val rootMeaning = ds.getRootMeaning(etymology)
      val relatedWords = ds.getRelatedWordsByRoot(etymology)
      val totalForms = ds.getRelatedWordsCount(etymology)
      val totalOccurrences = ds.getRelatedOccurrencesCount(etymology)

      if (!isAdded) return@launch

      // Display root meaning if available
      if (rootMeaning != null) {
        displayRootMeaning(view, rootMeaning)
      } else {
        displayRootMeaningFallback(view, etymology)
      }

      // Display related words
      displayRelatedWords(view, etymology, relatedWords, totalForms, totalOccurrences, currentWord)
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

    // Apply font sizes to root meaning views
    if (translationTextSize > 0f) {
      primaryMeaningView.textSize = translationTextSize
      view.findViewById<TextView>(R.id.primary_meaning_label).textSize = translationTextSize * 0.8f
      view.findViewById<TextView>(R.id.quran_usage_label).textSize = translationTextSize * 0.8f
      view.findViewById<TextView>(R.id.quran_usage).textSize = translationTextSize
      view.findViewById<TextView>(R.id.extended_meaning).textSize = translationTextSize
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
    totalFormCount: Int,
    totalOccurrences: Int,
    currentWord: WordTranslation
  ) {
    val relatedSection = view.findViewById<LinearLayout>(R.id.related_words_section)
    val relatedHeader = view.findViewById<TextView>(R.id.related_words_header)
    val relatedContainer = view.findViewById<LinearLayout>(R.id.related_words_container)
    val seeAllView = view.findViewById<TextView>(R.id.see_all_forms)
    val seeAllOccurrencesView = view.findViewById<TextView>(R.id.see_all_occurrences)

    // Filter out the current word if it happens to be included
    val forms = relatedWords
      .filter { it.arabicText != currentWord.arabicText || it.sura != currentWord.sura || it.ayah != currentWord.ayah }

    if (forms.isNotEmpty()) {
      relatedHeader.text = getString(R.string.word_detail_related_words, etymology, totalFormCount)
      relatedSection.visibility = View.VISIBLE

      relatedContainer.removeAllViews()
      val inflater = LayoutInflater.from(requireContext())

      for (related in forms) {
        val itemView = inflater.inflate(R.layout.word_detail_related_item, relatedContainer, false)

        val arabicView = itemView.findViewById<TextView>(R.id.related_arabic)
        val translationItemView = itemView.findViewById<TextView>(R.id.related_translation)
        val locationItemView = itemView.findViewById<TextView>(R.id.related_location)

        arabicView.text = related.arabicText
        arabicTypeface?.let { arabicView.typeface = it }
        translationItemView.text = related.translation
        locationItemView.text = getString(R.string.word_detail_related_word_location, related.sura, related.ayah)

        if (arabicTextSize > 0f) {
          arabicView.textSize = arabicTextSize
        }
        if (translationTextSize > 0f) {
          translationItemView.textSize = translationTextSize
          locationItemView.textSize = translationTextSize * 0.85f
        }

        if (isNightMode) {
          arabicView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_arabic_text_night))
          translationItemView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_translation_text_night))
          locationItemView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_transliteration_text_night))
        }

        // Click to scroll to this verse in the parent list
        itemView.setOnClickListener {
          (parentFragment as? WordByWordFragment)?.scrollToAyah(related.sura, related.ayah)
          dismissAllowingStateLoss()
        }

        relatedContainer.addView(itemView)
      }

      // Show "See all" if there are more forms than displayed
      if (totalFormCount > forms.size) {
        seeAllView.visibility = View.VISIBLE
        seeAllView.setOnClickListener {
          val sheet = RootFormsBottomSheet.newInstance(
            root = etymology,
            isNightMode = isNightMode,
            arabicTypeface = arabicTypeface,
            arabicTextSize = arabicTextSize,
            translationTextSize = translationTextSize,
            totalForms = totalFormCount,
            totalOccurrences = totalOccurrences
          ).apply { setDataSource(dataSource) }
          sheet.show(parentFragmentManager, RootFormsBottomSheet.TAG)
        }
        if (translationTextSize > 0f) {
          seeAllView.textSize = translationTextSize
        }
        if (isNightMode) {
          seeAllView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_translation_text_night))
        }
      } else {
        seeAllView.visibility = View.GONE
      }

      if (totalOccurrences > forms.size) {
        seeAllOccurrencesView.visibility = View.VISIBLE
        seeAllOccurrencesView.setOnClickListener {
          val sheet = RootOccurrencesBottomSheet.newInstance(
            root = etymology,
            isNightMode = isNightMode,
            arabicTypeface = arabicTypeface,
            arabicTextSize = arabicTextSize,
            translationTextSize = translationTextSize,
            totalOccurrences = totalOccurrences
          ).apply { setDataSource(dataSource) }
          sheet.show(parentFragmentManager, RootOccurrencesBottomSheet.TAG)
        }
        if (translationTextSize > 0f) {
          seeAllOccurrencesView.textSize = translationTextSize
        }
        if (isNightMode) {
          seeAllOccurrencesView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_translation_text_night))
        }
      } else {
        seeAllOccurrencesView.visibility = View.GONE
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

  fun setTextSizes(arabicSize: Float, translationSize: Float) {
    this.arabicTextSize = arabicSize
    this.translationTextSize = translationSize
  }

  fun setShowRootSearchAction(show: Boolean) {
    this.showRootSearchAction = show
  }

  private fun displayRootMeaningFallback(view: View, root: String) {
    val fallbackContainer = view.findViewById<LinearLayout>(R.id.root_meaning_fallback)
    val searchView = view.findViewById<TextView>(R.id.search_root_online)
    fallbackContainer.visibility = View.VISIBLE

    // Sizing
    if (translationTextSize > 0f) {
      view.findViewById<TextView>(R.id.root_meaning_missing).textSize = translationTextSize
      searchView.textSize = translationTextSize
    }

    if (showRootSearchAction) {
      searchView.visibility = View.VISIBLE
      searchView.setOnClickListener {
        val q = "Arabic root $root meaning Quran"
        val url = "https://www.google.com/search?q=" + URLEncoder.encode(q, Charsets.UTF_8.name())
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
      }
    } else {
      searchView.visibility = View.GONE
    }

    if (isNightMode) {
      val nightText = ContextCompat.getColor(requireContext(), R.color.word_card_translation_text_night)
      val nightLabel = ContextCompat.getColor(requireContext(), R.color.word_card_transliteration_text_night)
      view.findViewById<TextView>(R.id.root_meaning_missing).setTextColor(nightLabel)
      searchView.setTextColor(nightText)
    }
  }

  companion object {
    const val TAG = "WordDetailBottomSheet"

    fun newInstance(
      word: WordTranslation,
      arabicTypeface: Typeface? = null,
      isNightMode: Boolean = false,
      suraName: String? = null,
      dataSource: WordTranslationDataSource? = null,
      arabicTextSize: Float = 0f,
      translationTextSize: Float = 0f,
      showRootSearchAction: Boolean = true
    ): WordDetailBottomSheet {
      return WordDetailBottomSheet().apply {
        setWord(word)
        setArabicTypeface(arabicTypeface)
        setNightMode(isNightMode)
        setSuraName(suraName)
        setDataSource(dataSource)
        setTextSizes(arabicTextSize, translationTextSize)
        setShowRootSearchAction(showRootSearchAction)
      }
    }
  }
}
