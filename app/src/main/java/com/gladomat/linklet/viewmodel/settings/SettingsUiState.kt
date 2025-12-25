package com.gladomat.linklet.viewmodel.settings

import android.net.Uri

data class SettingsUiState(
    val selectedFolder: Uri? = null,
    val isSyncing: Boolean = false,
    val message: String? = null,
    val directoryChangeDialog: DirectoryChangeDialogState? = null,
    val periodicSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Long = 60L,
)

data class DirectoryChangeDialogState(
    val oldPath: String?,
    val newPath: String,
)
