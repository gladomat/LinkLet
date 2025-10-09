package com.gladomat.linklet.viewmodel.noteedit

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditViewModelTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `loadNote success transitions to editing`() = runTest {
        val repository = object : INoteRepository {
            override fun observeNotes() = MutableStateFlow(emptyList<Note>())
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.success(
                Note(
                    id = NoteId(path),
                    title = "Title",
                    content = "Content",
                    links = emptyList(),
                ),
            )
            override suspend fun reindex(): Result<Unit> = Result.success(Unit)
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
        }

        val viewModel = NoteEditViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteEditViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteEditUiState.Editing)
        state as NoteEditUiState.Editing
        assertEquals("Content", state.content)
    }

    @Test
    fun `save success emits saved state`() = runTest {
        val repository = object : INoteRepository {
            override fun observeNotes() = MutableStateFlow(emptyList<Note>())
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.success(
                Note(
                    id = NoteId(path),
                    title = "Title",
                    content = "Content",
                    links = emptyList(),
                ),
            )
            override suspend fun reindex(): Result<Unit> = Result.success(Unit)
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
        }

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
        val repository = object : INoteRepository {
            override fun observeNotes() = MutableStateFlow(emptyList<Note>())
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.success(
                Note(
                    id = NoteId(path),
                    title = "Title",
                    content = "Content",
                    links = emptyList(),
                ),
            )
            override suspend fun reindex(): Result<Unit> = Result.success(Unit)
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.failure(RuntimeException("boom"))
        }

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
}
