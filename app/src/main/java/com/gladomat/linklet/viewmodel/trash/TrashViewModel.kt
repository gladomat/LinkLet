package com.gladomat.linklet.viewmodel.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.domain.repository.INoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TrashEvent {
    data class Message(val text: String) : TrashEvent
}

/**
 * ViewModel for the Trash screen.
 *
 * Notes exposed here live only under the dedicated trash directory and never appear
 * in the main note list, mirroring modern note apps with a separate Trash view.
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: INoteRepository,
) : ViewModel() {

    private val _trashNotes = MutableStateFlow<List<Note>>(emptyList())
    val trashNotes: StateFlow<List<Note>> = _trashNotes.asStateFlow()

    private val _events = MutableSharedFlow<TrashEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TrashEvent> = _events

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _trashNotes.value = repository.listTrashNotes()
        }
    }

    fun restore(note: Note) {
        viewModelScope.launch {
            repository.restoreNoteFromTrash(note.id.path)
                .onSuccess { restoredPath ->
                    _events.tryEmit(TrashEvent.Message("Restored: $restoredPath"))
                    refresh()
                }
                .onFailure { error ->
                    _events.tryEmit(TrashEvent.Message("Restore failed: ${error.message ?: "Unknown error"}"))
                    refresh()
                }
        }
    }

    fun deleteForever(note: Note) {
        viewModelScope.launch {
            repository.deleteNotePermanent(note.id.path)
                .onSuccess {
                    _events.tryEmit(TrashEvent.Message("Deleted forever"))
                    refresh()
                }
                .onFailure { error ->
                    _events.tryEmit(TrashEvent.Message("Delete failed: ${error.message ?: "Unknown error"}"))
                    refresh()
                }
        }
    }

    fun restoreAll() {
        viewModelScope.launch {
            val notes = repository.listTrashNotes()
            if (notes.isEmpty()) {
                _events.tryEmit(TrashEvent.Message("Trash is empty"))
                return@launch
            }
            var restoredCount = 0
            var failureCount = 0
            notes.forEach { note ->
                repository.restoreNoteFromTrash(note.id.path)
                    .onSuccess { restoredCount++ }
                    .onFailure { failureCount++ }
            }
            refresh()
            val suffix = if (failureCount == 0) "" else " ($failureCount failed)"
            _events.tryEmit(TrashEvent.Message("Restored $restoredCount note(s)$suffix"))
        }
    }

    fun deleteAllForever() {
        viewModelScope.launch {
            val notes = repository.listTrashNotes()
            if (notes.isEmpty()) {
                _events.tryEmit(TrashEvent.Message("Trash is empty"))
                return@launch
            }
            var deletedCount = 0
            var failureCount = 0
            notes.forEach { note ->
                repository.deleteNotePermanent(note.id.path)
                    .onSuccess { deletedCount++ }
                    .onFailure { failureCount++ }
            }
            refresh()
            val suffix = if (failureCount == 0) "" else " ($failureCount failed)"
            _events.tryEmit(TrashEvent.Message("Deleted $deletedCount note(s)$suffix"))
        }
    }
}
