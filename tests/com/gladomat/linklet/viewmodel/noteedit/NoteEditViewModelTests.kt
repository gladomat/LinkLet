package com.gladomat.linklet.viewmodel.noteedit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditViewModelTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    // ================================
    // Existing Note Tests
    // ================================

    @Test
    fun `loadNote success transitions to editing`() = runTest {
        val repository = createRepository(content = "Content")

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteEditUiState.Editing)
        state as NoteEditUiState.Editing
        assertEquals("Content", state.value.text)
    }

    @Test
    fun `save success emits saved state`() = runTest {
        val repository = createRepository(content = "Content")

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteEditUiState.Saved)
        state as NoteEditUiState.Saved
        assertEquals("path.org", state.path)
    }

    @Test
    fun `save failure shows error`() = runTest {
        val repository = createRepository(content = "Content", saveResult = Result.failure(RuntimeException("boom")))

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteEditUiState.Editing)
        state as NoteEditUiState.Editing
        assertEquals("boom", state.errorMessage)
    }

    // ================================
    // New Note Tests
    // ================================

    @Test
    fun `new note starts with minimal template`() = runTest {
        val viewModel = createNewNoteViewModel()

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteEditUiState.Editing)
        state as NoteEditUiState.Editing
        assertTrue("Template should contain #+title:", state.value.text.contains("#+title:"))
    }

    @Test
    fun `new note has New note as initial display name`() = runTest {
        val viewModel = createNewNoteViewModel()

        advanceUntilIdle()

        val state = viewModel.state.value as NoteEditUiState.Editing
        assertEquals("New note", state.fileName)
    }

    @Test
    fun `new note display name updates when title is edited`() = runTest {
        val viewModel = createNewNoteViewModel()

        advanceUntilIdle()

        val editing = viewModel.state.value as NoteEditUiState.Editing
        viewModel.updateContent(
            editing.value.copy(text = "#+title: My Custom Title\n\nContent"),
        )

        val updated = viewModel.state.value as NoteEditUiState.Editing
        assertEquals("My Custom Title", updated.fileName)
    }

    @Test
    fun `new note display name reverts to default when title cleared`() = runTest {
        val viewModel = createNewNoteViewModel()

        advanceUntilIdle()

        // Set a title
        val editing = viewModel.state.value as NoteEditUiState.Editing
        viewModel.updateContent(
            editing.value.copy(text = "#+title: My Title\n\nContent"),
        )

        // Clear the title
        val withTitle = viewModel.state.value as NoteEditUiState.Editing
        viewModel.updateContent(
            withTitle.value.copy(text = "#+title: \n\nContent"),
        )

        val cleared = viewModel.state.value as NoteEditUiState.Editing
        assertEquals("New note", cleared.fileName)
    }

    @Test
    fun `new note first save generates filename from title`() = runTest {
        var savedPath: String? = null
        val repository = createRepository(
            content = "",
            onSave = { path, _ -> savedPath = path },
        )

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to NoteEditViewModel.NEW_NOTE_PATH)),
        )

        advanceUntilIdle()

        // Set title content
        val editing = viewModel.state.value as NoteEditUiState.Editing
        viewModel.updateContent(
            editing.value.copy(text = "#+title: Test Note\n\nSome content"),
        )

        viewModel.save()
        advanceUntilIdle()

        assertTrue("Saved state should be emitted", viewModel.state.value is NoteEditUiState.Saved)
        val saved = viewModel.state.value as NoteEditUiState.Saved

        // Filename should follow pattern: yyyyMMddHH-test-note.org
        assertTrue("Path should end with .org", saved.path.endsWith(".org"))
        assertTrue("Path should contain slugified title", saved.path.contains("test-note"))
        assertTrue("Path should start with timestamp", saved.path.matches(Regex("^\\d{10}-.*")))
    }

    @Test
    fun `new note first save inserts ID into properties drawer`() = runTest {
        var savedContent: String? = null
        val repository = createRepository(
            content = "",
            onSave = { _, content -> savedContent = content },
        )

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to NoteEditViewModel.NEW_NOTE_PATH)),
        )

        advanceUntilIdle()

        val editing = viewModel.state.value as NoteEditUiState.Editing
        viewModel.updateContent(
            editing.value.copy(text = "#+title: Test\n\nContent"),
        )

        viewModel.save()
        advanceUntilIdle()

        assertTrue("Saved content should contain PROPERTIES drawer", savedContent!!.contains(":PROPERTIES:"))
        assertTrue("Saved content should contain ID property", savedContent!!.contains(":ID:"))
        assertTrue("Saved content should contain END", savedContent!!.contains(":END:"))
    }

    @Test
    fun `new note uses untitled in filename when title is empty`() = runTest {
        var savedPath: String? = null
        val repository = createRepository(
            content = "",
            onSave = { path, _ -> savedPath = path },
        )

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to NoteEditViewModel.NEW_NOTE_PATH)),
        )

        advanceUntilIdle()

        // Don't add a title, keep template as-is
        viewModel.save()
        advanceUntilIdle()

        assertTrue("Path should contain untitled", savedPath!!.contains("untitled"))
    }

    // ================================
    // Display Name Tests for Existing Notes
    // ================================

    @Test
    fun `existing note shows parsed title as display name`() = runTest {
        val content = "#+title: Existing Note Title\n\nContent"
        val repository = createRepository(content = content)

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()

        val state = viewModel.state.value as NoteEditUiState.Editing
        assertEquals("Existing Note Title", state.fileName)
    }

    @Test
    fun `existing note falls back to path when no title`() = runTest {
        val content = "Some content without title line"
        val repository = createRepository(content = content)

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()

        val state = viewModel.state.value as NoteEditUiState.Editing
        assertEquals("path.org", state.fileName)
    }

    @Test
    fun `existing note display name updates when title edited`() = runTest {
        val content = "#+title: Original Title\n\nContent"
        val repository = createRepository(content = content)

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()

        val editing = viewModel.state.value as NoteEditUiState.Editing
        viewModel.updateContent(
            editing.value.copy(text = "#+title: Updated Title\n\nContent"),
        )

        val updated = viewModel.state.value as NoteEditUiState.Editing
        assertEquals("Updated Title", updated.fileName)
    }

    // ================================
    // Formatting Tests
    // ================================

    @Test
    fun `insertBold wraps current selection`() = runTest {
        val repository = createRepository(content = "bold")

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()
        val editing = viewModel.state.value as NoteEditUiState.Editing
        val updatedValue = editing.value.copy(selection = TextRange(0, 4))
        viewModel.updateContent(updatedValue)
        viewModel.insertBold()
        val result = (viewModel.state.value as NoteEditUiState.Editing).value.text
        assertEquals("**bold**", result.trim())
    }

    @Test
    fun `insertUnorderedList inserts bullet marker`() = runTest {
        val repository = createRepository(content = "line")
        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()
        viewModel.insertUnorderedList()

        val result = (viewModel.state.value as NoteEditUiState.Editing).value.text
        assertEquals("line\n- ", result)
    }

    @Test
    fun `insertOrderedList inserts numbered marker`() = runTest {
        val repository = createRepository(content = "item")
        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()
        viewModel.insertOrderedList()

        val result = (viewModel.state.value as NoteEditUiState.Editing).value.text
        assertEquals("item\n1. ", result)
    }

    @Test
    fun `increaseIndentation prefixes selected lines with spaces`() = runTest {
        val repository = createRepository(content = "line1\nline2")
        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()
        val editing = viewModel.state.value as NoteEditUiState.Editing
        viewModel.updateContent(editing.value.copy(selection = TextRange(0, editing.value.text.length)))
        viewModel.increaseIndentation()

        val result = (viewModel.state.value as NoteEditUiState.Editing).value.text
        assertEquals("    line1\n    line2", result)
    }

    @Test
    fun `decreaseIndentation removes leading spaces`() = runTest {
        val indented = "    line1\n    line2"
        val repository = createRepository(content = indented)
        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()
        val editing = viewModel.state.value as NoteEditUiState.Editing
        viewModel.updateContent(editing.value.copy(selection = TextRange(0, editing.value.text.length)))
        viewModel.decreaseIndentation()

        val result = (viewModel.state.value as NoteEditUiState.Editing).value.text
        assertEquals("line1\nline2", result)
    }

    // ================================
    // Undo Tests
    // ================================

    @Test
    fun `undo restores previous content`() = runTest {
        val repository = createRepository(content = "original")
        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()
        val editing = viewModel.state.value as NoteEditUiState.Editing
        viewModel.updateContent(editing.value.copy(text = "changed"))

        viewModel.undo()

        val result = (viewModel.state.value as NoteEditUiState.Editing).value.text
        assertEquals("original", result)
    }

    @Test
    fun `undo updates display name to match restored content`() = runTest {
        val content = "#+title: Original\n\nContent"
        val repository = createRepository(content = content)
        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()

        // Change title
        val editing = viewModel.state.value as NoteEditUiState.Editing
        viewModel.updateContent(
            editing.value.copy(text = "#+title: Changed\n\nContent"),
        )

        val changed = viewModel.state.value as NoteEditUiState.Editing
        assertEquals("Changed", changed.fileName)

        // Undo
        viewModel.undo()

        val undone = viewModel.state.value as NoteEditUiState.Editing
        assertEquals("Original", undone.fileName)
    }

    // ================================
    // Helpers
    // ================================

    private fun createNewNoteViewModel(): NoteEditViewModel {
        val repository = createRepository(content = "")
        return NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(
                mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to NoteEditViewModel.NEW_NOTE_PATH),
            ),
        )
    }
}

