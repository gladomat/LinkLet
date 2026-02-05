package com.gladomat.linklet.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavRemoteSyncProviderDirectoryTests {

    @Test
    fun `parentDirectoriesFor returns all parents in order`() {
        assertEquals(listOf("a", "a/b"), parentDirectoriesFor("a/b/c.txt"))
    }

    @Test
    fun `parentDirectoriesFor trims slashes`() {
        assertEquals(listOf("a", "a/b"), parentDirectoriesFor("/a/b/c.txt/"))
    }

    @Test
    fun `parentDirectoriesFor returns empty for root file`() {
        assertEquals(emptyList<String>(), parentDirectoriesFor("c.txt"))
    }
}
