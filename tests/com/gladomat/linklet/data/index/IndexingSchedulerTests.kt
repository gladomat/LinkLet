package com.gladomat.linklet.data.index

import androidx.work.ExistingWorkPolicy
import com.gladomat.linklet.data.index.worker.IndexPass1Worker
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexingSchedulerTests {

    @Test
    fun `pass1 scheduling appends instead of replacing running work`() {
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, IndexingScheduler.PASS1_EXISTING_WORK_POLICY)
    }

    @Test
    fun `pass2 scheduling appends instead of replacing retrying work`() {
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, IndexingScheduler.PASS2_EXISTING_WORK_POLICY)
    }

    @Test
    fun `pass1 worker appends pass2 instead of dropping stale completed work`() {
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, IndexPass1Worker.PASS2_EXISTING_WORK_POLICY)
    }
}
