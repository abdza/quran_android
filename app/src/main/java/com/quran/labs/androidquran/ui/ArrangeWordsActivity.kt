package com.quran.labs.androidquran.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.feature.wordbyword.model.ArrangeAyahState
import com.quran.labs.androidquran.feature.wordbyword.ui.ArrangeAyahView
import com.quran.labs.androidquran.feature.wordbyword.ui.ArrangeWordsAdapter
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.wordbyword.WordTranslationDataSource
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Memorization exercise: every ayah in the chosen range is shown with its words shuffled,
 * and the user puts them back in order.
 */
class ArrangeWordsActivity : AppCompatActivity(), ArrangeWordsAdapter.Callbacks {

  @Inject
  lateinit var wordTranslationDataSource: WordTranslationDataSource

  @Inject
  lateinit var quranSettings: QuranSettings

  private lateinit var recyclerView: RecyclerView
  private lateinit var progressBar: ProgressBar
  private lateinit var emptyStateView: TextView
  private lateinit var adapter: ArrangeWordsAdapter

  private var startTime = 0L
  private var completionShown = false

  // auto scrolling while a word is dragged near the top or bottom of the list
  private var lastDragY = NO_DRAG
  private var isAutoScrolling = false
  private val autoScroll = object : Runnable {
    override fun run() {
      val speed = autoScrollSpeed()
      if (speed == 0) {
        isAutoScrolling = false
        return
      }
      recyclerView.scrollBy(0, speed)
      recyclerView.postOnAnimation(this)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)

    val startSura = intent.getIntExtra(EXTRA_START_SURA, 0)
    val startAyah = intent.getIntExtra(EXTRA_START_AYAH, 0)
    val endSura = intent.getIntExtra(EXTRA_END_SURA, 0)
    val endAyah = intent.getIntExtra(EXTRA_END_AYAH, 0)
    if (startSura == 0 || startAyah == 0 || endSura == 0 || endAyah == 0) {
      finish()
      return
    }

    setContentView(R.layout.arrange_words_activity)

    val root = findViewById<ViewGroup>(R.id.root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = insets.top
        leftMargin = insets.left
        rightMargin = insets.right
      }
      windowInsets
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.menu_arrange_words)

    progressBar = findViewById(R.id.progress)
    emptyStateView = findViewById(R.id.empty_state)

