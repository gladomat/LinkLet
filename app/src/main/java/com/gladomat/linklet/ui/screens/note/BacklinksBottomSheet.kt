package com.gladomat.linklet.ui.screens.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.ui.theme.LinkLetAppTheme

/**
 * Bottom sheet dialog showing backlinks to the current note.
 * Clicking a backlink navigates to that note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklinksBottomSheet(
    backlinks: List<LinkEntityDto>,
    onBacklinkClick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            // Header with title and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Backlinks",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (backlinks.isEmpty()) {
                // Empty state
                Text(
                    text = "No backlinks found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            } else {
                // Backlinks list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(backlinks, key = { it.source }) { backlink ->
                        BacklinkItem(
                            backlink = backlink,
                            onClick = {
                                onBacklinkClick(backlink.source)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BacklinkItem(
    backlink: LinkEntityDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayTitle = backlink.sourceTitle ?: backlink.alias ?: backlink.source
    val displayPath = backlink.source

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (displayPath != displayTitle) {
                Text(
                    text = displayPath.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun BacklinksBottomSheetPreview() {
    LinkLetAppTheme {
        Surface {
            BacklinksBottomSheet(
                backlinks = listOf(
                    LinkEntityDto(
                        source = "notes/linked_note.org",
                        target = "notes/current.org",
                        alias = "Linked Note",
                        sourceTitle = "My Linked Note",
                    ),
                    LinkEntityDto(
                        source = "notes/another.org",
                        target = "notes/current.org",
                        alias = null,
                        sourceTitle = "Another Reference",
                    ),
                    LinkEntityDto(
                        source = "notes/third_note.org",
                        target = "notes/current.org",
                        alias = null,
                        sourceTitle = null,
                    ),
                ),
                onBacklinkClick = {},
                onDismiss = {},
            )
        }
    }
}

@Preview
@Composable
private fun BacklinksBottomSheetEmptyPreview() {
    LinkLetAppTheme(darkTheme = true) {
        Surface {
            BacklinksBottomSheet(
                backlinks = emptyList(),
                onBacklinkClick = {},
                onDismiss = {},
            )
        }
    }
}
