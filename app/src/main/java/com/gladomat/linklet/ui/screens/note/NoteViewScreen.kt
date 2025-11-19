package com.gladomat.linklet.ui.screens.note

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.ui.components.BacklinkList
import com.gladomat.linklet.ui.components.OrgSection
import com.gladomat.linklet.ui.components.OrgTextPalette
import com.gladomat.linklet.ui.components.ORG_EXTERNAL_LINK_ANNOTATION_TAG
import com.gladomat.linklet.ui.components.ORG_LINK_ANNOTATION_TAG
import com.gladomat.linklet.ui.components.buildOrgContentAnnotatedString
import com.gladomat.linklet.ui.components.parseOrgDocument
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.note.NoteViewUiState
import com.gladomat.linklet.viewmodel.note.NoteViewViewModel

@Composable
fun NoteViewRoute(
    onEditNote: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onExitToList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteViewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
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
    BackHandler {
        if (!viewModel.handleBackPress()) {
            handleExit()
        }
    }
    NoteViewScreen(
        state = state,
        onOpenLink = viewModel::openNote,
        onOpenExternalLink = { uri -> uriHandler.openUri(uri) },
        onEditNote = onEditNote,
        onNavigateHome = handleNavigateHome,
        onRetry = viewModel::loadNote,
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
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        NoteViewUiState.Loading -> LoadingState(modifier)
        is NoteViewUiState.Success -> SuccessState(
            note = state.note,
            backlinks = state.backlinks,
            onOpenLink = onOpenLink,
            onOpenExternalLink = onOpenExternalLink,
            onEditNote = onEditNote,
            onNavigateHome = onNavigateHome,
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
    onOpenExternalLink: (String) -> Unit,
    onEditNote: (String) -> Unit,
    onNavigateHome: () -> Unit,
    modifier: Modifier,
) {
    val document = remember(note.content) { parseOrgDocument(note.content) }
    var metadataExpanded by remember(note.id.path) { mutableStateOf(false) }
    val sectionExpansion = remember(document.sections) {
        mutableStateMapOf<String, Boolean>().apply {
            document.sections.forEach { collectIds(it, this) }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        TopActionRow(
            onNavigateHome = onNavigateHome,
            onEditNote = { onEditNote(note.id.path) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        NoteHeaderCard(
            title = note.title,
            path = note.id.path,
            metadata = document.metadata,
            expanded = metadataExpanded,
            onToggle = { metadataExpanded = !metadataExpanded },
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
            OrgBodyText(
                text = document.preface,
                note = note,
                palette = palette,
                onOpenLink = onOpenLink,
                onOpenExternalLink = onOpenExternalLink,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        document.sections.forEach { section ->
            OrgSectionView(
                section = section,
                expandedState = sectionExpansion,
                palette = palette,
                note = note,
                onOpenLink = onOpenLink,
                onOpenExternalLink = onOpenExternalLink,
            )
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

@Composable
private fun TopActionRow(
    onNavigateHome: () -> Unit,
    onEditNote: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateHome) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Back to list",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onEditNote) {
            Text("Edit")
        }
    }
}

@Composable
private fun NoteHeaderCard(
    title: String,
    path: String,
    metadata: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (metadata.isNotEmpty()) onToggle() },
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
            if (metadata.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Metadata",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                AnimatedVisibility(expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(12.dp),
                    ) {
                        metadata.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                        }
                    }
                }
            }
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
                        content = """
                            #+title: Sample
                            :PROPERTIES:
                            :ID: abc
                            :END:

                            Introduction paragraph with https://kotlinlang.org link.

                            * Heading One
                            Body *bold* text and [[file:other.org][alias]].
                            ** Sub heading
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
                ),
                onOpenLink = {},
                onOpenExternalLink = {},
                onEditNote = {},
                onNavigateHome = {},
                onRetry = {},
            )
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
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = palette.colorForLevel(section.level),
            )
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.colorForLevel(section.level),
                modifier = Modifier.padding(start = 8.dp),
                fontWeight = FontWeight.SemiBold,
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(start = 32.dp)) {
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
        Spacer(modifier = Modifier.height(8.dp))
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
