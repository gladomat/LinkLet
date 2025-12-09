package com.gladomat.linklet.viewmodel.noteedit

import android.util.Log
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.data.utils.OrgFileUtils
import com.gladomat.linklet.domain.repository.INoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.collections.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NoteEditViewModel @Inject constructor(
    private val repository: INoteRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val notePath: String = checkNotNull(savedStateHandle[NoteArgs.NOTE_PATH])
    private val isNewNote: Boolean = notePath == NEW_NOTE_PATH

    private val _state = MutableStateFlow<NoteEditUiState>(NoteEditUiState.Loading)
    val state: StateFlow<NoteEditUiState> = _state.asStateFlow()

    private val history = ArrayDeque<TextFieldValue>()
    private var suppressHistory = false

    // Track the actual path for new notes (generated on first save)
    private var actualPath: String? = if (isNewNote) null else notePath

    // Track creation time for new notes (used for ID generation and filename)
    private var createdAt: LocalDateTime? = null

    // Track whether the note has been saved at least once (ID has been assigned)
    private var hasBeenSaved: Boolean = !isNewNote

    // Track the initial content to detect changes
    private var initialContent: String = ""

    init {
        loadNote()
    }

    fun loadNote() {
        if (isNewNote) {
            // For new notes, start with minimal template and track creation time
            createdAt = LocalDateTime.now()
            history.clear()
            initialContent = NEW_NOTE_TEMPLATE
            _state.value = NoteEditUiState.Editing(
                value = TextFieldValue(
                    text = NEW_NOTE_TEMPLATE,
                    selection = TextRange(NEW_NOTE_TEMPLATE.length),
                ),
                fileName = NEW_NOTE_DISPLAY_NAME,
            )
            return
        }

        viewModelScope.launch {
            _state.value = NoteEditUiState.Loading
            val result = repository.getNote(notePath)
            _state.value = result.fold(
                onSuccess = { note ->
                    history.clear()
                    initialContent = note.content
                    // Use parsed title for display, fallback to path
                    val displayName = OrgFileUtils.getDisplayName(note.content, notePath)
                    NoteEditUiState.Editing(
                        value = TextFieldValue(
                            text = note.content,
                            selection = TextRange(note.content.length),
                        ),
                        fileName = displayName,
                    )
                },
                onFailure = { error ->
                    NoteEditUiState.Error(error.message ?: "Failed to load note")
                },
            )
        }
    }

    fun updateContent(newValue: TextFieldValue) {
        val current = _state.value
        if (current is NoteEditUiState.Editing) {
            if (!suppressHistory && current.value != newValue) {
                pushHistory(current.value)
            }
            suppressHistory = false

            // Update display name reactively based on parsed title
            val newDisplayName = if (isNewNote && actualPath == null) {
                // New note that hasn't been saved yet
                OrgFileUtils.getDisplayName(newValue.text, NEW_NOTE_DISPLAY_NAME)
            } else {
                // Existing note or already-saved new note
                OrgFileUtils.getDisplayName(newValue.text, current.fileName)
            }

            _state.value = current.copy(
                value = newValue,
                fileName = newDisplayName,
                errorMessage = null,
            )
        }
    }

    fun save() {
        val current = _state.value
        if (current !is NoteEditUiState.Editing || current.isSaving) return
        viewModelScope.launch {
            Log.d(TAG, "save() called - starting save operation")
            _state.value = current.copy(isSaving = true, errorMessage = null)

            val contentToSave: String
            val savePath: String

            if (isNewNote && actualPath == null) {
                // First save of a new note: generate ID, insert into drawer, generate filename
                val noteCreatedAt = createdAt ?: LocalDateTime.now()
                val title = OrgFileUtils.extractTitle(current.value.text)
                val noteId = OrgFileUtils.generateNoteId(title, noteCreatedAt)

                Log.d(TAG, "First save of new note - title='$title', id='$noteId'")

                // Insert ID into properties drawer (plain text manipulation)
                contentToSave = OrgFileUtils.ensureIdInProperties(current.value.text, noteId)

                // Generate filename from timestamp + slugified title
                savePath = OrgFileUtils.generateFilename(title, noteCreatedAt)
                actualPath = savePath
                hasBeenSaved = true
                Log.d(TAG, "Generated filename: $savePath")
            } else {
                // Subsequent save: use existing path, ensure ID not overwritten
                savePath = actualPath ?: notePath
                Log.d(TAG, "Subsequent save - path='$savePath'")
                contentToSave = if (!hasBeenSaved && !OrgFileUtils.hasExistingId(current.value.text)) {
                    // Edge case: note loaded but missing ID - generate one
                    val noteId = OrgFileUtils.generateNoteId(
                        OrgFileUtils.extractTitle(current.value.text),
                        LocalDateTime.now(),
                    )
                    hasBeenSaved = true
                    Log.d(TAG, "Adding missing ID to existing note: $noteId")
                    OrgFileUtils.ensureIdInProperties(current.value.text, noteId)
                } else {
                    current.value.text
                }
            }

            Log.d(TAG, "Calling repository.saveNote() for path='$savePath'")
            val result = repository.saveNote(savePath, contentToSave)
            _state.value = result.fold(
                onSuccess = {
                    Log.d(TAG, "Save successful for path='$savePath'")
                    // Update initial content to match what was saved
                    // This prevents the unsaved changes dialog from appearing after save
                    Log.d(TAG, "Updating initialContent to contentToSave (length=${contentToSave.length})")
                    initialContent = contentToSave
                    NoteEditUiState.Saved(savePath)
                },
                onFailure = { error ->
                    Log.e(TAG, "Save failed for path='$savePath': ${error.message}", error)
                    current.copy(isSaving = false, errorMessage = error.message ?: "Failed to save note")
                },
            )
        }
    }

    fun undo() {
        val current = _state.value
        if (current is NoteEditUiState.Editing) {
            val previous = history.removeLastOrNull() ?: return
            suppressHistory = true
            // Update display name based on restored content
            val displayName = if (isNewNote && actualPath == null) {
                OrgFileUtils.getDisplayName(previous.text, NEW_NOTE_DISPLAY_NAME)
            } else {
                OrgFileUtils.getDisplayName(previous.text, current.fileName)
            }
            _state.value = current.copy(
                value = previous,
                fileName = displayName,
                errorMessage = null,
            )
            suppressHistory = false
        }
    }

    /**
     * Check if the current content has unsaved changes compared to the initial content.
     * Returns true if there are changes, false otherwise.
     */
    fun hasUnsavedChanges(): Boolean {
        val current = _state.value
        val hasChanges = if (current is NoteEditUiState.Editing) {
            current.value.text != initialContent
        } else {
            false
        }
        Log.d(TAG, "hasUnsavedChanges(): $hasChanges (state=${current::class.simpleName})")
        if (current is NoteEditUiState.Editing && hasChanges) {
            Log.d(TAG, "  Current length: ${current.value.text.length}, Initial length: ${initialContent.length}")
        }
        return hasChanges
    }

    fun insertHeading(level: Int) {
        val stars = "*".repeat(level).takeIf { it.isNotEmpty() } ?: "*"
        insertSnippet("\n$stars ")
    }

    fun insertBold() {
        wrapSelection("**", "**")
    }

    fun insertItalic() {
        wrapSelection("/", "/")
    }

    fun insertSrcBlock() {
        insertSnippet("\n#+begin_src\n\n#+end_src\n")
    }

    fun insertUnorderedList() {
        insertSnippet("\n- ")
    }

    fun insertOrderedList() {
        insertSnippet("\n1. ")
    }

    fun increaseIndentation() {
        modifySelectedLines { line ->
            if (line.isBlank()) line else "    $line"
        }
    }

    fun decreaseIndentation() {
        modifySelectedLines { line ->
            when {
                line.startsWith("\t") -> line.drop(1)
                line.startsWith("    ") -> line.drop(4)
                line.startsWith("   ") -> line.drop(3)
                line.startsWith("  ") -> line.drop(2)
                line.startsWith(" ") -> line.drop(1)
                else -> line
            }
        }
    }

    private fun insertSnippet(snippet: String) {
        transformValue { value ->
            val selectionStart = value.selection.min
            val selectionEnd = value.selection.max
            val newText = value.text.replaceRange(selectionStart, selectionEnd, snippet)
            val cursor = selectionStart + snippet.length
            value.copy(text = newText, selection = TextRange(cursor))
        }
    }

    private fun wrapSelection(prefix: String, suffix: String) {
        transformValue { value ->
            val selectionStart = value.selection.start
            val selectionEnd = value.selection.end
            val selectedText = if (selectionStart != selectionEnd) {
                value.text.substring(selectionStart, selectionEnd)
            } else {
                ""
            }
            val replacement = prefix + selectedText + suffix
            val newText = value.text.replaceRange(selectionStart, selectionEnd, replacement)
            val cursor = if (selectedText.isEmpty()) {
                selectionStart + prefix.length
            } else {
                selectionStart + replacement.length
            }
            value.copy(text = newText, selection = TextRange(cursor))
        }
    }

    private fun transformValue(transform: (TextFieldValue) -> TextFieldValue) {
        val current = _state.value
        if (current is NoteEditUiState.Editing) {
            if (!suppressHistory) {
                pushHistory(current.value)
            }
            val updated = transform(current.value)
            // Update display name based on transformed content
            val displayName = if (isNewNote && actualPath == null) {
                OrgFileUtils.getDisplayName(updated.text, NEW_NOTE_DISPLAY_NAME)
            } else {
                OrgFileUtils.getDisplayName(updated.text, current.fileName)
            }
            _state.value = current.copy(
                value = updated,
                fileName = displayName,
                errorMessage = null,
            )
            suppressHistory = false
        }
    }

    private fun modifySelectedLines(transform: (String) -> String) {
        transformValue { value ->
            val text = value.text
            val selectionStart = value.selection.min
            val selectionEnd = value.selection.max
            val lineStart = if (selectionStart == 0) 0 else text.lastIndexOf('\n', selectionStart - 1).let { if (it == -1) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', selectionEnd).let { if (it == -1) text.length else it }
            val segment = text.substring(lineStart, lineEnd)
            val transformedSegment = segment.lines().joinToString("\n") { transform(it) }
            val newText = text.replaceRange(lineStart, lineEnd, transformedSegment)
            val newSelectionEnd = lineStart + transformedSegment.length
            value.copy(text = newText, selection = TextRange(lineStart, newSelectionEnd))
        }
    }

    private fun pushHistory(value: TextFieldValue) {
        if (history.size >= HISTORY_LIMIT) {
            history.removeFirst()
        }
        history.addLast(value)
    }

    private fun <T> ArrayDeque<T>.removeLastOrNull(): T? =
        if (isEmpty()) null else removeLast()

    object NoteArgs {
        const val NOTE_PATH = "note_path"
    }

    companion object {
        private const val TAG = "NoteEditViewModel"
        private const val HISTORY_LIMIT = 100
        const val NEW_NOTE_PATH = "__new__"
        // Minimal template - drawer will be added on first save with generated ID
        private const val NEW_NOTE_TEMPLATE = "#+title: \n\n"
        private const val NEW_NOTE_DISPLAY_NAME = "New note"
    }
}
