package com.gladomat.linklet.data.index

import org.junit.Assert.assertEquals
import org.junit.Test

class IndexTypeConvertersTest {

    private val converters = IndexTypeConverters()

    @Test
    fun `file tags round trip`() {
        val tags = listOf("one", "two", "tag-3")
        val serialized = converters.fromFileTags(tags)
        val parsed = converters.toFileTags(serialized)
        assertEquals(tags, parsed)
    }

    @Test
    fun `file tags empty round trip`() {
        val serialized = converters.fromFileTags(emptyList())
        val parsed = converters.toFileTags(serialized)
        assertEquals(emptyList<String>(), parsed)
    }

    @Test
    fun `index queue status defaults to pending on null or invalid`() {
        assertEquals(IndexQueueStatus.PENDING, converters.toIndexQueueStatus(null))
        assertEquals(IndexQueueStatus.PENDING, converters.toIndexQueueStatus("bogus"))
    }

    @Test
    fun `index queue status and operation round trip`() {
        assertEquals(IndexQueueStatus.RUNNING, converters.toIndexQueueStatus("RUNNING"))
        assertEquals(IndexQueueOperation.DELETE, converters.toIndexQueueOperation("DELETE"))
        assertEquals("DELETE", converters.fromIndexQueueOperation(IndexQueueOperation.DELETE))
    }
}
