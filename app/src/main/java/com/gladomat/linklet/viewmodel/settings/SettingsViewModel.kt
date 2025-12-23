package com.gladomat.linklet.viewmodel.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.data.sync.SyncScheduler
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
    private val syncScheduler: SyncScheduler,
    private val syncStateDao: SyncStateDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            folderSettingsRepository.folderUriFlow.collectLatest { uri ->
                _state.update { current ->
                    if (current.selectedFolder != uri) {
                        current.copy(selectedFolder = uri)
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
        triggerSync()
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun dismissDirectoryChangeDialog() {
        _state.update { it.copy(directoryChangeDialog = null) }
    }

    fun clearSyncStateAndRetry() {
        viewModelScope.launch {
            runCatching {
                syncStateDao.clearAllStates()
                _state.update { it.copy(directoryChangeDialog = null) }
                triggerSync()
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        directoryChangeDialog = null,
                        message = "Failed to clear sync state: ${error.message}"
                    )
                }
            }
        }
    }

    private fun triggerSync() {
        Log.i(TAG, "Manual sync requested")
        _state.update { it.copy(isSyncing = true, message = null) }
        runCatching { syncScheduler.scheduleManual() }
            .onSuccess {
                Log.i(TAG, "Sync scheduled successfully")
                _state.update { it.copy(isSyncing = false, message = "Sync scheduled") }
            }
            .onFailure { error ->
                Log.e(TAG, "Sync scheduling failed", error)
                _state.update { it.copy(isSyncing = false, message = "Sync failed: ${error.localizedMessage}") }
            }
    }
}
