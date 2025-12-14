package com.gladomat.linklet.data.parser

import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.model.LinkTarget

/**
 * MVP regex-based parser extracting note titles, properties, filetags, and links.
 */
class RegexParser : IParser {

    private val titleRegex = Regex(
        pattern = """^#\+title:\s*(.+)$""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val idPropertyRegex = Regex(
        pattern = """:ID:\s*([^\s]+)""",
        options = setOf(RegexOption.IGNORE_CASE),
    )
    private val linkRegex = Regex(
        pattern = """\[\[(file|id):([^\]]+)\](?:\[([^\]]+)\])?\]""",
        options = setOf(RegexOption.IGNORE_CASE),
    )
    /** Matches the entire :PROPERTIES: ... :END: drawer */
    private val propertiesDrawerRegex = Regex(
        pattern = """:PROPERTIES:\s*\n([\s\S]*?)\n\s*:END:""",
        options = setOf(RegexOption.IGNORE_CASE),
    )
    /** Matches individual property lines like ":KEY: value" */
    private val propertyLineRegex = Regex(
        pattern = """^\s*:([^:]+):\s*(.*)$""",
        options = setOf(RegexOption.MULTILINE),
    )
    /** Matches #+filetags: :tag1:tag2:tag3: */
    private val filetagsRegex = Regex(
        pattern = """^#\+filetags:\s*(.+)$""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

    override fun parse(content: String, path: String): Note {
        val rawTitle = titleRegex.find(content)?.groupValues?.getOrNull(1)?.trim()
        val title = rawTitle?.takeIf { it.isNotEmpty() } ?: path

        // Parse properties drawer
        val properties = parseProperties(content)
        val orgId = properties["ID"]

        // Parse filetags
        val fileTags = parseFileTags(content)

        // Parse links
        val links = linkRegex.findAll(content).mapNotNull { matchResult ->
            val type = matchResult.groupValues.getOrNull(1)?.lowercase()
            val targetValue = matchResult.groupValues.getOrNull(2)?.trim().orEmpty()
            if (type.isNullOrEmpty() || targetValue.isEmpty()) return@mapNotNull null
            val alias = matchResult.groupValues.getOrNull(3)?.trim().takeUnless { it.isNullOrEmpty() }
            val target = when (type) {
                "id" -> LinkTarget.Id(targetValue)
                else -> LinkTarget.Path(targetValue)
            }
            NoteLink(
                fromId = NoteId(path),
                target = target,
                label = alias,
            )
        }.toList()

        return Note(
            id = NoteId(path),
            title = title,
            content = content,
            links = links,
            orgId = orgId,
            properties = properties,
            fileTags = fileTags,
        )
    }

    /**
     * Parses the :PROPERTIES: drawer and returns a map of key-value pairs.
     */
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

    /**
     * Parses #+filetags: line and returns a list of tags.
     * Handles format like ":tag1:tag2:" or "tag1 tag2"
     */
    private fun parseFileTags(content: String): List<String> {
        val match = filetagsRegex.find(content) ?: return emptyList()
        val tagsValue = match.groupValues.getOrNull(1)?.trim() ?: return emptyList()
        
        // Handle :tag1:tag2: format
        return if (tagsValue.startsWith(":") || tagsValue.contains(":")) {
            tagsValue.split(":")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else {
            // Handle space-separated format
            tagsValue.split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}
