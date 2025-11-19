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
    private val searchQuery = MutableStateFlow("")
    private val hasLoaded = MutableStateFlow(false)

    val state: StateFlow<NoteListUiState> = combine(
        repository.observeNotes(),
        searchQuery,
        errorState,
        hasLoaded,
    ) { notes, query, error, loaded ->
        when {
            error != null -> NoteListUiState.Error(error)
            loaded -> {
                val trimmedQuery = query.trim()
                val filteredNotes = if (trimmedQuery.isEmpty()) {
                    notes
                } else {
                    notes.filter { it.matchesQuery(trimmedQuery) }
                }
                val uiModels = filteredNotes.map { note ->
                    val snippet = if (trimmedQuery.isEmpty()) {
                        null
                    } else {
                        note.buildSnippet(trimmedQuery)
                    }
                    note.toUiModel(snippet)
                }
                NoteListUiState.Success(uiModels)
            }
            else -> NoteListUiState.Loading
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = NoteListUiState.Loading,
        )

    val query: StateFlow<String> = searchQuery

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

    fun updateSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun clearSearchQuery() {
        searchQuery.value = ""
    }

    private fun Note.matchesQuery(rawQuery: String): Boolean {
        val query = rawQuery.lowercase()
        if (title.contains(query, ignoreCase = true)) return true
        if (content.lowercase().contains(query)) return true
        val tags = extractTagsFromContent(content)
        if (tags.any { it.contains(query, ignoreCase = true) }) return true
        return false
    }

    private fun Note.buildSnippet(rawQuery: String): String? {
        if (content.isEmpty()) return null
        val contentLower = content.lowercase()
        val queryLower = rawQuery.lowercase()
        val index = contentLower.indexOf(queryLower)
        if (index == -1) return null

        val radius = 40
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + rawQuery.length + radius).coerceAtMost(content.length)
        var snippet = content.substring(start, end).replace('\n', ' ')

        if (start > 0) {
            snippet = "…$snippet"
        }
        if (end < content.length) {
            snippet = "$snippet…"
        }

        return snippet
    }

    private fun extractTagsFromContent(content: String): List<String> {
        val prefix = "#+filetags:"
        val line = content.lineSequence()
            .firstOrNull { it.trimStart().startsWith(prefix, ignoreCase = true) }
            ?: return emptyList()
        val raw = line.substringAfter(":").trim()
        if (raw.isEmpty()) return emptyList()
        return raw
            .trim(':')
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun Note.toUiModel(snippet: String? = null): NoteListItemUiModel = NoteListItemUiModel(
        id = id,
        title = title,
        path = id.path,
        snippet = snippet,
    )
}
