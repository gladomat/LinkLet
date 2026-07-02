package com.gladomat.linklet.data.sync

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.gladomat.linklet.data.settings.SyncSettingsRepository
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var syncSettingsRepository: SyncSettingsRepository
    private lateinit var syncScheduler: SyncScheduler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Initialize WorkManager for testing
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            // Run BOTH the work executor and WorkManager's internal task executor synchronously, so
            // enqueueUniqueWork commits to the DB before getWorkInfos() is queried (the enqueue write
            // happens on the task executor, not the main looper).
            .setExecutor(androidx.work.testing.SynchronousExecutor())
            .setTaskExecutor(androidx.work.testing.SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        
        // Mock SyncSettingsRepository
        syncSettingsRepository = mockk<SyncSettingsRepository>()
        
        syncScheduler = SyncScheduler(context, syncSettingsRepository)
    }

    @Test
    fun `scheduleImmediate creates one-time work request`() = runTest {
        syncScheduler.scheduleImmediate()
        // Robolectric: let WorkManager's internal enqueue commit before querying.
        shadowOf(Looper.getMainLooper()).idle()

        val workInfos = workManager
            .getWorkInfosForUniqueWork(SyncWork.UNIQUE_ONE_TIME_NAME)
            .get()
        
        assertTrue("Work should be enqueued", workInfos.isNotEmpty())
        val workInfo = workInfos.first()
        // The synchronous test executor may run the unconstrained worker to a terminal state, so
        // accept any post-enqueue state (not a phantom). Existence proves the request was scheduled.
        assertTrue(
            "Work should be scheduled, state=${workInfo.state}",
            workInfo.state in setOf(
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.RUNNING,
                WorkInfo.State.SUCCEEDED,
                WorkInfo.State.FAILED,
            ),
        )
    }

    @Test
    fun `scheduleManual creates manual work request`() = runTest {
        syncScheduler.scheduleManual()
        // Robolectric: let WorkManager's internal enqueue commit before querying.
        shadowOf(Looper.getMainLooper()).idle()

        val workInfos = workManager
            .getWorkInfosForUniqueWork(SyncWork.UNIQUE_ONE_TIME_NAME)
            .get()
        
        assertTrue("Work should be enqueued", workInfos.isNotEmpty())
        val workInfo = workInfos.first()
        // The synchronous test executor may run the unconstrained worker to a terminal state, so
        // accept any post-enqueue state (not a phantom). Existence proves the request was scheduled.
        assertTrue(
            "Work should be scheduled, state=${workInfo.state}",
            workInfo.state in setOf(
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.RUNNING,
                WorkInfo.State.SUCCEEDED,
                WorkInfo.State.FAILED,
            ),
        )
    }

    @Test
    fun `schedulePeriodic with 60 minute interval creates periodic work`() = runTest {
        val intervalMinutes = 60L
        
        syncScheduler.schedulePeriodic(intervalMinutes)
        
        val workInfos = workManager
            .getWorkInfosForUniqueWork(SyncWork.UNIQUE_PERIODIC_TRIGGER_NAME)
            .get()
        
        assertTrue("Periodic work should be enqueued", workInfos.isNotEmpty())
        val workInfo = workInfos.first()
        assertTrue(
            "Work should be enqueued or running",
            workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING
        )
    }

    @Test
    fun `schedulePeriodic with 15 minute interval creates work with minimum interval`() = runTest {
        val intervalMinutes = 15L
        
        syncScheduler.schedulePeriodic(intervalMinutes)
        
        val workInfos = workManager
            .getWorkInfosForUniqueWork(SyncWork.UNIQUE_PERIODIC_TRIGGER_NAME)
            .get()
        
        assertTrue("Periodic work should be enqueued", workInfos.isNotEmpty())
        // WorkManager enforces 15 minute minimum, so this should succeed
    }

    @Test
    fun `schedulePeriodic replaces existing periodic work`() = runTest {
        // Schedule first periodic work
        syncScheduler.schedulePeriodic(60L)
        
        workManager
            .getWorkInfosForUniqueWork(SyncWork.UNIQUE_PERIODIC_TRIGGER_NAME)
            .get()
        
        // Schedule again with different interval - should UPDATE existing work
        syncScheduler.schedulePeriodic(120L)
        
        val secondWorkInfos = workManager
            .getWorkInfosForUniqueWork(SyncWork.UNIQUE_PERIODIC_TRIGGER_NAME)
            .get()
        
        // With UPDATE policy, the work should be replaced
        assertTrue("Periodic work should still be enqueued", secondWorkInfos.isNotEmpty())
    }

    @Test
    fun `cancelPeriodic cancels periodic work`() = runTest {
        // First schedule periodic work
        syncScheduler.schedulePeriodic(60L)
        
        var workInfos = workManager
            .getWorkInfosForUniqueWork(SyncWork.UNIQUE_PERIODIC_TRIGGER_NAME)
            .get()
        
        assertTrue("Work should be enqueued before cancel", workInfos.isNotEmpty())
        
        // Cancel periodic work
        syncScheduler.cancelPeriodic()
        
        workInfos = workManager
            .getWorkInfosForUniqueWork(SyncWork.UNIQUE_PERIODIC_TRIGGER_NAME)
            .get()
        
        // After cancel, work should be cancelled
        assertTrue("Work list should not be empty after cancel", workInfos.isNotEmpty())
        assertEquals(WorkInfo.State.CANCELLED, workInfos.first().state)
    }

    @Test
    fun `scheduleInitial creates one-time work request`() = runTest {
        syncScheduler.scheduleInitial()
        
        val immediateWorkInfos = workManager
            .getWorkInfosForUniqueWork(SyncWork.UNIQUE_ONE_TIME_NAME)
            .get()
        assertTrue("Immediate work should be enqueued", immediateWorkInfos.isNotEmpty())
    }
}
