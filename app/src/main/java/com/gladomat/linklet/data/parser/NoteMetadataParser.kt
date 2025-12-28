package com.gladomat.linklet.data.parser

data class NoteMetadata(
    val title: String,
    val orgId: String?,
    val fileTags: List<String>,
)

object NoteMetadataParser {
    private val titleRegex = Regex(
        pattern = """^#\+title:\s*(.+)$""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val idPropertyRegex = Regex(
        pattern = """^[ \t]*:ID:\s*(\S+)\s*$""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val propertiesDrawerRegex = Regex(
        pattern = """:PROPERTIES:\s*\r?\n([\s\S]*?)\r?\n\s*:END:""",
        options = setOf(RegexOption.IGNORE_CASE),
    )
    private val propertyLineRegex = Regex(
        pattern = """^\s*:([^:]+):\s*(.*)$""",
        options = setOf(RegexOption.MULTILINE),
    )
    private val filetagsRegex = Regex(
        pattern = """^#\+filetags:\s*(.*)$""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

    fun parse(content: String, path: String): NoteMetadata {
        val rawTitle = titleRegex.find(content)?.groupValues?.getOrNull(1)?.trim()
        val title = rawTitle?.takeIf { it.isNotEmpty() } ?: path

        val properties = parseProperties(content)
        val fallbackId = idPropertyRegex.find(content)?.groupValues?.getOrNull(1)?.trim()
        val mergedProperties = if (!fallbackId.isNullOrEmpty() && properties["ID"].isNullOrEmpty()) {
            properties + ("ID" to fallbackId)
        } else {
            properties
        }
        val orgId = mergedProperties["ID"]
        val fileTags = parseFileTags(content)

        return NoteMetadata(
            title = title,
            orgId = orgId,
            fileTags = fileTags,
        )
    }

    private fun parseProperties(content: String): Map<String, String> {
        val drawerMatch = propertiesDrawerRegex.find(content) ?: return emptyMap()
        val drawerContent = drawerMatch.groupValues.getOrNull(1) ?: return emptyMap()

        return propertyLineRegex.findAll(drawerContent)
            .mapNotNull { match ->
                val key = match.groupValues.getOrNull(1)?.trim()?.uppercase()
                val value = match.groupValues.getOrNull(2)?.trim()
                if (key.isNullOrEmpty()) null else key to (value ?: "")
            }
            .toMap()
    }

    private fun parseFileTags(content: String): List<String> {
        val match = filetagsRegex.find(content) ?: return emptyList()
        val tagsValue = match.groupValues.getOrNull(1)?.trim() ?: return emptyList()
        if (tagsValue.isBlank()) return emptyList()

        val rawTags = if (tagsValue.contains(":")) {
            tagsValue.trim(':').split(":")
        } else {
            tagsValue.split(Regex("\\s+"))
        }

        return rawTags.asSequence()
            .map(::normalizeTag)
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private fun normalizeTag(tag: String): String =
        tag.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "")
}
