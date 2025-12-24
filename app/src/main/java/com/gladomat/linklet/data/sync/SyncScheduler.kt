package com.gladomat.linklet.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gladomat.linklet.data.settings.SyncSettingsRepository
import com.gladomat.linklet.data.sync.worker.SyncEnqueueWorker
import com.gladomat.linklet.data.sync.worker.SyncWorker
import com.gladomat.linklet.data.sync.worker.SyncWorkType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncSettingsRepository: SyncSettingsRepository
) {

    private val workManager by lazy { WorkManager.getInstance(context) }

    fun scheduleInitial() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setConstraints(constraints)
            .setInputData(workDataOf(SyncWorker.KEY_WORK_TYPE to SyncWorkType.INITIAL.name))
            .addTag(SyncWork.TAG)
            .build()

        workManager.enqueueUniqueWork(
            SyncWork.UNIQUE_ONE_TIME_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun scheduleManual() {
        val request = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setInputData(workDataOf(SyncWorker.KEY_WORK_TYPE to SyncWorkType.MANUAL.name))
            .addTag(SyncWork.TAG)
            .build()

        workManager.enqueueUniqueWork(
            SyncWork.UNIQUE_ONE_TIME_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun schedulePeriodic(intervalMinutes: Long = 60) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequest.Builder(
            SyncEnqueueWorker::class.java,
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(SyncWork.TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWork.UNIQUE_PERIODIC_TRIGGER_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
        Log.d(TAG, "Scheduled periodic sync with interval: $intervalMinutes minutes")
    }

    fun cancelPeriodic() {
        workManager.cancelUniqueWork(SyncWork.UNIQUE_PERIODIC_TRIGGER_NAME)
        Log.d(TAG, "Cancelled periodic sync")
    }

    fun scheduleImmediate() {
        Log.d(TAG, "scheduleImmediate() called")
        scheduleManual()
    }

    companion object {
        private const val TAG = "SyncScheduler"
    }
}
