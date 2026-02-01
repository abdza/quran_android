package com.quran.labs.androidquran.feature.wordbyword.model

import com.quran.data.model.WordTranslation

sealed class WordByWordDisplayRow {
  abstract val sura: Int
  abstract val ayah: Int
  abstract val ayahId: Int

  data class SuraHeader(
    override val sura: Int,
    override val ayah: Int,
    override val ayahId: Int,
    val suraName: String
  ) : WordByWordDisplayRow()

  data class Basmallah(
    override val sura: Int,
    override val ayah: Int,
    override val ayahId: Int
  ) : WordByWordDisplayRow()

  data class VerseHeader(
    override val sura: Int,
    override val ayah: Int,
    override val ayahId: Int
  ) : WordByWordDisplayRow()

  data class WordsRow(
    override val sura: Int,
    override val ayah: Int,
    override val ayahId: Int,
    val words: List<WordTranslation>
  ) : WordByWordDisplayRow()

  data class TranslationRow(
    override val sura: Int,
    override val ayah: Int,
    override val ayahId: Int,
    val translations: List<TranslationText>
  ) : WordByWordDisplayRow()

  data class TranslationText(
    val translatorName: String,
    val text: String
  )

  data class Spacer(
    override val sura: Int,
    override val ayah: Int,
    override val ayahId: Int
  ) : WordByWordDisplayRow()

  object Type {
    const val SURA_HEADER = 0
    const val BASMALLAH = 1
    const val VERSE_HEADER = 2
    const val WORDS_ROW = 3
    const val SPACER = 4
    const val TRANSLATION_ROW = 5
  }
}
