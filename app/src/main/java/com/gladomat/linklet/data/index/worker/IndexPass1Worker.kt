package com.gladomat.linklet.data.index.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gladomat.linklet.data.index.IndexingWork
import com.gladomat.linklet.data.index.IndexPass1Processor
import com.gladomat.linklet.data.index.IndexQueueDao
import com.gladomat.linklet.data.index.IndexQueueStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class IndexPass1Worker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val processor: IndexPass1Processor,
    private val indexQueueDao: IndexQueueDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Pass 1 worker starting id=$id attempt=$runAttemptCount")
        val result = processor.run(timeBudgetMillis = TIME_BUDGET_MILLIS)
        return result.fold(
            onSuccess = {
                val pending = indexQueueDao.countByStatus(pass = PASS_1, status = IndexQueueStatus.PENDING)
                val done = indexQueueDao.countByStatus(pass = PASS_1, status = IndexQueueStatus.DONE)
                val failed = indexQueueDao.countByStatus(pass = PASS_1, status = IndexQueueStatus.FAILED)
                Log.d(TAG, "Pass 1 worker finished pending=$pending done=$done failed=$failed")
                if (pending > 0) {
                    Log.d(TAG, "Pass 1 worker retrying because pending work remains")
                    Result.retry()
                } else {
                    val pass2 = OneTimeWorkRequest.Builder(IndexPass2Worker::class.java)
                        .addTag(IndexingWork.TAG)
                        .build()
                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        IndexingWork.UNIQUE_PASS2_NAME,
                        PASS2_EXISTING_WORK_POLICY,
                        pass2,
                    )
                    Log.d(TAG, "Pass 1 worker scheduled pass 2")
                    Result.success()
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Pass 1 worker failed; retrying", error)
                Result.retry()
            },
        )
    }

    companion object {
        internal val PASS2_EXISTING_WORK_POLICY = ExistingWorkPolicy.APPEND_OR_REPLACE
        private const val TAG = "IndexPass1Worker"
        private const val PASS_1 = 1
        private const val TIME_BUDGET_MILLIS = 20_000L
    }
}
