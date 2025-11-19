package com.gladomat.linklet.ui.components

private val HeadingRegex = Regex("""^(\*{1,})\s+(.*)$""")

data class OrgDocument(
    val metadata: List<String>,
    val preface: String,
    val sections: List<OrgSection>,
)

data class OrgSection(
    val id: String,
    val level: Int,
    val title: String,
    val body: String,
    val children: List<OrgSection>,
)

fun parseOrgDocument(content: String): OrgDocument {
    val lines = content.lines()
    val (metadataLines, bodyStart) = extractMetadata(lines)
    val bodyLines = lines.drop(bodyStart)
    val root = OrgNode(
        id = "root",
        level = 0,
        title = "",
        contentLines = mutableListOf(),
        children = mutableListOf(),
    )
    val stack = ArrayDeque<OrgNode>()
    stack.add(root)
    var headingCounter = 0

    bodyLines.forEach { rawLine ->
        val trimmed = rawLine.trimEnd()
        val headingMatch = HeadingRegex.matchEntire(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length.coerceIn(1, 3)
            val title = headingMatch.groupValues[2].trim()
            val node = OrgNode(
                id = "heading-${headingCounter++}",
                level = level,
                title = title,
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
    return OrgDocument(
        metadata = metadataLines,
        preface = preface,
        sections = root.children.map { it.toSection() },
    )
}

private data class OrgNode(
    val id: String,
    val level: Int,
    val title: String,
    val contentLines: MutableList<String>,
    val children: MutableList<OrgNode>,
)

private fun OrgNode.toSection(): OrgSection =
    OrgSection(
        id = id,
        level = level,
        title = title,
        body = contentLines.joinToString("\n").trim(),
        children = children.map { it.toSection() },
    )

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
