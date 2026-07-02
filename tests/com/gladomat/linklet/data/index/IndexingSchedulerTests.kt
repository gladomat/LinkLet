package com.gladomat.linklet.data.index

import androidx.work.ExistingWorkPolicy
import com.gladomat.linklet.data.index.worker.IndexPass1Worker
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexingSchedulerTests {

    @Test
    fun `pass1 scheduling keeps an in-flight scan instead of restarting it`() {
        // KEEP prevents a re-schedule (app launch, sync completion, note save) from cancelling and
        // restarting a long cold SAF scan that would otherwise never commit. See IndexingScheduler.
        assertEquals(ExistingWorkPolicy.KEEP, IndexingScheduler.PASS1_EXISTING_WORK_POLICY)
    }

    @Test
    fun `pass2 scheduling keeps an in-flight pass2 instead of restarting it`() {
        assertEquals(ExistingWorkPolicy.KEEP, IndexingScheduler.PASS2_EXISTING_WORK_POLICY)
    }

    @Test
    fun `pass1 worker replaces stale delayed pass2 work`() {
        assertEquals(ExistingWorkPolicy.REPLACE, IndexPass1Worker.PASS2_EXISTING_WORK_POLICY)
    }
}
