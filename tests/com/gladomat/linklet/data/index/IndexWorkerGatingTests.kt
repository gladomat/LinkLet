package com.gladomat.linklet.data.index

import com.gladomat.linklet.data.index.worker.IndexPass2Worker
import com.gladomat.linklet.data.index.worker.IndexPass1Worker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexWorkerGatingTests {

    @Test
    fun `pass1 schedules continuation instead of retrying when pending remains`() {
        assertEquals(
            IndexPass1Worker.Pass1Completion.CONTINUE_PASS_1,
            IndexPass1Worker.completionForPendingCount(pending = 1),
        )
    }

    @Test
    fun `pass1 schedules pass2 when no pending remains`() {
        assertEquals(
            IndexPass1Worker.Pass1Completion.START_PASS_2,
            IndexPass1Worker.completionForPendingCount(pending = 0),
        )
    }

    @Test
    fun `pass2 gate blocks when pass1 pending`() {
        val shouldRun = IndexPass2Worker.shouldRunPass2(
            pass1Pending = 1,
            lastPass1EnqueueAt = null,
            now = 1_000L,
            idleWindowMillis = 10_000L,
        )
        assertFalse(shouldRun)
    }

    @Test
    fun `pass2 gate blocks when idle window not met`() {
        val shouldRun = IndexPass2Worker.shouldRunPass2(
            pass1Pending = 0,
            lastPass1EnqueueAt = 900L,
            now = 1_500L,
            idleWindowMillis = 1_000L,
        )
        assertFalse(shouldRun)
    }

    @Test
    fun `pass2 gate allows when idle window met and no pending`() {
        val shouldRun = IndexPass2Worker.shouldRunPass2(
            pass1Pending = 0,
            lastPass1EnqueueAt = 0L,
            now = 2_000L,
            idleWindowMillis = 1_000L,
        )
        assertTrue(shouldRun)
    }
}
