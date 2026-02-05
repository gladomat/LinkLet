package com.gladomat.linklet.ui.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.data.sync.SyncStatus
import com.gladomat.linklet.data.sync.SyncStatusType
import com.gladomat.linklet.viewmodel.sync.SyncStatusViewModel

@Composable
fun SyncStatusRoute(
    onNavigateBack: () -> Unit,
    viewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    SyncStatusScreen(
        status = status,
        onNavigateBack = onNavigateBack,
        onClearAndContinue = viewModel::clearAndContinue,
        onDismiss = viewModel::dismiss,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    status: SyncStatus?,
    onNavigateBack: () -> Unit,
    onClearAndContinue: () -> Unit,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Sync status") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (status == null) {
                Text(
                    text = "No sync issues detected.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onNavigateBack) {
                    Text("Back")
                }
                return@Column
            }

            Text(
                text = status.title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = status.message,
                style = MaterialTheme.typography.bodyMedium,
            )

            when (status.type) {
                SyncStatusType.DIRECTORY_CHANGED -> {
                    status.oldPath?.let { oldPath ->
                        Text(
                            text = "Previous: $oldPath",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    status.newPath?.let { newPath ->
                        Text(
                            text = "Current: $newPath",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                SyncStatusType.REQUIRES_CONFIRMATION -> {
                    status.newPath?.let { newPath ->
                        Text(
                            text = "Directory: $newPath",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (status.requiresAction) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onClearAndContinue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear & Continue")
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}
