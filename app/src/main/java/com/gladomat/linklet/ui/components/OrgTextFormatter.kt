package com.gladomat.linklet.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.parser.org.BracketLinkTarget
import com.gladomat.linklet.data.parser.org.OrgLinkParsing

private val HeadingRegex = Regex("""^(\*{1,3})\s+(.*)$""")
private val BulletRegex = Regex("""^([+-])\s+(.*)$""")
private val NumberedRegex = Regex("""^(\d+)\.\s+(.*)$""")
private val UrlRegex = OrgLinkParsing.UrlRegex

const val ORG_LINK_ANNOTATION_TAG = "org-link"
const val ORG_EXTERNAL_LINK_ANNOTATION_TAG = "org-external-link"

private val DefaultMatchHighlight = Color(0xFFFFF59D) // light yellow
private val DefaultActiveHighlight = Color(0xFFFFCC80) // orange

data class OrgTextPalette(
    val headingLevel1: Color,
    val headingLevel2: Color,
    val headingLevel3: Color,
    val bulletColor: Color,
    val linkColor: Color,
    val codeBackground: Color,
    val codeTextColor: Color,
    val verbatimBackground: Color,
    val verbatimTextColor: Color,
    val searchHighlightBackground: Color,
    val activeSearchHighlightBackground: Color,
)

