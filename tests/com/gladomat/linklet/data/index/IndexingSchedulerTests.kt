package com.gladomat.linklet.data.index

import androidx.work.ExistingWorkPolicy
import com.gladomat.linklet.data.index.worker.IndexPass1Worker
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexingSchedulerTests {

    @Test
    fun `pass1 scheduling appends work instead of canceling active pass1`() {
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, IndexingScheduler.PASS1_EXISTING_WORK_POLICY)
    }

    @Test
    fun `pass2 scheduling replaces stale delayed work`() {
        assertEquals(ExistingWorkPolicy.REPLACE, IndexingScheduler.PASS2_EXISTING_WORK_POLICY)
    }

    @Test
    fun `pass1 worker replaces stale delayed pass2 work`() {
        assertEquals(ExistingWorkPolicy.REPLACE, IndexPass1Worker.PASS2_EXISTING_WORK_POLICY)
    }
}
