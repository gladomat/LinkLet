package com.gladomat.linklet.ui.screens.note

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.parser.org.OrgBlock
import com.gladomat.linklet.data.parser.org.OrgSection
import com.gladomat.linklet.data.parser.org.parseOrgDocument
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.domain.service.MatchRange
import com.gladomat.linklet.domain.service.SearchOptions
import com.gladomat.linklet.ui.REFRESH_NOTE_KEY
import com.gladomat.linklet.ui.components.OrgBlockView
import com.gladomat.linklet.ui.components.OrgTextPalette
import com.gladomat.linklet.ui.components.applyHighlights
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.note.NoteViewUiState
import com.gladomat.linklet.viewmodel.note.NoteViewViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NoteViewRoute(
    onEditNote: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onExitToList: () -> Unit,
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteViewViewModel = hiltViewModel(),
    savedStateHandle: SavedStateHandle? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var allTags by remember { mutableStateOf(emptyList<String>()) }

    // Collect all tags for autocomplete
    LaunchedEffect(Unit) {
        allTags = viewModel.getAllTags()
    }

    // Observe refresh flag from navigation result (set by NoteEditRoute after save)
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow(REFRESH_NOTE_KEY, false)?.collect { shouldRefresh ->
            if (shouldRefresh) {
                android.util.Log.d("NoteViewRoute", "Refresh flag detected, reloading note")
                viewModel.loadNote()
                savedStateHandle[REFRESH_NOTE_KEY] = false
            }
        }
    }
    val handleNavigateHome = remember(viewModel, onNavigateHome) {
        {
            viewModel.resetHistory()
            onNavigateHome()
        }
    }
    val handleExit = remember(viewModel, onExitToList) {
        {
            viewModel.resetHistory()
            onExitToList()
        }
    }
    val handleBack = remember(viewModel, handleExit) {
        {
            if (!viewModel.handleBackPress()) {
                handleExit()
            }
        }
    }
    val handleShare = remember(context, state) {
        {
            val currentState = state
            if (currentState is NoteViewUiState.Success) {
                shareNoteContent(
                    context = context,
                    title = currentState.note.title,
                    filename = currentState.note.id.path.substringAfterLast('/'),
                    content = currentState.note.content,
                )
            }
        }
    }
    val clipboardManager = remember {
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }
    val coroutineScope = rememberCoroutineScope()
    val handleCopyToClipboard = remember(state, clipboardManager, coroutineScope) {
        {
            val currentState = state
            if (currentState is NoteViewUiState.Success) {
                val clipLabel = currentState.note.title
                val clipText = currentState.note.content
                val clip = android.content.ClipData.newPlainText(clipLabel, clipText)
                clipboardManager.setPrimaryClip(clip)
                android.widget.Toast.makeText(
                    context,
                    "Copied to clipboard (clears in 60s)",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                coroutineScope.launch {
                    delay(60_000)
                    val currentClip = clipboardManager.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                    if (currentClip == clipText) {
                        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                    }
                }
            }
        }
    }
    BackHandler {
        handleBack()
    }
    NoteViewScreen(
        state = state,
        searchState = searchState,
        onOpenLink = viewModel::openNote,
        onOpenExternalLink = { uri -> uriHandler.openUri(uri) },
        onEditNote = onEditNote,
        onNavigateHome = handleNavigateHome,
        onBack = handleBack,
        onShare = handleShare,
        onFavorite = viewModel::toggleFavorite,
        onCreateNote = onCreateNote,
        onRetry = viewModel::loadNote,
        onDelete = { viewModel.deleteNote { handleExit() } },
        onDuplicate = viewModel::duplicateNote,
        onRename = viewModel::renameNote,
        onOpenSearch = viewModel::openSearch,
        onCloseSearch = viewModel::closeSearch,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onClearSearch = viewModel::clearSearch,
        onSearchOptionsChange = viewModel::setSearchOptions,
        onPrevMatch = viewModel::selectPrevMatch,
        onNextMatch = viewModel::selectNextMatch,

        onCopyToClipboard = handleCopyToClipboard,
        onCopyIdLink = {
            val note = (state as? NoteViewUiState.Success)?.note
            val orgId = note?.orgId?.takeUnless { it.isBlank() }
            if (note != null && orgId != null) {
                val label = note.title.takeUnless { it.contains('[') || it.contains(']') }
                val link = if (label != null) {
                    "[[id:$orgId][$label]]"
                } else {
                    "[[id:$orgId]]"
                }
                val clip = android.content.ClipData.newPlainText("ID Link", link)
                clipboardManager.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Copied ID link", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "No ID found", android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        onCopyFileLink = {
            val note = (state as? NoteViewUiState.Success)?.note
            val relativePath = note?.id?.path?.takeUnless { it.isBlank() }
            if (note != null && relativePath != null) {
                val label = note.title.takeUnless { it.contains('[') || it.contains(']') }
                val link = if (label != null) {
                    "[[file:$relativePath][$label]]"
                } else {
                    "[[file:$relativePath]]"
                }
                val clip = android.content.ClipData.newPlainText("File Link", link)
                clipboardManager.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Copied file link", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "No file path found", android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        onUpdateProperties = viewModel::updateProperties,
        onUpdateTags = viewModel::updateTags,
        allTags = allTags,
        modifier = modifier,
    )
}

@Composable
fun NoteViewScreen(
    state: NoteViewUiState,
    searchState: NoteViewViewModel.NoteSearchState,
    onOpenLink: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onEditNote: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onCreateNote: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchOptionsChange: (SearchOptions) -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onCopyIdLink: () -> Unit,
    onCopyFileLink: () -> Unit,
    onUpdateProperties: (Map<String, String>) -> Unit,
    onUpdateTags: (List<String>) -> Unit,
    allTags: List<String>,
    modifier: Modifier = Modifier,
) {
    when (state) {
        NoteViewUiState.Loading -> LoadingState(modifier)
        is NoteViewUiState.Success -> SuccessState(
            note = state.note,
            backlinks = state.backlinks,
            lastModified = state.lastModified,
            isFavorite = state.isFavorite,
            searchState = searchState,
            onOpenLink = onOpenLink,
            onOpenExternalLink = onOpenExternalLink,
            onEditNote = onEditNote,
            onNavigateHome = onNavigateHome,
            onBack = onBack,
            onShare = onShare,
            onFavorite = onFavorite,
            onCreateNote = onCreateNote,
            onDelete = onDelete,
            onDuplicate = onDuplicate,
            onRename = onRename,
            onOpenSearch = onOpenSearch,
            onCloseSearch = onCloseSearch,
            onSearchQueryChange = onSearchQueryChange,
            onClearSearch = onClearSearch,
            onSearchOptionsChange = onSearchOptionsChange,
            onPrevMatch = onPrevMatch,
            onNextMatch = onNextMatch,
            onCopyToClipboard = onCopyToClipboard,
            onCopyIdLink = onCopyIdLink,
            onCopyFileLink = onCopyFileLink,
            onUpdateProperties = onUpdateProperties,
            onUpdateTags = onUpdateTags,
            allTags = allTags,
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
    lastModified: String?,
    isFavorite: Boolean,
    searchState: NoteViewViewModel.NoteSearchState,
    onOpenLink: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onEditNote: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onCreateNote: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchOptionsChange: (SearchOptions) -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onCopyIdLink: () -> Unit,
    onCopyFileLink: () -> Unit,
    onUpdateProperties: (Map<String, String>) -> Unit,
    onUpdateTags: (List<String>) -> Unit,
    allTags: List<String>,
    modifier: Modifier,
) {
    val logTag = "NoteSearch"
    val document = remember(note.content) { parseOrgDocument(note.content) }
    var propertiesExpanded by remember(note.id.path) { mutableStateOf(false) }
    var showBacklinksSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(searchState.isActive) {
        if (searchState.isActive) {
            Log.d(logTag, "Search activated for ${note.id.path}")
        } else {
            Log.d(logTag, "Search deactivated for ${note.id.path}")
        }
    }
    val sectionExpansion = remember(document.sections) {
        mutableStateMapOf<String, Boolean>().apply {
            document.sections.forEach { collectIds(it, this) }
        }
    }

    // Backlinks bottom sheet
    if (showBacklinksSheet) {
        BacklinksBottomSheet(
            backlinks = backlinks,
            onBacklinkClick = { path ->
                onOpenLink(path)
            },
            onDismiss = { showBacklinksSheet = false },
        )
    }

    // More actions bottom sheet
    if (showMoreSheet) {
        MoreActionsBottomSheet(
            onDismiss = { showMoreSheet = false },
            onDuplicate = onDuplicate,
            onRename = { showRenameDialog = true },
            onExport = { showExportSheet = true },
            onDelete = { showDeleteDialog = true },
            onProperties = { showPropertiesDialog = true },
            onTags = { showTagsDialog = true },
            onExpandAll = {
                sectionExpansion.keys.forEach { key -> sectionExpansion[key] = true }
            },
            onCollapseAll = {
                sectionExpansion.keys.forEach { key -> sectionExpansion[key] = false }
            },
            onCopyIdLink = onCopyIdLink,
            onCopyFileLink = onCopyFileLink,
        )
    }

    // Properties editor dialog
    if (showPropertiesDialog) {
        PropertiesEditorDialog(
            properties = note.properties,
            onSave = { newProperties ->
                showPropertiesDialog = false
                onUpdateProperties(newProperties)
            },
            onDismiss = { showPropertiesDialog = false },
        )
    }

    // Tag picker dialog
    if (showTagsDialog) {
        TagPickerDialog(
            currentTags = note.fileTags,
            allAvailableTags = allTags,
            onSave = { newTags ->
                showTagsDialog = false
                onUpdateTags(newTags)
            },
            onDismiss = { showTagsDialog = false },
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        DeleteNoteDialog(
            filename = note.id.path.substringAfterLast('/'),
            backlinksCount = backlinks.size,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    // Rename dialog
    if (showRenameDialog) {
        RenameNoteDialog(
            currentFilename = note.id.path.substringAfterLast('/'),
            onConfirm = { newFilename ->
                showRenameDialog = false
                onRename(newFilename)
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    // Export bottom sheet
    if (showExportSheet) {
        ExportBottomSheet(
            onDismiss = { showExportSheet = false },
            onShare = onShare,
            onCopyToClipboard = { onCopyToClipboard() },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            NoteHeader(
                filename = note.id.path.substringAfterLast('/'),
                lastModified = lastModified,
                searchQuery = searchState.query,
                onSearchQueryChange = onSearchQueryChange,
                searchActive = searchState.isActive,
                searchOptions = searchState.options,
                regexError = if (searchState.isActive) searchState.regexError else null,
                activeMatchNumber = if (searchState.isActive) searchState.activeMatchNumber else 0,
                totalMatches = if (searchState.isActive) searchState.totalMatches else 0,
                onToggleCaseSensitive = {
                    onSearchOptionsChange(searchState.options.copy(caseSensitive = !searchState.options.caseSensitive))
                },
                onToggleWholeWord = {
                    onSearchOptionsChange(searchState.options.copy(wholeWord = !searchState.options.wholeWord))
                },
                onToggleRegex = {
                    onSearchOptionsChange(searchState.options.copy(useRegex = !searchState.options.useRegex))
                },
                onPrevMatch = onPrevMatch,
                onNextMatch = onNextMatch,
                onClearSearch = onClearSearch,
                onCloseSearch = onCloseSearch,
                searchFocusRequester = searchFocusRequester,
                onBack = onBack,
                onHome = onNavigateHome,
                onOpenSearch = onOpenSearch,
                onEdit = { onEditNote(note.id.path) },
                modifier = Modifier.onSizeChanged { size -> topBarHeightPx = size.height },
            )
        },
        bottomBar = {
            NoteFooterBar(
                isFavorite = isFavorite,
                backlinksCount = backlinks.size,
                onShare = onShare,
                onFavorite = onFavorite,
                onBacklinks = { showBacklinksSheet = true },
                onMore = { showMoreSheet = true },
            )
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
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Create new note",
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { paddingValues ->
        val colorScheme = MaterialTheme.colorScheme
        val palette = remember(
            colorScheme.primary,
            colorScheme.secondary,
            colorScheme.tertiary,
            colorScheme.onSurfaceVariant,
            colorScheme.surfaceVariant,
            colorScheme.onSurface,
            colorScheme.secondaryContainer,
            colorScheme.tertiaryContainer,
        ) {
            OrgTextPalette(
                headingLevel1 = colorScheme.primary,
                headingLevel2 = colorScheme.secondary,
                headingLevel3 = colorScheme.tertiary,
                bulletColor = colorScheme.onSurfaceVariant,
                linkColor = colorScheme.secondary,
                codeBackground = colorScheme.surfaceVariant,
                codeTextColor = colorScheme.onSurface,
                verbatimBackground = colorScheme.surfaceVariant,
                verbatimTextColor = colorScheme.onSurface,
                searchHighlightBackground = colorScheme.secondaryContainer.copy(alpha = 0.6f),
                activeSearchHighlightBackground = colorScheme.tertiaryContainer.copy(alpha = 0.7f),
            )
        }

        LaunchedEffect(searchState.isActive) {
            if (searchState.isActive) {
                sectionExpansion.keys.forEach { key -> sectionExpansion[key] = true }
            }
        }

        val matchList = if (searchState.isActive) searchState.matches else emptyList()
        val matchListByBlockId = remember(matchList) { matchList.groupBy { it.blockId } }
        val renderContext = remember(
            palette,
            note.links,
            onOpenLink,
            onOpenExternalLink,
            searchState.isActive,
            matchList,
            matchListByBlockId,
            searchState.activeMatchIndex,
            searchState.options,
            searchState.query,
        ) {
            com.gladomat.linklet.ui.components.RenderContext(
                palette = palette,
                links = note.links,
                onOpenLink = onOpenLink,
                onOpenExternalLink = onOpenExternalLink,
                searchQuery = searchState.query.takeIf { searchState.isActive && it.isNotBlank() },
                matchList = matchList,
                activeMatchIndex = if (searchState.isActive) searchState.activeMatchIndex else -1,
                searchOptions = searchState.options,
                matchListByBlockId = matchListByBlockId,
            )
        }

        val contentItems = buildNoteBodyItems(document, sectionExpansion)
        val headerItemCount = 1 + if (document.properties.isNotEmpty()) 1 else 0
        val blockIdToIndex = buildBlockIdToIndexMap(contentItems, headerItemCount)
        val listState = rememberLazyListState()
        val density = LocalDensity.current

        LaunchedEffect(searchState.isActive, searchState.activeMatch?.blockId, blockIdToIndex, topBarHeightPx) {
            val activeBlockId = searchState.activeMatch?.blockId ?: return@LaunchedEffect
            if (!searchState.isActive) return@LaunchedEffect
            val targetIndex = blockIdToIndex[activeBlockId] ?: return@LaunchedEffect
            val fallbackPx = with(density) { 88.dp.roundToPx() }
            val chromePx = (if (topBarHeightPx > 0) topBarHeightPx else fallbackPx) + with(density) { 12.dp.roundToPx() }
            listState.animateScrollToItem(targetIndex, scrollOffset = -chromePx)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            if (document.properties.isNotEmpty()) {
                item(key = "properties") {
                    PropertiesSection(
                        properties = document.properties,
                        expanded = propertiesExpanded,
                        onToggle = { propertiesExpanded = !propertiesExpanded },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item(key = "title-card") {
                NoteHeaderCard(
                    title = note.title,
                    path = note.id.path,
                    fileTags = document.fileTags,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(
                items = contentItems,
                key = { it.key },
            ) { item ->
                when (item) {
                    is NoteBodyItem.SpacerItem -> Spacer(modifier = Modifier.height(item.height))
                    is NoteBodyItem.SectionHeaderItem -> SectionHeaderRow(
                        section = item.section,
                        expandedState = sectionExpansion,
                        palette = palette,
                        indent = item.indent,
                        searchActive = searchState.isActive,
                        matchListByBlockId = matchListByBlockId,
                        activeMatch = if (searchState.isActive) searchState.activeMatch else null,
                    )
                    is NoteBodyItem.SectionTagsItem -> TagRow(
                        tags = item.tags,
                        modifier = Modifier.padding(start = item.indent, bottom = 8.dp),
                    )
                    is NoteBodyItem.BlockItem -> {
                        Column(modifier = Modifier.padding(start = item.indent)) {
                            OrgBlockView(
                                block = item.block,
                                blockId = item.blockId,
                                context = renderContext,
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface NoteBodyItem {
    val key: String

    data class SectionHeaderItem(
        override val key: String,
        val section: OrgSection,
        val indent: androidx.compose.ui.unit.Dp,
    ) : NoteBodyItem

    data class SectionTagsItem(
        override val key: String,
        val tags: List<String>,
        val indent: androidx.compose.ui.unit.Dp,
    ) : NoteBodyItem

    data class BlockItem(
        override val key: String,
        val blockId: String,
        val block: OrgBlock,
        val indent: androidx.compose.ui.unit.Dp,
    ) : NoteBodyItem

    data class SpacerItem(
        override val key: String,
        val height: androidx.compose.ui.unit.Dp,
    ) : NoteBodyItem
}

private fun buildNoteBodyItems(
    document: com.gladomat.linklet.data.parser.org.OrgDocument,
    expandedState: Map<String, Boolean>,
): List<NoteBodyItem> {
    val items = mutableListOf<NoteBodyItem>()
    if (document.prefaceBlocks.isNotEmpty()) {
        document.prefaceBlocks.forEachIndexed { index, block ->
            items += NoteBodyItem.BlockItem(
                key = "preface/block-$index",
                blockId = "preface/block-$index",
                block = block,
                indent = 0.dp,
            )
            items += NoteBodyItem.SpacerItem(key = "preface/block-$index/spacer", height = 8.dp)
        }
    }

    fun visit(section: OrgSection) {
        val indentLevel = (section.level - 1).coerceIn(0, 5)
        val headerIndent = (indentLevel * 12).dp
        val blocksIndent = headerIndent + 24.dp
        items += NoteBodyItem.SectionHeaderItem(
            key = "section/${section.id}/header",
            section = section,
            indent = headerIndent,
        )
        if (section.tags.isNotEmpty()) {
            items += NoteBodyItem.SectionTagsItem(
                key = "section/${section.id}/tags",
                tags = section.tags,
                indent = headerIndent + 24.dp,
            )
        }
        if (expandedState[section.id] != false) {
            section.blocks.forEachIndexed { blockIndex, block ->
                items += NoteBodyItem.BlockItem(
                    key = "section/${section.id}/block-$blockIndex",
                    blockId = "section/${section.id}/block-$blockIndex",
                    block = block,
                    indent = blocksIndent,
                )
                items += NoteBodyItem.SpacerItem(
                    key = "section/${section.id}/block-$blockIndex/spacer",
                    height = 8.dp,
                )
            }
            section.children.forEach { child -> visit(child) }
        }
    }

    document.sections.forEach { visit(it) }
    return items
}

private fun buildBlockIdToIndexMap(
    bodyItems: List<NoteBodyItem>,
    headerItemCount: Int,
): Map<String, Int> {
    val map = mutableMapOf<String, Int>()
    bodyItems.forEachIndexed { index, item ->
        val lazyIndex = headerItemCount + index
        when (item) {
            is NoteBodyItem.SectionHeaderItem -> {
                map["section/${item.section.id}/header"] = lazyIndex
            }
            is NoteBodyItem.BlockItem -> {
                when (val block = item.block) {
                    is OrgBlock.Table -> {
                        block.rows.forEachIndexed { rowIndex, row ->
                            row.forEachIndexed { colIndex, _ ->
                                map["${item.blockId}/cell-$rowIndex-$colIndex"] = lazyIndex
                            }
                        }
                    }
                    else -> Unit
                }
                map[item.blockId] = lazyIndex
            }
            else -> Unit
        }
    }
    return map
}

@Composable
private fun SectionHeaderRow(
    section: OrgSection,
    expandedState: MutableMap<String, Boolean>,
    palette: OrgTextPalette,
    indent: androidx.compose.ui.unit.Dp,
    searchActive: Boolean,
    matchListByBlockId: Map<String, List<MatchRange>>,
    activeMatch: MatchRange?,
) {
    val isExpanded = expandedState[section.id] ?: true
    val blockId = "section/${section.id}/header"
    val ranges = if (searchActive) {
        matchListByBlockId[blockId]
            .orEmpty()
            .mapNotNull { match ->
                val endInclusive = match.end - 1
                if (match.start < 0 || endInclusive < match.start) null else match.start..endInclusive
            }
    } else {
        emptyList()
    }
    val activeRange = if (searchActive && activeMatch?.blockId == blockId) {
        val endInclusive = activeMatch.end - 1
        if (activeMatch.start < 0 || endInclusive < activeMatch.start) null else activeMatch.start..endInclusive
    } else {
        null
    }
    val highlightedTitle = remember(section.title, ranges, activeRange, palette) {
        applyHighlights(
            base = androidx.compose.ui.text.AnnotatedString(section.title),
            ranges = ranges,
            activeRange = activeRange,
            matchBackground = palette.searchHighlightBackground,
            activeBackground = palette.activeSearchHighlightBackground,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clickable { expandedState[section.id] = !isExpanded }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isExpanded) "▾" else "▸",
            style = MaterialTheme.typography.titleMedium,
            color = palette.colorForLevel(section.level),
        )
        Text(
            text = highlightedTitle,
            style = MaterialTheme.typography.titleMedium,
            color = palette.colorForLevel(section.level),
            modifier = Modifier.padding(start = 8.dp),
            fontWeight = FontWeight.SemiBold,
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

@Composable
private fun PropertiesSection(
    properties: Map<String, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Properties",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    properties.forEach { (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = "$key:",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteHeaderCard(
    title: String,
    path: String,
    fileTags: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (fileTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                TagRow(tags = fileTags)
            }
        }
    }
}

private fun collectIds(section: OrgSection, state: MutableMap<String, Boolean>) {
    state[section.id] = true
    section.children.forEach { collectIds(it, state) }
}

private fun OrgTextPalette.colorForLevel(level: Int): Color =
    when (level) {
        1 -> headingLevel1
        2 -> headingLevel2
        else -> headingLevel3
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
                        content = """
                            #+title: Sample
                            #+filetags: :test:demo:
                            :PROPERTIES:
                            :ID: abc
                            :CREATED: 2024-01-15
                            :END:

                            Introduction paragraph with https://kotlinlang.org link.

                            * Heading One                    :important:
                            Body *bold* text and [[file:other.org][alias]].
                            ** Sub heading                   :nested:tag:
                            More body.
                        """.trimIndent(),
                        links = listOf(
                            NoteLink(
                                fromId = NoteId("notes/sample.org"),
                                target = LinkTarget.Path("other.org"),
                                label = "Alias",
                                resolvedPath = "other.org",
                            ),
                        ),
                        orgId = "sample-id",
                    ),
                    backlinks = listOf(
                        LinkEntityDto(
                            source = "notes/linked.org",
                            target = "notes/sample.org",
                            alias = "Linked Note",
                            sourceTitle = "Linked Note",
                        ),
                    ),
                    lastModified = "Today at 10:42 AM",
                    isFavorite = false,
                ),
                searchState = NoteViewViewModel.NoteSearchState(
                    isActive = true,
                    query = "Note",
                    options = SearchOptions(),
                ),
                onOpenLink = {},
                onOpenExternalLink = {},
                onEditNote = {},
                onNavigateHome = {},
                onBack = {},
                onShare = {},
                onFavorite = {},
                onCreateNote = {},
                onRetry = {},
                onDelete = {},
                onDuplicate = {},
                onRename = {},
                onOpenSearch = {},
                onCloseSearch = {},
                onSearchQueryChange = {},
                onClearSearch = {},
                onSearchOptionsChange = {},
                onPrevMatch = {},
                onNextMatch = {},
                onCopyToClipboard = {},
                onCopyIdLink = {},
                onCopyFileLink = {},
                onUpdateProperties = {},
                onUpdateTags = {},
                allTags = listOf("example", "tag"),
            )
        }
    }
}

private fun shareNoteContent(
    context: android.content.Context,
    title: String,
    filename: String,
    content: String,
) {
    val safeFilename = filename
        .ifBlank { "note.org" }
        .replace(Regex("""[^\w.\-]+"""), "_")
        .let { if (it.endsWith(".org", ignoreCase = true)) it else "$it.org" }

    runCatching {
        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(sharedDir, safeFilename)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Note"))
    }.onFailure {
        android.widget.Toast
            .makeText(context, "Unable to share note", android.widget.Toast.LENGTH_SHORT)
            .show()
    }
}
