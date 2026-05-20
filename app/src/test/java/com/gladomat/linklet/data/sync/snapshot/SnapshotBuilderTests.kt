package com.gladomat.linklet.data.sync.snapshot

import com.gladomat.linklet.data.index.NoteAvailability
import com.gladomat.linklet.data.index.NoteDao
import com.gladomat.linklet.data.index.NoteEntity
import com.gladomat.linklet.data.index.NoteSource
import com.gladomat.linklet.data.sync.db.ServerSnapshotDao
import com.gladomat.linklet.data.sync.db.ServerSnapshotEntity
import com.gladomat.linklet.data.sync.metrics.InMemorySyncMetrics
import com.gladomat.linklet.data.sync.metrics.SyncMetricKeys
import com.gladomat.linklet.data.sync.webdav.PropfindItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// region Fakes

class FakePropfindClient : PropfindSource {
    private val responses = mutableMapOf<String, Result<List<PropfindItem>>>()
    private val depthResponses = mutableMapOf<Pair<String, Int>, Result<List<PropfindItem>>>()

    fun stub(url: String, items: List<PropfindItem>) {
        responses[url] = Result.success(items)
    }

    fun stub(url: String, depth: Int, items: List<PropfindItem>) {
        depthResponses[url to depth] = Result.success(items)
    }

    fun stubFailure(url: String, error: Throwable) {
        responses[url] = Result.failure(error)
    }

    override fun propfind(url: String, depth: Int): Result<List<PropfindItem>> {
        // Depth-specific stubs take priority
        depthResponses[url to depth]?.let { return it }
        return responses[url] ?: Result.failure(IllegalStateException("No stub for URL: $url depth: $depth"))
    }
}

class FakeServerSnapshotDao : ServerSnapshotDao {
    val items = mutableListOf<ServerSnapshotEntity>()

    override suspend fun upsertAll(items: List<ServerSnapshotEntity>) {
        for (item in items) {
            this.items.removeAll { it.rootId == item.rootId && it.path == item.path }
            this.items.add(item)
        }
    }

    override suspend fun upsert(item: ServerSnapshotEntity) {
        items.removeAll { it.rootId == item.rootId && it.path == item.path }
        items.add(item)
    }

    override suspend fun getByPath(rootId: String, path: String): ServerSnapshotEntity? {
        return items.find { it.rootId == rootId && it.path == path }
    }

    override suspend fun clearRoot(rootId: String) {
        items.removeAll { it.rootId == rootId }
    }

    override suspend fun getAllFiles(rootId: String): List<ServerSnapshotEntity> {
        return items.filter { it.rootId == rootId && !it.isDir }
    }

    override suspend fun purgeTombstones(rootId: String, cutoffEpochMillis: Long): Int {
        val before = items.size
        items.removeAll { it.rootId == rootId && it.deletedAtEpochMillis != null && it.deletedAtEpochMillis!! < cutoffEpochMillis }
        return before - items.size
    }

    override suspend fun findExpiredTombstones(rootId: String, cutoffEpochMillis: Long): List<ServerSnapshotEntity> {
        return items.filter { it.rootId == rootId && it.deletedAtEpochMillis != null && it.deletedAtEpochMillis!! < cutoffEpochMillis }
    }
}

class FakeNoteDao : NoteDao {
    val notes = mutableListOf<NoteEntity>()

    override suspend fun insertNotes(notes: List<NoteEntity>) {
        for (note in notes) {
            this.notes.removeAll { it.path == note.path }
            this.notes.add(note)
        }
    }

    override suspend fun getNoteAvailability(path: String): NoteAvailability? {
        return notes.find { it.path == path }?.availability
    }

