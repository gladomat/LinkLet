package com.gladomat.linklet.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.data.sync.SyncIgnoreRules
import com.gladomat.linklet.data.sync.SyncPathFilter
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.settings.SyncIgnoreEditorUiState
import com.gladomat.linklet.viewmodel.settings.SyncIgnoreEditorViewModel
import com.gladomat.linklet.viewmodel.settings.SyncIgnoreImpactPreview

@Composable
fun SyncIgnoreEditorRoute(
    onNavigateBack: () -> Unit,
    viewModel: SyncIgnoreEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val handleBack: () -> Unit = {
        if (state.isDirty) showDiscardDialog = true else onNavigateBack()
    }

    SyncIgnoreEditorScreen(
        state = state,
        onTextChange = viewModel::onTextChange,
        onSaveRequested = viewModel::requestSave,
        onConfirmSave = viewModel::confirmSave,
        onDismissPreview = viewModel::dismissImpactPreview,
        onRevert = viewModel::revert,
        onBack = handleBack,
        onCopyToClipboard = { clipboardManager.setText(AnnotatedString(state.text.text)) },
        snackbarHostState = snackbarHostState,
    )

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDismiss = { showDiscardDialog = false },
            onDiscard = {
                showDiscardDialog = false
                onNavigateBack()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncIgnoreEditorScreen(
    state: SyncIgnoreEditorUiState,
    onTextChange: (TextFieldValue) -> Unit,
    onSaveRequested: () -> Unit,
    onConfirmSave: () -> Unit,
    onDismissPreview: () -> Unit,
    onRevert: () -> Unit,
    onBack: () -> Unit,
    onCopyToClipboard: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.isDirty, onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Sync exclusions (.syncignore)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.loadError != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = state.loadError, style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "These rules apply only to this device. Copy this file's contents " +
                    "manually to any other device running LinkLet on this vault.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BuiltInExclusionsCard()

            if (state.droppedLines.isNotEmpty()) {
                DroppedLinesBanner(state.droppedLines)
            }

            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().height(320.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                singleLine = false,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = onCopyToClipboard, modifier = Modifier.weight(1f)) {
                    Text("Copy to clipboard")
                }
                OutlinedButton(
                    onClick = onRevert,
                    enabled = state.isDirty,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Revert")
                }
            }

            Button(
                onClick = onSaveRequested,
                enabled = !state.isSaving && !state.isComputingPreview,
                modifier = Modifier.fillMaxWidth().testTag("syncIgnoreSaveButton"),
            ) {
                if (state.isComputingPreview) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        state.isComputingPreview -> "Reviewing changes…"
                        state.isSaving -> "Saving…"
                        else -> "Save"
                    },
                )
            }
        }
    }

    if (state.impactPreview != null) {
        ImpactPreviewDialog(
            preview = state.impactPreview,
            isSaving = state.isSaving,
            onConfirm = onConfirmSave,
            onDismiss = onDismissPreview,
        )
    }
}

@Composable
private fun BuiltInExclusionsCard() {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "Also always excluded", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Regardless of the rules below, LinkLet never syncs: dot- or " +
                        "underscore-prefixed path segments, " +
                        SyncPathFilter.blockedFileNames.joinToString(", ") +
                        ", and directories named " +
                        SyncPathFilter.blockedDirectoryNames.joinToString(", ") +
                        ". A rule that only re-states one of these has no effect.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DroppedLinesBanner(droppedLines: List<SyncIgnoreRules.DroppedLine>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "${droppedLines.size} line(s) had no effect",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            droppedLines.forEach { dropped ->
                Text(
                    text = "Line ${dropped.lineNumber}: ${dropped.rawText}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ImpactPreviewDialog(
    preview: SyncIgnoreImpactPreview,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        // Deliberately not `onDismiss`: tapping outside the dialog or pressing system back used
        // to silently discard the pending save with no distinct feedback from "I chose Save" -
        // easy to do by reflex (e.g. pressing back right after tapping Save, before registering
        // this dialog appeared), which reads exactly like "I saved" until you leave the screen
        // and the discard-changes prompt reveals nothing was ever written. Require one of the
        // two explicit buttons below; a no-op keeps the dialog open otherwise.
        onDismissRequest = {},
        title = { Text(if (preview.isCatastrophic) "This looks like a big change" else "Review changes") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (preview.isCatastrophic) {
                    Text(
                        text = "This will stop syncing nearly your entire vault (or an unusually " +
                            "large share of it). Are you sure?",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text("${preview.newlyExcluded.size} file(s) will stop syncing.")
                if (preview.newlyExcluded.isNotEmpty()) {
                    Text(
                        text = "Already-downloaded copies of these files stay on this device — " +
                            "this only stops future sync, it does not delete anything.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    PathSample(preview.newlyExcluded)
                }
                Text("${preview.newlyIncluded.size} file(s) will start syncing.")
                if (preview.newlyIncluded.isNotEmpty()) {
                    PathSample(preview.newlyIncluded)
                }
                if (preview.droppedLines.isNotEmpty()) {
                    Text(
                        text = "${preview.droppedLines.size} line(s) in your edit have no effect.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier.testTag("syncIgnoreConfirmSaveButton"),
            ) {
                Text(if (preview.isCatastrophic) "Save anyway" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep editing")
            }
        },
    )
}

@Composable
private fun PathSample(paths: List<String>, limit: Int = 10) {
    Column {
        paths.take(limit).forEach { path ->
            Text(text = "  $path", style = MaterialTheme.typography.bodySmall)
        }
        if (paths.size > limit) {
            Text(
                text = "  …and ${paths.size - limit} more",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DiscardChangesDialog(
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard changes?") },
        text = { Text("You have unsaved changes to .syncignore.") },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text("Discard") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep editing") }
        },
    )
}
