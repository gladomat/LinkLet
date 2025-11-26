package com.gladomat.linklet.viewmodel.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.data.sync.SyncEngine
import com.gladomat.linklet.data.sync.WebDavRemoteSyncProvider
import com.gladomat.linklet.domain.repository.INoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SettingsViewModel"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val folderSettingsRepository: FolderSettingsRepository,
    private val noteRepository: INoteRepository,
    private val syncEngine: SyncEngine,
    private val webDavRemoteSyncProvider: WebDavRemoteSyncProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            folderSettingsRepository.folderUriFlow.collectLatest { uri ->
                _state.update { current ->
                    if (current.selectedFolder != uri) {
                        current.copy(selectedFolder = uri, message = null)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun onFolderSelected(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: SecurityException) {
                    // Permission already persisted; ignore
                }
                folderSettingsRepository.setFolderUri(uri)
                triggerSync()
            }.onFailure { error ->
                _state.update { current -> current.copy(message = error.message ?: "Unable to select folder") }
            }
        }
    }

    fun requestManualSync() {
        viewModelScope.launch {
            triggerSync()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun triggerSync() {
        Log.i(TAG, "Manual sync requested")
        val folderUri = _state.value.selectedFolder ?: folderSettingsRepository.currentFolderUri().also { uri ->
            if (uri != null) {
                _state.update { it.copy(selectedFolder = uri) }
            }
        }
        if (folderUri == null) {
            _state.update { it.copy(message = "Select a folder before syncing") }
            return
        }
        _state.update { it.copy(isSyncing = true, message = null) }
        val remoteReady = webDavRemoteSyncProvider.isReadyForSync()
        val syncResult = if (remoteReady) {
            runCatching {
                val summary = syncEngine.run(webDavRemoteSyncProvider).getOrThrow()
                noteRepository.reindex().getOrThrow()
                summary
            }
        } else {
            runCatching {
                noteRepository.reindex().getOrThrow()
                null
            }
        }
        _state.update { current ->
            syncResult.fold(
                onSuccess = { summary ->
                    val status = when {
                        summary != null && summary.resolvedConflicts > 0 ->
                            "Sync complete. ${summary.resolvedConflicts} conflict(s) resolved (check file list)."
                        remoteReady && summary != null ->
                            "Sync complete (uploads pending: ${summary.pendingUploads}, downloads pending: ${summary.pendingDownloads})"
                        remoteReady -> "Sync complete"
                        else -> "Local reindex complete. Configure WebDAV sync to push remote changes."
                    }
                    Log.i(TAG, "Sync finished successfully: $status")
                    current.copy(isSyncing = false, message = status)
                },
                onFailure = { error ->
                    Log.e(TAG, "Sync failed", error)
                    current.copy(isSyncing = false, message = "Sync failed: ${error.localizedMessage}")
                },
            )
        }
    }
}
