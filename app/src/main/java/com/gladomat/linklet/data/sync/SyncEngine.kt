package com.gladomat.linklet.data.sync

import android.util.Log
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.storage.IStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.text.Charsets

private const val TAG = "SyncEngine"

@Singleton
class SyncEngine @Inject constructor(
    private val storage: IStorage,
    private val syncStateDao: SyncStateDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun run(provider: RemoteSyncProvider): Result<SyncSummary> = withContext(dispatcher) {
        Log.i(TAG, "Starting sync run with provider: ${provider.name}")
        runCatching {
            sync(provider).getOrThrow()
            val resolved = processPending(provider)
            val summary = sync(provider).getOrThrow()
            Log.i(TAG, "Sync run completed successfully. Summary: $summary")
            summary.copy(resolvedConflicts = resolved)
        }.onFailure {
            Log.e(TAG, "Sync run failed", it)
        }
    }

    suspend fun sync(provider: RemoteSyncProvider): Result<SyncSummary> = withContext(dispatcher) {
        Log.d(TAG, "Starting sync cycle (indexing & state update)")
        runCatching {
            val localPaths = storage.listNotes().getOrThrow()
            val existingStates = syncStateDao.getAllStates().associateBy { it.path }
            val remoteNotes = provider.listRemoteNotes().getOrThrow()
            val remoteByPath = remoteNotes.associateBy { it.path }

            Log.d(TAG, "Found ${localPaths.size} local notes and ${remoteNotes.size} remote notes")

            updateLocalStates(
                localPaths = localPaths,
                existingStates = existingStates,
                remoteByPath = remoteByPath,
            )

            updateRemoteOnlyStates(
                localPaths = localPaths.toSet(),
                existingStates = existingStates,
                remoteNotes = remoteNotes,
            )

            cleanupDeletedStates(
                localPaths = localPaths.toSet(),
                remotePaths = remoteByPath.keys,
                existingStates = existingStates,
            )

            val pendingStates = syncStateDao.getPendingStates()
            val summary = SyncSummary(
                totalLocalNotes = localPaths.size,
                totalRemoteNotes = remoteNotes.size,
                pendingUploads = pendingStates.count { it.pendingAction == SyncPendingAction.UPLOAD },
                pendingDownloads = pendingStates.count { it.pendingAction == SyncPendingAction.DOWNLOAD },
                pendingDeletes = pendingStates.count { it.pendingAction == SyncPendingAction.DELETE },
                conflicts = pendingStates.count { it.pendingAction == SyncPendingAction.CONFLICT },
                resolvedConflicts = 0,
            )
            Log.d(TAG, "Sync cycle analyzed. Pending: Uploads=${summary.pendingUploads}, Downloads=${summary.pendingDownloads}, Deletes=${summary.pendingDeletes}, Conflicts=${summary.conflicts}")
            summary
        }
    }

    private suspend fun processPending(provider: RemoteSyncProvider): Int {
        val pendingStates = syncStateDao.getPendingStates()
        if (pendingStates.isNotEmpty()) {
            Log.i(TAG, "Processing ${pendingStates.size} pending actions")
        }
        var resolvedConflicts = 0
        for (state in pendingStates) {
            when (state.pendingAction) {
                SyncPendingAction.UPLOAD -> handleUpload(state, provider)
                SyncPendingAction.DOWNLOAD -> handleDownload(state, provider)
                SyncPendingAction.DELETE -> handleDelete(state, provider)
                SyncPendingAction.CONFLICT -> {
                    if (handleConflict(state, provider)) {
                        resolvedConflicts++
                    }
                }
                else -> Unit
            }
        }
        return resolvedConflicts
    }

    private suspend fun handleConflict(state: SyncStateEntity, provider: RemoteSyncProvider): Boolean {
        Log.i(TAG, "Resolving conflict for ${state.path}")
        return runCatching {
            // 1. Read local note content
            val localContent = storage.readNote(state.path).getOrThrow()

            // 2. Generate conflict-copy filename
            val conflictPath = createConflictPath(state.path)
            Log.d(TAG, "Created conflict copy path: $conflictPath")

            // 3. Write conflict copy to disk
            storage.writeNote(conflictPath, localContent).getOrThrow()

            // 4. Create sync state for the conflict copy
            val conflictHash = NoteHashCalculator.compute(localContent)
            val conflictState = SyncStateEntity(
                path = conflictPath,
                lastKnownHash = conflictHash,
                remoteId = null,
                remoteFingerprint = null,
                pendingAction = SyncPendingAction.UPLOAD,
                lastError = null,
                lastSyncedAtEpochMillis = 0L,
            )
            syncStateDao.upsert(conflictState)

            // 5. Download remote version for the original path
            if (state.remoteId == null) error("Missing remoteId for conflict resolution")
            val inputStream = provider.download(state.path, state.remoteId).getOrThrow()
            val remoteContent = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            storage.writeNote(state.path, remoteContent).getOrThrow()

            // 6. Update original sync state
            val newHash = NoteHashCalculator.compute(remoteContent)
            val updatedState = state.copy(
                lastKnownHash = newHash,
                pendingAction = SyncPendingAction.NONE,
                lastError = null,
                lastSyncedAtEpochMillis = System.currentTimeMillis(),
            )
            syncStateDao.upsert(updatedState)

            Log.i(TAG, "Conflict resolved successfully for ${state.path}")
            true
        }.getOrElse {
            Log.e(TAG, "Conflict resolution failed for ${state.path}", it)
            syncStateDao.updatePendingAction(state.path, SyncPendingAction.CONFLICT, it.message)
            false
        }
    }

    private fun createConflictPath(originalPath: String): String {
        val dir = originalPath.substringBeforeLast('/', "")
        val filename = originalPath.substringAfterLast('/')
        val name = filename.substringBeforeLast('.')
        val extension = if (filename.contains('.')) "." + filename.substringAfterLast('.') else ""

        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH-mm", java.util.Locale.US)
            .format(java.util.Date())

        val conflictName = "$name (Conflicted Copy $timestamp)$extension"

        return if (dir.isNotEmpty()) "$dir/$conflictName" else conflictName
    }

    private suspend fun handleUpload(state: SyncStateEntity, provider: RemoteSyncProvider) {
        Log.d(TAG, "Uploading ${state.path}")
        val content = storage.readNote(state.path).getOrElse {
            Log.e(TAG, "Failed to read local note for upload: ${state.path}", it)
            syncStateDao.updatePendingAction(state.path, SyncPendingAction.UPLOAD, it.message)
            return
        }
        val bytes = content.toByteArray(Charsets.UTF_8)
        runCatching {
            val result = provider.upload(
                path = state.path,
                content = java.io.ByteArrayInputStream(bytes),
                remoteId = state.remoteId,
                expectedFingerprint = state.remoteFingerprint,
            ).getOrThrow()

            val updated = state.copy(
                remoteId = result.remoteId,
                remoteFingerprint = result.fingerprint ?: state.remoteFingerprint,
                pendingAction = SyncPendingAction.NONE,
                lastSyncedAtEpochMillis = System.currentTimeMillis(),
                lastError = null,
            )
            syncStateDao.upsert(updated)
            Log.d(TAG, "Upload success: ${state.path}")
        }.onFailure {
            Log.e(TAG, "Upload failed: ${state.path}", it)
            syncStateDao.updatePendingAction(state.path, SyncPendingAction.UPLOAD, it.message)
        }
    }

    private suspend fun handleDownload(state: SyncStateEntity, provider: RemoteSyncProvider) {
        Log.d(TAG, "Downloading ${state.path}")
        if (state.remoteId == null) {
            val msg = "Missing remote id for download: ${state.path}"
            Log.e(TAG, msg)
            syncStateDao.updatePendingAction(state.path, SyncPendingAction.DOWNLOAD, msg)
            return
        }
        runCatching {
            val inputStream = provider.download(state.path, state.remoteId).getOrThrow()
            val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            storage.writeNote(state.path, content).getOrThrow()
            val hash = NoteHashCalculator.compute(content)
            val updated = state.copy(
                lastKnownHash = hash,
                pendingAction = SyncPendingAction.NONE,
                lastSyncedAtEpochMillis = System.currentTimeMillis(),
                lastError = null,
            )
            syncStateDao.upsert(updated)
            Log.d(TAG, "Download success: ${state.path}")
        }.onFailure {
            Log.e(TAG, "Download failed: ${state.path}", it)
            syncStateDao.updatePendingAction(state.path, SyncPendingAction.DOWNLOAD, it.message)
        }
    }

    private suspend fun handleDelete(state: SyncStateEntity, provider: RemoteSyncProvider) {
        Log.d(TAG, "Deleting remote ${state.path}")
        val remoteId = state.remoteId ?: state.path
        runCatching {
            provider.delete(state.path, remoteId, state.remoteFingerprint).getOrThrow()
            syncStateDao.deleteState(state.path)
            Log.d(TAG, "Remote delete success: ${state.path}")
        }.onFailure {
            Log.e(TAG, "Remote delete failed: ${state.path}", it)
            syncStateDao.updatePendingAction(state.path, SyncPendingAction.DELETE, it.message)
        }
    }

    private suspend fun updateLocalStates(
        localPaths: List<String>,
        existingStates: Map<String, SyncStateEntity>,
        remoteByPath: Map<String, RemoteNoteMetadata>,
    ) {
        for (path in localPaths) {
            val content = storage.readNote(path).getOrThrow()
            val hash = NoteHashCalculator.compute(content)
            val existing = existingStates[path]
            val previousHash = existing?.lastKnownHash
            val remote = remoteByPath[path]
            val remoteFingerprintBefore = existing?.remoteFingerprint

            val action = determineLocalAction(
                previousHash = previousHash,
                currentHash = hash,
                remote = remote,
                storedRemoteFingerprint = remoteFingerprintBefore,
            )

            val newState = (existing ?: SyncStateEntity(path = path)).copy(
                lastKnownHash = hash,
                remoteId = remote?.remoteId ?: existing?.remoteId,
                remoteFingerprint = remote?.fingerprint ?: remoteFingerprintBefore,
                pendingAction = action,
                lastError = if (action == SyncPendingAction.NONE) null else existing?.lastError,
            )
            syncStateDao.upsert(newState)
        }
    }

    private suspend fun updateRemoteOnlyStates(
        localPaths: Set<String>,
        existingStates: Map<String, SyncStateEntity>,
        remoteNotes: List<RemoteNoteMetadata>,
    ) {
        for (remote in remoteNotes) {
            if (localPaths.contains(remote.path)) continue
            val existing = existingStates[remote.path]
            val action = when {
                existing != null && existing.lastKnownHash != null -> SyncPendingAction.DELETE
                else -> SyncPendingAction.DOWNLOAD
            }
            val state = (existing ?: SyncStateEntity(path = remote.path)).copy(
                remoteId = remote.remoteId,
                remoteFingerprint = remote.fingerprint,
                pendingAction = action,
                lastError = if (action == SyncPendingAction.DOWNLOAD) null else existing?.lastError,
            )
            syncStateDao.upsert(state)
        }
    }

    private suspend fun cleanupDeletedStates(
        localPaths: Set<String>,
        remotePaths: Set<String>,
        existingStates: Map<String, SyncStateEntity>,
    ) {
        for ((path, state) in existingStates) {
            val existsLocally = localPaths.contains(path)
            val existsRemotely = remotePaths.contains(path)
            if (!existsLocally && !existsRemotely) {
                syncStateDao.deleteState(path)
            }
        }
    }

    private fun determineLocalAction(
        previousHash: String?,
        currentHash: String,
        remote: RemoteNoteMetadata?,
        storedRemoteFingerprint: String?,
    ): SyncPendingAction {
        val localChanged = previousHash != null && previousHash != currentHash
        val remoteFingerprintChanged = when {
            remote == null -> false
            storedRemoteFingerprint == null -> false
            remote.fingerprint == null -> false
            else -> remote.fingerprint != storedRemoteFingerprint
        }

        return when {
            remote == null -> SyncPendingAction.UPLOAD
            localChanged && remoteFingerprintChanged -> SyncPendingAction.CONFLICT
            localChanged -> SyncPendingAction.UPLOAD
            remoteFingerprintChanged -> SyncPendingAction.DOWNLOAD
            else -> SyncPendingAction.NONE
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
