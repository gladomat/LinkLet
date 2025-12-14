package com.gladomat.linklet.ui.components

private val HeadingRegex = Regex("""^(\*{1,})\s+(.*)$""")
private val HeadingWithTagsRegex = Regex("""^(\*{1,})\s+(.*?)\s*(:(?:[a-zA-Z0-9_@#%]+:)+)\s*$""")
private val FileTagsRegex = Regex("""^#\+filetags:\s*(.*)$""", RegexOption.IGNORE_CASE)
private val PropertyLineRegex = Regex("""^:([A-Za-z_-]+):\s*(.*)$""")
private val BeginBlockRegex = Regex("""^\s*#\+BEGIN_(\w+)(?:\s+(.*?))?\s*$""", RegexOption.IGNORE_CASE)
private val EndBlockRegex = Regex("""^\s*#\+END_(\w+)\s*$""", RegexOption.IGNORE_CASE)
private val TableRowRegex = Regex("""^\s*\|.*\|\s*$""")
private val TableSeparatorRegex = Regex("""^\s*\|[-+| :]+\|\s*$""")

data class OrgDocument(
    val metadata: List<String>,
    val properties: Map<String, String>,
    val fileTags: List<String>,
    val preface: String,
    val prefaceBlocks: List<OrgBlock>,
    val sections: List<OrgSection>,
)

data class OrgSection(
    val id: String,
    val level: Int,
    val title: String,
    val tags: List<String>,
    val body: String,
    val blocks: List<OrgBlock>,
    val children: List<OrgSection>,
)

fun parseOrgDocument(content: String): OrgDocument {
    val lines = content.lines()
    val (metadataLines, bodyStart) = extractMetadata(lines)
    val properties = extractProperties(metadataLines)
    val fileTags = extractFileTags(metadataLines)
    val bodyLines = lines.drop(bodyStart)
    val root = OrgNode(
        id = "root",
        level = 0,
        title = "",
        tags = emptyList(),
        contentLines = mutableListOf(),
        children = mutableListOf(),
    )
    val stack = ArrayDeque<OrgNode>()
    stack.add(root)
    var headingCounter = 0

    bodyLines.forEach { rawLine ->
        val trimmed = rawLine.trimEnd()
        val (headingLevel, headingTitle, headingTags) = parseHeadingLine(trimmed)
        if (headingLevel != null && headingTitle != null) {
            val level = headingLevel
            val node = OrgNode(
                id = "heading-${headingCounter++}",
                level = level,
                title = headingTitle,
                tags = headingTags,
                contentLines = mutableListOf(),
                children = mutableListOf(),
            )
            while (stack.isNotEmpty() && stack.last().level >= level) {
                stack.removeLast()
            }
            val parent = stack.lastOrNull() ?: root
            parent.children.add(node)
            stack.add(node)
        } else {
            stack.last().contentLines.add(trimmed)
        }
    }

    val preface = root.contentLines.joinToString("\n").trim()
    val prefaceBlocks = parseContentToBlocks(root.contentLines)
    return OrgDocument(
        metadata = metadataLines,
        properties = properties,
        fileTags = fileTags,
        preface = preface,
        prefaceBlocks = prefaceBlocks,
        sections = root.children.map { it.toSection() },
    )
}

private data class OrgNode(
    val id: String,
    val level: Int,
    val title: String,
    val tags: List<String>,
    val contentLines: MutableList<String>,
    val children: MutableList<OrgNode>,
)

private fun OrgNode.toSection(): OrgSection =
    OrgSection(
        id = id,
        level = level,
        title = title,
        tags = tags,
        body = contentLines.joinToString("\n").trim(),
        blocks = parseContentToBlocks(contentLines),
        children = children.map { it.toSection() },
    )

// ============================================================================
// Block Parsing State Machine
// ============================================================================

private sealed interface ParseState {
    data object Idle : ParseState
    data class InBlock(val type: String, val langOrParams: String?, val lines: MutableList<String>) : ParseState
    data class InTable(val rows: MutableList<List<String>>, val separatorAfterRow: Int?) : ParseState
}

/**
 * Parses a list of content lines into structured [OrgBlock]s.
 * Uses a state machine to detect tables, source blocks, and other Org blocks.
 */
internal fun parseContentToBlocks(lines: List<String>): List<OrgBlock> {
    val blocks = mutableListOf<OrgBlock>()
    var state: ParseState = ParseState.Idle
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        val text = paragraphBuffer.toString().trim()
        if (text.isNotEmpty()) {
            blocks.add(OrgBlock.Paragraph(text))
        }
        paragraphBuffer.clear()
    }

    fun flushTable(tableState: ParseState.InTable) {
        if (tableState.rows.isNotEmpty()) {
            val headerRowCount = if (tableState.separatorAfterRow != null) tableState.separatorAfterRow + 1 else 0
            blocks.add(OrgBlock.Table(rows = tableState.rows.toList(), headerRowCount = headerRowCount))
        }
    }

    fun handleIdleLine(line: String) {
        val beginMatch = BeginBlockRegex.matchEntire(line)
        if (beginMatch != null) {
            flushParagraph()
            val blockType = beginMatch.groupValues[1].uppercase()
            val params = beginMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
            state = ParseState.InBlock(blockType, params, mutableListOf())
            return
        }

        if (TableRowRegex.matches(line) && !TableSeparatorRegex.matches(line)) {
            flushParagraph()
            val cells = parseTableRow(line)
            state = ParseState.InTable(mutableListOf(cells), null)
            return
        }

        if (line.isBlank()) {
            if (paragraphBuffer.isNotEmpty()) {
                flushParagraph()
            }
        } else {
            if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append("\n")
            paragraphBuffer.append(line)
        }
    }

    for (line in lines) {
        when (val currentState = state) {
            is ParseState.Idle -> {
                handleIdleLine(line)
            }

            is ParseState.InBlock -> {
                val endMatch = EndBlockRegex.matchEntire(line)
                if (endMatch != null && endMatch.groupValues[1].uppercase() == currentState.type) {
                    val content = currentState.lines.joinToString("\n")
                    val block = createBlockFromType(currentState.type, content, currentState.langOrParams)
                    blocks.add(block)
                    state = ParseState.Idle
                } else {
                    currentState.lines.add(line)
                }
            }

            is ParseState.InTable -> {
                when {
                    TableSeparatorRegex.matches(line) -> {
                        // This is a separator row, mark header position
                        state = currentState.copy(separatorAfterRow = currentState.rows.size - 1)
                    }
                    TableRowRegex.matches(line) -> {
                        currentState.rows.add(parseTableRow(line))
                    }
                    else -> {
                        // End of table
                        flushTable(currentState)
                        state = ParseState.Idle
                        handleIdleLine(line)
                    }
                }
            }
        }
    }

    // Flush remaining state
    when (val finalState = state) {
        is ParseState.Idle -> flushParagraph()
        is ParseState.InBlock -> {
            // Unclosed block, treat as unknown/raw
            val content = finalState.lines.joinToString("\n")
            blocks.add(OrgBlock.UnknownBlock(finalState.type, content))
        }
        is ParseState.InTable -> flushTable(finalState)
    }

    return blocks
}

