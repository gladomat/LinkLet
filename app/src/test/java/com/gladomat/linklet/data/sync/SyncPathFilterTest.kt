package com.gladomat.linklet.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPathFilterTest {
    @Test
    fun `accepts normal paths and rejects dot or underscore folders`() {
        assertTrue(SyncPathFilter.shouldInclude("notes/foo.org"))
        assertTrue(SyncPathFilter.shouldInclude("notes/images/cat.png"))
        assertFalse(SyncPathFilter.shouldInclude(".git/config"))
        assertFalse(SyncPathFilter.shouldInclude("_private/secret.pdf"))
        assertFalse(SyncPathFilter.shouldInclude("notes/.attachments/file.png"))
        assertFalse(SyncPathFilter.shouldInclude("notes/_cache/file.bin"))
    }

    @Test
    fun `rejects known system files`() {
        assertFalse(SyncPathFilter.shouldInclude(".DS_Store"))
        assertFalse(SyncPathFilter.shouldInclude("Thumbs.db"))
        assertFalse(SyncPathFilter.shouldInclude("desktop.ini"))
        assertFalse(SyncPathFilter.shouldInclude("__MACOSX/thing"))
        assertFalse(SyncPathFilter.shouldInclude(".trash/item"))
    }
}
