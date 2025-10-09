package com.gladomat.linklet.data.model

/**
 * In-memory representation of a parsed org-roam note.
 */
data class Note(
    val id: NoteId,
    val title: String,
    val content: String,
    val links: List<NoteLink>,
)
