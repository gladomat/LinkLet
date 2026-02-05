package com.gladomat.linklet.data.sync

import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncStatusMapperTests {

    @Test
    fun `directory changed maps to status`() {
        val exception = SyncDirectoryChangedException(
            oldPath = "/old",
            newPath = "/new",
            message = "changed",
        )

        val status = SyncStatusMapper.fromDirectoryChanged(exception, nowEpochMillis = 1234L)

        assertEquals(SyncStatusType.DIRECTORY_CHANGED, status.type)
        assertEquals("/old", status.oldPath)
        assertEquals("/new", status.newPath)
        assertTrue(status.requiresAction)
        assertEquals(1234L, status.updatedAtEpochMillis)
    }
}
