package com.gladomat.linklet.viewmodel

import com.gladomat.linklet.data.model.NoteId

data class NoteListItemUiModel(
    val id: NoteId,
    val title: String,
    val path: String,
    val snippet: String? = null,
)

sealed interface NoteListUiState {
    data object Loading : NoteListUiState
    data class Success(val notes: List<NoteListItemUiModel>) : NoteListUiState
    data class Error(val message: String) : NoteListUiState
}
