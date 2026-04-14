package com.gladomat.linklet.data.sync.delta

import com.gladomat.linklet.data.sync.db.ServerSnapshotDao
import com.gladomat.linklet.data.sync.db.ServerSnapshotEntity
import com.gladomat.linklet.data.sync.db.SyncWatermarkDao
import com.gladomat.linklet.data.sync.db.SyncWatermarkEntity
import com.gladomat.linklet.data.sync.metrics.InMemorySyncMetrics
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

class FakeSyncWatermarkDao : SyncWatermarkDao {
    private val store = mutableMapOf<String, SyncWatermarkEntity>()

    override suspend fun upsert(entity: SyncWatermarkEntity) {
        store[entity.rootId] = entity
    }

    override suspend fun get(rootId: String): SyncWatermarkEntity? = store[rootId]

    override suspend fun delete(rootId: String) {
        store.remove(rootId)
    }
}

class FakeServerSnapshotDao : ServerSnapshotDao {
    private val items = mutableListOf<ServerSnapshotEntity>()

    override suspend fun upsertAll(items: List<ServerSnapshotEntity>) {
        items.forEach { upsert(it) }
    }

    override suspend fun upsert(item: ServerSnapshotEntity) {
        this.items.removeAll { it.rootId == item.rootId && it.path == item.path }
        this.items.add(item)
    }

    override suspend fun getByPath(rootId: String, path: String): ServerSnapshotEntity? =
        items.find { it.rootId == rootId && it.path == path }

    override suspend fun clearRoot(rootId: String) {
        items.removeAll { it.rootId == rootId }
    }

    override suspend fun getAllFiles(rootId: String): List<ServerSnapshotEntity> =
        items.filter { it.rootId == rootId && !it.isDir }

    override suspend fun purgeTombstones(rootId: String, cutoffEpochMillis: Long): Int {
        val before = items.size
        items.removeAll { it.rootId == rootId && it.deletedAtEpochMillis != null && it.deletedAtEpochMillis < cutoffEpochMillis }
        return before - items.size
    }

