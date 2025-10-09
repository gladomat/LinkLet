package com.gladomat.linklet.viewmodel.noteedit

sealed interface NoteEditUiState {
    data object Loading : NoteEditUiState
    data class Editing(
        val content: String,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    ) : NoteEditUiState
    data class Saved(val path: String) : NoteEditUiState
    data class Error(val message: String) : NoteEditUiState
}
