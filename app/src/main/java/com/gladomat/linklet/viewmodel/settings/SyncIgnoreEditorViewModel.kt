package com.gladomat.linklet.viewmodel.settings

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.sync.SyncIgnoreRules
import com.gladomat.linklet.data.sync.SyncPathFilter
import com.gladomat.linklet.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SYNC_IGNORE_FILE = ".syncignore"
private const val SEED_ASSET_NAME = "syncignore-default.txt"

/** A few hundred KB is already absurd for a rule file; guards against feeding garbage in. */
private const val MAX_LOAD_BYTES = 256 * 1024

data class SyncIgnoreEditorUiState(
    val isLoading: Boolean = true,
    val text: TextFieldValue = TextFieldValue(""),
    val lastSavedText: String = "",
    val isSaving: Boolean = false,
    /** True while requestSave() is diffing rules against storage.listFiles() - can take a
     *  noticeable moment on a large vault, so the Save button needs its own loading state
     *  distinct from [isSaving] (which only covers the actual file write). */
    val isComputingPreview: Boolean = false,
    val loadError: String? = null,
    /** Populated after a successful save; dropped lines are informational, never block Save. */
    val droppedLines: List<SyncIgnoreRules.DroppedLine> = emptyList(),
    val message: String? = null,
    val impactPreview: SyncIgnoreImpactPreview? = null,
) {
    val isDirty: Boolean get() = text.text != lastSavedText
}

/** Dry-run diff of old vs. new rules over the locally known vault files, shown before Save commits. */
data class SyncIgnoreImpactPreview(
    val newlyExcluded: List<String>,
    val newlyIncluded: List<String>,
    val droppedLines: List<SyncIgnoreRules.DroppedLine>,
    val isCatastrophic: Boolean,
)

@HiltViewModel
class SyncIgnoreEditorViewModel @Inject constructor(
    private val storage: IStorage,
    private val syncScheduler: SyncScheduler,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncIgnoreEditorUiState())
    val state: StateFlow<SyncIgnoreEditorUiState> = _state

    init {
        viewModelScope.launch {
            runCatching { loadInitialText() }
                .onSuccess { text ->
                    _state.update { it.copy(isLoading = false, text = TextFieldValue(text), lastSavedText = text) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, loadError = error.message ?: "Could not read `.syncignore`")
                    }
                }
        }
    }

    private suspend fun loadInitialText(): String {
        val existing = storage.readFileBytes(SYNC_IGNORE_FILE).getOrNull()
        val bytes = existing ?: context.assets.open(SEED_ASSET_NAME).use { it.readBytes() }
        if (bytes.size > MAX_LOAD_BYTES) {
            error("`.syncignore` is too large to edit in-app (${bytes.size} bytes)")
        }
        return decodeUtf8Strict(bytes)
    }

    fun onTextChange(value: TextFieldValue) {
        _state.update { it.copy(text = value) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /** Resets the field to the last-saved content without leaving the screen. */
    fun revert() {
        _state.update { it.copy(text = TextFieldValue(it.lastSavedText), impactPreview = null) }
    }

    /** Step 1 of Save: compute the impact preview; the user confirms via [confirmSave]. */
    fun requestSave() {
        val current = _state.value
        if (current.isSaving || current.isComputingPreview) return
        _state.update { it.copy(isComputingPreview = true) }
        viewModelScope.launch {
            val verbose = SyncIgnoreRules.parseVerbose(current.text.text)
            val preview = computeImpactPreview(current.lastSavedText, current.text.text, verbose)
            _state.update { it.copy(isComputingPreview = false, impactPreview = preview) }
        }
    }

    fun dismissImpactPreview() {
        _state.update { it.copy(impactPreview = null) }
    }

    /** Step 2 of Save: the user has seen the impact preview (and catastrophic warning, if any). */
    fun confirmSave() {
        val current = _state.value
        val preview = current.impactPreview ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            storage.writeFileBytes(SYNC_IGNORE_FILE, current.text.text.toByteArray(Charsets.UTF_8))
                .onSuccess {
                    runCatching { syncScheduler.scheduleManual() }
                    _state.update {
                        it.copy(
                            isSaving = false,
                            lastSavedText = current.text.text,
                            impactPreview = null,
                            droppedLines = preview.droppedLines,
                            message = "Saved · sync scheduled",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            impactPreview = null,
                            message = "Save failed: ${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                }
        }
    }

    private suspend fun computeImpactPreview(
        oldText: String,
        newText: String,
        verbose: SyncIgnoreRules.VerboseParseResult,
    ): SyncIgnoreImpactPreview {
        val oldRules = SyncIgnoreRules.parse(oldText)
        val newRules = verbose.rules
        val eligiblePaths = storage.listFiles().getOrDefault(emptyList())
            .filter { it != SYNC_IGNORE_FILE && SyncPathFilter.isBuiltInAllowed(it) }

        val newlyExcluded = eligiblePaths.filter { !oldRules.matches(it) && newRules.matches(it) }
        val newlyIncluded = eligiblePaths.filter { oldRules.matches(it) && !newRules.matches(it) }
        val currentlySyncingCount = eligiblePaths.count { !oldRules.matches(it) }

        val hasCatastrophicPattern = newText.lineSequence()
            .map { it.trim() }
            .any { it == "*" || it == "**" }
        val exceedsHalfOfTracked = currentlySyncingCount > 0 && newlyExcluded.size * 2 > currentlySyncingCount

        return SyncIgnoreImpactPreview(
            newlyExcluded = newlyExcluded,
            newlyIncluded = newlyIncluded,
            droppedLines = verbose.droppedLines,
            isCatastrophic = hasCatastrophicPattern || exceedsHalfOfTracked,
        )
    }
}

private fun decodeUtf8Strict(bytes: ByteArray): String {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        throw IllegalStateException("Could not read `.syncignore` — unexpected encoding", e)
    }
}
