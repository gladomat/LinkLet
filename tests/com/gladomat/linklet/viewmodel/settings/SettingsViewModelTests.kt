package com.gladomat.linklet.viewmodel.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.data.settings.SyncSettingsRepository
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.testing.MainDispatcherRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    fun tearDown() = runTest(dispatcherRule.dispatcher) {
        folderSettingsRepository.clearFolderUri()
        tempDir.delete()
    }

    private suspend fun awaitMessage(viewModel: SettingsViewModel): String =
        viewModel.state
            .map { it.message }
            .filterNotNull()
            .first()

    @Test
    fun `requestManualSync schedules work when folder selected`() = runTest(dispatcherRule.dispatcher) {
        val syncScheduler = mockk<SyncScheduler>(relaxed = true)
        val syncStateDao = mockk<SyncStateDao>(relaxed = true)
        val syncSettingsRepository = mockk<SyncSettingsRepository>(relaxed = true)
        val viewModel = SettingsViewModel(folderSettingsRepository, syncScheduler, syncStateDao, syncSettingsRepository)

        viewModel.requestManualSync()

        val message = awaitMessage(viewModel)
        verify(exactly = 1) { syncScheduler.scheduleManual() }
        assertTrue("message was $message", message.startsWith("Sync scheduled"))
    }

    @Test
    fun `requestManualSync reports error when folder not selected`() = runTest(dispatcherRule.dispatcher) {
        folderSettingsRepository.clearFolderUri()

        val syncScheduler = mockk<SyncScheduler>(relaxed = true)
        val syncStateDao = mockk<SyncStateDao>(relaxed = true)
        val syncSettingsRepository = mockk<SyncSettingsRepository>(relaxed = true)
        val viewModel = SettingsViewModel(folderSettingsRepository, syncScheduler, syncStateDao, syncSettingsRepository)

        viewModel.requestManualSync()

        val message = awaitMessage(viewModel)
        verify(exactly = 1) { syncScheduler.scheduleManual() }
        assertTrue("message was $message", message.startsWith("Sync scheduled"))
    }
}
