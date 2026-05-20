package com.gladomat.linklet.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.data.model.IndexingProgress
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.ConflictInfo
import com.gladomat.linklet.viewmodel.NoteListItemUiModel
import com.gladomat.linklet.viewmodel.NoteListSnackbarAction
import com.gladomat.linklet.viewmodel.NoteSortOption
import com.gladomat.linklet.viewmodel.NoteListUiState
import com.gladomat.linklet.viewmodel.NoteListViewModel

@Composable
fun NoteListRoute(
    onOpenNote: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onCreateNote: () -> Unit,
    onOpenSyncStatus: () -> Unit,
    viewModel: NoteListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val currentSortOption by viewModel.currentSortOption.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val indexingProgressPass1 by viewModel.indexingProgressPass1.collectAsStateWithLifecycle()
    val indexingProgressPass2 by viewModel.indexingProgressPass2.collectAsStateWithLifecycle()
    val indexingFailuresPass2 by viewModel.indexingFailuresPass2.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarAction by viewModel.snackbarAction.collectAsStateWithLifecycle()
    
    NoteListScreen(
        state = state,
        query = query,
        currentSortOption = currentSortOption,
        isSyncing = isSyncing,
        syncProgress = syncProgress,
        indexingProgressPass1 = indexingProgressPass1,
        indexingProgressPass2 = indexingProgressPass2,
        indexingFailuresPass2 = indexingFailuresPass2,
        snackbarMessage = snackbarMessage,
        snackbarAction = snackbarAction,
        onQueryChange = viewModel::updateSearchQuery,
        onClearQuery = viewModel::clearSearchQuery,
        onOpenNote = onOpenNote,
        onRetry = viewModel::refresh,
        onRetryLinkIndexing = viewModel::retryLinkIndexing,
        onOpenSettings = onOpenSettings,
        onOpenTrash = onOpenTrash,
        onSortOptionSelected = viewModel::updateSortOption,
        onCreateNote = onCreateNote,
        onClearSnackbar = viewModel::clearSnackbar,
        onOpenSyncStatus = onOpenSyncStatus,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    state: NoteListUiState,
    query: String,
    currentSortOption: NoteSortOption,
    isSyncing: Boolean,
    syncProgress: Float,
    indexingProgressPass1: IndexingProgress,
    indexingProgressPass2: IndexingProgress,
    indexingFailuresPass2: Int,
    snackbarMessage: String?,
    snackbarAction: NoteListSnackbarAction?,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenNote: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryLinkIndexing: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onSortOptionSelected: (NoteSortOption) -> Unit,
    onCreateNote: () -> Unit,
    onClearSnackbar: () -> Unit,
    onOpenSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val moreMenuExpanded = androidx.compose.runtime.mutableStateOf(false)
    val snackbarHostState = remember { SnackbarHostState() }
    val pass1Total = indexingProgressPass1.total
    val pass1Completed = indexingProgressPass1.completed
    val isIndexingPass1 = pass1Total > 0 && pass1Completed < pass1Total
    val pass2Total = indexingProgressPass2.total
    val pass2Completed = indexingProgressPass2.completed
    val isIndexingPass2 = pass2Total > 0 && pass2Completed < pass2Total
    
    // Show snackbar when message changes
    LaunchedEffect(snackbarMessage, snackbarAction) {
        snackbarMessage?.let { message ->
            val result = if (snackbarAction == NoteListSnackbarAction.OPEN_SYNC_STATUS) {
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Review",
                )
            } else {
                snackbarHostState.showSnackbar(message)
            }
            if (result == SnackbarResult.ActionPerformed) {
                onOpenSyncStatus()
            }
            onClearSnackbar()
        }
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Org-roam Notes",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            ),
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { moreMenuExpanded.value = true },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More",
                            )
                        }
                        DropdownMenu(
                            expanded = moreMenuExpanded.value,
                            onDismissRequest = { moreMenuExpanded.value = false },
                        ) {
                            SortDropdownMenuItem(
                                label = "Sort: Date (Newest)",
                                selected = currentSortOption == NoteSortOption.DATE_DESC,
                                onClick = {
                                    moreMenuExpanded.value = false
                                    onSortOptionSelected(NoteSortOption.DATE_DESC)
                                },
                            )
                            SortDropdownMenuItem(
                                label = "Sort: Date (Oldest)",
                                selected = currentSortOption == NoteSortOption.DATE_ASC,
                                onClick = {
                                    moreMenuExpanded.value = false
                                    onSortOptionSelected(NoteSortOption.DATE_ASC)
                                },
                            )
                            SortDropdownMenuItem(
                                label = "Sort: Name (A-Z)",
                                selected = currentSortOption == NoteSortOption.NAME_ASC,
                                onClick = {
                                    moreMenuExpanded.value = false
                                    onSortOptionSelected(NoteSortOption.NAME_ASC)
                                },
                            )
                            SortDropdownMenuItem(
                                label = "Sort: Name (Z-A)",
                                selected = currentSortOption == NoteSortOption.NAME_DESC,
                                onClick = {
                                    moreMenuExpanded.value = false
                                    onSortOptionSelected(NoteSortOption.NAME_DESC)
                                },
                            )
                            SortDropdownMenuItem(
                                label = "Sort: Path (A-Z)",
                                selected = currentSortOption == NoteSortOption.PATH_ASC,
                                onClick = {
                                    moreMenuExpanded.value = false
                                    onSortOptionSelected(NoteSortOption.PATH_ASC)
                                },
                            )
                            SortDropdownMenuItem(
                                label = "Sort: Path (Z-A)",
                                selected = currentSortOption == NoteSortOption.PATH_DESC,
                                onClick = {
                                    moreMenuExpanded.value = false
                                    onSortOptionSelected(NoteSortOption.PATH_DESC)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Show Deleted Notes") },
                                onClick = {
                                    moreMenuExpanded.value = false
                                    onOpenTrash()
                                },
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Open settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                
                if (isIndexingPass1) {
                    val progress = (pass1Completed.toFloat() / pass1Total.toFloat()).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Pass 1: Indexed $pass1Completed / $pass1Total notes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (isIndexingPass2 || indexingFailuresPass2 > 0) {
                    val progress = if (pass2Total > 0) {
                        (pass2Completed.toFloat() / pass2Total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Pass 2: Links $pass2Completed / $pass2Total",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )

                    if (indexingFailuresPass2 > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Pass 2 failures: $indexingFailuresPass2",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(onClick = onRetryLinkIndexing) {
                                Text("Retry")
                            }
                        }
                    }
                }

                // Sync progress indicator
                if (isSyncing) {
                    if (syncProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { syncProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNote,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 12.dp,
                ),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Create new note",
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Sticky search bar with backdrop blur effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                NoteSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

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
private fun SortDropdownMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    // Note: Pull-to-refresh is not available in this Material3 version
    // Sync can still be triggered via scheduled worker and app-open triggers

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 0.dp,
            bottom = 96.dp, // Extra padding for FAB
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(notes, key = { it.id.path }) { note ->
            NoteCard(
                note = note,
                onClick = { onOpenNote(note.id.path) },
            )
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteListItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.99f else 1f,
        label = "card_scale",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 8.dp else 2.dp,
                shape = RoundedCornerShape(12.dp),
                clip = false,
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 24.dp), // Space for chevron
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Title
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Conflict info (if present)
                note.conflictInfo?.let { conflict ->
                    ConflictStatusRow(conflict)
                }

                // Filename
                Text(
                    text = note.filename,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.sp,
                    ),
                    color = if (note.conflictInfo != null) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )

                // Snippet (if present from search)
                note.snippet?.let { snippet ->
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Chevron indicator
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun ConflictStatusRow(conflict: ConflictInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Conflicted copy warning",
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Conflicted Copy ${conflict.timestamp}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.sp,
            ),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "No notes yet",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to create your first note",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = "Retry")
            }
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
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp)),
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search notes",
                tint = if (query.isNotEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = "Search notes",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        shape = RoundedCornerShape(16.dp),
    )
}

@Preview
@Composable
private fun NoteListLoadingPreview() {
    LinkLetAppTheme {
        Surface {
            NoteListScreen(
                state = NoteListUiState.Loading,
                query = "",
                currentSortOption = NoteSortOption.NAME_ASC,
                isSyncing = false,
                syncProgress = 0f,
                indexingProgressPass1 = IndexingProgress(completed = 0, total = 0),
                indexingProgressPass2 = IndexingProgress(completed = 0, total = 0),
                indexingFailuresPass2 = 0,
                snackbarMessage = null,
                snackbarAction = null,
                onQueryChange = {},
                onClearQuery = {},
                onOpenNote = {},
                onRetry = {},
                onRetryLinkIndexing = {},
                onOpenSettings = {},
                onOpenTrash = {},
                onSortOptionSelected = {},
                onCreateNote = {},
                onClearSnackbar = {},
                onOpenSyncStatus = {},
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
                            filename = "20220228152819-sample.org",
                            snippet = "This is a sample snippet showing where the query matched…",
                        ),
                        NoteListItemUiModel(
                            id = NoteId("notes/ideas.org"),
                            title = "Ideas",
                            filename = "20220228152934-ideas.org",
                            conflictInfo = ConflictInfo("2025-11-27 18-06"),
                        ),
                        NoteListItemUiModel(
                            id = NoteId("notes/oolong.org"),
                            title = "oolong tea brewing",
                            filename = "20220302100929-oolong_tea_brewing.org",
                        ),
                    ),
                ),
                query = "sample",
                currentSortOption = NoteSortOption.NAME_ASC,
                isSyncing = true,
                syncProgress = 0.5f,
                indexingProgressPass1 = IndexingProgress(completed = 120, total = 240),
                indexingProgressPass2 = IndexingProgress(completed = 20, total = 240),
                indexingFailuresPass2 = 2,
                snackbarMessage = null,
                snackbarAction = null,
                onQueryChange = {},
                onClearQuery = {},
                onOpenNote = {},
                onRetry = {},
                onRetryLinkIndexing = {},
                onOpenSettings = {},
                onOpenTrash = {},
                onSortOptionSelected = {},
                onCreateNote = {},
                onClearSnackbar = {},
                onOpenSyncStatus = {},
            )
        }
    }
}

