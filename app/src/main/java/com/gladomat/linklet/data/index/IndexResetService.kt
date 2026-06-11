package com.gladomat.linklet.data.index

import android.util.Log
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resets the local note index (metadata, link graph, and the indexing work queue) so it can be
 * rebuilt from scratch against the current storage directory.
 *
 * This is intentionally separate from clearing sync state ([SyncStateDao.clearAllStates]): a
 * directory change invalidates the index (paths/fingerprints no longer match what is on disk),
 * but the two concerns used to drift apart — sync state was cleared while the index kept pointing
 * at the old directory, leaving orphaned rows stuck in an endless re-index/retry loop.
 */
@Singleton
class IndexResetService @Inject constructor(
    private val database: NoteDatabase,
    private val noteDao: NoteDao,
    private val indexQueueDao: IndexQueueDao,
    private val indexingStateDao: IndexingStateDao,
    private val indexingScheduler: IndexingScheduler,
) {

    /**
     * Clears all index-derived tables and re-schedules a fresh pass 1 scan.
     */
    suspend fun resetAndReindex() {
        database.withTransaction {
            indexQueueDao.clearAll()
            noteDao.clearNotes()
            noteDao.clearLinks()
            // Reset resumability checkpoints so pass 1 performs a full cold scan.
            indexingStateDao.upsert(IndexingStateEntity(id = 1))
        }
        Log.i(TAG, "Index reset complete; scheduling fresh pass 1")
        indexingScheduler.schedulePass1()
    }

    companion object {
        private const val TAG = "IndexResetService"
    }
}
