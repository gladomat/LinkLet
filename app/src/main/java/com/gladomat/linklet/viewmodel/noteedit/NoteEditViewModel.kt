package com.gladomat.linklet.viewmodel.noteedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.domain.repository.INoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NoteEditViewModel @Inject constructor(
    private val repository: INoteRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val notePath: String = checkNotNull(savedStateHandle[NoteArgs.NOTE_PATH])

    private val _state = MutableStateFlow<NoteEditUiState>(NoteEditUiState.Loading)
    val state: StateFlow<NoteEditUiState> = _state.asStateFlow()

    init {
        loadNote()
    }

    fun loadNote() {
        viewModelScope.launch {
            _state.value = NoteEditUiState.Loading
            val result = repository.getNote(notePath)
            _state.value = result.fold(
                onSuccess = { note -> NoteEditUiState.Editing(content = note.content) },
                onFailure = { error ->
                    NoteEditUiState.Error(error.message ?: "Failed to load note")
                },
            )
        }
    }

    fun updateContent(newContent: String) {
        val current = _state.value
        if (current is NoteEditUiState.Editing) {
            _state.value = current.copy(content = newContent, errorMessage = null)
        }
    }

    fun save() {
        val current = _state.value
        if (current !is NoteEditUiState.Editing || current.isSaving) return
        viewModelScope.launch {
            _state.value = current.copy(isSaving = true, errorMessage = null)
            val result = repository.saveNote(notePath, current.content)
            _state.value = result.fold(
                onSuccess = { NoteEditUiState.Saved(notePath) },
                onFailure = { error ->
                    current.copy(isSaving = false, errorMessage = error.message ?: "Failed to save note")
                },
            )
        }
    }

    fun insertHeading(level: Int) {
        val stars = "*".repeat(level).takeIf { it.isNotEmpty() } ?: "*"
        appendSnippet("\n$stars ")
    }

    fun insertBold() {
        appendSnippet("**bold**")
    }

    fun insertItalic() {
        appendSnippet("/italic/")
    }

    fun insertSrcBlock() {
        appendSnippet("\n#+begin_src\n\n#+end_src\n")
    }

    private fun appendSnippet(snippet: String) {
        val current = _state.value
        if (current is NoteEditUiState.Editing) {
            _state.value = current.copy(content = current.content + snippet, errorMessage = null)
        }
    }

    object NoteArgs {
        const val NOTE_PATH = "note_path"
    }
}
