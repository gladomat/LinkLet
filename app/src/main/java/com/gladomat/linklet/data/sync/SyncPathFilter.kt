package com.gladomat.linklet.data.sync

/**
 * Decides which vault paths participate in sync.
 *
 * By design, all files under the vault root sync (there is no file-extension allowlist) —
 * only a small built-in junk blocklist plus an optional user-supplied [SyncIgnoreRules] are
 * excluded. [ignoreRules] is loaded once per sync run by [SyncEngine] from an optional
 * `.syncignore` file at the vault root and applied here for the duration of that run.
 */
object SyncPathFilter {
    private val blockedFileNames = setOf(
        ".DS_Store",
        "Thumbs.db",
        "desktop.ini",
        "__MACOSX",
        ".git",
        ".trash",
    )

    private val blockedDirectoryNames = setOf(
        // Emacs/Org LaTeX preview cache.
        "ltximg",
    )

    @Volatile
    var ignoreRules: SyncIgnoreRules = SyncIgnoreRules.EMPTY

    fun shouldInclude(path: String): Boolean = isNameAllowed(path)

    /**
     * Whether a directory should be descended into during remote/local tree traversal.
     * Directories never have file extensions, so this shares [isNameAllowed]'s logic rather
     * than any extension check — pruning by extension here would drop every subfolder
     * (e.g. org-attach/image folders) from being discovered at all.
     */
    fun isDirectoryTraversable(path: String): Boolean = isNameAllowed(path)

    private fun isNameAllowed(path: String): Boolean {
        val segments = normalizedSegments(path)
        if (segments.isEmpty()) return false
        if (segments.any { it.startsWith('.') || it.startsWith('_') }) return false
        if (segments.any { blockedFileNames.contains(it) }) return false
        if (segments.any { segment -> blockedDirectoryNames.any { it.equals(segment, ignoreCase = true) } }) return false
        if (ignoreRules.matches(path)) return false
        return true
    }

    private fun normalizedSegments(path: String): List<String> =
        path.trim('/').split('/').filter { it.isNotEmpty() }
}
