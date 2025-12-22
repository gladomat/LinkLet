package com.gladomat.linklet.data.parser.org

data class BracketLink(
    val rawTarget: String,
    val description: String?,
    val range: IntRange,
)

sealed interface BracketLinkTarget {
    data class Org(val scheme: String, val value: String) : BracketLinkTarget
    data class External(val rawUrl: String) : BracketLinkTarget
    data object Unknown : BracketLinkTarget
}

object OrgLinkParsing {
    val UrlRegex = Regex(
        """((?:https?://|www\.)[^\s]+|[A-Za-z0-9._%+-]+(?:\.[A-Za-z0-9.-]+)+/[^\s]+|[A-Za-z0-9.-]+\.[A-Za-z]{2,})""",
    )

    private val BracketLinkRegex = Regex("""\[\[([^\]]+)\](?:\[([^\]]+)\])?\]""")

    fun findBracketLink(text: String, startIndex: Int): BracketLink? {
        val match = BracketLinkRegex.find(text, startIndex) ?: return null
        if (match.range.first != startIndex) return null
        val rawTarget = match.groupValues[1].trim()
        val description = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
        return BracketLink(rawTarget = rawTarget, description = description, range = match.range)
    }

    fun parseBracketTarget(rawTarget: String): BracketLinkTarget {
        val lower = rawTarget.lowercase()
        return when {
            lower.startsWith("file:") -> BracketLinkTarget.Org("file", rawTarget.drop(5))
            lower.startsWith("id:") -> BracketLinkTarget.Org("id", rawTarget.drop(3))
            UrlRegex.matches(rawTarget) -> BracketLinkTarget.External(rawTarget)
            else -> BracketLinkTarget.Unknown
        }
    }
}
