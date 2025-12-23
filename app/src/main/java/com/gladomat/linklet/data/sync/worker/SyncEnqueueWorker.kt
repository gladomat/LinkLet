package com.gladomat.linklet.data.sync.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gladomat.linklet.data.sync.SyncWork

private const val TAG = "SyncEnqueueWorker"

/**
 * Periodic work should never run sync logic directly; it should only enqueue a one-time sync
 * so we can't run multiple sync workers concurrently.
 */
class SyncEnqueueWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Enqueuing periodic sync")
        val request = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setInputData(workDataOf(SyncWorker.KEY_WORK_TYPE to SyncWorkType.PERIODIC.name))
            .addTag(SyncWork.TAG)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            SyncWork.UNIQUE_ONE_TIME_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
        return Result.success()
    }
}

