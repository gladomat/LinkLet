package com.gladomat.linklet.viewmodel.note

import androidx.lifecycle.SavedStateHandle
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewViewModelTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `loadNote emits success when repository returns note`() = runTest {
        val repository = object : INoteRepository {
            override fun observeNotes() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<Note>())
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.success(
                Note(
                    id = NoteId(path),
                    title = "Title",
                    content = "body",
                    links = emptyList(),
                ),
            )

            override suspend fun reindex(): Result<Unit> = Result.success(Unit)

            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(
                listOf(LinkEntityDto(source = "other", target = path, alias = null)),
            )

            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
        }
        val viewModel = NoteViewViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteViewViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteViewUiState.Success)
        state as NoteViewUiState.Success
        assertEquals("Title", state.note.title)
        assertEquals(1, state.backlinks.size)
    }

    @Test
    fun `loadNote emits error when repository fails`() = runTest {
        val repository = object : INoteRepository {
            override fun observeNotes() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<Note>())
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.failure(RuntimeException("boom"))
            override suspend fun reindex(): Result<Unit> = Result.success(Unit)
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
        }
        val viewModel = NoteViewViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteViewViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteViewUiState.Error)
    }
}
