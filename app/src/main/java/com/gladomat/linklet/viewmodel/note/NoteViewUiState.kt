package com.gladomat.linklet.viewmodel.note

import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.domain.repository.LinkEntityDto

sealed interface NoteViewUiState {
    data object Loading : NoteViewUiState
    data class Success(val note: Note, val backlinks: List<LinkEntityDto>) : NoteViewUiState
    data class Error(val message: String) : NoteViewUiState
}
