package com.gladomat.linklet.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    SettingsScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onOpenWebDavSettings = onOpenWebDavSettings,
        onPickFolder = { launcher.launch(state.selectedFolder) },
        onManualSync = viewModel::requestManualSync,
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
                snackbarHostState = SnackbarHostState(),
            )
        }
    }
}