    // Unused methods - minimal stubs for compilation
    override suspend fun insertLinks(links: List<com.gladomat.linklet.data.index.LinkEntity>) = Unit
    override suspend fun deleteLinksBySource(source: String) = Unit
    override suspend fun deleteLinksByTarget(target: String) = Unit
    override suspend fun deleteLinksByOrgId(orgId: String) = Unit
    override suspend fun clearNotes() = Unit
    override suspend fun clearLocalNotes() = Unit
    override suspend fun clearLinks() = Unit
    override suspend fun getAllNotes(): List<NoteEntity> = notes.toList()
    override suspend fun listNotesNeedingLinks(): List<NoteEntity> = emptyList()
    override fun observeActiveNotes() = kotlinx.coroutines.flow.flowOf(notes.toList())
    override suspend fun markDeleted(path: String, deletedAt: Long) = Unit
    override suspend fun updateLinksReady(path: String, linksReady: Boolean) = Unit
    override suspend fun getAllFileTagsSerialized(): List<String> = emptyList()
    override suspend fun findPathByOrgId(orgId: String): String? = null
    override suspend fun findOrgIdByPath(path: String): String? = null
    override suspend fun updateNotePath(oldPath: String, newPath: String) = Unit
    override suspend fun updateLinksSource(oldPath: String, newPath: String) = Unit
    override suspend fun updateLinksTarget(oldPath: String, newPath: String) = Unit
    override suspend fun renameNotePath(oldPath: String, newPath: String) = Unit
    override suspend fun getBacklinks(path: String): List<com.gladomat.linklet.data.index.LinkWithSourceTitle> = emptyList()
    override suspend fun getNotesByAvailability(availabilities: List<NoteAvailability>): List<NoteEntity> =
        notes.filter { it.availability in availabilities }
}

// endregion

class SnapshotBuilderTests {

    private lateinit var propfindClient: FakePropfindClient
    private lateinit var snapshotDao: FakeServerSnapshotDao
    private lateinit var noteDao: FakeNoteDao
    private lateinit var metrics: InMemorySyncMetrics

    private val rootUrl = "https://cloud.example.com/remote.php/dav/files/user/Notes"
    private val rootId = "root1"
    private val rootPrefix = "/remote.php/dav/files/user/Notes"

    @Before
    fun setUp() {
        propfindClient = FakePropfindClient()
        snapshotDao = FakeServerSnapshotDao()
        noteDao = FakeNoteDao()
        metrics = InMemorySyncMetrics()
    }

    private fun builder(batchSize: Int = 500) = SnapshotBuilder(
        propfindClient = propfindClient,
        snapshotDao = snapshotDao,
        noteDao = noteDao,
        metrics = metrics,
        batchSize = batchSize,
    )

    private fun fileItem(relativePath: String, etag: String = "etag1"): PropfindItem {
        return PropfindItem(
            href = "$rootPrefix/$relativePath",
            etag = etag,
            lastModifiedEpochMillis = 1000L,
            sizeBytes = 100L,
            isDir = false,
            fileId = "fid-$relativePath",
        )
    }

    private fun dirItem(relativePath: String): PropfindItem {
        val href = if (relativePath.isEmpty()) "$rootPrefix/" else "$rootPrefix/$relativePath/"
        return PropfindItem(
            href = href,
            etag = null,
            lastModifiedEpochMillis = null,
            sizeBytes = null,
            isDir = true,
            fileId = "did-$relativePath",
        )
    }

    @Test
    fun `basic traversal populates server_snapshot and creates stub notes`() = runTest {
        propfindClient.stub(rootUrl, listOf(
            dirItem(""),           // self
            fileItem("readme.org"),
            fileItem("image.png"),
        ))

        val result = builder().buildSnapshot(rootId, rootUrl)

        assertEquals(1, result.directoriesTraversed)
        assertEquals(2, result.filesFound)
        assertEquals(2, result.stubsCreated)
        assertTrue(result.pendingDirectories.isEmpty())

        // Verify snapshot entities
        assertEquals(2, snapshotDao.items.size)
        assertTrue(snapshotDao.items.any { it.path == "readme.org" && !it.isDir })
        assertTrue(snapshotDao.items.any { it.path == "image.png" && !it.isDir })

        // Verify stub notes
        assertEquals(2, noteDao.notes.size)
        val readmeNote = noteDao.notes.find { it.path == "readme.org" }!!
        assertEquals("readme", readmeNote.title)
        assertEquals(NoteSource.REMOTE_STUB, readmeNote.source)
        assertEquals(NoteAvailability.STUB, readmeNote.availability)

        val imageNote = noteDao.notes.find { it.path == "image.png" }!!
        assertEquals("image", imageNote.title)
    }

