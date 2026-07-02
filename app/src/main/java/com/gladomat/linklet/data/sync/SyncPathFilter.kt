package com.gladomat.linklet.data.sync

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

    // Tight allowlist to avoid syncing random artifacts (app backups, databases, caches, etc.).
    // Expand intentionally as new legitimate file types are needed.
    private val allowedExtensions = setOf(
        "org",
        // Common attachments.
        "png",
        "jpg",
        "jpeg",
        "gif",
        "webp",
        "svg",
        "pdf",
        // Bibliography
        "bib",
    )

    fun shouldInclude(path: String): Boolean {
        if (!isNameAllowed(path)) return false
        val segments = normalizedSegments(path)
        val last = segments.lastOrNull() ?: return false
        val ext = last.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return false
        return allowedExtensions.contains(ext)
    }

    /**
     * Whether a directory should be descended into during remote/local tree traversal.
     * Unlike [shouldInclude], this has no extension gate — directories never have
     * file extensions, so applying that gate here would prune every subfolder
     * (e.g. org-attach/image folders) from being discovered at all.
     */
    fun isDirectoryTraversable(path: String): Boolean = isNameAllowed(path)

    private fun isNameAllowed(path: String): Boolean {
        val segments = normalizedSegments(path)
        if (segments.isEmpty()) return false
        if (segments.any { it.startsWith('.') || it.startsWith('_') }) return false
        if (segments.any { blockedFileNames.contains(it) }) return false
        if (segments.any { segment -> blockedDirectoryNames.any { it.equals(segment, ignoreCase = true) } }) return false
        return true
    }

    private fun normalizedSegments(path: String): List<String> =
        path.trim('/').split('/').filter { it.isNotEmpty() }
}
