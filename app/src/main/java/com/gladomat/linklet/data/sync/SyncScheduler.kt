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
import com.gladomat.linklet.data.sync.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager by lazy { WorkManager.getInstance(context) }

    fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequest.Builder(
            SyncWorker::class.java,
            15,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "LinkletPeriodicSync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    fun scheduleImmediate() {
        Log.d(TAG, "scheduleImmediate() called - creating sync work request")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediateRequest = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setConstraints(constraints)
            .build()

        Log.d(TAG, "Enqueuing immediate sync work with id: ${immediateRequest.id}")
        workManager.enqueueUniqueWork(
            "LinkletImmediateSync",
            ExistingWorkPolicy.REPLACE,  // Use REPLACE to ensure latest sync runs
            immediateRequest
        )
        Log.d(TAG, "Immediate sync work enqueued successfully")
    }

    companion object {
        private const val TAG = "SyncScheduler"
    }
}