    @Test
    fun `non-syncable paths are skipped for stub creation`() = runTest {
        propfindClient.stub(rootUrl, listOf(
            dirItem(""),
            fileItem(".hidden/secret.org"),   // hidden segment
            fileItem("_draft/notes.org"),      // underscore-prefixed segment
            fileItem("readme.txt"),            // not in allowlist
            fileItem("readme.org"),            // allowed
        ))

        val result = builder().buildSnapshot(rootId, rootUrl)

        assertEquals(4, result.filesFound)
        assertEquals(1, result.stubsCreated) // only readme.org
        assertEquals(1, noteDao.notes.size)
        assertEquals("readme.org", noteDao.notes.first().path)

        // All files still go into server_snapshot
        assertEquals(4, snapshotDao.items.size)
    }

    @Test
    fun `directories are traversed recursively`() = runTest {
        propfindClient.stub(rootUrl, listOf(
            dirItem(""),
            dirItem("sub"),
            fileItem("root.org"),
        ))

        propfindClient.stub("$rootUrl/sub", listOf(
            dirItem("sub"),        // self
            fileItem("sub/deep.org"),
        ))

        val result = builder().buildSnapshot(rootId, rootUrl)

        assertEquals(2, result.directoriesTraversed)
        assertEquals(2, result.filesFound)
        assertEquals(2, result.stubsCreated)

        // Snapshot has the dir + 2 files
        assertTrue(snapshotDao.items.any { it.path == "sub" && it.isDir })
        assertTrue(snapshotDao.items.any { it.path == "root.org" })
        assertTrue(snapshotDao.items.any { it.path == "sub/deep.org" })

        // Metrics
        val snap = metrics.snapshot()
        assertEquals(2, snap.counts[SyncMetricKeys.HTTP_PROPFIND])
    }

    @Test
    fun `maxDirectories cap triggers early stop with pendingDirectories`() = runTest {
        propfindClient.stub(rootUrl, listOf(
            dirItem(""),
            dirItem("alpha"),
            dirItem("beta"),
            fileItem("top.org"),
        ))

        propfindClient.stub("$rootUrl/alpha", listOf(
            dirItem("alpha"),
            fileItem("alpha/a.org"),
        ))

        // beta is never traversed because maxDirectories=2
        propfindClient.stub("$rootUrl/beta", listOf(
            dirItem("beta"),
            fileItem("beta/b.org"),
        ))

        val result = builder().buildSnapshot(rootId, rootUrl, maxDirectories = 2)

        assertEquals(2, result.directoriesTraversed)
        assertEquals(listOf("beta"), result.pendingDirectories)
    }

    @Test
    fun `resumeFrom starts from specified directories instead of root`() = runTest {
        // Do NOT stub root URL - it should not be called
        propfindClient.stub("$rootUrl/alpha", listOf(
            dirItem("alpha"),
            fileItem("alpha/a.org"),
        ))

        propfindClient.stub("$rootUrl/beta", listOf(
            dirItem("beta"),
            fileItem("beta/b.org"),
        ))

        val result = builder().buildSnapshot(
            rootId,
            rootUrl,
            resumeFrom = listOf("alpha", "beta"),
        )

        assertEquals(2, result.directoriesTraversed)
        assertEquals(2, result.filesFound)
        assertEquals(2, result.stubsCreated)
        assertTrue(result.pendingDirectories.isEmpty())
    }

