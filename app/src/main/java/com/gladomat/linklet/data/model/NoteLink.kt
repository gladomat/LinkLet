package com.gladomat.linklet.data.model

/**
 * Represents a directional link between notes found inside note content.
 *
 * @property fromId note path where the link originates.
 * @property target raw reference captured from org markup (file path or id).
 * @property label optional human-friendly alias for the link.
 * @property resolvedPath filesystem path resolved via repository (null when unresolved).
 */
data class NoteLink(
    val fromId: NoteId,
    val target: LinkTarget,
    val label: String?,
    val resolvedPath: String? = null,
)

sealed interface LinkTarget {
    data class Path(val value: String) : LinkTarget
    data class Id(val value: String) : LinkTarget
}
