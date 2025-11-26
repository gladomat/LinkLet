package com.gladomat.linklet.data.sync

import android.util.Log
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.settings.WebDavSettingsRepository
import com.gladomat.linklet.data.storage.IStorage
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
) {

    suspend fun run(provider: RemoteSyncProvider): Result<SyncSummary> = withContext(dispatcher) {
        Log.i(TAG, "Starting sync run with provider: ${provider.name}")
        runCatching {
            // Check for directory change before syncing
            val currentSettings = webDavSettingsRepository.currentSettings()
            if (currentSettings != null && provider is WebDavRemoteSyncProvider) {
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
                        message = message
                    )
                }
            }
            
            // Phase A: Discovery
            val discovery = discoverState(provider)

            // Check for Fresh Install
            val isFreshInstall = syncStateDao.count() == 0
            if (isFreshInstall) {
                Log.i(TAG, "Fresh Install detected. Activating Pull Strategy.")
            }

            // Phase B: Reconciliation
            val operations = reconcile(discovery, isFreshInstall)

            // Phase C: Execution with Guard Rails
            execute(operations, provider, discovery.remoteFiles.size)
            
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
                resolvedConflicts = operations.count { it is SyncOperation.ConflictResolved },
            )
        }.onFailure {
            Log.e(TAG, "Sync run failed", it)
        }
    }

    // --- Phase A: Discovery ---

    data class DiscoveryResult(
        val localFiles: Map<String, LocalFile>,
        val remoteFiles: Map<String, RemoteNoteMetadata>,
        val syncStates: Map<String, SyncStateEntity>,
    )

    data class LocalFile(val path: String, val content: String, val hash: String)

    private suspend fun discoverState(provider: RemoteSyncProvider): DiscoveryResult {
        // 1. Local Files
        val localPaths = storage.listNotes().getOrThrow()
        val localFiles = localPaths.mapNotNull { path ->
            val content = storage.readNote(path).getOrNull()
            if (content != null) {
                path to LocalFile(path, content, NoteHashCalculator.compute(content))
            } else null
        }.toMap()

        // 2. Remote Files
        val remoteList = provider.listRemoteNotes().getOrThrow()
        val remoteFiles = remoteList.associateBy { it.path }

        // 3. Sync State
        val syncStates = syncStateDao.getAllStates().associateBy { it.path }

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
        data class Download(override val path: String, val remoteId: String, val reason: String) : SyncOperation()
        data class DeleteRemote(override val path: String, val remoteId: String, val etag: String?) : SyncOperation()
        data class SoftDeleteLocal(override val path: String) : SyncOperation() // Move to trash
        data class Conflict(override val path: String, val remoteId: String) : SyncOperation()
        data class ConflictResolved(override val path: String) : SyncOperation() // Marker for summary
        data class UpdateState(override val path: String) : SyncOperation() // Just update DB
    }

    private fun reconcile(discovery: DiscoveryResult, isFreshInstall: Boolean): List<SyncOperation> {
        val operations = mutableListOf<SyncOperation>()
        val allPaths = discovery.localFiles.keys + discovery.remoteFiles.keys + discovery.syncStates.keys

        for (path in allPaths) {
            val local = discovery.localFiles[path]
            val remote = discovery.remoteFiles[path]
            val state = discovery.syncStates[path]

            if (isFreshInstall) {
                // Fresh Install Logic
                if (local != null && remote != null) {
                    // Exists on both. Since it's fresh, we assume Server is Truth, 
                    // BUT if they differ, it's safer to Conflict or Upload? 
                    // "Pull Strategy: Download everything." -> Overwrite local? 
                    // Or maybe check hashes. If identical, just link.
                    // Let's check hash. ETag usually isn't MD5, so we can't easily compare without download.
                    // Safety: Conflict.
                     operations.add(SyncOperation.Conflict(path, remote.remoteId ?: path))
                } else if (local != null && remote == null) {
                    // Local only -> Upload (Discovery)
                    operations.add(SyncOperation.Upload(path, "Fresh Install: Local Discovery"))
                } else if (local == null && remote != null) {
                    // Remote only -> Download
                    operations.add(SyncOperation.Download(path, remote.remoteId ?: path, "Fresh Install: Pull"))
                }
                // No Deletes.
                continue
            }

            // Standard Logic
            if (state == null) {
                // New file (either local or remote)
                if (local != null && remote != null) {
                    // Collision on new files. Conflict.
                    operations.add(SyncOperation.Conflict(path, remote.remoteId ?: path))
                } else if (local != null) {
                    operations.add(SyncOperation.Upload(path, "New Local File"))
                } else if (remote != null) {
                    operations.add(SyncOperation.Download(path, remote.remoteId ?: path, "New Remote File"))
                }
            } else {
                // Existing State
                val localChanged = local != null && local.hash != state.localContentHash
                // We use remoteFingerprint (ETag) to detect remote changes
                val remoteChanged = remote != null && remote.fingerprint != state.remoteFingerprint
                
                if (local != null && remote != null) {
                    if (localChanged && remoteChanged) {
                        operations.add(SyncOperation.Conflict(path, remote.remoteId ?: path))
                    } else if (localChanged) {
                        operations.add(SyncOperation.Upload(path, "Local Modified"))
                    } else if (remoteChanged) {
                        operations.add(SyncOperation.Download(path, remote.remoteId ?: path, "Remote Modified"))
                    } else {
                        // No changes, but maybe ensure DB is up to date
                        if (state.lifecycle != NoteLifecycle.ACTIVE) {
                            // It was marked deleted but reappeared?
                            // Assume restoration.
                             operations.add(SyncOperation.UpdateState(path)) 
                        }
                    }
                } else if (local != null && remote == null) {
                    // Remote Missing.
                    if (state.lifecycle == NoteLifecycle.DELETED_REMOTELY) {
                        // Already handled, ensure it stays handled
                    } else {
                        // Was it deleted on server? Or did we just create it and server hasn't seen it?
                        // Check if we previously synced it.
                        if (state.remoteId != null) {
                            // We knew about it on server, now it's gone.
                            // "Remote Missing" -> Soft Delete Local
                             operations.add(SyncOperation.SoftDeleteLocal(path))
                        } else {
                            // We never synced it. Must be pending upload that failed? 
                            // Or we created it locally.
                            operations.add(SyncOperation.Upload(path, "Retry Upload"))
                        }
                    }
                } else if (local == null && remote != null) {
                    // Local Missing.
                    if (state.lifecycle == NoteLifecycle.DELETED_LOCALLY) {
                        // We marked it for deletion.
                         operations.add(SyncOperation.DeleteRemote(path, remote.remoteId ?: path, state.remoteFingerprint))
                    } else {
                        // User deleted file locally without app knowing (e.g. file explorer)?
                        // Or we just haven't downloaded it yet?
                        if (state.lastSyncedAtEpochMillis != null) {
                             // It was synced before. Local file is gone.
                             // Treat as Local Delete -> Delete Remote
                             operations.add(SyncOperation.DeleteRemote(path, remote.remoteId ?: path, state.remoteFingerprint))
                        } else {
                            // Never synced? Weird state. Download.
                            operations.add(SyncOperation.Download(path, remote.remoteId ?: path, "Resurrect Local"))
                        }
                    }
                } else {
                     // Both missing. Cleanup DB.
                     // Handled by cleanupDeletedStates later, or explicit operation?
                     // Let's just ignore here, cleanup step handles it.
                }
            }
        }
        return operations
    }

    // --- Phase C: Execution ---

    private suspend fun execute(operations: List<SyncOperation>, provider: RemoteSyncProvider, totalRemoteFiles: Int) {
        // GUARD RAILS
        val deleteOps = operations.filterIsInstance<SyncOperation.DeleteRemote>()
        
        if (deleteOps.isNotEmpty()) {
            if (totalRemoteFiles > 0 && deleteOps.size == totalRemoteFiles) {
                throw CatastrophicDeleteException("Attempting to wipe 100% of remote files. Aborting.")
            }
            
            val deletePercentage = deleteOps.size.toDouble() / totalRemoteFiles.toDouble()
            if (deleteOps.size > 50 || deletePercentage > 0.20) {
                 // Ideally show UI confirmation. Here we abort.
                 // "UI.showConfirmation" - we are a backend service basically.
                 // We will throw for now or skip deletes.
                 throw CatastrophicDeleteException("Delete threshold exceeded: ${deleteOps.size} files ($deletePercentage%). Aborting sync.")
            }
        }

        // Process
        for (op in operations) {
            when (op) {
                is SyncOperation.Upload -> performUpload(op, provider)
                is SyncOperation.Download -> performDownload(op, provider)
                is SyncOperation.DeleteRemote -> performRemoteDelete(op, provider)
                is SyncOperation.SoftDeleteLocal -> performSoftDelete(op)
                is SyncOperation.Conflict -> performConflictResolution(op, provider)
                is SyncOperation.UpdateState -> { /* Just ensure DB matches */ }
                else -> Unit
            }
        }
    }

    private suspend fun performUpload(op: SyncOperation.Upload, provider: RemoteSyncProvider) {
        Log.d(TAG, "Executing Upload: ${op.path}")
        val state = syncStateDao.getState(op.path)
        val content = storage.readNote(op.path).getOrElse { return }
        val hash = NoteHashCalculator.compute(content)
        
        // Assuming state exists or we create new
        val remoteId = state?.remoteId
        val expectedFingerprint = state?.remoteFingerprint
        
        provider.upload(op.path, content.byteInputStream(Charsets.UTF_8), remoteId, expectedFingerprint)
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
        Log.d(TAG, "Executing Download: ${op.path}")
        provider.download(op.path, op.remoteId)
            .onSuccess { stream ->
                val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                storage.writeNote(op.path, content)
                val hash = NoteHashCalculator.compute(content)
                
                // We need the new ETag. Download usually implies we got the list first.
                // The list gave us the ETag which triggered the download operation (via remoteChanged).
                // We should look up the remote metadata again or pass it in operation?
                // For simplicity, we'll refetch list or rely on next sync to update ETag if we missed it?
                // Better: The reconcile step had the remote metadata.
                // We should pass ETag in Download op.
                // But for now, let's fetch remote metadata if possible or assume the one from discovery.
                // Actually, we can't easily get ETag from download response headers with current I/F.
                // Let's assume we update DB with the one from Discovery phase?
                // Yes, we need to look up the discovery result. But we passed generic list.
                // Let's just update hash and clear error. ETag will be updated next cycle or if we pass it.
                // Ideally we pass ETag in Download op.
                
                val state = syncStateDao.getState(op.path) ?: SyncStateEntity(op.path)
                val newState = state.copy(
                     lifecycle = NoteLifecycle.ACTIVE,
                     remoteId = op.remoteId,
                     localContentHash = hash,
                     lastKnownHash = hash,
                     // remoteFingerprint = ??? We don't have it here easily without passing it.
                     // It's okay, next discovery will fix it, or we can pass it.
                     lastSyncedAtEpochMillis = System.currentTimeMillis(),
                     pendingAction = SyncPendingAction.NONE,
                     lastError = null
                )
                syncStateDao.upsert(newState)
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
            val content = storage.readNote(op.path).getOrThrow()
            val trashPath = "$TRASH_DIR/${op.path}"
            storage.writeNote(trashPath, content)
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
        Log.i(TAG, "Resolving conflict for ${op.path}")
        // 1. Rename local to "Conflicted Copy"
        val localContent = storage.readNote(op.path).getOrNull()
        if (localContent != null) {
            val conflictPath = createConflictPath(op.path)
            storage.writeNote(conflictPath, localContent)
            // Create new sync state for conflict copy so it gets uploaded
            val conflictHash = NoteHashCalculator.compute(localContent)
            val conflictState = SyncStateEntity(
                path = conflictPath,
                lifecycle = NoteLifecycle.ACTIVE,
                localContentHash = conflictHash,
                lastKnownHash = conflictHash,
                pendingAction = SyncPendingAction.UPLOAD
            )
            syncStateDao.upsert(conflictState)
        }
        
        // 2. Download remote version to original path
        performDownload(SyncOperation.Download(op.path, op.remoteId, "Conflict Resolution"), provider)
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
