package com.gladomat.linklet.data.index.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gladomat.linklet.data.index.IndexingWork
import com.gladomat.linklet.data.index.IndexPass1Processor
import com.gladomat.linklet.data.index.worker.IndexPass2Worker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class IndexPass1Worker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val processor: IndexPass1Processor,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val result = processor.run()
        return result.fold(
            onSuccess = {
                val pass2 = OneTimeWorkRequest.Builder(IndexPass2Worker::class.java)
                    .addTag(IndexingWork.TAG)
                    .build()
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    IndexingWork.UNIQUE_PASS2_NAME,
                    ExistingWorkPolicy.KEEP,
                    pass2,
                )
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }

}
