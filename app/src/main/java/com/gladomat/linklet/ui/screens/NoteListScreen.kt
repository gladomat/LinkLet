package com.gladomat.linklet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.viewmodel.NoteListItemUiModel
import com.gladomat.linklet.viewmodel.NoteListUiState
import com.gladomat.linklet.viewmodel.NoteListViewModel
import com.gladomat.linklet.ui.theme.LinkLetAppTheme

@Composable
fun NoteListRoute(
    onOpenNote: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: NoteListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    NoteListScreen(
        state = state,
        query = query,
        onQueryChange = viewModel::updateSearchQuery,
        onClearQuery = viewModel::clearSearchQuery,
        onOpenNote = onOpenNote,
        onRetry = viewModel::refresh,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    state: NoteListUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenNote: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Notes") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Open settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            NoteSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (state) {
                NoteListUiState.Loading -> LoadingState(
                    modifier = Modifier.weight(1f),
                )
                is NoteListUiState.Success -> SuccessState(
                    notes = state.notes,
                    onOpenNote = onOpenNote,
                    modifier = Modifier.weight(1f),
                )
                is NoteListUiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SuccessState(
    notes: List<NoteListItemUiModel>,
    onOpenNote: (String) -> Unit,
    modifier: Modifier,
) {
    if (notes.isEmpty()) {
        EmptyState(modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        items(notes) { note ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenNote(note.path) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
                Text(
                    text = note.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                note.snippet?.let { snippet ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No notes yet",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = "Retry")
            }
        }
    }
}

@Preview
@Composable
private fun NoteListLoadingPreview() {
    LinkLetAppTheme {
        Surface {
            NoteListScreen(
                state = NoteListUiState.Loading,
                query = "",
                onQueryChange = {},
                onClearQuery = {},
                onOpenNote = {},
                onRetry = {},
                onOpenSettings = {},
            )
        }
    }
}

@Preview
@Composable
private fun NoteListSuccessPreview() {
    LinkLetAppTheme {
        Surface {
            NoteListScreen(
                state = NoteListUiState.Success(
                    notes = listOf(
                        NoteListItemUiModel(
                            id = NoteId("notes/sample.org"),
                            title = "Sample Note",
                            path = "notes/sample.org",
                            snippet = "This is a sample snippet showing where the query matched…",
                        ),
                        NoteListItemUiModel(
                            id = NoteId("notes/ideas.org"),
                            title = "Ideas",
                            path = "notes/ideas.org",
                        ),
                    ),
                ),
                query = "sample",
                onQueryChange = {},
                onClearQuery = {},
                onOpenNote = {},
                onRetry = {},
                onOpenSettings = {},
            )
        }
    }
}

@Preview
@Composable
private fun NoteListErrorPreview() {
    LinkLetAppTheme {
        Surface {
            NoteListScreen(
                state = NoteListUiState.Error("Something went wrong"),
                query = "",
                onQueryChange = {},
                onClearQuery = {},
                onOpenNote = {},
                onRetry = {},
                onOpenSettings = {},
            )
        }
    }
}

@Composable
private fun NoteSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search notes",
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                    )
                }
            }
        },
        placeholder = { Text(text = "Search notes") },
    )
}
