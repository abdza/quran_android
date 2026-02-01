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

  // Memorization mode fields
  private var currentWord: WordTranslation? = null
  private var isRevealed: Boolean = true
  private var hideArabic: Boolean = false
  private var hideTranslation: Boolean = false
  private var isTemporarilyRevealed: Boolean = false

  init {
    LayoutInflater.from(context).inflate(R.layout.word_card_view, this, true)
    arabicText = findViewById(R.id.arabic_text)
    transliterationText = findViewById(R.id.transliteration_text)
    translationText = findViewById(R.id.translation_text)
  }

  fun setWord(word: WordTranslation) {
    currentWord = word

    if (isRevealed || !hideArabic) {
      arabicText.text = word.arabicText
    } else {
      arabicText.text = HIDDEN_PLACEHOLDER
    }

    if (isRevealed || !hideTranslation) {
      translationText.text = word.translation
    } else {
      translationText.text = HIDDEN_PLACEHOLDER
    }

    if (showTransliteration && word.transliteration != null) {
      if (isRevealed || !hideTranslation) {
        transliterationText.text = word.transliteration
      } else {
        transliterationText.text = HIDDEN_PLACEHOLDER
      }
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
      setBackgroundResource(R.drawable.word_card_background_ripple_night)
      arabicText.setTextColor(ContextCompat.getColor(context, R.color.word_card_arabic_text_night))
      transliterationText.setTextColor(ContextCompat.getColor(context, R.color.word_card_transliteration_text_night))
      translationText.setTextColor(ContextCompat.getColor(context, R.color.word_card_translation_text_night))
    } else {
      setBackgroundResource(R.drawable.word_card_background_ripple)
      arabicText.setTextColor(ContextCompat.getColor(context, R.color.word_card_arabic_text))
      transliterationText.setTextColor(ContextCompat.getColor(context, R.color.word_card_transliteration_text))
      translationText.setTextColor(ContextCompat.getColor(context, R.color.word_card_translation_text))
    }
  }

  // Memorization mode methods
  fun setMemorizationConfig(hideArabic: Boolean, hideTranslation: Boolean) {
    this.hideArabic = hideArabic
    this.hideTranslation = hideTranslation
  }

  fun hide() {
    isRevealed = false
    currentWord?.let { setWord(it) }
  }

  fun reveal() {
    isRevealed = true
    currentWord?.let { setWord(it) }
  }

  fun toggleReveal() {
    if (isRevealed) {
      hide()
    } else {
      reveal()
    }
  }

  fun temporarilyReveal() {
    if (!isRevealed) {
      isTemporarilyRevealed = true
      isRevealed = true
      currentWord?.let { setWord(it) }
    }
  }

  fun hideTemporarilyRevealed() {
    if (isTemporarilyRevealed) {
      isTemporarilyRevealed = false
      isRevealed = false
      currentWord?.let { setWord(it) }
    }
  }

  fun isTemporarilyRevealedState(): Boolean = isTemporarilyRevealed

  fun isHidden(): Boolean = !isRevealed

  companion object {
    private const val HIDDEN_PLACEHOLDER = "\u2022\u2022\u2022"
  }
}
