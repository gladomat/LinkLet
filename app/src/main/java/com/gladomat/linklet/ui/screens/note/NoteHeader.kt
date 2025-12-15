package com.gladomat.linklet.ui.screens.note

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.domain.service.SearchOptions
import com.gladomat.linklet.ui.theme.LinkLetAppTheme

/**
 * Sticky header for the note view screen.
 * Contains back, home, search, and edit actions along with note title and timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteHeader(
    filename: String,
    lastModified: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchActive: Boolean,
    searchOptions: SearchOptions,
    regexError: String?,
    activeMatchNumber: Int,
    totalMatches: Int,
    onToggleCaseSensitive: () -> Unit,
    onToggleWholeWord: () -> Unit,
    onToggleRegex: () -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClearSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    searchFocusRequester: FocusRequester? = null,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSearch: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        NoteAppBar(
            filename = filename,
            lastModified = lastModified,
            searchActive = searchActive,
            onBack = onBack,
            onHome = onHome,
            onOpenSearch = onOpenSearch,
            onCloseSearch = onCloseSearch,
            onEdit = onEdit,
        )

        AnimatedVisibility(
            visible = searchActive,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            NoteSearchPanel(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchOptions = searchOptions,
                regexError = regexError,
                activeMatchNumber = activeMatchNumber,
                totalMatches = totalMatches,
                onToggleCaseSensitive = onToggleCaseSensitive,
                onToggleWholeWord = onToggleWholeWord,
                onToggleRegex = onToggleRegex,
                onPrevMatch = onPrevMatch,
                onNextMatch = onNextMatch,
                onClearSearch = onClearSearch,
                onCloseSearch = onCloseSearch,
                searchFocusRequester = searchFocusRequester,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteAppBar(
    filename: String,
    lastModified: String?,
    searchActive: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onEdit: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            Row {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onHome) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Go to home",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = filename,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (lastModified != null) {
                    Text(
                        text = "Last modified: $lastModified",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        actions = {
            if (searchActive) {
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit note",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun NoteSearchPanel(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchOptions: SearchOptions,
    regexError: String?,
    activeMatchNumber: Int,
    totalMatches: Int,
    onToggleCaseSensitive: () -> Unit,
    onToggleWholeWord: () -> Unit,
    onToggleRegex: () -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClearSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    searchFocusRequester: FocusRequester?,
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val matchCounterText = remember(activeMatchNumber, totalMatches) {
        "${activeMatchNumber.coerceAtLeast(0)} / ${totalMatches.coerceAtLeast(0)}"
    }

    LaunchedEffect(searchFocusRequester) {
        val requester = searchFocusRequester ?: return@LaunchedEffect
        runCatching {
            withFrameNanos { }
            requester.requestFocus()
            Log.d("NoteSearch", "Requested focus for search field")
        }.onFailure { e ->
            Log.w("NoteSearch", "Failed to request focus for search field", e)
        }
    }

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .let { base ->
                            val requester = searchFocusRequester
                            if (requester != null) base.focusRequester(requester) else base
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Enter -> {
                                    if (event.isShiftPressed) onPrevMatch() else onNextMatch()
                                    true
                                }
                                Key.Escape -> {
                                    focusManager.clearFocus(force = true)
                                    onCloseSearch()
                                    true
                                }
                                else -> false
                            }
                        },
                    singleLine = true,
                    placeholder = { Text(text = "Search in note") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search query",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f),
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f),
                        disabledIndicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f),
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onNextMatch() }),
                )

                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        onCloseSearch()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconToggleButton(
                    checked = searchOptions.caseSensitive,
                    onCheckedChange = { onToggleCaseSensitive() },
                ) {
                    Text(text = "Aa", style = MaterialTheme.typography.labelMedium)
                }
                IconToggleButton(
                    checked = searchOptions.wholeWord,
                    onCheckedChange = { onToggleWholeWord() },
                ) {
                    Text(text = "W", style = MaterialTheme.typography.labelMedium)
                }
                IconToggleButton(
                    checked = searchOptions.useRegex,
                    onCheckedChange = { onToggleRegex() },
                ) {
                    Text(text = ".*", style = MaterialTheme.typography.labelMedium)
                }

                Text(
                    text = matchCounterText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                IconButton(
                    onClick = onPrevMatch,
                    enabled = totalMatches > 0,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous match",
                    )
                }
                IconButton(
                    onClick = onNextMatch,
                    enabled = totalMatches > 0,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next match",
                    )
                }
            }

            if (regexError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = regexError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview
@Composable
private fun NoteHeaderPreview() {
    LinkLetAppTheme {
        NoteHeader(
            filename = "oolong_tea_brewing.org",
            lastModified = "Today at 10:42 AM",
            searchQuery = "",
            onSearchQueryChange = {},
            searchActive = false,
            searchOptions = SearchOptions(),
            regexError = null,
            activeMatchNumber = 3,
            totalMatches = 17,
            onToggleCaseSensitive = {},
            onToggleWholeWord = {},
            onToggleRegex = {},
            onPrevMatch = {},
            onNextMatch = {},
            onClearSearch = {},
            onCloseSearch = {},
            onBack = {},
            onHome = {},
            onOpenSearch = {},
            onEdit = {},
        )
    }
}

@Preview
@Composable
private fun NoteHeaderDarkPreview() {
    LinkLetAppTheme(darkTheme = true) {
        NoteHeader(
            filename = "oolong_tea_brewing.org",
            lastModified = "Today at 10:42 AM",
            searchQuery = "",
            onSearchQueryChange = {},
            searchActive = false,
            searchOptions = SearchOptions(),
            regexError = null,
            activeMatchNumber = 3,
            totalMatches = 17,
            onToggleCaseSensitive = {},
            onToggleWholeWord = {},
            onToggleRegex = {},
            onPrevMatch = {},
            onNextMatch = {},
            onClearSearch = {},
            onCloseSearch = {},
            onBack = {},
            onHome = {},
            onOpenSearch = {},
            onEdit = {},
        )
    }
}

@Preview
@Composable
private fun NoteHeaderSearchExpandedPreview() {
    LinkLetAppTheme {
        NoteHeader(
            filename = "oolong_tea_brewing.org",
            lastModified = "Today at 10:42 AM",
            searchQuery = "water",
            onSearchQueryChange = {},
            searchActive = true,
            searchOptions = SearchOptions(),
            regexError = null,
            activeMatchNumber = 1,
            totalMatches = 5,
            onToggleCaseSensitive = {},
            onToggleWholeWord = {},
            onToggleRegex = {},
            onPrevMatch = {},
            onNextMatch = {},
            onClearSearch = {},
            onCloseSearch = {},
            onBack = {},
            onHome = {},
            onOpenSearch = {},
            onEdit = {},
        )
    }
}
