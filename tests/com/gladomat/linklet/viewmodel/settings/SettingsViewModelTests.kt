package com.gladomat.linklet.viewmodel.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.data.sync.SyncEngine
import com.gladomat.linklet.data.sync.SyncSummary
import com.gladomat.linklet.data.sync.WebDavRemoteSyncProvider
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SettingsViewModelTests {

    @get:Rule
    val tempDir = TemporaryFolder()

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val folderSettingsRepository = FolderSettingsRepository(context)

    @After
    fun tearDown() = runTest {
        folderSettingsRepository.clearFolderUri()
        tempDir.delete()
    }

    @Test
    fun `requestManualSync reports success message`() = runTest {
        val notes = MutableStateFlow<List<Note>>(emptyList())
        val repository = object : INoteRepository {
            override fun observeNotes() = notes
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.failure(RuntimeException("unused"))
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
        val syncEngine = mockk<SyncEngine>()
        val provider = mockk<WebDavRemoteSyncProvider>()
        val syncStateDao = mockk<SyncStateDao>(relaxed = true)
        coEvery { provider.isReadyForSync() } returns true
        coEvery { syncEngine.run(provider) } returns Result.success(
            SyncSummary(
                totalLocalNotes = 1,
                totalRemoteNotes = 1,
                pendingUploads = 0,
                pendingDownloads = 0,
                pendingDeletes = 0,
                conflicts = 0,
                resolvedConflicts = 0,
            ),
        )
        val viewModel = SettingsViewModel(folderSettingsRepository, repository, syncEngine, provider, syncStateDao)

        val folder = tempDir.newFolder("notes")
        folderSettingsRepository.setFolderUri(Uri.fromFile(folder))
        advanceUntilIdle()

        viewModel.requestManualSync()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("message was ${state.message}", state.message?.startsWith("Sync complete") == true)
        viewModel.clearMessage()
    }

    @Test
    fun `requestManualSync reports error when reindex fails`() = runTest {
        val notes = MutableStateFlow<List<Note>>(emptyList())
        val repository = object : INoteRepository {
            override fun observeNotes() = notes
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.failure(RuntimeException("unused"))
            override suspend fun reindex(): Result<Unit> = Result.failure(RuntimeException("boom"))
            override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = Result.success(emptyList())
            override suspend fun saveNote(path: String, content: String): Result<Unit> = Result.success(Unit)
            override suspend fun deleteNote(path: String): Result<Unit> = Result.success(Unit)
            override suspend fun duplicateNote(path: String): Result<String> = Result.success("copy-$path")
            override suspend fun renameNote(oldPath: String, newPath: String): Result<Unit> = Result.success(Unit)
            override suspend fun getAllTags(): Result<List<String>> = Result.success(emptyList())
            override suspend fun updateNoteProperties(path: String, properties: Map<String, String>): Result<Unit> = Result.success(Unit)
            override suspend fun updateNoteTags(path: String, tags: List<String>): Result<Unit> = Result.success(Unit)
        }
        val syncEngine = mockk<SyncEngine>()
        val provider = mockk<WebDavRemoteSyncProvider>()
        val syncStateDao = mockk<SyncStateDao>(relaxed = true)
        coEvery { provider.isReadyForSync() } returns true
        coEvery { syncEngine.run(provider) } returns Result.success(
            SyncSummary(0, 0, 0, 0, 0, 0, 0),
        )
        val viewModel = SettingsViewModel(folderSettingsRepository, repository, syncEngine, provider, syncStateDao)

        val folder = tempDir.newFolder("notes")
        folderSettingsRepository.setFolderUri(Uri.fromFile(folder))
        advanceUntilIdle()

        viewModel.requestManualSync()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.message?.contains("Sync failed") == true)
        viewModel.clearMessage()
    }

    @Test
    fun `requestManualSync runs local reindex when WebDAV disabled`() = runTest {
        val notes = MutableStateFlow<List<Note>>(emptyList())
        val repository = object : INoteRepository {
            override fun observeNotes() = notes
            override suspend fun listNotes(): Result<List<Note>> = Result.success(emptyList())
            override suspend fun getNote(path: String): Result<Note> = Result.failure(RuntimeException("unused"))
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
        val syncEngine = mockk<SyncEngine>(relaxed = true)
        val provider = mockk<WebDavRemoteSyncProvider>()
        val syncStateDao = mockk<SyncStateDao>(relaxed = true)
        coEvery { provider.isReadyForSync() } returns false
        val viewModel = SettingsViewModel(folderSettingsRepository, repository, syncEngine, provider, syncStateDao)

        val folder = tempDir.newFolder("notes")
        folderSettingsRepository.setFolderUri(Uri.fromFile(folder))
        advanceUntilIdle()

        assertNotNull("Folder should be selected before sync", viewModel.state.value.selectedFolder)

        viewModel.requestManualSync()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(
            "message was ${state.message}",
            state.message?.startsWith("Local reindex complete") == true,
        )
        viewModel.clearMessage()
    }
}
