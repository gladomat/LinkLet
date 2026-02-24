package com.gladomat.linklet.data.sync

import android.util.Log
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.settings.WebDavSettings
import com.gladomat.linklet.data.settings.WebDavSettingsRepository
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.sync.metrics.SyncMetricKeys
import com.gladomat.linklet.data.sync.metrics.SyncMetrics
import com.gladomat.linklet.data.sync.planner.ReconcilePlanner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.Charsets

private const val TAG = "SyncEngine"
private const val TRASH_DIR = "_trash_bin"

class SyncDirectoryChangedException(
    val oldPath: String?,
    val newPath: String,
    message: String,
) : Exception(message)

@Singleton
class SyncEngine @Inject constructor(
    private val storage: IStorage,
    private val syncStateDao: SyncStateDao,
    private val webDavSettingsRepository: WebDavSettingsRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val metrics: SyncMetrics,
) {
    private val reconcilePlanner = ReconcilePlanner()

    suspend fun preflight(provider: RemoteSyncProvider): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val currentSettings = webDavSettingsRepository.currentSettings()
            checkDirectoryChange(provider, currentSettings)
        }
    }

    suspend fun run(
        provider: RemoteSyncProvider,
        onProgress: suspend (SyncProgress) -> Unit = {},
    ): Result<SyncSummary> = withContext(dispatcher) {
        Log.i(TAG, "Starting sync run with provider: ${provider.name}")
        runCatching {
            val currentSettings = webDavSettingsRepository.currentSettings()
            checkDirectoryChange(provider, currentSettings)

            // Fresh install detection is intentionally early: it allows us to skip expensive
            // whole-vault hashing and resolve overlaps deterministically.
            val isFreshInstall = syncStateDao.count() == 0
            if (isFreshInstall) {
                Log.i(TAG, "Fresh Install detected. Activating Pull Strategy.")
            }
            
            // Phase A: Discovery
            onProgress(SyncProgress(phase = SyncPhase.DISCOVERY, message = "Discovering changes"))
            val discoveryStartNanos = System.nanoTime()
            val discovery = discoverState(provider, isFreshInstall)
            metrics.timing(SyncMetricKeys.STAGE_DISCOVERY_MS, nanosToMillis(System.nanoTime() - discoveryStartNanos))

            // Phase B: Reconciliation
            onProgress(SyncProgress(phase = SyncPhase.RECONCILE, message = "Planning sync operations"))
            val reconcileStartNanos = System.nanoTime()
            val operations = reconcile(discovery, isFreshInstall)
            metrics.timing(SyncMetricKeys.STAGE_RECONCILE_MS, nanosToMillis(System.nanoTime() - reconcileStartNanos))

            // Phase C: Execution with Guard Rails
            onProgress(
                SyncProgress(
                    phase = SyncPhase.EXECUTE,
                    completed = 0,
                    total = operations.size,
                    message = "Applying changes",
                ),
            )
            val executeStartNanos = System.nanoTime()
            execute(operations, provider, discovery.remoteFiles.size, onProgress)
            metrics.timing(SyncMetricKeys.STAGE_EXECUTE_MS, nanosToMillis(System.nanoTime() - executeStartNanos))
            
            // Update last synced path after successful sync
            if (currentSettings != null && provider is WebDavRemoteSyncProvider) {
                webDavSettingsRepository.updateLastSyncedPath(currentSettings.normalizedRootPath)
            }

            // Generate Summary
            val pendingStates = syncStateDao.getPendingStates()
            SyncSummary(
                totalLocalNotes = discovery.localFiles.size,
                totalRemoteNotes = discovery.remoteFiles.size,
                pendingUploads = pendingStates.count { it.pendingAction == SyncPendingAction.UPLOAD },
                pendingDownloads = pendingStates.count { it.pendingAction == SyncPendingAction.DOWNLOAD },
                pendingDeletes = pendingStates.count { it.pendingAction == SyncPendingAction.DELETE },
                conflicts = pendingStates.count { it.pendingAction == SyncPendingAction.CONFLICT },
                resolvedConflicts = operations.count { it is SyncOperation.Conflict },
            )
        }.onSuccess {
            val snapshot = metrics.snapshotAndReset()
            if (snapshot.counts.isNotEmpty() || snapshot.timingsMs.isNotEmpty()) {
                Log.d(TAG, "Sync metrics counts=${snapshot.counts}, timingsMs=${snapshot.timingsMs}")
            }
        }.onFailure {
            Log.e(TAG, "Sync run failed", it)
        }
    }

    private fun nanosToMillis(durationNanos: Long): Long = durationNanos / 1_000_000L

    private suspend fun checkDirectoryChange(
        provider: RemoteSyncProvider,
        currentSettings: WebDavSettings?,
    ) {
        if (currentSettings == null || provider !is WebDavRemoteSyncProvider) return

        val currentPath = currentSettings.normalizedRootPath
        val lastSyncedPath = currentSettings.lastSyncedRootPath
        val syncStateCount = syncStateDao.count()

        if (detectDirectoryChange(currentPath, lastSyncedPath, syncStateCount)) {
            val message = if (lastSyncedPath == null) {
                "Sync state detected from previous version. To ensure data safety, please confirm you want to sync to '$currentPath'."
            } else {
                "WebDAV directory changed from '$lastSyncedPath' to '$currentPath'. Clear sync state to continue."
            }
            throw SyncDirectoryChangedException(
                oldPath = lastSyncedPath,
                newPath = currentPath,
                message = message,
            )
        }
    }

    // --- Phase A: Discovery ---

    data class DiscoveryResult(
        val localFiles: Map<String, LocalFile>,
        val remoteFiles: Map<String, RemoteNoteMetadata>,
        val syncStates: Map<String, SyncStateEntity>,
    )

    data class LocalFile(val path: String, val hash: String)

    private suspend fun discoverState(provider: RemoteSyncProvider, isFreshInstall: Boolean): DiscoveryResult {
        // 1. Local Files (excluding trash bin - that's local-only)
        val localPaths = storage.listFiles().getOrThrow()
            .filter { !it.startsWith(TRASH_DIR) }
            .filter { SyncPathFilter.shouldInclude(it) }
        Log.d(TAG, "DEBUG Discovery: Found ${localPaths.size} local paths (excluding $TRASH_DIR)")
        localPaths.forEach { path -> Log.d(TAG, "DEBUG Discovery: Local path: '$path'") }
        
        val localFiles = localPaths.mapNotNull { path ->
            if (isFreshInstall) {
                // No sync state exists yet, so we don't need hashes to plan work.
                // Hashing whole vaults can be extremely slow on large note sets.
                path to LocalFile(path, "")
            } else {
                val bytes = storage.readFileBytes(path).getOrNull()
                if (bytes != null) {
                    path to LocalFile(path, NoteHashCalculator.computeBytes(bytes))
                } else {
                    Log.w(TAG, "DEBUG Discovery: Could not read local file: '$path'")
                    null
                }
            }
        }.toMap()
        Log.d(TAG, "DEBUG Discovery: Successfully read ${localFiles.size} local files")

        // 2. Remote Files
        val remoteList = provider.listRemoteNotes().getOrThrow()
        val remoteFiles = remoteList.associateBy { it.path }
        Log.d(TAG, "DEBUG Discovery: Found ${remoteFiles.size} remote files")
        remoteFiles.forEach { (path, meta) -> 
            Log.d(TAG, "DEBUG Discovery: Remote path: '$path', fingerprint=${meta.fingerprint}")
        }

        // 3. Sync State (excluding trash bin files - they're local-only)
        val allSyncStates = syncStateDao.getAllStates()
        val syncStates = allSyncStates
            .filter { !it.path.startsWith(TRASH_DIR) }
            .associateBy { it.path }
        Log.d(TAG, "DEBUG Discovery: Found ${syncStates.size} sync states (excluding $TRASH_DIR)")
        syncStates.forEach { (path, state) ->
            Log.d(TAG, "DEBUG Discovery: Sync state: path='$path', lifecycle=${state.lifecycle}, remoteId=${state.remoteId}, lastSynced=${state.lastSyncedAtEpochMillis}")
        }
        
        // Clean up any orphan trash bin sync states (they shouldn't exist but let's be safe)
        val trashStates = allSyncStates.filter { it.path.startsWith(TRASH_DIR) }
        if (trashStates.isNotEmpty()) {
            Log.d(TAG, "DEBUG Discovery: Cleaning up ${trashStates.size} orphan trash bin sync states")
            trashStates.forEach { syncStateDao.deleteState(it.path) }
        }

        // GUARD: Detect local storage misconfiguration
        // If local storage is empty but we have sync states with lastSyncedAtEpochMillis,
        // this is likely a permission issue or folder misconfiguration, NOT intentional deletion
        if (localFiles.isEmpty() && syncStates.isNotEmpty()) {
            val syncedStates = syncStates.values.filter { it.lastSyncedAtEpochMillis != null }
            if (syncedStates.isNotEmpty()) {
                Log.e(TAG, "DEBUG Discovery: MISCONFIGURATION DETECTED - Local storage returned 0 files but ${syncedStates.size} sync states exist with lastSyncedAtEpochMillis")
                throw LocalStorageMisconfiguredException(
                    "Local storage returned 0 files but ${syncedStates.size} previously synced files are expected. " +
                    "Please check that the local notes folder is correctly configured and the app has permission to access it. " +
                    "If you intentionally removed all local files, clear sync state first via Settings."
                )
            }
        }

        // Log summary of path overlaps
        val allPaths = localFiles.keys + remoteFiles.keys + syncStates.keys
        Log.d(TAG, "DEBUG Discovery: Total unique paths to reconcile: ${allPaths.size}")
        Log.d(TAG, "DEBUG Discovery: Local-only paths: ${localFiles.keys - remoteFiles.keys - syncStates.keys}")
        Log.d(TAG, "DEBUG Discovery: Remote-only paths: ${remoteFiles.keys - localFiles.keys - syncStates.keys}")
        Log.d(TAG, "DEBUG Discovery: State-only paths: ${syncStates.keys - localFiles.keys - remoteFiles.keys}")
        Log.d(TAG, "DEBUG Discovery: Local+Remote overlap: ${localFiles.keys.intersect(remoteFiles.keys)}")

        return DiscoveryResult(localFiles, remoteFiles, syncStates)
    }

    private fun detectDirectoryChange(
        currentPath: String,
        lastSyncedPath: String?,
        syncStateCount: Int,
    ): Boolean {
        // Return true if:
        // - We have sync states (not fresh install)
        // - AND either:
        //   a) Last synced path is null (migration case - we have states but never tracked path before)
        //   b) Current path differs from last synced path
        val hasStates = syncStateCount > 0
        val pathChanged = lastSyncedPath != null && lastSyncedPath != currentPath
        val isMigration = lastSyncedPath == null && hasStates
        
        val shouldDetect = hasStates && (pathChanged || isMigration)
        
        Log.d(TAG, "Directory change detection: hasStates=$hasStates, lastSyncedPath=$lastSyncedPath, currentPath=$currentPath, pathChanged=$pathChanged, isMigration=$isMigration, shouldDetect=$shouldDetect")
        
        return shouldDetect
    }

    // --- Phase B: Reconciliation ---

    sealed class SyncOperation {
        abstract val path: String
        data class Upload(override val path: String, val reason: String) : SyncOperation()
        data class Download(
            override val path: String,
            val remoteId: String,
            val fingerprint: String?,
            val reason: String,
            val backupLocalIfDifferent: Boolean = false,
        ) : SyncOperation()
        data class DeleteRemote(override val path: String, val remoteId: String, val etag: String?) : SyncOperation()
        data class SoftDeleteLocal(override val path: String) : SyncOperation() // Move to trash
        data class Conflict(override val path: String, val remoteId: String, val fingerprint: String?) : SyncOperation()
        data class UpdateState(override val path: String) : SyncOperation() // Just update DB
        data class CleanupOrphanState(override val path: String) : SyncOperation() // Remove orphan sync state
    }

    private fun reconcile(discovery: DiscoveryResult, isFreshInstall: Boolean): List<SyncOperation> {
        Log.d(TAG, "DEBUG Reconcile: Starting reconciliation, isFreshInstall=$isFreshInstall")
        val operations = reconcilePlanner.plan(discovery, isFreshInstall)
        
        // Log operation summary
        val uploads = operations.filterIsInstance<SyncOperation.Upload>().size
        val downloads = operations.filterIsInstance<SyncOperation.Download>().size
        val deleteRemotes = operations.filterIsInstance<SyncOperation.DeleteRemote>().size
        val softDeletes = operations.filterIsInstance<SyncOperation.SoftDeleteLocal>().size
        val conflictOps = operations.filterIsInstance<SyncOperation.Conflict>()
        Log.i(TAG, "DEBUG Reconcile: Summary - uploads=$uploads, downloads=$downloads, deleteRemotes=$deleteRemotes, softDeletes=$softDeletes, conflicts=${conflictOps.size}")
        
        // Log all delete operations explicitly
        operations.filterIsInstance<SyncOperation.DeleteRemote>().forEach { op ->
            Log.w(TAG, "DEBUG Reconcile: DELETE_REMOTE scheduled for path='${op.path}'")
        }
        
        // Log all conflict operations explicitly
        if (conflictOps.isNotEmpty()) {
            Log.w(TAG, "DEBUG Reconcile: ${conflictOps.size} CONFLICT(s) will be resolved:")
            conflictOps.forEach { op ->
                Log.w(TAG, "DEBUG Reconcile:   - CONFLICT: '${op.path}' (will create conflicted copy)")
            }
        }
        
        return operations
    }

    // --- Phase C: Execution ---

    private suspend fun execute(
        operations: List<SyncOperation>,
        provider: RemoteSyncProvider,
        totalRemoteFiles: Int,
        onProgress: suspend (SyncProgress) -> Unit,
    ) {
        Log.d(TAG, "DEBUG Execute: Starting execution with ${operations.size} operations, totalRemoteFiles=$totalRemoteFiles")
        
        // GUARD RAILS
        val deleteOps = operations.filterIsInstance<SyncOperation.DeleteRemote>()
        Log.d(TAG, "DEBUG Execute: Found ${deleteOps.size} delete operations")
        
        if (deleteOps.isNotEmpty()) {
            Log.w(TAG, "DEBUG Execute: Delete operations detected:")
            deleteOps.forEach { op -> Log.w(TAG, "DEBUG Execute:   - DELETE '${op.path}' (remoteId=${op.remoteId})") }
            
            if (totalRemoteFiles <= 0) {
                throw CatastrophicDeleteException("Attempting remote deletes without a remote listing. Aborting.")
            }

            if (deleteOps.size == totalRemoteFiles) {
                Log.e(TAG, "DEBUG Execute: GUARD RAIL TRIGGERED - All $totalRemoteFiles remote files would be deleted!")
                throw CatastrophicDeleteException("Attempting to wipe 100% of remote files. Aborting.")
            }
            
            val deletePercentage = deleteOps.size.toDouble() / totalRemoteFiles.toDouble()
            Log.d(TAG, "DEBUG Execute: Delete percentage = $deletePercentage (${deleteOps.size}/$totalRemoteFiles)")
            if (deleteOps.size > 50) {
                Log.e(TAG, "DEBUG Execute: GUARD RAIL TRIGGERED - Too many deletes!")
                throw CatastrophicDeleteException("Attempting to delete ${deleteOps.size} remote files. Aborting.")
            }

            if (deletePercentage > 0.50) {
                Log.e(TAG, "DEBUG Execute: GUARD RAIL TRIGGERED - Delete percentage catastrophic!")
                val percentDisplay = "%.0f%%".format(deletePercentage * 100)
                throw CatastrophicDeleteException("Attempting to delete ${deleteOps.size} remote files ($percentDisplay). Aborting.")
            }

            if (deletePercentage > 0.20) {
                Log.e(TAG, "DEBUG Execute: GUARD RAIL TRIGGERED - Delete threshold exceeded, confirmation required")
                val percentDisplay = "%.0f%%".format(deletePercentage * 100)
                throw RequiresConfirmationException(
                    pendingDeletesCount = deleteOps.size,
                    message = "Sync wants to delete ${deleteOps.size} remote files ($percentDisplay). Confirmation required."
                )
            }
        }

        // Process
        val totalOperations = operations.size
        var completedOperations = 0
        for (op in operations) {
            when (op) {
                is SyncOperation.Upload -> performUpload(op, provider)
                is SyncOperation.Download -> performDownload(op, provider)
                is SyncOperation.DeleteRemote -> performRemoteDelete(op, provider)
                is SyncOperation.SoftDeleteLocal -> performSoftDelete(op)
                is SyncOperation.Conflict -> performConflictResolution(op, provider)
                is SyncOperation.UpdateState -> { /* Just ensure DB matches */ }
                is SyncOperation.CleanupOrphanState -> {
                    Log.d(TAG, "Cleaning up orphan sync state: ${op.path}")
                    syncStateDao.deleteState(op.path)
                }
            }

            completedOperations += 1
            if (totalOperations > 0 && (completedOperations == totalOperations || completedOperations % 25 == 0)) {
                onProgress(
                    SyncProgress(
                        phase = SyncPhase.EXECUTE,
                        completed = completedOperations,
                        total = totalOperations,
                        message = "Applying changes",
                    ),
                )
            }
        }
    }

    private suspend fun performUpload(op: SyncOperation.Upload, provider: RemoteSyncProvider) {
        Log.d(TAG, "Executing Upload: ${op.path}")
        val state = syncStateDao.getState(op.path)
        val bytes = storage.readFileBytes(op.path).getOrElse { return }
        val hash = NoteHashCalculator.computeBytes(bytes)
        
        // Assuming state exists or we create new
        val remoteId = state?.remoteId
        val expectedFingerprint = state?.remoteFingerprint
        
        provider.upload(op.path, bytes.inputStream(), remoteId, expectedFingerprint)
            .onSuccess { result ->
                val newState = (state ?: SyncStateEntity(op.path)).copy(
                    lifecycle = NoteLifecycle.ACTIVE,
                    remoteId = result.remoteId,
                    remoteFingerprint = result.fingerprint,
                    localContentHash = hash,
                    lastKnownHash = hash,
                    remoteETag = result.fingerprint, // Assuming fingerprint is ETag
                    lastSyncedAtEpochMillis = System.currentTimeMillis(),
                    pendingAction = SyncPendingAction.NONE,
                    lastError = null
                )
                syncStateDao.upsert(newState)
            }
            .onFailure {
                Log.e(TAG, "Upload failed", it)
                // Update state to reflect error?
            }
    }

    private suspend fun performDownload(op: SyncOperation.Download, provider: RemoteSyncProvider) {
        Log.d(TAG, "Executing Download: ${op.path} (fingerprint=${op.fingerprint})")
        val localBytesForBackup = if (op.backupLocalIfDifferent) {
            storage.readFileBytes(op.path).getOrNull()
        } else {
            null
        }

        provider.download(op.path, op.remoteId)
            .onSuccess { stream ->
                val bytes = stream.use { it.readBytes() }

                if (localBytesForBackup != null) {
                    val localHash = NoteHashCalculator.computeBytes(localBytesForBackup)
                    val remoteHash = NoteHashCalculator.computeBytes(bytes)
                    if (localHash != remoteHash) {
                        val conflictPath = createConflictPath(op.path)
                        Log.d(TAG, "DEBUG Download: Backing up differing local content to '$conflictPath'")
                        writeLocalBytes(conflictPath, localBytesForBackup)

                        val conflictState = SyncStateEntity(
                            path = conflictPath,
                            lifecycle = NoteLifecycle.ACTIVE,
                            localContentHash = localHash,
                            lastKnownHash = localHash,
                            pendingAction = SyncPendingAction.UPLOAD,
                        )
                        syncStateDao.upsert(conflictState)
                    }
                }

                writeLocalBytes(op.path, bytes)
                val hash = NoteHashCalculator.computeBytes(bytes)
                
                val state = syncStateDao.getState(op.path) ?: SyncStateEntity(op.path)
                val newState = state.copy(
                     lifecycle = NoteLifecycle.ACTIVE,
                     remoteId = op.remoteId,
                     localContentHash = hash,
                     lastKnownHash = hash,
                     remoteFingerprint = op.fingerprint, // Save the fingerprint from discovery
                     remoteETag = op.fingerprint,
                     lastSyncedAtEpochMillis = System.currentTimeMillis(),
                     pendingAction = SyncPendingAction.NONE,
                     lastError = null
                )
                syncStateDao.upsert(newState)
                Log.d(TAG, "DEBUG Download: Saved state for ${op.path} with fingerprint=${op.fingerprint}")
            }
            .onFailure { e ->
                Log.e(TAG, "Download failed for ${op.path}", e)
            }
    }

    private suspend fun performRemoteDelete(op: SyncOperation.DeleteRemote, provider: RemoteSyncProvider) {
        Log.d(TAG, "Executing Safe Remote Delete: ${op.path} with ETag ${op.etag}")
        provider.delete(op.path, op.remoteId, op.etag)
            .onSuccess {
                syncStateDao.deleteState(op.path)
            }
            .onFailure {
                Log.e(TAG, "Delete failed", it)
                // If 412, we should mark for resync
            }
    }

    private suspend fun performSoftDelete(op: SyncOperation.SoftDeleteLocal) {
        Log.d(TAG, "Executing Soft Delete (Move to Trash): ${op.path}")
        runCatching {
            val trashPath = "$TRASH_DIR/${op.path}"
            val bytes = storage.readFileBytes(op.path).getOrThrow()
            writeLocalBytes(trashPath, bytes)
            storage.deleteNote(op.path)
            
            val state = syncStateDao.getState(op.path)
            if (state != null) {
                val newState = state.copy(
                    lifecycle = NoteLifecycle.DELETED_REMOTELY,
                    deletedAt = System.currentTimeMillis()
                )
                syncStateDao.upsert(newState)
            }
        }
    }

    private suspend fun performConflictResolution(op: SyncOperation.Conflict, provider: RemoteSyncProvider) {
        Log.i(TAG, "DEBUG Conflict: Resolving conflict for ${op.path}")
        // 1. Rename local to "Conflicted Copy"
        val localBytes = storage.readFileBytes(op.path).getOrNull()
        Log.d(TAG, "DEBUG Conflict: Local content exists=${localBytes != null}, length=${localBytes?.size ?: 0}")
        
        if (localBytes != null) {
            val conflictPath = createConflictPath(op.path)
            Log.d(TAG, "DEBUG Conflict: Creating conflicted copy at '$conflictPath'")
            writeLocalBytes(conflictPath, localBytes)
            // Create new sync state for conflict copy so it gets uploaded
            val conflictHash = NoteHashCalculator.computeBytes(localBytes)
            val conflictState = SyncStateEntity(
                path = conflictPath,
                lifecycle = NoteLifecycle.ACTIVE,
                localContentHash = conflictHash,
                lastKnownHash = conflictHash,
                pendingAction = SyncPendingAction.UPLOAD
            )
            syncStateDao.upsert(conflictState)
            Log.d(TAG, "DEBUG Conflict: Conflict copy created and sync state saved")
        } else {
            Log.w(TAG, "DEBUG Conflict: No local content to backup for ${op.path}")
        }
        
        // 2. Download remote version to original path
        Log.d(TAG, "DEBUG Conflict: Downloading remote version to original path ${op.path}")
        performDownload(
            SyncOperation.Download(
                path = op.path,
                remoteId = op.remoteId,
                fingerprint = op.fingerprint,
                reason = "Conflict Resolution",
            ),
            provider,
        )
    }

    private fun createConflictPath(originalPath: String): String {
        val dir = originalPath.substringBeforeLast('/', "")
        val filename = originalPath.substringAfterLast('/')
        val name = filename.substringBeforeLast('.')
        val extension = if (filename.contains('.')) "." + filename.substringAfterLast('.') else ""

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date())
        val conflictName = "$name (Conflicted Copy $timestamp)$extension"

        return if (dir.isNotEmpty()) "$dir/$conflictName" else conflictName
    }

    private fun isOrgPath(path: String): Boolean =
        path.endsWith(".org", ignoreCase = true)

    private suspend fun writeLocalBytes(path: String, bytes: ByteArray) {
        if (isOrgPath(path)) {
            storage.writeNote(path, bytes.toString(Charsets.UTF_8))
        } else {
            storage.writeFileBytes(path, bytes)
        }
    }
}

data class SyncSummary(
    val totalLocalNotes: Int,
    val totalRemoteNotes: Int,
    val pendingUploads: Int,
    val pendingDownloads: Int,
    val pendingDeletes: Int,
    val conflicts: Int,
    val resolvedConflicts: Int,
)
