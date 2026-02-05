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
        val normalized = path.trim('/')
        if (normalized.isEmpty()) return false
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        if (segments.any { it.startsWith('.') || it.startsWith('_') }) return false
        if (segments.any { blockedFileNames.contains(it) }) return false
        val last = segments.lastOrNull() ?: return false
        val ext = last.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return false
        return allowedExtensions.contains(ext)
    }
}
