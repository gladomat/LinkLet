package com.gladomat.linklet.data.sync.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gladomat.linklet.data.sync.CatastrophicDeleteException
import com.gladomat.linklet.data.sync.LocalStorageMisconfiguredException
import com.gladomat.linklet.data.sync.SyncEngine
import com.gladomat.linklet.data.sync.WebDavRemoteSyncProvider
import com.thegrizzlylabs.sardineandroid.impl.SardineException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

private const val TAG = "SyncWorker"

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val webDavRemoteSyncProvider: WebDavRemoteSyncProvider,
) : CoroutineWorker(appContext, workerParams) {

    init {
        Log.d(TAG, "SyncWorker constructor called - instance created successfully")
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "SyncWorker.doWork() started - about to check WebDAV configuration")

        if (!webDavRemoteSyncProvider.isReadyForSync()) {
            Log.i(TAG, "WebDAV not configured or enabled. Skipping sync.")
            return Result.success()
        }

        val result = syncEngine.run(webDavRemoteSyncProvider)

        return result.fold(
            onSuccess = {
                Log.i(TAG, "Sync completed successfully")
                Result.success()
            },
            onFailure = { error ->
                Log.e(TAG, "Sync failed", error)
                when {
                    error is CatastrophicDeleteException -> {
                        Log.e(TAG, "Catastrophic delete detected. Failing worker to prevent retries.")
                        Result.failure()
                    }
                    error is LocalStorageMisconfiguredException -> {
                        Log.e(TAG, "Local storage misconfigured. Failing worker to prevent retries.")
                        Result.failure()
                    }
                    error is SardineException && (error.statusCode == 401 || error.statusCode == 403) -> {
                        Log.e(TAG, "Authentication error (${error.statusCode}). Failing worker.")
                        Result.failure()
                    }
                    error is IOException -> {
                        Log.w(TAG, "Network/IO error. Retrying...")
                        Result.retry()
                    }
                    else -> {
                        Log.e(TAG, "Unknown error. Failing worker.")
                        Result.failure()
                    }
                }
            }
        )
    }
}
