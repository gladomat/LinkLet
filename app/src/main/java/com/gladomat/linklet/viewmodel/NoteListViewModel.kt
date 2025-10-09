package com.gladomat.linklet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.domain.repository.INoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val repository: INoteRepository,
) : ViewModel() {

    private val errorState = MutableStateFlow<String?>(null)
    private val hasLoaded = MutableStateFlow(false)

    val state: StateFlow<NoteListUiState> = combine(
        repository.observeNotes(),
        errorState,
        hasLoaded,
    ) { notes, error, loaded ->
        when {
            error != null -> NoteListUiState.Error(error)
            loaded -> NoteListUiState.Success(notes.map { it.toUiModel() })
            else -> NoteListUiState.Loading
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = NoteListUiState.Loading,
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            errorState.value = null
            val result = repository.reindex()
            if (result.isFailure) {
                errorState.value = result.exceptionOrNull()?.message ?: "Unable to index notes"
            } else {
                hasLoaded.value = true
            }
        }
    }

    private fun Note.toUiModel(): NoteListItemUiModel = NoteListItemUiModel(
        id = id,
        title = title,
        path = id.path,
    )
}
