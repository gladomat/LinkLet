package com.gladomat.linklet.data.sync.prefetch

import com.gladomat.linklet.data.index.NoteAvailability
import com.gladomat.linklet.data.index.NoteDao
import com.gladomat.linklet.data.sync.db.ServerSnapshotDao

class PrefetchPlanner(
    private val noteDao: NoteDao,
    private val snapshotDao: ServerSnapshotDao,
    private val policy: PrefetchPolicy = PrefetchPolicy(),
) {

    /**
     * Build a list of [PrefetchCandidate]s that should be downloaded, respecting the
     * configured [PrefetchPolicy] budget.
     */
    suspend fun planPrefetch(rootId: String): List<PrefetchCandidate> {
        val eligibleNotes = noteDao.getNotesByAvailability(
            listOf(NoteAvailability.STUB, NoteAvailability.PINNED_OFFLINE),
        )

        // Build a lookup of server snapshots keyed by path for size / lastModified info.
        val snapshots = snapshotDao.getAllFiles(rootId).associateBy { it.path }

        val candidates = eligibleNotes.map { note ->
            val snapshot = snapshots[note.path]
            PrefetchCandidate(
                path = note.path,
                availability = note.availability,
                lastModifiedEpochMillis = snapshot?.lastModifiedEpochMillis,
                sizeBytes = snapshot?.sizeBytes,
            )
        }

        return policy.selectForPrefetch(candidates)
    }
}
