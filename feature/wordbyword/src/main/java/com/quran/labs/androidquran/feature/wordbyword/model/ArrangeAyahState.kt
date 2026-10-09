package com.quran.labs.androidquran.feature.wordbyword.model

import com.quran.data.model.WordTranslation
import kotlin.random.Random

/**
 * State of one ayah in the "arrange the words" memorization exercise.
 *
 * Every word of the ayah is a tile, identified by its index in [words] (which is in the
 * correct order). A tile is either in [placed] (the user's answer, in the order they built
 * it) or in [pool] (the shuffled words not yet used).
 */
data class ArrangeAyahState(
  val sura: Int,
  val ayah: Int,
  val words: List<WordTranslation>,
  val placed: List<Int>,
  val pool: List<Int>
) {

  /**
   * Solved when every tile is placed and the text reads in the right order. Comparing the
   * text rather than tile ids means repeated words (e.g. the two «فِى» in 34:1) are
   * interchangeable.
   */
  val isSolved: Boolean
    get() = pool.isEmpty() &&
        placed.size == words.size &&
        placed.indices.all { i -> words[placed[i]].arabicText == words[i].arabicText }

  fun isPlaced(tile: Int): Boolean = tile in placed

  /** Moves [tile] from the pool into the answer at [index] (appending by default). */
  fun place(tile: Int, index: Int = placed.size): ArrangeAyahState {
    if (tile !in pool) return this
    val newPlaced = placed.toMutableList()
    newPlaced.add(index.coerceIn(0, newPlaced.size), tile)
    return copy(placed = newPlaced, pool = pool - tile)
  }

  /** Sends [tile] from the answer back to the end of the pool. */
  fun unplace(tile: Int): ArrangeAyahState {
    if (tile !in placed) return this
    return copy(placed = placed - tile, pool = pool + tile)
  }

  /**
   * Moves a placed [tile] so that it ends up at [index] in the answer, where [index] is
   * counted among the other placed tiles (i.e. with [tile] removed).
   */
  fun move(tile: Int, index: Int): ArrangeAyahState {
    if (tile !in placed) return this
    val newPlaced = placed.toMutableList()
    newPlaced.remove(tile)
    newPlaced.add(index.coerceIn(0, newPlaced.size), tile)
    return if (newPlaced == placed) this else copy(placed = newPlaced)
  }

  /** Puts every tile back into a freshly shuffled pool. */
  fun reshuffle(random: Random = Random.Default): ArrangeAyahState =
    create(sura, ayah, words, random)

  companion object {
    private const val MAX_SHUFFLE_ATTEMPTS = 10

    // waqf / pause marks (ۖ ۗ ۘ ۙ ۚ ۛ ۜ), rub el hizb (۞), sajdah (۩) and the rtl mark
    private const val MARKS = "\u06D6-\u06DC\u06DE\u06E9\u200F"
    private val TRAILING_MARKS = Regex("(\\s+[$MARKS]+)+\\s*$")
    private val LEADING_MARKS = Regex("^\\s*([$MARKS]+\\s+)+")

    /**
     * Removes free-standing pause marks around a word, since they give away where a phrase
     * ends. A mark written inside a word (like the ۜ in «وَيَبْصُۜطُ») is part of its
     * spelling and stays.
     */
    fun cleanArabic(text: String): String {
      val stripped = LEADING_MARKS.replace(TRAILING_MARKS.replace(text, ""), "")
      return stripped.ifBlank { text.trim() }
    }

    fun create(
      sura: Int,
      ayah: Int,
      words: List<WordTranslation>,
      random: Random = Random.Default
    ): ArrangeAyahState {
      val cleaned = words
        .sortedBy { it.wordPosition }
        .map { it.copy(arabicText = cleanArabic(it.arabicText)) }
      val correct = cleaned.map { it.arabicText }

      // avoid handing out a pool that is already in the right order, when that is possible
      var pool = cleaned.indices.shuffled(random)
      var attempts = 1
      while (attempts < MAX_SHUFFLE_ATTEMPTS && pool.map { cleaned[it].arabicText } == correct &&
        correct.distinct().size > 1
      ) {
        pool = cleaned.indices.shuffled(random)
        attempts++
      }
      return ArrangeAyahState(sura, ayah, cleaned, placed = emptyList(), pool = pool)
    }
  }
}
