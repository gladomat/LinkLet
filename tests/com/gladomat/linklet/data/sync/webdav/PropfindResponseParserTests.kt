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
}
