package com.gladomat.linklet.viewmodel

import com.gladomat.linklet.data.model.NoteId

/**
 * Conflict information for a note that is a conflicted copy.
 * @param timestamp The conflict timestamp in human-readable format (e.g., "2025-11-27 18-06").
 */
data class ConflictInfo(
    val timestamp: String,
)

/**
 * UI model for a note list item.
 * @param id The unique identifier of the note.
 * @param title The display title of the note.
 * @param filename The filename/identifier shown below the title.
 * @param snippet Optional search result snippet.
 * @param conflictInfo Present if this note is a conflicted copy.
 */
data class NoteListItemUiModel(
    val id: NoteId,
    val title: String,
    val filename: String,
    val snippet: String? = null,
    val conflictInfo: ConflictInfo? = null,
)

sealed interface NoteListUiState {
    data object Loading : NoteListUiState
    data class Success(val notes: List<NoteListItemUiModel>) : NoteListUiState
    data class Error(val message: String) : NoteListUiState
}
