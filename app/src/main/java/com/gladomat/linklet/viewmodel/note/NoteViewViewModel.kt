package com.gladomat.linklet.viewmodel.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NoteViewViewModel @Inject constructor(
    private val repository: INoteRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<NoteViewUiState>(NoteViewUiState.Loading)
    val state: StateFlow<NoteViewUiState> = _state.asStateFlow()

    private val notePath: String = checkNotNull(savedStateHandle[NoteArgs.NOTE_PATH])

    init {
        loadNote()
    }

    fun loadNote() {
        viewModelScope.launch {
            _state.value = NoteViewUiState.Loading
            val noteResult = repository.getNote(notePath)
            _state.value = noteResult.fold(
                onSuccess = { note ->
                    val backlinks = repository.getBacklinks(notePath).getOrDefault(emptyList<LinkEntityDto>())
                    NoteViewUiState.Success(note = note, backlinks = backlinks)
                },
                onFailure = { error ->
                    NoteViewUiState.Error(error.message ?: "Failed to load note")
                },
            )
        }
    }

    object NoteArgs {
        const val NOTE_PATH = "note_path"
    }
}
