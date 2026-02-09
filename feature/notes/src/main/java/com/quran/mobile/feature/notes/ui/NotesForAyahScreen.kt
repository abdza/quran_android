package com.quran.mobile.feature.notes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quran.data.model.note.NoteLabel
import com.quran.data.model.note.NoteWithLabels
import com.quran.mobile.feature.notes.R

@Composable
fun NotesForAyahScreen(
  notes: List<NoteWithLabels>,
  labels: List<NoteLabel>,
  onAddNote: () -> Unit,
  onEditNote: (NoteWithLabels) -> Unit,
  onDeleteNote: (Long) -> Unit,
  onViewReplies: (NoteWithLabels) -> Unit,
  modifier: Modifier = Modifier
) {
  var showingEditor by remember { mutableStateOf(false) }

  Column(modifier = modifier.padding(8.dp)) {
    if (notes.isEmpty() && !showingEditor) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = stringResource(R.string.no_notes),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddNote) {
          Text(stringResource(R.string.add_note))
        }
      }
    } else {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(R.string.notes_title),
          style = MaterialTheme.typography.titleSmall
        )
        Button(onClick = onAddNote) {
          Text(stringResource(R.string.add_note))
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      LazyColumn {
        items(notes, key = { it.note.id }) { noteWithLabels ->
          NoteCard(
            noteWithLabels = noteWithLabels,
            onEdit = { onEditNote(noteWithLabels) },
            onDelete = { onDeleteNote(noteWithLabels.note.id) },
            onViewReplies = { onViewReplies(noteWithLabels) }
          )
          Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(
  noteWithLabels: NoteWithLabels,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onViewReplies: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onEdit)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = noteWithLabels.note.text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis
      )

      if (noteWithLabels.labels.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          noteWithLabels.labels.forEach { label ->
            SuggestionChip(
              onClick = { },
              label = { Text(label.name, style = MaterialTheme.typography.labelSmall) }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))
      HorizontalDivider()

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (noteWithLabels.childCount > 0) {
          Text(
            text = stringResource(R.string.replies_count, noteWithLabels.childCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onViewReplies)
          )
        } else {
          Text(
            text = stringResource(R.string.add_reply),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onViewReplies)
          )
        }

        IconButton(onClick = onDelete) {
          Icon(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.delete_note),
            tint = MaterialTheme.colorScheme.error
          )
        }
      }
    }
  }
}
