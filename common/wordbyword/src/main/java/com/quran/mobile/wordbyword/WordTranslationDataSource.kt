package com.quran.mobile.wordbyword

import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.WordByWordAyahInfo
import com.quran.data.model.WordTranslation
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
class WordTranslationDataSource @Inject constructor(
  private val databaseProvider: WordByWordDatabaseProvider
) {

  suspend fun getWordsForAyah(sura: Int, ayah: Int): List<WordTranslation> {
    return withContext(Dispatchers.IO) {
      val database = databaseProvider.provideDatabase() ?: return@withContext emptyList()
      database.wordTranslationsQueries.wordsForAyah(sura.toLong(), ayah.toLong())
        .executeAsList()
        .map { row ->
          WordTranslation(
            sura = row.sura.toInt(),
            ayah = row.ayah.toInt(),
            wordPosition = row.word_position.toInt(),
            arabicText = row.arabic_text,
            translation = row.translation,
            transliteration = row.transliteration,
            etymology = row.etymology
          )
        }
    }
  }

  suspend fun getWordsForVerseRange(
    startSura: Int,
    startAyah: Int,
    endSura: Int,
    endAyah: Int
  ): List<WordByWordAyahInfo> {
    return withContext(Dispatchers.IO) {
      val database = databaseProvider.provideDatabase() ?: return@withContext emptyList()
      val rows = database.wordTranslationsQueries.wordsForVerseRange(
        startSura = startSura.toLong(),
        startAyah = startAyah.toLong(),
        endSura = endSura.toLong(),
        endAyah = endAyah.toLong()
      ).executeAsList()

      rows.groupBy { SuraAyah(it.sura.toInt(), it.ayah.toInt()) }
        .map { (suraAyah, wordRows) ->
          WordByWordAyahInfo(
            sura = suraAyah.sura,
            ayah = suraAyah.ayah,
            words = wordRows.map { row ->
              WordTranslation(
                sura = row.sura.toInt(),
                ayah = row.ayah.toInt(),
                wordPosition = row.word_position.toInt(),
                arabicText = row.arabic_text,
                translation = row.translation,
                transliteration = row.transliteration,
                etymology = row.etymology
              )
            }
          )
        }
    }
  }

  suspend fun getWordsForPage(
    pageVerseRange: List<SuraAyah>
  ): List<WordByWordAyahInfo> {
    if (pageVerseRange.isEmpty()) return emptyList()

    val start = pageVerseRange.first()
    val end = pageVerseRange.last()
    return getWordsForVerseRange(start.sura, start.ayah, end.sura, end.ayah)
  }

  fun isDatabaseAvailable(): Boolean = databaseProvider.isDatabaseAvailable()

  suspend fun getRelatedWordsByRoot(etymology: String): List<RootOccurrence> {
    return withContext(Dispatchers.IO) {
      val database = databaseProvider.provideDatabase() ?: return@withContext emptyList()
      database.wordTranslationsQueries.wordsByEtymology(etymology)
        .executeAsList()
        .map { row ->
          RootOccurrence(
            arabicText = row.arabic_text,
            translation = row.translation,
            sura = row.sura.toInt(),
            ayah = row.ayah.toInt(),
            wordPosition = row.word_position.toInt()
          )
        }
    }
  }

  suspend fun getRelatedWordsCount(etymology: String): Int {
    return withContext(Dispatchers.IO) {
      val database = databaseProvider.provideDatabase() ?: return@withContext 0
      database.wordTranslationsQueries.countByEtymology(etymology)
        .executeAsOne()
        .toInt()
    }
  }

  suspend fun getRootMeaning(root: String): RootMeaning? {
    return withContext(Dispatchers.IO) {
      try {
        val database = databaseProvider.provideDatabase() ?: return@withContext null
        database.wordTranslationsQueries.getRootMeaning(root)
          .executeAsOneOrNull()
          ?.let { row ->
            RootMeaning(
              root = row.root,
              primaryMeaning = row.primary_meaning,
              extendedMeaning = row.extended_meaning,
              quranUsage = row.quran_usage,
              notes = row.notes
            )
          }
      } catch (e: Exception) {
        // Table might not exist in older database versions
        null
      }
    }
  }
}

data class RootOccurrence(
  val arabicText: String,
  val translation: String,
  val sura: Int,
  val ayah: Int,
  val wordPosition: Int
)

data class RootMeaning(
  val root: String,
  val primaryMeaning: String,
  val extendedMeaning: String?,
  val quranUsage: String?,
  val notes: String?
)
