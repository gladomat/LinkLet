package com.gladomat.linklet.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun SyncDirectoryChangeDialog(
    oldPath: String?,
    newPath: String,
    onClearAndContinue: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "WebDAV Directory Changed",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = buildString {
                    if (oldPath == null) {
                        append("Sync state from a previous version was detected.\n\n")
                        append("Current directory: $newPath\n\n")
                        append("To ensure data safety and prevent accidental deletions, ")
                        append("you need to clear the sync state before continuing. ")
                        append("This will treat the current directory as a fresh installation.\n\n")
                    } else {
                        append("The WebDAV sync directory has changed:\n\n")
                        append("Previous: $oldPath\n")
                        append("Current: $newPath\n\n")
                        append("To prevent data loss, you need to clear the sync state before continuing. ")
                        append("This will treat the new directory as a fresh installation.\n\n")
                    }
                    append("Your local files will not be deleted, but the sync history will be reset.")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onClearAndContinue) {
                Text(
                    text = "Clear & Continue",
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
    )
}
