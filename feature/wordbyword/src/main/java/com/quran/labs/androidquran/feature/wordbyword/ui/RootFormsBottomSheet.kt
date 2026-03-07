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

class RootFormsBottomSheet : BottomSheetDialogFragment() {

  private var root: String = ""
  private var arabicTypeface: Typeface? = null
  private var isNightMode: Boolean = false
  private var dataSource: WordTranslationDataSource? = null
  private var arabicTextSize: Float = 0f
  private var translationTextSize: Float = 0f
  private var totalForms: Int = 0
  private var totalOccurrences: Int = 0

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? = inflater.inflate(R.layout.root_forms_bottom_sheet, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val rootView = view.findViewById<TextView>(R.id.root_title)
    rootView.text = root
    arabicTypeface?.let { rootView.typeface = it }

    val header = view.findViewById<TextView>(R.id.forms_header)
    header.text = getString(R.string.word_detail_related_words_all, root, totalForms, totalOccurrences)

    if (arabicTextSize > 0f) {
      rootView.textSize = arabicTextSize * 1.25f
    }
    if (translationTextSize > 0f) {
      header.textSize = translationTextSize
    }

    if (isNightMode) applyNightModeColors(view)

    scope.launch {
      val ds = dataSource ?: return@launch
      val forms = ds.getAllRelatedWordsByRoot(root)
      if (!isAdded) return@launch
      displayForms(view, forms)
    }
  }

  private fun displayForms(view: View, forms: List<RootOccurrence>) {
    val container = view.findViewById<LinearLayout>(R.id.forms_container)
    container.removeAllViews()
    val inflater = LayoutInflater.from(requireContext())

    for (occ in forms) {
      val item = inflater.inflate(R.layout.word_detail_related_item, container, false)

      val arabicView = item.findViewById<TextView>(R.id.related_arabic)
      val translationView = item.findViewById<TextView>(R.id.related_translation)
      val locationView = item.findViewById<TextView>(R.id.related_location)

      arabicView.text = occ.arabicText
      arabicTypeface?.let { arabicView.typeface = it }
      translationView.text = occ.translation
      locationView.text = getString(R.string.word_detail_related_word_location, occ.sura, occ.ayah)

      if (arabicTextSize > 0f) arabicView.textSize = arabicTextSize
      if (translationTextSize > 0f) {
        translationView.textSize = translationTextSize
        locationView.textSize = translationTextSize * 0.85f
      }

      if (isNightMode) {
        arabicView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_arabic_text_night))
        translationView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_translation_text_night))
        locationView.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_card_transliteration_text_night))
      }

      item.setOnClickListener {
        (parentFragment as? WordByWordFragment)?.scrollToAyah(occ.sura, occ.ayah)
        dismissAllowingStateLoss()
      }

      container.addView(item)
    }
  }

  private fun openWordDetail(word: WordTranslation) {
    // Not used anymore; we now scroll in the parent instead.
    (parentFragment as? WordByWordFragment)?.scrollToAyah(word.sura, word.ayah)
  }

  private fun applyNightModeColors(view: View) {
    val rootColor = ContextCompat.getColor(requireContext(), R.color.word_card_arabic_text_night)
    val labelColor = ContextCompat.getColor(requireContext(), R.color.word_card_transliteration_text_night)
    val textColor = ContextCompat.getColor(requireContext(), R.color.word_card_translation_text_night)
    view.findViewById<TextView>(R.id.root_title).setTextColor(rootColor)
    view.findViewById<TextView>(R.id.forms_header).setTextColor(labelColor)
  }

  override fun onDestroyView() {
    scope.cancel()
    super.onDestroyView()
  }

  fun setDataSource(source: WordTranslationDataSource?) { this.dataSource = source }

  companion object {
    const val TAG = "RootFormsBottomSheet"

    fun newInstance(
      root: String,
      isNightMode: Boolean,
      arabicTypeface: Typeface?,
      arabicTextSize: Float,
      translationTextSize: Float,
      totalForms: Int,
      totalOccurrences: Int
    ): RootFormsBottomSheet {
      return RootFormsBottomSheet().apply {
        this.root = root
        this.isNightMode = isNightMode
        this.arabicTypeface = arabicTypeface
        this.arabicTextSize = arabicTextSize
        this.translationTextSize = translationTextSize
        this.totalForms = totalForms
        this.totalOccurrences = totalOccurrences
      }
    }
  }
}
