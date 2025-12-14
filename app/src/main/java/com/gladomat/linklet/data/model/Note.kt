package com.gladomat.linklet.data.model

/**
 * In-memory representation of a parsed org-roam note.
 */
data class Note(
    val id: NoteId,
    val title: String,
    val content: String,
    val links: List<NoteLink>,
    val orgId: String? = null,
    /** Key-value pairs from :PROPERTIES: drawer (e.g., ID, ROAM_REFS, ROAM_ALIASES) */
    val properties: Map<String, String> = emptyMap(),
    /** Tags from #+filetags: line */
    val fileTags: List<String> = emptyList(),
)
