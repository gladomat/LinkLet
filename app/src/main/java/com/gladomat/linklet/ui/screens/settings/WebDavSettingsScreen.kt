package com.gladomat.linklet.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gladomat.linklet.ui.theme.LinkLetAppTheme
import com.gladomat.linklet.viewmodel.settings.WebDavSettingsUiState
import com.gladomat.linklet.viewmodel.settings.WebDavSettingsViewModel

@Composable
fun WebDavSettingsRoute(
    onNavigateBack: () -> Unit,
    onOpenSyncIgnoreEditor: () -> Unit,
    viewModel: WebDavSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    WebDavSettingsScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onBaseUrlChange = viewModel::updateBaseUrl,
        onRootPathChange = viewModel::updateRootPath,
        onUsernameChange = viewModel::updateUsername,
        onPasswordChange = viewModel::updatePassword,
        onEnabledChange = viewModel::updateEnabled,
        onSave = viewModel::save,
        onTestConnection = viewModel::testConnection,
        onOpenSyncIgnoreEditor = onOpenSyncIgnoreEditor,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavSettingsScreen(
    state: WebDavSettingsUiState,
    onNavigateBack: () -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onRootPathChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onOpenSyncIgnoreEditor: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordToggleVisible by remember(state.password) { mutableStateOf(state.password.isEmpty()) }
    var previousIsSaving by remember { mutableStateOf(state.isSaving) }
    LaunchedEffect(state.isSaving) {
        if (previousIsSaving && !state.isSaving) {
            passwordVisible = false
            passwordToggleVisible = false
        }
        previousIsSaving = state.isSaving
    }
    val handlePasswordChange: (String) -> Unit = { value ->
        if (!passwordToggleVisible) passwordToggleVisible = true
        onPasswordChange(value)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "WebDAV Settings") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Configure your WebDAV/Nextcloud endpoint.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Base URL") },
                singleLine = true,
                placeholder = { Text("https://example.com/remote.php/dav/files/user") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.rootPath,
                onValueChange = onRootPathChange,
                label = { Text("Folder path") },
                singleLine = true,
                placeholder = { Text("/Org") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = handlePasswordChange,
                label = { Text("App password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    if (passwordToggleVisible) {
                        Text(
                            text = if (passwordVisible) "Hide" else "Show",
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clickableWithoutRipple { passwordVisible = !passwordVisible },
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Enable WebDAV sync")
                Spacer(modifier = Modifier.height(4.dp))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onTestConnection,
                enabled = !state.isTestingConnection,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isTestingConnection) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                    )
                }
                Text(text = if (state.isTestingConnection) "Testing…" else "Test connection")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (state.isSaving) "Saving…" else "Save")
            }
            val isVaultConfigured = state.baseUrl.isNotBlank() &&
                state.username.isNotBlank() &&
                state.password.isNotBlank()
            if (isVaultConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenSyncIgnoreEditor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Edit sync exclusions (.syncignore)")
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier =
    this.then(
        clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick,
        ),
    )

@Preview(showBackground = true)
@Composable
private fun WebDavSettingsPreview() {
    LinkLetAppTheme {
        Surface {
            WebDavSettingsScreen(
                state = WebDavSettingsUiState(
                    baseUrl = "https://example.com/remote.php/dav/files/user/",
                    rootPath = "/Org",
                    username = "user",
                    password = "token",
                    enabled = true,
                ),
                onNavigateBack = {},
                onBaseUrlChange = {},
                onRootPathChange = {},
                onUsernameChange = {},
                onPasswordChange = {},
                onEnabledChange = {},
                onSave = {},
                onTestConnection = {},
                onOpenSyncIgnoreEditor = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
    }
}
