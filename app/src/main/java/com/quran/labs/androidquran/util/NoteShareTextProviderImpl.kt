package com.quran.labs.androidquran.util

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.database.DatabaseHandler.TextType
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.mobile.di.NoteShareTextProvider
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NoteShareTextProviderImpl @Inject constructor(
  @ApplicationContext private val appContext: Context,
  private val arabicDatabaseUtils: ArabicDatabaseUtils,
  private val quranDisplayData: QuranDisplayData,
  private val translationsDBAdapter: TranslationsDBAdapter,
  private val quranFileUtils: QuranFileUtils
) : NoteShareTextProvider {

  override suspend fun getShareText(
    context: Context,
    sura: Int,
    ayah: Int,
    noteText: String,
    labels: List<String>
  ): String {
    return withContext(Dispatchers.IO) {
      buildString {
        // Arabic text
        try {
          val verses = arabicDatabaseUtils
            .getVerses(SuraAyah(sura, ayah), SuraAyah(sura, ayah))
            .blockingGet()
          val arabicText = verses.firstOrNull()?.text?.trim()
          if (arabicText != null) {
            val cleanText = ArabicDatabaseUtils.getAyahWithoutBasmallah(sura, ayah, arabicText)
            append("{ $cleanText }\n")
          }
        } catch (_: Exception) { }

        // Surah reference
        val suraRef = quranDisplayData.getSuraAyahString(
          context, sura, ayah, R.string.sura_ayah_sharing_str
        )
        append("[$suraRef]\n")

        // Translation text
        try {
          val settings = QuranSettings.getInstance(appContext)
          val activeTranslationFilenames = settings.activeTranslations
          if (activeTranslationFilenames.isNotEmpty()) {
            val localTranslations = translationsDBAdapter.getTranslations().first()
            val activeTranslations = localTranslations.filter {
              it.filename in activeTranslationFilenames
            }
            val verseRange = VerseRange(sura, ayah, sura, ayah, 1)
            for (translation in activeTranslations) {
              try {
                val db = DatabaseHandler.getDatabaseHandler(
                  appContext, translation.filename, quranFileUtils
                )
                val verses = db.getVerses(verseRange, TextType.TRANSLATION)
                val text = verses.firstOrNull()?.text?.trim()
                if (!text.isNullOrBlank()) {
                  append("\n")
                  val translatorName = translation.resolveTranslatorName()
                  if (translatorName.isNotBlank()) {
                    append("$translatorName:\n")
                  }
                  append("$text\n")
                }
              } catch (_: Exception) {
              }
            }
          }
        } catch (_: Exception) { }

        // Note text
        append("\n---\n")
        append(noteText)

        // Labels
        if (labels.isNotEmpty()) {
          append("\n[${labels.joinToString(", ")}]")
        }
      }
    }
  }
}
