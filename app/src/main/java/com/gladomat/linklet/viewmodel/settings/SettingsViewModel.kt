package com.gladomat.linklet.viewmodel.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.domain.repository.INoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val folderSettingsRepository: FolderSettingsRepository,
    private val noteRepository: INoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            folderSettingsRepository.folderUriFlow.collectLatest { uri ->
                _state.update { current ->
                    current.copy(selectedFolder = uri, message = null)
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
        _state.update { it.copy(isSyncing = true, message = null) }
        val result = noteRepository.reindex()
        _state.update { current ->
            if (result.isSuccess) {
                current.copy(isSyncing = false, message = "Sync complete")
            } else {
                current.copy(
                    isSyncing = false,
                    message = result.exceptionOrNull()?.message ?: "Sync failed",
                )
            }
        }
    }
}
