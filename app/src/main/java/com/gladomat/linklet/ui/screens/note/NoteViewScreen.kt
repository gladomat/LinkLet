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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
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
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.ui.REFRESH_NOTE_KEY
import com.gladomat.linklet.ui.components.OrgSection
import com.gladomat.linklet.ui.components.OrgTextPalette
import com.gladomat.linklet.ui.components.ORG_EXTERNAL_LINK_ANNOTATION_TAG
import com.gladomat.linklet.ui.components.ORG_LINK_ANNOTATION_TAG
import com.gladomat.linklet.ui.components.buildOrgContentAnnotatedString
import com.gladomat.linklet.ui.components.parseOrgDocument
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
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteViewViewModel = hiltViewModel(),
    savedStateHandle: SavedStateHandle? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                    val currentClip = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
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
        onOpenLink = viewModel::openNote,
        onOpenExternalLink = { uri -> uriHandler.openUri(uri) },
        onEditNote = onEditNote,
        onNavigateHome = handleNavigateHome,
        onBack = handleBack,
        onSearch = onSearch,
        onShare = handleShare,
        onFavorite = viewModel::toggleFavorite,
        onCreateNote = onCreateNote,
        onRetry = viewModel::loadNote,
        onDelete = { viewModel.deleteNote { handleExit() } },
        onDuplicate = viewModel::duplicateNote,
        onRename = viewModel::renameNote,
        onCopyToClipboard = handleCopyToClipboard,
        onUpdateProperties = viewModel::updateProperties,
        onUpdateTags = viewModel::updateTags,
        allTags = allTags,
        modifier = modifier,
    )
}

@Composable
fun NoteViewScreen(
    state: NoteViewUiState,
    onOpenLink: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onEditNote: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onCreateNote: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: (String) -> Unit,
    onCopyToClipboard: () -> Unit,
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
            onOpenLink = onOpenLink,
            onOpenExternalLink = onOpenExternalLink,
            onEditNote = onEditNote,
            onNavigateHome = onNavigateHome,
            onBack = onBack,
            onSearch = onSearch,
            onShare = onShare,
            onFavorite = onFavorite,
            onCreateNote = onCreateNote,
            onDelete = onDelete,
            onDuplicate = onDuplicate,
            onRename = onRename,
            onCopyToClipboard = onCopyToClipboard,
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
    onOpenLink: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onEditNote: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onCreateNote: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: (String) -> Unit,
    onCopyToClipboard: () -> Unit,
    onUpdateProperties: (Map<String, String>) -> Unit,
    onUpdateTags: (List<String>) -> Unit,
    allTags: List<String>,
    modifier: Modifier,
) {
    val document = remember(note.content) { parseOrgDocument(note.content) }
    var propertiesExpanded by remember(note.id.path) { mutableStateOf(false) }
    var showBacklinksSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    val sectionExpansion = remember(document.sections) {
        mutableStateMapOf<String, Boolean>().apply {
            document.sections.forEach { collectIds(it, this) }
        }
    }
    val scrollState = rememberScrollState()

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
                onBack = onBack,
                onHome = onNavigateHome,
                onSearch = onSearch,
                onEdit = { onEditNote(note.id.path) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            // Properties drawer
            if (document.properties.isNotEmpty()) {
                PropertiesSection(
                    properties = document.properties,
                    expanded = propertiesExpanded,
                    onToggle = { propertiesExpanded = !propertiesExpanded },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Note title card
            NoteHeaderCard(
                title = note.title,
                path = note.id.path,
                fileTags = document.fileTags,
            )

            val colorScheme = MaterialTheme.colorScheme
            val palette = remember(
                colorScheme.primary,
                colorScheme.secondary,
                colorScheme.tertiary,
                colorScheme.onSurfaceVariant,
                colorScheme.surfaceVariant,
                colorScheme.onSurface,
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
                )
            }
            if (document.preface.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                OrgBodyText(
                    text = document.preface,
                    note = note,
                    palette = palette,
                    onOpenLink = onOpenLink,
                    onOpenExternalLink = onOpenExternalLink,
                )
            }
            document.sections.forEach { section ->
                Spacer(modifier = Modifier.height(8.dp))
                OrgSectionView(
                    section = section,
                    expandedState = sectionExpansion,
                    palette = palette,
                    note = note,
                    onOpenLink = onOpenLink,
                    onOpenExternalLink = onOpenExternalLink,
                )
            }
        }
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

@Composable
private fun OrgBodyText(
    text: String,
    note: Note,
    palette: OrgTextPalette,
    onOpenLink: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return
    val annotated = remember(text, note.links, palette) {
        buildOrgContentAnnotatedString(
            content = text,
            links = note.links,
            palette = palette,
        )
    }
    ClickableText(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            handleAnnotationClick(
                annotated = annotated,
                offset = offset,
                onOpenLink = onOpenLink,
                onOpenExternalLink = onOpenExternalLink,
            )
        },
    )
}

@Composable
private fun OrgSectionView(
    section: OrgSection,
    expandedState: MutableMap<String, Boolean>,
    palette: OrgTextPalette,
    note: Note,
    onOpenLink: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
) {
    val isExpanded = expandedState[section.id] ?: true
    val indent = ((section.level - 1).coerceAtLeast(0) * 12).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedState[section.id] = !isExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Expand/collapse indicator with ▸/▾ style
            Text(
                text = if (isExpanded) "▾" else "▸",
                style = MaterialTheme.typography.titleMedium,
                color = palette.colorForLevel(section.level),
            )
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.colorForLevel(section.level),
                modifier = Modifier.padding(start = 8.dp),
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Show headline tags if present
        if (section.tags.isNotEmpty()) {
            TagRow(
                tags = section.tags,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(start = 24.dp)) {
                OrgBodyText(
                    text = section.body,
                    note = note,
                    palette = palette,
                    onOpenLink = onOpenLink,
                    onOpenExternalLink = onOpenExternalLink,
                )
                section.children.forEach { child ->
                    OrgSectionView(
                        section = child,
                        expandedState = expandedState,
                        palette = palette,
                        note = note,
                        onOpenLink = onOpenLink,
                        onOpenExternalLink = onOpenExternalLink,
                    )
                }
            }
        }
    }
}

private fun handleAnnotationClick(
    annotated: AnnotatedString,
    offset: Int,
    onOpenLink: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
) {
    val internal = annotated.getStringAnnotations(ORG_LINK_ANNOTATION_TAG, offset, offset).firstOrNull()
    if (internal != null) {
        onOpenLink(internal.item)
        return
    }
    val external = annotated.getStringAnnotations(ORG_EXTERNAL_LINK_ANNOTATION_TAG, offset, offset).firstOrNull()
    if (external != null) {
        onOpenExternalLink(external.item)
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
                onOpenLink = {},
                onOpenExternalLink = {},
                onEditNote = {},
                onNavigateHome = {},
                onBack = {},
                onSearch = {},
                onShare = {},
                onFavorite = {},
                onCreateNote = {},
                onRetry = {},
                onDelete = {},
                onDuplicate = {},
                onRename = {},
                onCopyToClipboard = {},
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
