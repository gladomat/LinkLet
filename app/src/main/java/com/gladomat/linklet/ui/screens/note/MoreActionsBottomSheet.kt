package com.gladomat.linklet.ui.screens.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.ui.theme.LinkLetAppTheme

/**
 * Bottom sheet with "More" actions for note operations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreActionsBottomSheet(
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onTags: () -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Note Actions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // View controls
            ActionItem(
                icon = Icons.Default.UnfoldMore,
                label = "Expand All",
                onClick = {
                    onExpandAll()
                    onDismiss()
                },
            )

            ActionItem(
                icon = Icons.Default.UnfoldLess,
                label = "Collapse All",
                onClick = {
                    onCollapseAll()
                    onDismiss()
                },
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Metadata actions
            ActionItem(
                icon = Icons.Default.Settings,
                label = "Properties",
                onClick = {
                    onProperties()
                    onDismiss()
                },
            )

            ActionItem(
                icon = Icons.Default.Label,
                label = "Tags",
                onClick = {
                    onTags()
                    onDismiss()
                },
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // File actions
            ActionItem(
                icon = Icons.Default.FileCopy,
                label = "Duplicate",
                onClick = {
                    onDuplicate()
                    onDismiss()
                },
            )

            ActionItem(
                icon = Icons.Default.DriveFileRenameOutline,
                label = "Rename",
                onClick = {
                    onRename()
                    onDismiss()
                },
            )

            ActionItem(
                icon = Icons.Default.Share,
                label = "Export",
                onClick = {
                    onExport()
                    onDismiss()
                },
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            ActionItem(
                icon = Icons.Default.Delete,
                label = "Delete",
                onClick = {
                    onDelete()
                    onDismiss()
                },
                isDestructive = true,
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    color = contentColor,
                )
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

@Preview
@Composable
private fun MoreActionsBottomSheetPreview() {
    LinkLetAppTheme {
        Surface {
            MoreActionsBottomSheet(
                onDismiss = {},
                onDuplicate = {},
                onRename = {},
                onExport = {},
                onDelete = {},
                onProperties = {},
                onTags = {},
                onExpandAll = {},
                onCollapseAll = {},
            )
        }
    }
}
