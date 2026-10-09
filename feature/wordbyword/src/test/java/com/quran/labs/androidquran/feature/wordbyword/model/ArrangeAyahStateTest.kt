package com.quran.labs.androidquran.feature.wordbyword.model

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.WordTranslation
import org.junit.Test
import kotlin.random.Random

class ArrangeAyahStateTest {

  private fun words(vararg texts: String): List<WordTranslation> =
    texts.mapIndexed { i, text -> WordTranslation(34, 1, i + 1, text, "t$i") }

  private fun state(vararg texts: String) =
    ArrangeAyahState.create(34, 1, words(*texts), Random(42))

  @Test
  fun createPutsEveryTileInThePool() {
    val state = state("a", "b", "c", "d")
    assertThat(state.placed).isEmpty()
    assertThat(state.pool).containsExactly(0, 1, 2, 3)
    assertThat(state.isSolved).isFalse()
  }

  @Test
  fun createDoesNotHandOutTheAnswer() {
    repeat(50) { seed ->
      val state = ArrangeAyahState.create(1, 1, words("a", "b"), Random(seed))
      assertThat(state.pool).containsExactly(1, 0).inOrder()
    }
  }

  @Test
  fun createSortsByWordPosition() {
    val shuffled = words("a", "b", "c").reversed()
    val state = ArrangeAyahState.create(1, 1, shuffled, Random(1))
    assertThat(state.words.map { it.arabicText }).containsExactly("a", "b", "c").inOrder()
  }

  @Test
  fun placingInOrderSolves() {
    var state = state("a", "b", "c")
    listOf(0, 1, 2).forEach { state = state.place(it) }
    assertThat(state.placed).containsExactly(0, 1, 2).inOrder()
    assertThat(state.pool).isEmpty()
    assertThat(state.isSolved).isTrue()
  }

  @Test
  fun wrongOrderIsNotSolved() {
    var state = state("a", "b", "c")
    listOf(1, 0, 2).forEach { state = state.place(it) }
    assertThat(state.isSolved).isFalse()
  }

  @Test
  fun repeatedWordsAreInterchangeable() {
    var state = state("x", "فِى", "y", "فِى")
    listOf(0, 3, 2, 1).forEach { state = state.place(it) }
    assertThat(state.isSolved).isTrue()
  }

  @Test
  fun placeAtIndexInserts() {
    val state = state("a", "b", "c").place(0).place(2).place(1, index = 1)
    assertThat(state.placed).containsExactly(0, 1, 2).inOrder()
  }

  @Test
  fun unplaceReturnsToPool() {
    val state = state("a", "b", "c").place(1).unplace(1)
    assertThat(state.placed).isEmpty()
    assertThat(state.pool).contains(1)
    assertThat(state.pool).hasSize(3)
  }

  @Test
  fun placeAndUnplaceIgnoreTilesInTheWrongArea() {
    val start = state("a", "b")
    assertThat(start.unplace(0)).isSameInstanceAs(start)
    val placed = start.place(0)
    assertThat(placed.place(0)).isSameInstanceAs(placed)
  }

  @Test
  fun moveReordersPlacedTiles() {
    var state = state("a", "b", "c", "d")
    listOf(1, 2, 3, 0).forEach { state = state.place(it) }
    // drag the last tile to the front
    state = state.move(0, 0)
    assertThat(state.placed).containsExactly(0, 1, 2, 3).inOrder()
    assertThat(state.isSolved).isTrue()

    state = state.move(0, 3)
    assertThat(state.placed).containsExactly(1, 2, 3, 0).inOrder()
  }

  @Test
  fun moveToSameIndexKeepsInstance() {
    val state = state("a", "b", "c").place(0).place(1)
    assertThat(state.move(1, 1)).isSameInstanceAs(state)
  }

  @Test
  fun reshuffleResetsProgress() {
    val state = state("a", "b", "c").place(0).place(1).reshuffle(Random(3))
    assertThat(state.placed).isEmpty()
    assertThat(state.pool).containsExactly(0, 1, 2)
  }

  @Test
  fun cleanArabicStripsFreeStandingMarks() {
    assertThat(ArrangeAyahState.cleanArabic("رَيْبَ ۛ")).isEqualTo("رَيْبَ")
    assertThat(ArrangeAyahState.cleanArabic("مَّرْقَدِنَا ۜ ۗ")).isEqualTo("مَّرْقَدِنَا")
    assertThat(ArrangeAyahState.cleanArabic("ٱلْعَظِيمِ ۩\u200f")).isEqualTo("ٱلْعَظِيمِ")
    assertThat(ArrangeAyahState.cleanArabic("۞ إِنَّ")).isEqualTo("إِنَّ")
  }

  @Test
  fun cleanArabicKeepsMarksInsideWords() {
    assertThat(ArrangeAyahState.cleanArabic("وَيَبْصُۜطُ")).isEqualTo("وَيَبْصُۜطُ")
    assertThat(ArrangeAyahState.cleanArabic("عِوَجَاۜ")).isEqualTo("عِوَجَاۜ")
    assertThat(ArrangeAyahState.cleanArabic("بَعْدَ مَا")).isEqualTo("بَعْدَ مَا")
  }
}
