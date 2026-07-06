package com.gladomat.linklet.ui.screens.noteedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteIndexEntry
import com.gladomat.linklet.ui.theme.LinkLetAppTheme

/**
 * Search picker for inserting an org-mode link to another note into the note
 * currently being edited. Selecting a result inserts the link and dismisses.
 */
@Composable
fun LinkPickerDialog(
    query: String,
    results: List<NoteIndexEntry>,
    onQueryChange: (String) -> Unit,
    onSelect: (NoteIndexEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = "Add link",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search notes") },
                    placeholder = { Text("Type a note title…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )

                if (results.isEmpty()) {
                    Text(
                        text = if (query.isBlank()) "Start typing to search notes" else "No matching notes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(items = results, key = { it.id.path }) { entry ->
                            ListItem(
                                headlineContent = {
                                    Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(entry.id.path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(entry) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Preview
@Composable
private fun LinkPickerDialogPreview() {
    LinkLetAppTheme {
        LinkPickerDialog(
            query = "proj",
            results = listOf(
                NoteIndexEntry(
                    id = NoteId("notes/project-plan.org"),
                    title = "Project Plan",
                    fileTags = emptyList(),
                    deletedAt = null,
                    linksReady = true,
                    orgId = "ABC-123",
                ),
                NoteIndexEntry(
                    id = NoteId("notes/project-notes.org"),
                    title = "Project Notes",
                    fileTags = emptyList(),
                    deletedAt = null,
                    linksReady = true,
                ),
            ),
            onQueryChange = {},
            onSelect = {},
            onDismiss = {},
        )
    }
}
