package com.quran.data.model

data class WordTranslation(
  val sura: Int,
  val ayah: Int,
  val wordPosition: Int,
  val arabicText: String,
  val translation: String,
  val transliteration: String? = null,
  val etymology: String? = null
)
