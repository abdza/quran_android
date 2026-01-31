package com.quran.labs.androidquran.feature.wordbyword.model

data class MemorizationConfig(
  val enabled: Boolean,
  val hideArabic: Boolean,
  val hideTranslation: Boolean,
  val delaySeconds: Int
) {
  val hasContentToHide: Boolean
    get() = enabled && (hideArabic || hideTranslation)
}
