package com.gladomat.linklet.viewmodel.noteedit

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.text.TextRange
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditViewModelTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

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
}

private fun createRepository(
    content: String,
    saveResult: Result<Unit> = Result.success(Unit),
): INoteRepository =
    object : INoteRepository {
        override fun observeNotes() = MutableStateFlow(emptyList<Note>())
        override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
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
        override suspend fun saveNote(path: String, content: String): Result<Unit> = saveResult
    }
