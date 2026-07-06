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
    // Read-only from the outside — reused by the .syncignore editor's "always excluded" info
    // card so that UI text can't drift from what actually gets filtered here.
    val blockedFileNames = setOf(
        ".DS_Store",
        "Thumbs.db",
        "desktop.ini",
        "__MACOSX",
        ".git",
        ".trash",
    )

    val blockedDirectoryNames = setOf(
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

    /**
     * The built-in junk blocklist only, ignoring the user's [ignoreRules] layer entirely.
     * Used by the .syncignore impact preview to figure out which locally-known paths are even
     * eligible to be affected by a rule change — a path the blocklist already excludes can't be
     * "newly excluded" or "newly included" by editing `.syncignore`.
     */
    fun isBuiltInAllowed(path: String): Boolean {
        val segments = normalizedSegments(path)
        if (segments.isEmpty()) return false
        if (segments.any { it.startsWith('.') || it.startsWith('_') }) return false
        if (segments.any { blockedFileNames.contains(it) }) return false
        if (segments.any { segment -> blockedDirectoryNames.any { it.equals(segment, ignoreCase = true) } }) return false
        return true
    }

    private fun isNameAllowed(path: String): Boolean {
        if (!isBuiltInAllowed(path)) return false
        if (ignoreRules.matches(path)) return false
        return true
    }

    private fun normalizedSegments(path: String): List<String> =
        path.trim('/').split('/').filter { it.isNotEmpty() }
}
