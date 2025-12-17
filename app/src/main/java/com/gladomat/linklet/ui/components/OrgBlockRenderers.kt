package com.gladomat.linklet.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gladomat.linklet.data.parser.org.OrgBlock
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.utils.OrgPathResolver
import com.gladomat.linklet.domain.service.MatchRange
import com.gladomat.linklet.domain.service.SearchOptions

// =============================================================================
// Block Render Context
// =============================================================================

/**
 * Context for rendering Org blocks, passed down to avoid drilling individual parameters.
 */
data class RenderContext(
    val palette: OrgTextPalette,
    val links: List<NoteLink>,
    val notePath: String,
    val onOpenLink: (String) -> Unit,
    val onOpenExternalLink: (String) -> Unit,
    val resolveStorageUri: suspend (String) -> Result<Uri>,
    val onOpenImage: (Uri) -> Unit,
    val searchQuery: String? = null,
    val matchList: List<MatchRange> = emptyList(),
    val activeMatchIndex: Int = -1,
    val searchOptions: SearchOptions = SearchOptions(),
    val matchListByBlockId: Map<String, List<MatchRange>> = emptyMap(),
)

// =============================================================================
// Main Block Column Renderer
// =============================================================================

/**
 * Renders a list of [OrgBlock]s in a Column.
 * Each block is rendered using the appropriate Composable.
 */
@Composable
fun OrgBlockColumn(
    blocks: List<OrgBlock>,
    context: RenderContext,
    blockIdPrefix: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            OrgBlockView(
                block = block,
                blockId = "$blockIdPrefix/block-$index",
                context = context,
                nodeId = null,
                nodeDir = null,
            )
        }
    }
}

@Composable
fun OrgBlockView(
    block: OrgBlock,
    blockId: String,
    context: RenderContext,
    nodeId: String?,
    nodeDir: String?,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is OrgBlock.Paragraph -> OrgParagraphBlock(block, blockId, context, nodeId, nodeDir, modifier)
        is OrgBlock.Table -> OrgTableBlock(block, blockId, context, modifier)
        is OrgBlock.SourceBlock -> OrgSourceBlock(block, blockId, context, modifier)
        is OrgBlock.QuoteBlock -> OrgQuoteBlock(block, blockId, context, modifier)
        is OrgBlock.CenterBlock -> OrgCenterBlock(block, blockId, context, modifier)
        is OrgBlock.VerseBlock -> OrgVerseBlock(block, blockId, context, modifier)
        is OrgBlock.ExampleBlock -> OrgExampleBlock(block, blockId, context, modifier)
        is OrgBlock.UnknownBlock -> OrgUnknownBlock(block, blockId, context, modifier)
    }
}

private fun RenderContext.matchRangesFor(blockId: String): List<IntRange> =
    matchListByBlockId[blockId]
        .orEmpty()
        .mapNotNull { match ->
            val endInclusive = match.end - 1
            if (match.start < 0 || endInclusive < match.start) null else match.start..endInclusive
        }

private fun RenderContext.activeRangeFor(blockId: String): IntRange? {
    val active = matchList.getOrNull(activeMatchIndex) ?: return null
    if (active.blockId != blockId) return null
    val endInclusive = active.end - 1
    if (active.start < 0 || endInclusive < active.start) return null
    return active.start..endInclusive
}

private fun RenderContext.applySearchHighlights(
    base: AnnotatedString,
    blockId: String,
): AnnotatedString {
    val ranges = matchRangesFor(blockId)
    val active = activeRangeFor(blockId)
    return applyHighlights(
        base = base,
        ranges = ranges,
        activeRange = active,
        matchBackground = palette.searchHighlightBackground,
        activeBackground = palette.activeSearchHighlightBackground,
    )
}

// =============================================================================
// Paragraph Block
// =============================================================================

