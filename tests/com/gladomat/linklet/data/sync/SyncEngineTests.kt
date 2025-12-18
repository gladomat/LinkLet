package com.gladomat.linklet.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.index.NoteDatabase
import com.gladomat.linklet.data.storage.IStorage
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.gladomat.linklet.data.settings.WebDavSettingsRepository
import io.mockk.mockk

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncEngineTests {

    private lateinit var database: NoteDatabase
    private lateinit var engine: SyncEngine
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `new local note marked for upload`() = runTest(dispatcher) {
        val storage = FakeStorage(
            mutableMapOf("notes/a.org" to "Hello world"),
        )
        val provider = FakeRemoteSyncProvider()
        val settingsRepo = mockk<WebDavSettingsRepository>(relaxed = true)
        engine = SyncEngine(storage, database.syncStateDao(), settingsRepo, dispatcher)

        val summary = engine.run(provider).getOrThrow()

        assertEquals(0, summary.pendingUploads)
        assertEquals(0, summary.pendingDownloads)
        assertEquals(1, provider.uploadedPaths.size)
        assertEquals("notes/a.org", provider.uploadedPaths.single())

        val state = database.syncStateDao().getState("notes/a.org")
        assertEquals(SyncPendingAction.NONE, state?.pendingAction)
        assertEquals(provider.lastUploadFingerprint, state?.remoteFingerprint)
    }

    @Test
    fun `remote note without local copy marked download`() = runTest(dispatcher) {
        val storage = FakeStorage(mutableMapOf())
        val provider = FakeRemoteSyncProvider(
            metadata = listOf(
                RemoteNoteMetadata(
                    remoteId = "remote-1",
                    path = "notes/b.org",
                    fingerprint = "etag-1",
                    lastModifiedEpochMillis = null,
                ),
            ),
            remoteFiles = mapOf("remote-1" to "Remote note"),
        )
        val settingsRepo = mockk<WebDavSettingsRepository>(relaxed = true)
        engine = SyncEngine(storage, database.syncStateDao(), settingsRepo, dispatcher)

        val summary = engine.run(provider).getOrThrow()

        assertEquals(0, summary.pendingDownloads)
        assertEquals("Remote note", storage.files["notes/b.org"])

        val state = database.syncStateDao().getState("notes/b.org")
        assertEquals(SyncPendingAction.NONE, state?.pendingAction)
        assertEquals("etag-1", state?.remoteFingerprint)
    }

    @Test
    fun `local change triggers upload while remote change triggers download or conflict`() = runTest(dispatcher) {
        val storage = FakeStorage(
            mutableMapOf("notes/a.org" to "local"),
        )
        val dao = database.syncStateDao()
        // seed existing state
        dao.upsert(
            SyncStateEntity(
                path = "notes/a.org",
                remoteId = "remote-1",
                remoteFingerprint = "etag-1",
                localContentHash = NoteHashCalculator.compute("old"),
                lastKnownHash = NoteHashCalculator.compute("old"),
                lastSyncedAtEpochMillis = 1L,
                pendingAction = SyncPendingAction.NONE,
            ),
        )

        val settingsRepo = mockk<WebDavSettingsRepository>(relaxed = true)
        engine = SyncEngine(storage, dao, settingsRepo, dispatcher)

        // Remote unchanged -> expect upload due to local delta
        val providerUpload = FakeRemoteSyncProvider(
            metadata = listOf(
                RemoteNoteMetadata(
                    remoteId = "remote-1",
                    path = "notes/a.org",
                    fingerprint = "etag-1",
                    lastModifiedEpochMillis = null,
                ),
            ),
        )
        val summaryUpload = engine.run(providerUpload).getOrThrow()
        assertEquals(0, summaryUpload.pendingUploads)
        assertEquals(listOf("notes/a.org"), providerUpload.uploadedPaths)

        // Remote changed but local content unchanged -> expect download
        val providerDownload = FakeRemoteSyncProvider(
            metadata = listOf(
                RemoteNoteMetadata(
                    remoteId = "remote-1",
                    path = "notes/a.org",
                    fingerprint = "etag-2",
                    lastModifiedEpochMillis = null,
                ),
            ),
            remoteFiles = mapOf("remote-1" to "remote-content-2"),
        )
        storage.files["notes/a.org"] = "local" // ensure same hash
        val summaryDownload = engine.run(providerDownload).getOrThrow()
        assertEquals(0, summaryDownload.pendingDownloads)
        assertEquals("remote-content-2", storage.files["notes/a.org"])

        // Remote and local both changed -> conflict
        storage.files["notes/a.org"] = "local-updated"
        val providerConflict = FakeRemoteSyncProvider(
            metadata = listOf(
                RemoteNoteMetadata(
                    remoteId = "remote-1",
                    path = "notes/a.org",
                    fingerprint = "etag-3",
                    lastModifiedEpochMillis = null,
                ),
            ),
            remoteFiles = mapOf("remote-1" to "remote-content-3"),
        )
        val summaryConflict = engine.run(providerConflict).getOrThrow()
        assertEquals(1, summaryConflict.resolvedConflicts)
        assertEquals("remote-content-3", storage.files["notes/a.org"])
        val conflictFile = storage.files.keys.find { it.contains("(Conflicted Copy") }
        assertTrue("Conflict copy created", conflictFile != null)
        assertEquals("local-updated", storage.files[conflictFile])
        assertEquals(SyncPendingAction.UPLOAD, dao.getState(conflictFile!!)?.pendingAction)
    }

    @Test
    fun `local deletion queued as remote delete`() = runTest(dispatcher) {
        val storage = FakeStorage(
            mutableMapOf(
                "notes/other.org" to "keep-local-storage-nonempty",
                "notes/b.org" to "content-remote-b",
                "notes/c.org" to "content-remote-c",
                "notes/d.org" to "content-remote-d",
            ),
        )
        val dao = database.syncStateDao()
        dao.upsert(
            SyncStateEntity(
                path = "notes/a.org",
                lifecycle = NoteLifecycle.DELETED_LOCALLY,
                remoteId = "remote-1",
                remoteFingerprint = "etag-1",
                localContentHash = NoteHashCalculator.compute("old"),
                lastKnownHash = NoteHashCalculator.compute("old"),
                lastSyncedAtEpochMillis = 1L,
            ),
        )
        dao.upsert(
            SyncStateEntity(
                path = "notes/other.org",
                lifecycle = NoteLifecycle.ACTIVE,
                remoteId = "remote-other",
                remoteFingerprint = "etag-other",
                localContentHash = NoteHashCalculator.compute("keep-local-storage-nonempty"),
                lastKnownHash = NoteHashCalculator.compute("keep-local-storage-nonempty"),
                lastSyncedAtEpochMillis = 1L,
            ),
        )
        listOf(
            Triple("notes/b.org", "remote-b", "content-remote-b"),
            Triple("notes/c.org", "remote-c", "content-remote-c"),
            Triple("notes/d.org", "remote-d", "content-remote-d"),
        ).forEach { (path, remoteId, content) ->
            val hash = NoteHashCalculator.compute(content)
            dao.upsert(
                SyncStateEntity(
                    path = path,
                    lifecycle = NoteLifecycle.ACTIVE,
                    remoteId = remoteId,
                    remoteFingerprint = "etag-$remoteId",
                    localContentHash = hash,
                    lastKnownHash = hash,
                    lastSyncedAtEpochMillis = 1L,
                ),
            )
        }
        val provider = FakeRemoteSyncProvider(
            metadata = listOf(
                RemoteNoteMetadata(
                    remoteId = "remote-1",
                    path = "notes/a.org",
                    fingerprint = "etag-1",
                    lastModifiedEpochMillis = null,
                ),
                RemoteNoteMetadata(
                    remoteId = "remote-other",
                    path = "notes/other.org",
                    fingerprint = "etag-other",
                    lastModifiedEpochMillis = null,
                ),
                RemoteNoteMetadata(
                    remoteId = "remote-b",
                    path = "notes/b.org",
                    fingerprint = "etag-remote-b",
                    lastModifiedEpochMillis = null,
                ),
                RemoteNoteMetadata(
                    remoteId = "remote-c",
                    path = "notes/c.org",
                    fingerprint = "etag-remote-c",
                    lastModifiedEpochMillis = null,
                ),
                RemoteNoteMetadata(
                    remoteId = "remote-d",
                    path = "notes/d.org",
                    fingerprint = "etag-remote-d",
                    lastModifiedEpochMillis = null,
                ),
            ),
        )

        val settingsRepo = mockk<WebDavSettingsRepository>(relaxed = true)
        engine = SyncEngine(storage, dao, settingsRepo, dispatcher)
        val summary = engine.run(provider).getOrThrow()

        assertEquals(0, summary.pendingDeletes)
        assertEquals(listOf("notes/a.org"), provider.deletedPaths)
        assertEquals(null, dao.getState("notes/a.org"))
    }

    @Test
    fun `conflict resolution creates copy and updates original`() = runTest(dispatcher) {
        val storage = FakeStorage(
            mutableMapOf("notes/a.org" to "local-updated"),
        )
        val dao = database.syncStateDao()
        // Initial state: synced at etag-1, but local changed
        dao.upsert(
            SyncStateEntity(
                path = "notes/a.org",
                remoteId = "remote-1",
                remoteFingerprint = "etag-1",
                lastKnownHash = NoteHashCalculator.compute("old-local"),
                pendingAction = SyncPendingAction.NONE,
            ),
        )

        val settingsRepo = mockk<WebDavSettingsRepository>(relaxed = true)
        engine = SyncEngine(storage, dao, settingsRepo, dispatcher)

        // Remote has changed to etag-2
        val provider = FakeRemoteSyncProvider(
            metadata = listOf(
                RemoteNoteMetadata(
                    remoteId = "remote-1",
                    path = "notes/a.org",
                    fingerprint = "etag-2",
                    lastModifiedEpochMillis = null,
                ),
            ),
            remoteFiles = mapOf("remote-1" to "remote-content")
        )

        // 1. Run sync -> should detect conflict
        val summary = engine.run(provider).getOrThrow()

        // 2. Verify conflict resolved
        assertEquals(1, summary.resolvedConflicts)
        assertEquals(0, summary.conflicts) // conflicts are resolved immediately

        // 3. Verify storage
        // Original file should be overwritten by remote content
        assertEquals("remote-content", storage.files["notes/a.org"])

        // Conflict copy should exist with local content
        val conflictFile = storage.files.keys.find { it.contains("(Conflicted Copy") }
        assertTrue("Conflict copy created", conflictFile != null)
        assertEquals("local-updated", storage.files[conflictFile])

        // 4. Verify DB state
        // Original state should be synced
        val originalState = dao.getState("notes/a.org")
        assertEquals(SyncPendingAction.NONE, originalState?.pendingAction)
        assertEquals("etag-2", originalState?.remoteFingerprint)

        // Conflict state should be pending upload
        val conflictState = dao.getState(conflictFile!!)
        assertEquals(SyncPendingAction.UPLOAD, conflictState?.pendingAction)
    }

    @Test
    fun `catastrophic delete throws exception`() = runTest(dispatcher) {
        val storage = FakeStorage(
            mutableMapOf(
                "notes/keep.org" to "keep-local-storage-nonempty",
            ),
        )
        val dao = database.syncStateDao()

        // Seed many existing states marked "deleted locally" so they'd all be pushed as deletes.
        val remoteNotes = (1..10).map {
            RemoteNoteMetadata(
                remoteId = "remote-$it",
                path = "notes/$it.org",
                fingerprint = "etag-$it",
                lastModifiedEpochMillis = null,
            )
        }
        remoteNotes.forEach {
            dao.upsert(
                SyncStateEntity(
                    path = it.path,
                    lifecycle = NoteLifecycle.DELETED_LOCALLY,
                    remoteId = it.remoteId,
                    remoteFingerprint = it.fingerprint,
                    localContentHash = NoteHashCalculator.compute("some content"),
                    lastKnownHash = NoteHashCalculator.compute("some content"),
                    lastSyncedAtEpochMillis = 1L,
                ),
            )
        }

        // Simulate that the remote still has all of these files (100% delete attempt)
        val provider = FakeRemoteSyncProvider(metadata = remoteNotes)

        val settingsRepo = mockk<WebDavSettingsRepository>(relaxed = true)
        engine = SyncEngine(storage, dao, settingsRepo, dispatcher)
        val result = engine.run(provider)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CatastrophicDeleteException)
    }

    @Test
    fun `threshold delete throws confirmation exception`() = runTest(dispatcher) {
        val storage = FakeStorage(mutableMapOf()) // Some local, some deleted
        val dao = database.syncStateDao()

        // Seed 20 remote notes; mark 6 as deleted locally so deletePercentage = 30% (> 20%).
        val remoteNotes = (1..20).map {
            RemoteNoteMetadata(
                remoteId = "remote-$it",
                path = "notes/$it.org",
                fingerprint = "etag-$it",
                lastModifiedEpochMillis = null,
            )
        }

        remoteNotes.take(6).forEach {
            dao.upsert(
                SyncStateEntity(
                    path = it.path,
                    lifecycle = NoteLifecycle.DELETED_LOCALLY,
                    remoteId = it.remoteId,
                    remoteFingerprint = it.fingerprint,
                    localContentHash = NoteHashCalculator.compute("some content"),
                    lastKnownHash = NoteHashCalculator.compute("some content"),
                    lastSyncedAtEpochMillis = 1L,
                ),
            )
        }

        // Keep the other notes in sync (avoid scheduling operations for them).
        remoteNotes.drop(6).forEach {
            val content = "content-${it.remoteId}"
            storage.files[it.path] = content
            val hash = NoteHashCalculator.compute(content)
            dao.upsert(
                SyncStateEntity(
                    path = it.path,
                    lifecycle = NoteLifecycle.ACTIVE,
                    remoteId = it.remoteId,
                    remoteFingerprint = it.fingerprint,
                    localContentHash = hash,
                    lastKnownHash = hash,
                    lastSyncedAtEpochMillis = 1L,
                ),
            )
        }

        val provider = FakeRemoteSyncProvider(metadata = remoteNotes)

        val settingsRepo = mockk<WebDavSettingsRepository>(relaxed = true)
        engine = SyncEngine(storage, dao, settingsRepo, dispatcher)
        val result = engine.run(provider)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RequiresConfirmationException)
    }

    private class FakeStorage(
        val files: MutableMap<String, String>,
    ) : IStorage {
        override suspend fun listNotes(): Result<List<String>> = Result.success(files.keys.toList())

        override suspend fun readNote(path: String): Result<String> =
            files[path]?.let { Result.success(it) } ?: Result.failure(IOException("missing"))

        override suspend fun writeNote(path: String, content: String): Result<Unit> {
            files[path] = content
            return Result.success(Unit)
        }

        override suspend fun deleteNote(path: String): Result<Unit> {
            files.remove(path)
            return Result.success(Unit)
        }

        override suspend fun renameNote(oldPath: String, newPath: String): Result<Unit> {
            val content = files.remove(oldPath) ?: return Result.failure(IOException("Source not found"))
            files[newPath] = content
            return Result.success(Unit)
        }

        override suspend fun resolveUri(path: String): Result<android.net.Uri> =
            Result.failure(UnsupportedOperationException("Not used in these tests"))

        override suspend fun invalidateCache() {
            // No-op
        }
    }

    private class FakeRemoteSyncProvider(
        var metadata: List<RemoteNoteMetadata> = emptyList(),
        val remoteFiles: Map<String, String> = emptyMap(),
    ) : RemoteSyncProvider {
        override val name: String = "fake"

        val uploadedPaths = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        var lastUploadFingerprint: String? = null

        override suspend fun listRemoteNotes(): Result<List<RemoteNoteMetadata>> = Result.success(metadata)

        override suspend fun download(path: String, remoteId: String): Result<java.io.InputStream> =
            remoteFiles[remoteId]?.let {
                Result.success(java.io.ByteArrayInputStream(it.toByteArray()))
            } ?: Result.failure(java.io.IOException("Not found"))

        override suspend fun upload(
            path: String,
            content: java.io.InputStream,
            remoteId: String?,
            expectedFingerprint: String?,
        ): Result<RemoteUploadResult> =
            runCatching {
                val uploadedContent = content.bufferedReader(Charsets.UTF_8).use { it.readText() }
                uploadedPaths.add(path)
                lastUploadFingerprint = "etag-${uploadedContent.hashCode()}"
                RemoteUploadResult(
                    remoteId = remoteId ?: "remote-uploaded",
                    fingerprint = lastUploadFingerprint,
                )
            }

        override suspend fun delete(path: String, remoteId: String, fingerprint: String?): Result<Unit> =
            runCatching {
                deletedPaths.add(path)
            }.map { Unit }
    }
}
