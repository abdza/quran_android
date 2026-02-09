package com.quran.mobile.feature.notes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quran.data.model.note.NoteLabel
import com.quran.mobile.feature.notes.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
  initialText: String = "",
  initialLabelIds: List<Long> = emptyList(),
  labels: List<NoteLabel>,
  onSave: (text: String, labelIds: List<Long>) -> Unit,
  onCancel: () -> Unit,
  onAddLabel: (name: String) -> Unit,
  isEdit: Boolean = false
) {
  var noteText by remember { mutableStateOf(initialText) }
  var selectedLabelIds by remember { mutableStateOf(initialLabelIds.toSet()) }
  var showAddLabel by remember { mutableStateOf(false) }
  var newLabelName by remember { mutableStateOf("") }

  Column(modifier = Modifier.padding(16.dp)) {
    Text(
      text = stringResource(if (isEdit) R.string.edit_note else R.string.add_note),
      style = MaterialTheme.typography.titleMedium
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
      value = noteText,
      onValueChange = { noteText = it },
      modifier = Modifier.fillMaxWidth(),
      placeholder = { Text(stringResource(R.string.note_placeholder)) },
      minLines = 3,
      maxLines = 8
    )

    Spacer(modifier = Modifier.height(12.dp))

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      labels.forEach { label ->
        FilterChip(
          selected = label.id in selectedLabelIds,
          onClick = {
            selectedLabelIds = if (label.id in selectedLabelIds) {
              selectedLabelIds - label.id
            } else {
              selectedLabelIds + label.id
            }
          },
          label = { Text(label.name) }
        )
      }

      TextButton(onClick = { showAddLabel = !showAddLabel }) {
        Text(stringResource(R.string.add_label))
      }
    }

    if (showAddLabel) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = newLabelName,
          onValueChange = { newLabelName = it },
          modifier = Modifier.weight(1f),
          placeholder = { Text(stringResource(R.string.add_label)) },
          singleLine = true
        )
        Button(
          onClick = {
            if (newLabelName.isNotBlank()) {
              onAddLabel(newLabelName.trim())
              newLabelName = ""
              showAddLabel = false
            }
          }
        ) {
          Text(stringResource(R.string.save))
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End
    ) {
      OutlinedButton(onClick = onCancel) {
        Text(stringResource(R.string.cancel))
      }
      Spacer(modifier = Modifier.width(8.dp))
      Button(
        onClick = { onSave(noteText, selectedLabelIds.toList()) },
        enabled = noteText.isNotBlank()
      ) {
        Text(stringResource(R.string.save))
      }
    }
  }
}