private fun createRepository(
    content: String,
    saveResult: Result<Unit> = Result.success(Unit),
    onSave: (String, String) -> Unit = { _, _ -> },
): INoteRepository =
    object : INoteRepository {
        override fun observeNotes() = MutableStateFlow(emptyList<Note>())
        override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
        override suspend fun listAllNotes(): List<Note> = emptyList()
        override suspend fun listTrashNotes(): List<Note> = emptyList()
        override suspend fun getNote(path: String): Result<Note> = Result.success(
            Note(
                id = NoteId(path),
                title = "Title",
                content = content,
                links = emptyList(),
            ),
        )
        override suspend fun reindex(): Result<Unit> = Result.success(Unit)
        override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
        override suspend fun saveNote(path: String, content: String): Result<Unit> {
            onSave(path, content)
            return saveResult
        }
        override suspend fun deleteNoteSoft(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun restoreNoteFromTrash(trashPath: String): Result<String> = Result.success(trashPath)
        override suspend fun deleteNotePermanent(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteNote(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun duplicateNote(path: String): Result<String> = Result.success("copy-$path")
        override suspend fun renameNote(oldPath: String, newPath: String): Result<Unit> = Result.success(Unit)
        override suspend fun getAllTags(): Result<List<String>> = Result.success(emptyList())
        override suspend fun updateNoteProperties(path: String, properties: Map<String, String>): Result<Unit> = Result.success(Unit)
        override suspend fun updateNoteTags(path: String, tags: List<String>): Result<Unit> = Result.success(Unit)
        override suspend fun resolveStorageUri(path: String): Result<android.net.Uri> =
            Result.failure(UnsupportedOperationException("Not used in these tests"))
    }
