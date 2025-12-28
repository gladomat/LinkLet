package com.gladomat.linklet.data.index.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gladomat.linklet.data.index.IndexPass2Processor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class IndexPass2Worker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val processor: IndexPass2Processor,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val result = processor.run()
        return result.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}