    adapter = ArrangeWordsAdapter(
      headerText = { state -> getString(R.string.arrange_words_ayah_header, state.sura, state.ayah) },
      callbacks = this
    )
    recyclerView = findViewById(R.id.recycler_view)
    recyclerView.layoutManager = LinearLayoutManager(this)
    // rebuilding a moved word would only flash it
    recyclerView.itemAnimator = null
    recyclerView.adapter = adapter
    val listPadding = recyclerView.paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      view.setPadding(0, listPadding, 0, listPadding + insets.bottom)
      windowInsets
    }

    lifecycleScope.launch {
      val ayat = wordTranslationDataSource.getWordsForVerseRange(startSura, startAyah, endSura, endAyah)
      progressBar.visibility = View.GONE
      val states = ayat
        .filter { it.words.isNotEmpty() }
        .map { ArrangeAyahState.create(it.sura, it.ayah, it.words) }
      if (states.isEmpty()) {
        emptyStateView.visibility = View.VISIBLE
      } else {
        adapter.setData(states, currentStyle())
        startTime = SystemClock.elapsedRealtime()
      }
    }
  }

  private fun currentStyle(): ArrangeAyahView.Style {
    val nightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    return ArrangeAyahView.Style(
      isNightMode = nightMode,
      arabicTypeface = TypefaceManager.getTafseerTypeface(this),
      arabicTextSize = quranSettings.wbwArabicTextSize.toFloat(),
      translationTextSize = quranSettings.wbwTranslationTextSize.toFloat(),
      showTranslation = quranSettings.showTranslationInArrangeWords(),
      showTransliteration = quranSettings.showTransliteration()
    )
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.arrange_words_menu, menu)
    menu.findItem(R.id.arrange_show_translation).isChecked =
      quranSettings.showTranslationInArrangeWords()
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        finish()
        return true
      }

      R.id.arrange_show_translation -> {
        val show = !item.isChecked
        item.isChecked = show
        quranSettings.setShowTranslationInArrangeWords(show)
        adapter.setStyle(currentStyle())
        return true
      }

      R.id.arrange_shuffle -> {
        shuffleAgain()
        return true
      }
    }
    return super.onOptionsItemSelected(item)
  }

  private fun shuffleAgain() {
    val states = adapter.ayahStates
    if (states.isEmpty()) return
    completionShown = false
    adapter.setData(states.map { it.reshuffle() }, currentStyle())
    recyclerView.scrollToPosition(0)
    startTime = SystemClock.elapsedRealtime()
  }

  override fun onProgressChanged(solved: Int, total: Int) {
    supportActionBar?.subtitle = getString(R.string.arrange_words_progress, solved, total)
    if (total > 0 && solved == total && !completionShown) {
      completionShown = true
      showCompletion(total)
    }
  }

  private fun showCompletion(total: Int) {
    val elapsedSeconds = (SystemClock.elapsedRealtime() - startTime) / 1000
    AlertDialog.Builder(this, R.style.QuranDialogTheme)
      .setTitle(R.string.arrange_words_complete_title)
      .setMessage(
        resources.getQuantityString(
          R.plurals.arrange_words_complete_message,
          total,
          total,
          DateUtils.formatElapsedTime(elapsedSeconds)
        )
      )
      .setPositiveButton(R.string.arrange_words_shuffle) { _, _ -> shuffleAgain() }
      .setNegativeButton(R.string.arrange_words_close, null)
      .show()
  }

  override fun onDragMoved(windowY: Int) {
    lastDragY = windowY
    if (!isAutoScrolling && autoScrollSpeed() != 0) {
      isAutoScrolling = true
      recyclerView.postOnAnimation(autoScroll)
    }
  }

  override fun onDragEnded() {
    lastDragY = NO_DRAG
  }

  /** Pixels to scroll per frame: faster the closer the finger is to the edge. */
  private fun autoScrollSpeed(): Int {
    if (lastDragY == NO_DRAG) return 0
    val location = IntArray(2)
    recyclerView.getLocationInWindow(location)
    val top = location[1]
    val bottom = top + recyclerView.height
    val density = resources.displayMetrics.density
    val edge = AUTO_SCROLL_EDGE_DP * density
    val maxSpeed = AUTO_SCROLL_MAX_SPEED_DP * density
    return when {
      lastDragY < top + edge -> -(maxSpeed * (1 - (lastDragY - top) / edge).coerceIn(0f, 1f)).roundToInt()
      lastDragY > bottom - edge -> (maxSpeed * (1 - (bottom - lastDragY) / edge).coerceIn(0f, 1f)).roundToInt()
      else -> 0
    }
  }

  companion object {
    private const val EXTRA_START_SURA = "startSura"
    private const val EXTRA_START_AYAH = "startAyah"
    private const val EXTRA_END_SURA = "endSura"
    private const val EXTRA_END_AYAH = "endAyah"
    private const val EXTRA_TITLE = "title"

    private const val NO_DRAG = -1
    private const val AUTO_SCROLL_EDGE_DP = 72
    private const val AUTO_SCROLL_MAX_SPEED_DP = 16

    fun intent(
      context: Context,
      startSura: Int,
      startAyah: Int,
      endSura: Int,
      endAyah: Int,
      title: String
    ): Intent {
      return Intent(context, ArrangeWordsActivity::class.java)
        .putExtra(EXTRA_START_SURA, startSura)
        .putExtra(EXTRA_START_AYAH, startAyah)
        .putExtra(EXTRA_END_SURA, endSura)
        .putExtra(EXTRA_END_AYAH, endAyah)
        .putExtra(EXTRA_TITLE, title)
    }
  }
}
