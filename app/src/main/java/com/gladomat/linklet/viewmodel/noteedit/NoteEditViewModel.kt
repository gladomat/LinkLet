package com.gladomat.linklet.viewmodel.noteedit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gladomat.linklet.domain.repository.INoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    init {
        loadNote()
    }

    fun loadNote() {
        if (isNewNote) {
            // For new notes, start with empty editing state
            history.clear()
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
                    NoteEditUiState.Editing(
                        value = TextFieldValue(
                            text = note.content,
                            selection = TextRange(note.content.length),
                        ),
                        fileName = notePath,
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
            _state.value = current.copy(value = newValue, errorMessage = null)
        }
    }

    fun save() {
        val current = _state.value
        if (current !is NoteEditUiState.Editing || current.isSaving) return
        viewModelScope.launch {
            _state.value = current.copy(isSaving = true, errorMessage = null)

            // Generate path for new notes if not already set
            val savePath = actualPath ?: generateNewNotePath().also { actualPath = it }

            val result = repository.saveNote(savePath, current.value.text)
            _state.value = result.fold(
                onSuccess = { NoteEditUiState.Saved(savePath) },
                onFailure = { error ->
                    current.copy(isSaving = false, errorMessage = error.message ?: "Failed to save note")
                },
            )
        }
    }

    private fun generateNewNotePath(): String {
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        return "$timestamp-untitled.org"
    }

    fun undo() {
        val current = _state.value
        if (current is NoteEditUiState.Editing) {
            val previous = history.removeLastOrNull() ?: return
            suppressHistory = true
            _state.value = current.copy(value = previous, errorMessage = null)
            suppressHistory = false
        }
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
            _state.value = current.copy(value = updated, errorMessage = null)
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
        private const val HISTORY_LIMIT = 100
        const val NEW_NOTE_PATH = "__new__"
        private const val NEW_NOTE_TEMPLATE = ":PROPERTIES:\n:ID:\n:END:\n#+title: \n\n"
        private const val NEW_NOTE_DISPLAY_NAME = "New note"
    }
}
