package com.gladomat.linklet.ui.screens.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.viewmodel.trash.TrashEvent
import com.gladomat.linklet.viewmodel.trash.TrashViewModel
import kotlinx.coroutines.launch

@Composable
fun TrashRoute(
    onNavigateBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val notes by viewModel.trashNotes.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TrashEvent.Message -> coroutineScope.launch {
                    snackbarHostState.showSnackbar(event.text)
                }
            }
        }
    }
    TrashScreen(
        notes = notes,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        onRestoreAll = viewModel::restoreAll,
        onDeleteAllForever = viewModel::deleteAllForever,
        onRestore = { viewModel.restore(it) },
        onDeleteForever = { viewModel.deleteForever(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    notes: List<Note>,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onRestoreAll: () -> Unit,
    onDeleteAllForever: () -> Unit,
    onRestore: (Note) -> Unit,
    onDeleteForever: (Note) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    var deleteAllDialogVisible by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Restore all") },
                            enabled = notes.isNotEmpty(),
                            onClick = {
                                menuExpanded = false
                                onRestoreAll()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete all forever") },
                            enabled = notes.isNotEmpty(),
                            onClick = {
                                menuExpanded = false
                                deleteAllDialogVisible = true
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notes, key = { it.id.path }) { note ->
                    TrashNoteRow(
                        note = note,
                        onRestore = { onRestore(note) },
                        onDeleteForever = { pendingDelete = note },
                    )
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete forever?") },
            text = { Text("This permanently removes the file and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteForever(toDelete)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (deleteAllDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteAllDialogVisible = false },
            title = { Text("Delete all forever?") },
            text = { Text("This permanently removes all trashed notes and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAllDialogVisible = false
                        onDeleteAllForever()
                    },
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAllDialogVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashNoteRow(
    note: Note,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    androidx.compose.material3.ListItem(
        headlineContent = {
            Text(
                text = note.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = note.id.path.substringAfterLast('/'),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onRestore) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = "Restore",
                    )
                }
                IconButton(onClick = onDeleteForever) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = "Delete forever",
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
