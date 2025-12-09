package com.gladomat.linklet.viewmodel

import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.gladomat.linklet.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModelTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `refresh emits success when repository returns data`() = runTest {
        val repository = object : INoteRepository {
            private val notes = MutableStateFlow<List<Note>>(emptyList())

            override fun observeNotes() = notes

            override suspend fun listNotes(): Result<List<Note>> = Result.success(notes.value)
            override suspend fun getNote(path: String): Result<Note> = Result.failure(UnsupportedOperationException())
            override suspend fun reindex(): Result<Unit> {
                notes.value = listOf(
                    Note(
                        id = NoteId("note.org"),
                        title = "Note",
                        content = "",
                        links = emptyList(),
                    ),
                )
                return Result.success(Unit)
            }
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
            override suspend fun deleteNote(path: String): Result<Unit> = Result.success(Unit)
        }

        val viewModel = NoteListViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteListUiState.Success)
        val notes = (state as NoteListUiState.Success).notes
        assertEquals(1, notes.size)
        assertEquals("note.org", notes.first().id.path)
    }

    @Test
    fun `refresh emits error when reindex fails`() = runTest {
        val repository = object : INoteRepository {
            private val notes = MutableStateFlow<List<Note>>(emptyList())
            override fun observeNotes() = notes
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.failure(RuntimeException("unused"))
            override suspend fun reindex(): Result<Unit> = Result.failure(RuntimeException("index failure"))
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
            override suspend fun deleteNote(path: String): Result<Unit> = Result.success(Unit)
        }

        val viewModel = NoteListViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteListUiState.Error)
    }

    @Test
    fun `refresh success with empty notes emits success state`() = runTest {
        val repository = object : INoteRepository {
            private val notes = MutableStateFlow<List<Note>>(emptyList())
            override fun observeNotes() = notes
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.failure(RuntimeException("unused"))
            override suspend fun reindex(): Result<Unit> = Result.success(Unit)
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
            override suspend fun deleteNote(path: String): Result<Unit> = Result.success(Unit)
        }

        val viewModel = NoteListViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteListUiState.Success)
        val notes = (state as NoteListUiState.Success).notes
        assertTrue(notes.isEmpty())
    }

    @Test
    fun `search query filters notes and produces snippet`() = runTest {
        val notes = MutableStateFlow<List<Note>>(
            listOf(
                Note(
                    id = NoteId("a.org"),
                    title = "Kotlin Coroutines",
                    content = """
                        #+title: Kotlin Coroutines
                        #+filetags: :kotlin:async:

                        This note talks about structured concurrency and coroutine scopes.
                    """.trimIndent(),
                    links = emptyList(),
                ),
                Note(
                    id = NoteId("b.org"),
                    title = "Random Thoughts",
                    content = "Nothing related here.",
                    links = emptyList(),
                ),
            ),
        )

        val repository = object : INoteRepository {
            override fun observeNotes() = notes
            override suspend fun listNotes(): Result<List<Note>> = Result.success(notes.value)
            override suspend fun getNote(path: String): Result<Note> = Result.failure(RuntimeException("unused"))
            override suspend fun reindex(): Result<Unit> = Result.success(Unit)
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
            override suspend fun deleteNote(path: String): Result<Unit> = Result.success(Unit)
        }

        val viewModel = NoteListViewModel(repository)

        advanceUntilIdle()

        viewModel.updateSearchQuery("coroutines")

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteListUiState.Success)
        val items = (state as NoteListUiState.Success).notes
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("a.org", item.id.path)
        assertTrue(!item.snippet.isNullOrBlank())
    }
}
