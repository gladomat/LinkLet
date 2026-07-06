package com.gladomat.linklet.ui.screens.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.ui.theme.LinkLetAppTheme

/**
 * Bottom navigation bar for the note view screen.
 * Contains Share, Favorite, Backlinks, Graph, and More actions.
 */
@Composable
fun NoteFooterBar(
    isFavorite: Boolean,
    backlinksCount: Int,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onBacklinks: () -> Unit,
    onGraph: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasBacklinks = backlinksCount > 0

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onShare,
            icon = {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { Text("Share") },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        NavigationBarItem(
            selected = isFavorite,
            onClick = onFavorite,
            icon = {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { Text("Favorite") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        NavigationBarItem(
            selected = false,
            enabled = hasBacklinks,
            onClick = onBacklinks,
            icon = {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { Text("Backlinks") },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
        )
        NavigationBarItem(
            selected = false,
            onClick = onGraph,
            icon = {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { Text("Graph") },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        NavigationBarItem(
            selected = false,
            onClick = onMore,
            icon = {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { Text("More") },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Preview
@Composable
private fun NoteFooterBarPreview() {
    LinkLetAppTheme {
        Surface {
            NoteFooterBar(
                isFavorite = false,
                backlinksCount = 3,
                onShare = {},
                onFavorite = {},
                onBacklinks = {},
                onGraph = {},
                onMore = {},
            )
        }
    }
}

@Preview
@Composable
private fun NoteFooterBarDisabledBacklinksPreview() {
    LinkLetAppTheme(darkTheme = true) {
        Surface {
            NoteFooterBar(
                isFavorite = true,
                backlinksCount = 0,
                onShare = {},
                onFavorite = {},
                onBacklinks = {},
                onGraph = {},
                onMore = {},
            )
        }
    }
}
