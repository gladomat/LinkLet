package com.gladomat.linklet.viewmodel.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.index.IndexResetService
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.settings.AppearanceSettingsRepository
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.data.settings.ThemeMode
import com.gladomat.linklet.data.settings.ThemePalette
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SettingsViewModelTests {

    @get:Rule
    val tempDir = TemporaryFolder()

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val folderSettingsRepository = FolderSettingsRepository(context)
    private val appearanceSettingsRepository = AppearanceSettingsRepository(context)

    @After
    fun tearDown() = runTest(dispatcherRule.dispatcher) {
        folderSettingsRepository.clearFolderUri()
        appearanceSettingsRepository.setThemeMode(ThemeMode.SYSTEM)
        appearanceSettingsRepository.setThemePalette(ThemePalette.AMBER)
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
        val indexResetService = mockk<IndexResetService>(relaxed = true)
        val viewModel = SettingsViewModel(folderSettingsRepository, syncScheduler, syncStateDao, syncSettingsRepository, indexResetService, appearanceSettingsRepository)

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
        val indexResetService = mockk<IndexResetService>(relaxed = true)
        val viewModel = SettingsViewModel(folderSettingsRepository, syncScheduler, syncStateDao, syncSettingsRepository, indexResetService, appearanceSettingsRepository)

        viewModel.requestManualSync()

        val message = awaitMessage(viewModel)
        verify(exactly = 1) { syncScheduler.scheduleManual() }
        assertTrue("message was $message", message.startsWith("Sync scheduled"))
    }

    @Test
    fun `updateThemeMode persists the choice and surfaces it in state`() = runTest(dispatcherRule.dispatcher) {
        val viewModel = SettingsViewModel(
            folderSettingsRepository,
            mockk<SyncScheduler>(relaxed = true),
            mockk<SyncStateDao>(relaxed = true),
            mockk<SyncSettingsRepository>(relaxed = true),
            mockk<IndexResetService>(relaxed = true),
            appearanceSettingsRepository,
        )

        viewModel.updateThemeMode(ThemeMode.DARK)

        val mode = viewModel.state.map { it.themeMode }.first { it == ThemeMode.DARK }
        assertEquals(ThemeMode.DARK, mode)
        assertEquals(ThemeMode.DARK, appearanceSettingsRepository.currentThemeMode())
    }

    @Test
    fun `updateThemePalette persists the choice and surfaces it in state`() = runTest(dispatcherRule.dispatcher) {
        val viewModel = SettingsViewModel(
            folderSettingsRepository,
            mockk<SyncScheduler>(relaxed = true),
            mockk<SyncStateDao>(relaxed = true),
            mockk<SyncSettingsRepository>(relaxed = true),
            mockk<IndexResetService>(relaxed = true),
            appearanceSettingsRepository,
        )

        viewModel.updateThemePalette(ThemePalette.CERULEAN)

        val palette = viewModel.state.map { it.themePalette }.first { it == ThemePalette.CERULEAN }
        assertEquals(ThemePalette.CERULEAN, palette)
        assertEquals(ThemePalette.CERULEAN, appearanceSettingsRepository.currentThemePalette())
    }
}
