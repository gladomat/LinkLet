package com.gladomat.linklet.data.model

/**
 * Represents a directional link between notes found inside note content.
 *
 * @property fromId note path where the link originates.
 * @property toPath target note path referenced in the link.
 * @property label optional human-friendly alias for the link.
 */
data class NoteLink(
     val fromId: NoteId,
     val toPath: String,
     val label: String?,
 )
