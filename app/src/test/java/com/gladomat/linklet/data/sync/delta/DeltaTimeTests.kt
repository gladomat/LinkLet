package com.gladomat.linklet.data.sync.delta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeltaTimeTests {

    @Test
    fun `parseHttpDate works for valid RFC 1123`() {
        val date = "Sun, 06 Nov 1994 08:49:37 GMT"
        val result = DeltaTime.parseHttpDate(date)
        assertNotNull(result)
        assertEquals(784111777000L, result)
    }

    @Test
    fun `parseHttpDate returns null for garbage`() {
        assertNull(DeltaTime.parseHttpDate("not-a-date"))
        assertNull(DeltaTime.parseHttpDate(""))
        assertNull(DeltaTime.parseHttpDate(null))
        assertNull(DeltaTime.parseHttpDate("   "))
    }

    @Test
    fun `queryWatermark subtracts SKEW_MARGIN_MS`() {
        val serverTime = 1_000_000L
        val result = DeltaTime.queryWatermark(serverTime)
        assertEquals(serverTime - DeltaTime.SKEW_MARGIN_MS, result)
    }
}
