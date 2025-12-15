package com.gladomat.linklet.viewmodel.note

import androidx.lifecycle.SavedStateHandle
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.domain.service.SearchOptions
import com.gladomat.linklet.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class NoteViewSearchTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `navigation wraps around`() = runTest {
        val viewModel = NoteViewViewModel(
            repository = FakeRepository(
                note = Note(
                    id = NoteId("note.org"),
                    title = "Title",
                    content = "hello hello",
                    links = emptyList(),
                ),
            ),
            savedStateHandle = SavedStateHandle(mapOf(NoteViewViewModel.NoteArgs.NOTE_PATH to "note.org")),
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("hello")
        advanceTimeBy(200)

        assertEquals(2, viewModel.searchState.value.totalMatches)
        assertEquals(1, viewModel.searchState.value.activeMatchNumber)

        viewModel.selectNextMatch()
        assertEquals(2, viewModel.searchState.value.activeMatchNumber)

        viewModel.selectNextMatch()
        assertEquals(1, viewModel.searchState.value.activeMatchNumber)

        viewModel.selectPrevMatch()
        assertEquals(2, viewModel.searchState.value.activeMatchNumber)
    }

    @Test
    fun `invalid regex surfaces error and clears matches`() = runTest {
        val viewModel = NoteViewViewModel(
            repository = FakeRepository(
                note = Note(
                    id = NoteId("note.org"),
                    title = "Title",
                    content = "abc",
                    links = emptyList(),
                ),
            ),
            savedStateHandle = SavedStateHandle(mapOf(NoteViewViewModel.NoteArgs.NOTE_PATH to "note.org")),
        )
        advanceUntilIdle()

        viewModel.setSearchOptions(SearchOptions(useRegex = true))
        viewModel.updateSearchQuery("(")
        advanceTimeBy(200)

        assertEquals(0, viewModel.searchState.value.totalMatches)
        assertTrue(viewModel.searchState.value.regexError?.isNotBlank() == true)
    }

    @Test
    fun `fast typing uses latest query after debounce`() = runTest {
        val viewModel = NoteViewViewModel(
            repository = FakeRepository(
                note = Note(
                    id = NoteId("note.org"),
                    title = "Title",
                    content = "alpha beta gamma",
                    links = emptyList(),
                ),
            ),
            savedStateHandle = SavedStateHandle(mapOf(NoteViewViewModel.NoteArgs.NOTE_PATH to "note.org")),
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("a")
        viewModel.updateSearchQuery("al")
        viewModel.updateSearchQuery("alp")
        viewModel.updateSearchQuery("alpha")

        advanceTimeBy(200)

        assertEquals("alpha", viewModel.searchState.value.query)
        assertNull(viewModel.searchState.value.regexError)
        assertEquals(1, viewModel.searchState.value.totalMatches)
        assertEquals(1, viewModel.searchState.value.activeMatchNumber)
    }

    private class FakeRepository(
        private val note: Note,
    ) : INoteRepository {
        override fun observeNotes() = MutableStateFlow(emptyList<Note>())
        override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
        override suspend fun getNote(path: String): Result<Note> = Result.success(note.copy(id = NoteId(path)))
        override suspend fun reindex(): Result<Unit> = Result.success(Unit)
        override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
        override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteNote(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun duplicateNote(path: String): Result<String> = Result.success("copy-$path")
        override suspend fun renameNote(oldPath: String, newPath: String): Result<Unit> = Result.success(Unit)
        override suspend fun getAllTags(): Result<List<String>> = Result.success(emptyList())
        override suspend fun updateNoteProperties(path: String, properties: Map<String, String>): Result<Unit> = Result.success(Unit)
        override suspend fun updateNoteTags(path: String, tags: List<String>): Result<Unit> = Result.success(Unit)
    }
}
