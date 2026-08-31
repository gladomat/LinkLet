package com.gladomat.linklet.viewmodel.settings

import android.net.Uri
import com.gladomat.linklet.data.settings.ThemeId
import com.gladomat.linklet.data.settings.ThemeMode
import com.gladomat.linklet.data.settings.ThemePalette

data class SettingsUiState(
    val selectedFolder: Uri? = null,
    val isSyncing: Boolean = false,
    val message: String? = null,
    val directoryChangeDialog: DirectoryChangeDialogState? = null,
    val periodicSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Long = 60L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePalette: ThemePalette = ThemePalette.AMBER,
    val themeId: ThemeId = ThemeId.AMBERLINK,
)

data class DirectoryChangeDialogState(
    val oldPath: String?,
    val newPath: String,
)
