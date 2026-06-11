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
                text = "Sync directory changed",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = buildString {
                    if (oldPath == null) {
                        append("Sync history from a previous version was detected for:\n\n")
                        append("$newPath\n\n")
                    } else {
                        append("The sync directory has changed:\n\n")
                        append("Previous: $oldPath\n")
                        append("Now: $newPath\n\n")
                    }
                    append("Choose how to continue:\n\n")
                    append("• Clear & reconcile (recommended): resets the sync history and rebuilds ")
                    append("the local index for this directory, then syncs. Local files that already ")
                    append("match the server are adopted as-is — they are not re-downloaded or ")
                    append("overwritten. Files that genuinely differ are kept as conflict copies, so ")
                    append("nothing is lost. Your local files are never deleted.\n\n")
                    append("• Cancel: makes no changes. Syncing stays paused for this directory until ")
                    append("you reconcile, or switch the setting back to the previous directory.")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onClearAndContinue) {
                Text(
                    text = "Clear & reconcile",
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
