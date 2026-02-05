package com.gladomat.linklet.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncStatusRepositoryTests {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `set and clear status persists fields`() = runTest {
        val repository = SyncStatusRepository(context)
        repository.clearStatus()

        repository.setStatus(
            SyncStatus(
                type = SyncStatusType.DIRECTORY_CHANGED,
                title = "Sync blocked",
                message = "Directory changed",
                oldPath = "/old",
                newPath = "/new",
                requiresAction = true,
                updatedAtEpochMillis = 1234L,
            ),
        )

        val status = repository.statusFlow.first()
        assertEquals(SyncStatusType.DIRECTORY_CHANGED, status?.type)
        assertEquals("/old", status?.oldPath)
        assertEquals("/new", status?.newPath)
        assertEquals(true, status?.requiresAction)

        repository.clearStatus()
        assertNull(repository.statusFlow.first())
    }
}
