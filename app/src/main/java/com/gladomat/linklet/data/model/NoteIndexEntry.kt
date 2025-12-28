package com.gladomat.linklet.data.model

/**
 * Lightweight index entry for note list rendering.
 */
data class NoteIndexEntry(
    val id: NoteId,
    val title: String,
    val fileTags: List<String>,
    val deletedAt: Long?,
    val linksReady: Boolean,
)
