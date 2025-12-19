package com.gladomat.linklet.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavRemoteSyncProviderTest {
    @Test
    fun `includes non-org files and applies sync filter`() {
        assertTrue(WebDavRemoteSyncProvider.isSyncableRemotePath("notes/image.png"))
        assertTrue(WebDavRemoteSyncProvider.isSyncableRemotePath("docs/file.pdf"))
        assertFalse(WebDavRemoteSyncProvider.isSyncableRemotePath("_private/file.pdf"))
        assertFalse(WebDavRemoteSyncProvider.isSyncableRemotePath(".git/config"))
    }
}
