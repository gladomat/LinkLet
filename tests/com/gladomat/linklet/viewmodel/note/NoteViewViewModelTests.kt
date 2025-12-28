package com.gladomat.linklet.viewmodel.note

import androidx.lifecycle.SavedStateHandle
import com.gladomat.linklet.data.model.IndexingProgress
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteIndexEntry
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class NoteViewViewModelTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `loadNote emits success when repository returns note`() = runTest {
        val repository = object : INoteRepository {
            override fun observeNotes() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<NoteIndexEntry>())

            override fun observeIndexingProgress(pass: Int) =
                flowOf(IndexingProgress(completed = 0, total = 0))

            override fun observeIndexingFailures(pass: Int) = flowOf(0)

            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun listAllNotes(): List<Note> = emptyList()
            override suspend fun listTrashNotes(): List<Note> = emptyList()
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
                listOf(
                    LinkEntityDto(
                        source = "other",
                        target = path,
                        alias = null,
                        sourceTitle = "Other",
                    ),
                ),
            )

            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
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
            override fun observeNotes() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<NoteIndexEntry>())
            override fun observeIndexingProgress(pass: Int) =
                flowOf(IndexingProgress(completed = 0, total = 0))
            override fun observeIndexingFailures(pass: Int) = flowOf(0)
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun listAllNotes(): List<Note> = emptyList()
            override suspend fun listTrashNotes(): List<Note> = emptyList()
            override suspend fun getNote(path: String): Result<Note> = Result.failure(RuntimeException("boom"))
            override suspend fun reindex(): Result<Unit> = Result.success(Unit)
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
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
        val viewModel = NoteViewViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf(NoteViewViewModel.NoteArgs.NOTE_PATH to "path.org")),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteViewUiState.Error)
    }

    @Test
    fun `openNote updates saved path and reloads note`() = runTest {
        val repository = RecordingRepository()
        val savedStateHandle = SavedStateHandle(
            mapOf(NoteViewViewModel.NoteArgs.NOTE_PATH to "first.org"),
        )

        val viewModel = NoteViewViewModel(
            repository = repository,
            savedStateHandle = savedStateHandle,
        )

        advanceUntilIdle()
        viewModel.openNote("second.org")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is NoteViewUiState.Success)
        state as NoteViewUiState.Success
        assertEquals("second.org", state.note.id.path)
        assertEquals(listOf("first.org", "second.org"), repository.requestedPaths)
        assertEquals("second.org", savedStateHandle[NoteViewViewModel.NoteArgs.NOTE_PATH])
    }

    @Test
    fun `handleBackPress loads previous note when history exists`() = runTest {
        val repository = RecordingRepository()
        val savedStateHandle = SavedStateHandle(
            mapOf(NoteViewViewModel.NoteArgs.NOTE_PATH to "first.org"),
        )

        val viewModel = NoteViewViewModel(repository, savedStateHandle)

        advanceUntilIdle()
        viewModel.openNote("second.org")
        advanceUntilIdle()
        val handled = viewModel.handleBackPress()
        advanceUntilIdle()

        assertTrue(handled)
        val state = viewModel.state.value
        assertTrue(state is NoteViewUiState.Success)
        state as NoteViewUiState.Success
        assertEquals("first.org", state.note.id.path)
    }

    private class RecordingRepository : INoteRepository {
        val requestedPaths = mutableListOf<String>()

        override fun observeNotes() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<NoteIndexEntry>())

        override fun observeIndexingProgress(pass: Int) =
            flowOf(IndexingProgress(completed = 0, total = 0))

        override fun observeIndexingFailures(pass: Int) = flowOf(0)

        override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())

        override suspend fun listAllNotes(): List<Note> = emptyList()

        override suspend fun listTrashNotes(): List<Note> = emptyList()

        override suspend fun getNote(path: String): Result<Note> {
            requestedPaths += path
            return Result.success(
                Note(
                    id = NoteId(path),
                    title = "Title for $path",
                    content = "body",
                    links = emptyList(),
                ),
            )
        }

        override suspend fun reindex(): Result<Unit> = Result.success(Unit)

        override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())

        override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)

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
}
