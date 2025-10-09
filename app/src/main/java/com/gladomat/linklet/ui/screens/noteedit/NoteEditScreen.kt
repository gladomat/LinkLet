package com.gladomat.linklet.ui.screens.noteedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.noteedit.NoteEditUiState
import com.gladomat.linklet.viewmodel.noteedit.NoteEditViewModel

@Composable
fun NoteEditRoute(
    onDone: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: NoteEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    when (val current = state) {
        is NoteEditUiState.Saved -> LaunchedEffect(current) {
            onDone(current.path)
        }

        is NoteEditUiState.Error -> LaunchedEffect(current) {
            snackbarHostState.showSnackbar(current.message)
        }

        else -> Unit
    }

    Surface {
        NoteEditScreen(
            state = state,
            onContentChange = viewModel::updateContent,
            onSave = viewModel::save,
            onBack = onNavigateBack,
            onApplyHeading = { level ->
                viewModel.insertHeading(level)
            },
            onApplyBold = {
                viewModel.insertBold()
            },
            onApplyItalic = {
                viewModel.insertItalic()
            },
            onApplySrc = {
                viewModel.insertSrcBlock()
            },
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NoteEditScreen(
    state: NoteEditUiState,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onApplyHeading: (Int) -> Unit,
    onApplyBold: () -> Unit,
    onApplyItalic: () -> Unit,
    onApplySrc: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = "Edit Note", style = MaterialTheme.typography.titleLarge) },
        )
        SnackbarHost(hostState = snackbarHostState)
        when (state) {
            NoteEditUiState.Loading -> LoadingState()
            is NoteEditUiState.Error -> ErrorState(state.message, onBack)
            is NoteEditUiState.Saved -> Unit
            is NoteEditUiState.Editing -> EditingState(
                uiState = state,
                onContentChange = onContentChange,
                onSave = onSave,
                onApplyHeading = onApplyHeading,
                onApplyBold = onApplyBold,
                onApplyItalic = onApplyItalic,
                onApplySrc = onApplySrc,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Go back")
        }
    }
}

@Composable
private fun EditingState(
    uiState: NoteEditUiState.Editing,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onApplyHeading: (Int) -> Unit,
    onApplyBold: () -> Unit,
    onApplyItalic: () -> Unit,
    onApplySrc: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        FormattingToolbar(
            onApplyHeading = onApplyHeading,
            onApplyBold = onApplyBold,
            onApplyItalic = onApplyItalic,
            onApplySrc = onApplySrc,
        )
        Spacer(modifier = Modifier.height(4.dp))
        val scrollState = rememberScrollState()
        OutlinedTextField(
            value = uiState.content,
            onValueChange = onContentChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = false,
            minLines = 8,
        )
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = onSave,
            enabled = !uiState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(text = "Save")
        }
    }
}

@Composable
private fun FormattingToolbar(
    onApplyHeading: (Int) -> Unit,
    onApplyBold: () -> Unit,
    onApplyItalic: () -> Unit,
    onApplySrc: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { onApplyHeading(1) }) { Text("H1") }
        OutlinedButton(onClick = { onApplyHeading(2) }) { Text("H2") }
        OutlinedButton(onClick = { onApplyHeading(3) }) { Text("H3") }
        OutlinedButton(onClick = onApplyBold) { Text("*bold*") }
        OutlinedButton(onClick = onApplyItalic) { Text("/italic/") }
        OutlinedButton(onClick = onApplySrc) { Text("src") }
    }
}

@Preview
@Composable
private fun NoteEditScreenPreview() {
    LinkLetAppTheme {
        NoteEditScreen(
            state = NoteEditUiState.Editing(content = "Sample"),
            onContentChange = {},
            onSave = {},
            onBack = {},
            onApplyHeading = {},
            onApplyBold = {},
            onApplyItalic = {},
            onApplySrc = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}
