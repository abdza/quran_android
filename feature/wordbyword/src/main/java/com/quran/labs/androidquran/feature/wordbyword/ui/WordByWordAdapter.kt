package com.quran.labs.androidquran.feature.wordbyword.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.quran.data.model.SuraAyah
import com.quran.data.model.WordTranslation
import com.quran.labs.androidquran.feature.wordbyword.R
import com.quran.labs.androidquran.feature.wordbyword.model.MemorizationConfig
import com.quran.labs.androidquran.feature.wordbyword.model.WordByWordDisplayRow

class WordByWordAdapter(
  private val context: Context,
  private val recyclerView: RecyclerView,
  private val onClickListener: View.OnClickListener,
  private val onVerseSelectedListener: OnVerseSelectedListener,
  private val onWordClickListener: OnWordClickListener? = null
) : RecyclerView.Adapter<WordByWordAdapter.RowViewHolder>() {

  private val inflater: LayoutInflater = LayoutInflater.from(context)
  private val data: MutableList<WordByWordDisplayRow> = mutableListOf()

  private var arabicTextSize: Float = 22f
  private var translationTextSize: Float = 14f
  private var isNightMode: Boolean = false
  private var showTransliteration: Boolean = true
  private var arabicTypeface: Typeface? = null
  private var uthmaniSpanApplier: ((SpannableString) -> Unit)? = null

  private var highlightedAyahId: Int = 0
  private var highlightedStartPosition: Int = -1
  private var highlightedRowCount: Int = 0

  private var textColor: Int = Color.BLACK
  private var arabicTextColor: Int = Color.BLACK
  private var suraHeaderColor: Int = 0
  private var ayahSelectionColor: Int = 0
  private var footnoteColor: Int = 0

  // Track expanded footnotes: key = (sura, ayah, translationIndex), value = list of expanded footnote numbers
  private val expandedFootnotes: MutableMap<Triple<Int, Int, Int>, MutableList<Int>> = mutableMapOf()

  // Memorization mode fields
  private var memorizationConfig: MemorizationConfig? = null
  private var isMemorizationActive: Boolean = false
  private val wordCardViews: MutableList<WordCardView> = mutableListOf()
  private val ayahWordCards: MutableMap<Int, MutableList<WordCardView>> = mutableMapOf()
  private var temporarilyRevealedAyahId: Int = 0
  private var temporarilyRevealedWordCard: WordCardView? = null

  private val defaultClickListener = View.OnClickListener { handleClick(it) }
  private val defaultLongClickListener = View.OnLongClickListener { view ->
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val row = data[position]
      // In memorization mode, only reveal the ayah - don't select it
      if (isMemorizationActive) {
        temporarilyRevealAyah(row.ayahId)
        return@OnLongClickListener true
      }
    }
    selectVerseRows(view)
  }

  fun setData(rows: List<WordByWordDisplayRow>) {
    data.clear()
    data.addAll(rows)
    expandedFootnotes.clear()
  }

  fun refresh(
    arabicSize: Float,
    translationSize: Float,
    nightMode: Boolean,
    showTranslit: Boolean,
    arabicFont: Typeface?,
    spanApplier: ((SpannableString) -> Unit)?,
    memorizationConfig: MemorizationConfig? = null
  ) {
    this.arabicTextSize = arabicSize
    this.translationTextSize = translationSize
    this.isNightMode = nightMode
    this.showTransliteration = showTranslit
    this.arabicTypeface = arabicFont
    this.uthmaniSpanApplier = spanApplier
    this.memorizationConfig = memorizationConfig

    if (isNightMode) {
      textColor = Color.WHITE
      arabicTextColor = ContextCompat.getColor(context, R.color.word_card_arabic_text_night)
      suraHeaderColor = ContextCompat.getColor(context, R.color.word_by_word_header_background_night)
      ayahSelectionColor = ContextCompat.getColor(context, R.color.word_by_word_selection_highlight_night)
      footnoteColor = ContextCompat.getColor(context, R.color.word_by_word_footnote_night)
    } else {
      textColor = Color.BLACK
      arabicTextColor = ContextCompat.getColor(context, R.color.word_card_arabic_text)
      suraHeaderColor = ContextCompat.getColor(context, R.color.word_by_word_header_background)
      ayahSelectionColor = ContextCompat.getColor(context, R.color.word_by_word_selection_highlight)
      footnoteColor = ContextCompat.getColor(context, R.color.word_by_word_footnote)
    }

    if (data.isNotEmpty()) {
      notifyDataSetChanged()
    }
  }

  fun setHighlightedAyah(ayahId: Int) {
    highlightAyah(ayahId, true)
  }

  fun unhighlight() {
    if (highlightedAyahId > 0 && highlightedRowCount > 0) {
      notifyItemRangeChanged(highlightedStartPosition, highlightedRowCount)
    }
    highlightedAyahId = 0
    highlightedRowCount = 0
    highlightedStartPosition = -1
  }

  fun activateMemorization() {
    isMemorizationActive = true
    wordCardViews.forEach { it.hide() }
  }

  fun resetMemorization() {
    isMemorizationActive = false
    wordCardViews.forEach { it.reveal() }
    wordCardViews.clear()
    ayahWordCards.clear()
    temporarilyRevealedAyahId = 0
    temporarilyRevealedWordCard = null
  }

  fun temporarilyRevealAyah(ayahId: Int) {
    if (isMemorizationActive && ayahId != temporarilyRevealedAyahId) {
      // Hide previously revealed ayah
      hideTemporarilyRevealedAyah()
      // Reveal new ayah
      temporarilyRevealedAyahId = ayahId
      ayahWordCards[ayahId]?.forEach { it.reveal() }
    }
  }

  fun hideTemporarilyRevealedAyah() {
    if (temporarilyRevealedAyahId != 0) {
      ayahWordCards[temporarilyRevealedAyahId]?.forEach { it.hide() }
      temporarilyRevealedAyahId = 0
    }
  }

  fun isMemorizationModeActive(): Boolean = isMemorizationActive

  private fun highlightAyah(ayahId: Int, notify: Boolean) {
    if (ayahId != highlightedAyahId) {
      val previousStart = highlightedStartPosition
      val previousCount = highlightedRowCount

      val matches = data.withIndex().filter { it.value.ayahId == ayahId }
      highlightedStartPosition = matches.firstOrNull()?.index ?: -1
      highlightedRowCount = matches.size

      if (notify) {
        if (previousCount > 0 && previousStart >= 0) {
          notifyItemRangeChanged(previousStart, previousCount)
        }
        if (highlightedRowCount > 0 && highlightedStartPosition >= 0) {
          notifyItemRangeChanged(highlightedStartPosition, highlightedRowCount)
        }
      }

      highlightedAyahId = ayahId
    }
  }

  private fun handleClick(view: View) {
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val row = data[position]
      if (row.ayahId != highlightedAyahId) {
        onVerseSelectedListener.onVerseSelected(SuraAyah(row.sura, row.ayah))
      }
    }
    // Always show toolbar/menu on tap
    onClickListener.onClick(view)
  }

  private fun selectVerseRows(view: View): Boolean {
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val row = data[position]
      highlightAyah(row.ayahId, true)
      onVerseSelectedListener.onVerseSelected(SuraAyah(row.sura, row.ayah))
      return true
    }
    return false
  }

  override fun getItemViewType(position: Int): Int {
    return when (data[position]) {
      is WordByWordDisplayRow.SuraHeader -> WordByWordDisplayRow.Type.SURA_HEADER
      is WordByWordDisplayRow.Basmallah -> WordByWordDisplayRow.Type.BASMALLAH
      is WordByWordDisplayRow.VerseHeader -> WordByWordDisplayRow.Type.VERSE_HEADER
      is WordByWordDisplayRow.WordsRow -> WordByWordDisplayRow.Type.WORDS_ROW
      is WordByWordDisplayRow.TranslationRow -> WordByWordDisplayRow.Type.TRANSLATION_ROW
      is WordByWordDisplayRow.Spacer -> WordByWordDisplayRow.Type.SPACER
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
    val layout = when (viewType) {
      WordByWordDisplayRow.Type.SURA_HEADER -> R.layout.word_by_word_sura_header
      WordByWordDisplayRow.Type.BASMALLAH -> R.layout.word_by_word_basmallah
      WordByWordDisplayRow.Type.VERSE_HEADER -> R.layout.word_by_word_verse_header
      WordByWordDisplayRow.Type.WORDS_ROW -> R.layout.word_by_word_words_row
      WordByWordDisplayRow.Type.TRANSLATION_ROW -> R.layout.word_by_word_translation_row
      WordByWordDisplayRow.Type.SPACER -> R.layout.word_by_word_spacer
      else -> throw IllegalArgumentException("Unknown view type: $viewType")
    }

    val view = inflater.inflate(layout, parent, false)
    return RowViewHolder(view)
  }

  override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
    val row = data[position]

    when (row) {
      is WordByWordDisplayRow.SuraHeader -> {
        holder.text?.text = row.suraName
        holder.text?.setBackgroundColor(suraHeaderColor)
      }
      is WordByWordDisplayRow.Basmallah -> {
        val basmallah = SpannableString(AR_BASMALLAH)
        uthmaniSpanApplier?.invoke(basmallah)
        holder.text?.text = basmallah
        holder.text?.textSize = arabicTextSize * ARABIC_MULTIPLIER
        holder.text?.setTextColor(if (isNightMode) {
          ContextCompat.getColor(context, R.color.word_by_word_basmallah_text_night)
        } else {
          ContextCompat.getColor(context, R.color.word_by_word_basmallah_text)
        })
        arabicTypeface?.let { holder.text?.typeface = it }
      }
      is WordByWordDisplayRow.VerseHeader -> {
        holder.verseNumber?.text = context.getString(R.string.verse_number_format, row.sura, row.ayah)
        holder.verseNumber?.setTextColor(if (isNightMode) {
          ContextCompat.getColor(context, R.color.word_by_word_verse_number_night)
        } else {
          ContextCompat.getColor(context, R.color.word_by_word_verse_number)
        })
      }
      is WordByWordDisplayRow.WordsRow -> {
        holder.wordsContainer?.removeAllViews()
        for (word in row.words) {
          val wordCard = WordCardView(context)
          wordCard.setNightMode(isNightMode)
          wordCard.setShowTransliteration(showTransliteration)
          wordCard.setArabicTypeface(arabicTypeface)
          wordCard.setArabicTextSize(arabicTextSize)
          wordCard.setTranslationTextSize(translationTextSize)
          wordCard.isClickable = true
          wordCard.isFocusable = true

          // Memorization setup
          val config = memorizationConfig
          if (config != null && config.hasContentToHide) {
            wordCard.setMemorizationConfig(config.hideArabic, config.hideTranslation)
            wordCardViews.add(wordCard)
            // Track word cards by ayah for temporary reveal feature
            ayahWordCards.getOrPut(row.ayahId) { mutableListOf() }.add(wordCard)
            wordCard.setWord(word)
            if (isMemorizationActive && row.ayahId != temporarilyRevealedAyahId) {
              wordCard.hide()
            }
            wordCard.setOnClickListener {
              if (isMemorizationActive) {
                wordCard.toggleReveal()
              } else {
                onWordClickListener?.onWordClicked(word)
              }
            }
            // Long press on word to temporarily reveal until release
            wordCard.setOnLongClickListener {
              if (isMemorizationActive && wordCard.isHidden()) {
                temporarilyRevealedWordCard = wordCard
                wordCard.temporarilyReveal()
              }
              true
            }
            // Touch listener to hide word when user releases long press
            wordCard.setOnTouchListener(createTouchListenerForWordCard(wordCard))
          } else {
            wordCard.setWord(word)
            wordCard.setOnClickListener {
              onWordClickListener?.onWordClicked(word)
            }
          }

          holder.wordsContainer?.addView(wordCard)
        }
      }
      is WordByWordDisplayRow.TranslationRow -> {
        holder.translationContainer?.removeAllViews()
        val translationTextColor = if (isNightMode) {
          ContextCompat.getColor(context, R.color.word_by_word_translation_text_night)
        } else {
          ContextCompat.getColor(context, R.color.word_by_word_translation_text)
        }
        val translatorNameColor = if (isNightMode) {
          ContextCompat.getColor(context, R.color.word_by_word_translator_name_night)
        } else {
          ContextCompat.getColor(context, R.color.word_by_word_translator_name)
        }
        val backgroundColor = if (isNightMode) {
          ContextCompat.getColor(context, R.color.word_by_word_translation_background_night)
        } else {
          ContextCompat.getColor(context, R.color.word_by_word_translation_background)
        }
        holder.translationContainer?.setBackgroundColor(backgroundColor)

        for ((index, translation) in row.translations.withIndex()) {
          // Add translator name if multiple translations
          if (row.translations.size > 1) {
            val translatorView = TextView(context).apply {
              text = translation.translatorName
              textSize = translationTextSize * 0.85f
              setTextColor(translatorNameColor)
              setPadding(0, if (index > 0) 16 else 0, 0, 4)
            }
            holder.translationContainer?.addView(translatorView)
          }

          // Add translation text with footnote support
          val textView = TextView(context).apply {
            val displayText = if (translation.footnotes.isNotEmpty()) {
              val key = Triple(row.sura, row.ayah, index)
              val expanded = expandedFootnotes[key] ?: emptyList()
              processFootnotes(translation.text, translation.footnotes, expanded, position, index)
            } else {
              SpannableString(translation.text)
            }
            text = displayText
            textSize = translationTextSize
            setTextColor(translationTextColor)
            setPadding(0, 0, 0, 8)
            if (translation.footnotes.isNotEmpty()) {
              movementMethod = LinkMovementMethod.getInstance()
            }
          }
          holder.translationContainer?.addView(textView)
        }
      }
      is WordByWordDisplayRow.Spacer -> {
        // No binding needed for spacer
      }
    }

    updateHighlight(row, holder)
    holder.itemView.setOnClickListener(defaultClickListener)
    holder.itemView.setOnLongClickListener(defaultLongClickListener)
    // Touch listener to detect when user releases after long press (for memorization reveal)
    holder.itemView.setOnTouchListener(createTouchListenerForRow(row))
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun createTouchListenerForRow(row: WordByWordDisplayRow): View.OnTouchListener {
    return View.OnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          // Hide temporarily revealed ayah when user releases
          if (isMemorizationActive && temporarilyRevealedAyahId == row.ayahId) {
            hideTemporarilyRevealedAyah()
          }
        }
      }
      // Return false to allow click/long click listeners to work
      false
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun createTouchListenerForWordCard(wordCard: WordCardView): View.OnTouchListener {
    return View.OnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          // Hide temporarily revealed word when user releases long press
          if (isMemorizationActive && temporarilyRevealedWordCard == wordCard) {
            wordCard.hideTemporarilyRevealed()
            temporarilyRevealedWordCard = null
          }
        }
      }
      // Return false to allow click/long click listeners to work
      false
    }
  }

  private fun updateHighlight(row: WordByWordDisplayRow, holder: RowViewHolder) {
    val isHighlighted = row.ayahId == highlightedAyahId
    if (row !is WordByWordDisplayRow.SuraHeader &&
      row !is WordByWordDisplayRow.Basmallah &&
      row !is WordByWordDisplayRow.Spacer
    ) {
      holder.itemView.setBackgroundColor(if (isHighlighted) ayahSelectionColor else 0)
    }
  }

  override fun getItemCount(): Int = data.size

  private fun processFootnotes(
    text: String,
    footnotes: List<IntRange>,
    expandedFootnoteNumbers: List<Int>,
    position: Int,
    translationIndex: Int
  ): CharSequence {
    if (footnotes.isEmpty()) return text

    val spannableBuilder = SpannableStringBuilder(text)
    val sortedRanges = footnotes.sortedByDescending { it.last }

    sortedRanges.forEachIndexed { index, range ->
      val footnoteNumber = sortedRanges.size - index
      if (footnoteNumber !in expandedFootnoteNumbers) {
        // Collapsed: replace footnote text with superscript number
        val numberText = getLocalizedNumber(footnoteNumber)
        val spannable = SpannableString(numberText)
        spannable.setSpan(SuperscriptSpan(), 0, numberText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(0.7f), 0, numberText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(
          FootnoteClickSpan(position, translationIndex, footnoteNumber),
          0, numberText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(ForegroundColorSpan(footnoteColor), 0, numberText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableBuilder.replace(range.first, range.last + 1, spannable)
      } else {
        // Expanded: show footnote text with different styling, clickable to collapse
        val span = RelativeSizeSpan(0.85f)
        val colorSpan = ForegroundColorSpan(footnoteColor)
        val clickSpan = FootnoteClickSpan(position, translationIndex, footnoteNumber)
        spannableBuilder.setSpan(span, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableBuilder.setSpan(colorSpan, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableBuilder.setSpan(clickSpan, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    }

    return spannableBuilder
  }

  private fun toggleFootnote(position: Int, translationIndex: Int, footnoteNumber: Int) {
    if (position in 0 until data.size) {
      val row = data[position]
      if (row is WordByWordDisplayRow.TranslationRow) {
        val key = Triple(row.sura, row.ayah, translationIndex)
        val expanded = expandedFootnotes.getOrPut(key) { mutableListOf() }
        if (footnoteNumber in expanded) {
          expanded.remove(footnoteNumber)
        } else {
          expanded.add(footnoteNumber)
        }
        notifyItemChanged(position)
      }
    }
  }

  private fun getLocalizedNumber(number: Int): String {
    return number.toString()
  }

  private inner class FootnoteClickSpan(
    private val position: Int,
    private val translationIndex: Int,
    private val footnoteNumber: Int
  ) : ClickableSpan() {
    override fun onClick(widget: View) {
      toggleFootnote(position, translationIndex, footnoteNumber)
    }
  }

  inner class RowViewHolder(val wrapperView: View) : RecyclerView.ViewHolder(wrapperView) {
    val text: TextView? = wrapperView.findViewById(R.id.text)
    val verseNumber: TextView? = wrapperView.findViewById(R.id.verse_number)
    val wordsContainer: FlexboxLayout? = wrapperView.findViewById(R.id.words_container)
    val translationContainer: LinearLayout? = wrapperView.findViewById(R.id.translation_container)
  }

  interface OnVerseSelectedListener {
    fun onVerseSelected(suraAyah: SuraAyah)
  }

  interface OnWordClickListener {
    fun onWordClicked(word: WordTranslation)
  }

  companion object {
    const val ARABIC_MULTIPLIER = 1.4f
    private const val AR_BASMALLAH = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"
  }
}
