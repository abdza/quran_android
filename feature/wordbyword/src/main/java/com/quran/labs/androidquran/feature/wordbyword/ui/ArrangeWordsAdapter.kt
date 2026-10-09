package com.quran.labs.androidquran.feature.wordbyword.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.feature.wordbyword.model.ArrangeAyahState

/** One [ArrangeAyahView] per ayah of the chosen portion. */
class ArrangeWordsAdapter(
  private val headerText: (ArrangeAyahState) -> CharSequence,
  private val callbacks: Callbacks
) : RecyclerView.Adapter<ArrangeWordsAdapter.ViewHolder>() {

  interface Callbacks {
    fun onProgressChanged(solved: Int, total: Int)
    fun onDragMoved(windowY: Int)
    fun onDragEnded()
  }

  private var states: MutableList<ArrangeAyahState> = mutableListOf()
  private var style: ArrangeAyahView.Style? = null

  val ayahStates: List<ArrangeAyahState> get() = states

  @SuppressLint("NotifyDataSetChanged")
  fun setData(states: List<ArrangeAyahState>, style: ArrangeAyahView.Style) {
    this.states = states.toMutableList()
    this.style = style
    notifyDataSetChanged()
    reportProgress()
  }

  @SuppressLint("NotifyDataSetChanged")
  fun setStyle(style: ArrangeAyahView.Style) {
    if (style == this.style) return
    this.style = style
    notifyDataSetChanged()
  }

  private fun reportProgress() {
    callbacks.onProgressChanged(states.count { it.isSolved }, states.size)
  }

  override fun getItemCount(): Int = states.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = ArrangeAyahView(parent.context)
    val margin = (8 * parent.resources.displayMetrics.density).toInt()
    view.layoutParams = RecyclerView.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(margin, margin / 2, margin, margin / 2) }
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val style = style ?: return
    val state = states[position]
    holder.view.bind(state, headerText(state), style, holder)
  }

  inner class ViewHolder(val view: ArrangeAyahView) :
    RecyclerView.ViewHolder(view), ArrangeAyahView.Listener {

    override fun onStateChanged(view: ArrangeAyahView, state: ArrangeAyahState) {
      val position = bindingAdapterPosition
      if (position == RecyclerView.NO_POSITION) return
      val current = states[position]
      if (current.sura != state.sura || current.ayah != state.ayah) return
      // the view already shows this state, so there is nothing to rebind
      val wasSolved = current.isSolved
      states[position] = state
      if (wasSolved != state.isSolved) {
        reportProgress()
      }
    }

    override fun onDragMoved(windowY: Int) = callbacks.onDragMoved(windowY)

    override fun onDragEnded() = callbacks.onDragEnded()
  }
}
