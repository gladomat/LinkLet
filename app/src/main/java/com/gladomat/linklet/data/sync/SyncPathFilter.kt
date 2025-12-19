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

    fun shouldInclude(path: String): Boolean {
        val normalized = path.trim('/')
        if (normalized.isEmpty()) return false
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        if (segments.any { it.startsWith('.') || it.startsWith('_') }) return false
        if (segments.any { blockedFileNames.contains(it) }) return false
        return true
    }
}