@Preview
@Composable
private fun NoteListSuccessWithConflictsPreview() {
    LinkLetAppTheme(darkTheme = true) {
        Surface {
            NoteListScreen(
                state = NoteListUiState.Success(
                    notes = listOf(
                        NoteListItemUiModel(
                            id = NoteId("notes/meetstar.org"),
                            title = "meetstar[[id:96c0bee6-e226-469b...]]",
                            filename = "20220228152819-meetstar.org",
                            conflictInfo = ConflictInfo("2025-11-27 18-06"),
                        ),
                        NoteListItemUiModel(
                            id = NoteId("notes/meetstar2.org"),
                            title = "meetstar[[id:96c0bee6-e226...]]",
                            filename = "20220228152819-meetstar.org",
                        ),
                        NoteListItemUiModel(
                            id = NoteId("notes/action_sheets.org"),
                            title = "action_sheets",
                            filename = "20220228152934-action_sheets.org",
                            conflictInfo = ConflictInfo("2025-11-27 18-06"),
                        ),
                    ),
                ),
                query = "",
                currentSortOption = NoteSortOption.NAME_ASC,
                isSyncing = false,
                syncProgress = 0f,
                indexingProgressPass1 = IndexingProgress(completed = 0, total = 0),
                indexingProgressPass2 = IndexingProgress(completed = 0, total = 0),
                indexingFailuresPass2 = 0,
                snackbarMessage = null,
                snackbarAction = null,
                onQueryChange = {},
                onClearQuery = {},
                onOpenNote = {},
                onRetry = {},
                onRetryLinkIndexing = {},
                onOpenSettings = {},
                onOpenTrash = {},
                onSortOptionSelected = {},
                onCreateNote = {},
                onClearSnackbar = {},
                onOpenSyncStatus = {},
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
                currentSortOption = NoteSortOption.NAME_ASC,
                isSyncing = false,
                syncProgress = 0f,
                indexingProgressPass1 = IndexingProgress(completed = 0, total = 0),
                indexingProgressPass2 = IndexingProgress(completed = 0, total = 0),
                indexingFailuresPass2 = 0,
                snackbarMessage = null,
                snackbarAction = null,
                onQueryChange = {},
                onClearQuery = {},
                onOpenNote = {},
                onRetry = {},
                onRetryLinkIndexing = {},
                onOpenSettings = {},
                onOpenTrash = {},
                onSortOptionSelected = {},
                onCreateNote = {},
                onClearSnackbar = {},
                onOpenSyncStatus = {},
            )
        }
    }
}

@Preview
@Composable
private fun NoteListEmptyPreview() {
    LinkLetAppTheme {
        Surface {
            NoteListScreen(
                state = NoteListUiState.Success(notes = emptyList()),
                query = "",
                currentSortOption = NoteSortOption.NAME_ASC,
                isSyncing = false,
                syncProgress = 0f,
                indexingProgressPass1 = IndexingProgress(completed = 0, total = 0),
                indexingProgressPass2 = IndexingProgress(completed = 0, total = 0),
                indexingFailuresPass2 = 0,
                snackbarMessage = null,
                snackbarAction = null,
                onQueryChange = {},
                onClearQuery = {},
                onOpenNote = {},
                onRetry = {},
                onRetryLinkIndexing = {},
                onOpenSettings = {},
                onOpenTrash = {},
                onSortOptionSelected = {},
                onCreateNote = {},
                onClearSnackbar = {},
                onOpenSyncStatus = {},
            )
        }
    }
}