    @Test
    fun `existing note with better availability is not overwritten`() = runTest {
        // Pre-populate a note with AVAILABLE status
        noteDao.notes.add(
            NoteEntity(
                path = "existing.org",
                title = "Existing",
                source = NoteSource.LOCAL,
                availability = NoteAvailability.AVAILABLE,
            ),
        )

        propfindClient.stub(rootUrl, listOf(
            dirItem(""),
            fileItem("existing.org"),
            fileItem("new.org"),
        ))

        val result = builder().buildSnapshot(rootId, rootUrl)

        assertEquals(2, result.filesFound)
        assertEquals(1, result.stubsCreated) // only new.org

        // existing.org should still have AVAILABLE status
        val existing = noteDao.notes.find { it.path == "existing.org" }!!
        assertEquals(NoteAvailability.AVAILABLE, existing.availability)
        assertEquals(NoteSource.LOCAL, existing.source)
    }

    @Test
    fun `path extraction handles percent-encoded characters`() {
        val builder = builder()
        val path = builder.extractRelativePath(
            "$rootPrefix/My%20Notes/file%20name.org",
            rootPrefix,
        )
        assertEquals("My Notes/file name.org", path)
    }

    @Test
    fun `path extraction preserves encoded slashes`() {
        val builder = builder()
        val path = builder.extractRelativePath(
            "$rootPrefix/Foo%2FBar/note.org",
            rootPrefix,
        )
        assertEquals("Foo%2FBar/note.org", path)
    }

    @Test
    fun `propfind failure for a directory is skipped gracefully`() = runTest {
        propfindClient.stub(rootUrl, listOf(
            dirItem(""),
            dirItem("good"),
            dirItem("bad"),
        ))

        propfindClient.stub("$rootUrl/good", listOf(
            dirItem("good"),
            fileItem("good/a.org"),
        ))

        propfindClient.stubFailure("$rootUrl/bad", java.io.IOException("network error"))

        val result = builder().buildSnapshot(rootId, rootUrl)

        // Root + good traversed; bad failed so not counted
        assertEquals(2, result.directoriesTraversed)
        assertEquals(1, result.filesFound)
        // 3 PROPFIND calls made (root, good, bad)
        assertEquals(3, metrics.snapshot().counts[SyncMetricKeys.HTTP_PROPFIND])
    }

    @Test
    fun `batching flushes at configured batch size`() = runTest {
        val items = mutableListOf<PropfindItem>(dirItem(""))
        for (i in 1..5) {
            items.add(fileItem("file$i.org", etag = "etag$i"))
        }
        propfindClient.stub(rootUrl, items)

        // Use batch size of 3 to test flushing mid-traversal
        val result = builder(batchSize = 3).buildSnapshot(rootId, rootUrl)

        assertEquals(5, result.filesFound)
        assertEquals(5, snapshotDao.items.size)
        assertEquals(5, noteDao.notes.size)
    }

    @Test
    fun `title extraction handles nested paths and extensions`() = runTest {
        propfindClient.stub(rootUrl, listOf(
            dirItem(""),
            fileItem("sub/my.note.org"),
        ))

        builder().buildSnapshot(rootId, rootUrl)

        val note = noteDao.notes.find { it.path == "sub/my.note.org" }!!
        assertEquals("my.note", note.title)
    }

    // region ETag-bubbled traversal tests

    private fun dirItemWithEtag(relativePath: String, etag: String): PropfindItem {
        val href = if (relativePath.isEmpty()) "$rootPrefix/" else "$rootPrefix/$relativePath/"
        return PropfindItem(
            href = href,
            etag = etag,
            lastModifiedEpochMillis = null,
            sizeBytes = null,
            isDir = true,
            fileId = "did-$relativePath",
        )
    }

