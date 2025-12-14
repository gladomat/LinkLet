package com.gladomat.linklet.ui.components

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
import androidx.compose.runtime.remember
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
import com.gladomat.linklet.data.model.NoteLink

// =============================================================================
// Block Render Context
// =============================================================================

/**
 * Context for rendering Org blocks, passed down to avoid drilling individual parameters.
 */
data class BlockRenderContext(
    val palette: OrgTextPalette,
    val links: List<NoteLink>,
    val onOpenLink: (String) -> Unit,
    val onOpenExternalLink: (String) -> Unit,
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
    context: BlockRenderContext,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is OrgBlock.Paragraph -> OrgParagraphBlock(block, context)
                is OrgBlock.Table -> OrgTableBlock(block, context)
                is OrgBlock.SourceBlock -> OrgSourceBlock(block, context)
                is OrgBlock.QuoteBlock -> OrgQuoteBlock(block, context)
                is OrgBlock.CenterBlock -> OrgCenterBlock(block, context)
                is OrgBlock.VerseBlock -> OrgVerseBlock(block)
                is OrgBlock.ExampleBlock -> OrgExampleBlock(block)
                is OrgBlock.UnknownBlock -> OrgUnknownBlock(block)
            }
        }
    }
}

// =============================================================================
// Paragraph Block
// =============================================================================

@Composable
private fun OrgParagraphBlock(
    block: OrgBlock.Paragraph,
    context: BlockRenderContext,
    modifier: Modifier = Modifier,
) {
    if (block.text.isBlank()) return
    val annotated = remember(block.text, context.links, context.palette) {
        buildOrgContentAnnotatedString(
            content = block.text,
            links = context.links,
            palette = context.palette,
        )
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
    context: BlockRenderContext,
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
        columnCount,
    ) {
        block.rows.map { row ->
            List(columnCount) { columnIndex ->
                buildOrgContentAnnotatedString(
                    content = row.getOrNull(columnIndex).orEmpty(),
                    links = context.links,
                    palette = context.palette,
                )
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

@Suppress("UNUSED_PARAMETER")
@Composable
private fun OrgSourceBlock(
    block: OrgBlock.SourceBlock,
    context: BlockRenderContext,
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

    val highlighted = remember(block.content, block.language, theme) {
        SyntaxHighlighter.highlight(block.content, block.language, theme)
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
    context: BlockRenderContext,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(block.content, context.links, context.palette) {
        buildOrgContentAnnotatedString(
            content = block.content,
            links = context.links,
            palette = context.palette,
        )
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
    context: BlockRenderContext,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(block.content, context.links, context.palette) {
        buildOrgContentAnnotatedString(
            content = block.content,
            links = context.links,
            palette = context.palette,
        )
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = block.content,
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
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

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
                text = block.content,
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
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

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
                text = block.content,
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