private fun parseTableRow(line: String): List<String> {
    // Split by | and trim each cell, dropping the first/last empty strings from leading/trailing pipes
    return line.trim().split("|")
        .drop(1)
        .dropLast(1)
        .map { it.trim() }
}

private fun createBlockFromType(blockType: String, content: String, params: String?): OrgBlock {
    return when (blockType) {
        "SRC" -> {
            // params is "lang [other params]" e.g. "python :results output"
            val parts = params?.split(Regex("\\s+"), limit = 2)
            val lang = parts?.getOrNull(0)?.takeIf { it.isNotBlank() && !it.startsWith(":") }
            val extraParams = parts?.getOrNull(1)
            OrgBlock.SourceBlock(language = lang, content = content, params = extraParams)
        }
        "QUOTE" -> OrgBlock.QuoteBlock(content)
        "CENTER" -> OrgBlock.CenterBlock(content)
        "VERSE" -> OrgBlock.VerseBlock(content)
        "EXAMPLE" -> OrgBlock.ExampleBlock(content)
        else -> OrgBlock.UnknownBlock(blockType, content)
    }
}

// ============================================================================
// Metadata Extraction
// ============================================================================

private fun extractMetadata(lines: List<String>): Pair<List<String>, Int> {
    val metadataLines = mutableListOf<String>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        if (line.startsWith(":") || line.startsWith("#+")) {
            metadataLines.add(line)
            index++
        } else if (line.isBlank() && metadataLines.isNotEmpty()) {
            index++
            break
        } else if (metadataLines.isEmpty() && line.isBlank()) {
            index++
        } else {
            break
        }
    }
    return metadataLines to index
}

/**
 * Extracts :PROPERTIES: drawer key-value pairs from metadata lines.
 * Format: `:KEY: value`
 * Excludes structural lines like `:PROPERTIES:` and `:END:`
 */
private fun extractProperties(metadataLines: List<String>): Map<String, String> {
    val properties = mutableMapOf<String, String>()
    var inPropertiesDrawer = false
    for (line in metadataLines) {
        val trimmed = line.trim()
        when {
            trimmed.equals(":PROPERTIES:", ignoreCase = true) -> inPropertiesDrawer = true
            trimmed.equals(":END:", ignoreCase = true) -> inPropertiesDrawer = false
            inPropertiesDrawer -> {
                val match = PropertyLineRegex.matchEntire(trimmed)
                if (match != null) {
                    val key = match.groupValues[1]
                    val value = match.groupValues[2].trim()
                    properties[key] = value
                }
            }
        }
    }
    return properties
}

/**
 * Extracts file-level tags from `#+filetags:` keyword.
 * Format: `#+filetags: :tag1:tag2:tag3:`
 */
private fun extractFileTags(metadataLines: List<String>): List<String> {
    for (line in metadataLines) {
        val match = FileTagsRegex.matchEntire(line.trim())
        if (match != null) {
            return parseTags(match.groupValues[1])
        }
    }
    return emptyList()
}

/**
 * Parses a colon-delimited tag string into a list of tags.
 * Input: `:tag1:tag2:tag3:` or `tag1:tag2:tag3`
 * Output: ["tag1", "tag2", "tag3"]
 */
private fun parseTags(tagString: String): List<String> {
    return tagString
        .split(":")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

/**
 * Parses a heading line and extracts level, title, and optional tags.
 * Returns Triple(level, title, tags) or Triple(null, null, emptyList()) if not a heading.
 */
private fun parseHeadingLine(line: String): Triple<Int?, String?, List<String>> {
    // Try matching heading with tags first
    val withTagsMatch = HeadingWithTagsRegex.matchEntire(line)
    if (withTagsMatch != null) {
        val level = withTagsMatch.groupValues[1].length
        val title = withTagsMatch.groupValues[2].trim()
        val tags = parseTags(withTagsMatch.groupValues[3])
        return Triple(level, title, tags)
    }
    // Try matching heading without tags
    val headingMatch = HeadingRegex.matchEntire(line)
    if (headingMatch != null) {
        val level = headingMatch.groupValues[1].length
        val title = headingMatch.groupValues[2].trim()
        return Triple(level, title, emptyList())
    }
    return Triple(null, null, emptyList())
}
