package com.gladomat.linklet.viewmodel.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.gladomat.linklet.data.parser.org.OrgBlock
import com.gladomat.linklet.data.parser.org.buildOrgDisplayText
import com.gladomat.linklet.data.parser.org.parseOrgDocument
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.domain.service.MatchRange
import com.gladomat.linklet.domain.service.NoteSearchEngine
import com.gladomat.linklet.domain.service.SearchBlock
import com.gladomat.linklet.domain.service.SearchOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@kotlinx.coroutines.FlowPreview
class NoteViewViewModel @Inject constructor(
    private val repository: INoteRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val logTag = "NoteSearch"

    private val _state = MutableStateFlow<NoteViewUiState>(NoteViewUiState.Loading)
    val state: StateFlow<NoteViewUiState> = _state.asStateFlow()

    private var notePath: String = checkNotNull(savedStateHandle[NoteArgs.NOTE_PATH])
    private val history = ArrayDeque<String>()

    private val _searchState = MutableStateFlow(
        NoteSearchState(
            isActive = savedStateHandle[SearchKeys.ACTIVE] ?: false,
            query = savedStateHandle[SearchKeys.QUERY] ?: "",
            options = SearchOptions(
                caseSensitive = savedStateHandle[SearchKeys.CASE_SENSITIVE] ?: false,
                wholeWord = savedStateHandle[SearchKeys.WHOLE_WORD] ?: false,
                useRegex = savedStateHandle[SearchKeys.USE_REGEX] ?: false,
            ),
        ),
    )
    val searchState: StateFlow<NoteSearchState> = _searchState.asStateFlow()

    private val searchCorpus = MutableStateFlow<List<SearchBlock>>(emptyList())

    init {
        // Observe SavedStateHandle for path changes and reload when it changes
        // This ensures we reload when navigating to the same route with a different path
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String>(NoteArgs.NOTE_PATH, notePath)
                .collect { path ->
                    if (path != notePath) {
                        notePath = path
                        loadNote()
                    }
                }
        }
        // Initial load
        loadNote()

        viewModelScope.launch {
            _searchState
                .map { it.query }
                .distinctUntilChanged()
                .debounce { query -> if (query.isBlank()) 0L else 150L }
                .collect { recomputeSearch() }
        }

        viewModelScope.launch {
            _searchState
                .map { it.options }
                .distinctUntilChanged()
                .collect { recomputeSearch() }
        }
    }

    fun loadNote() {
        viewModelScope.launch {
            _state.value = NoteViewUiState.Loading
            val noteResult = repository.getNote(notePath)
            _state.value = noteResult.fold(
                onSuccess = { note ->
                    val backlinks = repository.getBacklinks(notePath).getOrDefault(emptyList<LinkEntityDto>())
                    updateSearchCorpus(content = note.content, links = note.links)
                    NoteViewUiState.Success(
                        note = note,
                        backlinks = backlinks,
                        lastModified = null, // TODO: get from file metadata
                        isFavorite = false, // TODO: persist favorites
                    )
                },
                onFailure = { error ->
                    NoteViewUiState.Error(error.message ?: "Failed to load note")
                },
            )
        }
    }

    fun openSearch() {
        Log.d(logTag, "openSearch path=$notePath")
        setSearchActive(true)
    }

    fun closeSearch() {
        Log.d(logTag, "closeSearch path=$notePath")
        setSearchActive(false)
    }

    fun updateSearchQuery(query: String) {
        Log.d(logTag, "updateQuery len=${query.length} path=$notePath")
        savedStateHandle[SearchKeys.QUERY] = query
        _searchState.update { it.copy(query = query) }
    }

    fun setSearchOptions(options: SearchOptions) {
        Log.d(logTag, "updateOptions $options path=$notePath")
        savedStateHandle[SearchKeys.CASE_SENSITIVE] = options.caseSensitive
        savedStateHandle[SearchKeys.WHOLE_WORD] = options.wholeWord
        savedStateHandle[SearchKeys.USE_REGEX] = options.useRegex
        _searchState.update { it.copy(options = options) }
    }

    fun clearSearch() {
        updateSearchQuery("")
        _searchState.update { it.copy(matches = emptyList(), activeMatchIndex = -1, regexError = null) }
    }

    fun selectNextMatch() {
        _searchState.update { state ->
            val count = state.matches.size
            if (count == 0) return@update state.copy(activeMatchIndex = -1)
            val next = if (state.activeMatchIndex < 0) 0 else (state.activeMatchIndex + 1) % count
            state.copy(activeMatchIndex = next)
        }
    }

    fun selectPrevMatch() {
        _searchState.update { state ->
            val count = state.matches.size
            if (count == 0) return@update state.copy(activeMatchIndex = -1)
            val prev = if (state.activeMatchIndex < 0) 0 else (state.activeMatchIndex - 1 + count) % count
            state.copy(activeMatchIndex = prev)
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

    fun toggleFavorite() {
        _state.update { currentState ->
            if (currentState is NoteViewUiState.Success) {
                currentState.copy(isFavorite = !currentState.isFavorite)
            } else {
                currentState
            }
        }
        // TODO: persist favorite state
    }

    /**
     * Deletes the current note and signals completion via onDeleted callback.
     */
    fun deleteNote(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteNote(notePath).fold(
                onSuccess = {
                    resetHistory()
                    onDeleted()
                },
                onFailure = { error ->
                    _state.value = NoteViewUiState.Error("Failed to delete: ${error.message}")
                },
            )
        }
    }

    /**
     * Duplicates the current note with a new timestamp filename and ID.
     * Navigates to the new note on success.
     */
    fun duplicateNote() {
        viewModelScope.launch {
            repository.duplicateNote(notePath).fold(
                onSuccess = { newPath ->
                    navigateTo(newPath, addToHistory = true)
                },
                onFailure = { error ->
                    _state.value = NoteViewUiState.Error("Failed to duplicate: ${error.message}")
                },
            )
        }
    }

    /**
     * Renames the current note to a new filename.
     * Navigates to the renamed note on success.
     */
    fun renameNote(newFilename: String) {
        val currentState = _state.value
        if (currentState !is NoteViewUiState.Success) return

        viewModelScope.launch {
            val directory = notePath.substringBeforeLast('/', "")
            val newPath = if (directory.isEmpty()) newFilename else "$directory/$newFilename"
            
            repository.renameNote(notePath, newPath).fold(
                onSuccess = {
                    notePath = newPath
                    savedStateHandle[NoteArgs.NOTE_PATH] = newPath
                    loadNote()
                },
                onFailure = { error ->
                    _state.value = NoteViewUiState.Error("Failed to rename: ${error.message}")
                },
            )
        }
    }

    /** Returns the current note path */
    fun currentPath(): String = notePath

    /** Gets all unique tags from the repository for autocomplete */
    suspend fun getAllTags(): List<String> {
        return repository.getAllTags().getOrDefault(emptyList())
    }

    /** Updates properties in the current note */
    fun updateProperties(properties: Map<String, String>) {
        viewModelScope.launch {
            repository.updateNoteProperties(notePath, properties).fold(
                onSuccess = { loadNote() },
                onFailure = { error ->
                    _state.value = NoteViewUiState.Error("Failed to update properties: ${error.message}")
                },
            )
        }
    }

    /** Updates tags in the current note */
    fun updateTags(tags: List<String>) {
        viewModelScope.launch {
            repository.updateNoteTags(notePath, tags).fold(
                onSuccess = { loadNote() },
                onFailure = { error ->
                    _state.value = NoteViewUiState.Error("Failed to update tags: ${error.message}")
                },
            )
        }
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

    private object SearchKeys {
        const val ACTIVE = "note_search_active"
        const val QUERY = "note_search_query"
        const val CASE_SENSITIVE = "note_search_case_sensitive"
        const val WHOLE_WORD = "note_search_whole_word"
        const val USE_REGEX = "note_search_use_regex"
    }

    data class NoteSearchState(
        val isActive: Boolean = false,
        val query: String = "",
        val options: SearchOptions = SearchOptions(),
        val matches: List<MatchRange> = emptyList(),
        val activeMatchIndex: Int = -1,
        val regexError: String? = null,
    ) {
        val totalMatches: Int get() = matches.size
        val activeMatchNumber: Int get() = if (activeMatchIndex in matches.indices) activeMatchIndex + 1 else 0
        val activeMatch: MatchRange? get() = matches.getOrNull(activeMatchIndex)
    }

    private fun setSearchActive(active: Boolean) {
        savedStateHandle[SearchKeys.ACTIVE] = active
        _searchState.update { it.copy(isActive = active) }
    }

    private fun recomputeSearch() {
        val state = _searchState.value
        val result = NoteSearchEngine.search(
            blocks = searchCorpus.value,
            query = state.query,
            options = state.options,
        )

        _searchState.update { current ->
            result.fold(
                onSuccess = { matches ->
                    val nextIndex = when {
                        matches.isEmpty() -> -1
                        current.activeMatchIndex in matches.indices -> current.activeMatchIndex
                        else -> 0
                    }
                    current.copy(
                        matches = matches,
                        activeMatchIndex = nextIndex,
                        regexError = null,
                    )
                },
                onFailure = { error ->
                    current.copy(
                        matches = emptyList(),
                        activeMatchIndex = -1,
                        regexError = error.message ?: "Invalid regex",
                    )
                },
            )
        }
    }

    private fun updateSearchCorpus(
        content: String,
        links: List<com.gladomat.linklet.data.model.NoteLink>,
    ) {
        val state = _searchState.value

        val document = parseOrgDocument(content)
        val blocks = mutableListOf<SearchBlock>()
        document.prefaceBlocks.forEachIndexed { index, block ->
            blocks += toSearchBlocks(
                block = block,
                blockIdBase = "preface/block-$index",
                links = links,
            )
        }
        fun visitSection(section: com.gladomat.linklet.data.parser.org.OrgSection) {
            blocks += SearchBlock(
                blockId = "section/${section.id}/header",
                text = section.title,
            )
            section.blocks.forEachIndexed { blockIndex, block ->
                blocks += toSearchBlocks(
                    block = block,
                    blockIdBase = "section/${section.id}/block-$blockIndex",
                    links = links,
                )
            }
            section.children.forEach { visitSection(it) }
        }
        document.sections.forEach { visitSection(it) }
        searchCorpus.value = blocks

        if (state.query.isNotBlank()) {
            recomputeSearch()
        }
    }

    private fun toSearchBlocks(
        block: OrgBlock,
        blockIdBase: String,
        links: List<com.gladomat.linklet.data.model.NoteLink>,
    ): List<SearchBlock> {
        return when (block) {
            is OrgBlock.Table -> {
                block.rows.flatMapIndexed { rowIndex, row ->
                    row.mapIndexed { colIndex, cell ->
                        SearchBlock(
                            blockId = "$blockIdBase/cell-$rowIndex-$colIndex",
                            text = buildOrgDisplayText(cell, links),
                        )
                    }
                }
            }
            is OrgBlock.Paragraph -> listOf(
                SearchBlock(
                    blockId = blockIdBase,
                    text = buildOrgDisplayText(block.text, links),
                ),
            )
            is OrgBlock.QuoteBlock -> listOf(
                SearchBlock(
                    blockId = blockIdBase,
                    text = buildOrgDisplayText(block.content, links),
                ),
            )
            is OrgBlock.CenterBlock -> listOf(
                SearchBlock(
                    blockId = blockIdBase,
                    text = buildOrgDisplayText(block.content, links),
                ),
            )
            is OrgBlock.SourceBlock -> listOf(
                SearchBlock(
                    blockId = blockIdBase,
                    text = block.content,
                ),
            )
            is OrgBlock.ExampleBlock -> listOf(
                SearchBlock(
                    blockId = blockIdBase,
                    text = block.content,
                ),
            )
            is OrgBlock.VerseBlock -> listOf(
                SearchBlock(
                    blockId = blockIdBase,
                    text = block.content,
                ),
            )
            is OrgBlock.UnknownBlock -> listOf(
                SearchBlock(
                    blockId = blockIdBase,
                    text = block.content,
                ),
            )
        }
    }
}
