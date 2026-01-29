package com.quran.labs.androidquran.feature.wordbyword.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.quran.data.model.WordTranslation
import com.quran.labs.androidquran.feature.wordbyword.R

class WordCardView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  private val arabicText: TextView
  private val transliterationText: TextView
  private val translationText: TextView

  private var isNightMode: Boolean = false
  private var showTransliteration: Boolean = true
  private var arabicTypeface: Typeface? = null

  init {
    LayoutInflater.from(context).inflate(R.layout.word_card_view, this, true)
    arabicText = findViewById(R.id.arabic_text)
    transliterationText = findViewById(R.id.transliteration_text)
    translationText = findViewById(R.id.translation_text)
  }

  fun setWord(word: WordTranslation) {
    arabicText.text = word.arabicText
    translationText.text = word.translation

    if (showTransliteration && word.transliteration != null) {
      transliterationText.text = word.transliteration
      transliterationText.visibility = View.VISIBLE
    } else {
      transliterationText.visibility = View.GONE
    }

    arabicTypeface?.let { arabicText.typeface = it }
  }

  fun setNightMode(nightMode: Boolean) {
    isNightMode = nightMode
    updateColors()
  }

  fun setShowTransliteration(show: Boolean) {
    showTransliteration = show
    transliterationText.visibility = if (show && transliterationText.text.isNotEmpty()) {
      View.VISIBLE
    } else {
      View.GONE
    }
  }

  fun setArabicTypeface(typeface: Typeface?) {
    arabicTypeface = typeface
    arabicTypeface?.let { arabicText.typeface = it }
  }

  fun setArabicTextSize(size: Float) {
    arabicText.textSize = size
  }

  fun setTranslationTextSize(size: Float) {
    translationText.textSize = size
    transliterationText.textSize = size * 0.85f
  }

  private fun updateColors() {
    if (isNightMode) {
      setBackgroundResource(R.drawable.word_card_background_night)
      arabicText.setTextColor(ContextCompat.getColor(context, R.color.word_card_arabic_text_night))
      transliterationText.setTextColor(ContextCompat.getColor(context, R.color.word_card_transliteration_text_night))
      translationText.setTextColor(ContextCompat.getColor(context, R.color.word_card_translation_text_night))
    } else {
      setBackgroundResource(R.drawable.word_card_background)
      arabicText.setTextColor(ContextCompat.getColor(context, R.color.word_card_arabic_text))
      transliterationText.setTextColor(ContextCompat.getColor(context, R.color.word_card_transliteration_text))
      translationText.setTextColor(ContextCompat.getColor(context, R.color.word_card_translation_text))
    }
  }
}
