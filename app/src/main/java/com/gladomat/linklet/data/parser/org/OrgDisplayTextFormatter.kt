package com.gladomat.linklet.data.parser.org

import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.parser.org.BracketLinkTarget
import com.gladomat.linklet.data.parser.org.OrgLinkParsing

private val HeadingRegex = Regex("""^(\*{1,3})\s+(.*)$""")
private val BulletRegex = Regex("""^([+-])\s+(.*)$""")
private val NumberedRegex = Regex("""^(\d+)\.\s+(.*)$""")
private val UrlRegex = OrgLinkParsing.UrlRegex

/**
 * Builds the exact plain-text display output used by the Org note renderer.
 *
 * This is used by in-note search to compute match indices that stay correct after recomposition:
 * search runs against the same text that is displayed.
 */
fun buildOrgDisplayText(
    content: String,
    links: List<NoteLink>,
): String {
    val linkBuckets = buildLinkBuckets(links)
    val lines = content.split('\n')
    return buildString {
        lines.forEachIndexed { index, rawLine ->
            val parsedLine = parseLine(rawLine)
            appendFormattedLine(parsedLine, linkBuckets)
            if (index < lines.lastIndex) append("\n")
        }
    }
}

private fun StringBuilder.appendFormattedLine(
    line: ParsedLine,
    linkBuckets: MutableMap<LinkKey, ArrayDeque<String>>,
) {
    when (line) {
        ParsedLine.Blank -> Unit
        is ParsedLine.Heading -> appendInlineText(line.text, linkBuckets)
        is ParsedLine.Bullet -> {
            append("  ")
            append("•")
            append(" ")
            appendInlineText(line.text, linkBuckets)
        }
        is ParsedLine.Numbered -> {
            append("  ")
            append(line.index)
            append(".")
            append(" ")
            appendInlineText(line.text, linkBuckets)
        }
        is ParsedLine.Paragraph -> appendInlineText(line.text, linkBuckets)
    }
}

private fun StringBuilder.appendInlineText(
    text: String,
    linkBuckets: MutableMap<LinkKey, ArrayDeque<String>>,
) {
    if (text.isEmpty()) return
    val plain = StringBuilder()
    var index = 0
    while (index < text.length) {
        val linkMatch = OrgLinkParsing.findBracketLink(text, index)
        if (linkMatch != null) {
            flushPlain(plain)
            val displayText = linkMatch.description ?: linkMatch.rawTarget
            when (val target = OrgLinkParsing.parseBracketTarget(linkMatch.rawTarget)) {
                is BracketLinkTarget.Org -> {
                    val destination = linkBuckets[LinkKey(target.scheme, target.value)]?.removeFirstOrNull()
                    if (destination != null) {
                        append(displayText)
                    } else {
                        append(text.substring(linkMatch.range))
                    }
                }
                is BracketLinkTarget.External -> append(displayText)
                BracketLinkTarget.Unknown -> append(text.substring(linkMatch.range))
            }
            index = linkMatch.range.last + 1
            continue
        }

        val urlMatch = UrlRegex.find(text, index)
        if (urlMatch != null && urlMatch.range.first == index) {
            val rawUrl = urlMatch.value
            if (!rawUrl.startsWith("[[")) {
                flushPlain(plain)
                append(rawUrl)
                index = urlMatch.range.last + 1
                continue
            }
        }

        val delimiter = EmphasisDelimiter.fromChar(text[index])
        if (delimiter != null) {
            val closing = text.indexOf(delimiter.char, index + 1)
            if (closing > index + 1 && isValidEmphasisBoundary(text, index, closing)) {
                flushPlain(plain)
                val inner = text.substring(index + 1, closing)
                appendInlineText(inner, linkBuckets)
                index = closing + 1
                continue
            }
        }

        plain.append(text[index])
        index++
    }
    flushPlain(plain)
}

private fun isValidEmphasisBoundary(text: String, start: Int, end: Int): Boolean {
    val before = text.getOrNull(start - 1)
    val after = text.getOrNull(end + 1)
    return (before == null || before.isWhitespaceDelimiter()) &&
        (after == null || after.isWhitespaceDelimiter())
}

private fun Char.isWhitespaceDelimiter(): Boolean =
    this.isWhitespace() || this in listOf('.', ',', ':', ';', '-', '!', '?', ')', '(')

private fun StringBuilder.flushPlain(buffer: StringBuilder) {
    if (buffer.isNotEmpty()) {
        append(buffer.toString())
        buffer.clear()
    }
}

private fun buildLinkBuckets(links: List<NoteLink>): MutableMap<LinkKey, ArrayDeque<String>> {
    val buckets = mutableMapOf<LinkKey, ArrayDeque<String>>()
    links.forEach { link ->
        val key = link.toLinkKey() ?: return@forEach
        val destination = link.resolvedPath ?: return@forEach
        val deque = buckets.getOrPut(key) { ArrayDeque() }
        deque.addLast(destination)
    }
    return buckets
}

private data class LinkKey(val scheme: String, val value: String)

private fun NoteLink.toLinkKey(): LinkKey? =
    when (val target = target) {
        is LinkTarget.Path -> LinkKey("file", target.value)
        is LinkTarget.Id -> LinkKey("id", target.value)
    }

private sealed interface ParsedLine {
    data class Heading(val level: Int, val text: String) : ParsedLine
    data class Bullet(val text: String) : ParsedLine
    data class Numbered(val index: String, val text: String) : ParsedLine
    data class Paragraph(val text: String) : ParsedLine
    data object Blank : ParsedLine
}

private fun parseLine(rawLine: String): ParsedLine {
    if (rawLine.isBlank()) return ParsedLine.Blank
    val heading = HeadingRegex.matchEntire(rawLine.trim())
    if (heading != null) {
        val level = heading.groupValues[1].length.coerceIn(1, 3)
        val text = heading.groupValues[2].trim()
        return ParsedLine.Heading(level, text)
    }
    val bullet = BulletRegex.matchEntire(rawLine.trim())
    if (bullet != null) {
        val text = bullet.groupValues[2].trim()
        return ParsedLine.Bullet(text)
    }
    val numbered = NumberedRegex.matchEntire(rawLine.trim())
    if (numbered != null) {
        val number = numbered.groupValues[1]
        val text = numbered.groupValues[2].trim()
        return ParsedLine.Numbered(number, text)
    }
    return ParsedLine.Paragraph(rawLine.trimEnd())
}

private enum class EmphasisDelimiter(val char: Char) {
    Bold('*'),
    Italic('/'),
    Underline('_'),
    Verbatim('='),
    Code('~'),
    StrikeThrough('+');

    companion object {
        fun fromChar(char: Char): EmphasisDelimiter? = entries.firstOrNull { it.char == char }
    }
}
