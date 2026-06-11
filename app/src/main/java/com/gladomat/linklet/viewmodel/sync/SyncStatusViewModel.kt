package com.gladomat.linklet.viewmodel.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.data.index.IndexResetService
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.data.sync.SyncStatus
import com.gladomat.linklet.data.sync.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the sync status screen.
 */
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val syncStatusRepository: SyncStatusRepository,
    private val syncStateDao: SyncStateDao,
    private val syncScheduler: SyncScheduler,
    private val indexResetService: IndexResetService,
) : ViewModel() {

    val status: StateFlow<SyncStatus?> = syncStatusRepository.statusFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Clears sync state, re-enqueues sync, and clears the persisted status.
     */
    fun clearAndContinue() {
        viewModelScope.launch {
            val cleared = runCatching {
                syncStateDao.clearAllStates()
                indexResetService.resetAndReindex()
            }
                .onFailure { error ->
                    _message.value = "Failed to clear sync state: ${error.message}"
                }
                .isSuccess

            if (!cleared) return@launch

            val scheduled = runCatching { syncScheduler.scheduleManual() }
                .onFailure { error ->
                    _message.value = "Failed to reschedule sync: ${error.message}"
                }
                .isSuccess

            if (!scheduled) return@launch

            syncStatusRepository.clearStatus()
        }
    }

    /**
     * Dismisses the current status entry.
     */
    fun dismiss() {
        viewModelScope.launch { syncStatusRepository.clearStatus() }
    }

    /**
     * Clears any user-facing message.
     */
    fun clearMessage() {
        _message.value = null
    }
}
