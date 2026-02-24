package com.gladomat.linklet.data.sync.planner

import com.gladomat.linklet.data.sync.NoteLifecycle
import com.gladomat.linklet.data.sync.SyncEngine

class ReconcilePlanner {
    fun plan(
        discovery: SyncEngine.DiscoveryResult,
        isFreshInstall: Boolean,
    ): List<SyncEngine.SyncOperation> {
        val operations = mutableListOf<SyncEngine.SyncOperation>()
        val allPaths = discovery.localFiles.keys + discovery.remoteFiles.keys + discovery.syncStates.keys

        for (path in allPaths) {
            val local = discovery.localFiles[path]
            val remote = discovery.remoteFiles[path]
            val state = discovery.syncStates[path]

            if (isFreshInstall) {
                when {
                    local != null && remote != null -> {
                        operations.add(
                            SyncEngine.SyncOperation.Download(
                                path = path,
                                remoteId = remote.remoteId,
                                fingerprint = remote.fingerprint,
                                reason = "Fresh Install: Server wins",
                                backupLocalIfDifferent = true,
                            ),
                        )
                    }

                    local != null -> {
                        operations.add(SyncEngine.SyncOperation.Upload(path, "Fresh Install: Local Discovery"))
                    }

                    remote != null -> {
                        operations.add(
                            SyncEngine.SyncOperation.Download(
                                path = path,
                                remoteId = remote.remoteId,
                                fingerprint = remote.fingerprint,
                                reason = "Fresh Install: Pull",
                            ),
                        )
                    }
                }
                continue
            }

            if (state == null) {
                when {
                    local != null && remote != null -> {
                        operations.add(SyncEngine.SyncOperation.Conflict(path, remote.remoteId, remote.fingerprint))
                    }

                    local != null -> {
                        operations.add(SyncEngine.SyncOperation.Upload(path, "New Local File"))
                    }

                    remote != null -> {
                        operations.add(
                            SyncEngine.SyncOperation.Download(
                                path = path,
                                remoteId = remote.remoteId,
                                fingerprint = remote.fingerprint,
                                reason = "New Remote File",
                            ),
                        )
                    }
                }
                continue
            }

            val localChanged = local != null && local.hash != state.localContentHash
            val remoteChanged = remote != null && remote.fingerprint != state.remoteFingerprint

            if (local != null && remote != null) {
                when {
                    localChanged && remoteChanged -> {
                        operations.add(SyncEngine.SyncOperation.Conflict(path, remote.remoteId, remote.fingerprint))
                    }

                    localChanged -> {
                        operations.add(SyncEngine.SyncOperation.Upload(path, "Local Modified"))
                    }

                    remoteChanged -> {
                        operations.add(
                            SyncEngine.SyncOperation.Download(
                                path = path,
                                remoteId = remote.remoteId,
                                fingerprint = remote.fingerprint,
                                reason = "Remote Modified",
                            ),
                        )
                    }

                    state.lifecycle != NoteLifecycle.ACTIVE -> {
                        operations.add(SyncEngine.SyncOperation.UpdateState(path))
                    }
                }
                continue
            }

            if (local != null && remote == null) {
                if (state.lifecycle != NoteLifecycle.DELETED_REMOTELY) {
                    if (state.remoteId != null) {
                        operations.add(SyncEngine.SyncOperation.SoftDeleteLocal(path))
                    } else {
                        operations.add(SyncEngine.SyncOperation.Upload(path, "Retry Upload"))
                    }
                }
                continue
            }

            if (local == null && remote != null) {
                if (state.lifecycle == NoteLifecycle.DELETED_LOCALLY || state.lastSyncedAtEpochMillis != null) {
                    operations.add(SyncEngine.SyncOperation.DeleteRemote(path, remote.remoteId, state.remoteFingerprint))
                } else {
                    operations.add(
                        SyncEngine.SyncOperation.Download(
                            path = path,
                            remoteId = remote.remoteId,
                            fingerprint = remote.fingerprint,
                            reason = "Resurrect Local",
                        ),
                    )
                }
                continue
            }

            operations.add(SyncEngine.SyncOperation.CleanupOrphanState(path))
        }

        return operations
    }
}
