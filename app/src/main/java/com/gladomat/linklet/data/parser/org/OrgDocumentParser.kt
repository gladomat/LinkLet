package com.gladomat.linklet.data.parser.org

private val HeadingRegex = Regex("""^(\*{1,})\s+(.*)$""")
private val HeadingWithTagsRegex = Regex("""^(\*{1,})\s+(.*?)\s*(:(?:[a-zA-Z0-9_@#%]+:)+)\s*$""")
private val FileTagsRegex = Regex("""^#\+filetags:\s*(.*)$""", RegexOption.IGNORE_CASE)
private val PropertyLineRegex = Regex("""^:([A-Za-z_-]+):\s*(.*)$""")
private val DrawerStartRegex = Regex("""^:([A-Za-z0-9_-]+):\s*$""")
private val DrawerEndRegex = Regex("""^:END:\s*$""", RegexOption.IGNORE_CASE)
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
    val properties: Map<String, String>,
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

private fun OrgNode.toSection(): OrgSection {
    val blocks = parseContentToBlocks(contentLines)
    val drawerProps = blocks.firstOrNull { it is OrgBlock.Drawer && it.name == "PROPERTIES" }
        ?.let { (it as OrgBlock.Drawer).properties }
        ?: emptyMap()
    return OrgSection(
        id = id,
        level = level,
        title = title,
        tags = tags,
        properties = drawerProps,
        body = contentLines.joinToString("\n").trim(),
        blocks = blocks,
        children = children.map { it.toSection() },
    )
}

private fun parseDrawerProperties(lines: List<String>): Map<String, String> {
    val properties = mutableMapOf<String, String>()
    for (line in lines) {
        val match = PropertyLineRegex.matchEntire(line.trim()) ?: continue
        properties[match.groupValues[1].uppercase()] = match.groupValues[2]
    }
    return properties
}

// ============================================================================
// Block Parsing State Machine
// ============================================================================

private sealed interface ParseState {
    data object Idle : ParseState
    data class InBlock(val type: String, val langOrParams: String?, val lines: MutableList<String>) : ParseState
    data class InTable(val rows: MutableList<List<String>>, val separatorAfterRow: Int?) : ParseState
    data class InDrawer(val name: String, val lines: MutableList<String>) : ParseState
}

/**
 * Parses a list of content lines into structured [OrgBlock]s.
 * Uses a state machine to detect tables, source blocks, and other Org blocks.
 */
fun parseContentToBlocks(lines: List<String>): List<OrgBlock> {
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

        val drawerMatch = DrawerStartRegex.matchEntire(line.trim())
        if (drawerMatch != null) {
            val name = drawerMatch.groupValues[1].uppercase()
            if (name != "END") {
                flushParagraph()
                state = ParseState.InDrawer(name, mutableListOf())
                return
            }
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
                        state = currentState.copy(separatorAfterRow = currentState.rows.size - 1)
                    }
                    TableRowRegex.matches(line) -> {
                        currentState.rows.add(parseTableRow(line))
                    }
                    else -> {
                        flushTable(currentState)
                        state = ParseState.Idle
                        handleIdleLine(line)
                    }
                }
            }

            is ParseState.InDrawer -> {
                if (DrawerEndRegex.matches(line.trim())) {
                    blocks.add(
                        OrgBlock.Drawer(
                            name = currentState.name,
                            lines = currentState.lines.toList(),
                            properties = parseDrawerProperties(currentState.lines),
                        ),
                    )
                    state = ParseState.Idle
                } else {
                    currentState.lines.add(line)
                }
            }
        }
    }

    when (val finalState = state) {
        is ParseState.Idle -> flushParagraph()
        is ParseState.InBlock -> {
            val content = finalState.lines.joinToString("\n")
            blocks.add(OrgBlock.UnknownBlock(finalState.type, content))
        }
        is ParseState.InTable -> flushTable(finalState)
        is ParseState.InDrawer -> {
            // Unclosed drawer: replay as paragraph text so no content is lost
            if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append("\n")
            paragraphBuffer.append(":${finalState.name}:")
            finalState.lines.forEach { l ->
                paragraphBuffer.append("\n")
                paragraphBuffer.append(l)
            }
            flushParagraph()
        }
    }

    return blocks
}

private fun parseTableRow(line: String): List<String> =
    line.trim().split("|")
        .drop(1)
        .dropLast(1)
        .map { it.trim() }

private fun createBlockFromType(blockType: String, content: String, params: String?): OrgBlock {
    return when (blockType) {
        "SRC" -> {
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

private fun extractProperties(metadataLines: List<String>): Map<String, String> {
    val properties = mutableMapOf<String, String>()
    var inDrawer = false
    metadataLines.forEach { line ->
        when {
            line.trim().equals(":PROPERTIES:", ignoreCase = true) -> inDrawer = true
            line.trim().equals(":END:", ignoreCase = true) -> inDrawer = false
            inDrawer -> {
                val match = PropertyLineRegex.matchEntire(line.trim())
                if (match != null) {
                    properties[match.groupValues[1]] = match.groupValues[2]
                }
            }
        }
    }
    return properties
}

private fun extractFileTags(metadataLines: List<String>): List<String> {
    val tagsLine = metadataLines.firstOrNull { FileTagsRegex.matches(it.trim()) } ?: return emptyList()
    val match = FileTagsRegex.matchEntire(tagsLine.trim()) ?: return emptyList()
    val raw = match.groupValues[1]
    return raw
        .split(':')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun parseHeadingLine(trimmed: String): Triple<Int?, String?, List<String>> {
    val withTags = HeadingWithTagsRegex.matchEntire(trimmed)
    if (withTags != null) {
        val level = withTags.groupValues[1].length
        val title = withTags.groupValues[2].trim()
        val tags = withTags.groupValues[3]
            .split(':')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return Triple(level, title, tags)
    }
    val heading = HeadingRegex.matchEntire(trimmed)
    if (heading != null) {
        val level = heading.groupValues[1].length
        val title = heading.groupValues[2].trim()
        return Triple(level, title, emptyList())
    }
    return Triple(null, null, emptyList())
}
