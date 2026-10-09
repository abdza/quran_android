package com.quran.labs.androidquran.ui.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.ArrangeWordsActivity
import dev.zacsweers.metro.Inject

/** Picks the pages, surah or ayat to practise in [ArrangeWordsActivity]. */
class ArrangeWordsRangeDialog : DialogFragment() {

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  private lateinit var modeGroup: RadioGroup
  private lateinit var pagesPanel: View
  private lateinit var fromPage: EditText
  private lateinit var toPage: EditText
  private lateinit var suraSpinner: Spinner
  private lateinit var ayatPanel: View
  private lateinit var fromAyah: EditText
  private lateinit var toAyah: EditText
  private lateinit var errorText: TextView

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val activity = requireActivity()

    @SuppressLint("InflateParams")
    val layout = activity.layoutInflater.inflate(R.layout.arrange_words_range_dialog, null)
    modeGroup = layout.findViewById(R.id.arrange_mode)
    pagesPanel = layout.findViewById(R.id.arrange_pages_panel)
    fromPage = layout.findViewById(R.id.arrange_from_page)
    toPage = layout.findViewById(R.id.arrange_to_page)
    suraSpinner = layout.findViewById(R.id.arrange_sura)
    ayatPanel = layout.findViewById(R.id.arrange_ayat_panel)
    fromAyah = layout.findViewById(R.id.arrange_from_ayah)
    toAyah = layout.findViewById(R.id.arrange_to_ayah)
    errorText = layout.findViewById(R.id.arrange_error)

    val page = requireArguments().getInt(EXTRA_PAGE).let {
      if (quranInfo.isValidPage(it)) it else 1
    }
    val pageRange = quranInfo.getVerseRangeForPage(page)
    val sura = pageRange.startSura

    val suraNames = (Constants.SURA_FIRST..Constants.SURA_LAST).map { quranDisplayData.getSuraNameWithNumber(activity, it, false) }
    suraSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, suraNames)
      .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    if (savedInstanceState == null) {
      fromPage.setText(page.toString())
      toPage.setText(page.toString())
      suraSpinner.setSelection(sura - 1)
      // the ayat of the current page that belong to its first surah
      val lastAyah = if (pageRange.endingSura == sura) {
        pageRange.endingAyah
      } else {
        quranInfo.getNumberOfAyahs(sura)
      }
      fromAyah.setText(pageRange.startAyah.toString())
      toAyah.setText(lastAyah.toString())
      modeGroup.check(R.id.arrange_mode_pages)
    }

    suraSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      private var selected = suraSpinner.selectedItemPosition

      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        // a newly picked surah starts with all of its ayat
        if (position != selected) {
          selected = position
          fromAyah.setText("1")
          toAyah.setText(quranInfo.getNumberOfAyahs(position + 1).toString())
        }
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    modeGroup.setOnCheckedChangeListener { _, _ -> updatePanels() }
    updatePanels()

    return AlertDialog.Builder(activity, R.style.QuranDialogTheme)
      .setTitle(R.string.arrange_words_choose_title)
      .setView(layout)
      .setPositiveButton(R.string.arrange_words_start, null)
      .setNegativeButton(android.R.string.cancel, null)
      .create()
  }

  override fun onResume() {
    super.onResume()
    // replaces the default listener so an invalid range keeps the dialog open
    (dialog as AlertDialog).getButton(Dialog.BUTTON_POSITIVE).setOnClickListener { start() }
  }

  private fun updatePanels() {
    val mode = modeGroup.checkedRadioButtonId
    pagesPanel.visibility = if (mode == R.id.arrange_mode_pages) View.VISIBLE else View.GONE
    suraSpinner.visibility = if (mode == R.id.arrange_mode_pages) View.GONE else View.VISIBLE
    ayatPanel.visibility = if (mode == R.id.arrange_mode_ayat) View.VISIBLE else View.GONE
    errorText.visibility = View.GONE
  }

  private fun start() {
    val context = requireContext()
    val intent = when (modeGroup.checkedRadioButtonId) {
      R.id.arrange_mode_pages -> {
        val firstPage = 1 + quranInfo.skip
        val lastPage = quranInfo.numberOfPages
        val from = fromPage.number()
        val to = toPage.number()
        if (from == null || to == null || from > to ||
          !quranInfo.isValidPage(from) || !quranInfo.isValidPage(to)
        ) {
          showError(getString(R.string.arrange_words_invalid_pages, firstPage, lastPage))
          return
        }
        val start = quranInfo.getVerseRangeForPage(from)
        val end = quranInfo.getVerseRangeForPage(to)
        val title = if (from == to) {
          getString(R.string.arrange_words_title_page, from)
        } else {
          getString(R.string.arrange_words_title_pages, from, to)
        }
        ArrangeWordsActivity.intent(
          context, start.startSura, start.startAyah, end.endingSura, end.endingAyah, title
        )
      }

      R.id.arrange_mode_sura -> {
        val sura = suraSpinner.selectedItemPosition + 1
        ArrangeWordsActivity.intent(
          context, sura, 1, sura, quranInfo.getNumberOfAyahs(sura), suraName(sura)
        )
      }

      else -> {
        val sura = suraSpinner.selectedItemPosition + 1
        val ayahCount = quranInfo.getNumberOfAyahs(sura)
        val from = fromAyah.number()
        val to = toAyah.number()
        if (from == null || to == null || from < 1 || from > to || to > ayahCount) {
          showError(getString(R.string.arrange_words_invalid_ayat, ayahCount))
          return
        }
        val title = if (from == to) {
          getString(R.string.arrange_words_title_ayah, suraName(sura), from)
        } else {
          getString(R.string.arrange_words_title_ayat, suraName(sura), from, to)
        }
        ArrangeWordsActivity.intent(context, sura, from, sura, to, title)
      }
    }
    startActivity(intent)
    dismiss()
  }

  private fun suraName(sura: Int): String =
    quranDisplayData.getSuraName(requireContext(), sura, false)

  private fun showError(message: String) {
    errorText.text = message
    errorText.visibility = View.VISIBLE
  }

  private fun EditText.number(): Int? = text?.toString()?.trim()?.toIntOrNull()

  companion object {
    const val TAG = "ArrangeWordsRangeDialog"
    private const val EXTRA_PAGE = "page"

    fun newInstance(page: Int): ArrangeWordsRangeDialog {
      return ArrangeWordsRangeDialog().apply {
        arguments = Bundle().apply { putInt(EXTRA_PAGE, page) }
      }
    }
  }
}
