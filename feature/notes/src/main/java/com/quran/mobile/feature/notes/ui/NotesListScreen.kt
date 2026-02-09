package com.quran.mobile.feature.notes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quran.data.model.note.NoteLabel
import com.quran.data.model.note.NoteWithLabels
import androidx.compose.ui.res.painterResource
import com.quran.mobile.feature.notes.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesListScreen(
  notes: List<NoteWithLabels>,
  labels: List<NoteLabel>,
  selectedLabelId: Long?,
  selectedSura: Int?,
  searchQuery: String,
  onNoteClicked: (NoteWithLabels) -> Unit,
  onFilterByLabel: (Long?) -> Unit,
  onFilterBySura: (Int?) -> Unit,
  onSearchQueryChanged: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.fillMaxSize()) {
    // Search bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchQueryChanged,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      placeholder = { Text(stringResource(R.string.search_notes)) },
      leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
      singleLine = true
    )

    // Filter bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      LabelFilterDropdown(
        labels = labels,
        selectedLabelId = selectedLabelId,
        onLabelSelected = onFilterByLabel
      )

      SuraFilterDropdown(
        selectedSura = selectedSura,
        onSuraSelected = onFilterBySura
      )
    }

    if (notes.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = stringResource(R.string.no_notes),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(notes, key = { it.note.id }) { noteWithLabels ->
          NotesListCard(
            noteWithLabels = noteWithLabels,
            onClick = { onNoteClicked(noteWithLabels) }
          )
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesListCard(
  noteWithLabels: NoteWithLabels,
  onClick: () -> Unit
) {
  val note = noteWithLabels.note
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = "${note.sura}:${note.ayah}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = note.text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
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

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        if (noteWithLabels.childCount > 0) {
          Text(
            text = stringResource(R.string.replies_count, noteWithLabels.childCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          Spacer(modifier = Modifier.width(1.dp))
        }

        Text(
          text = formatRelativeTime(note.updatedDate),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun LabelFilterDropdown(
  labels: List<NoteLabel>,
  selectedLabelId: Long?,
  onLabelSelected: (Long?) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel = labels.find { it.id == selectedLabelId }

  FilterChip(
    selected = selectedLabelId != null,
    onClick = { expanded = true },
    label = {
      Text(selectedLabel?.name ?: stringResource(R.string.filter_by_label))
    }
  )

  DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    DropdownMenuItem(
      text = { Text(stringResource(R.string.all_labels)) },
      onClick = {
        onLabelSelected(null)
        expanded = false
      }
    )
    labels.forEach { label ->
      DropdownMenuItem(
        text = { Text(label.name) },
        onClick = {
          onLabelSelected(label.id)
          expanded = false
        }
      )
    }
  }
}

@Composable
private fun SuraFilterDropdown(
  selectedSura: Int?,
  onSuraSelected: (Int?) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }

  FilterChip(
    selected = selectedSura != null,
    onClick = { expanded = true },
    label = {
      Text(
        if (selectedSura != null) stringResource(R.string.sura_number, selectedSura)
        else stringResource(R.string.filter_by_surah)
      )
    }
  )

  DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    DropdownMenuItem(
      text = { Text(stringResource(R.string.all_surahs)) },
      onClick = {
        onSuraSelected(null)
        expanded = false
      }
    )
    (1..114).forEach { sura ->
      DropdownMenuItem(
        text = { Text(stringResource(R.string.sura_number, sura)) },
        onClick = {
          onSuraSelected(sura)
          expanded = false
        }
      )
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
