package com.gladomat.linklet.data.sync

import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.settings.WebDavSettingsRepository
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.storage.StorageFileStat
import com.gladomat.linklet.data.sync.metrics.InMemorySyncMetrics
import com.gladomat.linklet.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineFreshInstallTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `fresh install overlap identical does not create conflicted copy`() = runTest {
        val path = "note.org"
        val content = "hello"
        val localStorage = InMemoryStorage(mutableMapOf(path to content.toByteArray()))
        val syncStateDao = InMemorySyncStateDao()
        val settingsRepo = mockk<WebDavSettingsRepository>()
        coEvery { settingsRepo.currentSettings() } returns null

        val remoteProvider = InMemoryRemoteProvider(
            remoteFiles = mapOf(
                path to RemoteFile(
                    bytes = content.toByteArray(),
                    fingerprint = "etag-1",
                ),
            ),
        )

        val engine = SyncEngine(
            storage = localStorage,
            syncStateDao = syncStateDao,
            webDavSettingsRepository = settingsRepo,
            dispatcher = dispatcherRule.dispatcher,
            metrics = InMemorySyncMetrics(),
        )

        val result = engine.run(remoteProvider).getOrThrow()
        assertNotNull(result)

        // No "(Conflicted Copy ...)" should be created when content is identical.
        val allPaths = localStorage.listFiles().getOrThrow()
        assertFalse(allPaths.any { it.contains("Conflicted Copy") })

        // State should be seeded for the original path.
        val state = syncStateDao.getState(path)
        assertNotNull(state)
        assertEquals("etag-1", state?.remoteFingerprint)
        assertNotNull(state?.localContentHash)

        // Remote content should be present at original path.
        val final = localStorage.readNote(path).getOrThrow()
        assertEquals(content, final)

        // We should have downloaded at least once.
        assertTrue(remoteProvider.downloadCount > 0)
        assertEquals(0, remoteProvider.uploadCount)
    }

    @Test
    fun `fresh install overlap different creates conflicted copy and keeps remote at original path`() = runTest {
        val path = "note.org"
        val localContent = "local"
        val remoteContent = "remote"
        val localStorage = InMemoryStorage(mutableMapOf(path to localContent.toByteArray()))
        val syncStateDao = InMemorySyncStateDao()
        val settingsRepo = mockk<WebDavSettingsRepository>()
        coEvery { settingsRepo.currentSettings() } returns null

        val remoteProvider = InMemoryRemoteProvider(
            remoteFiles = mapOf(
                path to RemoteFile(
                    bytes = remoteContent.toByteArray(),
                    fingerprint = "etag-2",
                ),
            ),
        )

        val engine = SyncEngine(
            storage = localStorage,
            syncStateDao = syncStateDao,
            webDavSettingsRepository = settingsRepo,
            dispatcher = dispatcherRule.dispatcher,
            metrics = InMemorySyncMetrics(),
        )

        engine.run(remoteProvider).getOrThrow()

        val allPaths = localStorage.listFiles().getOrThrow()
        val conflictPaths = allPaths.filter { it.contains("(Conflicted Copy") }
        assertEquals(1, conflictPaths.size)

        // The conflicted copy preserves local content.
        val conflictPath = conflictPaths.single()
        assertEquals(localContent, localStorage.readNote(conflictPath).getOrThrow())

        // Original path is replaced with remote content.
        assertEquals(remoteContent, localStorage.readNote(path).getOrThrow())

        // Original path state is updated.
        val state = syncStateDao.getState(path)
        assertNotNull(state)
        assertEquals("etag-2", state?.remoteFingerprint)

        // Conflicted copy should be marked for upload on a later run.
        val conflictState = syncStateDao.getState(conflictPath)
        assertNotNull(conflictState)
        assertEquals(SyncPendingAction.UPLOAD, conflictState?.pendingAction)

        assertEquals(0, remoteProvider.uploadCount)
        assertTrue(remoteProvider.downloadCount > 0)
    }

    private data class RemoteFile(
        val bytes: ByteArray,
        val fingerprint: String?,
    )

    private class InMemoryRemoteProvider(
        private val remoteFiles: Map<String, RemoteFile>,
    ) : RemoteSyncProvider {

        var downloadCount: Int = 0
            private set

        var uploadCount: Int = 0
            private set

        override val name: String = "test"

        override suspend fun listRemoteNotes(): Result<List<RemoteNoteMetadata>> = Result.success(
            remoteFiles.map { (path, file) ->
                RemoteNoteMetadata(
                    remoteId = path,
                    path = path,
                    fingerprint = file.fingerprint,
                    lastModifiedEpochMillis = null,
                )
            },
        )

        override suspend fun download(path: String, remoteId: String): Result<InputStream> {
            downloadCount += 1
            val file = remoteFiles[remoteId] ?: return Result.failure(IllegalArgumentException("missing remoteId=$remoteId"))
            return Result.success(ByteArrayInputStream(file.bytes))
        }

        override suspend fun upload(
            path: String,
            content: InputStream,
            remoteId: String?,
            expectedFingerprint: String?,
        ): Result<RemoteUploadResult> {
            uploadCount += 1
            return Result.failure(AssertionError("upload should not be called in these tests"))
        }

        override suspend fun delete(path: String, remoteId: String, fingerprint: String?): Result<Unit> {
            return Result.failure(AssertionError("delete should not be called in these tests"))
        }
    }

    private class InMemoryStorage(
        private val files: MutableMap<String, ByteArray>,
    ) : IStorage {

        override suspend fun listNotes(): Result<List<String>> = Result.success(
            files.keys.filter { it.endsWith(".org", ignoreCase = true) }.sorted(),
        )

        override suspend fun listFiles(): Result<List<String>> = Result.success(files.keys.sorted())

        override suspend fun readNote(path: String): Result<String> {
            val bytes = files[path] ?: return Result.failure(IllegalArgumentException("missing $path"))
            return Result.success(bytes.toString(Charsets.UTF_8))
        }

        override suspend fun readFileBytes(path: String): Result<ByteArray> {
            val bytes = files[path] ?: return Result.failure(IllegalArgumentException("missing $path"))
            return Result.success(bytes)
        }

        override suspend fun statNote(path: String): Result<StorageFileStat> {
            val bytes = files[path] ?: return Result.failure(IllegalArgumentException("missing $path"))
            return Result.success(
                StorageFileStat(
                    lastModifiedEpochMillis = 123L,
                    sizeBytes = bytes.size.toLong(),
                ),
            )
        }

        override suspend fun writeNote(path: String, content: String): Result<Unit> {
            files[path] = content.toByteArray(Charsets.UTF_8)
            return Result.success(Unit)
        }

        override suspend fun writeFileBytes(path: String, content: ByteArray): Result<Unit> {
            files[path] = content
            return Result.success(Unit)
        }

        override suspend fun deleteNote(path: String): Result<Unit> {
            files.remove(path)
            return Result.success(Unit)
        }

        override suspend fun renameNote(oldPath: String, newPath: String): Result<Unit> {
            val bytes = files.remove(oldPath) ?: return Result.failure(IllegalArgumentException("missing $oldPath"))
            files[newPath] = bytes
            return Result.success(Unit)
        }

        override suspend fun resolveUri(path: String): Result<android.net.Uri> {
            return Result.failure(UnsupportedOperationException("not needed"))
        }

        override suspend fun invalidateCache() {
            // no-op
        }
    }

    private class InMemorySyncStateDao : SyncStateDao {
        private val states = linkedMapOf<String, SyncStateEntity>()

        override suspend fun upsert(state: SyncStateEntity) {
            states[state.path] = state
        }

        override suspend fun getState(path: String): SyncStateEntity? = states[path]

        override suspend fun getPendingStates(none: SyncPendingAction): List<SyncStateEntity> =
            states.values.filter { it.pendingAction != none }

        override suspend fun updatePendingAction(path: String, action: SyncPendingAction, lastError: String?) {
            val current = states[path] ?: return
            states[path] = current.copy(pendingAction = action, lastError = lastError)
        }

        override suspend fun deleteState(path: String) {
            states.remove(path)
        }

        override suspend fun getAllStates(): List<SyncStateEntity> = states.values.toList()

        override suspend fun count(): Int = states.size

        override suspend fun clearAllStates() {
            states.clear()
        }
    }
}
