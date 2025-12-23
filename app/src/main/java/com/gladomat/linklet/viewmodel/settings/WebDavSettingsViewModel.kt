package com.gladomat.linklet.viewmodel.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.data.settings.WebDavSettings
import com.gladomat.linklet.data.settings.WebDavSettingsRepository
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.data.sync.WebDavRemoteSyncProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebDavSettingsUiState(
    val baseUrl: String = "",
    val rootPath: String = "/",
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = false,
    val isSaving: Boolean = false,
    val isTestingConnection: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class WebDavSettingsViewModel @Inject constructor(
    private val settingsRepository: WebDavSettingsRepository,
    private val webDavRemoteSyncProvider: WebDavRemoteSyncProvider,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(WebDavSettingsUiState())
    val state: StateFlow<WebDavSettingsUiState> = _state

    init {
        viewModelScope.launch {
            if (settingsRepository.consumeResetDueToErrorFlag()) {
                _state.update {
                    it.copy(
                        message = "WebDAV settings were reset due to an internal error. Please re-enter them.",
                    )
                }
            }
            settingsRepository.settingsFlow.collectLatest { settings ->
                if (settings != null) {
                    _state.update {
                        it.copy(
                            baseUrl = settings.baseUrl,
                            rootPath = settings.rootPath,
                            username = settings.username,
                            password = settings.password,
                            enabled = settings.enabled,
                            message = null,
                        )
                    }
                } else {
                    _state.update {
                        WebDavSettingsUiState()
                    }
                }
            }
        }
    }

    fun updateBaseUrl(value: String) {
        _state.update { it.copy(baseUrl = value) }
    }

    fun updateRootPath(value: String) {
        _state.update { it.copy(rootPath = value) }
    }

    fun updateUsername(value: String) {
        _state.update { it.copy(username = value) }
    }

    fun updatePassword(value: String) {
        _state.update { it.copy(password = value) }
    }

    fun updateEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        viewModelScope.launch {
            settingsRepository.setEnabled(enabled)
        }
    }

    fun save() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, message = null) }
            runCatching {
                val previousSettings = settingsRepository.currentSettings()
                val sanitizedRoot = current.rootPath.ifBlank { "/" }
                val newSettings = WebDavSettings(
                    baseUrl = current.baseUrl.trim(),
                    rootPath = sanitizedRoot,
                    username = current.username.trim(),
                    password = current.password,
                    enabled = current.enabled,
                )
                settingsRepository.save(newSettings)

                if (previousSettings == null && newSettings.enabled && newSettings.isConfigured()) {
                    syncScheduler.scheduleInitial()
                }
            }.onSuccess {
                _state.update { it.copy(isSaving = false, message = "Saved") }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, message = error.message ?: "Unable to save settings") }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun testConnection() {
        val current = _state.value
        val sanitizedRoot = current.rootPath.ifBlank { "/" }
        val candidate = WebDavSettings(
            baseUrl = current.baseUrl.trim(),
            rootPath = sanitizedRoot,
            username = current.username.trim(),
            password = current.password,
            enabled = current.enabled,
        )
        if (!candidate.isConfigured()) {
            _state.update { it.copy(message = "Fill in URL, username, and password before testing") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isTestingConnection = true, message = null) }
            webDavRemoteSyncProvider.testConnection(candidate)
                .onSuccess {
                    _state.update { it.copy(isTestingConnection = false, message = "Connection successful") }
                }.onFailure { error ->
                    Log.e("WebDavSettings", "Connection test failed", error)
                    val hint = error.localizedMessage ?: error.javaClass.simpleName
                    _state.update {
                        it.copy(
                            isTestingConnection = false,
                            message = "Connection failed: $hint",
                        )
                    }
                }
        }
    }
}
