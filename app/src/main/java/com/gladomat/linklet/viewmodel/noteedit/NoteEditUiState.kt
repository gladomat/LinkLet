package com.gladomat.linklet.viewmodel.noteedit

import androidx.compose.ui.text.input.TextFieldValue

sealed interface NoteEditUiState {
    data object Loading : NoteEditUiState

    data class Editing(
        val value: TextFieldValue,
        val fileName: String,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    ) : NoteEditUiState

    data class Saved(val path: String) : NoteEditUiState

    data class Error(val message: String) : NoteEditUiState
}
