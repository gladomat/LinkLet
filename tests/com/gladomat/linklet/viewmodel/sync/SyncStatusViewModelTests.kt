package com.gladomat.linklet.viewmodel.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.data.sync.SyncStatus
import com.gladomat.linklet.data.sync.SyncStatusRepository
import com.gladomat.linklet.data.sync.SyncStatusType
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import com.gladomat.linklet.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncStatusViewModelTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `clear and continue clears status and reschedules`() = runTest(dispatcherRule.dispatcher) {
        val repository = SyncStatusRepository(context)
        repository.clearStatus()
        repository.setStatus(
            SyncStatus(
                type = SyncStatusType.DIRECTORY_CHANGED,
                title = "Sync blocked",
                message = "Directory changed",
                oldPath = "/old",
                newPath = "/new",
                requiresAction = true,
                updatedAtEpochMillis = 1234L,
            ),
        )

        val syncStateDao = mockk<SyncStateDao>(relaxed = true)
        val syncScheduler = mockk<SyncScheduler>(relaxed = true)
        val viewModel = SyncStatusViewModel(repository, syncStateDao, syncScheduler)

        viewModel.clearAndContinue()

        coVerify(exactly = 1) { syncStateDao.clearAllStates() }
        verify(exactly = 1) { syncScheduler.scheduleManual() }
        assertNull(repository.statusFlow.first())
    }
}