@Composable
private fun OrgParagraphBlock(
    block: OrgBlock.Paragraph,
    blockId: String,
    context: RenderContext,
    nodeId: String?,
    nodeDir: String?,
    modifier: Modifier = Modifier,
) {
    if (block.text.isBlank()) return

    val inlineImage = remember(block.text) { detectInlineImageCandidate(block.text) }
    if (inlineImage != null) {
        val resolvedPath = remember(inlineImage, context.notePath, nodeId, nodeDir) {
            when (inlineImage.scheme) {
                "attachment" -> OrgPathResolver.resolveAttachmentPath(
                    notePath = context.notePath,
                    nodeId = nodeId,
                    dirProperty = nodeDir,
                    attachmentName = inlineImage.target,
                )
                else -> OrgPathResolver.resolveFileTargetPath(
                    notePath = context.notePath,
                    target = inlineImage.target,
                )
            }
        }

        var uri: Uri? by remember(resolvedPath) { mutableStateOf(null) }
        var error: String? by remember(resolvedPath) { mutableStateOf(null) }

        LaunchedEffect(resolvedPath) {
            uri = null
            error = null
            val path = resolvedPath
            if (path == null) {
                error = "Unable to resolve image path"
                return@LaunchedEffect
            }
            context.resolveStorageUri(path).fold(
                onSuccess = { uri = it },
                onFailure = { error = it.message ?: "Unable to load image" },
            )
        }

        when {
            error != null -> Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            uri == null -> Text(
                text = "Loading image…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            else -> OrgInlineImageBlock(
                uri = uri!!,
                caption = inlineImage.caption,
                align = inlineImage.attrs["align"],
                onOpen = context.onOpenImage,
                modifier = modifier,
            )
        }
        return
    }

    val base = remember(block.text, context.links, context.palette) {
        buildOrgContentAnnotatedString(
            content = block.text,
            links = context.links,
            palette = context.palette,
        )
    }
    val annotated = remember(base, context.matchListByBlockId, context.activeMatchIndex) {
        context.applySearchHighlights(base = base, blockId = blockId)
    }
    ClickableText(
        text = annotated,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            val internal = annotated.getStringAnnotations(ORG_LINK_ANNOTATION_TAG, offset, offset).firstOrNull()
            if (internal != null) {
                context.onOpenLink(internal.item)
                return@ClickableText
            }
            val external = annotated.getStringAnnotations(ORG_EXTERNAL_LINK_ANNOTATION_TAG, offset, offset).firstOrNull()
            if (external != null) {
                context.onOpenExternalLink(external.item)
            }
        },
    )
}

// =============================================================================
// Table Block
// =============================================================================

