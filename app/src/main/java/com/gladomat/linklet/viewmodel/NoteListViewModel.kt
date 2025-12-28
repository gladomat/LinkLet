package com.gladomat.linklet.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.gladomat.linklet.data.model.IndexingProgress
import com.gladomat.linklet.data.model.NoteIndexEntry
import com.gladomat.linklet.data.index.IndexingScheduler
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.data.sync.SyncWork
import com.gladomat.linklet.data.sync.worker.SyncWorker
import com.gladomat.linklet.domain.repository.INoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val repository: INoteRepository,
    private val indexingScheduler: IndexingScheduler,
    private val syncScheduler: SyncScheduler,
    private val application: Application,
) : ViewModel() {

    private val errorState = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val hasLoaded = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    private val workManager = WorkManager.getInstance(application)

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
                    val snippet = if (trimmedQuery.isEmpty()) null else note.snippetForQuery(trimmedQuery)
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

    // Sync state observation
    private val syncWorkInfo = workManager.getWorkInfosForUniqueWorkFlow(SyncWork.UNIQUE_ONE_TIME_NAME)
        .map { workInfos -> workInfos.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isSyncing: StateFlow<Boolean> = syncWorkInfo
        .map { it?.state == WorkInfo.State.RUNNING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val syncProgress: StateFlow<Float> = syncWorkInfo
        .map { workInfo ->
            if (workInfo?.state == WorkInfo.State.RUNNING) {
                val completed = workInfo.progress.getInt(SyncWorker.KEY_PROGRESS_COMPLETED, -1)
                val total = workInfo.progress.getInt(SyncWorker.KEY_PROGRESS_TOTAL, -1)
                if (completed >= 0 && total > 0) {
                    completed.toFloat() / total.toFloat()
                } else {
                    0f // Indeterminate
                }
            } else {
                0f
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    val indexingProgressPass1: StateFlow<IndexingProgress> = repository.observeIndexingProgress(pass = 1)
        .stateIn(viewModelScope, SharingStarted.Eagerly, IndexingProgress(completed = 0, total = 0))

    val indexingFailuresPass1: StateFlow<Int> = repository.observeIndexingFailures(pass = 1)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val indexingProgressPass2: StateFlow<IndexingProgress> = repository.observeIndexingProgress(pass = 2)
        .stateIn(viewModelScope, SharingStarted.Eagerly, IndexingProgress(completed = 0, total = 0))

    val indexingFailuresPass2: StateFlow<Int> = repository.observeIndexingFailures(pass = 2)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    init {
        refresh()
        // Trigger opportunistic sync on app open
        syncScheduler.scheduleImmediate()

        viewModelScope.launch {
            combine(indexingFailuresPass1, indexingFailuresPass2) { pass1, pass2 -> pass1 to pass2 }
                .distinctUntilChanged()
                .collect { (pass1, pass2) ->
                    if (pass1 > 0 || pass2 > 0) {
                        _snackbarMessage.value = "Indexing failed (pass1=$pass1, pass2=$pass2)"
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            errorState.value = null
            indexingScheduler.schedulePass1()
            indexingScheduler.schedulePass2()
            hasLoaded.value = true
        }
    }

    fun retryLinkIndexing() {
        viewModelScope.launch {
            indexingScheduler.schedulePass2()
        }
    }

    fun updateSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun clearSearchQuery() {
        searchQuery.value = ""
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun NoteIndexEntry.matchesQuery(rawQuery: String): Boolean {
        val query = rawQuery.lowercase()
        if (title.contains(query, ignoreCase = true)) return true
        return fileTags.any { it.contains(query, ignoreCase = true) }
    }

    private fun NoteIndexEntry.snippetForQuery(rawQuery: String): String? {
        val queryLower = rawQuery.lowercase()
        if (title.contains(queryLower, ignoreCase = true)) return null
        val matchedTag = fileTags.firstOrNull { it.contains(queryLower, ignoreCase = true) } ?: return null
        return "Tag: $matchedTag"
    }

    /**
     * Extracts conflict info from a filename if it contains a "conflicted copy" pattern.
     * Example: "20220228152819-meetstar (conflicted copy 2025-11-27 18-06).org"
     * Returns ConflictInfo with timestamp "2025-11-27 18-06" or null if not a conflict.
     */
    private fun extractConflictInfo(path: String): ConflictInfo? {
        val filename = path.substringAfterLast("/")
        val regex = Regex("""\(conflicted copy (\d{4}-\d{2}-\d{2} \d{2}-\d{2})\)""", RegexOption.IGNORE_CASE)
        val match = regex.find(filename) ?: return null
        return ConflictInfo(timestamp = match.groupValues[1])
    }

    /**
     * Extracts clean filename without conflict suffix for display.
     */
    private fun extractCleanFilename(path: String): String {
        val filename = path.substringAfterLast("/")
        // Remove conflict suffix if present
        val regex = Regex("""\s*\(conflicted copy \d{4}-\d{2}-\d{2} \d{2}-\d{2}\)""", RegexOption.IGNORE_CASE)
        return filename.replace(regex, "")
    }

    private fun NoteIndexEntry.toUiModel(snippet: String? = null): NoteListItemUiModel = NoteListItemUiModel(
        id = id,
        title = title,
        filename = extractCleanFilename(id.path),
        snippet = snippet,
        conflictInfo = extractConflictInfo(id.path),
    )
}
