package com.gladomat.linklet.data.utils

/**
 * Helpers for resolving Org link targets to storage-relative paths.
 *
 * This intentionally keeps logic filesystem-agnostic; storage backends validate
 * and resolve the resulting paths within the configured root.
 */
object OrgPathResolver {

    /**
     * Maps an Org `:ID:` to the default attachment directory under `data/` next to the Org file.
     *
     * Common convention (matches your notes): `data/<first2>/<rest-of-id>/`.
     */
    fun orgAttachIdToRelativeDir(id: String): String {
        val normalized = id.trim().trim('/').trim()
        if (normalized.length < 3) return "data/$normalized"
        val prefix = normalized.take(2)
        val rest = normalized.drop(2)
        return "data/$prefix/$rest"
    }

    /**
     * Resolves an `[[attachment:...]]` target to a storage-relative path.
     *
     * Resolution:
     * - If `dirProperty` exists: treat it as a path relative to the note's directory.
     * - Else if `nodeId` exists: use `data/<first2>/<rest>/` relative to the note's directory.
     * - Else: return null (no attachment context).
     *
     * Security:
     * - Disallows absolute paths and traversal above the storage root.
     */
    fun resolveAttachmentPath(
        notePath: String,
        nodeId: String?,
        dirProperty: String?,
        attachmentName: String,
    ): String? {
        val noteDir = noteDir(notePath)
        val base = when {
            !dirProperty.isNullOrBlank() -> joinRelative(noteDir, dirProperty, allowAbsolute = false, allowTraversal = false)
            !nodeId.isNullOrBlank() -> joinRelative(noteDir, orgAttachIdToRelativeDir(nodeId), allowAbsolute = false, allowTraversal = false)
            else -> null
        } ?: return null
        return joinRelative(base, attachmentName, allowAbsolute = false, allowTraversal = false)
    }

    /**
     * Resolves an Org file/path target (e.g. from `[[file:...]]`, `[[./img.png]]`, or `./img.png`)
     * to a storage-relative path.
     *
     * - Relative paths are resolved against the note's directory.
     * - Leading `/` is treated as storage-root-relative.
     * - Traversal (`..`) is normalized but cannot escape above storage root.
     */
    fun resolveFileTargetPath(notePath: String, target: String): String? {
        val normalizedTarget = target.trim().removePrefix("file:").trim()
        if (normalizedTarget.isBlank()) return null
        val noteDir = noteDir(notePath)
        val allowAbsolute = true
        return if (normalizedTarget.startsWith("/")) {
            joinRelative(baseDir = "", relative = normalizedTarget, allowAbsolute = allowAbsolute, allowTraversal = true)
        } else {
            joinRelative(baseDir = noteDir, relative = normalizedTarget, allowAbsolute = allowAbsolute, allowTraversal = true)
        }
    }

    private fun noteDir(notePath: String): String =
        notePath.substringBeforeLast('/', missingDelimiterValue = "")

    private fun joinRelative(baseDir: String, relative: String, allowAbsolute: Boolean, allowTraversal: Boolean): String? {
        val baseSegments = baseDir.split('/')
            .filter { it.isNotBlank() }
            .toMutableList()

        val raw = relative.replace('\\', '/').trim()
        if (raw.startsWith("/") && !allowAbsolute) return null
        val relativeSegments = raw.trimStart('/').split('/').filter { it.isNotBlank() }

        for (segment in relativeSegments) {
            when (segment) {
                "." -> Unit
                ".." -> {
                    if (!allowTraversal) return null
                    if (baseSegments.isEmpty()) return null
                    baseSegments.removeAt(baseSegments.lastIndex)
                }
                else -> baseSegments.add(segment)
            }
        }

        return baseSegments.joinToString("/")
    }
}
