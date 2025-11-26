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
        engine = SyncEngine(storage, database.syncStateDao(), dispatcher)

        val summary = engine.sync(provider).getOrThrow()

        assertEquals(1, summary.pendingUploads)
        assertEquals(0, summary.pendingDownloads)
        val states = database.syncStateDao().getPendingStates()
        assertEquals(1, states.size)
        assertEquals(SyncPendingAction.UPLOAD, states.first().pendingAction)
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
        )
        engine = SyncEngine(storage, database.syncStateDao(), dispatcher)

        val summary = engine.sync(provider).getOrThrow()

        assertEquals(1, summary.pendingDownloads)
        val states = database.syncStateDao().getPendingStates()
        assertEquals(SyncPendingAction.DOWNLOAD, states.first().pendingAction)
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
                lastKnownHash = NoteHashCalculator.compute("old"),
                pendingAction = SyncPendingAction.NONE,
            ),
        )

        engine = SyncEngine(storage, dao, dispatcher)

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
        val summaryUpload = engine.sync(providerUpload).getOrThrow()
        assertEquals(1, summaryUpload.pendingUploads)

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
        )
        storage.files["notes/a.org"] = "local" // ensure same hash
        val summaryDownload = engine.sync(providerDownload).getOrThrow()
        assertEquals(1, summaryDownload.pendingDownloads)

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
        )
        val summaryConflict = engine.sync(providerConflict).getOrThrow()
        assertEquals(1, summaryConflict.conflicts)
        val states = dao.getPendingStates()
        assertTrue(states.any { it.pendingAction == SyncPendingAction.CONFLICT })
    }

    @Test
    fun `local deletion queued as remote delete`() = runTest(dispatcher) {
        val storage = FakeStorage(mutableMapOf())
        val dao = database.syncStateDao()
        dao.upsert(
            SyncStateEntity(
                path = "notes/a.org",
                remoteId = "remote-1",
                remoteFingerprint = "etag-1",
                lastKnownHash = NoteHashCalculator.compute("old"),
            ),
        )
        val provider = FakeRemoteSyncProvider(
            metadata = listOf(
                RemoteNoteMetadata(
                    remoteId = "remote-1",
                    path = "notes/a.org",
                    fingerprint = "etag-1",
                    lastModifiedEpochMillis = null,
                ),
            ),
        )

        engine = SyncEngine(storage, dao, dispatcher)
        val summary = engine.sync(provider).getOrThrow()

        assertEquals(1, summary.pendingDeletes)
        val states = dao.getPendingStates()
        assertEquals(SyncPendingAction.DELETE, states.first().pendingAction)
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
    }

    private class FakeRemoteSyncProvider(
        var metadata: List<RemoteNoteMetadata> = emptyList(),
    ) : RemoteSyncProvider {
        override val name: String = "fake"

        override suspend fun listRemoteNotes(): Result<List<RemoteNoteMetadata>> = Result.success(metadata)

        override suspend fun download(path: String, remoteId: String): Result<java.io.InputStream> =
            Result.failure(UnsupportedOperationException())

        override suspend fun upload(
            path: String,
            content: java.io.InputStream,
            remoteId: String?,
            expectedFingerprint: String?,
        ): Result<RemoteUploadResult> =
            Result.failure(UnsupportedOperationException())

        override suspend fun delete(path: String, remoteId: String, fingerprint: String?): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }
}
