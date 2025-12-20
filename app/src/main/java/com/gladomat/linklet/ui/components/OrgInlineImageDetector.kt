package com.gladomat.linklet.ui.components

private val OrgKeywordRegex = Regex("""^#\+([A-Za-z_]+):\s*(.*)$""")
private val OrgNoDescLinkRegex = Regex("""^\[\[([^\]]+)\]\]$""")
private val OrgTypedNoDescLinkRegex = Regex("""^\[\[(file|attachment):([^\]]+)\]\]$""", RegexOption.IGNORE_CASE)
private val OrgTypedDescLinkRegex = Regex("""^\[\[(file|attachment):([^\]]+?)\]\[([^\]]*)\]\]$""", RegexOption.IGNORE_CASE)

data class OrgInlineImageCandidate(
    val target: String,
    val scheme: String,
    val caption: String? = null,
    val name: String? = null,
    val attrs: Map<String, String> = emptyMap(),
)

/**
 * Detects Org "inline image" candidates.
 *
 * Baseline behavior (Org 12.7-ish):
 * - A candidate is a standalone image reference (no description), typically on its own line.
 * - CAPTION/NAME/ATTR_* lines may precede it within the same paragraph.
 */
fun detectInlineImageCandidate(paragraphText: String): OrgInlineImageCandidate? {
    val nonBlankLines = paragraphText.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (nonBlankLines.isEmpty()) return null

    val imageLine = nonBlankLines.last()
    val keywordLines = nonBlankLines.dropLast(1)
    if (keywordLines.any { !it.startsWith("#+", ignoreCase = true) }) return null

    val keywords = keywordLines.mapNotNull { line ->
        val match = OrgKeywordRegex.matchEntire(line) ?: return@mapNotNull null
        match.groupValues[1].uppercase() to match.groupValues[2].trim()
    }

    val caption = keywords.lastOrNull { it.first == "CAPTION" }?.second?.takeIf { it.isNotBlank() }
    val name = keywords.lastOrNull { it.first == "NAME" }?.second?.takeIf { it.isNotBlank() }

    val attrs = keywords
        .filter { it.first.startsWith("ATTR_") }
        .flatMap { (_, value) -> parseAttrPairs(value).entries }
        .associate { it.key to it.value }

    val typed = OrgTypedNoDescLinkRegex.matchEntire(imageLine)
    if (typed != null) {
        val scheme = typed.groupValues[1].lowercase()
        val target = typed.groupValues[2].trim()
        if (!isImagePath(target)) return null
        return OrgInlineImageCandidate(
            target = target,
            scheme = scheme,
            caption = caption,
            name = name,
            attrs = attrs,
        )
    }

    val typedDesc = OrgTypedDescLinkRegex.matchEntire(imageLine)
    if (typedDesc != null) {
        val scheme = typedDesc.groupValues[1].lowercase()
        val target = typedDesc.groupValues[2].trim()
        val bracketCaption = typedDesc.groupValues[3].trim().takeIf { it.isNotBlank() }
        if (!isImagePath(target)) return null
        return OrgInlineImageCandidate(
            target = target,
            scheme = scheme,
            caption = caption ?: bracketCaption,
            name = name,
            attrs = attrs,
        )
    }

    val untyped = OrgNoDescLinkRegex.matchEntire(imageLine)
    if (untyped != null) {
        val rawTarget = untyped.groupValues[1].trim()
        if (!isImagePath(rawTarget)) return null
        return OrgInlineImageCandidate(
            target = rawTarget,
            scheme = "file",
            caption = caption,
            name = name,
            attrs = attrs,
        )
    }

    if (keywordLines.isNotEmpty()) return null
    if (!isImagePath(imageLine)) return null
    return OrgInlineImageCandidate(
        target = imageLine,
        scheme = "file",
        caption = null,
        name = null,
        attrs = emptyMap(),
    )
}

private fun isImagePath(path: String): Boolean {
    val lower = path.trim().lowercase()
    return lower.endsWith(".png") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".gif") ||
        lower.endsWith(".webp") ||
        lower.endsWith(".bmp") ||
        lower.endsWith(".heic") ||
        lower.endsWith(".heif") ||
        lower.endsWith(".avif") ||
        lower.endsWith(".svg")
}

private val AttrPairRegex = Regex(""":([A-Za-z0-9_-]+)\s+([^:]+?)(?=\s+:[A-Za-z0-9_-]+\s+|$)""")

private fun parseAttrPairs(raw: String): Map<String, String> {
    if (!raw.contains(':')) return emptyMap()
    return AttrPairRegex.findAll(raw).associate { match ->
        match.groupValues[1].lowercase() to match.groupValues[2].trim()
    }
}
