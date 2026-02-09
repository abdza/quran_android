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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.data.model.note.NoteWithLabels
import com.quran.mobile.feature.notes.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteDetailScreen(
  noteWithLabels: NoteWithLabels,
  childNotes: List<NoteWithLabels>,
  onAyahClicked: (sura: Int, ayah: Int) -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onAddReply: () -> Unit,
  onBack: () -> Unit,
  onShare: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val note = noteWithLabels.note

  LazyColumn(modifier = modifier.padding(16.dp)) {
    item {
      // Ayah reference
      Text(
        text = "${note.sura}:${note.ayah}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onAyahClicked(note.sura, note.ayah) }
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Note text
      Text(
        text = note.text,
        style = MaterialTheme.typography.bodyLarge
      )

      // Labels
      if (noteWithLabels.labels.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          noteWithLabels.labels.forEach { label ->
            SuggestionChip(
              onClick = { },
              label = { Text(label.name) }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(onClick = onEdit) {
          Text(stringResource(R.string.edit_note))
        }
        OutlinedButton(onClick = onDelete) {
          Text(stringResource(R.string.delete_note))
        }
        if (onShare != null) {
          OutlinedButton(onClick = onShare) {
            Text(stringResource(R.string.share_note))
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider()
      Spacer(modifier = Modifier.height(12.dp))

      // Replies header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(R.string.replies_count, childNotes.size),
          style = MaterialTheme.typography.titleSmall
        )
        Button(onClick = onAddReply) {
          Text(stringResource(R.string.add_reply))
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }

    items(childNotes, key = { it.note.id }) { child ->
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
            text = child.note.text,
            style = MaterialTheme.typography.bodyMedium
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = formatRelativeTime(child.note.updatedDate),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

private fun formatRelativeTime(epochSeconds: Long): String {
  val now = System.currentTimeMillis() / 1000
  val diff = now - epochSeconds
  return when {
    diff < 60 -> "just now"
    diff < 3600 -> "${diff / 60}m ago"
    diff < 86400 -> "${diff / 3600}h ago"
    diff < 604800 -> "${diff / 86400}d ago"
    else -> "${diff / 604800}w ago"
  }
}
