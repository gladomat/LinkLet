package com.gladomat.linklet.ui.screens.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.ui.theme.LinkLetAppTheme

/**
 * Dialog for managing file tags with autocomplete suggestions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerDialog(
    currentTags: List<String>,
    allAvailableTags: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTags by remember(currentTags) { mutableStateOf(currentTags.toMutableList()) }
    var inputText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    val suggestions = remember(inputText, allAvailableTags, selectedTags) {
        if (inputText.isBlank()) {
            emptyList()
        } else {
            allAvailableTags
                .filter { it.contains(inputText, ignoreCase = true) && it !in selectedTags }
                .take(5)
        }
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (trimmed.isNotEmpty() && trimmed !in selectedTags) {
            selectedTags = (selectedTags + trimmed).toMutableList()
        }
        inputText = ""
        showSuggestions = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = "Edit Tags",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Tag input with autocomplete
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            showSuggestions = it.isNotBlank()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Add tag") },
                        placeholder = { Text("Type to add or search...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (inputText.isNotBlank()) addTag(inputText)
                            },
                        ),
                    )

                    DropdownMenu(
                        expanded = showSuggestions && suggestions.isNotEmpty(),
                        onDismissRequest = { showSuggestions = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = { addTag(suggestion) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Selected tags
                if (selectedTags.isNotEmpty()) {
                    Text(
                        text = "Current Tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { 
                                    selectedTags = selectedTags.filter { it != tag }.toMutableList()
                                },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove $tag",
                                        modifier = Modifier.height(16.dp),
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No tags added yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Suggested tags from repository
                val unusedSuggestions = allAvailableTags.filter { it !in selectedTags }.take(10)
                if (unusedSuggestions.isNotEmpty() && inputText.isBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Suggestions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        unusedSuggestions.forEach { tag ->
                            AssistChip(
                                onClick = { addTag(tag) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedTags) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Preview
@Composable
private fun TagPickerDialogPreview() {
    LinkLetAppTheme {
        Surface {
            TagPickerDialog(
                currentTags = listOf("project", "work"),
                allAvailableTags = listOf("project", "work", "personal", "idea", "todo", "archive"),
                onSave = {},
                onDismiss = {},
            )
        }
    }
}
