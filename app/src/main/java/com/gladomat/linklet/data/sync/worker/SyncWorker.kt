package com.gladomat.linklet.data.sync.worker

import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.ForegroundInfo
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gladomat.linklet.data.settings.WebDavSettingsRepository
import com.gladomat.linklet.data.sync.CatastrophicDeleteException
import com.gladomat.linklet.data.sync.LocalStorageMisconfiguredException
import com.gladomat.linklet.data.sync.RequiresConfirmationException
import com.gladomat.linklet.data.sync.SyncDirectoryChangedException
import com.gladomat.linklet.data.sync.SyncPhase
import com.gladomat.linklet.data.sync.SyncProgress
import com.gladomat.linklet.data.sync.SyncEngine
import com.gladomat.linklet.data.sync.WebDavRemoteSyncProvider
import com.gladomat.linklet.domain.repository.INoteRepository
import com.thegrizzlylabs.sardineandroid.impl.SardineException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlin.math.max

private const val TAG = "SyncWorker"

private typealias WorkResult = androidx.work.ListenableWorker.Result

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val webDavRemoteSyncProvider: WebDavRemoteSyncProvider,
    private val webDavSettingsRepository: WebDavSettingsRepository,
    private val noteRepository: INoteRepository,
) : CoroutineWorker(appContext, workerParams) {

    init {
        Log.d(TAG, "SyncWorker constructor called - instance created successfully")
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "SyncWorker.doWork() started")

        val requestedType = inputData.getString(KEY_WORK_TYPE)
            ?.let { runCatching { SyncWorkType.valueOf(it) }.getOrNull() }
            ?: SyncWorkType.MANUAL

        val currentSettings = webDavSettingsRepository.currentSettings()
        val rootPath = currentSettings?.normalizedRootPath
        val initialSyncCompleted = rootPath != null && webDavSettingsRepository.hasCompletedInitialSync(rootPath)
        val webDavReady = webDavRemoteSyncProvider.isReadyForSync()
        val networkConnected = isNetworkConnected()

        val effectiveType = if (
            requestedType == SyncWorkType.INITIAL ||
            (!initialSyncCompleted && webDavReady && networkConnected)
        ) {
            SyncWorkType.INITIAL
        } else {
            requestedType
        }

        val notificationManager: NotificationManager? = if (effectiveType == SyncWorkType.INITIAL) {
            SyncWorkerNotifications.ensureChannel(applicationContext)
            val notification = SyncWorkerNotifications.build(
                context = applicationContext,
                title = "Initializing sync",
                text = "Starting…",
            )
            setForeground(ForegroundInfo(SyncWorkerNotifications.NOTIFICATION_ID, notification))
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        } else {
            null
        }

        val onProgress: suspend (SyncProgress) -> Unit = { progress ->
            val completed = progress.completed
            val total = progress.total
            val message = progress.message ?: progress.phase.name.lowercase()
            setProgress(
                workDataOf(
                    KEY_PROGRESS_PHASE to progress.phase.name,
                    KEY_PROGRESS_COMPLETED to (completed ?: -1),
                    KEY_PROGRESS_TOTAL to (total ?: -1),
                    KEY_PROGRESS_MESSAGE to message,
                ),
            )

            if (notificationManager != null) {
                val title = when (progress.phase) {
                    SyncPhase.DISCOVERY -> "Initializing sync"
                    SyncPhase.RECONCILE -> "Initializing sync"
                    SyncPhase.EXECUTE -> "Initializing sync"
                    SyncPhase.REINDEX -> "Indexing notes"
                }
                val notification = SyncWorkerNotifications.build(
                    context = applicationContext,
                    title = title,
                    text = message,
                    completed = completed?.let { max(it, 0) },
                    total = total?.let { max(it, 0) },
                )
                notificationManager.notify(SyncWorkerNotifications.NOTIFICATION_ID, notification)
            }
        }

        Log.i(
            TAG,
            "SyncWorker.doWork(): requestedType=$requestedType effectiveType=$effectiveType webdavReady=$webDavReady networkConnected=$networkConnected",
        )

        val shouldAttemptRemote = webDavReady && (effectiveType == SyncWorkType.INITIAL || networkConnected)
        val remoteResult: kotlin.Result<Unit> = if (!shouldAttemptRemote) {
            kotlin.Result.success(Unit)
        } else {
            syncEngine.run(webDavRemoteSyncProvider, onProgress).map { Unit }
        }

        onProgress(SyncProgress(phase = SyncPhase.REINDEX, message = "Reindexing notes"))
        val reindexResult = noteRepository.reindex()

        val reindexError = reindexResult.exceptionOrNull()
        if (reindexError != null) {
            Log.e(TAG, "Reindex failed", reindexError)
            SyncWorkerNotifications.notifyResult(applicationContext, "Sync failed", "Reindex failed: ${reindexError.localizedMessage}")
            return WorkResult.failure()
        }

        val remoteError = remoteResult.exceptionOrNull()
        if (remoteError == null) {
            if (effectiveType == SyncWorkType.INITIAL && shouldAttemptRemote && rootPath != null) {
                webDavSettingsRepository.markInitialSyncCompleted(rootPath)
            }
            Log.i(TAG, "Sync completed successfully")
            if (effectiveType == SyncWorkType.INITIAL) {
                SyncWorkerNotifications.notifyResult(applicationContext, "Sync complete", "Initial sync completed")
            }
            return WorkResult.success()
        }

        Log.e(TAG, "Remote sync failed", remoteError)
        return when (remoteError) {
            is SyncDirectoryChangedException -> {
                SyncWorkerNotifications.notifyResult(applicationContext, "Sync blocked", remoteError.message ?: "Sync directory changed")
                WorkResult.failure(
                    workDataOf(
                        KEY_ERROR_TYPE to ERROR_TYPE_DIRECTORY_CHANGED,
                        KEY_ERROR_MESSAGE to (remoteError.message ?: ""),
                        KEY_ERROR_OLD_PATH to (remoteError.oldPath ?: ""),
                        KEY_ERROR_NEW_PATH to remoteError.newPath,
                    ),
                )
            }
            is RequiresConfirmationException -> {
                SyncWorkerNotifications.notifyResult(applicationContext, "Sync needs confirmation", remoteError.message ?: "Confirmation required")
                WorkResult.failure(
                    workDataOf(
                        KEY_ERROR_TYPE to ERROR_TYPE_REQUIRES_CONFIRMATION,
                        KEY_ERROR_MESSAGE to (remoteError.message ?: ""),
                        KEY_ERROR_PENDING_DELETES to remoteError.pendingDeletesCount,
                    ),
                )
            }
            is CatastrophicDeleteException -> {
                SyncWorkerNotifications.notifyResult(applicationContext, "Sync aborted", remoteError.message ?: "Catastrophic delete prevented")
                WorkResult.failure()
            }
            is LocalStorageMisconfiguredException -> {
                SyncWorkerNotifications.notifyResult(applicationContext, "Sync failed", remoteError.message ?: "Local storage misconfigured")
                WorkResult.failure()
            }
            is SardineException -> {
                if (remoteError.statusCode == 401 || remoteError.statusCode == 403) {
                    SyncWorkerNotifications.notifyResult(applicationContext, "Sync failed", "Authentication failed (${remoteError.statusCode})")
                    WorkResult.failure()
                } else {
                    SyncWorkerNotifications.notifyResult(applicationContext, "Sync failed", "WebDAV error (${remoteError.statusCode})")
                    WorkResult.failure()
                }
            }
            is IOException -> {
                SyncWorkerNotifications.notifyResult(applicationContext, "Sync delayed", "Network error; will retry")
                WorkResult.retry()
            }
            else -> {
                SyncWorkerNotifications.notifyResult(applicationContext, "Sync failed", remoteError.localizedMessage ?: "Unknown error")
                WorkResult.failure()
            }
        }
    }

    companion object {
        const val KEY_WORK_TYPE = "sync_work_type"
        const val KEY_PROGRESS_PHASE = "sync_phase"
        const val KEY_PROGRESS_COMPLETED = "sync_completed"
        const val KEY_PROGRESS_TOTAL = "sync_total"
        const val KEY_PROGRESS_MESSAGE = "sync_message"

        const val KEY_ERROR_TYPE = "sync_error_type"
        const val KEY_ERROR_MESSAGE = "sync_error_message"
        const val KEY_ERROR_OLD_PATH = "sync_error_old_path"
        const val KEY_ERROR_NEW_PATH = "sync_error_new_path"
        const val KEY_ERROR_PENDING_DELETES = "sync_error_pending_deletes"

        const val ERROR_TYPE_DIRECTORY_CHANGED = "directory_changed"
        const val ERROR_TYPE_REQUIRES_CONFIRMATION = "requires_confirmation"
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
