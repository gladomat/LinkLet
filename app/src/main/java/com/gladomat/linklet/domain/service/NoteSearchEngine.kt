package com.gladomat.linklet.domain.service

/**
 * Search options for in-note search.
 *
 * Note: [useRegex] changes the semantics of [query]. When `true`, [query] is treated as a Kotlin regex pattern.
 */
data class SearchOptions(
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val useRegex: Boolean = false,
)

/**
 * A match range within a specific searchable block.
 *
 * [start] is inclusive, [end] is exclusive (Kotlin substring semantics).
 */
data class MatchRange(
    val start: Int,
    val end: Int,
    val blockId: String,
)

data class SearchBlock(
    val blockId: String,
    val text: String,
)

/**
 * Pure search engine used by the ViewModel.
 *
 * It operates on a list of independent blocks with local indices to avoid global-index mismatch
 * when the UI recomposes or when rendered blocks are collapsed/expanded.
 */
object NoteSearchEngine {

    fun search(
        blocks: List<SearchBlock>,
        query: String,
        options: SearchOptions,
    ): Result<List<MatchRange>> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return Result.success(emptyList())
        return if (options.useRegex) {
            searchRegex(blocks, trimmedQuery, options)
        } else {
            Result.success(searchLiteral(blocks, trimmedQuery, options))
        }
    }

    private fun searchLiteral(
        blocks: List<SearchBlock>,
        query: String,
        options: SearchOptions,
    ): List<MatchRange> {
        if (query.isEmpty()) return emptyList()
        val matches = mutableListOf<MatchRange>()
        blocks.forEach { block ->
            val haystack = block.text
            if (haystack.isEmpty()) return@forEach
            var index = 0
            while (index < haystack.length) {
                val matchIndex = haystack.indexOf(query, startIndex = index, ignoreCase = !options.caseSensitive)
                if (matchIndex < 0) break
                val endExclusive = matchIndex + query.length
                if (!options.wholeWord || isWholeWordMatch(haystack, matchIndex, endExclusive)) {
                    matches.add(
                        MatchRange(
                            start = matchIndex,
                            end = endExclusive,
                            blockId = block.blockId,
                        ),
                    )
                }
                index = endExclusive.coerceAtLeast(matchIndex + 1)
            }
        }
        return matches
    }

    private fun searchRegex(
        blocks: List<SearchBlock>,
        pattern: String,
        options: SearchOptions,
    ): Result<List<MatchRange>> {
        val regex = try {
            val finalPattern = if (options.wholeWord) "\\b(?:$pattern)\\b" else pattern
            if (options.caseSensitive) {
                Regex(finalPattern)
            } else {
                Regex(finalPattern, RegexOption.IGNORE_CASE)
            }
        } catch (e: IllegalArgumentException) {
            return Result.failure(e)
        }

        val matches = mutableListOf<MatchRange>()
        blocks.forEach { block ->
            regex.findAll(block.text).forEach { match ->
                val range = match.range
                val start = range.first
                val endExclusive = range.last + 1
                if (start < endExclusive) {
                    matches.add(
                        MatchRange(
                            start = start,
                            end = endExclusive,
                            blockId = block.blockId,
                        ),
                    )
                }
            }
        }
        return Result.success(matches)
    }

    private fun isWholeWordMatch(text: String, start: Int, endExclusive: Int): Boolean {
        val before = text.getOrNull(start - 1)
        val after = text.getOrNull(endExclusive)
        return !before.isWordChar() && !after.isWordChar()
    }

    private fun Char?.isWordChar(): Boolean {
        val c = this ?: return false
        return c.isLetterOrDigit() || c == '_'
    }
}

