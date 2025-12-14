package com.gladomat.linklet.ui.screens.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.ui.theme.LinkLetAppTheme

/**
 * Dialog for renaming a note file.
 * Validates the filename and ensures .org extension.
 */
@Composable
fun RenameNoteDialog(
    currentFilename: String,
    onConfirm: (newFilename: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Extract just the filename without extension for editing
    val baseName = currentFilename.removeSuffix(".org")
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = baseName,
                selection = TextRange(0, baseName.length), // Select all for easy replacement
            )
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Validate filename
    fun validate(name: String): String? {
        return when {
            name.isBlank() -> "Filename cannot be empty"
            name.contains("/") || name.contains("\\") -> "Filename cannot contain / or \\"
            name.contains(":") -> "Filename cannot contain :"
            name.startsWith(".") -> "Filename cannot start with ."
            else -> null
        }
    }

    fun handleConfirm() {
        val newName = textFieldValue.text.trim()
        val error = validate(newName)
        if (error != null) {
            errorMessage = error
        } else {
            val finalName = if (newName.endsWith(".org")) newName else "$newName.org"
            onConfirm(finalName)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = "Rename Note",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a new filename for this note:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        errorMessage = null // Clear error on edit
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("Filename") },
                    suffix = { Text(".org") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { handleConfirm() }),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Note: Backlinks remain intact because they use the note's ID, not the filename.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { handleConfirm() }) {
                Text("Rename")
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
private fun RenameNoteDialogPreview() {
    LinkLetAppTheme {
        Surface {
            RenameNoteDialog(
                currentFilename = "my-note.org",
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

@Preview
@Composable
private fun RenameNoteDialogDarkPreview() {
    LinkLetAppTheme(darkTheme = true) {
        Surface {
            RenameNoteDialog(
                currentFilename = "20231215-project-notes.org",
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
