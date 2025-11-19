package com.gladomat.linklet.viewmodel.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NoteViewViewModel @Inject constructor(
    private val repository: INoteRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<NoteViewUiState>(NoteViewUiState.Loading)
    val state: StateFlow<NoteViewUiState> = _state.asStateFlow()

    private var notePath: String = checkNotNull(savedStateHandle[NoteArgs.NOTE_PATH])
    private val history = ArrayDeque<String>()

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

    fun openNote(path: String) {
        if (path == notePath) return
        rememberCurrentPath()
        navigateTo(path)
    }

    fun handleBackPress(): Boolean {
        val previous = history.removeFirstOrNull() ?: return false
        navigateTo(previous, addToHistory = false)
        return true
    }

    fun resetHistory() {
        history.clear()
    }

    private fun rememberCurrentPath() {
        if (history.peekFirst() == notePath) return
        history.removeIf { it == notePath }
        history.addFirst(notePath)
        if (history.size > 32) {
            history.removeLast()
        }
    }

    private fun navigateTo(path: String, addToHistory: Boolean = true) {
        if (path == notePath && addToHistory) return
        notePath = path
        savedStateHandle[NoteArgs.NOTE_PATH] = path
        loadNote()
    }

    private fun <T> ArrayDeque<T>.removeFirstOrNull(): T? = if (isEmpty()) null else removeFirst()

    object NoteArgs {
        const val NOTE_PATH = "note_path"
    }
}
