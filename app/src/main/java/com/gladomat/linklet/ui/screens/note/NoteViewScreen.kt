package com.gladomat.linklet.ui.screens.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.ui.components.BacklinkList
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.note.NoteViewUiState
import com.gladomat.linklet.viewmodel.note.NoteViewViewModel

@Composable
fun NoteViewRoute(
    onOpenLink: (String) -> Unit,
    onEditNote: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteViewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoteViewScreen(
        state = state,
        onOpenLink = onOpenLink,
        onEditNote = onEditNote,
        onRetry = viewModel::loadNote,
        modifier = modifier,
    )
}

@Composable
fun NoteViewScreen(
    state: NoteViewUiState,
    onOpenLink: (String) -> Unit,
    onEditNote: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        NoteViewUiState.Loading -> LoadingState(modifier)
        is NoteViewUiState.Success -> SuccessState(
            note = state.note,
            backlinks = state.backlinks,
            onOpenLink = onOpenLink,
            onEditNote = onEditNote,
            modifier = modifier,
        )
        is NoteViewUiState.Error -> ErrorState(
            message = state.message,
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Loading...", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SuccessState(
    note: Note,
    backlinks: List<LinkEntityDto>,
    onOpenLink: (String) -> Unit,
    onEditNote: (String) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = note.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Button(
            onClick = { onEditNote(note.id.path) },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Edit")
        }
        Text(
            text = note.id.path,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Text(
            text = note.content,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (note.links.isNotEmpty()) {
            Text(
                text = "Links",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            note.links.forEach { link ->
                Text(
                    text = link.label ?: link.toPath,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onOpenLink(link.toPath) },
                )
            }
        }
        BacklinkList(
            backlinks = backlinks,
            onOpenNote = onOpenLink,
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(text = "Retry")
        }
    }
}

@Preview
@Composable
private fun NoteViewSuccessPreview() {
    LinkLetAppTheme {
        Surface {
            NoteViewScreen(
                state = NoteViewUiState.Success(
                    note = Note(
                        id = NoteId("notes/sample.org"),
                        title = "Sample Note",
                        content = "#+title: Sample\nContent body.",
                        links = listOf(
                            NoteLink(NoteId("notes/sample.org"), "other.org", "Alias"),
                        ),
                    ),
                    backlinks = listOf(
                        LinkEntityDto(
                            source = "notes/linked.org",
                            target = "notes/sample.org",
                            alias = "Linked Note",
                        ),
                    ),
                ),
                onOpenLink = {},
                onEditNote = {},
                onRetry = {},
            )
        }
    }
}
