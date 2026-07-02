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

    enum class Pass1Completion {
        CONTINUE_PASS_1,
        START_PASS_2,
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Pass 1 worker starting id=$id attempt=$runAttemptCount")
        val result = processor.run(timeBudgetMillis = TIME_BUDGET_MILLIS)
        return result.fold(
            onSuccess = { outcome ->
                val pending = indexQueueDao.countByStatus(pass = PASS_1, status = IndexQueueStatus.PENDING)
                val done = indexQueueDao.countByStatus(pass = PASS_1, status = IndexQueueStatus.DONE)
                val failed = indexQueueDao.countByStatus(pass = PASS_1, status = IndexQueueStatus.FAILED)
                val running = indexQueueDao.countRunning(PASS_1)
                Log.d(
                    TAG,
                    "Pass 1 worker finished pending=$pending done=$done failed=$failed running=$running scanTruncated=${outcome.scanTruncated}",
                )
                if (pending > 0 || outcome.scanTruncated) {
                    schedulePass1Continuation()
                    Log.d(TAG, "Pass 1 worker scheduled continuation because pending work remains")
                } else {
                    schedulePass2()
                    Log.d(TAG, "Pass 1 worker scheduled pass 2")
                    if (running > 0) {
                        // Entries left RUNNING were orphaned by a killed worker. Re-run pass 1 once
                        // they age past the orphan-recovery window so they get requeued and
                        // finished — otherwise the indexing progress bar would never reach 100%.
                        schedulePass1Continuation(delayMillis = ORPHAN_RETRY_DELAY_MILLIS)
                        Log.w(TAG, "Pass 1 scheduled delayed continuation to recover $running orphaned entries")
                    }
                }
                Result.success()
            },
            onFailure = { error ->
                Log.e(TAG, "Pass 1 worker failed; retrying", error)
                Result.retry()
            },
        )
    }

    private fun schedulePass1Continuation(delayMillis: Long = 0L) {
        val pass1 = OneTimeWorkRequest.Builder(IndexPass1Worker::class.java)
            .addTag(IndexingWork.TAG)
            .apply { if (delayMillis > 0L) setInitialDelay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS) }
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            IndexingWork.UNIQUE_PASS1_NAME,
            PASS1_CONTINUATION_EXISTING_WORK_POLICY,
            pass1,
        )
    }

    private fun schedulePass2() {
        val pass2 = OneTimeWorkRequest.Builder(IndexPass2Worker::class.java)
            .addTag(IndexingWork.TAG)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            IndexingWork.UNIQUE_PASS2_NAME,
            PASS2_EXISTING_WORK_POLICY,
            pass2,
        )
    }

    companion object {
        internal fun completionForPendingCount(pending: Int): Pass1Completion =
            if (pending > 0) Pass1Completion.CONTINUE_PASS_1 else Pass1Completion.START_PASS_2

        internal val PASS1_CONTINUATION_EXISTING_WORK_POLICY = ExistingWorkPolicy.APPEND_OR_REPLACE
        internal val PASS2_EXISTING_WORK_POLICY = ExistingWorkPolicy.REPLACE
        private const val TAG = "IndexPass1Worker"
        private const val PASS_1 = 1
        private const val TIME_BUDGET_MILLIS = 20_000L
        // Must exceed IndexPass1Processor.ORPHAN_RECOVERY_MILLIS so the delayed re-run actually
        // requeues the orphaned entries instead of seeing them as still-fresh.
        private const val ORPHAN_RETRY_DELAY_MILLIS = 3 * 60 * 1000L
    }
}
