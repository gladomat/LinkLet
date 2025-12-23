package com.gladomat.linklet.viewmodel.settings

import com.gladomat.linklet.data.settings.WebDavSettings
import com.gladomat.linklet.data.settings.WebDavSettingsRepository
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.data.sync.WebDavRemoteSyncProvider
import com.gladomat.linklet.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebDavSettingsViewModelTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `save schedules initial sync when settings were not previously configured`() = runTest(dispatcherRule.dispatcher) {
        val settingsFlow = MutableStateFlow<WebDavSettings?>(null)
        val repository = mockk<WebDavSettingsRepository>()
        val provider = mockk<WebDavRemoteSyncProvider>(relaxed = true)
        val syncScheduler = mockk<SyncScheduler>(relaxed = true)

        every { repository.settingsFlow } returns settingsFlow
        every { repository.consumeResetDueToErrorFlag() } returns false
        coEvery { repository.currentSettings() } returns null
        coEvery { repository.setEnabled(any()) } returns Unit
        coEvery { repository.save(any()) } coAnswers {
            settingsFlow.value = firstArg()
        }

        val viewModel = WebDavSettingsViewModel(repository, provider, syncScheduler)

        viewModel.updateBaseUrl("https://example.com/remote.php/dav/files/user")
        viewModel.updateRootPath("/Roam")
        viewModel.updateUsername("user")
        viewModel.updatePassword("token")
        viewModel.updateEnabled(true)

        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.save(match { it.enabled && it.isConfigured() }) }
        verify(exactly = 1) { syncScheduler.scheduleInitial() }
    }

    @Test
    fun `save does not schedule initial sync when settings already exist`() = runTest(dispatcherRule.dispatcher) {
        val existing = WebDavSettings(
            baseUrl = "https://example.com/remote.php/dav/files/user",
            rootPath = "/Roam",
            username = "user",
            password = "token",
            enabled = true,
        )
        val settingsFlow = MutableStateFlow<WebDavSettings?>(existing)
        val repository = mockk<WebDavSettingsRepository>()
        val provider = mockk<WebDavRemoteSyncProvider>(relaxed = true)
        val syncScheduler = mockk<SyncScheduler>(relaxed = true)

        every { repository.settingsFlow } returns settingsFlow
        every { repository.consumeResetDueToErrorFlag() } returns false
        coEvery { repository.currentSettings() } returns existing
        coEvery { repository.setEnabled(any()) } returns Unit
        coEvery { repository.save(any()) } coAnswers {
            settingsFlow.value = firstArg()
        }

        val viewModel = WebDavSettingsViewModel(repository, provider, syncScheduler)

        viewModel.updatePassword("new-token")
        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.save(any()) }
        verify(exactly = 0) { syncScheduler.scheduleInitial() }
    }
}
