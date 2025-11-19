package com.gladomat.linklet.data.parser

import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.model.LinkTarget

/**
 * MVP regex-based parser extracting note titles and file links.
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

    override fun parse(content: String, path: String): Note {
        val rawTitle = titleRegex.find(content)?.groupValues?.getOrNull(1)?.trim()
        val title = rawTitle?.takeIf { it.isNotEmpty() } ?: path
        val orgId = idPropertyRegex.find(content)?.groupValues?.getOrNull(1)?.trim()

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
        )
    }
}
