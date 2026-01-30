package com.quran.labs.androidquran.feature.wordbyword.presenter

import com.quran.data.core.QuranInfo
import com.quran.data.di.QuranPageScope
import com.quran.data.model.WordByWordAyahInfo
import com.quran.labs.androidquran.feature.wordbyword.model.WordByWordDisplayRow
import com.quran.mobile.wordbyword.WordTranslationDataSource
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@QuranPageScope
class WordByWordPresenter @Inject constructor(
  private val wordTranslationDataSource: WordTranslationDataSource,
  private val quranInfo: QuranInfo,
  private val pages: IntArray
) {
  private var wordByWordScreen: WordByWordScreen? = null

  fun bind(screen: WordByWordScreen) {
    wordByWordScreen = screen
  }

  fun unbind(screen: WordByWordScreen) {
    if (wordByWordScreen == screen) {
      wordByWordScreen = null
    }
  }

  suspend fun refresh(getSuraName: (Int) -> String) {
    val page = pages.firstOrNull() ?: return
    val verseRange = quranInfo.getVerseRangeForPage(page)
    if (verseRange.versesInRange <= 0) return

    val wordData = withContext(Dispatchers.IO) {
      wordTranslationDataSource.getWordsForVerseRange(
        verseRange.startSura, verseRange.startAyah,
        verseRange.endingSura, verseRange.endingAyah
      )
    }

    val displayRows = buildDisplayRows(wordData, getSuraName)
    wordByWordScreen?.setWords(page, displayRows)
  }

  private fun buildDisplayRows(
    wordData: List<WordByWordAyahInfo>,
    getSuraName: (Int) -> String
  ): List<WordByWordDisplayRow> {
    val rows = mutableListOf<WordByWordDisplayRow>()
    var lastSura = -1

    for (ayahInfo in wordData) {
      val sura = ayahInfo.sura
      val ayah = ayahInfo.ayah
      val ayahId = quranInfo.getAyahId(sura, ayah)

      // Add sura header if this is a new sura
      if (sura != lastSura) {
        rows.add(
          WordByWordDisplayRow.SuraHeader(
            sura = sura,
            ayah = ayah,
            ayahId = ayahId,
            suraName = getSuraName(sura)
          )
        )

        // Add Basmallah for all suras except At-Tawbah (sura 9) and Al-Fatihah (sura 1)
        if (sura != 1 && sura != 9 && ayah == 1) {
          rows.add(
            WordByWordDisplayRow.Basmallah(
              sura = sura,
              ayah = ayah,
              ayahId = ayahId
            )
          )
        }

        lastSura = sura
      }

      // Add verse header
      rows.add(
        WordByWordDisplayRow.VerseHeader(
          sura = sura,
          ayah = ayah,
          ayahId = ayahId
        )
      )

      // Add words row
      if (ayahInfo.words.isNotEmpty()) {
        rows.add(
          WordByWordDisplayRow.WordsRow(
            sura = sura,
            ayah = ayah,
            ayahId = ayahId,
            words = ayahInfo.words
          )
        )
      }

      // Add spacer
      rows.add(
        WordByWordDisplayRow.Spacer(
          sura = sura,
          ayah = ayah,
          ayahId = ayahId
        )
      )
    }

    return rows
  }

  fun isDatabaseAvailable(): Boolean = wordTranslationDataSource.isDatabaseAvailable()

  fun getDataSource(): WordTranslationDataSource = wordTranslationDataSource

  interface WordByWordScreen {
    fun setWords(page: Int, rows: List<WordByWordDisplayRow>)
    fun updateScrollPosition()
  }
}