fun buildOrgContentAnnotatedString(
    content: String,
    links: List<NoteLink>,
    palette: OrgTextPalette,
): AnnotatedString {
    val linkBuckets = buildLinkBuckets(links)
    val lines = content.split('\n')
    return buildAnnotatedString {
        lines.forEachIndexed { index, rawLine ->
            val parsedLine = parseLine(rawLine)
            appendFormattedLine(parsedLine, linkBuckets, palette)
            if (index < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

/**
 * Applies highlight spans over [text] using the default match/active styles.
 */
fun applyHighlights(
    text: String,
    ranges: List<IntRange>,
    activeRange: IntRange?,
): AnnotatedString = applyHighlights(
    base = AnnotatedString(text),
    ranges = ranges,
    activeRange = activeRange,
    matchBackground = DefaultMatchHighlight,
    activeBackground = DefaultActiveHighlight,
)

/**
 * Applies highlight spans over an existing [AnnotatedString] while preserving all existing spans/annotations.
 *
 * [ranges] and [activeRange] must be expressed in the same index space as [base.text].
 */
fun applyHighlights(
    base: AnnotatedString,
    ranges: List<IntRange>,
    activeRange: IntRange?,
    matchBackground: Color,
    activeBackground: Color,
): AnnotatedString {
    if (ranges.isEmpty() && activeRange == null) return base
    val normalized = normalizeRanges(base.text.length, ranges)
    val normalizedActive = activeRange?.let { clampRange(base.text.length, it) }
    if (normalized.isEmpty() && normalizedActive == null) return base

    return buildAnnotatedString {
        append(base)
        val matchStyle = SpanStyle(background = matchBackground)
        val activeStyle = SpanStyle(background = activeBackground, fontWeight = FontWeight.Bold)
        normalized.forEach { range ->
            addStyle(matchStyle, range.first, range.last + 1)
        }
        if (normalizedActive != null) {
            addStyle(activeStyle, normalizedActive.first, normalizedActive.last + 1)
        }
    }
}

private fun AnnotatedString.Builder.appendFormattedLine(
    line: ParsedLine,
    linkBuckets: MutableMap<LinkKey, ArrayDeque<String>>,
    palette: OrgTextPalette,
) {
    when (line) {
        ParsedLine.Blank -> {
            // no-op, newline handled by caller
        }

        is ParsedLine.Heading -> {
            val style = SpanStyle(
                color = headingColorFor(line.level, palette),
                fontWeight = FontWeight.Bold,
            )
            pushStyle(style)
            appendInlineText(line.text, linkBuckets, palette)
            pop()
        }

        is ParsedLine.Bullet -> {
            append("  ")
            appendBulletPrefix("•", palette)
            append(" ")
            appendInlineText(line.text, linkBuckets, palette)
        }

        is ParsedLine.Numbered -> {
            append("  ")
            appendBulletPrefix("${line.index}.", palette)
            append(" ")
            appendInlineText(line.text, linkBuckets, palette)
        }

        is ParsedLine.Paragraph -> {
            appendInlineText(line.text, linkBuckets, palette)
        }
    }
}

private fun AnnotatedString.Builder.appendBulletPrefix(text: String, palette: OrgTextPalette) {
    pushStyle(
        SpanStyle(
            color = palette.bulletColor,
            fontWeight = FontWeight.SemiBold,
        ),
    )
    append(text)
    pop()
}

private fun AnnotatedString.Builder.appendInlineText(
    text: String,
    linkBuckets: MutableMap<LinkKey, ArrayDeque<String>>,
    palette: OrgTextPalette,
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
                        pushStringAnnotation(tag = ORG_LINK_ANNOTATION_TAG, annotation = destination)
                        pushStyle(
                            SpanStyle(
                                color = palette.linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        append(displayText)
                        pop()
                        pop()
                    } else {
                        append(text.substring(linkMatch.range))
                    }
                }
                is BracketLinkTarget.External -> {
                    pushStringAnnotation(tag = ORG_EXTERNAL_LINK_ANNOTATION_TAG, annotation = normalizeUrl(target.rawUrl))
                    pushStyle(
                        SpanStyle(
                            color = palette.linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontStyle = FontStyle.Italic,
                        ),
                    )
                    append(displayText)
                    pop()
                    pop()
                }
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
                val destination = normalizeUrl(rawUrl)
                pushStringAnnotation(tag = ORG_EXTERNAL_LINK_ANNOTATION_TAG, annotation = destination)
                pushStyle(
                    SpanStyle(
                        color = palette.linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontStyle = FontStyle.Italic,
                    ),
                )
                append(rawUrl)
                pop()
                pop()
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
                pushStyle(delimiter.style(palette))
                appendInlineText(inner, linkBuckets, palette)
                pop()
                index = closing + 1
                continue
            }
        }

        plain.append(text[index])
        index++
    }
    flushPlain(plain)
}

private fun headingColorFor(level: Int, palette: OrgTextPalette): Color =
    when (level) {
        1 -> palette.headingLevel1
        2 -> palette.headingLevel2
        else -> palette.headingLevel3
    }

private fun isValidEmphasisBoundary(text: String, start: Int, end: Int): Boolean {
    val before = text.getOrNull(start - 1)
    val after = text.getOrNull(end + 1)
    return (before == null || before.isWhitespaceDelimiter()) &&
        (after == null || after.isWhitespaceDelimiter())
}

private fun Char.isWhitespaceDelimiter(): Boolean =
    this.isWhitespace() || this in listOf('.', ',', ':', ';', '-', '!', '?', ')', '(')

private fun AnnotatedString.Builder.flushPlain(buffer: StringBuilder) {
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
    Bold('*') {
        override fun style(palette: OrgTextPalette): SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)
    },
    Italic('/') {
        override fun style(palette: OrgTextPalette): SpanStyle = SpanStyle(fontStyle = FontStyle.Italic)
    },
    Underline('_') {
        override fun style(palette: OrgTextPalette): SpanStyle = SpanStyle(textDecoration = TextDecoration.Underline)
    },
    Verbatim('=') {
        override fun style(palette: OrgTextPalette): SpanStyle = SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = palette.verbatimBackground,
            color = palette.verbatimTextColor,
        )
    },
    Code('~') {
        override fun style(palette: OrgTextPalette): SpanStyle = SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = palette.codeBackground,
            color = palette.codeTextColor,
        )
    },
    StrikeThrough('+') {
        override fun style(palette: OrgTextPalette): SpanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
    };

    abstract fun style(palette: OrgTextPalette): SpanStyle

    companion object {
        fun fromChar(char: Char): EmphasisDelimiter? = entries.firstOrNull { it.char == char }
    }
}

private fun normalizeUrl(raw: String): String =
    when {
        raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> raw
        raw.startsWith("www.", ignoreCase = true) -> "https://$raw"
        else -> "https://$raw"
    }

private fun normalizeRanges(length: Int, ranges: List<IntRange>): List<IntRange> {
    if (length <= 0) return emptyList()
    val sorted = ranges
        .mapNotNull { clampRange(length, it) }
        .sortedBy { it.first }

    val merged = mutableListOf<IntRange>()
    sorted.forEach { next ->
        val last = merged.lastOrNull()
        if (last == null) {
            merged += next
            return@forEach
        }
        if (next.first <= last.last) {
            // Enforce non-overlapping ranges by merging.
            merged[merged.lastIndex] = last.first..maxOf(last.last, next.last)
        } else {
            merged += next
        }
    }
    return merged
}

private fun clampRange(length: Int, range: IntRange): IntRange? {
    if (length <= 0) return null
    val start = range.first.coerceIn(0, length)
    val endInclusive = range.last.coerceIn(-1, length - 1)
    if (start > endInclusive) return null
    return start..endInclusive
}
