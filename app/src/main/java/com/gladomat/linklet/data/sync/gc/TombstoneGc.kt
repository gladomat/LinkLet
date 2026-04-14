package com.gladomat.linklet.data.sync.gc

import com.gladomat.linklet.data.sync.db.ServerSnapshotDao

class TombstoneGc(
    private val snapshotDao: ServerSnapshotDao,
) {
    companion object {
        const val DEFAULT_TTL_MS: Long = 30L * 24 * 60 * 60 * 1000 // 30 days
    }

    /**
     * Delete server_snapshot rows that have been soft-deleted (deletedAtEpochMillis != null)
     * and whose deletedAtEpochMillis is older than the cutoff.
     *
     * @return number of tombstones purged
     */
    suspend fun purge(
        rootId: String,
        nowEpochMillis: Long,
        ttlMs: Long = DEFAULT_TTL_MS,
    ): Int {
        val cutoff = nowEpochMillis - ttlMs
        return snapshotDao.purgeTombstones(rootId, cutoff)
    }
}
