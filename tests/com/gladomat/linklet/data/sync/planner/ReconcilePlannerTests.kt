package com.gladomat.linklet.data.sync.planner

import com.gladomat.linklet.data.sync.NoteLifecycle
import com.gladomat.linklet.data.sync.RemoteNoteMetadata
import com.gladomat.linklet.data.sync.SyncEngine
import com.gladomat.linklet.data.sync.SyncStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcilePlannerTests {
    private val planner = ReconcilePlanner()

    @Test
    fun `planner writes download operation for remote-only notes`() {
        val discovery = SyncEngine.DiscoveryResult(
            localFiles = emptyMap(),
            remoteFiles = mapOf(
                "notes/remote.org" to RemoteNoteMetadata(
                    remoteId = "remote-1",
                    path = "notes/remote.org",
                    fingerprint = "etag-1",
                    lastModifiedEpochMillis = null,
                ),
            ),
            syncStates = emptyMap(),
        )

        val operations = planner.plan(discovery, isFreshInstall = false)

        assertEquals(1, operations.size)
        val operation = operations.single()
        assertTrue(operation is SyncEngine.SyncOperation.Download)
        operation as SyncEngine.SyncOperation.Download
        assertEquals("notes/remote.org", operation.path)
        assertEquals("remote-1", operation.remoteId)
        assertEquals("New Remote File", operation.reason)
    }

    @Test
    fun `planner writes delete remote for locally deleted synchronized note`() {
        val path = "notes/deleted.org"
        val discovery = SyncEngine.DiscoveryResult(
            localFiles = emptyMap(),
            remoteFiles = mapOf(
                path to RemoteNoteMetadata(
                    remoteId = "remote-delete-1",
                    path = path,
                    fingerprint = "etag-delete-1",
                    lastModifiedEpochMillis = null,
                ),
            ),
            syncStates = mapOf(
                path to SyncStateEntity(
                    path = path,
                    lifecycle = NoteLifecycle.DELETED_LOCALLY,
                    remoteId = "remote-delete-1",
                    remoteFingerprint = "etag-delete-1",
                    lastSyncedAtEpochMillis = 100L,
                ),
            ),
        )

        val operations = planner.plan(discovery, isFreshInstall = false)

        assertEquals(1, operations.size)
        val operation = operations.single()
        assertTrue(operation is SyncEngine.SyncOperation.DeleteRemote)
        operation as SyncEngine.SyncOperation.DeleteRemote
        assertEquals(path, operation.path)
        assertEquals("remote-delete-1", operation.remoteId)
    }

    @Test
    fun `planner writes conflict when local and remote changed`() {
        val path = "notes/conflict.org"
        val discovery = SyncEngine.DiscoveryResult(
            localFiles = mapOf(
                path to SyncEngine.LocalFile(
                    path = path,
                    hash = "local-new-hash",
                ),
            ),
            remoteFiles = mapOf(
                path to RemoteNoteMetadata(
                    remoteId = "remote-conflict-1",
                    path = path,
                    fingerprint = "etag-new",
                    lastModifiedEpochMillis = null,
                ),
            ),
            syncStates = mapOf(
                path to SyncStateEntity(
                    path = path,
                    lifecycle = NoteLifecycle.ACTIVE,
                    remoteId = "remote-conflict-1",
                    remoteFingerprint = "etag-old",
                    localContentHash = "local-old-hash",
                    lastSyncedAtEpochMillis = 10L,
                ),
            ),
        )

        val operations = planner.plan(discovery, isFreshInstall = false)

        assertEquals(1, operations.size)
        val operation = operations.single()
        assertTrue(operation is SyncEngine.SyncOperation.Conflict)
    }
}
