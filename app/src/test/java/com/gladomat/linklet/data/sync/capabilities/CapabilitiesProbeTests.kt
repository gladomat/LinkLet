package com.gladomat.linklet.data.sync.capabilities

import com.gladomat.linklet.data.sync.db.CapabilitiesCacheDao
import com.gladomat.linklet.data.sync.db.CapabilitiesCacheEntity
import com.gladomat.linklet.data.sync.metrics.InMemorySyncMetrics
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

class FakeCapabilitiesCacheDao : CapabilitiesCacheDao {
    private val cache = mutableMapOf<String, CapabilitiesCacheEntity>()

    override suspend fun upsert(entity: CapabilitiesCacheEntity) {
        cache[entity.rootId] = entity
    }

    override suspend fun get(rootId: String): CapabilitiesCacheEntity? {
        return cache[rootId]
    }

    override suspend fun delete(rootId: String) {
        cache.remove(rootId)
    }

    fun getAll(): Map<String, CapabilitiesCacheEntity> = cache.toMap()
}

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CapabilitiesProbeTests {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var dao: FakeCapabilitiesCacheDao
    private lateinit var metrics: InMemorySyncMetrics
    private lateinit var probe: CapabilitiesProbe

    private val rootId = "test-root"
    private val rootPath = "/remote.php/dav/files/alice/Documents"
    private val now = 1_700_000_000_000L

    // Responses keyed by capability — set per test
    private var searchResponse = MockResponse().setResponseCode(405)
    private var trashbinResponse = MockResponse().setResponseCode(404)
    private var fileIdResponse = MockResponse().setResponseCode(207).setBody(propfindResponseWithoutFileId())

    @Before
    fun setUp() {
        server = MockWebServer()
        // Route responses by request characteristics instead of FIFO order,
        // so parallel async probes get the correct response regardless of arrival order.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.method == "SEARCH" -> searchResponse
                    request.path?.contains("/trashbin/") == true -> trashbinResponse
                    request.method == "PROPFIND" -> fileIdResponse
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
        server.start()
        client = OkHttpClient.Builder().build()
        dao = FakeCapabilitiesCacheDao()
        metrics = InMemorySyncMetrics()
        // Use StandardTestDispatcher so async blocks are deterministic
        probe = CapabilitiesProbe(client, dao, metrics, dispatcher = StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    @Test
    fun `cache hit returns without network call`() = runTest {
        dao.upsert(
            CapabilitiesCacheEntity(
                rootId = rootId,
                supportsSearch = true,
                supportsTrashbin = false,
                supportsFileId = true,
                checkedAtEpochMillis = now - 1000,
                expiresAtEpochMillis = now + 1000,
            ),
        )

        val result = probe.probe(rootId, baseUrl(), rootPath, nowEpochMillis = now)

        assertTrue(result.supportsSearch)
        assertFalse(result.supportsTrashbin)
        assertTrue(result.supportsFileId)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `expired cache triggers re-probe`() = runTest {
        dao.upsert(
            CapabilitiesCacheEntity(
                rootId = rootId,
                supportsSearch = true,
                supportsTrashbin = false,
                supportsFileId = false,
                checkedAtEpochMillis = now - 100_000,
                expiresAtEpochMillis = now - 1,
            ),
        )

        searchResponse = MockResponse().setResponseCode(405)
        trashbinResponse = MockResponse().setResponseCode(404)
        fileIdResponse = MockResponse().setResponseCode(207).setBody(propfindResponseWithoutFileId())

        val result = probe.probe(rootId, baseUrl(), rootPath, nowEpochMillis = now)

        assertFalse(result.supportsSearch)
        assertFalse(result.supportsTrashbin)
        assertFalse(result.supportsFileId)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `SEARCH support detected with 207 response`() = runTest {
        searchResponse = MockResponse().setResponseCode(207)

        val result = probe.probe(rootId, baseUrl(), rootPath, nowEpochMillis = now)

        assertTrue(result.supportsSearch)
    }

    @Test
    fun `SEARCH not supported with 405 response`() = runTest {
        searchResponse = MockResponse().setResponseCode(405)

        val result = probe.probe(rootId, baseUrl(), rootPath, nowEpochMillis = now)

        assertFalse(result.supportsSearch)
    }

    @Test
    fun `trashbin support detected with 207 response`() = runTest {
        trashbinResponse = MockResponse().setResponseCode(207)

        val result = probe.probe(rootId, baseUrl(), rootPath, nowEpochMillis = now)

        assertTrue(result.supportsTrashbin)
    }

    @Test
    fun `fileId support detected when propfind returns item with fileId`() = runTest {
        fileIdResponse = MockResponse().setResponseCode(207).setBody(propfindResponseWithFileId("99999"))

        val result = probe.probe(rootId, baseUrl(), rootPath, nowEpochMillis = now)

        assertTrue(result.supportsFileId)
    }

    @Test
    fun `all negative uses shorter TTL`() = runTest {
        searchResponse = MockResponse().setResponseCode(405)
        trashbinResponse = MockResponse().setResponseCode(404)
        fileIdResponse = MockResponse().setResponseCode(207).setBody(propfindResponseWithoutFileId())

        probe.probe(rootId, baseUrl(), rootPath, nowEpochMillis = now)

        val cached = dao.get(rootId)!!
        assertEquals(now + CapabilitiesProbe.NEGATIVE_TTL_MS, cached.expiresAtEpochMillis)
    }

    @Test
    fun `negative cache expires and re-probes`() = runTest {
        dao.upsert(
            CapabilitiesCacheEntity(
                rootId = rootId,
                supportsSearch = false,
                supportsTrashbin = false,
                supportsFileId = false,
                checkedAtEpochMillis = now - CapabilitiesProbe.NEGATIVE_TTL_MS - 1000,
                expiresAtEpochMillis = now - 1,
            ),
        )

        searchResponse = MockResponse().setResponseCode(207)
        trashbinResponse = MockResponse().setResponseCode(207)
        fileIdResponse = MockResponse().setResponseCode(207).setBody(propfindResponseWithFileId())

        val result = probe.probe(rootId, baseUrl(), rootPath, nowEpochMillis = now)

        assertTrue(result.supportsSearch)
        assertTrue(result.supportsTrashbin)
        assertTrue(result.supportsFileId)
        assertEquals(3, server.requestCount)

        val cached = dao.get(rootId)!!
        assertEquals(now + CapabilitiesProbe.TTL_MS, cached.expiresAtEpochMillis)
    }

    @Test
    fun `metrics are incremented for each probe request`() = runTest {
        searchResponse = MockResponse().setResponseCode(207)
        trashbinResponse = MockResponse().setResponseCode(207)
        fileIdResponse = MockResponse().setResponseCode(207).setBody(propfindResponseWithFileId())

        probe.probe(rootId, baseUrl(), rootPath, nowEpochMillis = now)

        val snapshot = metrics.snapshot()
        assertEquals(1, snapshot.counts["http_SEARCH"])
        assertEquals(2, snapshot.counts["http_PROPFIND"])
    }

    @Test
    fun `extractUsername parses username from rootPath`() {
        assertEquals("alice", CapabilitiesProbe.extractUsername("/remote.php/dav/files/alice/Documents"))
        assertEquals("bob", CapabilitiesProbe.extractUsername("/remote.php/dav/files/bob/"))
        assertEquals(null, CapabilitiesProbe.extractUsername("/some/other/path"))
    }

    @Test
    fun `trashbin returns false when username cannot be extracted`() = runTest {
        searchResponse = MockResponse().setResponseCode(207)
        fileIdResponse = MockResponse().setResponseCode(207).setBody(propfindResponseWithFileId())

        val result = probe.probe(rootId, baseUrl(), "/some/path/without/username", nowEpochMillis = now)

        assertFalse(result.supportsTrashbin)
        // Only 2 requests: SEARCH + fileId PROPFIND (no trashbin since no username)
        assertEquals(2, server.requestCount)
    }

    companion object {
        fun propfindResponseWithFileId(fileId: String = "12345"): String = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response>
                <d:href>/remote.php/dav/files/alice/Documents</d:href>
                <d:propstat>
                  <d:prop>
                    <oc:fileid>$fileId</oc:fileid>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        fun propfindResponseWithoutFileId(): String = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response>
                <d:href>/remote.php/dav/files/alice/Documents</d:href>
                <d:propstat>
                  <d:prop>
                    <oc:fileid></oc:fileid>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }
}
