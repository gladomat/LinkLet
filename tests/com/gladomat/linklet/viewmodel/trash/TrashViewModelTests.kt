package com.gladomat.linklet.viewmodel.trash

import com.gladomat.linklet.data.model.IndexingProgress
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteIndexEntry
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `deleteAllForever deletes all trash notes`() = runTest {
        val notes = listOf(
            Note(id = NoteId("_trash_bin/1_a.org"), title = "A", content = "", links = emptyList()),
            Note(id = NoteId("_trash_bin/2_b.org"), title = "B", content = "", links = emptyList()),
        )
        val deleted = mutableListOf<String>()
        val repository = object : INoteRepository {
            override fun observeNotes() = MutableStateFlow(emptyList<NoteIndexEntry>())
            override fun observeIndexingProgress(pass: Int) =
                flowOf(IndexingProgress(completed = 0, total = 0))
            override fun observeIndexingFailures(pass: Int) = flowOf(0)
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.failure(RuntimeException("unused"))
            override suspend fun reindex(): Result<Unit> = Result.success(Unit)
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
            override suspend fun deleteNoteSoft(path: String): Result<Unit> = Result.success(Unit)
            override suspend fun restoreNoteFromTrash(trashPath: String): Result<String> = Result.success(trashPath)
            override suspend fun deleteNotePermanent(path: String): Result<Unit> {
                deleted += path
                return Result.success(Unit)
            }
            override suspend fun deleteNote(path: String): Result<Unit> = Result.success(Unit)
            override suspend fun listAllNotes(): List<Note> = emptyList()
            override suspend fun listTrashNotes(): List<Note> = notes
            override suspend fun duplicateNote(path: String): Result<String> = Result.success("copy-$path")
            override suspend fun renameNote(oldPath: String, newPath: String): Result<Unit> = Result.success(Unit)
            override suspend fun getAllTags(): Result<List<String>> = Result.success(emptyList())
            override suspend fun updateNoteProperties(path: String, properties: Map<String, String>): Result<Unit> = Result.success(Unit)
            override suspend fun updateNoteTags(path: String, tags: List<String>): Result<Unit> = Result.success(Unit)
            override suspend fun resolveStorageUri(path: String): Result<android.net.Uri> =
                Result.failure(UnsupportedOperationException("Not used in these tests"))
            override fun observeGraph(center: NoteId?, hopDepth: Int) =
                flowOf(com.gladomat.linklet.domain.repository.GraphSnapshot(emptyList(), emptyList(), emptyMap()))
            override suspend fun saveGraphPositions(
                positions: Map<String, com.gladomat.linklet.domain.repository.GraphPoint>,
            ): Result<Unit> = Result.success(Unit)
        }

        val viewModel = TrashViewModel(repository)
        advanceUntilIdle()

        viewModel.deleteAllForever()
        advanceUntilIdle()

        assertEquals(setOf("_trash_bin/1_a.org", "_trash_bin/2_b.org"), deleted.toSet())
    }
}
