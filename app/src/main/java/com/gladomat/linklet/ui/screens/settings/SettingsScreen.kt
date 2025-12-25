package com.gladomat.linklet.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.ui.components.SyncDirectoryChangeDialog
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.settings.SettingsUiState
import com.gladomat.linklet.viewmodel.settings.SettingsViewModel

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    onOpenWebDavSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.onFolderSelected(uri, context.contentResolver)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    // Show directory change dialog if needed
    state.directoryChangeDialog?.let { dialogState ->
        SyncDirectoryChangeDialog(
            oldPath = dialogState.oldPath,
            newPath = dialogState.newPath,
            onClearAndContinue = viewModel::clearSyncStateAndRetry,
            onCancel = viewModel::dismissDirectoryChangeDialog,
            onDismiss = viewModel::dismissDirectoryChangeDialog,
        )
    }

    SettingsScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onOpenWebDavSettings = onOpenWebDavSettings,
        onPickFolder = { launcher.launch(state.selectedFolder) },
        onManualSync = viewModel::requestManualSync,
        onTogglePeriodicSync = viewModel::togglePeriodicSync,
        onUpdateSyncInterval = viewModel::updateSyncInterval,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onNavigateBack: () -> Unit,
    onOpenWebDavSettings: () -> Unit,
    onPickFolder: () -> Unit,
    onManualSync: () -> Unit,
    onTogglePeriodicSync: (Boolean) -> Unit,
    onUpdateSyncInterval: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = "Selected folder",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = state.selectedFolder?.toReadableName() ?: "Not selected",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Button(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
                Text(text = if (state.selectedFolder == null) "Choose folder" else "Change folder")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenWebDavSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "WebDAV settings")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onManualSync,
                enabled = !state.isSyncing && state.selectedFolder != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                    )
                }
                Text(text = "Sync now")
            }
            
            // Sync Settings Section
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Sync Settings",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    // Periodic Sync Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Periodic Sync",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "Automatically sync on schedule",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.periodicSyncEnabled,
                            onCheckedChange = onTogglePeriodicSync,
                        )
                    }
                    
                    // Sync Interval Selector
                    if (state.periodicSyncEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        var intervalMenuExpanded by remember { mutableStateOf(false) }
                        val intervalOptions = listOf(
                            15L to "15 minutes",
                            30L to "30 minutes",
                            60L to "1 hour",
                            120L to "2 hours",
                            360L to "6 hours",
                            720L to "12 hours",
                            1440L to "24 hours",
                        )
                        
                        Column {
                            Text(
                                text = "Sync Interval",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { intervalMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = intervalOptions.find { it.first == state.syncIntervalMinutes }?.second ?: "${state.syncIntervalMinutes} minutes",
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                )
                            }
                            
                            DropdownMenu(
                                expanded = intervalMenuExpanded,
                                onDismissRequest = { intervalMenuExpanded = false },
                            ) {
                                intervalOptions.forEach { (minutes, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onUpdateSyncInterval(minutes)
                                            intervalMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Uri.toReadableName(): String =
    lastPathSegment ?: toString()

@Preview
@Composable
private fun SettingsScreenPreview() {
    LinkLetAppTheme {
        Surface {
            SettingsScreen(
                state = SettingsUiState(),
                onNavigateBack = {},
                onOpenWebDavSettings = {},
                onPickFolder = {},
                onManualSync = {},
                onTogglePeriodicSync = {},
                onUpdateSyncInterval = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
    }
}
