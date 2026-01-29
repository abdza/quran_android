package com.quran.data.model

data class WordByWordAyahInfo(
  val sura: Int,
  val ayah: Int,
  val words: List<WordTranslation>
)