@Composable
private fun OrgTableBlock(
    block: OrgBlock.Table,
    blockId: String,
    context: RenderContext,
    modifier: Modifier = Modifier,
) {
    if (block.rows.isEmpty()) return

    val scrollState = rememberScrollState()
    val headerRowCount = block.headerRowCount.coerceIn(0, block.rows.size)
    val columnCount = remember(block.rows) { block.rows.maxOfOrNull { it.size } ?: 0 }
    if (columnCount == 0) return

    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val cellBg = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cellTextStyle = MaterialTheme.typography.bodySmall
    val headerTextStyle = cellTextStyle.copy(fontWeight = FontWeight.Bold)
    val cellPaddingHorizontal = 12.dp
    val cellPaddingVertical = 8.dp

    val cellAnnotatedRows: List<List<AnnotatedString>> = remember(
        block.rows,
        context.links,
        context.palette,
        context.matchListByBlockId,
        context.activeMatchIndex,
        columnCount,
    ) {
        block.rows.mapIndexed { rowIndex, row ->
            List(columnCount) { columnIndex ->
                val cellId = "$blockId/cell-$rowIndex-$columnIndex"
                val base = buildOrgContentAnnotatedString(
                    content = row.getOrNull(columnIndex).orEmpty(),
                    links = context.links,
                    palette = context.palette,
                )
                context.applySearchHighlights(base = base, blockId = cellId)
            }
        }
    }

    val columnWidthsDp = remember(
        cellAnnotatedRows,
        headerRowCount,
        cellTextStyle,
        headerTextStyle,
        density,
    ) {
        val widthsPx = IntArray(columnCount)
        cellAnnotatedRows.forEachIndexed { rowIndex, row ->
            val style = if (rowIndex < headerRowCount) headerTextStyle else cellTextStyle
            row.forEachIndexed { colIndex, annotated ->
                val measuredWidth = textMeasurer.measure(text = annotated, style = style).size.width
                if (measuredWidth > widthsPx[colIndex]) widthsPx[colIndex] = measuredWidth
            }
        }
        with(density) {
            widthsPx.map { it.toDp() + (cellPaddingHorizontal * 2) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .horizontalScroll(scrollState),
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            block.rows.forEachIndexed { rowIndex, _ ->
                val isHeader = rowIndex < headerRowCount
                Row(
                    modifier = Modifier
                        .background(if (isHeader) headerBg else cellBg),
                ) {
                    cellAnnotatedRows[rowIndex].forEachIndexed { columnIndex, annotated ->
                        Box(
                            modifier = Modifier
                                .width(columnWidthsDp[columnIndex])
                                .border(0.5.dp, borderColor)
                                .padding(horizontal = cellPaddingHorizontal, vertical = cellPaddingVertical),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = annotated,
                                style = if (isHeader) headerTextStyle else cellTextStyle,
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Source Block
// =============================================================================

@Composable
private fun OrgSourceBlock(
    block: OrgBlock.SourceBlock,
    blockId: String,
    context: RenderContext,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val theme = remember(colorScheme) {
        SyntaxHighlighter.Theme(
            keyword = colorScheme.primary,
            string = colorScheme.tertiary,
            comment = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            number = colorScheme.secondary,
            function = colorScheme.primaryContainer,
            type = colorScheme.secondary,
            defaultText = colorScheme.onSurface,
        )
    }

    val baseHighlighted = remember(block.content, block.language, theme) {
        SyntaxHighlighter.highlight(block.content, block.language, theme)
    }
    val highlighted = remember(baseHighlighted, context.matchListByBlockId, context.activeMatchIndex) {
        context.applySearchHighlights(base = baseHighlighted, blockId = blockId)
    }

    val scrollState = rememberScrollState()
    val lines = block.content.lines()
    val lineNumberWidth = lines.size.toString().length

    SelectionContainer {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .horizontalScroll(scrollState)
                .padding(12.dp),
        ) {
            Row {
                // Line numbers column
                Column(
                    modifier = Modifier.padding(end = 12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    lines.forEachIndexed { index, _ ->
                        Text(
                            text = (index + 1).toString().padStart(lineNumberWidth),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            ),
                        )
                    }
                }

                // Code content
                Text(
                    text = highlighted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

// =============================================================================
// Quote Block
// =============================================================================

@Composable
private fun OrgQuoteBlock(
    block: OrgBlock.QuoteBlock,
    blockId: String,
    context: RenderContext,
    modifier: Modifier = Modifier,
) {
    val base = remember(block.content, context.links, context.palette) {
        buildOrgContentAnnotatedString(
            content = block.content,
            links = context.links,
            palette = context.palette,
        )
    }
    val annotated = remember(base, context.matchListByBlockId, context.activeMatchIndex) {
        context.applySearchHighlights(base = base, blockId = blockId)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(
                width = 3.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            )
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            ),
        )
    }
}

// =============================================================================
// Center Block
// =============================================================================

@Composable
private fun OrgCenterBlock(
    block: OrgBlock.CenterBlock,
    blockId: String,
    context: RenderContext,
    modifier: Modifier = Modifier,
) {
    val base = remember(block.content, context.links, context.palette) {
        buildOrgContentAnnotatedString(
            content = block.content,
            links = context.links,
            palette = context.palette,
        )
    }
    val annotated = remember(base, context.matchListByBlockId, context.activeMatchIndex) {
        context.applySearchHighlights(base = base, blockId = blockId)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

// =============================================================================
// Verse Block
// =============================================================================

@Composable
private fun OrgVerseBlock(
    block: OrgBlock.VerseBlock,
    blockId: String,
    context: RenderContext,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(block.content, context.matchListByBlockId, context.activeMatchIndex) {
        context.applySearchHighlights(base = AnnotatedString(block.content), blockId = blockId)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            ),
        )
    }
}

// =============================================================================
// Example Block
// =============================================================================

@Composable
private fun OrgExampleBlock(
    block: OrgBlock.ExampleBlock,
    blockId: String,
    context: RenderContext,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val annotated = remember(block.content, context.matchListByBlockId, context.activeMatchIndex) {
        context.applySearchHighlights(base = AnnotatedString(block.content), blockId = blockId)
    }

    SelectionContainer {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .horizontalScroll(scrollState)
                .padding(12.dp),
        ) {
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            )
        }
    }
}

// =============================================================================
// Unknown Block (Fallback)
// =============================================================================

@Composable
private fun OrgUnknownBlock(
    block: OrgBlock.UnknownBlock,
    blockId: String,
    context: RenderContext,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val annotated = remember(block.content, context.matchListByBlockId, context.activeMatchIndex) {
        context.applySearchHighlights(base = AnnotatedString(block.content), blockId = blockId)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .horizontalScroll(scrollState)
            .padding(12.dp),
    ) {
        Column {
            Text(
                text = "#+BEGIN_${block.blockType}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                ),
            )
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                text = "#+END_${block.blockType}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                ),
            )
        }
    }
}