    override suspend fun findExpiredTombstones(rootId: String, cutoffEpochMillis: Long): List<ServerSnapshotEntity> =
        items.filter { it.rootId == rootId && it.deletedAtEpochMillis != null && it.deletedAtEpochMillis < cutoffEpochMillis }
}

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NextcloudDeltaClientTests {
    private lateinit var server: MockWebServer
    private lateinit var watermarkDao: FakeSyncWatermarkDao
    private lateinit var metrics: InMemorySyncMetrics
    private lateinit var client: NextcloudDeltaClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        watermarkDao = FakeSyncWatermarkDao()
        metrics = InMemorySyncMetrics()
        client = NextcloudDeltaClient(
            httpClient = OkHttpClient(),
            watermarkDao = watermarkDao,
            metrics = metrics,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `no watermark returns hard invalid`() = runTest {
        val result = client.fetchDelta(
            rootId = "root1",
            baseUrl = server.url("/").toString(),
            rootPath = "/remote.php/dav/files/user/",
            supportsSearch = true,
            supportsTrashbin = false,
            nowEpochMillis = System.currentTimeMillis(),
        )

        assertEquals(DeltaValidityState.HARD_INVALID, result.validity.state)
        assertEquals("no watermark", result.validity.reason)
        assertTrue(result.changes.isEmpty())
        assertNull(result.serverTimeEpochMillis)
    }

    @Test
    fun `search not supported returns hard invalid`() = runTest {
        watermarkDao.upsert(
            SyncWatermarkEntity(
                rootId = "root1",
                lastDeltaServerTimeEpochMillis = 1_000_000L,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )

        val result = client.fetchDelta(
            rootId = "root1",
            baseUrl = server.url("/").toString(),
            rootPath = "/remote.php/dav/files/user/",
            supportsSearch = false,
            supportsTrashbin = false,
            nowEpochMillis = System.currentTimeMillis(),
        )

        assertEquals(DeltaValidityState.HARD_INVALID, result.validity.state)
        assertEquals("search not supported", result.validity.reason)
    }

    @Test
    fun `successful delta returns changes and valid`() = runTest {
        watermarkDao.upsert(
            SyncWatermarkEntity(
                rootId = "root1",
                lastDeltaServerTimeEpochMillis = 1_000_000L,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )

        val multistatusXml = """<?xml version="1.0" encoding="UTF-8"?>
<d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:response>
    <d:href>/remote.php/dav/files/user/test.txt</d:href>
    <d:propstat>
      <d:prop>
        <d:getetag>"abc123"</d:getetag>
        <d:getlastmodified>Mon, 01 Jan 2024 12:00:00 GMT</d:getlastmodified>
        <d:getcontentlength>1024</d:getcontentlength>
        <d:resourcetype/>
        <oc:fileid>42</oc:fileid>
      </d:prop>
    </d:propstat>
  </d:response>
</d:multistatus>"""

        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .addHeader("Date", "Mon, 01 Jan 2024 12:05:00 GMT")
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody(multistatusXml),
        )

        val result = client.fetchDelta(
            rootId = "root1",
            baseUrl = server.url("/").toString(),
            rootPath = "/remote.php/dav/files/user/",
            supportsSearch = true,
            supportsTrashbin = false,
            nowEpochMillis = System.currentTimeMillis(),
        )

        assertEquals(DeltaValidityState.VALID, result.validity.state)
        // Verify server time was captured from Date header (Mon, 01 Jan 2024 12:05:00 GMT)
        assertEquals(1704110700000L, result.serverTimeEpochMillis)
        assertEquals(1, result.changes.size)
        val change = result.changes[0]
        assertEquals("/remote.php/dav/files/user/test.txt", change.href)
        assertEquals("abc123", change.etag)
        assertEquals("42", change.fileId)
        assertEquals(1024L, change.sizeBytes)
        assertEquals(false, change.isDir)
        assertEquals(false, change.isDeleted)
    }

    @Test
    fun `server error 5xx returns hard invalid`() = runTest {
        watermarkDao.upsert(
            SyncWatermarkEntity(
                rootId = "root1",
                lastDeltaServerTimeEpochMillis = 1_000_000L,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Date", "Mon, 01 Jan 2024 12:05:00 GMT")
                .setBody("Internal Server Error"),
        )

        val result = client.fetchDelta(
            rootId = "root1",
            baseUrl = server.url("/").toString(),
            rootPath = "/remote.php/dav/files/user/",
            supportsSearch = true,
            supportsTrashbin = false,
            nowEpochMillis = System.currentTimeMillis(),
        )

        assertEquals(DeltaValidityState.HARD_INVALID, result.validity.state)
        assertEquals("server error: 500", result.validity.reason)
    }

    @Test
    fun `deduplication by dedupKey works`() = runTest {
        watermarkDao.upsert(
            SyncWatermarkEntity(
                rootId = "root1",
                lastDeltaServerTimeEpochMillis = 1_000_000L,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )

        // Two responses with the same fileId and etag should be deduplicated
        val multistatusXml = """<?xml version="1.0" encoding="UTF-8"?>
<d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:response>
    <d:href>/remote.php/dav/files/user/dup1.txt</d:href>
    <d:propstat>
      <d:prop>
        <d:getetag>"same-etag"</d:getetag>
        <d:getlastmodified>Mon, 01 Jan 2024 12:00:00 GMT</d:getlastmodified>
        <d:getcontentlength>100</d:getcontentlength>
        <d:resourcetype/>
        <oc:fileid>99</oc:fileid>
      </d:prop>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/remote.php/dav/files/user/dup2.txt</d:href>
    <d:propstat>
      <d:prop>
        <d:getetag>"same-etag"</d:getetag>
        <d:getlastmodified>Mon, 01 Jan 2024 12:00:00 GMT</d:getlastmodified>
        <d:getcontentlength>100</d:getcontentlength>
        <d:resourcetype/>
        <oc:fileid>99</oc:fileid>
      </d:prop>
    </d:propstat>
  </d:response>
</d:multistatus>"""

        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .addHeader("Date", "Mon, 01 Jan 2024 12:05:00 GMT")
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody(multistatusXml),
        )

        val result = client.fetchDelta(
            rootId = "root1",
            baseUrl = server.url("/").toString(),
            rootPath = "/remote.php/dav/files/user/",
            supportsSearch = true,
            supportsTrashbin = false,
            nowEpochMillis = System.currentTimeMillis(),
        )

        // Both have fileId=99 and etag=same-etag, so dedupKey is "fid:99:same-etag"
        assertEquals(1, result.changes.size)
    }

    @Test
    fun `commitWatermark persists to dao`() = runTest {
        client.commitWatermark(
            rootId = "root1",
            serverTimeEpochMillis = 2_000_000L,
            validity = DeltaValidity.valid(),
        )

        val saved = watermarkDao.get("root1")!!
        assertEquals(2_000_000L, saved.lastDeltaServerTimeEpochMillis)
        assertEquals("VALID", saved.deltaValidityState)
        assertEquals("ok", saved.deltaValidityReason)
    }
}