    @Test
    fun `etag bubbling skips unchanged directory`() = runTest {
        // Pre-populate snapshot with a directory that has a known ETag
        snapshotDao.upsert(ServerSnapshotEntity(
            rootId = rootId,
            path = "sub",
            href = "$rootPrefix/sub/",
            etag = "dir-etag-1",
            isDir = true,
            fileId = "did-sub",
            lastSeenAtEpochMillis = 1000L,
        ))

        // Root listing returns one subdirectory
        propfindClient.stub(rootUrl, listOf(
            dirItem(""),
            dirItemWithEtag("sub", "dir-etag-1"),
            fileItem("root.org"),
        ))

        // Depth:0 probe for sub returns same ETag → skip
        propfindClient.stub("$rootUrl/sub", 0, listOf(
            dirItemWithEtag("sub", "dir-etag-1"),
        ))

        val result = builder().buildSnapshot(rootId, rootUrl, etagBubbling = true)

        assertEquals(1, result.directoriesTraversed)  // only root
        assertEquals(1, result.directoriesSkipped)      // sub was skipped
        assertEquals(1, result.filesFound)               // root.org only

        // 2 PROPFIND calls: root (depth=1) + sub (depth=0 probe)
        assertEquals(2, metrics.snapshot().counts[SyncMetricKeys.HTTP_PROPFIND])
    }

    @Test
    fun `etag bubbling traverses changed directory`() = runTest {
        // Pre-populate snapshot with old ETag
        snapshotDao.upsert(ServerSnapshotEntity(
            rootId = rootId,
            path = "sub",
            href = "$rootPrefix/sub/",
            etag = "dir-etag-OLD",
            isDir = true,
            fileId = "did-sub",
            lastSeenAtEpochMillis = 1000L,
        ))

        propfindClient.stub(rootUrl, listOf(
            dirItem(""),
            dirItemWithEtag("sub", "dir-etag-NEW"),
            fileItem("root.org"),
        ))

        // Depth:0 probe returns NEW ETag → must traverse
        propfindClient.stub("$rootUrl/sub", 0, listOf(
            dirItemWithEtag("sub", "dir-etag-NEW"),
        ))

        // Depth:1 full listing for sub
        propfindClient.stub("$rootUrl/sub", 1, listOf(
            dirItemWithEtag("sub", "dir-etag-NEW"),
            fileItem("sub/new.org"),
        ))

        val result = builder().buildSnapshot(rootId, rootUrl, etagBubbling = true)

        assertEquals(2, result.directoriesTraversed)  // root + sub
        assertEquals(0, result.directoriesSkipped)
        assertEquals(2, result.filesFound)             // root.org + sub/new.org

        // 3 PROPFIND calls: root (depth=1) + sub (depth=0 probe) + sub (depth=1 listing)
        assertEquals(3, metrics.snapshot().counts[SyncMetricKeys.HTTP_PROPFIND])
    }

    @Test
    fun `etag bubbling does not skip root directory`() = runTest {
        // Even with etagBubbling, root ("") should always be traversed
        propfindClient.stub(rootUrl, listOf(
            dirItem(""),
            fileItem("note.org"),
        ))

        val result = builder().buildSnapshot(rootId, rootUrl, etagBubbling = true)

        assertEquals(1, result.directoriesTraversed)
        assertEquals(0, result.directoriesSkipped)
        assertEquals(1, result.filesFound)
    }

    @Test
    fun `etag bubbling falls back to full listing when no stored snapshot exists`() = runTest {
        // No pre-populated snapshot → probe cannot match, must do full listing
        propfindClient.stub(rootUrl, listOf(
            dirItem(""),
            dirItemWithEtag("sub", "some-etag"),
            fileItem("root.org"),
        ))

        // Depth:0 probe returns an ETag, but no stored snapshot exists
        propfindClient.stub("$rootUrl/sub", 0, listOf(
            dirItemWithEtag("sub", "some-etag"),
        ))

        // Full depth:1 listing
        propfindClient.stub("$rootUrl/sub", 1, listOf(
            dirItemWithEtag("sub", "some-etag"),
            fileItem("sub/file.org"),
        ))

        val result = builder().buildSnapshot(rootId, rootUrl, etagBubbling = true)

        assertEquals(2, result.directoriesTraversed)  // root + sub (not skipped)
        assertEquals(0, result.directoriesSkipped)
        assertEquals(2, result.filesFound)
    }

    // endregion
}
