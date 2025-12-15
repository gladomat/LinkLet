package com.gladomat.linklet.data.parser.org

/**
 * Sealed interface representing a block-level element in an Org document.
 * The section body is parsed into a list of these blocks for rendering and search.
 */
sealed interface OrgBlock {
    /**
     * A paragraph of text. Supports inline formatting (bold, italic, links, etc.).
     */
    data class Paragraph(val text: String) : OrgBlock

    /**
     * An Org table.
     *
     * @param rows All rows of data (excluding separator rows like `|---|`).
     * @param headerRowCount The number of leading rows that should be styled as headers.
     *        (1 if a separator `|---|` was found after the first row, 0 otherwise).
     */
    data class Table(
        val rows: List<List<String>>,
        val headerRowCount: Int = 0,
    ) : OrgBlock

    /**
     * A source code block (`#+BEGIN_SRC <lang>`).
     *
     * @param language The language identifier (e.g., "python", "kotlin").
     * @param content The raw code content (newlines preserved).
     * @param params Optional raw parameters after the language on the BEGIN line.
     */
    data class SourceBlock(
        val language: String?,
        val content: String,
        val params: String? = null,
    ) : OrgBlock

    /**
     * A quote block (`#+BEGIN_QUOTE`). Supports inline formatting.
     */
    data class QuoteBlock(val content: String) : OrgBlock

    /**
     * A center block (`#+BEGIN_CENTER`). Supports inline formatting.
     */
    data class CenterBlock(val content: String) : OrgBlock

    /**
     * A verse block (`#+BEGIN_VERSE`). Preserves whitespace and newlines.
     */
    data class VerseBlock(val content: String) : OrgBlock

    /**
     * An example block (`#+BEGIN_EXAMPLE`). Monospaced, preserves whitespace.
     */
    data class ExampleBlock(val content: String) : OrgBlock

    /**
     * Fallback for any unrecognized block type.
     *
     * @param blockType The captured TYPE from `#+BEGIN_<TYPE>`.
     * @param content The raw content within the block.
     */
    data class UnknownBlock(
        val blockType: String,
        val content: String,
    ) : OrgBlock
}

