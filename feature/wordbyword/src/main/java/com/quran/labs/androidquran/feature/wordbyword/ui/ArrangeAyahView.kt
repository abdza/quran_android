package com.quran.labs.androidquran.feature.wordbyword.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.google.android.flexbox.FlexboxLayout
import com.quran.labs.androidquran.feature.wordbyword.R
import com.quran.labs.androidquran.feature.wordbyword.model.ArrangeAyahState
import kotlin.math.abs

/**
 * One ayah of the "arrange the words" exercise: the user's answer on top and the shuffled
 * words below.
 *
 * - tapping a word in the pool appends it to the answer, and tapping a word in the answer
 *   sends it back to the pool
 * - any word can be dragged (sideways, or after a long press) to a spot in the answer, and
 *   dropping an answer word on the pool sends it back
 *
 * The background is light red until the answer reads correctly, then turns green and the
 * ayah is locked.
 */
class ArrangeAyahView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  interface Listener {
    fun onStateChanged(view: ArrangeAyahView, state: ArrangeAyahState)

    /** Called while a word is dragged, with the finger's y position in the window. */
    fun onDragMoved(windowY: Int)
    fun onDragEnded()
  }

  data class Style(
    val isNightMode: Boolean,
    val arabicTypeface: Typeface?,
    val arabicTextSize: Float,
    val translationTextSize: Float,
    val showTranslation: Boolean,
    val showTransliteration: Boolean
  )

  /** Identifies the dragged word, so other ayat ignore it. */
  private class DragToken(val owner: ArrangeAyahView, val tile: Int)

  private val header: TextView
  private val answer: FlexboxLayout
  private val hint: TextView
  private val divider: View
  private val pool: FlexboxLayout

  private var state: ArrangeAyahState? = null
  private var style: Style? = null
  private var listener: Listener? = null
  private var headerText: CharSequence = ""
  private var tiles: List<WordCardView> = emptyList()
  private var draggingTile = NO_TILE

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private val cornerRadius = 12 * resources.displayMetrics.density
  private val strokeWidth = (1 * resources.displayMetrics.density).toInt().coerceAtLeast(1)

  init {
    orientation = VERTICAL
    LayoutInflater.from(context).inflate(R.layout.arrange_ayah_view, this, true)
    header = findViewById(R.id.arrange_header)
    answer = findViewById(R.id.arrange_answer)
    hint = findViewById(R.id.arrange_hint)
    divider = findViewById(R.id.arrange_divider)
    pool = findViewById(R.id.arrange_pool)

    answer.setOnDragListener(::onAnswerDrag)
    pool.setOnDragListener(::onPoolDrag)
    // the rest of the card only reports the position, for auto scrolling
    setOnDragListener { _, event -> onOtherDrag(event) }
  }

  fun bind(
    state: ArrangeAyahState,
    headerText: CharSequence,
    style: Style,
    listener: Listener
  ) {
    val current = this.state
    val sameAyah = current != null && current.sura == state.sura && current.ayah == state.ayah
    if (!sameAyah || style != this.style || tiles.size != state.words.size) {
      this.style = style
      tiles = state.words.mapIndexed { index, word ->
        WordCardView(context).apply {
          setNightMode(style.isNightMode)
          setShowTransliteration(style.showTransliteration)
          setShowTranslation(style.showTranslation)
          setArabicTypeface(style.arabicTypeface)
          setArabicTextSize(style.arabicTextSize)
          setTranslationTextSize(style.translationTextSize)
          setWord(word)
          attachTileHandlers(this, index)
        }
      }
      applyColors(style.isNightMode)
    }
    if (!sameAyah) {
      draggingTile = NO_TILE
    }

    this.listener = listener
    this.state = state
    this.headerText = headerText
    render()
  }

  private fun applyColors(isNightMode: Boolean) {
    header.setTextColor(
      color(if (isNightMode) R.color.word_by_word_verse_number_night else R.color.word_by_word_verse_number)
    )
    hint.setTextColor(header.currentTextColor)
    divider.setBackgroundColor(
      color(if (isNightMode) R.color.arrange_ayah_pool_divider_night else R.color.arrange_ayah_pool_divider)
    )
  }

  private fun render() {
    val state = state ?: return
    val nightMode = style?.isNightMode == true
    // while a word is being dragged, the answer may pass through the right order on the way
    // to somewhere else, so only show it as solved once the word is let go
    val solved = state.isSolved && draggingTile == NO_TILE

    fillContainer(answer, state.placed)
    fillContainer(pool, state.pool)
    tiles.forEachIndexed { index, tile ->
      tile.alpha = if (index == draggingTile) DRAGGING_ALPHA else 1f
    }

    hint.visibility = if (state.placed.isEmpty()) VISIBLE else GONE
    val poolVisibility = if (state.pool.isEmpty()) GONE else VISIBLE
    pool.visibility = poolVisibility
    divider.visibility = poolVisibility

    background = GradientDrawable().apply {
      cornerRadius = this@ArrangeAyahView.cornerRadius
      val (fill, stroke) = when {
        solved && nightMode -> R.color.arrange_ayah_solved_night to R.color.arrange_ayah_solved_border_night
        solved -> R.color.arrange_ayah_solved to R.color.arrange_ayah_solved_border
        nightMode -> R.color.arrange_ayah_unsolved_night to R.color.arrange_ayah_unsolved_border_night
        else -> R.color.arrange_ayah_unsolved to R.color.arrange_ayah_unsolved_border
      }
      setColor(color(fill))
      setStroke(strokeWidth, color(stroke))
    }
    header.text = if (solved) "$headerText$SOLVED_SUFFIX" else headerText
  }

  private fun fillContainer(container: ViewGroup, tileIds: List<Int>) {
    val wanted = tileIds.map { tiles[it] }
    val unchanged = container.childCount == wanted.size &&
        wanted.indices.all { container.getChildAt(it) === wanted[it] }
    if (unchanged) return

    container.removeAllViews()
    wanted.forEach { tile ->
      (tile.parent as? ViewGroup)?.removeView(tile)
      container.addView(
        tile,
        FlexboxLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
      )
    }
  }

  private fun update(newState: ArrangeAyahState, notify: Boolean) {
    if (newState === state) return
    val wasSolved = state?.isSolved == true
    state = newState
    render()
    if (notify) {
      notifyChanged(wasSolved)
    }
  }

  private fun notifyChanged(wasSolved: Boolean) {
    val state = state ?: return
    if (state.isSolved && !wasSolved) {
      performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          HapticFeedbackConstants.CONFIRM
        } else {
          HapticFeedbackConstants.LONG_PRESS
        }
      )
    }
    listener?.onStateChanged(this, state)
  }

  private fun isLocked(): Boolean = state?.isSolved == true && draggingTile == NO_TILE

  private fun onTileTapped(tile: Int) {
    val state = state ?: return
    if (isLocked()) return
    update(if (state.isPlaced(tile)) state.unplace(tile) else state.place(tile), notify = true)
  }

  // region dragging

  private fun startDrag(view: View, tile: Int): Boolean {
    if (isLocked() || draggingTile != NO_TILE) return false
    val started = ViewCompat.startDragAndDrop(
      view, null, DragShadowBuilder(view), DragToken(this, tile), 0
    )
    if (started) {
      view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
      draggingTile = tile
      render()
    }
    return started
  }

  private fun finishDrag() {
    if (draggingTile == NO_TILE) return
    draggingTile = NO_TILE
    render()
    listener?.onDragEnded()
    // checked once the word is let go, see render()
    notifyChanged(wasSolved = false)
  }

  private fun tokenFor(event: DragEvent): DragToken? =
    (event.localState as? DragToken)?.takeIf { it.owner === this }

  private fun reportDrag(view: View, event: DragEvent) {
    val location = IntArray(2)
    view.getLocationInWindow(location)
    listener?.onDragMoved(location[1] + event.y.toInt())
  }

  private fun onAnswerDrag(view: View, event: DragEvent): Boolean {
    val token = tokenFor(event) ?: return onOtherDrag(event)
    when (event.action) {
      DragEvent.ACTION_DRAG_LOCATION, DragEvent.ACTION_DROP -> {
        reportDrag(view, event)
        val state = state ?: return true
        val index = insertionIndex(event.x, event.y, tiles[token.tile])
        val newState = if (state.isPlaced(token.tile)) {
          state.move(token.tile, index)
        } else {
          state.place(token.tile, index)
        }
        update(newState, notify = false)
      }
      DragEvent.ACTION_DRAG_ENDED -> finishDrag()
    }
    return true
  }

  private fun onPoolDrag(view: View, event: DragEvent): Boolean {
    val token = tokenFor(event) ?: return onOtherDrag(event)
    when (event.action) {
      DragEvent.ACTION_DRAG_LOCATION -> reportDrag(view, event)
      DragEvent.ACTION_DROP -> state?.let { update(it.unplace(token.tile), notify = false) }
      DragEvent.ACTION_DRAG_ENDED -> finishDrag()
    }
    return true
  }

  /** Drags over the rest of this card, or of words belonging to another ayah. */
  private fun onOtherDrag(event: DragEvent): Boolean {
    if (event.localState !is DragToken) return false
    when (event.action) {
      DragEvent.ACTION_DRAG_LOCATION -> reportDrag(this, event)
      DragEvent.ACTION_DRAG_ENDED -> if (tokenFor(event) != null) finishDrag() else listener?.onDragEnded()
    }
    return true
  }

  /**
   * Where a word dropped at ([x], [y]) in the answer should go, counted among the answer's
   * other words. Words flow right to left and wrap downwards, so the closest row wins and
   * then the side of the nearest word in that row.
   */
  private fun insertionIndex(x: Float, y: Float, dragged: View): Int {
    var bestIndex = 0
    var bestScore = Float.MAX_VALUE
    var bestIsAfter = false
    var sequence = 0
    for (i in 0 until answer.childCount) {
      val child = answer.getChildAt(i)
      if (child === dragged) continue

      val dy = when {
        y < child.top -> child.top - y
        y > child.bottom -> y - child.bottom
        else -> 0f
      }
      val centerX = (child.left + child.right) / 2f
      val score = dy * ROW_WEIGHT + abs(x - centerX)
      if (score < bestScore) {
        bestScore = score
        bestIndex = sequence
        // right to left: left of a word's middle means after it
        bestIsAfter = x < centerX
      }
      sequence++
    }
    return if (sequence == 0) 0 else bestIndex + if (bestIsAfter) 1 else 0
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun attachTileHandlers(view: View, tile: Int) {
    // performClick (from the touch listener or accessibility) taps the word
    view.setOnClickListener { onTileTapped(tile) }
    view.setOnTouchListener(TileTouchListener(tile))
  }

  /**
   * Taps place or remove a word. A sideways drag starts moving it straight away; vertical
   * movement is left to the list so it can scroll, unless the word was long pressed first.
   */
  private inner class TileTouchListener(private val tile: Int) : OnTouchListener {
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var view: View? = null
    private val longPress = Runnable {
      val view = view ?: return@Runnable
      if (tracking && startDrag(view, tile)) {
        stopTracking(view)
      }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          if (isLocked()) return false
          view = v
          downX = event.rawX
          downY = event.rawY
          tracking = true
          v.isPressed = true
          v.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
        }
        MotionEvent.ACTION_MOVE -> if (tracking) {
          val dx = abs(event.rawX - downX)
          val dy = abs(event.rawY - downY)
          if (dx > touchSlop && dx > dy) {
            v.parent?.requestDisallowInterceptTouchEvent(true)
            stopTracking(v)
            startDrag(v, tile)
          } else if (dy > touchSlop) {
            stopTracking(v)
          }
        }
        MotionEvent.ACTION_UP -> if (tracking) {
          stopTracking(v)
          v.performClick()
        }
        MotionEvent.ACTION_CANCEL -> stopTracking(v)
      }
      return true
    }

    private fun stopTracking(v: View) {
      tracking = false
      v.isPressed = false
      v.removeCallbacks(longPress)
    }
  }

  // endregion

  private fun color(resource: Int): Int = ContextCompat.getColor(context, resource)

  companion object {
    private const val NO_TILE = -1
    private const val DRAGGING_ALPHA = 0.3f
    private const val ROW_WEIGHT = 1000f
    private const val SOLVED_SUFFIX = "  ✓"
  }
}
