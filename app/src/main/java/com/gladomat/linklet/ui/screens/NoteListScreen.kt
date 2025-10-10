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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    NoteListScreen(
        state = state,
        onOpenNote = onOpenNote,
        onRetry = viewModel::refresh,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    state: NoteListUiState,
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
        when (state) {
            NoteListUiState.Loading -> LoadingState(modifier.padding(padding))
            is NoteListUiState.Success -> SuccessState(
                notes = state.notes,
                onOpenNote = onOpenNote,
                modifier = modifier.padding(padding),
            )
            is NoteListUiState.Error -> ErrorState(
                message = state.message,
                onRetry = onRetry,
                modifier = modifier.padding(padding),
            )
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
                        NoteListItemUiModel(id = com.gladomat.linklet.data.model.NoteId("notes/sample.org"), title = "Sample Note", path = "notes/sample.org"),
                        NoteListItemUiModel(id = com.gladomat.linklet.data.model.NoteId("notes/ideas.org"), title = "Ideas", path = "notes/ideas.org"),
                    ),
                ),
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
                onOpenNote = {},
                onRetry = {},
                onOpenSettings = {},
            )
        }
    }
}
