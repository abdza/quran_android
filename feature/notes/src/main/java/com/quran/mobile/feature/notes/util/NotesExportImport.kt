package com.quran.mobile.feature.notes.util

import android.content.Context
import android.net.Uri
import com.quran.data.dao.NotesDao
import com.quran.data.model.note.NoteWithLabels
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportResult(val imported: Int, val skipped: Int)

object NotesExportImport {

  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

  suspend fun exportNotes(
    context: Context,
    fileUri: Uri,
    notesDao: NotesDao,
    suraNames: Array<String>
  ): Int {
    val allNotes = notesDao.allNotesSortedByUpdated()
    if (allNotes.isEmpty()) return 0

    val parentNotes = allNotes.filter { it.note.parentNoteId == null }
    val grouped = parentNotes.groupBy { it.note.sura to it.note.ayah }
      .toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })

    val sb = StringBuilder()
    sb.appendLine("# Quran Notes")
    sb.appendLine()

    var exportedCount = 0
    var firstAyah = true

    for ((key, notesForAyah) in grouped) {
      val (sura, ayah) = key
      val suraName = if (sura in 1..suraNames.size) suraNames[sura - 1] else "Unknown"

      if (!firstAyah) {
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
      }
      firstAyah = false

      sb.appendLine("## $suraName $sura:$ayah")

      for (noteWithLabels in notesForAyah) {
        sb.appendLine()
        appendNote(sb, noteWithLabels)

        val children = notesDao.childNotes(noteWithLabels.note.id)
        for (child in children) {
          sb.appendLine()
          sb.appendLine("#### Reply")
          appendNoteBody(sb, child)
        }
      }

      exportedCount += notesForAyah.size
    }

    context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
      outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    return exportedCount
  }

  private fun appendNote(sb: StringBuilder, noteWithLabels: NoteWithLabels) {
    sb.appendLine("### Note")
    val note = noteWithLabels.note
    val labels = noteWithLabels.labels
    if (labels.isNotEmpty()) {
      sb.appendLine("**Labels:** ${labels.joinToString(", ") { it.name }}")
    }
    sb.appendLine("**Created:** ${dateFormat.format(Date(note.createdDate))}")
    sb.appendLine("**Updated:** ${dateFormat.format(Date(note.updatedDate))}")
    sb.appendLine()
    sb.appendLine(note.text)
  }

  private fun appendNoteBody(sb: StringBuilder, noteWithLabels: NoteWithLabels) {
    val note = noteWithLabels.note
    val labels = noteWithLabels.labels
    if (labels.isNotEmpty()) {
      sb.appendLine("**Labels:** ${labels.joinToString(", ") { it.name }}")
    }
    sb.appendLine("**Created:** ${dateFormat.format(Date(note.createdDate))}")
    sb.appendLine("**Updated:** ${dateFormat.format(Date(note.updatedDate))}")
    sb.appendLine()
    sb.appendLine(note.text)
  }

  suspend fun importNotes(
    context: Context,
    fileUri: Uri,
    notesDao: NotesDao,
    quranInfo: com.quran.data.core.QuranInfo
  ): ImportResult {
    val content = context.contentResolver.openInputStream(fileUri)?.use {
      it.bufferedReader().readText()
    } ?: return ImportResult(0, 0)

    val existingNotes = notesDao.allNotesSortedByUpdated()
    val existingSet = existingNotes.map {
      Triple(it.note.sura, it.note.ayah, it.note.text.trim())
    }.toSet()

    val allLabels = notesDao.allLabels().associateBy { it.name }.toMutableMap()

    val sections = parseAllSections(content)

    var imported = 0
    var skipped = 0

    for (section in sections) {
      for (parsedNote in section.notes) {
        val noteText = parsedNote.text.trim()
        if (noteText.isEmpty()) continue

        val key = Triple(section.sura, section.ayah, noteText)
        if (key in existingSet) {
          skipped++
          continue
        }

        val page = quranInfo.getPageFromSuraAyah(section.sura, section.ayah)
        val noteId = if (parsedNote.createdDate != null && parsedNote.updatedDate != null) {
          notesDao.addNoteWithDates(section.sura, section.ayah, page, noteText, null, parsedNote.createdDate, parsedNote.updatedDate)
        } else {
          notesDao.addNote(section.sura, section.ayah, page, noteText)
        }

        val labelIds = resolveLabelIds(parsedNote.labels, allLabels, notesDao)
        if (labelIds.isNotEmpty()) {
          notesDao.setLabelsForNote(noteId, labelIds)
        }

        for (reply in parsedNote.replies) {
          val replyText = reply.text.trim()
          if (replyText.isEmpty()) continue
          val replyId = if (reply.createdDate != null && reply.updatedDate != null) {
            notesDao.addNoteWithDates(section.sura, section.ayah, page, replyText, noteId, reply.createdDate, reply.updatedDate)
          } else {
            notesDao.addNote(section.sura, section.ayah, page, replyText, noteId)
          }
          val replyLabelIds = resolveLabelIds(reply.labels, allLabels, notesDao)
          if (replyLabelIds.isNotEmpty()) {
            notesDao.setLabelsForNote(replyId, replyLabelIds)
          }
        }

        imported++
      }
    }

    return ImportResult(imported, skipped)
  }

  private suspend fun resolveLabelIds(
    labelNames: List<String>,
    knownLabels: MutableMap<String, com.quran.data.model.note.NoteLabel>,
    notesDao: NotesDao
  ): List<Long> {
    val ids = mutableListOf<Long>()
    for (name in labelNames) {
      val existing = knownLabels[name]
      if (existing != null) {
        ids.add(existing.id)
      } else {
        val newId = notesDao.addLabel(name)
        knownLabels[name] = com.quran.data.model.note.NoteLabel(newId, name)
        ids.add(newId)
      }
    }
    return ids
  }

  private data class ParsedSection(val sura: Int, val ayah: Int, val notes: List<ParsedNote>)
  private data class ParsedNote(val text: String, val labels: List<String>, val replies: List<ParsedReply>, val createdDate: Long? = null, val updatedDate: Long? = null)
  private data class ParsedReply(val text: String, val labels: List<String>, val createdDate: Long? = null, val updatedDate: Long? = null)

  private fun parseAllSections(content: String): List<ParsedSection> {
    val lines = content.lines()
    val sections = mutableListOf<ParsedSection>()

    var currentSura = 0
    var currentAyah = 0
    var currentNotes = mutableListOf<ParsedNote>()
    var inSection = false

    var noteLabels = mutableListOf<String>()
    var noteTextLines = mutableListOf<String>()
    var noteReplies = mutableListOf<ParsedReply>()
    var noteCreatedDate: Long? = null
    var noteUpdatedDate: Long? = null
    var inNote = false

    var replyLabels = mutableListOf<String>()
    var replyTextLines = mutableListOf<String>()
    var replyCreatedDate: Long? = null
    var replyUpdatedDate: Long? = null
    var inReply = false

    for (line in lines) {
      when {
        line.startsWith("## ") && !line.startsWith("### ") -> {
          // New ayah section - flush previous
          if (inReply && replyTextLines.isNotEmpty()) {
            noteReplies.add(ParsedReply(cleanText(replyTextLines), replyLabels, replyCreatedDate, replyUpdatedDate))
          }
          if (inNote && noteTextLines.isNotEmpty()) {
            currentNotes.add(ParsedNote(cleanText(noteTextLines), noteLabels, noteReplies, noteCreatedDate, noteUpdatedDate))
          }
          if (inSection && currentNotes.isNotEmpty()) {
            sections.add(ParsedSection(currentSura, currentAyah, currentNotes))
          }

          val match = Regex("""(\d+):(\d+)\s*$""").find(line)
          if (match != null) {
            currentSura = match.groupValues[1].toIntOrNull() ?: 0
            currentAyah = match.groupValues[2].toIntOrNull() ?: 0
            inSection = true
          } else {
            inSection = false
          }
          currentNotes = mutableListOf()
          inNote = false
          inReply = false
        }
        line.startsWith("### Note") -> {
          if (inReply && replyTextLines.isNotEmpty()) {
            noteReplies.add(ParsedReply(cleanText(replyTextLines), replyLabels, replyCreatedDate, replyUpdatedDate))
          }
          if (inNote && noteTextLines.isNotEmpty()) {
            currentNotes.add(ParsedNote(cleanText(noteTextLines), noteLabels, noteReplies, noteCreatedDate, noteUpdatedDate))
          }
          inNote = true
          inReply = false
          noteLabels = mutableListOf()
          noteTextLines = mutableListOf()
          noteReplies = mutableListOf()
          noteCreatedDate = null
          noteUpdatedDate = null
        }
        line.startsWith("#### Reply") -> {
          if (inReply && replyTextLines.isNotEmpty()) {
            noteReplies.add(ParsedReply(cleanText(replyTextLines), replyLabels, replyCreatedDate, replyUpdatedDate))
          }
          inReply = true
          replyLabels = mutableListOf()
          replyTextLines = mutableListOf()
          replyCreatedDate = null
          replyUpdatedDate = null
        }
        line.startsWith("---") -> {
          // separator, handled by "## " header
        }
        line.startsWith("# Quran Notes") -> {
          // skip file header
        }
        line.startsWith("**Labels:**") -> {
          val labelsStr = line.removePrefix("**Labels:**").trim()
          val parsed = labelsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
          if (inReply) replyLabels.addAll(parsed) else noteLabels.addAll(parsed)
        }
        line.startsWith("**Created:**") -> {
          val parsed = parseDateFromLine(line.removePrefix("**Created:**").trim())
          if (inReply) replyCreatedDate = parsed else noteCreatedDate = parsed
        }
        line.startsWith("**Updated:**") -> {
          val parsed = parseDateFromLine(line.removePrefix("**Updated:**").trim())
          if (inReply) replyUpdatedDate = parsed else noteUpdatedDate = parsed
        }
        else -> {
          if (inReply) replyTextLines.add(line)
          else if (inNote) noteTextLines.add(line)
        }
      }
    }

    // Flush remaining
    if (inReply && replyTextLines.isNotEmpty()) {
      noteReplies.add(ParsedReply(cleanText(replyTextLines), replyLabels, replyCreatedDate, replyUpdatedDate))
    }
    if (inNote && noteTextLines.isNotEmpty()) {
      currentNotes.add(ParsedNote(cleanText(noteTextLines), noteLabels, noteReplies, noteCreatedDate, noteUpdatedDate))
    }
    if (inSection && currentNotes.isNotEmpty()) {
      sections.add(ParsedSection(currentSura, currentAyah, currentNotes))
    }

    return sections
  }

  private fun parseDateFromLine(dateStr: String): Long? {
    return try {
      dateFormat.parse(dateStr)?.time?.let { it / 1000 }
    } catch (e: Exception) {
      null
    }
  }

  private fun cleanText(lines: List<String>): String {
    return lines.joinToString("\n").trim()
  }
}
