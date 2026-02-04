package com.gladomat.linklet.data.index.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gladomat.linklet.data.index.IndexingStateDao
import com.gladomat.linklet.data.index.IndexingStateEntity
import com.gladomat.linklet.data.index.IndexPass2Processor
import com.gladomat.linklet.data.index.IndexQueueDao
import com.gladomat.linklet.data.index.IndexQueueStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class IndexPass2Worker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val processor: IndexPass2Processor,
    private val indexQueueDao: IndexQueueDao,
    private val indexingStateDao: IndexingStateDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val pass1Pending = indexQueueDao.countByStatus(pass = PASS_1, status = IndexQueueStatus.PENDING)
        val state = indexingStateDao.get()
        if (!shouldRunPass2(pass1Pending, state?.lastPass1EnqueueAtEpochMillis, now, IDLE_WINDOW_MILLIS)) {
            return Result.retry()
        }

        val result = processor.run()
        return result.fold(
            onSuccess = {
                val existing = indexingStateDao.get()
                indexingStateDao.upsert(
                    IndexingStateEntity(
                        lastScanAtEpochMillis = existing?.lastScanAtEpochMillis,
                        lastPass1EnqueueAtEpochMillis = existing?.lastPass1EnqueueAtEpochMillis,
                        lastPass2RunAtEpochMillis = now,
                    ),
                )
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val PASS_1 = 1
        private const val IDLE_WINDOW_MILLIS = 10_000L
        private const val PASS1_PENDING_THRESHOLD = 0

        internal fun shouldRunPass2(
            pass1Pending: Int,
            lastPass1EnqueueAt: Long?,
            now: Long,
            idleWindowMillis: Long,
        ): Boolean {
            if (pass1Pending > PASS1_PENDING_THRESHOLD) return false
            if (lastPass1EnqueueAt == null) return true
            return now - lastPass1EnqueueAt > idleWindowMillis
        }
    }
}
