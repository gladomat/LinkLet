package com.gladomat.linklet.viewmodel.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.LinkEntityDto
import com.gladomat.linklet.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
        }
        val viewModel = SettingsViewModel(folderSettingsRepository, repository)

        val folder = tempDir.newFolder("notes")
        folderSettingsRepository.setFolderUri(Uri.fromFile(folder))
        advanceUntilIdle()

        viewModel.requestManualSync()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.message?.contains("Sync complete") == true)
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
        }
        val viewModel = SettingsViewModel(folderSettingsRepository, repository)

        val folder = tempDir.newFolder("notes")
        folderSettingsRepository.setFolderUri(Uri.fromFile(folder))
        advanceUntilIdle()

        viewModel.requestManualSync()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("boom", state.message)
        viewModel.clearMessage()
    }
}
