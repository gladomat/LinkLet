package com.gladomat.linklet.data.sync.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PropfindResponseParserTests {

    @Test
    fun `parser streams responses without holding full list`() {
        val xml = checkNotNull(javaClass.classLoader?.getResource("webdav/propfind-multistatus-2-items.xml"))
            .readText()

        val items = PropfindResponseParser.parse(xml.byteInputStream()).toList()

        assertEquals(2, items.count { !it.isDir })
        assertEquals("12345", items[0].etag)
        assertEquals("a.org", items[0].fileId)
        assertNotNull(items[0].lastModifiedEpochMillis)
    }

    @Test
    fun `parser ignores custom namespace tags that shadow DAV names`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:" xmlns:x="urn:custom">
              <d:response>
                <x:href>wrong-value</x:href>
                <d:href>/remote.php/dav/files/user/Org/real.org</d:href>
                <d:propstat>
                  <d:prop>
                    <x:getetag>"wrong-etag"</x:getetag>
                    <d:getetag>"real-etag"</d:getetag>
                    <d:resourcetype/>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val item = PropfindResponseParser.parse(xml.byteInputStream()).single()

        assertEquals("/remote.php/dav/files/user/Org/real.org", item.href)
        assertEquals("real-etag", item.etag)
    }

    @Test
    fun `parser ignores unqualified tags that lack a namespace`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <href>unqualified-href</href>
                <d:href>/remote.php/dav/files/user/Org/real.org</d:href>
                <d:propstat>
                  <d:prop>
                    <getetag>"unqualified-etag"</getetag>
                    <d:getetag>"real-etag"</d:getetag>
                    <d:resourcetype/>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val item = PropfindResponseParser.parse(xml.byteInputStream()).single()

        assertEquals("/remote.php/dav/files/user/Org/real.org", item.href)
        assertEquals("real-etag", item.etag)
    }
}
