package com.gladomat.linklet.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootIdTests {

    @Test
    fun `rootId is stable and normalized`() {
        val first = buildRootId(
            baseUrl = "https://example.com/dav/",
            normalizedRootPath = "/Org/Notes/",
            username = "alice",
        )
        val second = buildRootId(
            baseUrl = "https://example.com/dav",
            normalizedRootPath = "Org/Notes",
            username = "alice",
        )

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `rootId changes when username changes`() {
        val alice = buildRootId("https://example.com/dav", "Org", "alice")
        val bob = buildRootId("https://example.com/dav", "Org", "bob")
        assertNotEquals(alice, bob)
    }
}
