package com.gladomat.linklet.ui.components

private val HeadingRegex = Regex("""^(\*{1,})\s+(.*)$""")
private val HeadingWithTagsRegex = Regex("""^(\*{1,})\s+(.*?)\s*(:(?:[a-zA-Z0-9_@#%]+:)+)\s*$""")
private val FileTagsRegex = Regex("""^#\+filetags:\s*(.*)$""", RegexOption.IGNORE_CASE)
private val PropertyLineRegex = Regex("""^:([A-Za-z_-]+):\s*(.*)$""")

data class OrgDocument(
    val metadata: List<String>,
    val properties: Map<String, String>,
    val fileTags: List<String>,
    val preface: String,
    val sections: List<OrgSection>,
)

data class OrgSection(
    val id: String,
    val level: Int,
    val title: String,
    val tags: List<String>,
    val body: String,
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
            val level = headingLevel.coerceIn(1, 6)
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
    return OrgDocument(
        metadata = metadataLines,
        properties = properties,
        fileTags = fileTags,
        preface = preface,
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
