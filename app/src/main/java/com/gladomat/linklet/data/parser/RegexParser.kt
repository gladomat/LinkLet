package com.gladomat.linklet.data.parser

import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteLink

/**
 * MVP regex-based parser extracting note titles and file links.
 */
class RegexParser : IParser {

    private val titleRegex = Regex(
        pattern = """^#\+title:\s*(.+)$""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val linkRegex = Regex(
        pattern = """\[\[file:([^\]]+)\](?:\[([^\]]+)\])?\]""",
        option = RegexOption.IGNORE_CASE,
    )

    override fun parse(content: String, path: String): Note {
        val rawTitle = titleRegex.find(content)?.groupValues?.getOrNull(1)?.trim()
        val title = rawTitle?.takeIf { it.isNotEmpty() } ?: path

        val links = linkRegex.findAll(content).map { matchResult ->
            val target = matchResult.groupValues.getOrNull(1)?.trim().orEmpty()
            val alias = matchResult.groupValues.getOrNull(2)?.trim().takeUnless { it.isNullOrEmpty() }
            NoteLink(
                fromId = NoteId(path),
                toPath = target,
                label = alias,
            )
        }.toList()

        return Note(
            id = NoteId(path),
            title = title,
            content = content,
            links = links,
        )
    }
}
