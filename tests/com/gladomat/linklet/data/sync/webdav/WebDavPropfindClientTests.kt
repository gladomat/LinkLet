package com.gladomat.linklet.data.sync.webdav

import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WebDavPropfindClientTests {

    private lateinit var server: MockWebServer
    private lateinit var client: WebDavPropfindClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = WebDavPropfindClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `propfind sends valid xml namespace attributes`() = runTest {
        val body = """
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response>
                <d:href>/dav/a.org</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getetag>"etag-1"</d:getetag>
                    <d:resourcetype/>
                    <oc:fileid>1</oc:fileid>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(207).setBody(body))

        val result = client.propfind(server.url("/remote.php/dav").toString()).getOrThrow()

        val request = server.takeRequest()
        val sentBody = request.body.readUtf8()
        assertEquals("PROPFIND", request.method)
        assertTrue(sentBody.contains("xmlns:d=\"DAV:\""))
        assertTrue(sentBody.contains("xmlns:oc=\"http://owncloud.org/ns\""))
        assertFalse(sentBody.contains("\\\""))
        assertEquals(1, result.size)
    }
}
