package com.gladomat.linklet.data.sync

/**
 * User-editable exclude rules loaded from an optional `.syncignore` file at the vault root.
 *
 * Gitignore-lite syntax:
 *  - `#` starts a comment; blank lines are skipped.
 *  - `*` matches any run of characters within one path segment (not `/`).
 *  - `**` matches any run of characters across segments (including `/`).
 *  - `?` matches a single non-`/` character.
 *  - A pattern containing `/` anywhere but the end is root-anchored (matches from the vault
 *    root only). A pattern with no other `/` matches the name at any depth.
 *  - A trailing `/` restricts the pattern to directories (and everything under them).
 *  - `!`-negation is not supported.
 */
class SyncIgnoreRules private constructor(private val patterns: List<Regex>) {

    fun matches(path: String): Boolean {
        val normalized = path.trim('/')
        if (normalized.isEmpty()) return false
        return patterns.any { it.matches(normalized) }
    }

    companion object {
        val EMPTY = SyncIgnoreRules(emptyList())

        fun parse(text: String): SyncIgnoreRules {
            val patterns = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { compile(it) }
                .toList()
            return if (patterns.isEmpty()) EMPTY else SyncIgnoreRules(patterns)
        }

        private fun compile(rawPattern: String, forceDirOnly: Boolean = false): Regex? {
            // A leading "**/" means "at any depth", which is already the default for a
            // slash-less pattern once anchoring is resolved below — so strip it and recurse.
            if (rawPattern.startsWith("**/")) return compile(rawPattern.removePrefix("**/"), forceDirOnly)
            // A trailing "/**" means "the directory and everything under it".
            if (rawPattern.endsWith("/**")) return compile(rawPattern.removeSuffix("/**"), forceDirOnly = true)

            var pattern = rawPattern
            val dirOnly = forceDirOnly || pattern.endsWith("/")
            if (pattern.endsWith("/")) pattern = pattern.dropLast(1)
            if (pattern.isEmpty()) return null

            val anchored = pattern.startsWith("/") || pattern.dropLast(1).contains("/")
            if (pattern.startsWith("/")) pattern = pattern.drop(1)
            if (pattern.isEmpty()) return null

            val body = globToRegexBody(pattern)
            val suffix = if (dirOnly) "(/.*)?" else ""
            val regexText = if (anchored) {
                "^$body$suffix$"
            } else {
                "^(.*/)?$body$suffix$"
            }
            return runCatching { Regex(regexText) }.getOrNull()
        }

        private fun globToRegexBody(glob: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < glob.length) {
                val c = glob[i]
                when {
                    glob.startsWith("**", i) -> {
                        sb.append(".*")
                        i += 2
                    }
                    c == '*' -> {
                        sb.append("[^/]*")
                        i++
                    }
                    c == '?' -> {
                        sb.append("[^/]")
                        i++
                    }
                    else -> {
                        sb.append(Regex.escape(c.toString()))
                        i++
                    }
                }
            }
            return sb.toString()
        }
    }
}
